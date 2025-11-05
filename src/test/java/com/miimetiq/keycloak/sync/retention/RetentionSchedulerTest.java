package com.miimetiq.keycloak.sync.retention;

import com.miimetiq.keycloak.sync.service.RetentionService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetentionScheduler.
 * <p>
 * Tests scheduled purge execution, post-sync purge trigger, overlap prevention, and error handling.
 */
@QuarkusTest
@DisplayName("RetentionScheduler Tests")
class RetentionSchedulerTest {

    @Inject
    RetentionScheduler scheduler;

    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        // Create mock for RetentionService
        retentionService = mock(RetentionService.class);

        // Install mock using QuarkusMock
        QuarkusMock.installMockForType(retentionService, RetentionService.class);
    }

    @Test
    @DisplayName("executePurge should call both TTL and size-based purge")
    void testExecutePurge_Success() {
        // Given: mock successful purge operations
        when(retentionService.purgeTtl()).thenReturn(10L);
        when(retentionService.purgeBySize()).thenReturn(5L);
        when(retentionService.executeVacuum()).thenReturn(true);

        // When: executing purge with scheduled reason
        RetentionScheduler.PurgeResult result = scheduler.executePurge("scheduled");

        // Then: should call both purge methods and vacuum
        assertNotNull(result);
        assertEquals(10L, result.ttlDeleted);
        assertEquals(5L, result.sizeDeleted);

        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
        verify(retentionService, times(1)).executeVacuum();
    }

    @Test
    @DisplayName("executePurge should skip vacuum if no records deleted")
    void testExecutePurge_NoRecordsDeleted_SkipsVacuum() {
        // Given: no records to delete
        when(retentionService.purgeTtl()).thenReturn(0L);
        when(retentionService.purgeBySize()).thenReturn(0L);

        // When: executing purge with post-batch reason
        RetentionScheduler.PurgeResult result = scheduler.executePurge("post-batch");

        // Then: should not call vacuum
        assertNotNull(result);
        assertEquals(0L, result.ttlDeleted);
        assertEquals(0L, result.sizeDeleted);

        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
        verify(retentionService, never()).executeVacuum();
    }

    @Test
    @DisplayName("executePurge should continue with size purge even if TTL fails")
    void testExecutePurge_TtlFailsContinuesWithSize() {
        // Given: TTL purge fails but size purge succeeds
        when(retentionService.purgeTtl()).thenThrow(new RuntimeException("TTL purge failed"));
        when(retentionService.purgeBySize()).thenReturn(5L);
        when(retentionService.executeVacuum()).thenReturn(true);

        // When: executing purge
        RetentionScheduler.PurgeResult result = scheduler.executePurge("scheduled");

        // Then: should continue with size purge and vacuum
        assertNotNull(result);
        assertEquals(0L, result.ttlDeleted); // Failed, so 0
        assertEquals(5L, result.sizeDeleted);

        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
        verify(retentionService, times(1)).executeVacuum();
    }

    @Test
    @DisplayName("executePurge should continue with vacuum even if size purge fails")
    void testExecutePurge_SizeFailsContinuesWithVacuum() {
        // Given: size purge fails
        when(retentionService.purgeTtl()).thenReturn(10L);
        when(retentionService.purgeBySize()).thenThrow(new RuntimeException("Size purge failed"));
        when(retentionService.executeVacuum()).thenReturn(true);

        // When: executing purge
        RetentionScheduler.PurgeResult result = scheduler.executePurge("scheduled");

        // Then: should still call vacuum (TTL deleted records)
        assertNotNull(result);
        assertEquals(10L, result.ttlDeleted);
        assertEquals(0L, result.sizeDeleted); // Failed, so 0

        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
        verify(retentionService, times(1)).executeVacuum();
    }

    @Test
    @DisplayName("executePurge should not fail if vacuum fails")
    void testExecutePurge_VacuumFailsDoesNotFail() {
        // Given: vacuum fails
        when(retentionService.purgeTtl()).thenReturn(10L);
        when(retentionService.purgeBySize()).thenReturn(5L);
        when(retentionService.executeVacuum()).thenThrow(new RuntimeException("Vacuum failed"));

        // When: executing purge
        RetentionScheduler.PurgeResult result = scheduler.executePurge("scheduled");

        // Then: should complete successfully despite vacuum failure
        assertNotNull(result);
        assertEquals(10L, result.ttlDeleted);
        assertEquals(5L, result.sizeDeleted);

        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
        verify(retentionService, times(1)).executeVacuum();
    }

    @Test
    @DisplayName("scheduledPurge should handle exceptions gracefully and continue running")
    void testScheduledPurge_ExceptionHandling() {
        // Given: purge throws exception
        when(retentionService.purgeTtl()).thenThrow(new RuntimeException("Test exception"));
        when(retentionService.purgeBySize()).thenThrow(new RuntimeException("Test exception"));

        // When: scheduled method is called
        // Then: should not throw exception (scheduler should catch it)
        assertDoesNotThrow(() -> scheduler.scheduledPurge());

        // Verify purge was attempted
        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
    }

    @Test
    @DisplayName("triggerPostSyncPurge should execute purge and log results")
    void testTriggerPostSyncPurge_Success() {
        // Given: successful purge
        when(retentionService.purgeTtl()).thenReturn(3L);
        when(retentionService.purgeBySize()).thenReturn(2L);
        when(retentionService.executeVacuum()).thenReturn(true);

        // When: triggering post-sync purge
        assertDoesNotThrow(() -> scheduler.triggerPostSyncPurge());

        // Then: should call purge methods
        verify(retentionService, times(1)).purgeTtl();
        verify(retentionService, times(1)).purgeBySize();
        verify(retentionService, times(1)).executeVacuum();
    }

    @Test
    @DisplayName("triggerPostSyncPurge should not fail if purge throws exception")
    void testTriggerPostSyncPurge_ExceptionHandling() {
        // Given: purge throws exception
        when(retentionService.purgeTtl()).thenThrow(new RuntimeException("Purge failed"));

        // When: triggering post-sync purge
        // Then: should not throw exception
        assertDoesNotThrow(() -> scheduler.triggerPostSyncPurge());

        // Verify purge was attempted
        verify(retentionService, times(1)).purgeTtl();
    }

    @Test
    @DisplayName("triggerPostSyncPurge should skip if scheduled purge is already running")
    void testTriggerPostSyncPurge_SkipsIfRunning() throws Exception {
        // Given: slow purge operation
        when(retentionService.purgeTtl()).thenAnswer(invocation -> {
            Thread.sleep(100);
            return 10L;
        });
        when(retentionService.purgeBySize()).thenReturn(5L);
        when(retentionService.executeVacuum()).thenReturn(true);

        // When: starting scheduled purge in background
        Thread thread = new Thread(() -> scheduler.scheduledPurge());
        thread.start();

        // Give it time to start
        Thread.sleep(10);

        // Then: post-sync purge should be skipped
        scheduler.triggerPostSyncPurge();

        // Wait for scheduled purge to complete
        thread.join();

        // Verify only scheduled purge ran (TTL called once)
        verify(retentionService, times(1)).purgeTtl();
    }

    @Test
    @DisplayName("isPurgeRunning should return correct status")
    void testIsPurgeRunning() throws Exception {
        // Initially should not be running
        assertFalse(scheduler.isPurgeRunning(), "Should not be running initially");

        // Given: slow purge operation
        when(retentionService.purgeTtl()).thenAnswer(invocation -> {
            Thread.sleep(100);
            return 10L;
        });
        when(retentionService.purgeBySize()).thenReturn(5L);
        when(retentionService.executeVacuum()).thenReturn(true);

        // When: starting purge in background
        Thread thread = new Thread(() -> scheduler.scheduledPurge());
        thread.start();

        // Give it time to start
        Thread.sleep(10);

        // Then: should be running
        assertTrue(scheduler.isPurgeRunning(), "Should be running during purge");

        // Wait for completion
        thread.join();

        // Should not be running after completion
        assertFalse(scheduler.isPurgeRunning(), "Should not be running after completion");
    }

    @Test
    @DisplayName("scheduledPurge should reset running flag even if purge fails")
    void testScheduledPurge_ExceptionResetsFlag() {
        // Given: purge throws exception
        when(retentionService.purgeTtl()).thenThrow(new RuntimeException("Test exception"));
        when(retentionService.purgeBySize()).thenReturn(0L); // Mock purgeBySize to avoid further exceptions

        // When: scheduled purge is called
        scheduler.scheduledPurge();

        // Then: flag should be reset
        assertFalse(scheduler.isPurgeRunning(), "Flag should be reset after exception");

        // Next purge should work - reset mock and set up new behavior
        reset(retentionService);
        when(retentionService.purgeTtl()).thenReturn(5L);
        when(retentionService.purgeBySize()).thenReturn(3L);
        when(retentionService.executeVacuum()).thenReturn(true);

        assertDoesNotThrow(() -> scheduler.scheduledPurge(),
                "Should be able to run again after exception");
    }
}
