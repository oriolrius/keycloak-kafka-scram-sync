package com.miimetiq.keycloak.sync.integration;

import com.miimetiq.keycloak.sync.webhook.EventQueueService;
import com.miimetiq.keycloak.sync.webhook.KeycloakAdminEvent;
import com.miimetiq.keycloak.sync.webhook.WebhookEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for event queue functionality under load.
 * <p>
 * Tests queue behavior including capacity limits, overflow handling,
 * async processing, and metrics.
 */
@QuarkusTest
@DisplayName("Event Queue Integration Tests")
class EventQueueIntegrationTest {

    @Inject
    EventQueueService queueService;

    @BeforeEach
    void setUp() {
        // Clear queue before each test
        queueService.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        queueService.clear();
    }

    @Test
    @DisplayName("Queue should accept events with correlation ID")
    void testQueueAcceptsEventsWithCorrelationId() {
        // Create test event
        String correlationId = UUID.randomUUID().toString();
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("CREATE");

        WebhookEvent webhookEvent = new WebhookEvent(correlationId, event);

        // Enqueue event
        boolean enqueued = queueService.enqueue(webhookEvent);

        // Verify
        assertTrue(enqueued, "Event should be enqueued successfully");
        assertEquals(1, queueService.size(), "Queue should contain 1 event");
    }

    @Test
    @DisplayName("Queue should respect capacity limit")
    void testQueueRespectsCapacityLimit() {
        int capacity = queueService.capacity();

        // Fill queue to capacity
        for (int i = 0; i < capacity; i++) {
            KeycloakAdminEvent event = new KeycloakAdminEvent();
            event.setResourceType("USER");
            event.setOperationType("CREATE");
            WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
            boolean enqueued = queueService.enqueue(webhookEvent);
            assertTrue(enqueued, "Event " + i + " should be enqueued");
        }

        // Verify queue is full
        assertEquals(capacity, queueService.size(), "Queue should be at capacity");

        // Try to enqueue one more (behavior depends on overflow strategy)
        KeycloakAdminEvent extraEvent = new KeycloakAdminEvent();
        extraEvent.setResourceType("USER");
        extraEvent.setOperationType("CREATE");
        WebhookEvent extraWebhookEvent = new WebhookEvent(UUID.randomUUID().toString(), extraEvent);
        boolean enqueued = queueService.enqueue(extraWebhookEvent);

        if ("REJECT".equalsIgnoreCase(queueService.getOverflowStrategy())) {
            assertFalse(enqueued, "Event should be rejected when queue is full with REJECT strategy");
            assertEquals(capacity, queueService.size(), "Queue size should remain at capacity");
        } else if ("DROP_OLDEST".equalsIgnoreCase(queueService.getOverflowStrategy())) {
            assertTrue(enqueued, "Event should be enqueued with DROP_OLDEST strategy");
            assertEquals(capacity, queueService.size(), "Queue size should remain at capacity");
            assertTrue(queueService.getDroppedCount() > 0, "Dropped count should increase");
        }
    }

