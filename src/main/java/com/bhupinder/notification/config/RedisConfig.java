package com.bhupinder.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis Pub/Sub wiring.
 *
 * Two responsibilities split across two beans, matching the two sides of pub/sub:
 *
 * 1. StringRedisTemplate - used to PUBLISH. We serialize NotificationPayload to a
 *    JSON string ourselves (via Jackson, in NotificationPublisher) rather than configuring
 *    a generic-typed JSON RedisTemplate. Fewer moving parts, and the JSON on the wire
 *    is identical to what STOMP clients already receive - one serialization format
 *    end to end, not two.
 *
 * 2. RedisMessageListenerContainer - used to SUBSCRIBE. Every instance that boots
 *    registers a listener on NOTIFICATIONS_CHANNEL. This is what makes the system
 *    distributed: an instance doesn't need to know which clients are connected to
 *    OTHER instances - it just needs to hear the Redis message and forward to
 *    whichever of ITS OWN clients are listening.
 */
@Configuration
public class RedisConfig {

    public static final String NOTIFICATIONS_CHANNEL = "notifications";

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new org.springframework.data.redis.listener.ChannelTopic(NOTIFICATIONS_CHANNEL));
        return container;
    }

    /**
     * Adapts our plain NotificationRedisListener (which just has an onMessage(String) method)
     * into the org.springframework.data.redis.connection.MessageListener interface the
     * container expects. The "onMessage" string here is the method name to invoke -
     * this is reflection-based wiring, standard for Spring Data Redis pub/sub.
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(com.bhupinder.notification.redis.NotificationRedisListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }
}
