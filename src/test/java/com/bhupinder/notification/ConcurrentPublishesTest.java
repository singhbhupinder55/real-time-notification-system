package com.bhupinder.notification;

import com.bhupinder.notification.dto.NotificationPayload;
import com.bhupinder.notification.dto.NotificationRequest;
import com.bhupinder.notification.testsupport.TestStompClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves: multiple notifications published to the SAME topic at nearly the
 * same time are ALL delivered (no drops, no duplicates) to a subscribed
 * client - WITHOUT asserting any particular delivery order.
 *
 * This backs the resume claim about testing concurrent publishes, and informs
 * the README's honest "Design Decisions" statement: Redis Pub/Sub on a single
 * instance, in practice, tends to deliver same-process publishes roughly in
 * the order PUBLISH commands were issued - but that is NOT a guaranteed
 * contract of this design (no sequence numbers, no broker-level ordering
 * guarantee like a real message queue would provide). We test for completeness
 * (all N messages arrive exactly once) and report the observed order as
 * evidence, without asserting on it - asserting strict ordering would make
 * this test flaky for a guarantee the system was never designed to provide.
 */
class ConcurrentPublishesTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentPublishesTest.class);

    private static final int PUBLISH_COUNT = 10;
    private static final String TOPIC = "concurrent-publishes-topic";

    @Autowired
    private TestRestTemplate restTemplate;

    private TestStompClient client;
    private ExecutorService executor;

    @AfterEach
    void cleanup() {
        if (client != null) {
            client.disconnect();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void allConcurrentPublishesToSameTopicAreDeliveredExactlyOnce() throws Exception {
        client = new TestStompClient();
        client.connect(wsUrl(), 5, TimeUnit.SECONDS);
        client.subscribe(TOPIC);

        // Same documented grace period rationale as the other concurrency
        // tests - let the server finish registering the subscription before
        // we start firing publishes at it.
        Thread.sleep(300);

        List<String> sentMessages = IntStream.range(0, PUBLISH_COUNT)
                .mapToObj(i -> "concurrent message #" + i)
                .collect(Collectors.toList());

        executor = Executors.newFixedThreadPool(PUBLISH_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(PUBLISH_COUNT);

        for (String message : sentMessages) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    NotificationRequest request = new NotificationRequest(TOPIC, message);
                    restTemplate.postForEntity(notifyUrl(), request, NotificationPayload.class);
                } catch (Exception e) {
                    log.error("Publish failed for message={}", message, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all PUBLISH_COUNT threads at once
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(finished).as("all publish threads should finish within timeout").isTrue();

        // --- COMPLETENESS: exactly PUBLISH_COUNT messages received, no drops,
        // no duplicates ---
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(client.getReceivedMessages()).hasSize(PUBLISH_COUNT));

        List<String> receivedMessages = client.getReceivedMessages().stream()
                .map(NotificationPayload::message)
                .collect(Collectors.toList());

        // Order-independent comparison: same SET of messages, regardless of
        // arrival order. containsExactlyInAnyOrderElementsOf is the AssertJ
        // method specifically for "same elements, order doesn't matter."
        assertThat(receivedMessages)
                .as("every published message should be received exactly once, in any order")
                .containsExactlyInAnyOrderElementsOf(sentMessages);

        // --- EVIDENCE, not an assertion: log the actual observed order next
        // to the sent order. Useful for the README/interview discussion of
        // what ordering behavior was actually observed on this design, even
        // though we don't rely on it. ---
        log.info("Sent order:     {}", sentMessages);
        log.info("Received order: {}", receivedMessages);
        log.info("Order preserved: {}", sentMessages.equals(receivedMessages));
    }
}
