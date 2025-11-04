package com.miimetiq.keycloak.sync.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RetentionState entity.
 * Tests entity creation, field mappings, singleton constraints, and business logic.
 */
class RetentionStateTest {

    @Test
    void testDefaultConstructor() {
        RetentionState state = new RetentionState();
        assertNotNull(state, "Default constructor should create an instance");
        assertEquals(RetentionState.SINGLETON_ID, state.getId(), "ID should be set to SINGLETON_ID (1)");
    }

    @Test
    void testParameterizedConstructor() {
        // Given
        Long approxDbBytes = 1024000L;
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        RetentionState state = new RetentionState(approxDbBytes, updatedAt);

        // Then
        assertEquals(RetentionState.SINGLETON_ID, state.getId(), "ID should be SINGLETON_ID");
        assertEquals(approxDbBytes, state.getApproxDbBytes());
        assertEquals(updatedAt, state.getUpdatedAt());
        assertNull(state.getMaxBytes(), "MaxBytes should be null by default");
        assertNull(state.getMaxAgeDays(), "MaxAgeDays should be null by default");
    }

    @Test
    void testSingletonIdConstant() {
        assertEquals(1, RetentionState.SINGLETON_ID, "SINGLETON_ID should be 1");
    }

    @Test
    void testSetIdWithValidValue() {
        // Given
        RetentionState state = new RetentionState();

        // When/Then - setting to SINGLETON_ID should work
        assertDoesNotThrow(() -> state.setId(RetentionState.SINGLETON_ID));
        assertEquals(RetentionState.SINGLETON_ID, state.getId());
    }

    @Test
    void testSetIdWithInvalidValue() {
        // Given
        RetentionState state = new RetentionState();

        // When/Then - setting to any other value should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            state.setId(2);
        });

        assertTrue(exception.getMessage().contains("must always be"));
    }

    @Test
    void testAllFieldsWithSetters() {
        // Given
        RetentionState state = new RetentionState();
        LocalDateTime now = LocalDateTime.now();

        // When
        state.setMaxBytes(5000000L);
        state.setMaxAgeDays(30);
        state.setApproxDbBytes(2500000L);
        state.setUpdatedAt(now);

        // Then
        assertEquals(RetentionState.SINGLETON_ID, state.getId());
        assertEquals(5000000L, state.getMaxBytes());
        assertEquals(30, state.getMaxAgeDays());
        assertEquals(2500000L, state.getApproxDbBytes());
        assertEquals(now, state.getUpdatedAt());
    }

    @Test
    void testHasMaxBytesLimit() {
        // Given
        RetentionState state = new RetentionState();

        // When/Then - initially no limit
        assertFalse(state.hasMaxBytesLimit(), "Should return false when maxBytes is null");

        // When - set a limit
        state.setMaxBytes(10000000L);

        // Then
        assertTrue(state.hasMaxBytesLimit(), "Should return true when maxBytes is set");
    }

    @Test
    void testHasMaxAgeLimit() {
        // Given
        RetentionState state = new RetentionState();

        // When/Then - initially no limit
        assertFalse(state.hasMaxAgeLimit(), "Should return false when maxAgeDays is null");

        // When - set a limit
        state.setMaxAgeDays(60);

        // Then
        assertTrue(state.hasMaxAgeLimit(), "Should return true when maxAgeDays is set");
    }

    @Test
    void testUpdateSize() {
        // Given
        RetentionState state = new RetentionState(1000000L, LocalDateTime.now().minusDays(1));
        Long newSize = 1500000L;
        LocalDateTime newTimestamp = LocalDateTime.now();

        // When
        state.updateSize(newSize, newTimestamp);

        // Then
        assertEquals(newSize, state.getApproxDbBytes());
        assertEquals(newTimestamp, state.getUpdatedAt());
    }

    @Test
    void testNullableFields() {
        // maxBytes and maxAgeDays should be nullable
        RetentionState state = new RetentionState();

        assertNull(state.getMaxBytes(), "MaxBytes should be nullable");
        assertNull(state.getMaxAgeDays(), "MaxAgeDays should be nullable");
    }

    @Test
    void testRetentionConfiguration() {
        // Test various retention configurations
        RetentionState state = new RetentionState();

        // No limits configured
        assertFalse(state.hasMaxBytesLimit());
        assertFalse(state.hasMaxAgeLimit());

        // Size limit only
        state.setMaxBytes(10000000L);
        assertTrue(state.hasMaxBytesLimit());
        assertFalse(state.hasMaxAgeLimit());

        // Both limits configured
        state.setMaxAgeDays(30);
        assertTrue(state.hasMaxBytesLimit());
        assertTrue(state.hasMaxAgeLimit());

        // Age limit only
        state.setMaxBytes(null);
        assertFalse(state.hasMaxBytesLimit());
        assertTrue(state.hasMaxAgeLimit());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        RetentionState state1 = new RetentionState();
        state1.setId(RetentionState.SINGLETON_ID);

        RetentionState state2 = new RetentionState();
        state2.setId(RetentionState.SINGLETON_ID);

        // Then - both instances should be equal (same singleton ID)
        assertEquals(state1, state2, "RetentionStates with same ID should be equal");
        assertEquals(state1.hashCode(), state2.hashCode(), "RetentionStates with same ID should have same hashCode");
    }

    @Test
    void testToString() {
        // Given
        RetentionState state = new RetentionState();
        state.setMaxBytes(5000000L);
        state.setMaxAgeDays(30);
        state.setApproxDbBytes(2500000L);

        // When
        String toString = state.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("RetentionState"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("5000000"));
        assertTrue(toString.contains("30"));
        assertTrue(toString.contains("2500000"));
    }

    @Test
    void testSizeTracking() {
        // Test realistic size tracking scenario
        LocalDateTime now = LocalDateTime.now();
        RetentionState state = new RetentionState(0L, now);

        // Simulate database growth
        state.updateSize(500000L, now.plusHours(1));
        assertEquals(500000L, state.getApproxDbBytes());

        state.updateSize(1200000L, now.plusHours(2));
        assertEquals(1200000L, state.getApproxDbBytes());

        state.updateSize(2500000L, now.plusHours(3));
        assertEquals(2500000L, state.getApproxDbBytes());

        // Check if approaching limit
        state.setMaxBytes(3000000L);
        assertTrue(state.hasMaxBytesLimit());
        assertTrue(state.getApproxDbBytes() < state.getMaxBytes());
    }
}
