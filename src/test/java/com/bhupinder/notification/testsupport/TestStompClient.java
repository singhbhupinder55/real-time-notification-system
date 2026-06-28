package com.bhupinder.notification.testsupport;

import com.bhupinder.notification.dto.NotificationPayload;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A real STOMP-over-WebSocket test client, built on Spring's own WebSocketStompClient
 * (not a third-party library, not a mock). One instance of this = one simulated
 * "browser tab" for test purposes.
 *
 * THREAD SAFETY NOTE: receivedMessages is a CopyOnWriteArrayList deliberately.
 * STOMP frames arrive on the underlying WebSocket IO thread, not the test thread
 * that called subscribe(). If this were a plain ArrayList, concurrent delivery to
 * multiple TestStompClient instances - or even just async delivery timing - could
 * corrupt the list or lose adds. We want any flakiness in our concurrency tests to
 * come from the SYSTEM UNDER TEST, never from the test harness itself being unsafe.
 *
 * Usage pattern in tests:
 *   TestStompClient client = new TestStompClient();
 *   client.connect(wsUrl);
 *   client.subscribe("user-1");
 *   ... trigger a publish elsewhere ...
 *   await().atMost(...).until(() -> client.getReceivedMessages().size() == 1);
 */
public class TestStompClient {

    private final WebSocketStompClient stompClient;
    private final List<NotificationPayload> receivedMessages = new CopyOnWriteArrayList<>();
    private volatile StompSession session;
    private volatile Throwable connectionError;

    public TestStompClient() {
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        // A task scheduler is required for STOMP heartbeats. We don't rely on
        // heartbeats for correctness in these tests (our tests run in seconds,
        // not minutes), but WebSocketStompClient requires one to be set or it
        // throws at connect time.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.afterPropertiesSet();
        stompClient.setTaskScheduler(scheduler);

        // Lets the client deserialize STOMP frame bodies (JSON) straight into
        // NotificationPayload, instead of us hand-parsing JSON strings here -
        // mirrors how the REAL browser client receives the payload shape.
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    /**
     * Connects and blocks until CONNECTED or failure, up to the given timeout.
     * Intentionally synchronous from the caller's point of view - tests that need
     * many concurrent connections will call this from many threads (e.g. an
     * ExecutorService), not by making THIS method internally concurrent.
     */
    public void connect(String wsUrl, long timeout, TimeUnit unit) throws Exception {
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession connectedSession, StompHeaders connectedHeaders) {
                // Kept for visibility/logging purposes only - session is no
                // longer read FROM here. See note below on why.
                session = connectedSession;
            }

            @Override
            public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                         byte[] payload, Throwable exception) {
                connectionError = exception;
            }

            @Override
            public void handleTransportError(StompSession s, Throwable exception) {
                connectionError = exception;
            }
        };

        // IMPORTANT: connectAsync(...).get() returns the StompSession directly
        // as its result. We use THAT return value as the source of truth for
        // `session`, rather than relying solely on afterConnected() having
        // already run by the time get() returns.
        //
        // Why this matters: afterConnected() is invoked via a callback that
        // can fire on a slightly different thread/timing than the future's
        // own completion. With a single connection this gap is invisible -
        // but orchestrating 100 concurrent connects (ConcurrentFanOutTest)
        // surfaced it: occasionally get() would return before the
        // afterConnected() callback had set the `session` field, causing a
        // false "session is null" failure even though the connection
        // actually succeeded. This was a bug in the TEST HARNESS's own
        // synchronization, not in the WebSocket/Redis system under test -
        // worth being precise about that distinction.
        StompSession connectedSession;
        try {
            connectedSession = stompClient.connectAsync(wsUrl, handler).get(timeout, unit);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to connect TestStompClient to " + wsUrl, e);
        }

        if (connectedSession == null) {
            throw new IllegalStateException("Connect completed but returned session is null - connectionError=" + connectionError);
        }

        this.session = connectedSession;
    }

    public void subscribe(String topic) {
        requireConnected();
        session.subscribe("/topics/" + topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificationPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessages.add((NotificationPayload) payload);
            }
        });
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    public List<NotificationPayload> getReceivedMessages() {
        return receivedMessages;
    }

    private void requireConnected() {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("Cannot subscribe before a successful connect()");
        }
    }
}
