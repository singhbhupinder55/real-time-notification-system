package com.bhupinder.notification.redis;

import com.bhupinder.notification.config.NotificationProperties;
import com.bhupinder.notification.dto.NotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Receives messages from the Redis "notifications" channel and forwards them to
 * THIS instance's locally-connected STOMP subscribers.
 *
 * This bean exists on every instance. Every instance subscribes to the same Redis
 * channel on startup (wired in RedisConfig), so every instance's onMessage() fires
 * for every publish - regardless of which instance originally received the
 * POST /api/notify request. Each instance only forwards to ITS OWN local clients;
 * SimpMessagingTemplate has no visibility into other instances' connections, which
 * is exactly why this fan-out-via-Redis design is necessary in the first place.
 *
 * Method name "onMessage" is referenced by name in RedisConfig's MessageListenerAdapter -
 * if you rename this method, update that wiring too.
 *
 * Phase 3: logs server-side delivery latency (publish timestamp -> the moment THIS
 * instance is about to hand the message to its local STOMP clients) tagged with
 * instance ID. This is the number we trust for the README's measured-latency claim -
 * unlike the client-side browser measurement, it isn't affected by browser event-loop
 * scheduling or render timing, and once multiple instances exist, the instance ID tag
 * lets us see which instance is relaying for which client.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRedisListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationProperties notificationProperties;

    public void onMessage(String json) {
        try {
            NotificationPayload payload = objectMapper.readValue(json, NotificationPayload.class);
            String destination = "/topics/" + payload.topic();

            long deliveryLatencyMillis = System.currentTimeMillis() - payload.publishedAtEpochMillis();

            log.info("instanceId={} delivering destination={} serverLatencyMs={} payload={}",
                    notificationProperties.instanceId(), destination, deliveryLatencyMillis, payload);

            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            // A malformed message on the shared channel shouldn't crash the listener
            // thread - that would silently stop this instance from receiving ALL
            // future notifications. Log and move on.
            log.error("instanceId={} failed to process Redis message: {}", notificationProperties.instanceId(), json, e);
        }
    }
}
