package com.collabplatform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Internal application event carrying one outbound STOMP message. Published by
 * WebSocketEventPublisher and picked up by WsBroadcastListener, which is the
 * only place that actually touches SimpMessagingTemplate.
 *
 * The indirection exists so the send can be deferred to *after* the enclosing
 * database transaction commits (see WsBroadcastListener) instead of firing
 * synchronously in the middle of a service method - see that class's Javadoc
 * for why that ordering matters.
 */
@Getter
public class WsBroadcastEvent extends ApplicationEvent {

    private final String destination;
    private final Object message;

    public WsBroadcastEvent(Object source, String destination, Object message) {
        super(source);
        this.destination = destination;
        this.message = message;
    }
}
