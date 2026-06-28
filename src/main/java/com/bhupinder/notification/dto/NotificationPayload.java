package com.bhupinder.notification.dto;

/**
 * What actually gets pushed down the WebSocket to subscribed clients.
 *
 * Distinct from NotificationRequest because the server adds fields the client
 * didn't provide: a publish timestamp (for latency measurement, Phase 3+) and
 * the topic itself (so a client subscribed to multiple topics can tell them apart).
 *
 * publishedAtEpochMillis is the moment THIS instance handed the message off for
 * delivery - in Phase 2, once Redis is in the loop, this timestamp should be set
 * at the ORIGINAL publish point, not re-stamped by each instance that relays it,
 * otherwise latency measurements would be meaningless.
 */
public record NotificationPayload(
        String topic,
        String message,
        long publishedAtEpochMillis
) {
}
