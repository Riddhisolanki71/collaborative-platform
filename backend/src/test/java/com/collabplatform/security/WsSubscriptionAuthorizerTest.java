package com.collabplatform.security;

import com.collabplatform.repository.ProjectMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the WebSocket SUBSCRIBE-time authorization rule.
 *
 * These exist specifically to cover a real gap found during review: the STOMP
 * CONNECT frame was authenticated, but nothing checked that a subscribing
 * session was actually allowed to see the project or user destination it
 * asked for - meaning any connected client (authenticated as *someone*, or
 * not authenticated at all) could subscribe to any project's live event feed
 * or another user's personal notifications just by knowing/guessing an id.
 * See WsSubscriptionAuthorizer / WebSocketConfig for the fix.
 */
@ExtendWith(MockitoExtension.class)
class WsSubscriptionAuthorizerTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private WsSubscriptionAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new WsSubscriptionAuthorizer(projectMemberRepository);
    }

    @Test
    void allowsAProjectMemberToSubscribeToThatProjectsTopic() {
        when(projectMemberRepository.existsByProjectIdAndUserId(42L, 7L)).thenReturn(true);

        assertThatCode(() -> authorizer.authorize("/topic/projects/42", 7L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsANonMemberSubscribingToAProjectsTopic() {
        when(projectMemberRepository.existsByProjectIdAndUserId(42L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> authorizer.authorize("/topic/projects/42", 7L))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsAnUnauthenticatedSessionSubscribingToAProjectsTopic() {
        assertThatThrownBy(() -> authorizer.authorize("/topic/projects/42", null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void allowsAUserToSubscribeToTheirOwnNotifications() {
        assertThatCode(() -> authorizer.authorize("/topic/users/7/notifications", 7L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAUserSubscribingToSomeoneElsesNotifications() {
        assertThatThrownBy(() -> authorizer.authorize("/topic/users/7/notifications", 99L))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsAnUnauthenticatedSessionSubscribingToAnyNotificationsTopic() {
        assertThatThrownBy(() -> authorizer.authorize("/topic/users/7/notifications", null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsAnUnrecognizedTopicDestination() {
        assertThatThrownBy(() -> authorizer.authorize("/topic/something-else/1", 7L))
                .isInstanceOf(MessagingException.class);
    }
}
