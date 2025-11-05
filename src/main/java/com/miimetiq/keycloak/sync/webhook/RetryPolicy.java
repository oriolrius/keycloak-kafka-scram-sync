package com.miimetiq.keycloak.sync.webhook;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Retry policy with exponential backoff for failed event processing.
 * <p>
 * Implements exponential backoff with a base delay and maximum delay cap.
 * Formula: delay = min(base_delay * 2^(attempt - 1), max_delay)
 */
@ApplicationScoped
public class RetryPolicy {

    @ConfigProperty(name = "webhook.retry.max-attempts", defaultValue = "3")
    int maxAttempts;

    @ConfigProperty(name = "webhook.retry.base-delay-ms", defaultValue = "1000")
    long baseDelayMs;

    @ConfigProperty(name = "webhook.retry.max-delay-ms", defaultValue = "30000")
    long maxDelayMs;

    /**
     * Check if an event should be retried based on its retry count.
     *
     * @param retryCount current retry count (0 for first attempt)
     * @return true if event should be retried, false otherwise
     */
    public boolean shouldRetry(int retryCount) {
        return retryCount < maxAttempts;
    }

    /**
     * Calculate the backoff delay for a retry attempt using exponential backoff.
     * <p>
     * Formula: delay = min(base_delay * 2^(attempt - 1), max_delay)
     * <p>
     * Examples with base=1000ms:
     * - Attempt 1: 1000ms (1s)
     * - Attempt 2: 2000ms (2s)
     * - Attempt 3: 4000ms (4s)
     * - Attempt 4: 8000ms (8s)
     *
     * @param retryCount current retry count (0 for first attempt, 1 for first retry, etc.)
     * @return delay in milliseconds before next retry
     */
    public long calculateBackoffDelay(int retryCount) {
        if (retryCount <= 0) {
            return baseDelayMs;
        }

        // Calculate exponential backoff: base * 2^(retryCount - 1)
        long delay = baseDelayMs * (1L << (retryCount - 1));

        // Cap at maximum delay
        return Math.min(delay, maxDelayMs);
    }

    /**
     * Get maximum number of retry attempts.
     *
     * @return max retry attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Get base delay in milliseconds.
     *
     * @return base delay in ms
     */
    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    /**
     * Get maximum delay in milliseconds.
     *
     * @return max delay in ms
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    /**
     * Check if this is the final retry attempt.
     *
     * @param retryCount current retry count
     * @return true if this is the last attempt before permanent failure
     */
    public boolean isFinalAttempt(int retryCount) {
        return retryCount >= maxAttempts - 1;
    }
}
