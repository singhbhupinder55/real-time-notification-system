package com.bhupinder.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared base class for all integration tests that need the full Spring app
 * context running on a random port, talking to a REAL Redis instance.
 *
 * REDIS LIFECYCLE NOTE: this deliberately does NOT use Testcontainers to manage
 * the Redis container's start/stop lifecycle. On this machine, Testcontainers'
 * Docker client cannot establish a working connection to Docker Desktop's socket
 * (confirmed via direct debugging - docker CLI itself works fine, but
 * Testcontainers' Java HTTP client to the same socket does not), and this is a
 * known class of issue on certain Docker Desktop + Mac + JVM combinations.
 *
 * Workaround (same one used successfully on the Event Booking project): Redis is
 * started externally via `docker compose -f docker-compose.test.yml up -d` BEFORE
 * running tests, on a fixed port (6380, deliberately different from the manual
 * dev Redis on 6379 so both can run side by side). Tests connect to that fixed,
 * already-running instance instead of asking Testcontainers to manage it.
 *
 * This is an orchestration difference, not an engineering compromise: tests still
 * run against a real Redis instance, not a mock - the property we actually care
 * about for the "validated against real infrastructure" resume claim is preserved.
 * Only the tool that starts/stops the container changed.
 *
 * See README "Known Limitations" / "Design Decisions" for why this approach was
 * chosen over Testcontainers specifically on this environment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    protected String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    protected String notifyUrl() {
        return "http://localhost:" + port + "/api/notify";
    }
}
