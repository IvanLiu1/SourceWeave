package com.ivanliu.ragproject.controller;

import com.ivanliu.ragproject.exception.CustomException;
import com.ivanliu.ragproject.service.ConversationService;
import com.ivanliu.ragproject.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 特征化测试——锁定重构前的线上 JSON 格式,重构后必须原样通过。
 *
 * <p>覆盖 {@link ConversationSessionController} 的五个端点
 * (GET /api/v1/users/conversations, POST /api/v1/users/conversations,
 * PUT /{conversationId}/archive, PUT /{conversationId}/switch,
 * PUT /{conversationId}/unarchive),逐一钉死:</p>
 * <ul>
 *   <li>HTTP 状态码;</li>
 *   <li>顶层字段的精确集合(成功且带 data 时为 {code, message, data} 共 3 个字段;
 *       归档/切换/取消归档成功以及所有错误响应仅有 {code, message} 共 2 个字段,
 *       data 字段完全缺失,而非显式 null);</li>
 *   <li>message 中文文案逐字符一致;</li>
 *   <li>无效 token 走 CustomException 分支返回 401;
 *       服务层 CustomException 透传其自带状态码;
 *       其他异常统一 500,message 前缀为 "服务器内部错误: "。</li>
 * </ul>
 */
class ConversationSessionControllerWebTest {

    private static final String AUTH_HEADER = "Bearer good-token";
    private static final String RAW_TOKEN = "good-token";

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ConversationService conversationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ConversationSessionController controller = new ConversationSessionController();
        ReflectionTestUtils.setField(controller, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(controller, "conversationService", conversationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ---------- GET /api/v1/users/conversations ----------

    @Test
    void listSessions_success_returns200EnvelopeWithDataArray() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");
        when(jwtUtils.extractUserIdFromToken(RAW_TOKEN)).thenReturn("1");

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("conversation_id", "conv-1");
        session.put("summary", "第一段对话");
        session.put("is_current", true);
        when(conversationService.getConversationSessions(1L)).thenReturn(List.of(session));

        mockMvc.perform(get("/api/v1/users/conversations")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // 顶层字段集合恰为 {code, message, data}
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取对话列表成功"))
                // data 为服务层返回的会话列表原样透传
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].length()").value(3))
                .andExpect(jsonPath("$.data[0].conversation_id").value("conv-1"))
                .andExpect(jsonPath("$.data[0].summary").value("第一段对话"))
                .andExpect(jsonPath("$.data[0].is_current").value(true));
    }

    @Test
    void listSessions_invalidToken_nullUsername_returns401WithoutData() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn(null);

        mockMvc.perform(get("/api/v1/users/conversations")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("无效的token"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void listSessions_serviceThrowsRuntimeException_returns500WithPrefixedMessage() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");
        when(jwtUtils.extractUserIdFromToken(RAW_TOKEN)).thenReturn("1");
        when(conversationService.getConversationSessions(1L)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/users/conversations")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误: boom"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---------- POST /api/v1/users/conversations ----------

    @Test
    void createSession_success_returns200EnvelopeWithDataObject() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");
        when(jwtUtils.extractUserIdFromToken(RAW_TOKEN)).thenReturn("42");

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("conversation_id", "conv-new");
        created.put("created_at", "2026-07-10 12:00:00");
        when(conversationService.createConversationSession(42L)).thenReturn(created);

        mockMvc.perform(post("/api/v1/users/conversations")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("创建新对话成功"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.conversation_id").value("conv-new"))
                .andExpect(jsonPath("$.data.created_at").value("2026-07-10 12:00:00"));
    }

    @Test
    void createSession_invalidToken_emptyUsername_returns401WithoutData() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("");

        mockMvc.perform(post("/api/v1/users/conversations")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("无效的token"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---------- PUT /api/v1/users/conversations/{id}/archive ----------

    @Test
    void archiveSession_success_returns200WithoutDataField() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");

        mockMvc.perform(put("/api/v1/users/conversations/conv-1/archive")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                // 成功响应也只有 {code, message} 两个字段,没有 data
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("归档成功"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(conversationService).archiveConversationSession("conv-1");
    }

    @Test
    void archiveSession_serviceThrowsRuntimeException_returns500WithPrefixedMessage() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");
        doThrow(new RuntimeException("db down"))
                .when(conversationService).archiveConversationSession("conv-1");

        mockMvc.perform(put("/api/v1/users/conversations/conv-1/archive")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误: db down"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---------- PUT /api/v1/users/conversations/{id}/switch ----------

    @Test
    void switchSession_success_returns200WithoutDataField() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");
        when(jwtUtils.extractUserIdFromToken(RAW_TOKEN)).thenReturn("7");

        mockMvc.perform(put("/api/v1/users/conversations/conv-9/switch")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("切换对话成功"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(conversationService).switchCurrentConversation(7L, "conv-9");
    }

    @Test
    void switchSession_serviceThrowsCustomException_statusAndMessagePassThrough() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");
        when(jwtUtils.extractUserIdFromToken(RAW_TOKEN)).thenReturn("7");
        doThrow(new CustomException("无权限访问该对话", HttpStatus.FORBIDDEN))
                .when(conversationService).switchCurrentConversation(7L, "conv-9");

        mockMvc.perform(put("/api/v1/users/conversations/conv-9/switch")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限访问该对话"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---------- PUT /api/v1/users/conversations/{id}/unarchive ----------

    @Test
    void unarchiveSession_success_returns200WithoutDataField() throws Exception {
        when(jwtUtils.extractUsernameFromToken(RAW_TOKEN)).thenReturn("ivan");

        mockMvc.perform(put("/api/v1/users/conversations/conv-1/unarchive")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("取消归档成功"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(conversationService).unarchiveConversationSession("conv-1");
    }
}
