package com.ivanliu.ragproject.controller;

import com.ivanliu.ragproject.service.AgentToolRegistry;
import com.ivanliu.ragproject.service.ChatGenerationStateService;
import com.ivanliu.ragproject.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 特征化测试——锁定重构前的线上 JSON 格式，重构后必须原样通过。
 *
 * <p>覆盖 {@link ChatController} 的 REST 端点，通过 standalone MockMvc 走真实的
 * Jackson 序列化，逐字段钉死响应信封（code / message / data 三键，且 data
 * 在无值时以显式 {@code "data":null} 出现在报文中，而不是缺失）。</p>
 */
class ChatControllerWebTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ChatGenerationStateService chatGenerationStateService;

    @Mock
    private AgentToolRegistry agentToolRegistry;

    @Captor
    private ArgumentCaptor<Map<String, Object>> toolArgumentsCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ChatController chatController =
                new ChatController(jwtUtils, chatGenerationStateService, agentToolRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(chatController)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private void stubValidToken() {
        when(jwtUtils.validateToken("valid-token")).thenReturn(true);
        when(jwtUtils.extractUserIdFromToken("valid-token")).thenReturn("user-1");
    }

    // ---------------------------------------------------------------
    // GET /api/v1/chat/generation/{generationId}
    // ---------------------------------------------------------------

    /**
     * 关键特征：Optional.empty() 时 data 键仍然存在且值为显式 null
     * （responseBody 用 LinkedHashMap，允许 null 值）。
     */
    @Test
    void getGeneration_whenNotFound_returnsExplicitNullData() throws Exception {
        stubValidToken();
        when(chatGenerationStateService.getGenerationForUser("gen-404", "user-1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/chat/generation/{generationId}", "gen-404")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取生成状态成功"))
                .andExpect(jsonPath("$.length()").value(3))
                // data 键必须存在且为显式 null（jsonPath 的 exists() 对 null 值会失败，故用字符串断言）
                .andExpect(content().string(containsString("\"data\":null")))
                // 钉死完整报文：LinkedHashMap 保证 code、message、data 的插入顺序
                .andExpect(content().string(
                        "{\"code\":200,\"message\":\"获取生成状态成功\",\"data\":null}"));
    }

    @Test
    void getGeneration_whenFound_returnsSnapshotAsData() throws Exception {
        stubValidToken();
        ChatGenerationStateService.GenerationSnapshot snapshot =
                new ChatGenerationStateService.GenerationSnapshot(
                        "gen-1",
                        "user-1",
                        "conv-1",
                        "什么是RAG？",
                        ChatGenerationStateService.GenerationStatus.COMPLETED,
                        "RAG 是检索增强生成。",
                        "2026-07-12 10:00:00",
                        "2026-07-12 10:00:05",
                        null,
                        Map.of("1", Map.of("docId", "doc-1")));
        when(chatGenerationStateService.getGenerationForUser("gen-1", "user-1"))
                .thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/api/v1/chat/generation/{generationId}", "gen-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取生成状态成功"))
                .andExpect(jsonPath("$.length()").value(3))
                // data 为记录序列化后的对象，共 10 个字段（含值为 null 的 errorMessage）
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data.generationId").value("gen-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.question").value("什么是RAG？"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content").value("RAG 是检索增强生成。"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-07-12 10:00:00"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-07-12 10:00:05"))
                .andExpect(jsonPath("$.data.referenceMappings['1'].docId").value("doc-1"))
                // 记录中的 null 字段按 Jackson 默认序列化为显式 null，不会缺失
                .andExpect(content().string(containsString("\"errorMessage\":null")));
    }

    @Test
    void getGeneration_whenTokenInvalid_returns401Envelope() throws Exception {
        when(jwtUtils.validateToken("bad-token")).thenReturn(false);

        mockMvc.perform(get("/api/v1/chat/generation/{generationId}", "gen-1")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().is(401))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid token"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(content().string(
                        "{\"code\":401,\"message\":\"Invalid token\",\"data\":null}"));
    }

    @Test
    void getGeneration_whenAuthorizationNotBearer_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/chat/generation/{generationId}", "gen-1")
                        .header("Authorization", "Basic abc"))
                .andExpect(status().is(401))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid token"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/chat/active-generation
    // ---------------------------------------------------------------

    @Test
    void getActiveGeneration_whenNoActive_returnsExplicitNullData() throws Exception {
        stubValidToken();
        when(chatGenerationStateService.getActiveGenerationForUser("user-1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/chat/active-generation")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取当前活动生成状态成功"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(content().string(
                        "{\"code\":200,\"message\":\"获取当前活动生成状态成功\",\"data\":null}"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/chat/feedback
    // ---------------------------------------------------------------

    @Test
    void submitFeedback_success_returnsToolResultDataAndBuildsReason() throws Exception {
        stubValidToken();
        AgentToolRegistry.ToolExecutionResult toolResult =
                new AgentToolRegistry.ToolExecutionResult(
                        "submit_feedback", true, "已记录", Map.of("feedbackId", "fb-1"));
        when(agentToolRegistry.executeTool(eq("submit_feedback"), anyMap(), eq("user-1")))
                .thenReturn(toolResult);

        mockMvc.perform(post("/api/v1/chat/feedback")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"up\",\"reason\":\"回答很好\","
                                + "\"conversationId\":\"conv-1\",\"generationId\":\"gen-1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("反馈已记录"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data.feedbackId").value("fb-1"));

        verify(agentToolRegistry)
                .executeTool(eq("submit_feedback"), toolArgumentsCaptor.capture(), eq("user-1"));
        Map<String, Object> arguments = toolArgumentsCaptor.getValue();
        assertEquals(2, arguments.size());
        assertEquals("up", arguments.get("rating"));
        assertEquals("回答很好; conversationId=conv-1; generationId=gen-1",
                arguments.get("reason"));
    }

    @Test
    void submitFeedback_ratingOnly_omitsReasonArgument() throws Exception {
        stubValidToken();
        AgentToolRegistry.ToolExecutionResult toolResult =
                new AgentToolRegistry.ToolExecutionResult(
                        "submit_feedback", true, "已记录", Map.of("feedbackId", "fb-2"));
        when(agentToolRegistry.executeTool(eq("submit_feedback"), anyMap(), eq("user-1")))
                .thenReturn(toolResult);

        mockMvc.perform(post("/api/v1/chat/feedback")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"down\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("反馈已记录"))
                .andExpect(jsonPath("$.length()").value(3));

        verify(agentToolRegistry)
                .executeTool(eq("submit_feedback"), toolArgumentsCaptor.capture(), eq("user-1"));
        Map<String, Object> arguments = toolArgumentsCaptor.getValue();
        assertEquals(1, arguments.size());
        assertEquals("down", arguments.get("rating"));
        assertFalse(arguments.containsKey("reason"));
    }

    @Test
    void submitFeedback_whenRatingMissing_returns400Envelope() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/v1/chat/feedback")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("rating 不能为空"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(content().string(
                        "{\"code\":400,\"message\":\"rating 不能为空\",\"data\":null}"));
    }

    @Test
    void submitFeedback_whenTokenInvalid_returns401Envelope() throws Exception {
        when(jwtUtils.validateToken("bad-token")).thenReturn(false);

        mockMvc.perform(post("/api/v1/chat/feedback")
                        .header("Authorization", "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"up\"}"))
                .andExpect(status().is(401))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid token"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(content().string(containsString("\"data\":null")));
    }
}