    @Test
    @DisplayName("Queue should handle concurrent enqueueing")
    void testConcurrentEnqueueing() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Submit concurrent enqueue tasks
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < eventsPerThread; i++) {
                        KeycloakAdminEvent event = new KeycloakAdminEvent();
                        event.setResourceType("USER");
                        event.setOperationType("CREATE");
                        WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
                        if (queueService.enqueue(webhookEvent)) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within timeout");
        executor.shutdown();

        // Verify results
        assertTrue(successCount.get() > 0, "At least some events should be enqueued");
        assertTrue(queueService.size() <= queueService.capacity(),
                "Queue size should not exceed capacity");
    }

    @Test
    @DisplayName("REST endpoint should enqueue events successfully")
    void testRestEndpointEnqueuesEvents() throws InterruptedException {
        // Clear queue
        queueService.clear();

        // Send multiple events via REST
        int eventCount = 10;
        for (int i = 0; i < eventCount; i++) {
            String event = String.format("""
                    {
                        "id": "test-event-%d",
                        "time": %d,
                        "realmId": "test-realm",
                        "resourceType": "USER",
                        "operationType": "CREATE",
                        "resourcePath": "users/user-%d"
                    }
                    """, i, System.currentTimeMillis(), i);

            given()
                    .contentType(ContentType.JSON)
                    .body(event)
                    .when()
                    .post("/api/kc/events")
                    .then()
                    .statusCode(200);
        }

        // Give a moment for events to be enqueued
        Thread.sleep(100);

        // Verify events are in queue
        assertTrue(queueService.size() > 0, "Queue should contain enqueued events");
        assertTrue(queueService.size() <= eventCount, "Queue should not have more than sent events");
    }

    @Test
    @DisplayName("REST endpoint should return 503 when queue is full (REJECT strategy)")
    void testRestEndpointRejectsWhenQueueFull() {
        // Only test if REJECT strategy is configured
        if (!"REJECT".equalsIgnoreCase(queueService.getOverflowStrategy())) {
            return; // Skip test for DROP_OLDEST strategy
        }

        // Fill queue to capacity
        int capacity = queueService.capacity();
        for (int i = 0; i < capacity; i++) {
            KeycloakAdminEvent event = new KeycloakAdminEvent();
            event.setResourceType("USER");
            event.setOperationType("CREATE");
            WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
            queueService.enqueue(webhookEvent);
        }

        // Try to send one more event via REST
        String event = """
                {
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/overflow-test"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(event)
                .when()
                .post("/api/kc/events")
                .then()
                .statusCode(503)
                .body("error", containsString("queue is full"));
    }

    @Test
    @DisplayName("Events should be polled from queue")
    void testEventPolling() throws InterruptedException {
        // Enqueue an event
        String correlationId = UUID.randomUUID().toString();
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("CREATE");
        WebhookEvent webhookEvent = new WebhookEvent(correlationId, event);

        queueService.enqueue(webhookEvent);
        assertEquals(1, queueService.size());

        // Poll event
        var polled = queueService.poll(1, TimeUnit.SECONDS);

        // Verify
        assertTrue(polled.isPresent(), "Event should be polled");
        assertEquals(correlationId, polled.get().getCorrelationId());
        assertEquals(0, queueService.size(), "Queue should be empty after polling");
    }

    @Test
    @DisplayName("Poll should timeout when queue is empty")
    void testPollTimeout() {
        // Ensure queue is empty
        queueService.clear();

        // Poll with short timeout
        long startTime = System.currentTimeMillis();
        var polled = queueService.poll(500, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        // Verify
        assertFalse(polled.isPresent(), "No event should be polled from empty queue");
        assertTrue(elapsed >= 400, "Poll should wait for timeout duration");
        assertTrue(elapsed < 1000, "Poll should not wait much longer than timeout");
    }

    @Test
    @DisplayName("Queue metrics should reflect queue state")
    void testQueueMetrics() {
        // Clear queue
        queueService.clear();
        assertEquals(0, queueService.size());

        // Enqueue some events
        int count = 5;
        for (int i = 0; i < count; i++) {
            KeycloakAdminEvent event = new KeycloakAdminEvent();
            event.setResourceType("USER");
            event.setOperationType("CREATE");
            WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
            queueService.enqueue(webhookEvent);
        }

        // Verify queue size metric (allow for async processing)
        int initialSize = queueService.size();
        assertTrue(initialSize > 0 && initialSize <= count,
                "Queue size should be between 1 and " + count + " but was " + initialSize);

        // Poll some events manually
        queueService.poll(1, TimeUnit.SECONDS);
        queueService.poll(1, TimeUnit.SECONDS);

        // Verify size decreased (but may have decreased more due to background workers)
        int sizeAfterPoll = queueService.size();
        assertTrue(sizeAfterPoll < initialSize || sizeAfterPoll == 0,
                "Queue size should decrease after polling");
    }

    @Test
    @DisplayName("Queue should preserve event metadata")
    void testEventMetadataPreservation() throws InterruptedException {
        // Create event with metadata
        String correlationId = "test-correlation-123";
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("UPDATE");
        event.setResourcePath("users/test-user-id");
        event.setRealmId("test-realm");

        WebhookEvent webhookEvent = new WebhookEvent(correlationId, event);

        // Enqueue and poll
        queueService.enqueue(webhookEvent);
        var polled = queueService.poll(1, TimeUnit.SECONDS);

        // Verify metadata is preserved
        assertTrue(polled.isPresent());
        assertEquals(correlationId, polled.get().getCorrelationId());
        assertEquals("USER", polled.get().getEvent().getResourceType());
        assertEquals("UPDATE", polled.get().getEvent().getOperationType());
        assertEquals("users/test-user-id", polled.get().getEvent().getResourcePath());
        assertEquals("test-realm", polled.get().getEvent().getRealmId());
        assertNotNull(polled.get().getEnqueuedAt());
    }
}
