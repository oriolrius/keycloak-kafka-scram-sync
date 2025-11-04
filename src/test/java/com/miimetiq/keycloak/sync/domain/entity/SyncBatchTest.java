package com.miimetiq.keycloak.sync.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the SyncBatch entity.
 * Tests entity creation, field mappings, and business logic.
 */
class SyncBatchTest {

    @Test
    void testDefaultConstructor() {
        SyncBatch batch = new SyncBatch();
        assertNotNull(batch, "Default constructor should create an instance");
        assertNull(batch.getId(), "ID should be null for new entity");
        assertEquals(0, batch.getItemsSuccess(), "Items success should default to 0");
        assertEquals(0, batch.getItemsError(), "Items error should default to 0");
    }

    @Test
    void testParameterizedConstructor() {
        // Given
        String correlationId = "batch-123";
        LocalDateTime startedAt = LocalDateTime.now();
        String source = "scheduled-reconciliation";
        Integer itemsTotal = 100;

        // When
        SyncBatch batch = new SyncBatch(correlationId, startedAt, source, itemsTotal);

        // Then
        assertNull(batch.getId(), "ID should be null before persistence");
        assertEquals(correlationId, batch.getCorrelationId());
        assertEquals(startedAt, batch.getStartedAt());
        assertEquals(source, batch.getSource());
        assertEquals(itemsTotal, batch.getItemsTotal());
        assertEquals(0, batch.getItemsSuccess(), "Items success should default to 0");
        assertEquals(0, batch.getItemsError(), "Items error should default to 0");
        assertNull(batch.getFinishedAt(), "Finished at should be null initially");
    }

    @Test
    void testAllFieldsWithSetters() {
        // Given
        SyncBatch batch = new SyncBatch();
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime finishedAt = startedAt.plusMinutes(5);

        // When
        batch.setId(1L);
        batch.setCorrelationId("batch-456");
        batch.setStartedAt(startedAt);
        batch.setFinishedAt(finishedAt);
        batch.setSource("manual-trigger");
        batch.setItemsTotal(50);
        batch.setItemsSuccess(45);
        batch.setItemsError(5);

        // Then
        assertEquals(1L, batch.getId());
        assertEquals("batch-456", batch.getCorrelationId());
        assertEquals(startedAt, batch.getStartedAt());
        assertEquals(finishedAt, batch.getFinishedAt());
        assertEquals("manual-trigger", batch.getSource());
        assertEquals(50, batch.getItemsTotal());
        assertEquals(45, batch.getItemsSuccess());
        assertEquals(5, batch.getItemsError());
    }

    @Test
    void testIncrementSuccess() {
        // Given
        SyncBatch batch = new SyncBatch("batch-1", LocalDateTime.now(), "test", 10);
        assertEquals(0, batch.getItemsSuccess());

        // When
        batch.incrementSuccess();
        batch.incrementSuccess();
        batch.incrementSuccess();

        // Then
        assertEquals(3, batch.getItemsSuccess());
    }

    @Test
    void testIncrementError() {
        // Given
        SyncBatch batch = new SyncBatch("batch-1", LocalDateTime.now(), "test", 10);
        assertEquals(0, batch.getItemsError());

        // When
        batch.incrementError();
        batch.incrementError();

        // Then
        assertEquals(2, batch.getItemsError());
    }

    @Test
    void testIsComplete() {
        // Given
        SyncBatch batch = new SyncBatch("batch-1", LocalDateTime.now(), "test", 10);

        // When/Then - initially not complete
        assertFalse(batch.isComplete(), "Batch should not be complete without finished_at");

        // When - mark as complete
        batch.setFinishedAt(LocalDateTime.now());

        // Then
        assertTrue(batch.isComplete(), "Batch should be complete when finished_at is set");
    }

    @Test
    void testBatchProgress() {
        // Test a realistic batch processing scenario
        SyncBatch batch = new SyncBatch(
                "batch-reconcile-1",
                LocalDateTime.now(),
                "scheduled",
                100
        );

        // Process some items
        for (int i = 0; i < 95; i++) {
            batch.incrementSuccess();
        }
        for (int i = 0; i < 5; i++) {
            batch.incrementError();
        }

        // Mark as complete
        batch.setFinishedAt(LocalDateTime.now());

        // Verify
        assertEquals(100, batch.getItemsTotal());
        assertEquals(95, batch.getItemsSuccess());
        assertEquals(5, batch.getItemsError());
        assertTrue(batch.isComplete());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        SyncBatch batch1 = new SyncBatch();
        batch1.setId(1L);

        SyncBatch batch2 = new SyncBatch();
        batch2.setId(1L);

        SyncBatch batch3 = new SyncBatch();
        batch3.setId(2L);

        // Then
        assertEquals(batch1, batch2, "Batches with same ID should be equal");
        assertEquals(batch1.hashCode(), batch2.hashCode(), "Batches with same ID should have same hashCode");
        assertNotEquals(batch1, batch3, "Batches with different IDs should not be equal");
    }

    @Test
    void testToString() {
        // Given
        SyncBatch batch = new SyncBatch();
        batch.setId(1L);
        batch.setCorrelationId("batch-789");
        batch.setSource("manual");

        // When
        String toString = batch.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("SyncBatch"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("batch-789"));
        assertTrue(toString.contains("manual"));
    }

    @Test
    void testNullableFinishedAt() {
        // finishedAt should be nullable for in-progress batches
        SyncBatch batch = new SyncBatch("batch-1", LocalDateTime.now(), "test", 10);
        assertNull(batch.getFinishedAt(), "Finished at should be nullable");
    }

    @Test
    void testUniqueCorrelationId() {
        // The correlation ID should be unique (enforced by database constraint)
        // This is more of a documentation test
        SyncBatch batch1 = new SyncBatch("unique-corr-id", LocalDateTime.now(), "test", 10);
        SyncBatch batch2 = new SyncBatch("unique-corr-id", LocalDateTime.now(), "test", 10);

        assertEquals(batch1.getCorrelationId(), batch2.getCorrelationId());
        // Note: Uniqueness is enforced at the database level, not in the entity
    }
}
