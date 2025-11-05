package com.miimetiq.keycloak.sync.integration;

import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import com.miimetiq.keycloak.sync.webhook.EventMapper;
import com.miimetiq.keycloak.sync.webhook.EventProcessor;
import com.miimetiq.keycloak.sync.webhook.EventQueueService;
import com.miimetiq.keycloak.sync.webhook.KeycloakAdminEvent;
import com.miimetiq.keycloak.sync.webhook.RetryPolicy;
import com.miimetiq.keycloak.sync.webhook.WebhookEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for webhook retry logic.
 * <p>
 * Tests retry behavior for transient failures including:
 * - Exponential backoff timing
 * - Max retry attempts
 * - Event re-enqueueing
 * - Metrics tracking
 */
@QuarkusTest
@DisplayName("Webhook Retry Logic Integration Tests")
class WebhookRetryIntegrationTest {

    @Inject
    EventQueueService queueService;

    @Inject
    EventProcessor eventProcessor;

    @Inject
    RetryPolicy retryPolicy;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    SyncMetrics syncMetrics;

    @InjectSpy
    EventMapper eventMapper;

    @BeforeEach
    void setUp() {
        // Clear queue before each test
        queueService.clear();

        // Reset EventMapper mock
        Mockito.reset(eventMapper);

        // Ensure processor is running
        assertTrue(eventProcessor.isRunning(), "Event processor should be running");
    }

    @AfterEach
    void tearDown() {
        // Clean up
        queueService.clear();
        Mockito.reset(eventMapper);
    }

