package com.manager.websocket;

import com.manager.config.GeminiConfig;
import com.manager.config.OpenAiConfig;
import com.manager.dto.AiRuleDto;
import com.manager.service.AiRulesService;
import com.manager.service.AiService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveAudioWebSocketHandlerTest {

    @Test
    void legacyMultiMegabyteRuleIsCappedBeforeOpeningLiveSession() throws Exception {
        AiRulesService rulesService = mock(AiRulesService.class);
        String legacyRule = "x".repeat(6_166_000) + "RULE_END_MARKER";
        when(rulesService.getActiveRulesForDepartment("dept-1")).thenReturn(List.of(
                AiRuleDto.builder().title("Legacy uploaded document").content(legacyRule).build(),
                AiRuleDto.builder().title("Short rule").content("Answer in Uzbek.").build()));

        LiveAudioWebSocketHandler handler = new LiveAudioWebSocketHandler(
                mock(GeminiConfig.class), mock(OpenAiConfig.class),
                rulesService, mock(AiService.class));

        String instruction = handler.buildSystemInstruction("dept-1");

        assertThat(instruction)
                .hasSizeLessThanOrEqualTo(LiveAudioWebSocketHandler.MAX_SYSTEM_INSTRUCTION_CHARS)
                .contains(LiveAudioWebSocketHandler.GOLDEN_RULES.trim())
                .contains("Legacy uploaded document")
                .contains("Short rule: Answer in Uzbek.")
                .doesNotContain("RULE_END_MARKER");
    }
}
