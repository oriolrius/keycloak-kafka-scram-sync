package com.miimetiq.keycloak.sync.webhook;

import java.time.Instant;

/**
 * Wrapper class for webhook events with metadata.
 * <p>
 * Encapsulates a Keycloak admin event with tracking metadata including
 * correlation ID, timestamps, and retry information for queue processing.
 */
public class WebhookEvent {

    private final String correlationId;
    private final KeycloakAdminEvent event;
    private final Instant enqueuedAt;
    private int retryCount;
    private Instant lastAttemptAt;

    /**
     * Create a new webhook event wrapper.
     *
     * @param correlationId unique identifier for tracking this event
     * @param event the Keycloak admin event
     */
    public WebhookEvent(String correlationId, KeycloakAdminEvent event) {
        this.correlationId = correlationId;
        this.event = event;
        this.enqueuedAt = Instant.now();
        this.retryCount = 0;
        this.lastAttemptAt = null;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public KeycloakAdminEvent getEvent() {
        return event;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.lastAttemptAt = Instant.now();
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    @Override
    public String toString() {
        return "WebhookEvent{" +
                "correlationId='" + correlationId + '\'' +
                ", event=" + event +
                ", enqueuedAt=" + enqueuedAt +
                ", retryCount=" + retryCount +
                ", lastAttemptAt=" + lastAttemptAt +
                '}';
    }
}
