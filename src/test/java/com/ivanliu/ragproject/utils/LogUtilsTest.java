package com.ivanliu.ragproject.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogUtilsTest {

    @Test
    void redactsBackendWebSocketTicketPath() {
        assertEquals(
                "/chat/{redacted}",
                LogUtils.sanitizeRequestPath("/chat/0123456789abcdef0123456789abcdef")
        );
    }

    @Test
    void redactsLegacyJwtAndKeepsQueryMetadata() {
        assertEquals(
                "/chat/{redacted}?source=reconnect",
                LogUtils.sanitizeRequestPath("/chat/eyJhbGciOiJIUzI1NiJ9.payload.signature?source=reconnect")
        );
    }

    @Test
    void redactsFrontendProxyWebSocketPath() {
        assertEquals(
                "/proxy-ws/chat/{redacted}",
                LogUtils.sanitizeRequestPath("/proxy-ws/chat/0123456789abcdef0123456789abcdef")
        );
    }

    @Test
    void leavesChatRestEndpointAndUnrelatedPathsUnchanged() {
        assertEquals(
                "/api/v1/chat/websocket-ticket",
                LogUtils.sanitizeRequestPath("/api/v1/chat/websocket-ticket")
        );
        assertEquals("/api/v1/users", LogUtils.sanitizeRequestPath("/api/v1/users"));
        assertNull(LogUtils.sanitizeRequestPath(null));
    }
}
