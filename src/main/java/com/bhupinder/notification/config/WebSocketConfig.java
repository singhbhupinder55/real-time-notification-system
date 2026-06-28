package com.bhupinder.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration.
 *
 * Phase 1: uses Spring's built-in SIMPLE BROKER (in-memory, per-instance).
 * This is intentional and temporary - it proves the WebSocket/STOMP mechanics
 * work end-to-end before we introduce Redis as an external broker relay in Phase 2.
 *
 * The simple broker's core limitation - it only knows about clients connected
 * to THIS instance - is exactly the problem Redis Pub/Sub solves later.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Clients connect here, e.g. ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // fine for local dev/demo; would be locked down for a real prod deploy
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations clients can SUBSCRIBE to, e.g. /topics/user-1
        registry.enableSimpleBroker("/topics");

        // Prefix for destinations the client sends TO the server (not used yet -
        // we publish via REST in this design, not via client-sent STOMP messages,
        // but this is the conventional setup so it's here for completeness)
        registry.setApplicationDestinationPrefixes("/app");
    }
}
