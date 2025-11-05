package com.miimetiq.keycloak.sync.service;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.repository.RetentionRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RetentionService.
 * <p>
 * Tests TTL-based purge logic, configuration management, and edge cases.
 */
@QuarkusTest
class RetentionServiceTest {

    @Inject
    RetentionService retentionService;

    @Inject
    RetentionRepository retentionRepository;

    @Inject
    SyncOperationRepository operationRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data before each test
        operationRepository.deleteAll();

        // Reset retention state to known values
        RetentionState state = retentionRepository.getOrThrow();
        state.setMaxAgeDays(30);
        state.setMaxBytes(null);
        state.setUpdatedAt(LocalDateTime.now());
        retentionRepository.persist(state);
    }

    @Test
    void testPurgeTtl_DeletesOldRecords() {
        // Given: operations with various ages
        LocalDateTime now = LocalDateTime.now();
        createOperation("old-op-1", now.minusDays(35)); // Older than 30 days
        createOperation("old-op-2", now.minusDays(40)); // Older than 30 days
        createOperation("recent-op-1", now.minusDays(10)); // Within 30 days
        createOperation("recent-op-2", now.minusDays(5)); // Within 30 days

        // When: executing TTL purge (max_age_days = 30)
        long deletedCount = retentionService.purgeTtl();

        // Then: old records should be deleted, recent ones retained
        assertEquals(2, deletedCount, "Should delete 2 old records");

        long remainingCount = operationRepository.count();
        assertEquals(2, remainingCount, "Should have 2 recent records remaining");

        // Verify recent operations are still present
        List<SyncOperation> remaining = operationRepository.listAll();
        assertTrue(remaining.stream().anyMatch(op -> op.getCorrelationId().equals("recent-op-1")));
        assertTrue(remaining.stream().anyMatch(op -> op.getCorrelationId().equals("recent-op-2")));
    }

    @Test
    @Transactional
    void testPurgeTtl_UpdatesRetentionStateTimestamp() {
        // Given: initial retention state
        RetentionState stateBefore = retentionRepository.getOrThrow();
        LocalDateTime updatedAtBefore = stateBefore.getUpdatedAt();

        // Create one old operation
        createOperation("old-op", LocalDateTime.now().minusDays(35));

        // Wait a bit to ensure timestamp changes
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: executing purge
        retentionService.purgeTtl();

        // Then: retention state timestamp should be updated
        RetentionState stateAfter = retentionRepository.getOrThrow();
        assertTrue(stateAfter.getUpdatedAt().isAfter(updatedAtBefore),
                "Retention state updated_at should be newer");
    }

    @Test
    void testPurgeTtl_NoRecordsToDelete() {
        // Given: only recent operations
        LocalDateTime now = LocalDateTime.now();
        createOperation("recent-op-1", now.minusDays(10));
        createOperation("recent-op-2", now.minusDays(5));

        // When: executing TTL purge
        long deletedCount = retentionService.purgeTtl();

        // Then: no records should be deleted
        assertEquals(0, deletedCount, "Should delete 0 records");
        assertEquals(2, operationRepository.count(), "All records should remain");
    }

    @Test
    void testPurgeTtl_AllRecordsExpired() {
        // Given: all operations are old
        LocalDateTime now = LocalDateTime.now();
        createOperation("old-op-1", now.minusDays(35));
        createOperation("old-op-2", now.minusDays(40));
        createOperation("old-op-3", now.minusDays(50));

        // When: executing TTL purge
        long deletedCount = retentionService.purgeTtl();

        // Then: all records should be deleted
        assertEquals(3, deletedCount, "Should delete all 3 records");
        assertEquals(0, operationRepository.count(), "No records should remain");
    }

    @Test
    @Transactional
    void testPurgeTtl_MaxAgeDaysNull_NoPurge() {
        // Given: retention state with null max_age_days
        RetentionState state = retentionRepository.getOrThrow();
        state.setMaxAgeDays(null);
        retentionRepository.persist(state);

        // Create old operation
        createOperation("old-op", LocalDateTime.now().minusDays(100));

        // When: executing purge
        long deletedCount = retentionService.purgeTtl();

        // Then: no purge should occur
        assertEquals(0, deletedCount, "Should not delete any records when max_age_days is null");
        assertEquals(1, operationRepository.count(), "Operation should remain");
    }

    @Test
    void testPurgeTtl_ExactBoundary() {
        // Given: operations at different boundaries relative to retention period
        LocalDateTime now = LocalDateTime.now();

        // Create operation well within retention (25 days old - clearly kept)
        createOperation("recent-op", now.minusDays(25));

        // Create operation well beyond retention (35 days old - clearly deleted)
        createOperation("old-op", now.minusDays(35));

        // When: executing purge (max_age_days = 30)
        long deletedCount = retentionService.purgeTtl();

        // Then: old operation should be deleted, recent one kept
        assertEquals(1, deletedCount, "Should delete operation beyond retention period");
        assertEquals(1, operationRepository.count(), "Should keep operation within retention period");
    }

    @Test
    @Transactional
    void testGetRetentionState() {
        // When: retrieving retention state
        RetentionState state = retentionService.getRetentionState();

        // Then: should return the singleton state
        assertNotNull(state);
        assertEquals(RetentionState.SINGLETON_ID, state.getId());
        assertNotNull(state.getUpdatedAt());
    }

    @Test
    @Transactional
    void testUpdateRetentionConfig() {
        // Given: new configuration values
        Long newMaxBytes = 500000000L; // 500 MB
        Integer newMaxAgeDays = 60;

        // When: updating configuration
        retentionService.updateRetentionConfig(newMaxBytes, newMaxAgeDays);

        // Then: retention state should be updated
        RetentionState state = retentionRepository.getOrThrow();
        assertEquals(newMaxBytes, state.getMaxBytes());
        assertEquals(newMaxAgeDays, state.getMaxAgeDays());
    }

    @Test
    @Transactional
    void testUpdateRetentionConfig_NullValues() {
        // Given: null values to disable limits

        // When: updating configuration with nulls
        retentionService.updateRetentionConfig(null, null);

        // Then: retention state should have null limits
        RetentionState state = retentionRepository.getOrThrow();
        assertNull(state.getMaxBytes());
        assertNull(state.getMaxAgeDays());
        assertFalse(state.hasMaxBytesLimit());
        assertFalse(state.hasMaxAgeLimit());
    }

    @Test
    void testPurgeTtl_CorrectTtlCalculation() {
        // Given: max_age_days = 7 (custom value)
        updateRetentionConfig(null, 7);

        LocalDateTime now = LocalDateTime.now();
        createOperation("old-10-days", now.minusDays(10));  // Well beyond 7 days - should be deleted
        createOperation("recent-5-days", now.minusDays(5)); // Well within 7 days - should be kept

        // When: executing purge
        long deletedCount = retentionService.purgeTtl();

        // Then: correct calculation based on 7 days
        assertEquals(1, deletedCount, "Should delete only records older than 7 days");
        assertEquals(1, operationRepository.count(), "Should keep 1 recent record");
    }

    // Helper methods

    @Transactional
    void createOperation(String correlationId, LocalDateTime occurredAt) {
        SyncOperation operation = new SyncOperation();
        operation.setCorrelationId(correlationId);
        operation.setOccurredAt(occurredAt);
        operation.setRealm("test-realm");
        operation.setClusterId("test-cluster");
        operation.setPrincipal("test-user");
        operation.setOpType(OpType.SCRAM_UPSERT);
        operation.setResult(OperationResult.SUCCESS);
        operation.setDurationMs(100);

        operationRepository.persist(operation);
    }

    @Transactional
    void updateRetentionConfig(Long maxBytes, Integer maxAgeDays) {
        retentionService.updateRetentionConfig(maxBytes, maxAgeDays);
    }
}
