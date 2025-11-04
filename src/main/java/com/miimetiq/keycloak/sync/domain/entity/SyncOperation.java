package com.miimetiq.keycloak.sync.domain.entity;

import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing an individual synchronization operation between Keycloak and Kafka.
 * Each operation tracks a single action (SCRAM credential management or ACL operation)
 * with its result, duration, and any error information.
 */
@Entity
@Table(name = "sync_operation")
public class SyncOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "realm", nullable = false)
    private String realm;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @Column(name = "principal", nullable = false)
    private String principal;

    @Enumerated(EnumType.STRING)
    @Column(name = "op_type", nullable = false)
    private OpType opType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mechanism")
    private ScramMechanism mechanism;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private OperationResult result;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    // Constructors

    public SyncOperation() {
        // Default constructor required by JPA
    }

    public SyncOperation(String correlationId, LocalDateTime occurredAt, String realm,
                         String clusterId, String principal, OpType opType,
                         OperationResult result, Integer durationMs) {
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.realm = realm;
        this.clusterId = clusterId;
        this.principal = principal;
        this.opType = opType;
        this.result = result;
        this.durationMs = durationMs;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public OpType getOpType() {
        return opType;
    }

    public void setOpType(OpType opType) {
        this.opType = opType;
    }

    public ScramMechanism getMechanism() {
        return mechanism;
    }

    public void setMechanism(ScramMechanism mechanism) {
        this.mechanism = mechanism;
    }

    public OperationResult getResult() {
        return result;
    }

    public void setResult(OperationResult result) {
        this.result = result;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    // equals, hashCode, and toString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncOperation that = (SyncOperation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SyncOperation{" +
                "id=" + id +
                ", correlationId='" + correlationId + '\'' +
                ", occurredAt=" + occurredAt +
                ", realm='" + realm + '\'' +
                ", clusterId='" + clusterId + '\'' +
                ", principal='" + principal + '\'' +
                ", opType=" + opType +
                ", mechanism=" + mechanism +
                ", result=" + result +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", durationMs=" + durationMs +
                '}';
    }
}
