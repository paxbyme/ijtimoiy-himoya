package com.manager.service;

import com.manager.dto.DocumentDto;
import com.manager.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
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

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final DocumentProcessorService documentProcessorService;

    public DocumentService(DocumentRepository documentRepository,
                           EmbeddingService embeddingService,
                           DocumentProcessorService documentProcessorService) {
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.documentProcessorService = documentProcessorService;
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

    private String extractText(byte[] fileBytes, String fileName) throws IOException {
        if (fileName == null) {
            return new String(fileBytes, StandardCharsets.UTF_8);
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            return extractPdfText(fileBytes);
        } else if (lowerName.endsWith(".docx")) {
            return extractDocxText(fileBytes);
        } else if (lowerName.endsWith(".csv")) {
            return new String(fileBytes, StandardCharsets.UTF_8);
        } else {
            // txt, md, and other text-based files
            return new String(fileBytes, StandardCharsets.UTF_8);
        }
    }

    private String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocxText(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(bis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
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
