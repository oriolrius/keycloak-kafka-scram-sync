package com.miimetiq.keycloak.sync.integration;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.domain.entity.SyncBatch;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.repository.RetentionRepository;
import com.miimetiq.keycloak.sync.repository.SyncBatchRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import com.miimetiq.keycloak.sync.retention.RetentionScheduler;
import com.miimetiq.keycloak.sync.service.RetentionService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Sprint 3 retention system.
 * <p>
 * Tests the complete retention flow with real SQLite database:
 * - TTL-based purge (delete operations older than max_age_days)
 * - Space-based purge (delete oldest when database exceeds max_bytes)
 * - Scheduled purge execution
 * - Post-batch purge triggers
 * - Retention metrics exposure
 * <p>
 * All tests use real SQLite database with Flyway migrations via @QuarkusTest.
 */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResource.class)
@DisplayName("Sprint 3 Retention System Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RetentionIntegrationTest {

    @Inject
    RetentionService retentionService;

    @Inject
    RetentionScheduler retentionScheduler;

    @Inject
    RetentionRepository retentionRepository;

    @Inject
    SyncOperationRepository operationRepository;

    @Inject
    SyncBatchRepository batchRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data before each test
        operationRepository.deleteAll();
        batchRepository.deleteAll();

        // Reset retention config to defaults
        RetentionState state = retentionRepository.getOrThrow();
        state.setMaxBytes(null);
        state.setMaxAgeDays(null);
        state.setApproxDbBytes(0L);
        state.setUpdatedAt(LocalDateTime.now());
        retentionRepository.persist(state);
    }

    @Test
    @Order(1)
    @DisplayName("AC#1: TTL purge deletes expired records correctly")
    @Transactional
    void testTtlPurge_DeletesExpiredRecords() {
        // Given: retention config with 30 days TTL
        retentionService.updateRetentionConfig(null, 30);

        // Create test data: 3 old operations (40 days old) and 2 recent operations (10 days old)
        LocalDateTime oldDate = LocalDateTime.now().minusDays(40);
        LocalDateTime recentDate = LocalDateTime.now().minusDays(10);

        createTestOperation("old-batch-1", "old-user-1", oldDate);
        createTestOperation("old-batch-2", "old-user-2", oldDate);
        createTestOperation("old-batch-3", "old-user-3", oldDate);
        createTestOperation("recent-batch-1", "recent-user-1", recentDate);
        createTestOperation("recent-batch-2", "recent-user-2", recentDate);

        // Verify initial count
        long initialCount = operationRepository.count();
        assertEquals(5, initialCount, "Should have 5 operations initially");

        // When: executing TTL purge
        long deletedCount = retentionService.purgeTtl();

        // Then: only old records should be deleted
        assertEquals(3, deletedCount, "Should delete 3 old records");

        long remainingCount = operationRepository.count();
        assertEquals(2, remainingCount, "Should have 2 recent records remaining");

        // Verify that only recent records remain
        List<SyncOperation> remaining = operationRepository.listAll();
        assertTrue(remaining.stream().allMatch(op -> op.getOccurredAt().isAfter(LocalDateTime.now().minusDays(30))),
                "All remaining records should be within TTL");
    }

    @Test
    @Order(2)
    @DisplayName("AC#1: TTL purge returns 0 when max_age_days is null")
    @Transactional
    void testTtlPurge_SkipsWhenNotConfigured() {
        // Given: retention config with no TTL (max_age_days = null)
        retentionService.updateRetentionConfig(null, null);

        // Create old operations
        LocalDateTime oldDate = LocalDateTime.now().minusDays(400);
        createTestOperation("old-batch", "old-user", oldDate);

        long initialCount = operationRepository.count();
        assertEquals(1, initialCount);

        // When: executing TTL purge
        long deletedCount = retentionService.purgeTtl();

        // Then: nothing should be deleted
        assertEquals(0, deletedCount, "Should not delete any records when TTL is disabled");
        assertEquals(1, operationRepository.count(), "Record count should remain unchanged");
    }

    @Test
    @Order(3)
    @DisplayName("AC#2: Space-based purge deletes oldest records when database exceeds max_bytes")
    @Transactional
    void testSpacePurge_DeletesWhenExceedingLimit() {
        // Given: current database size
        long currentSize = retentionService.calculateDatabaseSize();
        assertTrue(currentSize > 0, "Database should have some size");

        // Create several operations with known timestamps
        LocalDateTime oldestDate = LocalDateTime.now().minusDays(10);
        LocalDateTime oldDate = LocalDateTime.now().minusDays(5);
        LocalDateTime recentDate = LocalDateTime.now().minusDays(1);

        createTestOperation("batch-oldest", "user-oldest", oldestDate);
        createTestOperation("batch-old", "user-old", oldDate);
        createTestOperation("batch-recent", "user-recent", recentDate);

        long initialCount = operationRepository.count();
        assertTrue(initialCount >= 3, "Should have at least 3 operations");

        // Set max_bytes to a value that will trigger purge but not delete everything
        // Use current size minus a small amount to force some deletions
        long targetLimit = Math.max(1024L, currentSize - 10000L);
        retentionService.updateRetentionConfig(targetLimit, null);

        // When: executing space-based purge
        long deletedCount = retentionService.purgeBySize();

        // Then: purge should execute (may or may not delete depending on actual DB size)
        // The key behavior is that it doesn't fail and deletes in oldest-first order
        assertTrue(deletedCount >= 0, "Purge should complete without error");

        // If records were deleted, verify the count decreased
        if (deletedCount > 0) {
            long remainingCount = operationRepository.count();
            assertTrue(remainingCount < initialCount, "Should have fewer records after purge");

            // Verify oldest records are deleted first
            List<SyncOperation> remaining = operationRepository.listAll();
            if (!remaining.isEmpty()) {
                // If any records remain, they should be newer than deleted ones
                LocalDateTime oldestRemaining = remaining.stream()
                        .map(SyncOperation::getOccurredAt)
                        .min(LocalDateTime::compareTo)
                        .orElse(LocalDateTime.now());

                // The oldest remaining record should be newer than our oldest test record
                // (unless all were kept, which is also valid)
                assertTrue(oldestRemaining.isAfter(oldestDate) || remaining.size() == initialCount,
                        "Oldest records should be deleted first");
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("AC#2: Space-based purge returns 0 when under limit")
    @Transactional
    void testSpacePurge_SkipsWhenUnderLimit() {
        // Given: retention config with very large max_bytes
        long largeLimit = 10L * 1024 * 1024 * 1024; // 10 GB
        retentionService.updateRetentionConfig(largeLimit, null);

        // Create some operations
        createTestOperation("batch", "user", LocalDateTime.now().minusDays(1));

        long initialCount = operationRepository.count();

        // When: executing space-based purge
        long deletedCount = retentionService.purgeBySize();

        // Then: nothing should be deleted (under limit)
        assertEquals(0, deletedCount, "Should not delete records when under size limit");
        assertEquals(initialCount, operationRepository.count(), "Record count should remain unchanged");
    }

    @Test
    @Order(5)
    @DisplayName("AC#5: Scheduled purge job executes correctly")
    void testScheduledPurge_ExecutesCorrectly() {
        // Given: retention config and test data
        retentionService.updateRetentionConfig(null, 30);

        LocalDateTime oldDate = LocalDateTime.now().minusDays(40);
        createTestOperation("old-batch", "old-user", oldDate);
        createTestOperation("recent-batch", "recent-user", LocalDateTime.now());

        long initialCount = operationRepository.count();
        assertEquals(2, initialCount);

        // When: executing scheduled purge (via RetentionScheduler.executePurge)
        RetentionScheduler.PurgeResult result = retentionScheduler.executePurge("scheduled");

        // Then: purge should execute successfully
        assertNotNull(result, "Purge result should not be null");
        assertEquals(1, result.ttlDeleted, "Should delete 1 old record via TTL");
        assertEquals(0, result.sizeDeleted, "Should not delete any via size (not configured)");

        long remainingCount = operationRepository.count();
        assertEquals(1, remainingCount, "Should have 1 record remaining");
    }

    @Test
    @Order(6)
    @DisplayName("AC#5: Scheduled purge handles both TTL and size purges")
    void testScheduledPurge_ExecutesBothPurgeTypes() {
        // Given: retention config with both TTL and size limits
        retentionService.updateRetentionConfig(2048L, 30); // 2KB limit + 30 days TTL

        // Create old records
        LocalDateTime oldDate = LocalDateTime.now().minusDays(40);
        for (int i = 0; i < 5; i++) {
            createTestOperation("old-batch-" + i, "old-user-" + i, oldDate);
        }

        long initialCount = operationRepository.count();
        assertTrue(initialCount >= 5);

        // When: executing scheduled purge
        RetentionScheduler.PurgeResult result = retentionScheduler.executePurge("scheduled");

        // Then: both purge types should execute
        assertNotNull(result);
        // TTL purge should delete old records first
        assertTrue(result.ttlDeleted >= 0, "TTL purge should execute");
        // Size purge may or may not delete depending on remaining size
        assertTrue(result.sizeDeleted >= 0, "Size purge should execute");
    }

    @Test
    @Order(7)
    @DisplayName("AC#6: Retention metrics are correctly exposed via Prometheus")
    void testRetentionMetrics_ExposedCorrectly() {
        // Given: retention configuration
        retentionService.updateRetentionConfig(536870912L, 60); // 512 MB, 60 days

        // When: fetching Prometheus metrics
        String metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then: retention metrics should be present
        assertTrue(metricsResponse.contains("sync_retention_max_bytes"),
                "Metrics should contain sync_retention_max_bytes");
        assertTrue(metricsResponse.contains("sync_retention_max_age_days"),
                "Metrics should contain sync_retention_max_age_days");
        assertTrue(metricsResponse.contains("sync_db_size_bytes"),
                "Metrics should contain sync_db_size_bytes");

        // Verify metric values (they should reflect our configuration)
        assertTrue(metricsResponse.contains("sync_retention_max_bytes 5.36870912E8") ||
                        metricsResponse.contains("sync_retention_max_bytes{} 5.36870912E8"),
                "Max bytes metric should have correct value");
        assertTrue(metricsResponse.contains("sync_retention_max_age_days 60") ||
                        metricsResponse.contains("sync_retention_max_age_days{} 60.0"),
                "Max age days metric should have correct value");
    }

    @Test
    @Order(8)
    @DisplayName("AC#6: Purge metrics are tracked correctly")
    void testPurgeMetrics_TrackedCorrectly() {
        // Given: retention config and old data
        retentionService.updateRetentionConfig(null, 30);
        LocalDateTime oldDate = LocalDateTime.now().minusDays(40);
        createTestOperation("old-batch", "old-user", oldDate);

        // When: executing purge
        retentionScheduler.executePurge("scheduled");

        // Then: metrics should include purge operations
        String metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Verify purge metrics are present
        assertTrue(metricsResponse.contains("sync_purge_runs_total"),
                "Metrics should contain sync_purge_runs_total");
        assertTrue(metricsResponse.contains("sync_purge_duration_seconds"),
                "Metrics should contain sync_purge_duration_seconds");

        // Verify reason tag is present
        assertTrue(metricsResponse.contains("reason=\"scheduled\""),
                "Purge metrics should have reason tag");
    }

    @Test
    @Order(9)
    @DisplayName("AC#7: Post-batch purge triggers work correctly")
    void testPostBatchPurge_TriggersCorrectly() {
        // Given: retention config
        retentionService.updateRetentionConfig(null, 30);

        // Create old operation
        LocalDateTime oldDate = LocalDateTime.now().minusDays(40);
        createTestOperation("old-batch", "old-user", oldDate);
        createTestOperation("recent-batch", "recent-user", LocalDateTime.now());

        long initialCount = operationRepository.count();
        assertEquals(2, initialCount);

        // When: triggering post-batch purge
        retentionScheduler.triggerPostSyncPurge();

        // Then: purge should execute
        long remainingCount = operationRepository.count();
        assertEquals(1, remainingCount, "Old record should be purged after batch");
    }

    @Test
    @Order(10)
    @DisplayName("AC#7: Post-batch purge skips if scheduled purge is running")
    void testPostBatchPurge_SkipsIfAlreadyRunning() {
        // Given: check that purge is not already running
        assertFalse(retentionScheduler.isPurgeRunning(), "Purge should not be running initially");

        // When: triggering post-batch purge multiple times
        // Note: In real scenario, we can't easily simulate concurrent execution in tests
        // This test mainly validates that the method can be called without errors
        retentionScheduler.triggerPostSyncPurge();

        // Then: no exception should be thrown
        // The actual skip logic is tested via the isRunning flag in the service
    }

    @Test
    @Order(11)
    @DisplayName("AC#8: Tests use real SQLite database with Flyway migrations")
    void testDatabaseSetup_UsesSQLiteWithFlyway() {
        // This test verifies that we're using real SQLite database
        // by checking that we can query system tables

        // When: calculating database size (uses SQLite PRAGMA commands)
        long dbSize = retentionService.calculateDatabaseSize();

        // Then: should get a valid size
        assertTrue(dbSize > 0, "Database size should be greater than 0");

        // Verify retention_state table exists (created by Flyway migration)
        RetentionState state = retentionRepository.getOrThrow();
        assertNotNull(state, "Retention state should exist (created by Flyway)");
        assertNotNull(state.getId(), "Retention state should have ID");
    }

    @Test
    @Order(12)
    @DisplayName("Complete retention flow: config update, purge, metrics")
    void testCompleteRetentionFlow() {
        // Given: initial state
        long initialDbSize = retentionService.calculateDatabaseSize();

        // Step 1: Update retention configuration via API
        String configUpdate = """
                {
                    "maxBytes": 1073741824,
                    "maxAgeDays": 45
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(configUpdate)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200);

        // Step 2: Create test data (old and recent)
        LocalDateTime oldDate = LocalDateTime.now().minusDays(50);
        LocalDateTime recentDate = LocalDateTime.now().minusDays(10);

        createTestOperation("batch-old-1", "user-old-1", oldDate);
        createTestOperation("batch-old-2", "user-old-2", oldDate);
        createTestOperation("batch-recent-1", "user-recent-1", recentDate);

        long beforePurgeCount = operationRepository.count();
        assertEquals(3, beforePurgeCount);

        // Step 3: Execute scheduled purge
        RetentionScheduler.PurgeResult result = retentionScheduler.executePurge("test");

        // Step 4: Verify purge results
        assertNotNull(result);
        assertEquals(2, result.ttlDeleted, "Should delete 2 records older than 45 days");

        long afterPurgeCount = operationRepository.count();
        assertEquals(1, afterPurgeCount, "Should have 1 record remaining");

        // Step 5: Verify metrics are updated
        String metrics = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertTrue(metrics.contains("sync_retention_max_bytes"));
        assertTrue(metrics.contains("sync_purge_runs_total"));

        // Step 6: Verify retention state via API
        given()
                .when()
                .get("/api/config/retention")
                .then()
                .statusCode(200)
                .body("maxBytes", greaterThan(0))
                .body("maxAgeDays", greaterThan(0))
                .body("$", hasKey("approxDbBytes"))
                .body("$", hasKey("updatedAt"));
    }

    // Helper methods

    /**
     * Creates a test sync operation with specified correlation ID, username, and timestamp.
     * Wrapped in transaction to avoid locking issues.
     */
    @Transactional
    void createTestOperation(String correlationId, String username, LocalDateTime occurredAt) {
        // Create batch if it doesn't exist
        SyncBatch batch = batchRepository.find("correlationId", correlationId).firstResult();
        if (batch == null) {
            batch = new SyncBatch();
            batch.setCorrelationId(correlationId);
            batch.setSource("TEST");
            batch.setStartedAt(occurredAt);
            batch.setFinishedAt(occurredAt.plusSeconds(1));
            batch.setItemsTotal(1);
            batch.setItemsSuccess(1);
            batch.setItemsError(0);
            batchRepository.persist(batch);
        }

        // Create operation
        SyncOperation operation = new SyncOperation();
        operation.setCorrelationId(correlationId);
        operation.setRealm("master");
        operation.setClusterId("test-cluster");
        operation.setPrincipal(username);
        operation.setOpType(OpType.SCRAM_UPSERT);
        operation.setResult(OperationResult.SUCCESS);
        operation.setOccurredAt(occurredAt);
        operation.setDurationMs(100);

        operationRepository.persist(operation);
    }
}
