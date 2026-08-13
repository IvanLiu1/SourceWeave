package com.ivanliu.ragproject.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTicketServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private WebSocketTicketService ticketService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ticketService = new WebSocketTicketService(redisTemplate, 30);
    }

    @Test
    void issueTicketStoresOpaqueTicketWithShortTtl() {
        WebSocketTicketService.IssuedTicket issuedTicket = ticketService.issueTicket("user-1");

        assertTrue(issuedTicket.value().matches("[0-9a-f]{32}"));
        assertEquals(30, issuedTicket.expiresInSeconds());
        verify(valueOperations).set(
                eq("websocket:ticket:" + issuedTicket.value()),
                eq("user-1"),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void consumeTicketAtomicallyDeletesIt() {
        String ticket = "0123456789abcdef0123456789abcdef";
        when(valueOperations.getAndDelete("websocket:ticket:" + ticket)).thenReturn("user-1");

        assertEquals(Optional.of("user-1"), ticketService.consumeTicket(ticket));
        verify(valueOperations).getAndDelete("websocket:ticket:" + ticket);
    }

    @Test
    void consumeTicketRejectsMalformedValueWithoutRedisLookup() {
        assertTrue(ticketService.consumeTicket("not-a-ticket").isEmpty());
        verify(valueOperations, never()).getAndDelete(anyString());
    }
}
