package com.miimetiq.keycloak.sync.reconcile;

import com.miimetiq.keycloak.sync.domain.KeycloakUserInfo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SyncDiffEngine.
 * <p>
 * Tests various reconciliation scenarios including:
 * - New users (upserts)
 * - Deleted users (orphaned principals)
 * - No changes (empty diff)
 * - Exclusion filtering
 * - Performance with large datasets
 */
@QuarkusTest
class SyncDiffEngineTest {

    @Inject
    SyncDiffEngine diffEngine;

    private List<KeycloakUserInfo> keycloakUsers;
    private Set<String> kafkaPrincipals;

    @BeforeEach
    void setUp() {
        keycloakUsers = new ArrayList<>();
        kafkaPrincipals = new HashSet<>();
    }

    @Test
    void testComputeDiff_NewUsersOnly() {
        // Given: 3 users in Keycloak, 0 in Kafka
        keycloakUsers = List.of(
                createUser("user1"),
                createUser("user2"),
                createUser("user3")
        );
        kafkaPrincipals = Set.of();

        // When: computing diff
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // Then: all users should be upserted, no deletes
        assertEquals(3, plan.getUpsertCount(), "Should upsert all 3 users");
        assertEquals(0, plan.getDeleteCount(), "Should have no deletes");
        assertFalse(plan.isEmpty(), "Plan should not be empty");
        assertEquals(3, plan.getTotalOperations());
    }

    @Test
    void testComputeDiff_DeletedUsersOnly() {
        // Given: 0 users in Keycloak, 3 principals in Kafka
        keycloakUsers = List.of();
        kafkaPrincipals = Set.of("user1", "user2", "user3");

        // When: computing diff
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // Then: all principals should be deleted, no upserts
        assertEquals(0, plan.getUpsertCount(), "Should have no upserts");
        assertEquals(3, plan.getDeleteCount(), "Should delete all 3 principals");
        assertFalse(plan.isEmpty(), "Plan should not be empty");
        assertEquals(3, plan.getTotalOperations());

        // Verify delete list is sorted
        List<String> deletes = plan.getDeletes();
        assertEquals("user1", deletes.get(0));
        assertEquals("user2", deletes.get(1));
        assertEquals("user3", deletes.get(2));
    }

    @Test
    void testComputeDiff_NoChanges() {
        // Given: same users in both systems
        keycloakUsers = List.of(
                createUser("user1"),
                createUser("user2")
        );
        kafkaPrincipals = Set.of("user1", "user2");

        // When: computing diff with alwaysUpsert=false (need to check actual config)
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // Then: behavior depends on alwaysUpsert config
        // If alwaysUpsert=true (default): upserts all users
        // If alwaysUpsert=false: no operations
        if (diffEngine.isAlwaysUpsert()) {
            assertEquals(2, plan.getUpsertCount(), "Should upsert all users (alwaysUpsert=true)");
            assertEquals(0, plan.getDeleteCount());
        } else {
            assertTrue(plan.isEmpty(), "Should have no operations (alwaysUpsert=false)");
        }
    }

    @Test
    void testComputeDiff_MixedOperations() {
        // Given: some new users, some deleted, some unchanged
        keycloakUsers = List.of(
                createUser("user1"),  // Exists in both
                createUser("user2"),  // New in Keycloak
                createUser("user3")   // Exists in both
        );
        kafkaPrincipals = Set.of("user1", "user3", "user4"); // user4 orphaned

        // When: computing diff
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // Then: depending on alwaysUpsert mode
        if (diffEngine.isAlwaysUpsert()) {
            // All Keycloak users are upserted
            assertEquals(3, plan.getUpsertCount());
        } else {
            // Only new user is upserted
            assertEquals(1, plan.getUpsertCount());
        }

        // Orphaned principal should always be deleted
        assertEquals(1, plan.getDeleteCount());
        assertTrue(plan.getDeletes().contains("user4"));
    }

    @Test
    void testComputeDiff_ExcludedPrincipals() {
        // Given: Kafka principals include system accounts
        keycloakUsers = List.of(createUser("user1"));
        kafkaPrincipals = Set.of(
                "user1",
                "admin",      // Should be excluded
                "kafka",      // Should be excluded
                "system",     // Should be excluded
                "admin-user"  // Should be excluded (prefix match)
        );

        // When: computing diff
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // Then: excluded principals should not appear in deletes
        assertEquals(0, plan.getDeleteCount(), "Excluded principals should not be deleted");

        // Verify excluded principals are configured
        Set<String> excluded = diffEngine.getExcludedPrincipals();
        assertTrue(excluded.contains("admin"));
        assertTrue(excluded.contains("kafka"));
        assertTrue(excluded.contains("system"));
    }

