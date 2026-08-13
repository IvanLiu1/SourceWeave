package com.ivanliu.ragproject.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanliu.ragproject.model.ChatWebSocketRequest;
import com.ivanliu.ragproject.config.WebSocketTicketHandshakeInterceptor;
import com.ivanliu.ragproject.service.ChatHandler;
import com.ivanliu.ragproject.service.ChatSessionRegistry;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String HEARTBEAT_PING = "__chat_ping__";
    private static final String HEARTBEAT_PONG = "__chat_pong__";
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String ENGLISH_LOCALE = "en-US";
    private final ChatHandler chatHandler;
    private final ChatSessionRegistry chatSessionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 内部指令令牌 - 可以从配置文件读取
    private static final String INTERNAL_CMD_TOKEN = "WSS_STOP_CMD_" + System.currentTimeMillis() % 1000000;

    public ChatWebSocketHandler(ChatHandler chatHandler, ChatSessionRegistry chatSessionRegistry) {
        this.chatHandler = chatHandler;
        this.chatSessionRegistry = chatSessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId;
        try {
            userId = getAuthenticatedUserId(session);
        } catch (Exception exception) {
            logger.warn("拒绝非法WebSocket连接，会话ID: {}, 原因: {}", session.getId(), exception.getMessage());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception closeException) {
                logger.error("关闭无效WebSocket连接失败: {}", closeException.getMessage(), closeException);
            }
            return;
        }

        chatSessionRegistry.registerSession(userId, session);
        logger.info("WebSocket连接已建立，用户ID: {}，会话ID: {}", userId, session.getId());

        // 发送会话ID到前端
        try {
            Map<String, String> connectionMessage = Map.of(
                "type", "connection",
                "sessionId", session.getId(),
                "message", "WebSocket连接已建立"
            );
            String jsonMessage = objectMapper.writeValueAsString(connectionMessage);
            session.sendMessage(new TextMessage(jsonMessage));
            logger.info("已发送会话ID到前端: sessionId={}", session.getId());
        } catch (Exception e) {
            logger.error("发送会话ID失败: {}", e.getMessage(), e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getAuthenticatedUserId(session);
        try {
            String payload = message.getPayload();

            // 心跳消息只用于保活连接，不进入聊天处理链路。
            if (HEARTBEAT_PING.equals(payload)) {
                session.sendMessage(new TextMessage(HEARTBEAT_PONG));
                return;
            }

            logger.info("接收到消息，用户ID: {}，会话ID: {}，消息长度: {}", 
                       userId, session.getId(), payload.length());
            
            // 检查是否是JSON格式的系统指令
            if (payload.trim().startsWith("{")) {
                ChatWebSocketRequest request = null;
                try {
                    request = objectMapper.readValue(payload, ChatWebSocketRequest.class);
                } catch (Exception jsonParseError) {
                    // JSON解析失败，当作普通文本消息处理
                    logger.debug("JSON解析失败，当作普通消息处理: {}", jsonParseError.getMessage());
                }

                if (request != null) {
                    String messageType = request.type();
                    
                    // 只有包含正确内部令牌的停止指令才处理
                    if ("stop".equals(messageType) && INTERNAL_CMD_TOKEN.equals(request.internalCommandToken())) {
                        // 处理停止指令
                        logger.info("收到有效的停止按钮指令，用户ID: {}，会话ID: {}", userId, session.getId());
                        chatHandler.stopResponse(userId, request.generationId());
                        return;
                    }

                    if ("chat".equals(messageType)) {
                        if (request.message() == null || request.message().isBlank()) {
                            throw new IllegalArgumentException("聊天消息不能为空");
                        }
                        chatHandler.processMessage(userId, request.message(), normalizeLocale(request.locale()), session);
                        return;
                    }
                    
                    // 其他JSON消息当作普通消息处理
                    logger.debug("收到JSON格式的聊天消息，当作普通消息处理");
                }
            }
                
            // 普通聊天消息处理（保持向下兼容）
            chatHandler.processMessage(userId, payload, DEFAULT_LOCALE, session);
            
        } catch (Exception e) {
            logger.error("处理消息出错，用户ID: {}，会话ID: {}，错误: {}", 
                        userId, session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = "unknown";
        try {
            userId = getAuthenticatedUserId(session);
            chatSessionRegistry.unregisterSession(userId, session);
        } catch (Exception e) {
            logger.debug("关闭连接时无法解析用户信息，会话ID: {}", session.getId());
        }

        if (CloseStatus.POLICY_VIOLATION.equals(status)) {
            logger.debug("WebSocket连接因策略校验失败被关闭，用户ID: {}，会话ID: {}，状态: {}",
                    userId, session.getId(), status);
        } else {
            logger.info("WebSocket连接已关闭，用户ID: {}，会话ID: {}，状态: {}",
                    userId, session.getId(), status);
        }

    }

    private String getAuthenticatedUserId(WebSocketSession session) {
        Object authenticatedUserId = session.getAttributes()
                .get(WebSocketTicketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        String userId = authenticatedUserId instanceof String value ? value : null;
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("WebSocket会话缺少已认证用户ID");
        }

        return userId;
    }

    private String normalizeLocale(String locale) {
        return ENGLISH_LOCALE.equals(locale) ? ENGLISH_LOCALE : DEFAULT_LOCALE;
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            logger.info("已发送错误消息到会话: {}, 错误: {}", session.getId(), errorMessage);
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取内部指令令牌 - 供前端调用
     */
    public static String getInternalCmdToken() {
        return INTERNAL_CMD_TOKEN;
    }
} 
