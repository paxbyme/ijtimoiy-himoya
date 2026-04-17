package com.manager.service;

import com.manager.dto.DocumentDto;
import com.manager.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final int OCR_MIN_TEXT_LENGTH = 20;
    private static final int OCR_MAX_IMAGES_PER_CALL = 10;

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final DocumentProcessorService documentProcessorService;
    private final GeminiService geminiService;

    public DocumentService(DocumentRepository documentRepository,
                           EmbeddingService embeddingService,
                           DocumentProcessorService documentProcessorService,
                           GeminiService geminiService) {
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.documentProcessorService = documentProcessorService;
        this.geminiService = geminiService;
    }

    /**
     * Create document metadata and return immediately.
     * Call processDocumentAsync separately for background processing.
     */
    public DocumentDto createDocument(String fileName, String departmentId, String uploadedBy) throws Exception {
        DocumentDto document = DocumentDto.builder()
                .departmentId(departmentId)
                .uploadedBy(uploadedBy)
                .title(fileName)
                .fileName(fileName)
                .storageUrl("")
                .status("PROCESSING")
                .createdAt(Instant.now().toString())
                .build();

        return documentRepository.save(document);
    }

    /**
     * Extracts text and chunks the document, then delegates to DocumentProcessorService
     * which handles @Retryable + @Recover (dead-letter on final failure).
     *
     * @Async here + @Retryable in DocumentProcessorService is the correct pattern:
     * both annotations need separate proxy beans to function properly.
     */
    @Async("documentProcessorExecutor")
    public void processDocumentAsync(String documentId, byte[] fileBytes, String fileName, String departmentId) {
        try {
            log.info("Starting async document processing documentId={} fileName={}", documentId, fileName);
            String text = extractText(fileBytes, fileName);
            List<String> chunks = splitIntoChunks(text, 500, 100);
            log.debug("Extracted {} chunks from documentId={}", chunks.size(), documentId);
            documentProcessorService.process(documentId, fileBytes, fileName, departmentId, chunks);
        } catch (Exception e) {
            // @Recover in DocumentProcessorService handles FAILED status + dead-letter;
            // this catch guards against extraction failures before we reach the retryable call.
            log.error("Pre-processing failure documentId={}: {}", documentId, e.getMessage(), e);
            try {
                updateDocumentStatus(documentId, "FAILED");
            } catch (Exception ex) {
                log.error("Failed to update document status for documentId={}: {}", documentId, ex.getMessage());
            }
        }
    }

    /**
     * Legacy synchronous processing method (kept for backward compatibility).
     */
    public DocumentDto uploadAndProcess(MultipartFile file, String departmentId, String uploadedBy) throws Exception {
        DocumentDto document = DocumentDto.builder()
                .departmentId(departmentId)
                .uploadedBy(uploadedBy)
                .title(file.getOriginalFilename())
                .fileName(file.getOriginalFilename())
                .storageUrl("")
                .status("PROCESSING")
                .createdAt(Instant.now().toString())
                .build();

        document = documentRepository.save(document);

        try {
            String text = extractText(file.getBytes(), file.getOriginalFilename());
            List<String> chunks = splitIntoChunks(text, 500, 100);
            embeddingService.embedAndStore(document.getId(), departmentId, chunks);
            updateDocumentStatus(document.getId(), "COMPLETED");
            document.setStatus("COMPLETED");
        } catch (Exception e) {
            updateDocumentStatus(document.getId(), "FAILED");
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }

        return document;
    }

    public List<DocumentDto> getDocuments(String departmentId) throws Exception {
        return documentRepository.findByDepartmentId(departmentId);
    }

    public void deleteDocument(String id) throws Exception {
        List<Map<String, Object>> chunks = documentRepository.findChunksByDocumentId(id);
        try {
            embeddingService.deleteVectors(id, chunks.size());
        } catch (Exception e) {
            log.warn("Failed to delete vectors from Pinecone for documentId={}: {}", id, e.getMessage());
        }
        documentRepository.delete(id);
    }

    private void updateDocumentStatus(String documentId, String status) throws Exception {
        DocumentDto doc = documentRepository.findById(documentId);
        if (doc != null) {
            doc.setStatus(status);
            documentRepository.save(doc);
        }
    }

    public String extractText(byte[] fileBytes, String fileName) throws IOException {
        if (fileName == null) {
            return new String(fileBytes, StandardCharsets.UTF_8);
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            return extractPdfText(fileBytes);
        } else if (lowerName.endsWith(".docx")) {
            return extractDocxText(fileBytes);
        } else if (lowerName.endsWith(".doc")) {
            return extractDocText(fileBytes);
        } else {
            // txt, md, csv, and other text-based files
            return new String(fileBytes, StandardCharsets.UTF_8);
        }
    }

    private String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && text.trim().length() >= OCR_MIN_TEXT_LENGTH) {
                return text;
            }
            log.info("PDF text extraction yielded {} chars — trying OCR via Gemini",
                    text == null ? 0 : text.trim().length());
            List<byte[]> images = renderPdfPages(document, OCR_MAX_IMAGES_PER_CALL);
            return ocrFallback(images, text);
        }
    }

    private String extractDocxText(byte[] bytes) throws IOException {
        String extractedText;
        List<byte[]> pageImages = new ArrayList<>();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(bis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            extractedText = extractor.getText();

            // Always look for large embedded images (> 30 KB) that are JPEG or PNG —
            // these are almost certainly scanned page images, not decorative logos.
            for (XWPFPictureData pic : document.getAllPictures()) {
                byte[] picData = pic.getData();
                if (picData.length > 30_000 && isSupportedImageFormat(picData)) {
                    pageImages.add(picData);
                    if (pageImages.size() >= OCR_MAX_IMAGES_PER_CALL) break;
                }
            }

            // If no large supported images were found but extracted text is too short,
            // try any supported-format image (small logos may still contain text).
            if (pageImages.isEmpty()
                    && (extractedText == null || extractedText.trim().length() < OCR_MIN_TEXT_LENGTH)) {
                log.info("DOCX text extraction yielded {} chars — trying all supported images for OCR",
                        extractedText == null ? 0 : extractedText.trim().length());
                for (XWPFPictureData pic : document.getAllPictures()) {
                    byte[] picData = pic.getData();
                    if (isSupportedImageFormat(picData)) {
                        pageImages.add(picData);
                        if (pageImages.size() >= OCR_MAX_IMAGES_PER_CALL) break;
                    }
                }
            }
        }

        if (!pageImages.isEmpty()) {
            log.info("DOCX: sending {} image(s) to OCR (extracted text: {} chars)",
                    pageImages.size(), extractedText == null ? 0 : extractedText.trim().length());
            return ocrFallback(pageImages, extractedText);
        }
        return extractedText != null ? extractedText : "";
    }

    /** Returns true only for image formats that Gemini Vision can decode (JPEG, PNG, GIF). */
    private boolean isSupportedImageFormat(byte[] bytes) {
        if (bytes.length < 4) return false;
        // JPEG: FF D8
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return true;
        // PNG: 89 50 4E 47
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return true;
        // GIF: 47 49 46
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return true;
        return false;
    }

    private String ocrFallback(List<byte[]> images, String originalText) {
        if (images.isEmpty()) return originalText != null ? originalText : "";
        try {
            String ocrText = geminiService.ocrImages(images);
            if (ocrText != null && !ocrText.isBlank()
                    && !ocrText.startsWith("I'm sorry")) {
                // Prefer OCR result if it provides substantially more content
                int origLen = originalText != null ? originalText.trim().length() : 0;
                if (ocrText.trim().length() > origLen) {
                    return ocrText;
                }
            }
        } catch (Exception e) {
            log.warn("OCR via Gemini failed: {}", e.getMessage());
        }
        return originalText != null ? originalText : "";
    }

    private List<byte[]> renderPdfPages(PDDocument document, int maxPages) {
        List<byte[]> images = new ArrayList<>();
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(img, "JPEG", baos);
                    images.add(baos.toByteArray());
                }
            }
        } catch (Exception e) {
            log.warn("PDF page rendering failed: {}", e.getMessage());
        }
        return images;
    }

    private String extractDocText(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(bis);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        } catch (OfficeXmlFileException e) {
            // File has .doc extension but is actually Office 2007+ XML (.docx format)
            log.warn("File with .doc extension is actually OOXML format, retrying as .docx");
            return extractDocxText(bytes);
        } catch (IllegalArgumentException e) {
            // File has .doc extension but is actually HTML (Word HTML format)
            if (e.getMessage() != null && e.getMessage().contains("HTML")) {
                log.warn("File with .doc extension is Word HTML format, extracting as HTML");
                return extractHtmlText(bytes);
            }
            throw new IOException("Cannot read .doc file: " + e.getMessage(), e);
        }
    }

    private String extractHtmlText(byte[] bytes) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        return html
                .replaceAll("(?si)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?si)<script[^>]*>.*?</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#[0-9]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Sentence-aware text chunking with overlap.
     * Splits text into chunks of approximately maxChunkSize characters,
     * respecting sentence boundaries to avoid cutting mid-sentence.
     */
    List<String> splitIntoChunks(String text, int maxChunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // Normalize line endings
        text = text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");

        // Split into sentences
        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            // Fallback: if no sentences detected, use simple split
            return simpleSplit(text, maxChunkSize, overlapSize);
        }

        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            // If a single sentence exceeds max chunk size, split it further
            if (sentence.length() > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                // Split long sentence by clauses or fixed size
                List<String> subChunks = simpleSplit(sentence, maxChunkSize, overlapSize);
                chunks.addAll(subChunks);
                continue;
            }

            // If adding this sentence would exceed the limit
            if (currentChunk.length() + sentence.length() > maxChunkSize && currentChunk.length() > 0) {
                String chunkText = currentChunk.toString().trim();
                chunks.add(chunkText);

                // Create overlap from the end of the current chunk
                currentChunk = new StringBuilder();
                if (overlapSize > 0 && chunkText.length() > overlapSize) {
                    // Find a sentence boundary within the overlap region
                    String overlapCandidate = chunkText.substring(chunkText.length() - overlapSize);
                    int sentenceStart = overlapCandidate.indexOf(". ");
                    if (sentenceStart >= 0 && sentenceStart < overlapCandidate.length() - 2) {
                        currentChunk.append(overlapCandidate.substring(sentenceStart + 2));
                    } else {
                        currentChunk.append(overlapCandidate);
                    }
                }
            }

            currentChunk.append(sentence);
        }

        if (currentChunk.length() > 0) {
            String remaining = currentChunk.toString().trim();
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end);
            if (!sentence.trim().isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private List<String> simpleSplit(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end).trim());
            start += chunkSize - overlap;
        }
        return chunks;
    }
}
