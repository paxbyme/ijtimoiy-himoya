package com.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfig {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    /** Vision-capable model used for OCR (image -> text). */
    @Value("${openai.ocr.model:gpt-4o}")
    private String ocrModel;

    /** Speech-to-text model used for voice-message transcription. */
    @Value("${openai.transcribe.model:whisper-1}")
    private String transcribeModel;

    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    /** Must match the Pinecone index dimension (512) so vectors stay compatible. */
    @Value("${openai.embedding.dimension:512}")
    private int embeddingDimension;

    /** Realtime (live voice) model. */
    @Value("${openai.realtime.model:gpt-4o-realtime-preview}")
    private String realtimeModel;

    /** Realtime voice id (alloy, echo, shimmer, ...). */
    @Value("${openai.realtime.voice:alloy}")
    private String realtimeVoice;

    /** Override in tests to point at a mock server. */
    @Value("${openai.base.url:https://api.openai.com}")
    private String baseUrl;

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getOcrModel() { return ocrModel; }
    public String getTranscribeModel() { return transcribeModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public String getRealtimeModel() { return realtimeModel; }
    public String getRealtimeVoice() { return realtimeVoice; }
    public String getBaseUrl() { return baseUrl; }
}
