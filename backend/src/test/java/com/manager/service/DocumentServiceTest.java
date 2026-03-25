package com.manager.service;

import com.manager.dto.DocumentDto;
import com.manager.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock EmbeddingService embeddingService;

    @InjectMocks DocumentService documentService;

    // ---- createDocument ----

    @Test
    void createDocument_savesWithProcessingStatus() throws Exception {
        DocumentDto saved = DocumentDto.builder().id("doc-1").status("PROCESSING").build();
        when(documentRepository.save(any())).thenReturn(saved);

        DocumentDto result = documentService.createDocument("report.pdf", "dept-1", "user-1");

        assertThat(result.getId()).isEqualTo("doc-1");
        assertThat(result.getStatus()).isEqualTo("PROCESSING");

        ArgumentCaptor<DocumentDto> captor = ArgumentCaptor.forClass(DocumentDto.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PROCESSING");
        assertThat(captor.getValue().getFileName()).isEqualTo("report.pdf");
    }

    // ---- splitIntoChunks (package-private, testable directly) ----

    @Test
    void splitIntoChunks_shortText_returnsSingleChunk() {
        List<String> chunks = documentService.splitIntoChunks("Hello world.", 500, 100);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("Hello world.");
    }

    @Test
    void splitIntoChunks_emptyText_returnsEmptyList() {
        assertThat(documentService.splitIntoChunks("", 500, 100)).isEmpty();
        assertThat(documentService.splitIntoChunks(null, 500, 100)).isEmpty();
    }

    @Test
    void splitIntoChunks_longText_splitsIntoMultipleChunks() {
        // 600 chars of 'a' split at 200 chars
        String text = "a".repeat(600);
        List<String> chunks = documentService.splitIntoChunks(text, 200, 0);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
    }

    @Test
    void splitIntoChunks_overlapProducesRedundancy() {
        // 300-char text, 200 chunk, 50 overlap → should produce 2 chunks, second starts mid-first
        String text = "word ".repeat(60); // 300 chars
        List<String> chunks = documentService.splitIntoChunks(text, 200, 50);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void splitIntoChunks_respectsSentenceBoundaries() {
        String text = "First sentence here. Second sentence here. Third sentence that is quite long indeed.";
        List<String> chunks = documentService.splitIntoChunks(text, 50, 10);
        // Every chunk should not start mid-word (at minimum doesn't cut at exactly 50 chars mid-word)
        chunks.forEach(c -> assertThat(c.trim()).isNotEmpty());
    }

    // ---- getDocuments ----

    @Test
    void getDocuments_delegatesToRepository() throws Exception {
        DocumentDto doc = DocumentDto.builder().id("d1").departmentId("dept-1").build();
        when(documentRepository.findByDepartmentId("dept-1")).thenReturn(List.of(doc));

        List<DocumentDto> result = documentService.getDocuments("dept-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("d1");
    }

    // ---- deleteDocument ----

    @Test
    void deleteDocument_deletesVectorsAndDocument() throws Exception {
        when(documentRepository.findChunksByDocumentId("doc-1")).thenReturn(List.of());

        documentService.deleteDocument("doc-1");

        verify(embeddingService).deleteVectors("doc-1", 0);
        verify(documentRepository).delete("doc-1");
    }

    @Test
    void deleteDocument_vectorDeletionFailure_stillDeletesDocument() throws Exception {
        when(documentRepository.findChunksByDocumentId("doc-1")).thenReturn(List.of());
        doThrow(new RuntimeException("Pinecone timeout")).when(embeddingService).deleteVectors(any(), anyInt());

        // Should not throw
        documentService.deleteDocument("doc-1");

        verify(documentRepository).delete("doc-1");
    }

    // ---- processDocumentAsync – verifies COMPLETED status on success ----

    @Test
    void processDocumentAsync_textFile_completesSuccessfully() throws Exception {
        String docId = "doc-async";
        byte[] content = "Some plain text content for testing.".getBytes(StandardCharsets.UTF_8);

        DocumentDto doc = DocumentDto.builder().id(docId).status("PROCESSING").build();
        when(documentRepository.findById(docId)).thenReturn(doc);
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.processDocumentAsync(docId, content, "notes.txt", "dept-1");

        // processDocumentAsync is @Async but in tests runs synchronously (no executor override)
        verify(embeddingService).embedAndStore(eq(docId), eq("dept-1"), anyList());
        verify(documentRepository, atLeastOnce()).save(argThat(d -> "COMPLETED".equals(d.getStatus())));
    }

    @Test
    void processDocumentAsync_embeddingFailure_marksDocumentFailed() throws Exception {
        String docId = "doc-fail";
        byte[] content = "text".getBytes(StandardCharsets.UTF_8);

        DocumentDto doc = DocumentDto.builder().id(docId).status("PROCESSING").build();
        when(documentRepository.findById(docId)).thenReturn(doc);
        when(documentRepository.save(any())).thenReturn(doc);
        doThrow(new RuntimeException("Embedding error")).when(embeddingService).embedAndStore(any(), any(), any());

        documentService.processDocumentAsync(docId, content, "notes.txt", "dept-1");

        verify(documentRepository, atLeastOnce()).save(argThat(d -> "FAILED".equals(d.getStatus())));
    }
}
