package com.ivanliu.ragproject.config;

import com.ivanliu.ragproject.service.WebSocketTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
public class WebSocketTicketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTicketHandshakeInterceptor.class);

    private final WebSocketTicketService ticketService;

    public WebSocketTicketHandshakeInterceptor(WebSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Optional<String> ticket = extractTicket(request.getURI());
        Optional<String> userId = ticket.flatMap(ticketService::consumeTicket);
        if (userId.isEmpty()) {
            logger.warn("拒绝缺失、过期或已使用的WebSocket票据");
            return false;
        }

        attributes.put(USER_ID_ATTRIBUTE, userId.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op.
    }

    private Optional<String> extractTicket(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return Optional.empty();
        }

        String path = uri.getPath();
        int separatorIndex = path.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex == path.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(path.substring(separatorIndex + 1));
    }
}
