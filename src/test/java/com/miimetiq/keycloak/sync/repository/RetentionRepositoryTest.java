package com.miimetiq.keycloak.sync.repository;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RetentionRepository.
 * <p>
 * Tests repository methods for accessing the retention_state singleton table.
 */
@QuarkusTest
class RetentionRepositoryTest {

    @Inject
    RetentionRepository retentionRepository;

    @Test
    void testFindSingleton_ReturnsState() {
        // When: finding the singleton retention state
        Optional<RetentionState> result = retentionRepository.findSingleton();

        // Then: should find the singleton row
        assertTrue(result.isPresent(), "Singleton retention state should exist");
        assertEquals(RetentionState.SINGLETON_ID, result.get().getId());
    }

    @Test
    void testGetOrThrow_ReturnsState() {
        // When: getting the singleton retention state
        RetentionState state = retentionRepository.getOrThrow();

        // Then: should return the singleton row
        assertNotNull(state, "State should not be null");
        assertEquals(RetentionState.SINGLETON_ID, state.getId());
        assertNotNull(state.getUpdatedAt());
    }

    @Test
    void testStateHasExpectedFields() {
        // When: retrieving the state
        RetentionState state = retentionRepository.getOrThrow();

        // Then: should have all expected fields
        assertNotNull(state.getId());
        assertNotNull(state.getUpdatedAt());
        assertNotNull(state.getApproxDbBytes());
        // maxBytes and maxAgeDays can be null
    }

    @Test
    void testSingletonConstraint() {
        // When: retrieving singleton
        RetentionState state = retentionRepository.getOrThrow();

        // Then: should always be id=1
        assertEquals(1, state.getId());

        // Verify count - there should be exactly one row
        long count = retentionRepository.count();
        assertEquals(1, count, "retention_state table should have exactly one row");
    }

    @Test
    void testFindByIdUsesSingletonMethod() {
        // When: using findSingleton (preferred method)
        Optional<RetentionState> result = retentionRepository.findSingleton();

        // Then: should find the state
        assertTrue(result.isPresent());
        assertEquals(RetentionState.SINGLETON_ID, result.get().getId());
    }

    @Test
    void testStateHelperMethods() {
        // When: retrieving the state
        RetentionState state = retentionRepository.getOrThrow();

        // Then: helper methods should work correctly based on current state
        // These are just state queries, not mutations
        if (state.getMaxBytes() != null) {
            assertTrue(state.hasMaxBytesLimit());
        } else {
            assertFalse(state.hasMaxBytesLimit());
        }

        if (state.getMaxAgeDays() != null) {
            assertTrue(state.hasMaxAgeLimit());
        } else {
            assertFalse(state.hasMaxAgeLimit());
        }
    }
}
