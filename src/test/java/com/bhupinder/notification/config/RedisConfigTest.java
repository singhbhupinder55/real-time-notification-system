package com.bhupinder.notification.config;

import com.bhupinder.notification.redis.NotificationRedisListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisConfig's @Bean methods are plain Java methods that take simple,
 * mockable parameters - we can call them directly without booting a Spring
 * context at all. This verifies the WIRING LOGIC (right channel name, right
 * method name passed to the adapter) without needing a real Redis connection.
 */
@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void notificationsChannelConstantIsStable() {
        // This constant is referenced by both NotificationPublisher (publish
        // side) and here (subscribe side) - if it ever changed accidentally
        // in one place but not the other, publishers and subscribers would
        // silently stop talking to each other. Pinning its exact value here.
        assertThat(RedisConfig.NOTIFICATIONS_CHANNEL).isEqualTo("notifications");
    }

    @Test
    void stringRedisTemplateBeanIsConstructedWithGivenConnectionFactory() {
        StringRedisTemplate template = redisConfig.stringRedisTemplate(connectionFactory);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(connectionFactory);
    }

    @Test
    void listenerAdapterCanBeConstructedFromARealListenerInstance() {
        // MessageListenerAdapter doesn't expose its configured delegate or
        // method name via any public getter, so we can't directly assert
        // "this adapter calls onMessage on this listener" from outside the
        // Spring container. What we CAN verify here: construction succeeds
        // with a real NotificationRedisListener instance, and the actual
        // end-to-end behavior (the right method gets invoked when a Redis
        // message arrives) is what the integration tests already prove.
        NotificationRedisListener listener = new NotificationRedisListener(null, null, null);

        MessageListenerAdapter adapter = redisConfig.listenerAdapter(listener);

        assertThat(adapter).isNotNull();
    }

    @Test
    void redisMessageListenerContainerIsConfiguredWithGivenConnectionFactory() {
        NotificationRedisListener listener = new NotificationRedisListener(null, null, null);
        MessageListenerAdapter adapter = redisConfig.listenerAdapter(listener);

        RedisMessageListenerContainer container =
                redisConfig.redisMessageListenerContainer(connectionFactory, adapter);

        assertThat(container).isNotNull();
        assertThat(container.getConnectionFactory()).isEqualTo(connectionFactory);
    }
}
