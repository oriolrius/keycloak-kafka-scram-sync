package com.miimetiq.keycloak.sync.kafka;

import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsResult;
import org.apache.kafka.clients.admin.DescribeUserScramCredentialsResult;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.UserScramCredentialAlteration;
import org.apache.kafka.clients.admin.UserScramCredentialDeletion;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.clients.admin.UserScramCredentialsDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaScramManager with mocked AdminClient.
 * <p>
 * Tests all SCRAM credential operations with comprehensive error handling scenarios.
 */
@QuarkusTest
@DisplayName("KafkaScramManager Unit Tests")
class KafkaScramManagerTest {

    @Inject
    KafkaScramManager scramManager;

    private AdminClient adminClient;
    private SyncMetrics syncMetrics;

    private Timer.Sample timerSample;

    @BeforeEach
    void setUp() {
        // Create mocks
        adminClient = mock(AdminClient.class);
        syncMetrics = mock(SyncMetrics.class);
        timerSample = mock(Timer.Sample.class);

        // Install mocks using QuarkusMock
        QuarkusMock.installMockForType(adminClient, AdminClient.class);
        QuarkusMock.installMockForType(syncMetrics, SyncMetrics.class);

        // Configure mock behavior
        when(syncMetrics.startAdminOpTimer()).thenReturn(timerSample);
    }

    @Test
    @DisplayName("describeUserScramCredentials returns map of principals to mechanisms")
    void testDescribeUserScramCredentials_Success() throws Exception {
        // Given: mock AdminClient returns credential info
        ScramCredentialInfo sha256Info = new ScramCredentialInfo(
                org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256, 4096);
        ScramCredentialInfo sha512Info = new ScramCredentialInfo(
                org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_512, 4096);

        UserScramCredentialsDescription user1Desc = new UserScramCredentialsDescription(
                "user1", List.of(sha256Info, sha512Info));
        UserScramCredentialsDescription user2Desc = new UserScramCredentialsDescription(
                "user2", List.of(sha256Info));

        Map<String, UserScramCredentialsDescription> descriptionMap = Map.of(
                "user1", user1Desc,
                "user2", user2Desc
        );

        KafkaFuture<Map<String, UserScramCredentialsDescription>> future =
                KafkaFuture.completedFuture(descriptionMap);

        DescribeUserScramCredentialsResult mockResult = mock(DescribeUserScramCredentialsResult.class);
        when(mockResult.all()).thenReturn(future);
        when(adminClient.describeUserScramCredentials()).thenReturn(mockResult);

        // When: describing all credentials
        Map<String, List<ScramCredentialInfo>> result = scramManager.describeUserScramCredentials();

        // Then: result contains all users and their mechanisms
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("user1"));
        assertTrue(result.containsKey("user2"));
        assertEquals(2, result.get("user1").size());
        assertEquals(1, result.get("user2").size());

