package com.miimetiq.keycloak.sync.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the OpType enum.
 */
class OpTypeTest {

    @Test
    void testEnumValues() {
        // Verify all expected values exist
        OpType[] values = OpType.values();
        assertEquals(4, values.length, "OpType should have exactly 4 values");

        // Verify each value exists
        assertNotNull(OpType.valueOf("SCRAM_UPSERT"));
        assertNotNull(OpType.valueOf("SCRAM_DELETE"));
        assertNotNull(OpType.valueOf("ACL_CREATE"));
        assertNotNull(OpType.valueOf("ACL_DELETE"));
    }

    @Test
    void testEnumNames() {
        // Verify enum names match expected string values
        assertEquals("SCRAM_UPSERT", OpType.SCRAM_UPSERT.name());
        assertEquals("SCRAM_DELETE", OpType.SCRAM_DELETE.name());
        assertEquals("ACL_CREATE", OpType.ACL_CREATE.name());
        assertEquals("ACL_DELETE", OpType.ACL_DELETE.name());
    }

    @Test
    void testValueOf() {
        // Test that valueOf works correctly
        assertEquals(OpType.SCRAM_UPSERT, OpType.valueOf("SCRAM_UPSERT"));
        assertEquals(OpType.SCRAM_DELETE, OpType.valueOf("SCRAM_DELETE"));
        assertEquals(OpType.ACL_CREATE, OpType.valueOf("ACL_CREATE"));
        assertEquals(OpType.ACL_DELETE, OpType.valueOf("ACL_DELETE"));
    }

    @Test
    void testInvalidValue() {
        // Test that invalid value throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            OpType.valueOf("INVALID_TYPE");
        });
    }
}
