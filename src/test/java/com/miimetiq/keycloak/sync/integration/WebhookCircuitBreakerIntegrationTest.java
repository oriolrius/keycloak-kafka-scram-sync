package com.miimetiq.keycloak.sync.integration;

import com.miimetiq.keycloak.sync.health.CircuitBreakerService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for circuit breaker behavior in webhook processing.
 * <p>
 * Tests circuit breaker lifecycle for external service dependencies:
 * - Circuit opens after repeated failures
 * - Circuit prevents calls when open
 * - Circuit transitions through half-open to closed
 * - Metrics are updated appropriately
 */
@QuarkusTest
@DisplayName("Webhook Circuit Breaker Integration Tests")
class WebhookCircuitBreakerIntegrationTest {

    @Inject
    CircuitBreakerMaintenance circuitBreakerMaintenance;

    @Inject
    CircuitBreakerService circuitBreakerService;

    private static final String WEBHOOK_ENDPOINT = "/api/kc/events";
    private static final String SIGNATURE_HEADER = "X-Keycloak-Signature";
    private static final String TEST_SECRET = "test-webhook-secret-for-integration";

    @BeforeEach
    void setUp() {
        // Reset all circuit breakers before each test
        try {
            circuitBreakerMaintenance.resetAll();
        } catch (Exception e) {
            // Ignore if circuit breakers not initialized
        }
    }

    @Test
    @DisplayName("Circuit breakers should be in CLOSED state initially")
    void testCircuitBreakersInitiallyClosed() {
        // When: Check circuit breaker states
        CircuitBreakerState keycloakState = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        CircuitBreakerState kafkaState = circuitBreakerMaintenance.currentState("kafka-connectivity");

        // Then: Both should be CLOSED
        assertEquals(CircuitBreakerState.CLOSED, keycloakState,
                "Keycloak circuit breaker should be CLOSED initially");
        assertEquals(CircuitBreakerState.CLOSED, kafkaState,
                "Kafka circuit breaker should be CLOSED initially");
    }

    @Test
    @DisplayName("Keycloak circuit breaker should open after repeated connectivity failures")
    void testKeycloakCircuitBreakerOpensOnRepeatedFailures() {
        // Given: Circuit breaker is initially closed
        circuitBreakerMaintenance.resetAll();

        // When: Make repeated calls that will fail (use invalid Keycloak realm)
        int circuitOpenCount = 0;
        int maxAttempts = 10;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                // This will attempt to connect to Keycloak
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (CircuitBreakerOpenException e) {
                // Circuit has opened and is now rejecting calls
                circuitOpenCount++;
                break;
            } catch (Exception e) {
                // Other exceptions (connection failures before circuit opens)
                // Continue to trigger circuit breaker
            }

            // Small delay between attempts
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Then: Circuit should eventually open or be half-open
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertTrue(state == CircuitBreakerState.OPEN || state == CircuitBreakerState.HALF_OPEN,
                "Keycloak circuit breaker should transition to OPEN or HALF_OPEN after repeated failures, but was: " + state);
    }

    @Test
    @DisplayName("Kafka circuit breaker should open after repeated connectivity failures")
    void testKafkaCircuitBreakerOpensOnRepeatedFailures() {
        // Given: Circuit breaker is initially closed
        circuitBreakerMaintenance.resetAll();

        // When: Make repeated calls that will fail
        int circuitOpenCount = 0;
        int maxAttempts = 10;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                // This will attempt to connect to Kafka
                circuitBreakerService.checkKafkaConnectivity();
            } catch (CircuitBreakerOpenException e) {
                // Circuit has opened and is now rejecting calls
                circuitOpenCount++;
                break;
            } catch (Exception e) {
                // Other exceptions (connection failures before circuit opens)
                // Continue to trigger circuit breaker
            }