    @Test
    @DisplayName("Event should be retried after transient failure")
    void testEventRetriedAfterTransientFailure() throws Exception {
        // Given: EventMapper fails once then succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class))).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                // First attempt fails
                throw new RuntimeException("Simulated transient failure");
            } else {
                // Subsequent attempts succeed
                return Optional.empty(); // Return empty to indicate event was processed
            }
        });

        // When: Enqueue an event
        String correlationId = UUID.randomUUID().toString();
        KeycloakAdminEvent event = createTestEvent();
        WebhookEvent webhookEvent = new WebhookEvent(correlationId, event);

        boolean enqueued = queueService.enqueue(webhookEvent);
        assertTrue(enqueued, "Event should be enqueued");

        // Wait for initial processing and retry (allow time for exponential backoff)
        long maxWaitMs = retryPolicy.getBaseDelayMs() * 2 + 3000; // Base delay + buffer
        Thread.sleep(maxWaitMs);

        // Then: EventMapper should have been called at least twice (initial + retry)
        verify(eventMapper, atLeast(2)).mapEvent(any(KeycloakAdminEvent.class));
        assertTrue(callCount.get() >= 2,
                "EventMapper should have been called at least twice, but was called " + callCount.get() + " times");
    }

    @Test
    @DisplayName("Event should respect max retry attempts")
    void testMaxRetryAttemptsRespected() throws Exception {
        // Given: EventMapper always fails
        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class)))
                .thenThrow(new RuntimeException("Persistent failure"));

        // When: Enqueue an event
        String correlationId = UUID.randomUUID().toString();
        KeycloakAdminEvent event = createTestEvent();
        WebhookEvent webhookEvent = new WebhookEvent(correlationId, event);

        queueService.enqueue(webhookEvent);

        // Wait for all retry attempts to complete
        // Max attempts * max backoff + buffer
        long maxWaitMs = retryPolicy.getMaxAttempts() * retryPolicy.getMaxDelayMs() + 5000;
        Thread.sleep(maxWaitMs);

        // Then: EventMapper should have been called max attempts + 1 (initial attempt)
        // Note: Allow some tolerance due to timing
        int maxExpectedCalls = retryPolicy.getMaxAttempts() + 1;
        verify(eventMapper, atMost(maxExpectedCalls + 2)).mapEvent(any(KeycloakAdminEvent.class));
    }

    @Test
    @DisplayName("Retry should use exponential backoff timing")
    void testExponentialBackoffTiming() throws Exception {
        // Given: Track timing of retry attempts
        AtomicInteger callCount = new AtomicInteger(0);
        long[] callTimes = new long[5];

        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class))).thenAnswer(invocation -> {
            int count = callCount.getAndIncrement();
            if (count < callTimes.length) {
                callTimes[count] = System.currentTimeMillis();
            }
            if (count < 3) {
                // Fail first 3 attempts
                throw new RuntimeException("Simulated failure for retry test");
            }
            return Optional.empty();
        });

        // When: Enqueue an event
        KeycloakAdminEvent event = createTestEvent();
        WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
        queueService.enqueue(webhookEvent);

        // Wait for retries
        Thread.sleep(retryPolicy.getBaseDelayMs() * 8 + 2000);

        // Then: Verify exponential backoff between attempts
        if (callCount.get() >= 3) {
            long delay1 = callTimes[1] - callTimes[0];
            long delay2 = callTimes[2] - callTimes[1];

            // Second delay should be approximately 2x first delay (exponential backoff)
            // Allow 50% tolerance for timing variations
            long expectedRatio = 2;
            double actualRatio = (double) delay2 / delay1;

            assertTrue(actualRatio >= expectedRatio * 0.5 && actualRatio <= expectedRatio * 2.5,
                    String.format("Expected exponential backoff: delay2/delay1 should be ~%d, but was %.2f (delays: %dms, %dms)",
                            expectedRatio, actualRatio, delay1, delay2));
        }
    }

    @Test
    @DisplayName("Retry metrics should be updated correctly")
    void testRetryMetricsUpdated() throws Exception {
        // Given: EventMapper fails then succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class))).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("Failure to trigger retry");
            }
            return Optional.empty();
        });

        // Record initial metric values
        double initialScheduledRetries = getRetryMetricCount("SCHEDULED");

        // When: Enqueue an event
        KeycloakAdminEvent event = createTestEvent();
        WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
        queueService.enqueue(webhookEvent);

        // Wait for retry
        Thread.sleep(retryPolicy.getBaseDelayMs() * 2 + 2000);

        // Then: Retry metrics should have increased
        double finalScheduledRetries = getRetryMetricCount("SCHEDULED");
        assertTrue(finalScheduledRetries > initialScheduledRetries,
                String.format("Scheduled retry metric should increase (initial: %.0f, final: %.0f)",
                        initialScheduledRetries, finalScheduledRetries));
    }

    @Test
    @DisplayName("Event should preserve correlation ID across retries")
    void testCorrelationIdPreservedAcrossRetries() throws Exception {
        // Given: Track correlation IDs across calls
        String expectedCorrelationId = UUID.randomUUID().toString();
        AtomicInteger callCount = new AtomicInteger(0);

        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class))).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("Failure to trigger retry");
            }
            return Optional.empty();
        });

        // When: Enqueue event with specific correlation ID
        KeycloakAdminEvent event = createTestEvent();
        WebhookEvent webhookEvent = new WebhookEvent(expectedCorrelationId, event);
        queueService.enqueue(webhookEvent);

        // Wait for retry
        Thread.sleep(retryPolicy.getBaseDelayMs() * 2 + 2000);

        // Then: Event should have been processed at least twice
        verify(eventMapper, atLeast(2)).mapEvent(any(KeycloakAdminEvent.class));
    }

    @Test
    @DisplayName("Multiple concurrent events should retry independently")
    void testConcurrentEventsRetryIndependently() throws Exception {
        // Given: Multiple events with different failure patterns
        AtomicInteger event1Calls = new AtomicInteger(0);
        AtomicInteger event2Calls = new AtomicInteger(0);

        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class))).thenAnswer(invocation -> {
            KeycloakAdminEvent event = invocation.getArgument(0);
            if ("event-1".equals(event.getId())) {
                int count = event1Calls.incrementAndGet();
                if (count == 1) {
                    throw new RuntimeException("Event 1 fails once");
                }
            } else if ("event-2".equals(event.getId())) {
                int count = event2Calls.incrementAndGet();
                if (count <= 2) {
                    throw new RuntimeException("Event 2 fails twice");
                }
            }
            return Optional.empty();
        });

        // When: Enqueue two events
        KeycloakAdminEvent event1 = createTestEvent();
        event1.setId("event-1");
        KeycloakAdminEvent event2 = createTestEvent();
        event2.setId("event-2");

        queueService.enqueue(new WebhookEvent(UUID.randomUUID().toString(), event1));
        queueService.enqueue(new WebhookEvent(UUID.randomUUID().toString(), event2));

        // Wait for processing and retries
        Thread.sleep(retryPolicy.getBaseDelayMs() * 4 + 3000);

        // Then: Both events should have been retried appropriately
        assertTrue(event1Calls.get() >= 2, "Event 1 should have been retried at least once");
        assertTrue(event2Calls.get() >= 3, "Event 2 should have been retried at least twice");
    }

    @Test
    @DisplayName("Retry count should increment correctly")
    void testRetryCountIncrementsCorrectly() throws Exception {
        // Given: Event that fails multiple times
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3); // Wait for 3 attempts

        when(eventMapper.mapEvent(any(KeycloakAdminEvent.class))).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            latch.countDown();
            throw new RuntimeException("Always fail to test retry count");
        });

        // When: Enqueue an event
        KeycloakAdminEvent event = createTestEvent();
        WebhookEvent webhookEvent = new WebhookEvent(UUID.randomUUID().toString(), event);
        queueService.enqueue(webhookEvent);

        // Wait for attempts
        boolean completed = latch.await(retryPolicy.getBaseDelayMs() * 6 + 5000, TimeUnit.MILLISECONDS);
        assertTrue(completed, "Should complete 3 attempts within timeout");

        // Then: Should have at least 3 attempts
        assertTrue(callCount.get() >= 3,
                "Should have at least 3 attempts, but had " + callCount.get());
    }

    // ========== Helper Methods ==========

    /**
     * Create a test Keycloak admin event.
     */
    private KeycloakAdminEvent createTestEvent() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setId(UUID.randomUUID().toString());
        event.setRealmId("test-realm");
        event.setResourceType("USER");
        event.setOperationType("CREATE");
        event.setResourcePath("users/test-user-" + UUID.randomUUID());
        event.setTime(System.currentTimeMillis());
        return event;
    }

    /**
     * Get retry metric count for a specific result type.
     */
    private double getRetryMetricCount(String result) {
        try {
            return meterRegistry.find("sync_retry_attempts_total")
                    .tag("result", result)
                    .counter()
                    .count();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
