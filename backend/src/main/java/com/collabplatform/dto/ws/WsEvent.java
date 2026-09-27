package com.collabplatform.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Generic envelope for every real-time event broadcast over WebSocket.
 * `type` is one of the constants in WsEventType (e.g. TASK_STATUS_CHANGED),
 * `payload` is the relevant response DTO (TaskResponse, CommentResponse, ...).
 * The frontend switches on `type` to decide how to update its local state.
 */
@Getter
@AllArgsConstructor
public class WsEvent<T> {
    private String type;
    private T payload;
    private Instant timestamp;

    public static <T> WsEvent<T> of(String type, T payload) {
        return new WsEvent<>(type, payload, Instant.now());
    }
}
