package com.ivanliu.ragproject.service;

import com.ivanliu.ragproject.client.DeepSeekClient;
import com.ivanliu.ragproject.client.RerankClient;
import com.ivanliu.ragproject.entity.SearchResult;
import com.ivanliu.ragproject.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    private HybridSearchService hybridSearchService;
    private RerankClient rerankClient;
    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        hybridSearchService = mock(HybridSearchService.class);
        rerankClient = mock(RerankClient.class);
        registry = new AgentToolRegistry(
                hybridSearchService,
                mock(DeepSeekClient.class),
                mock(StringRedisTemplate.class),
                null,
                mock(FileUploadRepository.class),
                rerankClient
        );
        ReflectionTestUtils.setField(registry, "hardRejectThreshold", 0.4d);
        ReflectionTestUtils.setField(registry, "lowScoreWarnThreshold", 0.5d);
    }

    @Test
    void withholdsCalibratedLowConfidenceResultsFromTheAnswerModel() {
        when(rerankClient.isEnabled()).thenReturn(true);
        when(hybridSearchService.searchWithPermission("query", "user", 5))
                .thenReturn(List.of(result(0.39d, "HYBRID_RERANK"), result(0.20d, "HYBRID_RERANK")));

        AgentToolRegistry.ToolExecutionResult result = registry.executeTool(
                "search_knowledge",
                Map.of("query", "query", "topK", 5),
                "user"
        );

        assertTrue(result.content().startsWith("LOW_CONFIDENCE:"));
        assertEquals(List.of(), result.data().get("results"));
        assertEquals(2, result.data().get("discardedResultCount"));
    }

    @Test
    void neverAppliesRerankThresholdToUncalibratedFallbackScores() {
        when(rerankClient.isEnabled()).thenReturn(true);
        List<SearchResult> fallback = List.of(result(0.10d, "HYBRID"));
        when(hybridSearchService.searchWithPermission("query", "user", 5)).thenReturn(fallback);

        AgentToolRegistry.ToolExecutionResult result = registry.executeTool(
                "search_knowledge",
                Map.of("query", "query"),
                "user"
        );

        assertFalse(result.content().startsWith("LOW_CONFIDENCE:"));
        assertEquals(fallback, result.data().get("results"));
        assertEquals(false, result.data().get("scoreCalibrated"));
    }

    private SearchResult result(double score, String retrievalMode) {
        return new SearchResult(
                "file",
                1,
                "text",
                score,
                "user",
                "org",
                true,
                "file.txt",
                null,
                null,
                retrievalMode,
                "text"
        );
    }
}
