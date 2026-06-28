package com.bhupinder.notification;

import com.bhupinder.notification.dto.NotificationPayload;
import com.bhupinder.notification.dto.NotificationRequest;
import com.bhupinder.notification.testsupport.TestStompClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SANITY CHECK before scaling to 100 concurrent clients in the real fan-out test.
 *
 * This proves the entire pipeline works for the simplest possible case:
 * one client, one subscribe, one publish, one received message. If THIS test
 * is flaky or wrong, debugging it is tractable. If we went straight to 100
 * clients and something failed, we'd have no idea whether the bug is in
 * TestStompClient, the app, or the test orchestration itself.
 */
class SingleClientRoundTripTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private TestStompClient client;

    @AfterEach
    void cleanup() {
        if (client != null) {
            client.disconnect();
        }
    }

    @Test
    void singleClientReceivesPublishedNotification() throws Exception {
        client = new TestStompClient();
        client.connect(wsUrl(), 5, TimeUnit.SECONDS);
        client.subscribe("sanity-check-topic");

        NotificationRequest request = new NotificationRequest("sanity-check-topic", "hello from sanity test");
        restTemplate.postForEntity(notifyUrl(), request, NotificationPayload.class);

        await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(client.getReceivedMessages()).hasSize(1));

        NotificationPayload received = client.getReceivedMessages().get(0);
        assertThat(received.topic()).isEqualTo("sanity-check-topic");
        assertThat(received.message()).isEqualTo("hello from sanity test");
    }
}
