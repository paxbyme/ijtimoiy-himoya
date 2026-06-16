package com.manager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provider-agnostic facade for all LLM calls. A single {@code ai.provider}
 * property ("openai" or "gemini", default gemini) selects the backend for the
 * whole app — chat, embeddings, transcription, and OCR. Keeping the switch in
 * one place means callers (RagService, EmbeddingService, DocumentService,
 * AiController) never branch on the provider themselves.
 *
 * Both backends share the same method signatures and the same
 * {@link com.manager.exception.GeminiRateLimitException} on HTTP 429, so the
 * provider is fully interchangeable.
 */
@Service
public class AiService {

    private final GeminiService gemini;
    private final OpenAiService openai;

    @Value("${ai.provider:gemini}")
    private String provider;

    public AiService(GeminiService gemini, OpenAiService openai) {
        this.gemini = gemini;
        this.openai = openai;
    }

    public boolean isOpenAi() {
        return "openai".equalsIgnoreCase(provider);
    }

    // ---- Chat ----

    public String chat(String systemInstruction, List<Map<String, Object>> history, String userMessage)
            throws IOException {
        return isOpenAi()
                ? openai.chat(systemInstruction, history, userMessage)
                : gemini.chat(systemInstruction, history, userMessage);
    }

    public String chatStream(String systemInstruction, List<Map<String, Object>> history,
                             String userMessage, Consumer<String> onToken) throws IOException {
        return isOpenAi()
                ? openai.chatStream(systemInstruction, history, userMessage, onToken)
                : gemini.chatStream(systemInstruction, history, userMessage, onToken);
    }

    // ---- Embeddings ----

    public float[] embed(String text) throws IOException {
        return isOpenAi() ? openai.embed(text) : gemini.embed(text);
    }

    public List<float[]> batchEmbed(List<String> texts) throws IOException {
        return isOpenAi() ? openai.batchEmbed(texts) : gemini.batchEmbed(texts);
    }

    // ---- Transcription ----

    public String transcribeAudio(byte[] bytes, String mimeType) throws IOException {
        return isOpenAi() ? openai.transcribeAudio(bytes, mimeType) : gemini.transcribeAudio(bytes, mimeType);
    }

    // ---- OCR ----

    public String ocrDocument(byte[] bytes, String mimeType) throws IOException {
        return isOpenAi() ? openai.ocrDocument(bytes, mimeType) : gemini.ocrDocument(bytes, mimeType);
    }

    public String ocrImagesViaFilesApi(List<byte[]> images) throws IOException {
        return isOpenAi() ? openai.ocrImagesViaFilesApi(images) : gemini.ocrImagesViaFilesApi(images);
    }

    public String ocrImages(List<byte[]> images) throws IOException {
        return isOpenAi() ? openai.ocrImages(images) : gemini.ocrImages(images);
    }
}
