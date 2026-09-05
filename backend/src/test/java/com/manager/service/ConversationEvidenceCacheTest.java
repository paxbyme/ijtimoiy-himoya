package com.manager.service;

import com.manager.dto.RagSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationEvidenceCacheTest {

    private static final List<RagSource> EVIDENCE = List.of(new RagSource(
            "v1", "d1", "Ijtimoiy ish to'g'risida. O'zbekiston Respublikasining Qonuni",
            3, 0.9, "37-modda. Ijtimoiy xodimning maqomi tan olinadi.",
            "https://lex.uz/uz/docs/-8346774#-8348791"));

    @Test
    void previousTurnEvidenceIsServedToTheNextTurn() {
        ConversationEvidenceCache cache = new ConversationEvidenceCache();
        cache.put("conv-1", EVIDENCE);

        assertThat(cache.get("conv-1")).isEqualTo(EVIDENCE);
    }

    @Test
    void emptyRetrievalNeverOverwritesRememberedEvidence() {
        ConversationEvidenceCache cache = new ConversationEvidenceCache();
        cache.put("conv-1", EVIDENCE);
        cache.put("conv-1", List.of());

        assertThat(cache.get("conv-1")).isEqualTo(EVIDENCE);
    }

    @Test
    void unknownConversationHasNoEvidence() {
        ConversationEvidenceCache cache = new ConversationEvidenceCache();

        assertThat(cache.get("missing")).isEmpty();
        assertThat(cache.get(null)).isEmpty();
        assertThat(cache.get("  ")).isEmpty();
    }

    @Test
    void evictedConversationIsForgotten() {
        ConversationEvidenceCache cache = new ConversationEvidenceCache();
        cache.put("conv-1", EVIDENCE);
        cache.evict("conv-1");

        assertThat(cache.get("conv-1")).isEmpty();
    }
}
