package com.miimetiq.keycloak.sync.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing the retention policy configuration and current database size.
 * This is a single-row table (id always equals 1) that stores the global retention
 * settings and tracks the approximate size of the database.
 */
@Entity
@Table(name = "retention_state")
public class RetentionState {

    /**
     * Constant ID value for the single retention state row.
     * This table should only ever have one row with id=1.
     */
    public static final Integer SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Integer id = SINGLETON_ID;

    @Column(name = "max_bytes")
    private Long maxBytes;

    @Column(name = "max_age_days")
    private Integer maxAgeDays;

    @Column(name = "approx_db_bytes", nullable = false)
    private Long approxDbBytes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors

    public RetentionState() {
        // Default constructor required by JPA
        this.id = SINGLETON_ID;
    }

    public RetentionState(Long approxDbBytes, LocalDateTime updatedAt) {
        this.id = SINGLETON_ID;
        this.approxDbBytes = approxDbBytes;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        if (!SINGLETON_ID.equals(id)) {
            throw new IllegalArgumentException("RetentionState id must always be " + SINGLETON_ID);
        }
        this.id = id;
    }

    public Long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(Long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public Integer getMaxAgeDays() {
        return maxAgeDays;
    }

    public void setMaxAgeDays(Integer maxAgeDays) {
        this.maxAgeDays = maxAgeDays;
    }

    public Long getApproxDbBytes() {
        return approxDbBytes;
    }

    public void setApproxDbBytes(Long approxDbBytes) {
        this.approxDbBytes = approxDbBytes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods

    /**
     * Checks if a maximum size limit is configured.
     */
    public boolean hasMaxBytesLimit() {
        return maxBytes != null;
    }

    /**
     * Checks if a maximum age limit is configured.
     */
    public boolean hasMaxAgeLimit() {
        return maxAgeDays != null;
    }

    /**
     * Updates the approximate database size and the updated timestamp.
     */
    public void updateSize(Long newApproxDbBytes, LocalDateTime timestamp) {
        this.approxDbBytes = newApproxDbBytes;
        this.updatedAt = timestamp;
    }

    // equals, hashCode, and toString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetentionState that = (RetentionState) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RetentionState{" +
                "id=" + id +
                ", maxBytes=" + maxBytes +
                ", maxAgeDays=" + maxAgeDays +
                ", approxDbBytes=" + approxDbBytes +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
