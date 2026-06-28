package com.bhupinder.notification.redis;

import com.bhupinder.notification.config.NotificationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Pure unit test - mocks SimpMessagingTemplate, so this never touches a real
 * STOMP broker or WebSocket connection. Uses a real ObjectMapper since
 * deserializing test-authored JSON strings is exactly the behavior we want
 * to verify, not something worth hiding behind a mock.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRedisListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationRedisListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationProperties properties = new NotificationProperties("test-instance");
        listener = new NotificationRedisListener(messagingTemplate, objectMapper, properties);
    }

    @Test
    void onMessageForwardsToCorrectStompDestination() {
        String json = "{\"topic\":\"user-1\",\"message\":\"hello\",\"publishedAtEpochMillis\":123}";

        listener.onMessage(json);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), any(Object.class));

        assertThat(destinationCaptor.getValue()).isEqualTo("/topics/user-1");
    }

    @Test
    void onMessageDeserializesPayloadCorrectlyBeforeForwarding() {
        String json = "{\"topic\":\"announcements\",\"message\":\"server restarting\",\"publishedAtEpochMillis\":999}";

        listener.onMessage(json);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .extracting("topic", "message", "publishedAtEpochMillis")
                .containsExactly("announcements", "server restarting", 999L);
    }

    @Test
    void malformedJsonDoesNotThrowAndDoesNotForwardAnything() {
        // This is the test that matters most for this class: a malformed
        // message on the shared Redis channel must NOT crash the listener
        // thread, because that would silently stop this instance from
        // receiving ALL future notifications - not just the bad one.
        String malformedJson = "{not valid json at all";

        // The key assertion is simply that this does NOT throw.
        listener.onMessage(malformedJson);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void emptyStringDoesNotThrow() {
        listener.onMessage("");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void validJsonWithMissingFieldsDoesNotThrow() {
        // Missing "message" field - Jackson will deserialize this into a
        // NotificationPayload with message=null rather than throwing, since
        // there's no @JsonProperty(required=true) configured. Documenting
        // this behavior explicitly rather than assuming it.
        String partialJson = "{\"topic\":\"user-1\",\"publishedAtEpochMillis\":123}";

        listener.onMessage(partialJson);

        // Forwards anyway, with message=null - this is current behavior,
        // not necessarily ideal behavior. Worth knowing about for the
        // README's "Known Limitations" section.
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void messageWithSpecialCharactersIsForwardedIntact() {
        // Quotes, backslashes, and unicode are exactly the characters that
        // break naive string concatenation but should round-trip cleanly
        // through proper JSON serialization/deserialization.
        String json = "{\"topic\":\"user-1\",\"message\":\"quote\\\" backslash\\\\ emoji \\ud83d\\ude00\",\"publishedAtEpochMillis\":123}";

        listener.onMessage(json);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .extracting("message")
                .isEqualTo("quote\" backslash\\ emoji \uD83D\uDE00");
    }

    @Test
    void veryLongMessageIsForwardedWithoutTruncation() {
        String longMessage = "x".repeat(10_000);
        String json = String.format(
                "{\"topic\":\"user-1\",\"message\":\"%s\",\"publishedAtEpochMillis\":123}", longMessage);

        listener.onMessage(json);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .extracting("message")
                .isEqualTo(longMessage);
    }

    @Test
    void multipleSequentialMessagesAreEachForwardedIndependently() {
        // Confirms no state leaks between calls on the same listener
        // instance - relevant since one NotificationRedisListener bean
        // handles every message for the lifetime of the application.
        listener.onMessage("{\"topic\":\"topic-a\",\"message\":\"first\",\"publishedAtEpochMillis\":1}");
        listener.onMessage("{\"topic\":\"topic-b\",\"message\":\"second\",\"publishedAtEpochMillis\":2}");

        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topics/topic-a"), any(Object.class));
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topics/topic-b"), any(Object.class));
    }

    @Test
    void topicWithSlashesIsAppendedDirectlyWithoutSanitization() {
        // Documents CURRENT behavior: the listener does not sanitize topic
        // names before building the destination string. A topic containing
        // "/" would produce a destination with an extra path segment, e.g.
        // "/topics/a/b" instead of failing or escaping it. Worth knowing
        // about - this is a real, if minor, input-validation gap.
        String json = "{\"topic\":\"a/b\",\"message\":\"hello\",\"publishedAtEpochMillis\":1}";

        listener.onMessage(json);

        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topics/a/b"), any(Object.class));
    }
}
