package com.miimetiq.keycloak.sync.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a batch synchronization operation (reconciliation cycle).
 * Each batch groups multiple sync operations together and tracks aggregate statistics
 * such as total items processed, successful operations, and errors.
 */
@Entity
@Table(name = "sync_batch")
public class SyncBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "correlation_id", nullable = false, unique = true)
    private String correlationId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "items_total", nullable = false)
    private Integer itemsTotal;

    @Column(name = "items_success", nullable = false)
    private Integer itemsSuccess = 0;

    @Column(name = "items_error", nullable = false)
    private Integer itemsError = 0;

    // Constructors

    public SyncBatch() {
        // Default constructor required by JPA
    }

    public SyncBatch(String correlationId, LocalDateTime startedAt, String source, Integer itemsTotal) {
        this.correlationId = correlationId;
        this.startedAt = startedAt;
        this.source = source;
        this.itemsTotal = itemsTotal;
        this.itemsSuccess = 0;
        this.itemsError = 0;
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getItemsTotal() {
        return itemsTotal;
    }

    public void setItemsTotal(Integer itemsTotal) {
        this.itemsTotal = itemsTotal;
    }

    public Integer getItemsSuccess() {
        return itemsSuccess;
    }

    public void setItemsSuccess(Integer itemsSuccess) {
        this.itemsSuccess = itemsSuccess;
    }

    public Integer getItemsError() {
        return itemsError;
    }

    public void setItemsError(Integer itemsError) {
        this.itemsError = itemsError;
    }

    // Helper methods

    /**
     * Increments the success counter by one.
     */
    public void incrementSuccess() {
        this.itemsSuccess++;
    }

    /**
     * Increments the error counter by one.
     */
    public void incrementError() {
        this.itemsError++;
    }

    /**
     * Checks if the batch is complete (finished_at is set).
     */
    public boolean isComplete() {
        return finishedAt != null;
    }

    // equals, hashCode, and toString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncBatch syncBatch = (SyncBatch) o;
        return Objects.equals(id, syncBatch.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SyncBatch{" +
                "id=" + id +
                ", correlationId='" + correlationId + '\'' +
                ", startedAt=" + startedAt +
                ", finishedAt=" + finishedAt +
                ", source='" + source + '\'' +
                ", itemsTotal=" + itemsTotal +
                ", itemsSuccess=" + itemsSuccess +
                ", itemsError=" + itemsError +
                '}';
    }
}
