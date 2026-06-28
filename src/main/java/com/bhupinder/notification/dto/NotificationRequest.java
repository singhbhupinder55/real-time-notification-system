package com.bhupinder.notification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/notify.
 *
 * topic: the STOMP destination to publish to, e.g. "user-1" or "announcements".
 *        The controller will prefix this with "/topics/" when broadcasting.
 * message: the payload to deliver to subscribers - kept as a plain string for now;
 *          can evolve into a richer payload type later without changing the wire contract much.
 */
public record NotificationRequest(
        @NotBlank(message = "topic must not be blank")
        String topic,

        @NotBlank(message = "message must not be blank")
        String message
) {
}
