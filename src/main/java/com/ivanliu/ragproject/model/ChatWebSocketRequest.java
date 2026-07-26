package com.ivanliu.ragproject.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Client-to-server WebSocket payload for chat messages and internal commands. */
public record ChatWebSocketRequest(
        String type,
        String message,
        String locale,
        String generationId,
        @JsonProperty("_internal_cmd_token") String internalCommandToken
) {
}
