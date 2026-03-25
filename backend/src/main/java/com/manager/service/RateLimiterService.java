package com.manager.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sliding-window rate limiter backed by Firestore.
 * Survives server restarts, works across multiple backend instances.
 *
 * Collection: rate_limits
 * Document:   {key}  (typically Firebase UID)
 * Fields:     timestamps: List<Long>  – epoch-ms of each request in the current window
 *
 * Firestore transaction guarantees atomicity; each AI request adds ~one round-trip
 * (~80–150ms) which is acceptable given the 120s SSE timeout in AiController.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String COLLECTION = "rate_limits";
    private static final long WINDOW_MS = 60_000L;   // 1-minute sliding window

    private final Firestore firestore;

    public RateLimiterService(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Returns true if the caller has exceeded {@code maxRequests} in the last minute.
     * Also records the current request timestamp when NOT limited.
     *
     * Falls back to NOT rate-limiting on Firestore errors so AI chat stays available.
     */
    public boolean isRateLimited(String key, int maxRequests) {
        try {
            return runCheck(key, maxRequests);
        } catch (Exception e) {
            log.warn("RateLimiterService Firestore error for key={}, allowing request: {}", key, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean runCheck(String key, int maxRequests) throws Exception {
        DocumentReference ref = firestore.collection(COLLECTION).document(key);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        return firestore.<Boolean>runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();

            List<Long> timestamps;
            if (snap.exists()) {
                Object raw = snap.get("timestamps");
                if (raw instanceof List) {
                    // Firestore stores numbers as Long
                    timestamps = new ArrayList<>((List<Long>) raw);
                } else {
                    timestamps = new ArrayList<>();
                }
            } else {
                timestamps = new ArrayList<>();
            }

            // Evict entries outside the window
            final long ws = windowStart;
            timestamps.removeIf(t -> t < ws);

            if (timestamps.size() >= maxRequests) {
                log.debug("Rate limit exceeded for key={}: {} requests in last 60s", key, timestamps.size());
                return true;
            }

            timestamps.add(now);
            tx.set(ref, Map.of("timestamps", timestamps));
            return false;
        }).get();
    }
}
