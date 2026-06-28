package com.bhupinder.notification.controller;

import com.bhupinder.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple enough that it doesn't need Mockito - NotificationProperties is a
 * plain record, easiest to just construct directly with the value we want
 * to assert on, rather than mocking something with no real behavior to mock.
 */
class InstanceInfoControllerTest {

    @Test
    void returnsTheConfiguredInstanceId() {
        NotificationProperties properties = new NotificationProperties("instance-42");
        InstanceInfoController controller = new InstanceInfoController(properties);

        Map<String, String> response = controller.instanceInfo();

        assertThat(response).containsExactly(Map.entry("instanceId", "instance-42"));
    }

    @Test
    void returnsLocalDevWhenThatIsTheConfiguredValue() {
        // Mirrors the actual default in application.yml (INSTANCE_ID:local-dev)
        // for anyone running without the env var set, e.g. plain `bootRun`.
        NotificationProperties properties = new NotificationProperties("local-dev");
        InstanceInfoController controller = new InstanceInfoController(properties);

        Map<String, String> response = controller.instanceInfo();

        assertThat(response.get("instanceId")).isEqualTo("local-dev");
    }
}
