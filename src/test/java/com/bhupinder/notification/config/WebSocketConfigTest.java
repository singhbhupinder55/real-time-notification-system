package com.bhupinder.notification.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies WebSocketConfig calls the registry APIs with the EXACT paths our
 * test client and browser test client depend on ("/ws", "/topics", "/app").
 * A typo in any of these (e.g. "/topic" instead of "/topics") would silently
 * break every client - this test exists to catch that at the configuration
 * level, in milliseconds, rather than only discovering it via a failed
 * integration test or manual click-through.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    private final WebSocketConfig config = new WebSocketConfig();

    @Test
    void registersStompEndpointAtSlashWs() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("*")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).setAllowedOriginPatterns("*");
    }

    @Test
    void enablesSimpleBrokerOnTopicsPrefix() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topics");
    }

    @Test
    void setsApplicationDestinationPrefixToApp() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).setApplicationDestinationPrefixes("/app");
    }
}
