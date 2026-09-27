package com.collabplatform.config;

import com.collabplatform.security.JwtService;
import com.collabplatform.security.WsSubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import java.security.Principal;

/**
 * WebSocket / STOMP wiring.
 *
 * Endpoint:  /ws                (SockJS fallback enabled)
 * Broker:    /topic/**          (server -> client broadcast destinations)
 * App prefix: /app/**           (not used in the base version - all real-time
 *                                 events are server-initiated after REST calls,
 *                                 see WebSocketEventPublisher)
 *
 * Authentication: clients authenticate the STOMP CONNECT frame with a
 * "Authorization: Bearer <jwt>" STOMP header (set via the JS client's
 * `connectHeaders`). The interceptor below validates it and attaches a
 * Principal to the session.
 *
 * Authorization: every SUBSCRIBE frame is additionally checked by
 * WsSubscriptionAuthorizer, which verifies the caller is actually allowed to
 * see the destination they're subscribing to (project membership for
 * /topic/projects/{id}, "is this your own feed" for
 * /topic/users/{id}/notifications) - see that class's Javadoc for why this
 * check exists separately from authentication.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final WsSubscriptionAuthorizer subscriptionAuthorizer;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    subscriptionAuthorizer.authorize(accessor.getDestination(), callerUserId(accessor.getUser()));
                }

                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String email = jwtService.extractEmail(token);
                if (jwtService.isTokenValid(token, email)) {
                    Long userId = jwtService.extractUserId(token);
                    accessor.setUser(new StompPrincipal(String.valueOf(userId), email));
                }
            } catch (Exception ignored) {
                // leave the session unauthenticated; any SUBSCRIBE it attempts to a
                // protected destination will then be rejected by subscriptionAuthorizer.
            }
        }
    }

    private Long callerUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Minimal Principal implementation carrying the authenticated user's id. */
    public record StompPrincipal(String userId, String email) implements Principal {
        @Override
        public String getName() {
            return userId;
        }
    }
}
