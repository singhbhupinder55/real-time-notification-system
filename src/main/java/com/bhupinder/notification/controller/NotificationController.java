package com.bhupinder.notification.controller;

import com.bhupinder.notification.dto.NotificationPayload;
import com.bhupinder.notification.dto.NotificationRequest;
import com.bhupinder.notification.redis.NotificationPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2: this controller no longer touches SimpMessagingTemplate at all.
 * It ONLY publishes to Redis via NotificationPublisher. Local STOMP delivery
 * happens exclusively in NotificationRedisListener - including for THIS instance's
 * own locally-connected clients, even though that might look like an unnecessary
 * hop. That's deliberate: one delivery code path for every instance, no special
 * case for "the instance that happened to receive the REST call."
 *
 * The REST contract (POST /api/notify, same request/response shape) is unchanged
 * from Phase 1 - that's the point of having separated "how clients ask to publish"
 * from "how messages actually get distributed" from the start.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "*") // fine for local dev/demo; would be scoped down for a real prod deploy
public class NotificationController {

    private final NotificationPublisher notificationPublisher;

    @PostMapping("/api/notify")
    public NotificationPayload notify(@Valid @RequestBody NotificationRequest request) {
        NotificationPayload payload = new NotificationPayload(
                request.topic(),
                request.message(),
                System.currentTimeMillis()
        );

        notificationPublisher.publish(payload);

        return payload;
    }
}
