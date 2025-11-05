package com.miimetiq.keycloak.sync.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for RetentionConfigResource REST endpoints.
 * <p>
 * Tests retention configuration GET and PUT endpoints with real database.
 */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResource.class)
@DisplayName("Retention Configuration REST API Integration Tests")
class RetentionConfigResourceIntegrationTest {

    @Test
    @DisplayName("GET /api/config/retention should return current retention configuration")
    void testGetRetentionConfig() {
        given()
                .when()
                .get("/api/config/retention")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("maxBytes", notNullValue())
                .body("maxAgeDays", notNullValue())
                .body("approxDbBytes", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    @DisplayName("PUT /api/config/retention should update retention configuration")
    void testUpdateRetentionConfig_Success() {
        String requestBody = """
                {
                    "maxBytes": 536870912,
                    "maxAgeDays": 60
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("maxBytes", equalTo(536870912))
                .body("maxAgeDays", equalTo(60))
                .body("approxDbBytes", notNullValue())
                .body("updatedAt", notNullValue());

        // Verify the change persisted
        given()
                .when()
                .get("/api/config/retention")
                .then()
                .statusCode(200)
                .body("maxBytes", equalTo(536870912))
                .body("maxAgeDays", equalTo(60));
    }

    @Test
    @DisplayName("PUT /api/config/retention should update both maxBytes and maxAgeDays")
    void testUpdateRetentionConfig_BothFields() {
        String requestBody = """
                {
                    "maxBytes": 1073741824,
                    "maxAgeDays": 90
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200)
                .body("maxBytes", equalTo(1073741824))
                .body("maxAgeDays", equalTo(90));

        // Verify changes persisted
        given()
                .when()
                .get("/api/config/retention")
                .then()
                .statusCode(200)
                .body("maxBytes", equalTo(1073741824))
                .body("maxAgeDays", equalTo(90));
    }

    @Test
    @DisplayName("PUT /api/config/retention should reject negative maxBytes")
    void testUpdateRetentionConfig_NegativeMaxBytes() {
        String requestBody = """
                {
                    "maxBytes": -1000
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("non-negative"));
    }

    @Test
    @DisplayName("PUT /api/config/retention should reject negative maxAgeDays")
    void testUpdateRetentionConfig_NegativeMaxAgeDays() {
        String requestBody = """
                {
                    "maxAgeDays": -5
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("non-negative"));
    }

    @Test
    @DisplayName("PUT /api/config/retention should reject maxBytes exceeding 10GB limit")
    void testUpdateRetentionConfig_MaxBytesTooLarge() {
        String requestBody = """
                {
                    "maxBytes": 20000000000
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("10 GB"));
    }

    @Test
    @DisplayName("PUT /api/config/retention should reject maxAgeDays exceeding 3650 days limit")
    void testUpdateRetentionConfig_MaxAgeDaysTooLarge() {
        String requestBody = """
                {
                    "maxAgeDays": 5000
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("3650"));
    }

    @Test
    @DisplayName("PUT /api/config/retention should accept empty body as nulls (disables both limits)")
    void testUpdateRetentionConfig_EmptyRequestAsNulls() {
        // First set both to non-null
        String setupBody = """
                {
                    "maxBytes": 268435456,
                    "maxAgeDays": 30
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(setupBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200);

        // Now send empty body (both fields become null)
        String requestBody = "{}";

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200)
                .body("maxBytes", nullValue())
                .body("maxAgeDays", nullValue());
    }

    @Test
    @DisplayName("PUT /api/config/retention should accept null to disable limits")
    void testUpdateRetentionConfig_DisableLimits() {
        // Set both to null to disable retention limits
        String requestBody = """
                {
                    "maxBytes": null,
                    "maxAgeDays": null
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200)
                .body("maxBytes", nullValue())
                .body("maxAgeDays", nullValue());
    }

    @Test
    @DisplayName("Retention config endpoints should be accessible")
    void testEndpointsAccessible() {
        // GET endpoint
        given()
                .when()
                .get("/api/config/retention")
                .then()
                .statusCode(200);

        // PUT endpoint requires body
        given()
                .contentType(ContentType.JSON)
                .body("{\"maxBytes\": 268435456}")
                .when()
                .put("/api/config/retention")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Invalid HTTP methods should return 405")
    void testInvalidMethods() {
        // Retention endpoint doesn't accept POST
        given()
                .when()
                .post("/api/config/retention")
                .then()
                .statusCode(405); // Method Not Allowed

        // Retention endpoint doesn't accept DELETE
        given()
                .when()
                .delete("/api/config/retention")
                .then()
                .statusCode(405); // Method Not Allowed
    }
}
