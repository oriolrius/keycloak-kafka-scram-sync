package com.miimetiq.keycloak.sync.service;

import com.miimetiq.keycloak.sync.domain.entity.SyncBatch;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SyncPersistenceService.
 * <p>
 * Tests CRUD operations, batch management, and operation recording.
 */
@QuarkusTest
class SyncPersistenceServiceTest {

    @Inject
    SyncPersistenceService persistenceService;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data before each test
        // Note: In a real scenario, you might want to use @TestTransaction or similar
    }

    @Test
    void testCreateBatch() {
        // When: creating a new batch
        String correlationId = persistenceService.createBatch("MANUAL", 10);

        // Then: batch should be created and persisted
        assertNotNull(correlationId, "Correlation ID should not be null");
        assertFalse(correlationId.isBlank(), "Correlation ID should not be blank");

        // Verify batch can be retrieved
        Optional<SyncBatch> batch = persistenceService.getBatch(correlationId);
        assertTrue(batch.isPresent(), "Batch should be retrievable");
        assertEquals("MANUAL", batch.get().getSource());
        assertEquals(10, batch.get().getItemsTotal());
        assertNull(batch.get().getFinishedAt(), "Batch should not be finished yet");
    }

    @Test
    void testRecordOperation() {
        // Given: a batch exists
        String correlationId = persistenceService.createBatch("SCHEDULED", 5);

        // When: recording an operation
        SyncOperation operation = createOperation(correlationId, "user1", OperationResult.SUCCESS);
        persistenceService.recordOperation(operation);

        // Then: operation should be persisted
        List<SyncOperation> operations = persistenceService.getOperations(correlationId);
        assertEquals(1, operations.size(), "Should have 1 operation");
        assertEquals("user1", operations.get(0).getPrincipal());
    }

    @Test
    void testRecordOperationAndUpdateBatch() {
        // Given: a batch exists
        String correlationId = persistenceService.createBatch("WEBHOOK", 3);

        // When: recording operations with automatic batch updates
        persistenceService.recordOperationAndUpdateBatch(
                createOperation(correlationId, "user1", OperationResult.SUCCESS)
        );
        persistenceService.recordOperationAndUpdateBatch(
                createOperation(correlationId, "user2", OperationResult.SUCCESS)
        );
        persistenceService.recordOperationAndUpdateBatch(
                createOperation(correlationId, "user3", OperationResult.ERROR)
        );

        // Then: batch counters should be updated
        Optional<SyncBatch> batch = persistenceService.getBatch(correlationId);
        assertTrue(batch.isPresent());
        assertEquals(2, batch.get().getItemsSuccess(), "Should have 2 successful operations");
        assertEquals(1, batch.get().getItemsError(), "Should have 1 failed operation");
    }

    @Test
    void testRecordMultipleOperations() {
        // Given: a batch and multiple operations
        String correlationId = persistenceService.createBatch("MANUAL", 3);
        List<SyncOperation> operations = List.of(
                createOperation(correlationId, "user1", OperationResult.SUCCESS),
                createOperation(correlationId, "user2", OperationResult.SUCCESS),
                createOperation(correlationId, "user3", OperationResult.ERROR)
        );

        // When: recording all operations at once
        persistenceService.recordOperations(operations);

        // Then: all operations should be persisted
        List<SyncOperation> retrieved = persistenceService.getOperations(correlationId);
        assertEquals(3, retrieved.size(), "Should have 3 operations");
    }

    @Test
    void testCompleteBatch_WithCounts() {
        // Given: a batch with some operations
        String correlationId = persistenceService.createBatch("SCHEDULED", 10);

        // When: completing the batch with explicit counts
        boolean completed = persistenceService.completeBatch(correlationId, 8, 2);

        // Then: batch should be marked as complete
        assertTrue(completed, "Batch should be completed successfully");

        Optional<SyncBatch> batch = persistenceService.getBatch(correlationId);
        assertTrue(batch.isPresent());
        assertNotNull(batch.get().getFinishedAt(), "Batch should have finish timestamp");
        assertEquals(8, batch.get().getItemsSuccess());
        assertEquals(2, batch.get().getItemsError());
    }

    @Test
    void testCompleteBatch_WithCurrentCounts() {
        // Given: a batch with operations that updated counters
        String correlationId = persistenceService.createBatch("MANUAL", 2);
        persistenceService.recordOperationAndUpdateBatch(
                createOperation(correlationId, "user1", OperationResult.SUCCESS)
        );
        persistenceService.recordOperationAndUpdateBatch(
                createOperation(correlationId, "user2", OperationResult.SUCCESS)
        );

        // When: completing the batch without explicit counts
        boolean completed = persistenceService.completeBatch(correlationId);

        // Then: batch should be completed with current counter values
        assertTrue(completed);

        Optional<SyncBatch> batch = persistenceService.getBatch(correlationId);
        assertTrue(batch.isPresent());
        assertNotNull(batch.get().getFinishedAt());
        assertEquals(2, batch.get().getItemsSuccess());
        assertEquals(0, batch.get().getItemsError());
    }

    @Test
    void testCompleteBatch_NotFound() {
        // When: attempting to complete a non-existent batch
        boolean completed = persistenceService.completeBatch("non-existent-id");

        // Then: operation should return false
        assertFalse(completed, "Should return false for non-existent batch");
    }

    @Test
    void testGetBatch() {
        // Given: a batch exists
        String correlationId = persistenceService.createBatch("TEST", 5);

        // When: retrieving the batch
        Optional<SyncBatch> batch = persistenceService.getBatch(correlationId);

        // Then: batch should be found
        assertTrue(batch.isPresent());
        assertEquals(correlationId, batch.get().getCorrelationId());
        assertEquals("TEST", batch.get().getSource());
        assertEquals(5, batch.get().getItemsTotal());
    }

    @Test
    void testGetBatch_NotFound() {
        // When: retrieving a non-existent batch
        Optional<SyncBatch> batch = persistenceService.getBatch("non-existent-id");

        // Then: should return empty
        assertFalse(batch.isPresent());
    }

    @Test
    void testGetOperations() {
        // Given: a batch with multiple operations
        String correlationId = persistenceService.createBatch("SCHEDULED", 3);
        persistenceService.recordOperations(List.of(
                createOperation(correlationId, "user1", OperationResult.SUCCESS),
                createOperation(correlationId, "user2", OperationResult.SUCCESS),
                createOperation(correlationId, "user3", OperationResult.ERROR)
        ));

        // When: retrieving operations
        List<SyncOperation> operations = persistenceService.getOperations(correlationId);

        // Then: all operations should be returned
        assertEquals(3, operations.size());
        assertTrue(operations.stream().anyMatch(op -> op.getPrincipal().equals("user1")));
        assertTrue(operations.stream().anyMatch(op -> op.getPrincipal().equals("user2")));
        assertTrue(operations.stream().anyMatch(op -> op.getPrincipal().equals("user3")));
    }

    @Test
    void testGetOperationsByPrincipal() {
        // Given: multiple batches with operations for the same principal
        // Use unique principal name to avoid collision with other tests/scheduled reconciliation
        String uniquePrincipal = "testuser-" + System.currentTimeMillis();
        String correlationId1 = persistenceService.createBatch("MANUAL", 1);
        String correlationId2 = persistenceService.createBatch("SCHEDULED", 1);

        persistenceService.recordOperation(createOperation(correlationId1, uniquePrincipal, OperationResult.SUCCESS));
        persistenceService.recordOperation(createOperation(correlationId2, uniquePrincipal, OperationResult.ERROR));
        persistenceService.recordOperation(createOperation(correlationId2, "otheruser", OperationResult.SUCCESS));

        // When: retrieving operations by principal
        List<SyncOperation> operations = persistenceService.getOperationsByPrincipal(uniquePrincipal);

        // Then: should return all operations for that principal
        assertEquals(2, operations.size());
        assertTrue(operations.stream().allMatch(op -> op.getPrincipal().equals(uniquePrincipal)));
    }

    @Test
    void testGetRecentBatches() {
        // Given: multiple batches
        String id1 = persistenceService.createBatch("SCHEDULED", 1);
        String id2 = persistenceService.createBatch("MANUAL", 2);
        String id3 = persistenceService.createBatch("WEBHOOK", 3);

        // When: retrieving recent batches
        List<SyncBatch> recent = persistenceService.getRecentBatches(2);

        // Then: should return most recent batches in descending order
        assertEquals(2, recent.size(), "Should return 2 most recent batches");
        // Most recent should be first (id3, then id2)
        assertEquals(id3, recent.get(0).getCorrelationId());
        assertEquals(id2, recent.get(1).getCorrelationId());
    }

    @Test
    void testGetBatchesByTimeRange() {
        // Given: batches created at different times
        String id1 = persistenceService.createBatch("TEST1", 1);

        // Small delay to ensure different timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String id2 = persistenceService.createBatch("TEST2", 2);

        // When: retrieving batches by time range
        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);
        List<SyncBatch> batches = persistenceService.getBatchesByTimeRange(start, end);

        // Then: should return all batches in the range
        assertTrue(batches.size() >= 2, "Should have at least 2 batches");
        assertTrue(batches.stream().anyMatch(b -> b.getCorrelationId().equals(id1)));
        assertTrue(batches.stream().anyMatch(b -> b.getCorrelationId().equals(id2)));
    }

    // Helper method to create test operations
    private SyncOperation createOperation(String correlationId, String principal, OperationResult result) {
        SyncOperation operation = new SyncOperation(
                correlationId,
                LocalDateTime.now(),
                "test-realm",
                "test-cluster",
                principal,
                OpType.SCRAM_UPSERT,
                result,
                50
        );
        operation.setMechanism(ScramMechanism.SCRAM_SHA_256);
        return operation;
    }
}
