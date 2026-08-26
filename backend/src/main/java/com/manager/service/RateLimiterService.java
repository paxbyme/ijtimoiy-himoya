package com.manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-instance token-bucket rate limiter for latency-sensitive AI endpoints.
 *
 * The request path is memory-only: no Firestore transaction or other network
 * operation occurs before an AI request is cleared. Each key has a small,
 * independently synchronized bucket, so unrelated users never contend on a
 * global lock. If the backend is scaled to multiple replicas, replace this
 * implementation with a Redis-backed atomic bucket to preserve a global limit.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final long REFILL_PERIOD_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long STALE_BUCKET_NANOS = Duration.ofMinutes(15).toNanos();

    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} when the request must be rejected.
     *
     * A full bucket allows {@code maxRequests} immediately and then refills at
     * {@code maxRequests / minute}. The method performs only map and arithmetic
     * operations and is intended to remain comfortably below 10 ms.
     */
    public boolean isRateLimited(String key, int maxRequests) {
        if (key == null || key.isBlank() || maxRequests <= 0) {
            return true;
        }

        long now = System.nanoTime();
        TokenBucket bucket = buckets.computeIfAbsent(
                key, ignored -> new TokenBucket(maxRequests, now));
        boolean allowed = bucket.tryConsume(maxRequests, now);

        if (!allowed) {
            log.debug("Rate limit exceeded for key={}", key);
        }
        return !allowed;
    }

    /** Cleanup is off the request path, so a large key set cannot affect TTFT. */
    @Scheduled(fixedDelayString = "${rate-limit.cleanup-ms:300000}")
    void removeStaleBuckets() {
        long cutoff = System.nanoTime() - STALE_BUCKET_NANOS;
        buckets.entrySet().removeIf(entry -> entry.getValue().lastSeenNanos() < cutoff);
    }

    int trackedBucketCount() {
        return buckets.size();
    }

    private static final class TokenBucket {
        private int capacity;
        private double tokens;
        private long lastRefillNanos;
        private volatile long lastSeenNanos;

        private TokenBucket(int capacity, long now) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = now;
            this.lastSeenNanos = now;
        }

        private synchronized boolean tryConsume(int requestedCapacity, long now) {
            refill(now);

            if (requestedCapacity != capacity) {
                capacity = requestedCapacity;
                tokens = Math.min(tokens, capacity);
            }

            lastSeenNanos = now;
            if (tokens < 1.0) {
                return false;
            }

            tokens -= 1.0;
            return true;
        }

        private void refill(long now) {
            long elapsed = Math.max(0, now - lastRefillNanos);
            if (elapsed == 0) return;

            double replenished = (double) elapsed * capacity / REFILL_PERIOD_NANOS;
            tokens = Math.min(capacity, tokens + replenished);
            lastRefillNanos = now;
        }

        private long lastSeenNanos() {
            return lastSeenNanos;
        }
    }
}
