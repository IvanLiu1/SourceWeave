package com.ivanliu.ragproject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanliu.ragproject.client.ProviderWebClientFactory;
import com.ivanliu.ragproject.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LlmProviderRouterLocaleTest {

    private LlmProviderRouter router;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getPrompt().setRules("Base system rule");
        aiProperties.getPrompt().setRefStart("<<REF>>");
        aiProperties.getPrompt().setRefEnd("<<END>>");
        aiProperties.getPrompt().setNoResultText("No preset retrieval results");

        router = new LlmProviderRouter(
                aiProperties,
                mock(RateLimitService.class),
                mock(UsageQuotaService.class),
                mock(ModelProviderConfigService.class),
                new ObjectMapper(),
                mock(ProviderWebClientFactory.class));
    }

    @Test
    void injectsEnglishAnswerAndCitationContract() {
        String systemPrompt = systemPrompt(router.buildReActMessages("question", "", List.of(), "", "en-US"));

        assertTrue(systemPrompt.contains("Answer only in English"));
        assertTrue(systemPrompt.contains("Source #N"));
        assertTrue(systemPrompt.contains("Page X"));
        assertFalse(systemPrompt.contains("仅用简体中文作答"));
    }

    @Test
    void defaultsExistingOverloadToChineseContract() {
        String systemPrompt = systemPrompt(router.buildReActMessages("问题", "", List.of()));

        assertTrue(systemPrompt.contains("仅用简体中文作答"));
        assertTrue(systemPrompt.contains("来源#N"));
        assertTrue(systemPrompt.contains("第X页"));
    }

    @Test
    void fallsBackToChineseForUnsupportedLocale() {
        String systemPrompt = systemPrompt(router.buildReActMessages("question", "", List.of(), "", "fr-FR"));

        assertTrue(systemPrompt.contains("仅用简体中文作答"));
        assertFalse(systemPrompt.contains("Answer only in English"));
    }

    private String systemPrompt(List<Map<String, Object>> messages) {
        return (String) messages.get(0).get("content");
    }
}
