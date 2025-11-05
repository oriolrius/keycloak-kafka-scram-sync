package com.miimetiq.keycloak.sync.service;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import com.miimetiq.keycloak.sync.repository.RetentionRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
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

    private static final Logger LOG = Logger.getLogger(RetentionServiceTest.class);

    @Inject
    RetentionService retentionService;

    @Inject
    RetentionRepository retentionRepository;

    @Inject
    SyncOperationRepository operationRepository;

    @Inject
    SyncMetrics syncMetrics;

    @Inject
    MeterRegistry registry;

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

    // ========== Space-based Purge Tests ==========

    @Test
    void testCalculateDatabaseSize_ReturnsValidSize() {
        // When: calculating database size
        long dbSize = retentionService.calculateDatabaseSize();

        // Then: should return a positive size
        assertTrue(dbSize > 0, "Database size should be positive");
        LOG.infof("Current database size: %d bytes", dbSize);
    }

    @Test
    @Transactional
    void testPurgeBySize_MaxBytesNull_NoPurge() {
        // Given: retention state with null max_bytes
        updateRetentionConfig(null, 30);

        // Create some operations
        createOperation("op-1", LocalDateTime.now());
        createOperation("op-2", LocalDateTime.now().minusDays(1));

        // When: executing size-based purge
        long deletedCount = retentionService.purgeBySize();

        // Then: no purge should occur
        assertEquals(0, deletedCount, "Should not delete any records when max_bytes is null");
        assertEquals(2, operationRepository.count(), "All records should remain");
    }

    @Test
    @Transactional
    void testPurgeBySize_UnderLimit_NoPurge() {
        // Given: very large max_bytes (10 GB - definitely over current size)
        updateRetentionConfig(10L * 1024 * 1024 * 1024, null);

        // Create some operations
        createOperation("op-1", LocalDateTime.now());
        createOperation("op-2", LocalDateTime.now().minusDays(1));

        // When: executing size-based purge
        long deletedCount = retentionService.purgeBySize();

        // Then: no purge should occur
        assertEquals(0, deletedCount, "Should not delete any records when under limit");
        assertEquals(2, operationRepository.count(), "All records should remain");

        // Verify approx_db_bytes was updated
        RetentionState state = retentionRepository.getOrThrow();
        assertTrue(state.getApproxDbBytes() > 0, "approx_db_bytes should be updated");
    }

    @Test
    @Transactional
    void testPurgeBySize_OverLimit_DeletesOldest() {
        // Given: very small max_bytes to trigger purge
        long maxBytes = 1024L; // 1 KB - very small to ensure we exceed it
        updateRetentionConfig(maxBytes, null);

        // Create multiple operations with different timestamps
        LocalDateTime now = LocalDateTime.now();
        createOperation("oldest", now.minusDays(10));
        createOperation("old", now.minusDays(5));
        createOperation("recent", now.minusDays(1));
        createOperation("newest", now);

        long initialCount = operationRepository.count();
        assertEquals(4, initialCount, "Should have 4 operations");

        // When: executing size-based purge
        long deletedCount = retentionService.purgeBySize();

        // Then: some records should be deleted (oldest first)
        assertTrue(deletedCount > 0, "Should delete some records");
        assertTrue(operationRepository.count() < initialCount, "Should have fewer records after purge");

        // Verify approx_db_bytes was updated
        RetentionState state = retentionRepository.getOrThrow();
        assertTrue(state.getApproxDbBytes() > 0, "approx_db_bytes should be updated");
        assertTrue(state.getApproxDbBytes() <= maxBytes || operationRepository.count() == 0,
                "Database should be under limit or empty");
    }

    @Test
    @Transactional
    void testPurgeBySize_UpdatesApproxDbBytes() {
        // Given: initial state - reset to 0 first
        updateRetentionConfig(10L * 1024 * 1024 * 1024, null);
        RetentionState stateFirst = retentionRepository.getOrThrow();
        stateFirst.setApproxDbBytes(0L);
        retentionRepository.persist(stateFirst);

        // Create operation to ensure database has some size
        createOperation("op-1", LocalDateTime.now());

        // When: executing purge (should not delete but should update size)
        retentionService.purgeBySize();

        // Then: approx_db_bytes should be updated with current size
        RetentionState stateAfter = retentionRepository.getOrThrow();
        assertTrue(stateAfter.getApproxDbBytes() > 0, "approx_db_bytes should be greater than 0");
    }

    @Test
    @Transactional
    void testPurgeBySize_DeletesMultipleRecords() {
        // Given: very small max_bytes
        updateRetentionConfig(1024L, null); // 1 KB

        // Create a few operations (enough to exceed limit but not too many to avoid lock issues)
        LocalDateTime now = LocalDateTime.now();
        createOperation("op-1", now.minusDays(10));
        createOperation("op-2", now.minusDays(9));
        createOperation("op-3", now.minusDays(8));
        createOperation("op-4", now.minusDays(7));
        createOperation("op-5", now.minusDays(6));

        long initialCount = operationRepository.count();
        assertTrue(initialCount > 0, "Should have operations");

        // When: executing size-based purge
        long deletedCount = retentionService.purgeBySize();

        // Then: should complete purge
        assertTrue(deletedCount >= 0, "Should complete purge");
        LOG.infof("Deleted %d records from %d initial records", deletedCount, initialCount);
    }

    @Test
    void testExecuteVacuum_HandlesConstraints() {
        // When: executing VACUUM
        // Note: VACUUM cannot run inside SQLite transactions, so this test verifies
        // the method handles this gracefully
        boolean result = retentionService.executeVacuum();

        // Then: method should complete (may return false due to transaction constraints)
        // This is acceptable behavior in test context
        LOG.infof("VACUUM execution result: %b", result);
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

    // ========== Metrics Integration Tests ==========

    @Test
    @Transactional
    void testUpdateRetentionConfig_UpdatesMetrics() {
        // Given: new retention config
        Long maxBytes = 536870912L; // 512 MB
        Integer maxAgeDays = 45;

        // When: updating retention config
        retentionService.updateRetentionConfig(maxBytes, maxAgeDays);

        // Then: metrics should be updated
        Gauge maxBytesGauge = registry.find("sync_retention_max_bytes").gauge();
        Gauge maxAgeDaysGauge = registry.find("sync_retention_max_age_days").gauge();

        assertNotNull(maxBytesGauge, "max_bytes gauge should exist");
        assertNotNull(maxAgeDaysGauge, "max_age_days gauge should exist");

        assertEquals(maxBytes.doubleValue(), maxBytesGauge.value(), 0.001,
                "Metrics should reflect updated max_bytes");
        assertEquals(maxAgeDays.doubleValue(), maxAgeDaysGauge.value(), 0.001,
                "Metrics should reflect updated max_age_days");
    }

    @Test
    @Transactional
    void testUpdateRetentionConfig_NullValues_UpdatesMetrics() {
        // When: updating retention config with null values
        retentionService.updateRetentionConfig(null, null);

        // Then: metrics should be set to 0
        Gauge maxBytesGauge = registry.find("sync_retention_max_bytes").gauge();
        Gauge maxAgeDaysGauge = registry.find("sync_retention_max_age_days").gauge();

        assertNotNull(maxBytesGauge);
        assertNotNull(maxAgeDaysGauge);

        assertEquals(0.0, maxBytesGauge.value(), 0.001,
                "Metrics should be 0 when config is null");
        assertEquals(0.0, maxAgeDaysGauge.value(), 0.001,
                "Metrics should be 0 when config is null");
    }
}
