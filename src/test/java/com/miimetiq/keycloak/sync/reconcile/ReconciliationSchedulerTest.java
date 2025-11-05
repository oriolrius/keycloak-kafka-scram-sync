package com.miimetiq.keycloak.sync.reconcile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReconciliationScheduler.
 * <p>
 * Tests scheduled execution, manual trigger, overlap prevention, and error handling.
 */
@QuarkusTest
@DisplayName("ReconciliationScheduler Tests")
class ReconciliationSchedulerTest {

    @Inject
    ReconciliationScheduler scheduler;

    private ReconciliationService reconciliationService;
    private ReconcileConfig reconcileConfig;

    @BeforeEach
    void setUp() {
        // Create mocks
        reconciliationService = mock(ReconciliationService.class);
        reconcileConfig = mock(ReconcileConfig.class);

        // Install mocks using QuarkusMock
        QuarkusMock.installMockForType(reconciliationService, ReconciliationService.class);
        QuarkusMock.installMockForType(reconcileConfig, ReconcileConfig.class);

        // Default config: scheduler enabled
        when(reconcileConfig.schedulerEnabled()).thenReturn(true);
        when(reconcileConfig.intervalSeconds()).thenReturn(120);
    }

    @Test
    @DisplayName("Manual trigger should call reconciliation service with MANUAL source")
    void testManualTrigger_Success() {
        // Given: mock successful reconciliation
        ReconciliationResult mockResult = new ReconciliationResult(
                "test-correlation-id",
                LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(5),
                "MANUAL",
                10,
                10,
                0
        );
        when(reconciliationService.performReconciliation("MANUAL")).thenReturn(mockResult);

        // When: triggering manual reconciliation
        ReconciliationResult result = scheduler.triggerManualReconciliation();

        // Then: should return result and call service
        assertNotNull(result);
        assertEquals("test-correlation-id", result.getCorrelationId());
        assertEquals(10, result.getSuccessfulOperations());
        verify(reconciliationService, times(1)).performReconciliation("MANUAL");
    }

    @Test
    @DisplayName("Manual trigger should reject concurrent requests")
    void testManualTrigger_OverlapPrevention() throws Exception {
        // Given: slow reconciliation that takes time
        when(reconciliationService.performReconciliation("MANUAL"))
                .thenAnswer(invocation -> {
                    Thread.sleep(100); // Simulate slow reconciliation
                    return new ReconciliationResult(
                            "test-id",
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            "MANUAL",
                            1,
                            1,
                            0
                    );
                });

        // When: triggering first reconciliation in background thread
        Thread thread1 = new Thread(() -> scheduler.triggerManualReconciliation());
        thread1.start();

        // Give it time to start
        Thread.sleep(10);

        // Then: second trigger should fail with exception
        assertThrows(
                ReconciliationScheduler.ReconciliationInProgressException.class,
                () -> scheduler.triggerManualReconciliation(),
                "Should throw exception when reconciliation already in progress"
        );

        // Wait for first thread to complete
        thread1.join();

        // Verify only one reconciliation was called
        verify(reconciliationService, times(1)).performReconciliation("MANUAL");
    }

    @Test
    @DisplayName("Scheduler should handle exceptions gracefully and continue running")
    void testScheduler_ExceptionHandling() {
        // Given: reconciliation service throws exception
        when(reconciliationService.performReconciliation("SCHEDULED"))
                .thenThrow(new RuntimeException("Test exception"));

        // When: scheduled method is called
        // Then: should not throw exception (scheduler should catch it)
        assertDoesNotThrow(() -> scheduler.scheduledReconciliation());

        // Verify reconciliation was attempted
        verify(reconciliationService, times(1)).performReconciliation("SCHEDULED");
    }

    @Test
    @DisplayName("Scheduler should skip execution when disabled")
    void testScheduler_Disabled() {
        // Given: scheduler is disabled
        when(reconcileConfig.schedulerEnabled()).thenReturn(false);

        // When: scheduled method is called
        scheduler.scheduledReconciliation();

        // Then: reconciliation service should not be called
        verify(reconciliationService, never()).performReconciliation(anyString());
    }

    @Test
    @DisplayName("isReconciliationRunning should return correct status")
    void testIsReconciliationRunning() throws Exception {
        // Initially should not be running
        assertFalse(scheduler.isReconciliationRunning(), "Should not be running initially");

        // Given: slow reconciliation
        when(reconciliationService.performReconciliation("MANUAL"))
                .thenAnswer(invocation -> {
                    Thread.sleep(100);
                    return new ReconciliationResult(
                            "test-id",
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            "MANUAL",
                            1,
                            1,
                            0
                    );
                });

        // When: starting reconciliation in background
        Thread thread = new Thread(() -> scheduler.triggerManualReconciliation());
        thread.start();

        // Give it time to start
        Thread.sleep(10);

        // Then: should be running
        assertTrue(scheduler.isReconciliationRunning(), "Should be running during reconciliation");

        // Wait for completion
        thread.join();

        // Should not be running after completion
        assertFalse(scheduler.isReconciliationRunning(), "Should not be running after completion");
    }

    @Test
    @DisplayName("Manual trigger should reset running flag even if reconciliation fails")
    void testManualTrigger_ExceptionResetsFlag() {
        // Given: reconciliation throws exception
        when(reconciliationService.performReconciliation("MANUAL"))
                .thenThrow(new RuntimeException("Test exception"));

        // When: triggering manual reconciliation (should throw)
        assertThrows(RuntimeException.class, () -> scheduler.triggerManualReconciliation());

        // Then: flag should be reset, allowing next reconciliation
        assertFalse(scheduler.isReconciliationRunning(), "Flag should be reset after exception");

        // Next trigger should work
        when(reconciliationService.performReconciliation("MANUAL"))
                .thenReturn(new ReconciliationResult(
                        "test-id",
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        "MANUAL",
                        1,
                        1,
                        0
                ));

        assertDoesNotThrow(() -> scheduler.triggerManualReconciliation(),
                "Should be able to trigger again after exception");
    }
}
