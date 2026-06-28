package com.bhupinder.notification.controller;

import com.bhupinder.notification.dto.NotificationPayload;
import com.bhupinder.notification.dto.NotificationRequest;
import com.bhupinder.notification.redis.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Pure unit test - mocks NotificationPublisher, so this never touches Redis.
 * Verifies the controller's actual responsibility: build a correctly-shaped
 * NotificationPayload from the incoming request (including stamping a
 * publish timestamp the client never provided) and delegate to the
 * publisher - nothing more.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationPublisher notificationPublisher;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationPublisher);
    }

    @Test
    void notifyBuildsPayloadFromRequestAndDelegatesToPublisher() {
        NotificationRequest request = new NotificationRequest("user-1", "hello");

        long before = System.currentTimeMillis();
        NotificationPayload result = controller.notify(request);
        long after = System.currentTimeMillis();

        assertThat(result.topic()).isEqualTo("user-1");
        assertThat(result.message()).isEqualTo("hello");
        // Timestamp should be stamped at call time, not client-provided -
        // bounded check rather than an exact match, since we can't predict
        // the precise millisecond System.currentTimeMillis() returns.
        assertThat(result.publishedAtEpochMillis()).isBetween(before, after);

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationPublisher).publish(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isEqualTo(result);
    }

    @Test
    void notifyReturnsTheSamePayloadItPublishes() {
        // The REST response and what gets published to Redis must be the
        // SAME payload (same timestamp included) - if these diverged, the
        // latency a caller could compute from the REST response wouldn't
        // match what subscribers actually see.
        NotificationRequest request = new NotificationRequest("announcements", "server restarting");

        NotificationPayload returned = controller.notify(request);

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationPublisher).publish(payloadCaptor.capture());

        assertThat(returned).isEqualTo(payloadCaptor.getValue());
    }

    @Test
    void differentRequestsProduceDifferentTimestamps() throws InterruptedException {
        NotificationRequest request1 = new NotificationRequest("user-1", "first");
        NotificationPayload result1 = controller.notify(request1);

        Thread.sleep(5); // ensure the clock actually advances between calls

        NotificationRequest request2 = new NotificationRequest("user-1", "second");
        NotificationPayload result2 = controller.notify(request2);

        assertThat(result2.publishedAtEpochMillis()).isGreaterThan(result1.publishedAtEpochMillis());
    }
}
