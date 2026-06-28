package com.bhupinder.notification;

import com.bhupinder.notification.dto.NotificationPayload;
import com.bhupinder.notification.dto.NotificationRequest;
import com.bhupinder.notification.testsupport.TestStompClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves: a client disconnecting at almost the exact moment a notification is
 * published to its topic does NOT crash the publish path for OTHER clients,
 * does NOT throw back to the REST caller, and does NOT leave the Redis
 * listener thread (or anything else server-side) in a broken state.
 *
 * This backs the resume claim "I tested the disconnect-during-publish race
 * condition specifically, not just the happy path."
 *
 * DESIGN: two clients subscribe to the same topic - a "victim" (disconnects)
 * and a "survivor" (stays connected). A CountDownLatch releases the disconnect
 * and the publish from two separate threads at nearly the same instant, with
 * no artificial ordering delay between them - that's the actual race we're
 * trying to exercise, not a scripted "disconnect THEN publish" sequence which
 * wouldn't prove anything about concurrent timing.
 *
 * PROVING SERVER HEALTH AFTER THE RACE: JUnit can't directly assert "no
 * exception was swallowed in a background thread." Instead, this test sends a
 * SECOND, unrelated notification (different topic) after the race and asserts
 * the survivor receives THAT one too. If the disconnect race had killed the
 * Redis listener thread or corrupted shared state, this follow-up publish
 * would silently fail to arrive - making it a real, falsifiable proof of
 * server health, not just an assumption.
 */
class DisconnectRaceConditionTest extends AbstractIntegrationTest {

    private static final String TOPIC = "disconnect-race-topic";
    private static final String FOLLOWUP_TOPIC = "disconnect-race-followup-topic";

    @Autowired
    private TestRestTemplate restTemplate;

    private TestStompClient victim;
    private TestStompClient survivor;
    private ExecutorService executor;

    @AfterEach
    void cleanup() {
        if (victim != null) {
            victim.disconnect();
        }
        if (survivor != null) {
            survivor.disconnect();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void disconnectDuringPublishDoesNotCrashPublishPathOrAffectOtherClients() throws Exception {
        victim = new TestStompClient();
        survivor = new TestStompClient();

        victim.connect(wsUrl(), 5, TimeUnit.SECONDS);
        victim.subscribe(TOPIC);

        survivor.connect(wsUrl(), 5, TimeUnit.SECONDS);
        survivor.subscribe(TOPIC);

        // Same documented grace period rationale as ConcurrentFanOutTest -
        // give the server a moment to finish registering both subscriptions
        // before we start the race.
        Thread.sleep(300);

        // --- THE RACE: disconnect victim and publish, released as close to
        // simultaneously as two separate threads allow ---
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Will capture whatever the publish call returns/throws, so we can
        // assert on it from the main test thread afterward.
        final ResponseEntity<NotificationPayload>[] publishResponse = new ResponseEntity[1];
        final Throwable[] publishError = new Throwable[1];

        executor.submit(() -> {
            try {
                startLatch.await();
                victim.disconnect();
            } catch (Exception ignored) {
                // Disconnecting an already-racing socket can itself throw in
                // some edge cases - that's fine, it's not what we're asserting
                // on. We only care about the PUBLISH path's behavior below.
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                NotificationRequest request = new NotificationRequest(TOPIC, "race condition message");
                publishResponse[0] = restTemplate.postForEntity(notifyUrl(), request, NotificationPayload.class);
            } catch (Throwable t) {
                publishError[0] = t;
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // release both threads at once
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(finished).as("both racing threads should finish within timeout").isTrue();

        // --- ASSERTION 1: the publish call itself did not throw, and returned 2xx ---
        assertThat(publishError[0])
                .as("POST /api/notify should not throw even when a subscriber disconnects mid-publish")
                .isNull();
        assertThat(publishResponse[0]).isNotNull();
        assertThat(publishResponse[0].getStatusCode()).isEqualTo(HttpStatus.OK);

        // --- ASSERTION 2: the survivor still gets the message cleanly ---
        // (we deliberately do NOT assert anything about whether the victim
        // received it - that's a genuine race, either outcome is acceptable
        // and trying to pin it down would make this test flaky for the wrong
        // reasons)
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(survivor.getReceivedMessages()).hasSize(1));
        assertThat(survivor.getReceivedMessages().get(0).message()).isEqualTo("race condition message");

        // --- ASSERTION 3: server health after the race - an unrelated
        // follow-up publish on a DIFFERENT topic still gets delivered. This is
        // what actually proves the Redis listener thread / STOMP broker wasn't
        // left in a broken state by the race above. ---
        NotificationRequest followupRequest = new NotificationRequest(FOLLOWUP_TOPIC, "server still healthy");
        survivor.subscribe(FOLLOWUP_TOPIC);
        Thread.sleep(300); // same subscription-registration grace period as above

        ResponseEntity<NotificationPayload> followupResponse =
                restTemplate.postForEntity(notifyUrl(), followupRequest, NotificationPayload.class);
        assertThat(followupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(survivor.getReceivedMessages())
                                .as("server should still be healthy and deliver an unrelated follow-up notification after the race")
                                .anyMatch(p -> p.topic().equals(FOLLOWUP_TOPIC) && p.message().equals("server still healthy"))
                );
    }
}