        verify(adminClient).describeUserScramCredentials();
        verify(syncMetrics).recordAdminOpDuration(timerSample, "describe");
    }

    @Test
    @DisplayName("describeUserScramCredentials with specific principals")
    void testDescribeUserScramCredentials_SpecificPrincipals() throws Exception {
        // Given: mock AdminClient for specific principals
        ScramCredentialInfo sha256Info = new ScramCredentialInfo(
                org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256, 4096);

        UserScramCredentialsDescription userDesc = new UserScramCredentialsDescription(
                "alice", List.of(sha256Info));

        Map<String, UserScramCredentialsDescription> descriptionMap = Map.of("alice", userDesc);

        KafkaFuture<Map<String, UserScramCredentialsDescription>> future =
                KafkaFuture.completedFuture(descriptionMap);

        DescribeUserScramCredentialsResult mockResult = mock(DescribeUserScramCredentialsResult.class);
        when(mockResult.all()).thenReturn(future);
        when(adminClient.describeUserScramCredentials(List.of("alice"))).thenReturn(mockResult);

        // When: describing specific principals
        Map<String, List<ScramCredentialInfo>> result =
                scramManager.describeUserScramCredentials(List.of("alice"));

        // Then: result contains only requested principal
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("alice"));
        assertEquals(1, result.get("alice").size());

        verify(adminClient).describeUserScramCredentials(List.of("alice"));
    }

    @Test
    @DisplayName("describeUserScramCredentials handles UnsupportedVersionException")
    void testDescribeUserScramCredentials_UnsupportedVersion() throws Exception {
        // Given: Kafka broker doesn't support SCRAM
        KafkaFuture<Map<String, UserScramCredentialsDescription>> future = mock(KafkaFuture.class);
        when(future.get()).thenThrow(new ExecutionException(new UnsupportedVersionException("Not supported")));

        DescribeUserScramCredentialsResult mockResult = mock(DescribeUserScramCredentialsResult.class);
        when(mockResult.all()).thenReturn(future);
        when(adminClient.describeUserScramCredentials()).thenReturn(mockResult);

        // When/Then: should throw KafkaScramException
        KafkaScramManager.KafkaScramException exception = assertThrows(
                KafkaScramManager.KafkaScramException.class,
                () -> scramManager.describeUserScramCredentials()
        );

        assertTrue(exception.getMessage().contains("does not support SCRAM"));
        verify(syncMetrics).recordAdminOpDuration(timerSample, "describe");
    }

    @Test
    @DisplayName("upsertUserScramCredential creates credential for single user")
    void testUpsertUserScramCredential_SingleUser() {
        // Given: mock AdminClient
        AlterUserScramCredentialsResult mockResult = mock(AlterUserScramCredentialsResult.class);
        when(mockResult.values()).thenReturn(Collections.emptyMap());
        when(adminClient.alterUserScramCredentials(any())).thenReturn(mockResult);

        // When: upserting single credential
        AlterUserScramCredentialsResult result = scramManager.upsertUserScramCredential(
                "bob", ScramMechanism.SCRAM_SHA_256, "password123", 4096);

        // Then: alteration was submitted
        assertNotNull(result);

        ArgumentCaptor<List<UserScramCredentialAlteration>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(adminClient).alterUserScramCredentials(captor.capture());

        List<UserScramCredentialAlteration> alterations = captor.getValue();
        assertEquals(1, alterations.size());
        assertTrue(alterations.get(0) instanceof UserScramCredentialUpsertion);

        UserScramCredentialUpsertion upsertion = (UserScramCredentialUpsertion) alterations.get(0);
        assertEquals("bob", upsertion.user());
        assertEquals(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256,
                upsertion.credentialInfo().mechanism());
        assertEquals(4096, upsertion.credentialInfo().iterations());

        verify(syncMetrics).recordAdminOpDuration(timerSample, "upsert");
    }

    @Test
    @DisplayName("upsertUserScramCredentials supports batch operations")
    void testUpsertUserScramCredentials_Batch() {
        // Given: batch of credentials to upsert
        Map<String, KafkaScramManager.CredentialSpec> credentials = new HashMap<>();
        credentials.put("user1", new KafkaScramManager.CredentialSpec(
                ScramMechanism.SCRAM_SHA_256, "pass1", 4096));
        credentials.put("user2", new KafkaScramManager.CredentialSpec(
                ScramMechanism.SCRAM_SHA_512, "pass2", 8192));

        AlterUserScramCredentialsResult mockResult = mock(AlterUserScramCredentialsResult.class);
        when(mockResult.values()).thenReturn(Collections.emptyMap());
        when(adminClient.alterUserScramCredentials(any())).thenReturn(mockResult);

        // When: upserting batch
        AlterUserScramCredentialsResult result = scramManager.upsertUserScramCredentials(credentials);

        // Then: all alterations submitted
        assertNotNull(result);

        ArgumentCaptor<List<UserScramCredentialAlteration>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(adminClient).alterUserScramCredentials(captor.capture());

        List<UserScramCredentialAlteration> alterations = captor.getValue();
        assertEquals(2, alterations.size());
        assertTrue(alterations.stream().allMatch(a -> a instanceof UserScramCredentialUpsertion));

        verify(syncMetrics).recordAdminOpDuration(timerSample, "upsert");
    }

    @Test
    @DisplayName("deleteUserScramCredential deletes single credential")
    void testDeleteUserScramCredential_Single() {
        // Given: mock AdminClient
        AlterUserScramCredentialsResult mockResult = mock(AlterUserScramCredentialsResult.class);
        when(mockResult.values()).thenReturn(Collections.emptyMap());
        when(adminClient.alterUserScramCredentials(any())).thenReturn(mockResult);

        // When: deleting single credential
        AlterUserScramCredentialsResult result = scramManager.deleteUserScramCredential(
                "charlie", ScramMechanism.SCRAM_SHA_256);

        // Then: deletion was submitted
        assertNotNull(result);

        ArgumentCaptor<List<UserScramCredentialAlteration>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(adminClient).alterUserScramCredentials(captor.capture());

        List<UserScramCredentialAlteration> alterations = captor.getValue();
        assertEquals(1, alterations.size());
        assertTrue(alterations.get(0) instanceof UserScramCredentialDeletion);

        UserScramCredentialDeletion deletion = (UserScramCredentialDeletion) alterations.get(0);
        assertEquals("charlie", deletion.user());
        assertEquals(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256,
                deletion.mechanism());

        verify(syncMetrics).recordAdminOpDuration(timerSample, "delete");
    }

    @Test
    @DisplayName("deleteUserScramCredentials supports batch operations")
    void testDeleteUserScramCredentials_Batch() {
        // Given: batch of credentials to delete
        Map<String, List<ScramMechanism>> deletions = new HashMap<>();
        deletions.put("user1", List.of(ScramMechanism.SCRAM_SHA_256, ScramMechanism.SCRAM_SHA_512));
        deletions.put("user2", List.of(ScramMechanism.SCRAM_SHA_256));

        AlterUserScramCredentialsResult mockResult = mock(AlterUserScramCredentialsResult.class);
        when(mockResult.values()).thenReturn(Collections.emptyMap());
        when(adminClient.alterUserScramCredentials(any())).thenReturn(mockResult);

        // When: deleting batch
        AlterUserScramCredentialsResult result = scramManager.deleteUserScramCredentials(deletions);

        // Then: all deletions submitted (3 total: 2 for user1, 1 for user2)
        assertNotNull(result);

        ArgumentCaptor<List<UserScramCredentialAlteration>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(adminClient).alterUserScramCredentials(captor.capture());

        List<UserScramCredentialAlteration> alterations = captor.getValue();
        assertEquals(3, alterations.size());
        assertTrue(alterations.stream().allMatch(a -> a instanceof UserScramCredentialDeletion));

        verify(syncMetrics).recordAdminOpDuration(timerSample, "delete");
    }

    @Test
    @DisplayName("alterUserScramCredentials returns null for empty alterations")
    void testAlterUserScramCredentials_EmptyList() {
        // When: passing null alterations
        AlterUserScramCredentialsResult result = scramManager.alterUserScramCredentials(null);

        // Then: should return null without calling AdminClient
        assertNull(result);
        verify(adminClient, never()).alterUserScramCredentials(any());
        verify(syncMetrics, never()).recordAdminOpDuration(any(), anyString());
    }

    @Test
    @DisplayName("alterUserScramCredentials handles AdminClient exceptions")
    void testAlterUserScramCredentials_Exception() {
        // Given: AdminClient throws exception
        List<UserScramCredentialAlteration> alterations = List.of(
                new UserScramCredentialUpsertion(
                        "user1",
                        new ScramCredentialInfo(
                                org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256, 4096),
                        "password"
                )
        );

        when(adminClient.alterUserScramCredentials(any()))
                .thenThrow(new RuntimeException("Kafka connection failed"));

        // When/Then: should throw KafkaScramException
        KafkaScramManager.KafkaScramException exception = assertThrows(
                KafkaScramManager.KafkaScramException.class,
                () -> scramManager.alterUserScramCredentials(alterations)
        );

        assertTrue(exception.getMessage().contains("Failed to alter SCRAM credentials"));
        verify(syncMetrics).recordAdminOpDuration(timerSample, "upsert");
    }

    @Test
    @DisplayName("waitForAlterations waits for all operations and collects errors")
    void testWaitForAlterations_MixedResults() throws Exception {
        // Given: some operations succeed, some fail
        KafkaFuture<Void> successFuture = KafkaFuture.completedFuture(null);
        KafkaFuture<Void> failureFuture = mock(KafkaFuture.class);
        when(failureFuture.get()).thenThrow(new ExecutionException(
                new RuntimeException("User already exists")));

        Map<String, KafkaFuture<Void>> futuresMap = Map.of(
                "alice", successFuture,
                "bob", failureFuture
        );

        AlterUserScramCredentialsResult mockResult = mock(AlterUserScramCredentialsResult.class);
        when(mockResult.values()).thenReturn(futuresMap);

        // When: waiting for alterations
        Map<String, Throwable> errors = scramManager.waitForAlterations(mockResult);

        // Then: errors map contains only failed operations
        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertTrue(errors.containsKey("bob"));
        assertFalse(errors.containsKey("alice"));
        assertTrue(errors.get("bob").getMessage().contains("User already exists"));
    }

    @Test
    @DisplayName("waitForAlterations returns empty map when result is null")
    void testWaitForAlterations_NullResult() {
        // When: passing null result
        Map<String, Throwable> errors = scramManager.waitForAlterations(null);

        // Then: should return empty map
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("waitForAlterations handles InterruptedException")
    void testWaitForAlterations_Interrupted() throws Exception {
        // Given: future that throws InterruptedException
        KafkaFuture<Void> interruptedFuture = mock(KafkaFuture.class);
        when(interruptedFuture.get()).thenThrow(new InterruptedException("Thread interrupted"));

        Map<String, KafkaFuture<Void>> futuresMap = Map.of("user1", interruptedFuture);

        AlterUserScramCredentialsResult mockResult = mock(AlterUserScramCredentialsResult.class);
        when(mockResult.values()).thenReturn(futuresMap);

        // When: waiting for alterations
        Map<String, Throwable> errors = scramManager.waitForAlterations(mockResult);

        // Then: errors map contains interrupted operation
        assertEquals(1, errors.size());
        assertTrue(errors.containsKey("user1"));
        assertTrue(errors.get("user1") instanceof InterruptedException);

        // Verify interrupt status was restored
        assertTrue(Thread.interrupted(), "Thread interrupt flag should be set");
    }
}
