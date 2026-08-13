package com.ivanliu.ragproject.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Issues short-lived, single-use tickets for authenticating browser WebSocket handshakes.
 * The access JWT remains in the authenticated HTTP request and is never placed in a URL.
 */
@Service
public class WebSocketTicketService {

    private static final String TICKET_PREFIX = "websocket:ticket:";
    private static final Pattern TICKET_PATTERN = Pattern.compile("[0-9a-f]{32}");

    private final StringRedisTemplate redisTemplate;
    private final long ticketTtlSeconds;

    public WebSocketTicketService(
            StringRedisTemplate redisTemplate,
            @Value("${security.websocket-ticket-ttl-seconds:30}") long ticketTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ticketTtlSeconds = Math.max(1, ticketTtlSeconds);
    }

    public IssuedTicket issueTicket(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("WebSocket ticket requires a user ID");
        }

        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                TICKET_PREFIX + ticket,
                userId,
                Duration.ofSeconds(ticketTtlSeconds)
        );
        return new IssuedTicket(ticket, ticketTtlSeconds);
    }

    public Optional<String> consumeTicket(String ticket) {
        if (ticket == null || !TICKET_PATTERN.matcher(ticket).matches()) {
            return Optional.empty();
        }

        String userId = redisTemplate.opsForValue().getAndDelete(TICKET_PREFIX + ticket);
        return Optional.ofNullable(userId).filter(value -> !value.isBlank());
    }

    public record IssuedTicket(String value, long expiresInSeconds) {
    }
}
