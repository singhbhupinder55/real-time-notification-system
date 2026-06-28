package com.bhupinder.notification.redis;

import com.bhupinder.notification.config.NotificationProperties;
import com.bhupinder.notification.config.RedisConfig;
import com.bhupinder.notification.dto.NotificationPayload;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test - Mockito mocks StringRedisTemplate, so this never touches
 * a real Redis instance. We use a REAL ObjectMapper (not mocked) for the
 * happy-path tests because Jackson serialization is deterministic and cheap;
 * mocking it there would just hide the actual behavior we care about - that
 * the right JSON goes out. The one test that needs serialization to actually
 * FAIL uses a mocked ObjectMapper instead, since NotificationPayload's simple
 * field shape can never realistically fail real Jackson serialization.
 */
@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationProperties properties = new NotificationProperties("test-instance");
        publisher = new NotificationPublisher(redisTemplate, objectMapper, properties);
    }

    @Test
    void publishSendsCorrectlySerializedJsonToTheNotificationsChannel() {
        NotificationPayload payload = new NotificationPayload("user-1", "hello", 123L);

        publisher.publish(payload);

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo(RedisConfig.NOTIFICATIONS_CHANNEL);
        assertThat(messageCaptor.getValue())
                .contains("\"topic\":\"user-1\"")
                .contains("\"message\":\"hello\"")
                .contains("\"publishedAtEpochMillis\":123");
    }

    @Test
    void publishIsCalledExactlyOncePerPayload() {
        NotificationPayload payload = new NotificationPayload("user-2", "world", 456L);

        publisher.publish(payload);

        verify(redisTemplate).convertAndSend(eq(RedisConfig.NOTIFICATIONS_CHANNEL), anyString());
    }

    @Test
    void publishThrowsIllegalStateAndDoesNotCallRedisWhenSerializationFails() {
        // This test needs serialization to ACTUALLY fail, which the real
        // ObjectMapper can't do for this DTO's simple field shape (String,
        // String, long always serializes cleanly). So here - and only here -
        // we use a mocked ObjectMapper to force a JsonProcessingException,
        // proving NotificationPublisher's catch block does what it claims:
        // fail loudly with IllegalStateException, and never call Redis with
        // a half-formed payload.
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        try {
            when(failingMapper.writeValueAsString(any()))
                    .thenThrow(new JsonParseException((JsonParser) null, "forced failure for test"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        NotificationProperties properties = new NotificationProperties("test-instance");
        NotificationPublisher publisherWithFailingMapper = new NotificationPublisher(redisTemplate, failingMapper, properties);

        NotificationPayload payload = new NotificationPayload("user-1", "hello", 123L);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> publisherWithFailingMapper.publish(payload)
        );
        assertThat(thrown.getMessage()).contains("Failed to serialize");

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }
}
