package com.manager.service;

/**
 * A single bidirectional live-voice session, independent of provider.
 * Implemented by {@link GeminiLiveClient} (Gemini Live API) and
 * {@link OpenAiLiveClient} (OpenAI Realtime API) so the WebSocket bridge can
 * use either backend behind the {@code ai.provider} switch.
 *
 * Contract (matches the mobile wire protocol): callers feed raw 16-bit PCM
 * 16 kHz mono mic audio via {@link #sendAudio}; the implementation emits model
 * audio / text / turn-complete through the callbacks given at construction.
 */
public interface LiveClient {
    /** Forward a raw 16-bit PCM 16 kHz mono mic frame to the model. */
    void sendAudio(byte[] pcm16kHzMono);

    /** Signal end of the user's turn (no-op when the server owns VAD). */
    void endTurn();

    /** Signal start of the user's turn (no-op when the server owns VAD). */
    void startTurn();

    /** Close the underlying session. */
    void close();
}
