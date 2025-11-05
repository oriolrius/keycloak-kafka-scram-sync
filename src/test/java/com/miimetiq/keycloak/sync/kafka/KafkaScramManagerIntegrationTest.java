package com.miimetiq.keycloak.sync.kafka;

import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import com.miimetiq.keycloak.sync.integration.IntegrationTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsResult;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KafkaScramManager using real Kafka via Testcontainers.
 * <p>
 * Tests SCRAM credential operations against a real Kafka broker to validate
 * end-to-end functionality including network communication and Kafka API behavior.
 */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResource.class)
@DisplayName("KafkaScramManager Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaScramManagerIntegrationTest {

    @Inject
    KafkaScramManager scramManager;

    private static final String TEST_USER_1 = "integration-test-user-1";
    private static final String TEST_USER_2 = "integration-test-user-2";
    private static final String TEST_PASSWORD = "test-password-123";
    private static final int DEFAULT_ITERATIONS = 4096;

    @AfterEach
    void cleanup() {
        // Clean up test users after each test to ensure isolation
        try {
            // Describe to see what credentials exist
            Map<String, List<ScramCredentialInfo>> existing = scramManager.describeUserScramCredentials();

            // Delete test users if they exist
            if (existing.containsKey(TEST_USER_1)) {
                List<ScramMechanism> mechanisms = existing.get(TEST_USER_1).stream()
                        .map(info -> convertFromKafkaScramMechanism(info.mechanism()))
                        .toList();
                AlterUserScramCredentialsResult result = scramManager.deleteUserScramCredentials(
                        Collections.singletonMap(TEST_USER_1, mechanisms));
                scramManager.waitForAlterations(result);
            }

            if (existing.containsKey(TEST_USER_2)) {
                List<ScramMechanism> mechanisms = existing.get(TEST_USER_2).stream()
                        .map(info -> convertFromKafkaScramMechanism(info.mechanism()))
                        .toList();
                AlterUserScramCredentialsResult result = scramManager.deleteUserScramCredentials(
                        Collections.singletonMap(TEST_USER_2, mechanisms));
                scramManager.waitForAlterations(result);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Order(1)
    @DisplayName("AC#1: Can describe SCRAM credentials from real Kafka")
    void testDescribeUserScramCredentials() {
        // When: describing all SCRAM credentials
        Map<String, List<ScramCredentialInfo>> credentials = scramManager.describeUserScramCredentials();

        // Then: should return a map (possibly empty for new Kafka)
        assertNotNull(credentials, "Credentials map should not be null");
        // Don't assert size since we don't know initial state of Kafka
    }

    @Test
    @Order(2)
    @DisplayName("AC#2-4: Can upsert SCRAM-SHA-256 credential for user")
    void testUpsertUserScramCredential_SHA256() {
        // When: upserting SCRAM-SHA-256 credential
        AlterUserScramCredentialsResult result = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_256, TEST_PASSWORD, DEFAULT_ITERATIONS);

        // Then: operation should complete successfully
        assertNotNull(result, "Result should not be null");

        Map<String, Throwable> errors = scramManager.waitForAlterations(result);
        assertTrue(errors.isEmpty(), "Should have no errors: " + errors);

        // Verify credential was created
        Map<String, List<ScramCredentialInfo>> credentials =
                scramManager.describeUserScramCredentials(List.of(TEST_USER_1));

        assertTrue(credentials.containsKey(TEST_USER_1), "User should exist in credentials");
        List<ScramCredentialInfo> userCreds = credentials.get(TEST_USER_1);
        assertEquals(1, userCreds.size(), "User should have 1 credential");

        ScramCredentialInfo credInfo = userCreds.get(0);
        assertEquals(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256,
                credInfo.mechanism(), "Mechanism should be SCRAM-SHA-256");
        assertEquals(DEFAULT_ITERATIONS, credInfo.iterations(), "Iterations should match");
    }

    @Test
    @Order(3)
    @DisplayName("AC#2-4: Can upsert SCRAM-SHA-512 credential for user")
    void testUpsertUserScramCredential_SHA512() {
        // When: upserting SCRAM-SHA-512 credential
        AlterUserScramCredentialsResult result = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_512, TEST_PASSWORD, 8192);

        // Then: operation should complete successfully
        assertNotNull(result);

        Map<String, Throwable> errors = scramManager.waitForAlterations(result);
        assertTrue(errors.isEmpty(), "Should have no errors: " + errors);

        // Verify credential was created
        Map<String, List<ScramCredentialInfo>> credentials =
                scramManager.describeUserScramCredentials(List.of(TEST_USER_1));

        assertTrue(credentials.containsKey(TEST_USER_1));
        List<ScramCredentialInfo> userCreds = credentials.get(TEST_USER_1);
        assertEquals(1, userCreds.size(), "User should have 1 credential");

        ScramCredentialInfo credInfo = userCreds.get(0);
        assertEquals(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_512,
                credInfo.mechanism(), "Mechanism should be SCRAM-SHA-512");
        assertEquals(8192, credInfo.iterations(), "Iterations should be 8192");
    }

    @Test
    @Order(4)
    @DisplayName("AC#4: Can update existing credential (upsert)")
    void testUpsertUserScramCredential_Update() {
        // Given: user with existing credential
        AlterUserScramCredentialsResult createResult = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_256, TEST_PASSWORD, 4096);
        scramManager.waitForAlterations(createResult);

        // When: updating with different iterations
        AlterUserScramCredentialsResult updateResult = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_256, "new-password", 8192);

        // Then: update should succeed
        Map<String, Throwable> errors = scramManager.waitForAlterations(updateResult);
        assertTrue(errors.isEmpty(), "Update should have no errors");

        // Verify credential was updated
        Map<String, List<ScramCredentialInfo>> credentials =
                scramManager.describeUserScramCredentials(List.of(TEST_USER_1));

        ScramCredentialInfo credInfo = credentials.get(TEST_USER_1).get(0);
        assertEquals(8192, credInfo.iterations(), "Iterations should be updated to 8192");
    }

    @Test
    @Order(5)
    @DisplayName("AC#5: Can delete SCRAM credential")
    void testDeleteUserScramCredential() {
        // Given: user with credential
        AlterUserScramCredentialsResult createResult = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_256, TEST_PASSWORD, DEFAULT_ITERATIONS);
        scramManager.waitForAlterations(createResult);

        // When: deleting credential
        AlterUserScramCredentialsResult deleteResult = scramManager.deleteUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_256);

        // Then: deletion should succeed
        Map<String, Throwable> errors = scramManager.waitForAlterations(deleteResult);
        assertTrue(errors.isEmpty(), "Deletion should have no errors: " + errors);

        // Verify credential was deleted
        Map<String, List<ScramCredentialInfo>> credentials =
                scramManager.describeUserScramCredentials(List.of(TEST_USER_1));

        assertTrue(!credentials.containsKey(TEST_USER_1) || credentials.get(TEST_USER_1).isEmpty(),
                "User should have no credentials after deletion");
    }

    @Test
    @Order(6)
    @DisplayName("AC#6: Can perform batch upsert operations")
    void testBatchUpsert() {
        // Given: batch of credentials to create
        Map<String, KafkaScramManager.CredentialSpec> credentials = Map.of(
                TEST_USER_1, new KafkaScramManager.CredentialSpec(
                        ScramMechanism.SCRAM_SHA_256, "password1", 4096),
                TEST_USER_2, new KafkaScramManager.CredentialSpec(
                        ScramMechanism.SCRAM_SHA_512, "password2", 8192)
        );

        // When: performing batch upsert
        AlterUserScramCredentialsResult result = scramManager.upsertUserScramCredentials(credentials);

        // Then: all operations should succeed
        assertNotNull(result);
        Map<String, Throwable> errors = scramManager.waitForAlterations(result);
        assertTrue(errors.isEmpty(), "Batch upsert should have no errors: " + errors);

        // Verify both credentials were created
        Map<String, List<ScramCredentialInfo>> described = scramManager.describeUserScramCredentials(
                List.of(TEST_USER_1, TEST_USER_2));

        assertTrue(described.containsKey(TEST_USER_1), "User 1 should exist");
        assertTrue(described.containsKey(TEST_USER_2), "User 2 should exist");
        assertEquals(1, described.get(TEST_USER_1).size());
        assertEquals(1, described.get(TEST_USER_2).size());

        assertEquals(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256,
                described.get(TEST_USER_1).get(0).mechanism());
        assertEquals(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_512,
                described.get(TEST_USER_2).get(0).mechanism());
    }

    @Test
    @Order(7)
    @DisplayName("AC#6: Can perform batch delete operations")
    void testBatchDelete() {
        // Given: users with credentials
        Map<String, KafkaScramManager.CredentialSpec> credentials = Map.of(
                TEST_USER_1, new KafkaScramManager.CredentialSpec(
                        ScramMechanism.SCRAM_SHA_256, "password1", 4096),
                TEST_USER_2, new KafkaScramManager.CredentialSpec(
                        ScramMechanism.SCRAM_SHA_256, "password2", 4096)
        );
        AlterUserScramCredentialsResult createResult = scramManager.upsertUserScramCredentials(credentials);
        scramManager.waitForAlterations(createResult);

        // When: performing batch delete
        Map<String, List<ScramMechanism>> deletions = Map.of(
                TEST_USER_1, List.of(ScramMechanism.SCRAM_SHA_256),
                TEST_USER_2, List.of(ScramMechanism.SCRAM_SHA_256)
        );
        AlterUserScramCredentialsResult deleteResult = scramManager.deleteUserScramCredentials(deletions);

        // Then: all deletions should succeed
        Map<String, Throwable> errors = scramManager.waitForAlterations(deleteResult);
        assertTrue(errors.isEmpty(), "Batch delete should have no errors: " + errors);

        // Verify both credentials were deleted
        Map<String, List<ScramCredentialInfo>> described = scramManager.describeUserScramCredentials(
                List.of(TEST_USER_1, TEST_USER_2));

        assertTrue(!described.containsKey(TEST_USER_1) || described.get(TEST_USER_1).isEmpty(),
                "User 1 should have no credentials");
        assertTrue(!described.containsKey(TEST_USER_2) || described.get(TEST_USER_2).isEmpty(),
                "User 2 should have no credentials");
    }

    @Test
    @Order(8)
    @DisplayName("AC#7: Returns AlterUserScramCredentialsResult with per-principal futures")
    void testPerPrincipalFutures() {
        // Given: batch operation
        Map<String, KafkaScramManager.CredentialSpec> credentials = Map.of(
                TEST_USER_1, new KafkaScramManager.CredentialSpec(
                        ScramMechanism.SCRAM_SHA_256, "password1", 4096)
        );

        // When: performing operation
        AlterUserScramCredentialsResult result = scramManager.upsertUserScramCredentials(credentials);

        // Then: result should contain futures map
        assertNotNull(result, "Result should not be null");
        assertNotNull(result.values(), "Result.values() should not be null");
        assertTrue(result.values().containsKey(TEST_USER_1),
                "Result should contain future for TEST_USER_1");

        // Verify we can access individual futures
        assertDoesNotThrow(() -> result.values().get(TEST_USER_1).get(),
                "Should be able to wait on individual principal future");
    }

    @Test
    @Order(9)
    @DisplayName("AC#8: Properly handles deletion of non-existent credential")
    void testDeleteNonExistentCredential() {
        // When: deleting credential that doesn't exist
        AlterUserScramCredentialsResult result = scramManager.deleteUserScramCredential(
                "non-existent-user-xyz", ScramMechanism.SCRAM_SHA_256);

        // Then: operation should complete (Kafka may succeed or fail depending on version)
        assertNotNull(result);
        Map<String, Throwable> errors = scramManager.waitForAlterations(result);

        // Note: Some Kafka versions succeed when deleting non-existent credentials,
        // others may fail. We just verify the operation completes without throwing.
        assertNotNull(errors, "Errors map should not be null");
    }

    @Test
    @Order(10)
    @DisplayName("AC#9: User can have multiple SCRAM mechanisms simultaneously")
    void testMultipleMechanismsPerUser() {
        // When: creating both SHA-256 and SHA-512 for same user
        AlterUserScramCredentialsResult sha256Result = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_256, TEST_PASSWORD, 4096);
        scramManager.waitForAlterations(sha256Result);

        AlterUserScramCredentialsResult sha512Result = scramManager.upsertUserScramCredential(
                TEST_USER_1, ScramMechanism.SCRAM_SHA_512, TEST_PASSWORD, 8192);
        scramManager.waitForAlterations(sha512Result);

        // Then: user should have both credentials
        Map<String, List<ScramCredentialInfo>> credentials =
                scramManager.describeUserScramCredentials(List.of(TEST_USER_1));

        assertTrue(credentials.containsKey(TEST_USER_1));
        List<ScramCredentialInfo> userCreds = credentials.get(TEST_USER_1);
        assertEquals(2, userCreds.size(), "User should have 2 credentials");

        List<org.apache.kafka.clients.admin.ScramMechanism> mechanisms = userCreds.stream()
                .map(ScramCredentialInfo::mechanism)
                .toList();

        assertTrue(mechanisms.contains(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256));
        assertTrue(mechanisms.contains(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_512));
    }

    /**
     * Helper method to convert Kafka's ScramMechanism to our domain enum.
     */
    private ScramMechanism convertFromKafkaScramMechanism(
            org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism) {
        return switch (kafkaMechanism) {
            case SCRAM_SHA_256 -> ScramMechanism.SCRAM_SHA_256;
            case SCRAM_SHA_512 -> ScramMechanism.SCRAM_SHA_512;
            default -> throw new IllegalArgumentException("Unsupported SCRAM mechanism: " + kafkaMechanism);
        };
    }
}
