package com.bhupinder.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the "notification.instance-id" property from application.yml
 * (which itself reads the INSTANCE_ID env var, defaulting to "local-dev").
 *
 * Centralizing this as a typed properties bean instead of scattering
 * @Value("${notification.instance-id}") across multiple classes - one
 * place to look if this ever needs a default change or validation.
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String instanceId) {
}
