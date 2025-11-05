package com.miimetiq.keycloak.sync.webhook;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RetryPolicy with exponential backoff.
 */
@QuarkusTest
@DisplayName("RetryPolicy Unit Tests")
class RetryPolicyTest {

    @Inject
    RetryPolicy retryPolicy;

    @Test
    @DisplayName("shouldRetry returns true when retry count is below max attempts")
    void testShouldRetry_BelowMax() {
        assertTrue(retryPolicy.shouldRetry(0), "Should retry on first failure");
        assertTrue(retryPolicy.shouldRetry(1), "Should retry on second failure");
        assertTrue(retryPolicy.shouldRetry(2), "Should retry on third failure");
    }

    @Test
    @DisplayName("shouldRetry returns false when retry count reaches max attempts")
    void testShouldRetry_AtMax() {
        int maxAttempts = retryPolicy.getMaxAttempts();
        assertFalse(retryPolicy.shouldRetry(maxAttempts),
                "Should not retry when max attempts reached");
        assertFalse(retryPolicy.shouldRetry(maxAttempts + 1),
                "Should not retry when beyond max attempts");
    }

    @Test
    @DisplayName("calculateBackoffDelay returns base delay for first retry")
    void testCalculateBackoffDelay_FirstRetry() {
        long delay = retryPolicy.calculateBackoffDelay(1);
        assertEquals(retryPolicy.getBaseDelayMs(), delay,
                "First retry should use base delay");
    }

    @Test
    @DisplayName("calculateBackoffDelay implements exponential backoff")
    void testCalculateBackoffDelay_Exponential() {
        long baseDelay = retryPolicy.getBaseDelayMs();

        // Test exponential growth: base * 2^(n-1)
        assertEquals(baseDelay, retryPolicy.calculateBackoffDelay(1),
                "Retry 1: should be base delay");
        assertEquals(baseDelay * 2, retryPolicy.calculateBackoffDelay(2),
                "Retry 2: should be 2x base delay");
        assertEquals(baseDelay * 4, retryPolicy.calculateBackoffDelay(3),
                "Retry 3: should be 4x base delay");
        assertEquals(baseDelay * 8, retryPolicy.calculateBackoffDelay(4),
                "Retry 4: should be 8x base delay");
    }

    @Test
    @DisplayName("calculateBackoffDelay respects maximum delay")
    void testCalculateBackoffDelay_MaxCap() {
        long maxDelay = retryPolicy.getMaxDelayMs();

        // With default config (base=1000ms, max=30000ms), retry 10 should exceed max
        long delay = retryPolicy.calculateBackoffDelay(10);

        assertTrue(delay <= maxDelay,
                "Delay should not exceed maximum: " + delay + " > " + maxDelay);
        assertEquals(maxDelay, delay,
                "Delay should be capped at maximum");
    }

    @Test
    @DisplayName("calculateBackoffDelay returns base delay for zero retry count")
    void testCalculateBackoffDelay_Zero() {
        long delay = retryPolicy.calculateBackoffDelay(0);
        assertEquals(retryPolicy.getBaseDelayMs(), delay,
                "Zero retry count should return base delay");
    }

    @Test
    @DisplayName("isFinalAttempt returns true on last retry")
    void testIsFinalAttempt() {
        int maxAttempts = retryPolicy.getMaxAttempts();

        assertFalse(retryPolicy.isFinalAttempt(0),
                "First attempt should not be final");
        assertFalse(retryPolicy.isFinalAttempt(maxAttempts - 2),
                "Second-to-last attempt should not be final");
        assertTrue(retryPolicy.isFinalAttempt(maxAttempts - 1),
                "Last attempt should be final");
        assertTrue(retryPolicy.isFinalAttempt(maxAttempts),
                "Beyond last attempt should also be final");
    }

    @Test
    @DisplayName("Backoff delays follow expected pattern with default config")
    void testBackoffPattern_DefaultConfig() {
        // With default base=1000ms, expected pattern is:
        // Retry 1: 1000ms (1s)
        // Retry 2: 2000ms (2s)
        // Retry 3: 4000ms (4s)

        assertEquals(1000L, retryPolicy.calculateBackoffDelay(1),
                "First retry: 1 second");
        assertEquals(2000L, retryPolicy.calculateBackoffDelay(2),
                "Second retry: 2 seconds");
        assertEquals(4000L, retryPolicy.calculateBackoffDelay(3),
                "Third retry: 4 seconds");
    }

    @Test
    @DisplayName("Configuration values are accessible")
    void testConfigurationAccessors() {
        assertTrue(retryPolicy.getMaxAttempts() > 0,
                "Max attempts should be positive");
        assertTrue(retryPolicy.getBaseDelayMs() > 0,
                "Base delay should be positive");
        assertTrue(retryPolicy.getMaxDelayMs() >= retryPolicy.getBaseDelayMs(),
                "Max delay should be >= base delay");
    }
}