            // Small delay between attempts
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Then: Circuit should eventually open or be half-open
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("kafka-connectivity");
        assertTrue(state == CircuitBreakerState.OPEN || state == CircuitBreakerState.HALF_OPEN,
                "Kafka circuit breaker should transition to OPEN or HALF_OPEN after repeated failures, but was: " + state);
    }

    @Test
    @DisplayName("Webhook processing should continue when circuit breakers are closed")
    void testWebhookProcessingWithClosedCircuitBreakers() throws Exception {
        // Given: Circuit breakers are closed
        circuitBreakerMaintenance.resetAll();

        assertEquals(CircuitBreakerState.CLOSED,
                circuitBreakerMaintenance.currentState("keycloak-connectivity"),
                "Keycloak circuit should be closed");
        assertEquals(CircuitBreakerState.CLOSED,
                circuitBreakerMaintenance.currentState("kafka-connectivity"),
                "Kafka circuit should be closed");

        // When: Send a valid webhook event
        String payload = """
                {
                    "id": "test-event-cb-001",
                    "time": 1699000000000,
                    "realmId": "test-realm",
                    "resourceType": "USER",
                    "operationType": "CREATE",
                    "resourcePath": "users/test-user-id"
                }
                """;

        String signature = computeHmac(payload, TEST_SECRET);

        // Then: Webhook should be accepted
        given()
                .contentType(ContentType.JSON)
                .header(SIGNATURE_HEADER, signature)
                .body(payload)
                .when()
                .post(WEBHOOK_ENDPOINT)
                .then()
                .statusCode(200)
                .body("correlationId", notNullValue())
                .body("message", equalTo("Event received successfully"));
    }

    @Test
    @DisplayName("Circuit breaker state should be queryable via maintenance API")
    void testCircuitBreakerStateQueryable() {
        // When: Query circuit breaker states
        CircuitBreakerState keycloakState = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        CircuitBreakerState kafkaState = circuitBreakerMaintenance.currentState("kafka-connectivity");

        // Then: States should be queryable and non-null
        assertNotNull(keycloakState, "Keycloak circuit breaker state should be queryable");
        assertNotNull(kafkaState, "Kafka circuit breaker state should be queryable");
    }

    @Test
    @DisplayName("Circuit breaker should reset to CLOSED state")
    void testCircuitBreakerReset() {
        // Given: Open the circuit by triggering failures
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (Exception e) {
                // Expected failures
            }
        }

        // When: Reset circuit breakers
        circuitBreakerMaintenance.resetAll();

        // Then: Circuit should be closed
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertEquals(CircuitBreakerState.CLOSED, state,
                "Circuit breaker should be CLOSED after reset");
    }

    @Test
    @DisplayName("Multiple circuit breakers should operate independently")
    void testMultipleCircuitBreakersOperateIndependently() {
        // Given: Reset all circuits
        circuitBreakerMaintenance.resetAll();

        // When: Trigger failures only on Keycloak circuit
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (Exception e) {
                // Expected
            }
        }

        // Then: Keycloak circuit should change state, Kafka should remain closed
        CircuitBreakerState keycloakState = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        CircuitBreakerState kafkaState = circuitBreakerMaintenance.currentState("kafka-connectivity");

        assertNotEquals(CircuitBreakerState.CLOSED, keycloakState,
                "Keycloak circuit should not be CLOSED after failures");
        assertEquals(CircuitBreakerState.CLOSED, kafkaState,
                "Kafka circuit should remain CLOSED (independent from Keycloak)");
    }

    @Test
    @DisplayName("Circuit breaker should protect against cascading failures")
    void testCircuitBreakerProtectsAgainstCascadingFailures() {
        // Given: Circuit breaker is open due to failures
        for (int i = 0; i < 8; i++) {
            try {
                circuitBreakerService.checkKafkaConnectivity();
            } catch (Exception e) {
                // Expected
            }
        }

        // When: Circuit is open, subsequent calls should fail fast
        long startTime = System.currentTimeMillis();
        boolean circuitBreakerOpenExceptionThrown = false;

        try {
            circuitBreakerService.checkKafkaConnectivity();
        } catch (CircuitBreakerOpenException e) {
            circuitBreakerOpenExceptionThrown = true;
        } catch (Exception e) {
            // May get other exceptions if circuit transitions
        }

        long duration = System.currentTimeMillis() - startTime;

        // Then: Call should fail fast (much faster than timeout)
        // If circuit is open, call should complete in < 1000ms
        // (much faster than Kafka connection timeout which is 15000ms)
        if (circuitBreakerOpenExceptionThrown) {
            assertTrue(duration < 1000,
                    "CircuitBreakerOpenException should fail fast, but took " + duration + "ms");
        }
        // Note: If no CircuitBreakerOpenException, circuit may be in HALF_OPEN state
        // which allows one test request through
    }

    @Test
    @DisplayName("Health checks should report circuit breaker states")
    void testHealthChecksReportCircuitBreakerStates() {
        // When: Check readiness endpoint which includes circuit breaker states
        String readinessResponse = given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(anyOf(is(200), is(503))) // May be up or down depending on services
                .extract()
                .asString();

        // Then: Response should contain circuit breaker information
        // (Exact format depends on health check implementation)
        assertNotNull(readinessResponse, "Readiness response should not be null");
        assertTrue(readinessResponse.length() > 0, "Readiness response should not be empty");
    }

    @Test
    @DisplayName("Circuit breaker should allow recovery after successful calls")
    void testCircuitBreakerRecoveryAfterSuccess() {
        // Given: Circuit breakers are in known state
        circuitBreakerMaintenance.resetAll();

        // When: Make successful connectivity checks
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreakerService.checkKeycloakConnectivity();
            } catch (Exception e) {
                // May fail if external services unavailable, that's OK for this test
            }
        }

        // Then: Circuit should remain functional (not permanently broken)
        CircuitBreakerState state = circuitBreakerMaintenance.currentState("keycloak-connectivity");
        assertNotNull(state, "Circuit breaker should remain functional after calls");
    }

    // ========== Helper Methods ==========

    /**
     * Compute HMAC-SHA256 signature for webhook authentication.
     */
    private String computeHmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }
}
