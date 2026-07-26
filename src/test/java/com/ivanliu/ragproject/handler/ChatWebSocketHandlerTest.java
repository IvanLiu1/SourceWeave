package com.ivanliu.ragproject.handler;

import com.ivanliu.ragproject.service.ChatHandler;
import com.ivanliu.ragproject.service.ChatSessionRegistry;
import com.ivanliu.ragproject.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    private ChatHandler chatHandler;
    private WebSocketSession session;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        chatHandler = mock(ChatHandler.class);
        JwtUtils jwtUtils = mock(JwtUtils.class);
        session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/chat/test-token"));
        when(session.getId()).thenReturn("session-1");
        when(jwtUtils.extractUserIdFromToken("test-token")).thenReturn("42");
        handler = new ChatWebSocketHandler(chatHandler, jwtUtils, mock(ChatSessionRegistry.class));
    }

    @Test
    void parsesEnglishChatFrame() {
        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"chat\",\"message\":\"Hello\",\"locale\":\"en-US\"}"));

        verify(chatHandler).processMessage("42", "Hello", "en-US", session);
    }

    @Test
    void fallsBackToChineseForMissingOrInvalidLocale() {
        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"chat\",\"message\":\"missing locale\"}"));
        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"chat\",\"message\":\"invalid locale\",\"locale\":\"fr-FR\"}"));

        verify(chatHandler).processMessage("42", "missing locale", "zh-CN", session);
        verify(chatHandler).processMessage("42", "invalid locale", "zh-CN", session);
    }

    @Test
    void keepsPlainTextClientCompatibility() {
        handler.handleTextMessage(session, new TextMessage("旧客户端消息"));

        verify(chatHandler).processMessage("42", "旧客户端消息", "zh-CN", session);
    }

    @Test
    void rejectsBlankStructuredChatWithoutTreatingFrameAsPlainText() throws Exception {
        String payload = "{\"type\":\"chat\",\"message\":\"  \",\"locale\":\"en-US\"}";

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(chatHandler, never()).processMessage("42", payload, "zh-CN", session);
        verify(session).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }

    @Test
    void keepsStopCommandCompatibility() {
        String stopPayload = "{\"type\":\"stop\",\"generationId\":\"generation-1\","
                + "\"_internal_cmd_token\":\"" + ChatWebSocketHandler.getInternalCmdToken() + "\"}";

        handler.handleTextMessage(session, new TextMessage(stopPayload));

        verify(chatHandler).stopResponse("42", "generation-1");
        verify(chatHandler, never()).processMessage("42", stopPayload, "zh-CN", session);
    }
}
