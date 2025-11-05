package com.miimetiq.keycloak.sync.webhook;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventMapper - parsing and mapping Keycloak admin events.
 */
@QuarkusTest
@DisplayName("EventMapper Unit Tests")
class EventMapperTest {

    @Inject
    EventMapper eventMapper;

    // ========== USER CREATE Tests ==========

    @Test
    @DisplayName("USER CREATE should map to UPSERT operation")
    void testUserCreate() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("CREATE");
        event.setResourcePath("users/john.doe");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map USER CREATE event");
        assertEquals(SyncOperation.Type.UPSERT, result.get().getType());
        assertEquals("test-realm", result.get().getRealm());
        assertEquals("john.doe", result.get().getPrincipal());
        assertFalse(result.get().isPasswordChange());
    }

    // ========== USER UPDATE Tests ==========

    @Test
    @DisplayName("USER UPDATE should map to UPSERT operation")
    void testUserUpdate() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("UPDATE");
        event.setResourcePath("users/jane.smith");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map USER UPDATE event");
        assertEquals(SyncOperation.Type.UPSERT, result.get().getType());
        assertEquals("test-realm", result.get().getRealm());
        assertEquals("jane.smith", result.get().getPrincipal());
        assertFalse(result.get().isPasswordChange());
    }

    @Test
    @DisplayName("USER UPDATE with reset-password should flag password change")
    void testUserUpdatePasswordReset() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("UPDATE");
        event.setResourcePath("users/user123/reset-password");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map password reset event");
        assertEquals(SyncOperation.Type.UPSERT, result.get().getType());
        assertEquals("user123", result.get().getPrincipal());
        assertTrue(result.get().isPasswordChange(), "Should flag as password change");
    }

    @Test
    @DisplayName("USER UPDATE with reset-password-email should flag password change")
    void testUserUpdatePasswordResetEmail() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("UPDATE");
        event.setResourcePath("users/user456/reset-password-email");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent());
        assertTrue(result.get().isPasswordChange(), "Should flag as password change");
    }

    @Test
    @DisplayName("USER UPDATE with execute-actions-email should flag password change")
    void testUserUpdateExecuteActions() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("UPDATE");
        event.setResourcePath("users/user789/execute-actions-email");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent());
        assertTrue(result.get().isPasswordChange(), "Should flag as password change");
    }

    // ========== USER DELETE Tests ==========

    @Test
    @DisplayName("USER DELETE should map to DELETE operation")
    void testUserDelete() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("DELETE");
        event.setResourcePath("users/deleted.user");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map USER DELETE event");
        assertEquals(SyncOperation.Type.DELETE, result.get().getType());
        assertEquals("test-realm", result.get().getRealm());
        assertEquals("deleted.user", result.get().getPrincipal());
        assertFalse(result.get().isPasswordChange());
    }

    // ========== CLIENT Tests ==========

    @Test
    @DisplayName("CLIENT CREATE should map to UPSERT operation")
    void testClientCreate() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("CLIENT");
        event.setOperationType("CREATE");
        event.setResourcePath("clients/my-service");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map CLIENT CREATE event");
        assertEquals(SyncOperation.Type.UPSERT, result.get().getType());
        assertEquals("test-realm", result.get().getRealm());
        assertEquals("my-service", result.get().getPrincipal());
        assertFalse(result.get().isPasswordChange());
    }

    @Test
    @DisplayName("CLIENT UPDATE should map to UPSERT operation")
    void testClientUpdate() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("CLIENT");
        event.setOperationType("UPDATE");
        event.setResourcePath("clients/my-service");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map CLIENT UPDATE event");
        assertEquals(SyncOperation.Type.UPSERT, result.get().getType());
        assertEquals("my-service", result.get().getPrincipal());
    }

    @Test
    @DisplayName("CLIENT DELETE should map to DELETE operation")
    void testClientDelete() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("CLIENT");
        event.setOperationType("DELETE");
        event.setResourcePath("clients/old-service");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent(), "Should map CLIENT DELETE event");
        assertEquals(SyncOperation.Type.DELETE, result.get().getType());
        assertEquals("old-service", result.get().getPrincipal());
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    @DisplayName("Null event should return empty")
    void testNullEvent() {
        Optional<SyncOperation> result = eventMapper.mapEvent(null);
        assertFalse(result.isPresent(), "Should return empty for null event");
    }

    @Test
    @DisplayName("Event with missing resourceType should return empty")
    void testMissingResourceType() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setOperationType("CREATE");
        event.setResourcePath("users/test");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should return empty for missing resourceType");
    }

    @Test
    @DisplayName("Event with missing operationType should return empty")
    void testMissingOperationType() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setResourcePath("users/test");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should return empty for missing operationType");
    }

    @Test
    @DisplayName("Event with missing resourcePath should return empty")
    void testMissingResourcePath() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("CREATE");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should return empty for missing resourcePath");
    }

    @Test
    @DisplayName("Unknown resource type should return empty")
    void testUnknownResourceType() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("REALM");
        event.setOperationType("UPDATE");
        event.setResourcePath("realms/test-realm");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should ignore unknown resource type");
    }

    @Test
    @DisplayName("Unknown operation type should return empty")
    void testUnknownOperationType() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("DISABLE");
        event.setResourcePath("users/test");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should ignore unknown operation type");
    }

    @Test
    @DisplayName("Malformed user resourcePath should return empty")
    void testMalformedUserPath() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("CREATE");
        event.setResourcePath("invalid-path");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should return empty for malformed path");
    }

    @Test
    @DisplayName("Malformed client resourcePath should return empty")
    void testMalformedClientPath() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("CLIENT");
        event.setOperationType("CREATE");
        event.setResourcePath("invalid-path");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);
        assertFalse(result.isPresent(), "Should return empty for malformed path");
    }

    @Test
    @DisplayName("User path with UUID should extract correctly")
    void testUserPathWithUuid() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("CREATE");
        event.setResourcePath("users/550e8400-e29b-41d4-a716-446655440000");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.get().getPrincipal());
    }

    @Test
    @DisplayName("User path with nested resource should extract user ID")
    void testUserPathNested() {
        KeycloakAdminEvent event = new KeycloakAdminEvent();
        event.setResourceType("USER");
        event.setOperationType("UPDATE");
        event.setResourcePath("users/john.doe/consents/client-123");
        event.setRealmId("test-realm");

        Optional<SyncOperation> result = eventMapper.mapEvent(event);

        assertTrue(result.isPresent());
        assertEquals("john.doe", result.get().getPrincipal());
    }

    @Test
    @DisplayName("Case-insensitive resource type matching")
    void testCaseInsensitiveResourceType() {
        KeycloakAdminEvent event1 = new KeycloakAdminEvent();
        event1.setResourceType("user");  // lowercase
        event1.setOperationType("CREATE");
        event1.setResourcePath("users/test");
        event1.setRealmId("test-realm");

        KeycloakAdminEvent event2 = new KeycloakAdminEvent();
        event2.setResourceType("User");  // mixed case
        event2.setOperationType("CREATE");
        event2.setResourcePath("users/test");
        event2.setRealmId("test-realm");

        assertTrue(eventMapper.mapEvent(event1).isPresent(), "Should handle lowercase");
        assertTrue(eventMapper.mapEvent(event2).isPresent(), "Should handle mixed case");
    }

    @Test
    @DisplayName("Case-insensitive operation type matching")
    void testCaseInsensitiveOperationType() {
        KeycloakAdminEvent event1 = new KeycloakAdminEvent();
        event1.setResourceType("USER");
        event1.setOperationType("create");  // lowercase
        event1.setResourcePath("users/test");
        event1.setRealmId("test-realm");

        KeycloakAdminEvent event2 = new KeycloakAdminEvent();
        event2.setResourceType("USER");
        event2.setOperationType("Create");  // mixed case
        event2.setResourcePath("users/test");
        event2.setRealmId("test-realm");

        assertTrue(eventMapper.mapEvent(event1).isPresent(), "Should handle lowercase");
        assertTrue(eventMapper.mapEvent(event2).isPresent(), "Should handle mixed case");
    }
}
