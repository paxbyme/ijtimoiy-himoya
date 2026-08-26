package com.manager.dto;

/**
 * One ranked piece of documentary evidence returned by the RAG search.
 * Chunk indexes are internal retrieval metadata and must never be presented
 * as legal clause/paragraph numbers.
 */
public record RagSource(
        String vectorId,
        String documentId,
        String documentTitle,
        int chunkIndex,
        double score,
        String content,
        String sourceUrl
) {
    public RagSource(String vectorId, String documentId, String documentTitle,
                     int chunkIndex, double score, String content) {
        this(vectorId, documentId, documentTitle, chunkIndex, score, content, "");
    }

    public String citationLabel() {
        String title = documentTitle == null || documentTitle.isBlank()
                ? "Noma'lum hujjat"
                : documentTitle.trim();
        return title;
    }

    public String citationReference() {
        String label = citationLabel();
        return sourceUrl == null || sourceUrl.isBlank()
                ? label
                : label + " — " + sourceUrl.trim();
    }
}
