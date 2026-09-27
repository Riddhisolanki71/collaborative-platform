package com.collabplatform.service;

import com.collabplatform.dto.notification.NotificationResponse;
import com.collabplatform.dto.ws.WsEvent;
import com.collabplatform.event.WsBroadcastEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper that centralizes the two destination "shapes" used by the app:
 *
 *  - /topic/projects/{projectId}            -> everyone currently viewing that project
 *  - /topic/users/{userId}/notifications    -> a single user's personal notification feed
 *
 * Called by the *Service classes (TaskService, CommentService, etc.) right after
 * a REST write succeeds - never for reads. Normal CRUD still goes through REST;
 * WebSockets here are purely for pushing "something changed" updates to
 * already-connected clients.
 *
 * This does NOT send over STOMP directly. It publishes a WsBroadcastEvent via
 * Spring's ApplicationEventPublisher, which com.collabplatform.event.WsBroadcastListener
 * picks up and sends only after the enclosing transaction commits - see that
 * class's Javadoc for why. Callers don't need to know or care about that; the
 * method signatures here are unchanged from a naive "send immediately" version.
 */
@Service
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public <T> void publishToProject(Long projectId, String eventType, T payload) {
        applicationEventPublisher.publishEvent(
                new WsBroadcastEvent(this, "/topic/projects/" + projectId, WsEvent.of(eventType, payload)));
    }

    public void publishNotification(Long userId, NotificationResponse notification) {
        applicationEventPublisher.publishEvent(new WsBroadcastEvent(
                this, "/topic/users/" + userId + "/notifications", WsEvent.of("NOTIFICATION_CREATED", notification)));
    }
}
