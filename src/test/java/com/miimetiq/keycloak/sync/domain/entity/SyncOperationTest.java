package com.miimetiq.keycloak.sync.domain.entity;

import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the SyncOperation entity.
 * Tests entity creation, field mappings, and business logic.
 */
class SyncOperationTest {

    @Test
    void testDefaultConstructor() {
        SyncOperation operation = new SyncOperation();
        assertNotNull(operation, "Default constructor should create an instance");
        assertNull(operation.getId(), "ID should be null for new entity");
    }

    @Test
    void testParameterizedConstructor() {
        // Given
        String correlationId = "corr-123";
        LocalDateTime occurredAt = LocalDateTime.now();
        String realm = "test-realm";
        String clusterId = "kafka-cluster-1";
        String principal = "user@example.com";
        OpType opType = OpType.SCRAM_UPSERT;
        OperationResult result = OperationResult.SUCCESS;
        Integer durationMs = 150;

        // When
        SyncOperation operation = new SyncOperation(
                correlationId, occurredAt, realm, clusterId,
                principal, opType, result, durationMs
        );

        // Then
        assertNull(operation.getId(), "ID should be null before persistence");
        assertEquals(correlationId, operation.getCorrelationId());
        assertEquals(occurredAt, operation.getOccurredAt());
        assertEquals(realm, operation.getRealm());
        assertEquals(clusterId, operation.getClusterId());
        assertEquals(principal, operation.getPrincipal());
        assertEquals(opType, operation.getOpType());
        assertEquals(result, operation.getResult());
        assertEquals(durationMs, operation.getDurationMs());
    }

    @Test
    void testAllFieldsWithSetters() {
        // Given
        SyncOperation operation = new SyncOperation();
        LocalDateTime now = LocalDateTime.now();

        // When
        operation.setId(1L);
        operation.setCorrelationId("corr-456");
        operation.setOccurredAt(now);
        operation.setRealm("production");
        operation.setClusterId("kafka-prod");
        operation.setPrincipal("admin@example.com");
        operation.setOpType(OpType.ACL_CREATE);
        operation.setMechanism(ScramMechanism.SCRAM_SHA_512);
        operation.setResult(OperationResult.ERROR);
        operation.setErrorCode("ERR_001");
        operation.setErrorMessage("Connection timeout");
        operation.setDurationMs(500);

        // Then
        assertEquals(1L, operation.getId());
        assertEquals("corr-456", operation.getCorrelationId());
        assertEquals(now, operation.getOccurredAt());
        assertEquals("production", operation.getRealm());
        assertEquals("kafka-prod", operation.getClusterId());
        assertEquals("admin@example.com", operation.getPrincipal());
        assertEquals(OpType.ACL_CREATE, operation.getOpType());
        assertEquals(ScramMechanism.SCRAM_SHA_512, operation.getMechanism());
        assertEquals(OperationResult.ERROR, operation.getResult());
        assertEquals("ERR_001", operation.getErrorCode());
        assertEquals("Connection timeout", operation.getErrorMessage());
        assertEquals(500, operation.getDurationMs());
    }

    @Test
    void testNullableFields() {
        // Mechanism, errorCode, and errorMessage should be nullable
        SyncOperation operation = new SyncOperation();

        assertNull(operation.getMechanism(), "Mechanism should be nullable");
        assertNull(operation.getErrorCode(), "Error code should be nullable");
        assertNull(operation.getErrorMessage(), "Error message should be nullable");
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        SyncOperation op1 = new SyncOperation();
        op1.setId(1L);

        SyncOperation op2 = new SyncOperation();
        op2.setId(1L);

        SyncOperation op3 = new SyncOperation();
        op3.setId(2L);

        // Then
        assertEquals(op1, op2, "Operations with same ID should be equal");
        assertEquals(op1.hashCode(), op2.hashCode(), "Operations with same ID should have same hashCode");
        assertNotEquals(op1, op3, "Operations with different IDs should not be equal");
    }

    @Test
    void testToString() {
        // Given
        SyncOperation operation = new SyncOperation();
        operation.setId(1L);
        operation.setCorrelationId("corr-789");
        operation.setPrincipal("test@example.com");

        // When
        String toString = operation.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("SyncOperation"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("corr-789"));
        assertTrue(toString.contains("test@example.com"));
    }

    @Test
    void testSuccessfulScramOperation() {
        // Test a successful SCRAM upsert operation
        SyncOperation operation = new SyncOperation(
                "batch-1",
                LocalDateTime.now(),
                "master",
                "cluster-1",
                "user1",
                OpType.SCRAM_UPSERT,
                OperationResult.SUCCESS,
                100
        );

        operation.setMechanism(ScramMechanism.SCRAM_SHA_256);

        assertEquals(OpType.SCRAM_UPSERT, operation.getOpType());
        assertEquals(OperationResult.SUCCESS, operation.getResult());
        assertEquals(ScramMechanism.SCRAM_SHA_256, operation.getMechanism());
        assertNull(operation.getErrorCode());
        assertNull(operation.getErrorMessage());
    }

    @Test
    void testFailedOperation() {
        // Test a failed operation with error details
        SyncOperation operation = new SyncOperation(
                "batch-2",
                LocalDateTime.now(),
                "master",
                "cluster-1",
                "user2",
                OpType.ACL_DELETE,
                OperationResult.ERROR,
                250
        );

        operation.setErrorCode("KAFKA_ERROR");
        operation.setErrorMessage("Unable to delete ACL: resource not found");

        assertEquals(OperationResult.ERROR, operation.getResult());
        assertNotNull(operation.getErrorCode());
        assertNotNull(operation.getErrorMessage());
        assertTrue(operation.getErrorMessage().contains("resource not found"));
    }
}
