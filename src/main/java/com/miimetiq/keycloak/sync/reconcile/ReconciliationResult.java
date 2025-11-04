package com.miimetiq.keycloak.sync.reconcile;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Summary result of a reconciliation operation.
 * <p>
 * Contains statistics about what was processed, how many succeeded/failed,
 * and timing information for monitoring and observability.
 */
public class ReconciliationResult {

    private final String correlationId;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final String source;
    private final int totalUsers;
    private final int successfulOperations;
    private final int failedOperations;
    private final Duration duration;

    public ReconciliationResult(String correlationId, LocalDateTime startedAt, LocalDateTime finishedAt,
                                String source, int totalUsers, int successfulOperations, int failedOperations) {
        this.correlationId = correlationId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.source = source;
        this.totalUsers = totalUsers;
        this.successfulOperations = successfulOperations;
        this.failedOperations = failedOperations;
        this.duration = Duration.between(startedAt, finishedAt);
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getSource() {
        return source;
    }

    public int getTotalUsers() {
        return totalUsers;
    }

    public int getSuccessfulOperations() {
        return successfulOperations;
    }

    public int getFailedOperations() {
        return failedOperations;
    }

    public Duration getDuration() {
        return duration;
    }

    public long getDurationMs() {
        return duration.toMillis();
    }

    public boolean hasFailures() {
        return failedOperations > 0;
    }

    @Override
    public String toString() {
        return "ReconciliationResult{" +
                "correlationId='" + correlationId + '\'' +
                ", source='" + source + '\'' +
                ", totalUsers=" + totalUsers +
                ", successfulOperations=" + successfulOperations +
                ", failedOperations=" + failedOperations +
                ", durationMs=" + getDurationMs() +
                '}';
    }
}
