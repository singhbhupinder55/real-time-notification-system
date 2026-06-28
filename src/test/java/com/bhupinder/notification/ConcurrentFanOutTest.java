package com.bhupinder.notification;

import com.bhupinder.notification.dto.NotificationPayload;
import com.bhupinder.notification.dto.NotificationRequest;
import com.bhupinder.notification.testsupport.TestStompClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves: with 100 concurrent clients subscribed to the SAME topic, a single
 * publish results in EVERY client receiving EXACTLY ONE copy of the message -
 * no drops, no duplicates.
 *
 * This is the test that backs the resume claim "I have a test that opens
 * 50-100 concurrent WebSocket connections and proves every one receives
 * exactly one copy of the message, no drops, no duplicates."
 *
 * STRUCTURE - three distinct phases, each with its own synchronization:
 *
 *   1. ARRANGE: connect + subscribe 100 clients concurrently via an
 *      ExecutorService, then await() until all 100 report isConnected() ==
 *      true, PLUS a short documented grace period. TestStompClient.subscribe()
 *      returns as soon as the SUBSCRIBE frame is SENT, not once the server's
 *      subscription registry has processed it - isConnected() alone does not
 *      close that gap. Publishing before registration completes would cause
 *      some clients to legitimately miss the message, which would be a false
 *      failure (a bug in the TEST, not the SYSTEM).
 *
 *   2. ACT: publish exactly once.
 *
 *   3. ASSERT: await() until every one of the 100 clients has received
 *      exactly 1 message, bounded by a timeout (never Thread.sleep-guessing
 *      for THIS part - delivery time is exactly what we're trying to measure).
 *      Then assert per-client: exactly 1 message, correct content.
 */
class ConcurrentFanOutTest extends AbstractIntegrationTest {

    private static final int CLIENT_COUNT = 100;
    private static final String TOPIC = "fan-out-test-topic";

    @Autowired
    private TestRestTemplate restTemplate;

    private final List<TestStompClient> clients = new ArrayList<>();
    private ExecutorService executor;

    @AfterEach
    void cleanup() {
        clients.forEach(TestStompClient::disconnect);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void allHundredClientsReceiveExactlyOneCopyOfTheMessage() throws InterruptedException {
        executor = Executors.newFixedThreadPool(CLIENT_COUNT);

        // --- PHASE 1: ARRANGE - connect + subscribe all 100 concurrently ---
        List<CompletableFuture<TestStompClient>> connectFutures = IntStream.range(0, CLIENT_COUNT)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    TestStompClient client = new TestStompClient();
                    try {
                        client.connect(wsUrl(), 5, TimeUnit.SECONDS);
                        client.subscribe(TOPIC);
                    } catch (Exception e) {
                        throw new RuntimeException("Client " + i + " failed to connect/subscribe", e);
                    }
                    return client;
                }, executor))
                .collect(Collectors.toList());

        // Block until every connect+subscribe attempt has finished (success or
        // exception) - this is a different wait than the Awaitility ones below,
        // because we're waiting for FUTURES to complete, not polling application state.
        clients.addAll(
                connectFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        );

        assertThat(clients).hasSize(CLIENT_COUNT);

        // Confirm every client's underlying WebSocket session is open before we
        // publish. NOTE: this proves the socket is connected, not that the
        // server's STOMP subscription registry has finished processing each
        // client's SUBSCRIBE frame - that registration happens asynchronously
        // on the server a moment after the frame arrives.
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(clients).allSatisfy(c -> assertThat(c.isConnected()).isTrue())
                );

        // Explicit, documented grace period for STOMP subscription registration
        // to complete server-side for all 100 clients. This is NOT a guess
        // standing in for "I don't know how long delivery takes" - delivery
        // itself is verified below with a bounded await(). This is specifically
        // tolerance for the connect-to-subscribed-on-server gap, which on
        // localhost is consistently sub-100ms; 300ms gives comfortable margin
        // without meaningfully slowing the test down.
        Thread.sleep(300);

        // --- PHASE 2: ACT - publish exactly once ---
        NotificationRequest request = new NotificationRequest(TOPIC, "fan-out test message");
        restTemplate.postForEntity(notifyUrl(), request, NotificationPayload.class);

        // --- PHASE 3: ASSERT - every client gets EXACTLY one copy ---
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(clients)
                                .allSatisfy(c -> assertThat(c.getReceivedMessages()).hasSize(1))
                );

        // Final, explicit assertions - not just "eventually true", but checked
        // content and an exact count per client, stated plainly for anyone
        // reading this test (or an interviewer asking about it).
        for (int i = 0; i < clients.size(); i++) {
            List<NotificationPayload> received = clients.get(i).getReceivedMessages();
            assertThat(received)
                    .as("client %d should receive exactly one message, no drops, no duplicates", i)
                    .hasSize(1);
            assertThat(received.get(0).topic()).isEqualTo(TOPIC);
            assertThat(received.get(0).message()).isEqualTo("fan-out test message");
        }
    }
}