    @Test
    void testComputeDiff_DryRunMode() {
        // Given: some operations needed
        keycloakUsers = List.of(createUser("user1"));
        kafkaPrincipals = Set.of("user2");

        // When: computing diff in dry-run mode
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals, true);

        // Then: plan should be marked as dry-run
        assertTrue(plan.isDryRun(), "Should be marked as dry-run");
        assertTrue(plan.getSummary().contains("[DRY-RUN]"), "Summary should indicate dry-run");
    }

    @Test
    void testComputeDiff_EmptyInputs() {
        // Given: no users in either system
        keycloakUsers = List.of();
        kafkaPrincipals = Set.of();

        // When: computing diff
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // Then: should be empty
        assertTrue(plan.isEmpty(), "Plan should be empty");
        assertEquals(0, plan.getTotalOperations());
    }

    @Test
    void testComputeDiff_LargeDataset() {
        // Given: large dataset (10,000 users)
        int userCount = 10000;
        List<KeycloakUserInfo> largeUserList = new ArrayList<>();
        Set<String> largePrincipalSet = new HashSet<>();

        // Create 8000 users in both systems
        IntStream.range(0, 8000).forEach(i -> {
            String username = "user" + i;
            largeUserList.add(createUser(username));
            largePrincipalSet.add(username);
        });

        // Create 2000 new users in Keycloak
        IntStream.range(8000, 10000).forEach(i -> {
            largeUserList.add(createUser("user" + i));
        });

        // Create 1000 orphaned principals in Kafka
        IntStream.range(10000, 11000).forEach(i -> {
            largePrincipalSet.add("orphan" + i);
        });

        // When: computing diff
        long startTime = System.currentTimeMillis();
        SyncPlan plan = diffEngine.computeDiff(largeUserList, largePrincipalSet);
        long duration = System.currentTimeMillis() - startTime;

        // Then: should complete quickly (< 1 second)
        assertTrue(duration < 1000, "Should complete in less than 1 second, took: " + duration + "ms");

        // Verify counts
        if (diffEngine.isAlwaysUpsert()) {
            assertEquals(10000, plan.getUpsertCount());
        } else {
            assertEquals(2000, plan.getUpsertCount(), "Should have 2000 new users");
        }
        assertEquals(1000, plan.getDeleteCount(), "Should have 1000 orphaned principals");
    }

    @Test
    void testSyncPlan_ImmutableLists() {
        // Given: a sync plan
        keycloakUsers = List.of(createUser("user1"));
        kafkaPrincipals = Set.of("user2");
        SyncPlan plan = diffEngine.computeDiff(keycloakUsers, kafkaPrincipals);

        // When/Then: attempting to modify lists should throw exception
        assertThrows(UnsupportedOperationException.class, () -> {
            plan.getUpserts().add(createUser("hacker"));
        }, "Upserts list should be immutable");

        assertThrows(UnsupportedOperationException.class, () -> {
            plan.getDeletes().add("hacker");
        }, "Deletes list should be immutable");
    }

    @Test
    void testSyncPlan_Builder() {
        // Given: a plan builder
        SyncPlan.Builder builder = new SyncPlan.Builder();

        // When: building a plan with builder
        SyncPlan plan = builder
                .addUpsert(createUser("user1"))
                .addUpsert(createUser("user2"))
                .addDelete("orphan1")
                .dryRun(true)
                .build();

        // Then: plan should have correct values
        assertEquals(2, plan.getUpsertCount());
        assertEquals(1, plan.getDeleteCount());
        assertTrue(plan.isDryRun());
    }

    @Test
    void testSyncPlan_Summary() {
        // Test various summary formats
        SyncPlan emptyPlan = new SyncPlan(List.of(), List.of(), false);
        assertTrue(emptyPlan.getSummary().contains("No synchronization needed"));

        SyncPlan upsertsOnly = new SyncPlan(List.of(createUser("u1")), List.of(), false);
        assertTrue(upsertsOnly.getSummary().contains("1 upsert(s)"));

        SyncPlan deletesOnly = new SyncPlan(List.of(), List.of("d1"), false);
        assertTrue(deletesOnly.getSummary().contains("1 delete(s)"));

        SyncPlan mixed = new SyncPlan(List.of(createUser("u1")), List.of("d1"), true);
        assertTrue(mixed.getSummary().contains("1 upsert(s)"));
        assertTrue(mixed.getSummary().contains("1 delete(s)"));
        assertTrue(mixed.getSummary().contains("[DRY-RUN]"));
    }

    // Helper method to create test users
    private KeycloakUserInfo createUser(String username) {
        return new KeycloakUserInfo(
                UUID.randomUUID().toString(),
                username,
                username + "@example.com",
                true,
                System.currentTimeMillis()
        );
    }
}
