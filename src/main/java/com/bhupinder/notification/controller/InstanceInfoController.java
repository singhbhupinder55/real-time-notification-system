package com.bhupinder.notification.controller;

import com.bhupinder.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Purely a demo/proof endpoint - lets you hit the same load-balanced URL repeatedly
 * (Phase 5) and see different instance IDs answer, proving requests are actually
 * being distributed across multiple containers rather than all landing on one.
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
public class InstanceInfoController {

    private final NotificationProperties notificationProperties;

    @GetMapping("/api/instance-info")
    public Map<String, String> instanceInfo() {
        return Map.of("instanceId", notificationProperties.instanceId());
    }
}
