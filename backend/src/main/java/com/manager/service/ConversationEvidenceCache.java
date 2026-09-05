package com.manager.service;

import com.manager.dto.RagSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the legal evidence each conversation was last answered from.
 *
 * <p>Retrieval is a live scrape of Lex.uz, so it can come back empty on a slow
 * fetch even when the law exists and was found minutes earlier. Without a
 * memory of the previous turn the assistant would answer a follow-up with "no
 * normative document found" while its own earlier answer, still on the user's
 * screen, cites one. Serving the conversation's last evidence keeps the follow-up
 * both answered and grounded in the same documents.</p>
 *
 * <p>The cache is per-instance and intentionally lossy: a restart only means the
 * next turn retrieves again.</p>
 */
@Service
public class ConversationEvidenceCache {

    private static final int MAX_CONVERSATIONS = 500;
    private static final Duration TTL = Duration.ofHours(2);

    private final Map<String, Entry> entries = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            });

    public void put(String conversationId, List<RagSource> evidence) {
        if (conversationId == null || conversationId.isBlank()) return;
        if (evidence == null || evidence.isEmpty()) return;
        entries.put(conversationId, new Entry(List.copyOf(evidence), Instant.now().plus(TTL)));
    }

    /** The conversation's last evidence, or an empty list when there is none. */
    public List<RagSource> get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        Entry entry = entries.get(conversationId);
        if (entry == null) return List.of();
        if (entry.expiresAt.isBefore(Instant.now())) {
            entries.remove(conversationId);
            return List.of();
        }
        return entry.evidence;
    }

    public void evict(String conversationId) {
        if (conversationId != null) entries.remove(conversationId);
    }

    private record Entry(List<RagSource> evidence, Instant expiresAt) {}
}
