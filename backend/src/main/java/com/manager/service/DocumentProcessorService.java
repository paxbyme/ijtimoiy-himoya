package com.manager.service;

import com.google.cloud.firestore.Firestore;
import com.manager.dto.DocumentDto;
import com.manager.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Extracted from DocumentService so that @Retryable AOP proxy works correctly.
 * (@Async and @Retryable cannot both be on the same bean method — each creates
 *  its own proxy and they do not compose.  The @Async wrapper lives in
 *  DocumentService, the @Retryable logic lives here.)
 *
 * Retry policy:
 *   - Max 3 attempts (1 original + 2 retries)
 *   - Exponential backoff: 2s → 4s (capped at 10s)
 *   - Retries on IOException (Gemini/Pinecone network) and RuntimeException
 *
 * Dead-letter:
 *   - On total failure a document is written to Firestore collection
 *     "dead_letter_docs" for manual inspection / reprocessing.
 */
@Service
public class DocumentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessorService.class);
    private static final String DEAD_LETTER_COLLECTION = "dead_letter_docs";

    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final Firestore firestore;

    public DocumentProcessorService(EmbeddingService embeddingService,
                                    DocumentRepository documentRepository,
                                    Firestore firestore) {
        this.embeddingService = embeddingService;
        this.documentRepository = documentRepository;
        this.firestore = firestore;
    }

    @Retryable(
            retryFor  = { IOException.class, RuntimeException.class },
            maxAttempts = 3,
            backoff   = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000)
    )
    public void process(String documentId, byte[] fileBytes, String fileName,
                        String departmentId, List<String> chunks) throws Exception {
        log.debug("Processing document documentId={} chunks={}", documentId, chunks.size());
        embeddingService.embedAndStore(documentId, departmentId, chunks);
        updateStatus(documentId, "COMPLETED");
        log.info("Document processing complete documentId={}", documentId);
    }

    /**
     * Called automatically by Spring Retry after all attempts are exhausted.
     * Saves a dead-letter record so ops can re-trigger processing later.
     */
    @Recover
    public void recover(Exception cause, String documentId, byte[] fileBytes,
                        String fileName, String departmentId, List<String> chunks) {
        log.error("Document processing failed after max retries documentId={} error={}",
                documentId, cause.getMessage(), cause);

        try {
            updateStatus(documentId, "FAILED");
        } catch (Exception e) {
            log.error("Could not mark document FAILED documentId={}", documentId, e);
        }

        try {
            Map<String, Object> deadLetter = Map.of(
                    "documentId",   documentId,
                    "fileName",     fileName != null ? fileName : "",
                    "departmentId", departmentId != null ? departmentId : "",
                    "errorMessage", cause.getMessage() != null ? cause.getMessage() : "unknown",
                    "errorType",    cause.getClass().getName(),
                    "failedAt",     Instant.now().toString()
            );
            firestore.collection(DEAD_LETTER_COLLECTION).document(documentId).set(deadLetter).get();
            log.info("Dead-letter record written for documentId={}", documentId);
        } catch (Exception e) {
            log.error("Failed to write dead-letter record for documentId={}", documentId, e);
        }
    }

    private void updateStatus(String documentId, String status) throws Exception {
        DocumentDto doc = documentRepository.findById(documentId);
        if (doc != null) {
            doc.setStatus(status);
            documentRepository.save(doc);
        }
    }
}
