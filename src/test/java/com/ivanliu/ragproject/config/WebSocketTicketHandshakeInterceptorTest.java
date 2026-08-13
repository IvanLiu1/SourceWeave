package com.ivanliu.ragproject.config;

import com.ivanliu.ragproject.service.WebSocketTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketTicketHandshakeInterceptorTest {

    private WebSocketTicketService ticketService;
    private WebSocketTicketHandshakeInterceptor interceptor;
    private ServerHttpRequest request;
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @BeforeEach
    void setUp() {
        ticketService = mock(WebSocketTicketService.class);
        interceptor = new WebSocketTicketHandshakeInterceptor(ticketService);
        request = mock(ServerHttpRequest.class);
    }

    @Test
    void acceptsTicketAndStoresAuthenticatedUserId() {
        String ticket = "0123456789abcdef0123456789abcdef";
        Map<String, Object> attributes = new HashMap<>();
        when(request.getURI()).thenReturn(URI.create("ws://localhost/chat/" + ticket));
        when(ticketService.consumeTicket(ticket)).thenReturn(Optional.of("user-1"));

        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));
        assertEquals("user-1", attributes.get(WebSocketTicketHandshakeInterceptor.USER_ID_ATTRIBUTE));
    }

    @Test
    void rejectsExpiredOrPreviouslyUsedTicket() {
        String ticket = "0123456789abcdef0123456789abcdef";
        when(request.getURI()).thenReturn(URI.create("ws://localhost/chat/" + ticket));
        when(ticketService.consumeTicket(ticket)).thenReturn(Optional.empty());

        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }
}
