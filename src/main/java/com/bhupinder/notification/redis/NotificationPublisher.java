package com.bhupinder.notification.redis;

import com.bhupinder.notification.config.NotificationProperties;
import com.bhupinder.notification.config.RedisConfig;
import com.bhupinder.notification.dto.NotificationPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes a NotificationPayload to the shared Redis channel.
 *
 * This is the Phase 2 replacement for the controller calling SimpMessagingTemplate
 * directly. The controller no longer pushes to local WebSocket clients at all -
 * it ONLY publishes to Redis. Local delivery now happens exclusively through
 * NotificationRedisListener, even for the instance that received the original
 * POST request. This is intentional: it means there is exactly ONE code path
 * that delivers to STOMP clients, regardless of which instance originated the
 * publish. One path = one thing to test, one thing to reason about.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationProperties notificationProperties;

    public void publish(NotificationPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("instanceId={} publishing to Redis channel={} payload={}",
                    notificationProperties.instanceId(), RedisConfig.NOTIFICATIONS_CHANNEL, payload);
            redisTemplate.convertAndSend(RedisConfig.NOTIFICATIONS_CHANNEL, json);
        } catch (JsonProcessingException e) {
            // Serialization failure on our own DTO would indicate a programming error,
            // not a runtime/network condition - fail loudly rather than swallow it.
            throw new IllegalStateException("Failed to serialize NotificationPayload for Redis publish", e);
        }
    }
}
