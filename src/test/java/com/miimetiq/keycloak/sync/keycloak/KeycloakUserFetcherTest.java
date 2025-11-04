package com.miimetiq.keycloak.sync.keycloak;

import com.miimetiq.keycloak.sync.domain.KeycloakUserInfo;
import com.miimetiq.keycloak.sync.reconcile.ReconcileConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KeycloakUserFetcher.
 * <p>
 * Tests pagination, filtering, retry logic, and error handling with mocked Keycloak Admin client.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakUserFetcherTest {

    @Mock
    private Keycloak keycloak;

    @Mock
    private KeycloakConfig keycloakConfig;

    @Mock
    private ReconcileConfig reconcileConfig;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    private KeycloakUserFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new KeycloakUserFetcher();
        fetcher.keycloak = keycloak;
        fetcher.keycloakConfig = keycloakConfig;
        fetcher.reconcileConfig = reconcileConfig;

        // Default config behavior
        when(keycloakConfig.realm()).thenReturn("test-realm");
        when(reconcileConfig.pageSize()).thenReturn(100);

        // Setup resource chain
        when(keycloak.realm("test-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void testFetchAllUsers_SinglePage_Success() {
        // Given
        List<UserRepresentation> users = createMockUsers(50);
        when(usersResource.list(0, 100)).thenReturn(users);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(50, result.size());
        verify(usersResource, times(1)).list(0, 100);
    }

    @Test
    void testFetchAllUsers_MultiplePagesSuccess() {
        // Given - 250 users across 3 pages (100, 100, 50)
        List<UserRepresentation> page1 = createMockUsers(100);
        List<UserRepresentation> page2 = createMockUsers(100);
        List<UserRepresentation> page3 = createMockUsers(50);

        when(usersResource.list(0, 100)).thenReturn(page1);
        when(usersResource.list(100, 100)).thenReturn(page2);
        when(usersResource.list(200, 100)).thenReturn(page3);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(250, result.size());
        verify(usersResource, times(1)).list(0, 100);
        verify(usersResource, times(1)).list(100, 100);
        verify(usersResource, times(1)).list(200, 100);
    }

    @Test
    void testFetchAllUsers_EmptyResult() {
        // Given
        when(usersResource.list(0, 100)).thenReturn(Collections.emptyList());

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(usersResource, times(1)).list(0, 100);
    }

    @Test
    void testFetchAllUsers_FiltersServiceAccounts() {
        // Given
        List<UserRepresentation> users = new ArrayList<>();
        users.add(createUser("1", "service-account-test", "test@example.com", true));
        users.add(createUser("2", "normal-user", "user@example.com", true));
        users.add(createUser("3", "system-admin", "admin@example.com", true));
        users.add(createUser("4", "admin-console", "console@example.com", true));
        users.add(createUser("5", "another-user", "another@example.com", true));

        when(usersResource.list(0, 100)).thenReturn(users);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then - should filter out service-account-*, system-*, admin-*
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getUsername().equals("normal-user")));
        assertTrue(result.stream().anyMatch(u -> u.getUsername().equals("another-user")));
        assertFalse(result.stream().anyMatch(u -> u.getUsername().startsWith("service-account-")));
        assertFalse(result.stream().anyMatch(u -> u.getUsername().startsWith("system-")));
        assertFalse(result.stream().anyMatch(u -> u.getUsername().startsWith("admin-")));
    }

    @Test
    void testFetchAllUsers_FiltersDisabledUsers() {
        // Given
        List<UserRepresentation> users = new ArrayList<>();
        users.add(createUser("1", "enabled-user", "enabled@example.com", true));
        users.add(createUser("2", "disabled-user", "disabled@example.com", false));
        users.add(createUser("3", "another-enabled", "another@example.com", true));

        when(usersResource.list(0, 100)).thenReturn(users);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then - should filter out disabled users
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(KeycloakUserInfo::isEnabled));
        assertFalse(result.stream().anyMatch(u -> u.getUsername().equals("disabled-user")));
    }

    @Test
    void testFetchAllUsers_RetriesOnTransientFailure() {
        // Given - first attempt fails, second succeeds
        List<UserRepresentation> users = createMockUsers(10);

        when(usersResource.list(0, 100))
                .thenThrow(new RuntimeException("Transient network error"))
                .thenReturn(users);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then - should succeed after retry
        assertNotNull(result);
        assertEquals(10, result.size());
        // First call fails, second call succeeds (attempt 2)
        verify(usersResource, times(2)).list(0, 100);
    }

    @Test
    void testFetchAllUsers_FailsAfterMaxRetries() {
        // Given - all attempts fail
        when(usersResource.list(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Persistent error"));

        // When/Then - should throw after 3 retries
        KeycloakUserFetcher.KeycloakFetchException exception = assertThrows(
                KeycloakUserFetcher.KeycloakFetchException.class,
                () -> fetcher.fetchAllUsers()
        );

        assertTrue(exception.getMessage().contains("Failed after 3 attempts"));
        // Should attempt 3 times
        verify(usersResource, times(3)).list(0, 100);
    }

    @Test
    void testFetchAllUsers_HandlesNullEmail() {
        // Given - user with null email
        List<UserRepresentation> users = new ArrayList<>();
        users.add(createUser("1", "user-no-email", null, true));

        when(usersResource.list(0, 100)).thenReturn(users);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getEmail());
    }

    @Test
    void testFetchAllUsers_HandlesNullCreatedTimestamp() {
        // Given - user with null created timestamp
        UserRepresentation user = createUser("1", "legacy-user", "legacy@example.com", true);
        user.setCreatedTimestamp(null);

        when(usersResource.list(0, 100)).thenReturn(Collections.singletonList(user));
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getCreatedTimestamp());
    }

    @Test
    void testFetchAllUsers_HandlesNullEnabledField() {
        // Given - user with null enabled field (should default to true)
        UserRepresentation user = createUser("1", "user", "user@example.com", null);

        when(usersResource.list(0, 100)).thenReturn(Collections.singletonList(user));
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isEnabled());
    }

    @Test
    void testFetchAllUsers_UsesConfiguredPageSize() {
        // Given - custom page size
        when(reconcileConfig.pageSize()).thenReturn(50);

        List<UserRepresentation> page1 = createMockUsers(50);
        List<UserRepresentation> page2 = createMockUsers(25);

        when(usersResource.list(0, 50)).thenReturn(page1);
        when(usersResource.list(50, 50)).thenReturn(page2);
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(75, result.size());
        verify(usersResource, times(1)).list(0, 50);
        verify(usersResource, times(1)).list(50, 50);
    }

    @Test
    void testFetchAllUsers_PreservesUserData() {
        // Given
        UserRepresentation user = createUser("test-id-123", "testuser", "test@example.com", true);
        user.setCreatedTimestamp(1234567890L);

        when(usersResource.list(0, 100)).thenReturn(Collections.singletonList(user));
        // No need to stub next page - pagination stops when result < pageSize

        // When
        List<KeycloakUserInfo> result = fetcher.fetchAllUsers();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        KeycloakUserInfo userInfo = result.get(0);
        assertEquals("test-id-123", userInfo.getId());
        assertEquals("testuser", userInfo.getUsername());
        assertEquals("test@example.com", userInfo.getEmail());
        assertTrue(userInfo.isEnabled());
        assertEquals(1234567890L, userInfo.getCreatedTimestamp());
    }

    // Helper methods

    private List<UserRepresentation> createMockUsers(int count) {
        List<UserRepresentation> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createUser(
                    "id-" + i,
                    "user" + i,
                    "user" + i + "@example.com",
                    true
            ));
        }
        return users;
    }

    private UserRepresentation createUser(String id, String username, String email, Boolean enabled) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(enabled);
        user.setCreatedTimestamp(System.currentTimeMillis());
        return user;
    }
}
