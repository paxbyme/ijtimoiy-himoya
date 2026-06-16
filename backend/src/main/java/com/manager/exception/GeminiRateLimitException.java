package com.manager.exception;

/**
 * Thrown when the Gemini API responds with HTTP 429 RESOURCE_EXHAUSTED.
 *
 * Extends RuntimeException (NOT IOException) on purpose: the chat methods are
 * annotated {@code @Retryable(retryFor = IOException.class)}, and a quota error
 * must NOT be retried — blindly retrying a 429 fires more calls at the exact
 * per-model token bucket that is already exhausted, deepening the hole and
 * lengthening the server-side retry delay. By using a separate type we let
 * genuine network IOExceptions retry while quota errors propagate immediately.
 */
public class GeminiRateLimitException extends RuntimeException {
    public GeminiRateLimitException(String message) {
        super(message);
    }
}
