package com.miimetiq.keycloak.sync.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the OperationResult enum.
 */
class OperationResultTest {

    @Test
    void testEnumValues() {
        // Verify all expected values exist
        OperationResult[] values = OperationResult.values();
        assertEquals(3, values.length, "OperationResult should have exactly 3 values");

        // Verify each value exists
        assertNotNull(OperationResult.valueOf("SUCCESS"));
        assertNotNull(OperationResult.valueOf("ERROR"));
        assertNotNull(OperationResult.valueOf("SKIPPED"));
    }

    @Test
    void testEnumNames() {
        // Verify enum names match expected string values
        assertEquals("SUCCESS", OperationResult.SUCCESS.name());
        assertEquals("ERROR", OperationResult.ERROR.name());
        assertEquals("SKIPPED", OperationResult.SKIPPED.name());
    }

    @Test
    void testValueOf() {
        // Test that valueOf works correctly
        assertEquals(OperationResult.SUCCESS, OperationResult.valueOf("SUCCESS"));
        assertEquals(OperationResult.ERROR, OperationResult.valueOf("ERROR"));
        assertEquals(OperationResult.SKIPPED, OperationResult.valueOf("SKIPPED"));
    }

    @Test
    void testInvalidValue() {
        // Test that invalid value throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            OperationResult.valueOf("INVALID_RESULT");
        });
    }
}
