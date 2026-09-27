package com.collabplatform.security;

import com.collabplatform.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a STOMP session is allowed to subscribe to a given
 * destination. Pulled out of WebSocketConfig's channel interceptor so the
 * authorization rule itself can be unit tested directly (see
 * WsSubscriptionAuthorizerTest) without needing a running WebSocket server.
 *
 * Background: the CONNECT frame is authenticated (see WebSocketConfig), but
 * authenticating the connection is not the same as authorizing a specific
 * subscription - the simple broker will deliver messages to any destination
 * a connected session subscribes to. Without this check, any client that can
 * open a WebSocket connection to /ws - authenticated or not - could subscribe
 * to any project's topic (or another user's personal notification topic) by
 * guessing/incrementing an id, and silently receive that data. This class is
 * what closes that gap.
 *
 * Rules:
 *  - /topic/projects/{projectId}          -> caller must be a member of that project
 *  - /topic/users/{userId}/notifications  -> caller must be that user
 *  - anything else under /topic/**        -> denied (the app never publishes there)
 */
@Component
@RequiredArgsConstructor
public class WsSubscriptionAuthorizer {

    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/topic/projects/(\\d+)$");
    private static final Pattern NOTIFICATIONS_TOPIC = Pattern.compile("^/topic/users/(\\d+)/notifications$");

    private final ProjectMemberRepository projectMemberRepository;

    /**
     * @param destination   the STOMP destination being subscribed to
     * @param callerUserId  the authenticated caller's user id, or null if the session never
     *                      presented a valid JWT on CONNECT
     * @throws MessagingException if the subscription is not allowed
     */
    public void authorize(String destination, Long callerUserId) {
        if (destination == null) {
            throw new MessagingException("Subscription is missing a destination");
        }

        Matcher projectMatch = PROJECT_TOPIC.matcher(destination);
        if (projectMatch.matches()) {
            Long projectId = Long.valueOf(projectMatch.group(1));
            if (callerUserId == null || !projectMemberRepository.existsByProjectIdAndUserId(projectId, callerUserId)) {
                throw new MessagingException("Not authorized to subscribe to project " + projectId);
            }
            return;
        }

        Matcher notificationsMatch = NOTIFICATIONS_TOPIC.matcher(destination);
        if (notificationsMatch.matches()) {
            Long targetUserId = Long.valueOf(notificationsMatch.group(1));
            if (callerUserId == null || !callerUserId.equals(targetUserId)) {
                throw new MessagingException("Not authorized to subscribe to another user's notifications");
            }
            return;
        }

        // Deny by default: the app only ever publishes to the two destination shapes
        // above, so an unrecognized /topic/** destination has no legitimate subscriber.
        if (destination.startsWith("/topic/")) {
            throw new MessagingException("Unknown subscription destination");
        }
    }
}
