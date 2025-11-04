package com.miimetiq.keycloak.sync.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ScramMechanism enum.
 */
class ScramMechanismTest {

    @Test
    void testEnumValues() {
        // Verify all expected values exist
        ScramMechanism[] values = ScramMechanism.values();
        assertEquals(2, values.length, "ScramMechanism should have exactly 2 values");

        // Verify each value exists
        assertNotNull(ScramMechanism.valueOf("SCRAM_SHA_256"));
        assertNotNull(ScramMechanism.valueOf("SCRAM_SHA_512"));
    }

    @Test
    void testEnumNames() {
        // Verify enum names match expected string values
        assertEquals("SCRAM_SHA_256", ScramMechanism.SCRAM_SHA_256.name());
        assertEquals("SCRAM_SHA_512", ScramMechanism.SCRAM_SHA_512.name());
    }

    @Test
    void testValueOf() {
        // Test that valueOf works correctly
        assertEquals(ScramMechanism.SCRAM_SHA_256, ScramMechanism.valueOf("SCRAM_SHA_256"));
        assertEquals(ScramMechanism.SCRAM_SHA_512, ScramMechanism.valueOf("SCRAM_SHA_512"));
    }

    @Test
    void testInvalidValue() {
        // Test that invalid value throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            ScramMechanism.valueOf("INVALID_MECHANISM");
        });
    }
}
