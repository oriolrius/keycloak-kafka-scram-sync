package com.miimetiq.keycloak.sync.webhook;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps Keycloak Admin Events to internal synchronization operations.
 * <p>
 * Supports:
 * - User CREATE/UPDATE → UPSERT
 * - User DELETE → DELETE
 * - Password changes → UPSERT with password flag
 * - Client CREATE/UPDATE/DELETE → UPSERT/DELETE
 */
@ApplicationScoped
public class EventMapper {

    private static final Logger LOG = Logger.getLogger(EventMapper.class);

    // Pattern to extract username from resourcePath: "users/{userId}" or "users/{userId}/..."
    private static final Pattern USER_PATH_PATTERN = Pattern.compile("^users/([^/]+)(?:/.*)?$");

    // Pattern to extract client ID from resourcePath: "clients/{clientId}" or "clients/{clientId}/..."
    private static final Pattern CLIENT_PATH_PATTERN = Pattern.compile("^clients/([^/]+)(?:/.*)?$");

    /**
     * Map a Keycloak admin event to a sync operation.
     * <p>
     * Returns empty if the event should be ignored (unknown type, unsupported resource, etc.)
     *
     * @param event the Keycloak admin event
     * @return optional sync operation, empty if event should be ignored
     */
    public Optional<SyncOperation> mapEvent(KeycloakAdminEvent event) {
        if (event == null) {
            LOG.warn("Received null event");
            return Optional.empty();
        }

        String resourceType = event.getResourceType();
        String operationType = event.getOperationType();
        String resourcePath = event.getResourcePath();
        String realmId = event.getRealmId();

        if (resourceType == null || operationType == null || resourcePath == null) {
            LOG.warnf("Event missing required fields: resourceType=%s, operationType=%s, resourcePath=%s",
                    resourceType, operationType, resourcePath);
            return Optional.empty();
        }

        // Handle USER events
        if ("USER".equalsIgnoreCase(resourceType)) {
            return mapUserEvent(operationType, resourcePath, realmId);
        }

        // Handle CLIENT events
        if ("CLIENT".equalsIgnoreCase(resourceType)) {
            return mapClientEvent(operationType, resourcePath, realmId);
        }

        // Unknown/unsupported resource type - log and ignore
        LOG.debugf("Ignoring unsupported resource type: %s (operation=%s)", resourceType, operationType);
        return Optional.empty();
    }

    /**
     * Map a USER event to a sync operation.
     *
     * @param operationType the operation type (CREATE, UPDATE, DELETE)
     * @param resourcePath the resource path
     * @param realmId the realm ID
     * @return optional sync operation
     */
    private Optional<SyncOperation> mapUserEvent(String operationType, String resourcePath, String realmId) {
        // Extract username from resource path
        String principal = extractPrincipalFromPath(resourcePath, USER_PATH_PATTERN);
        if (principal == null) {
            LOG.warnf("Failed to extract username from resourcePath: %s", resourcePath);
            return Optional.empty();
        }

        // Check if this is a password change
        boolean isPasswordChange = resourcePath.contains("/reset-password") ||
                                   resourcePath.contains("/reset-password-email") ||
                                   resourcePath.contains("/execute-actions-email");

        switch (operationType.toUpperCase()) {
            case "CREATE":
                // User created → UPSERT credentials
                LOG.debugf("Mapping USER CREATE to UPSERT: principal=%s, realm=%s", principal, realmId);
                return Optional.of(new SyncOperation(SyncOperation.Type.UPSERT, realmId, principal, false));

            case "UPDATE":
                // User updated → UPSERT credentials (may be password change)
                LOG.debugf("Mapping USER UPDATE to UPSERT: principal=%s, realm=%s, passwordChange=%s",
                        principal, realmId, isPasswordChange);
                return Optional.of(new SyncOperation(SyncOperation.Type.UPSERT, realmId, principal, isPasswordChange));

            case "DELETE":
                // User deleted → DELETE credentials
                LOG.debugf("Mapping USER DELETE to DELETE: principal=%s, realm=%s", principal, realmId);
                return Optional.of(new SyncOperation(SyncOperation.Type.DELETE, realmId, principal, false));

            default:
                LOG.debugf("Ignoring unsupported USER operation: %s", operationType);
                return Optional.empty();
        }
    }

    /**
     * Map a CLIENT event to a sync operation.
     *
     * @param operationType the operation type (CREATE, UPDATE, DELETE)
     * @param resourcePath the resource path
     * @param realmId the realm ID
     * @return optional sync operation
     */
    private Optional<SyncOperation> mapClientEvent(String operationType, String resourcePath, String realmId) {
        // Extract client ID from resource path
        String principal = extractPrincipalFromPath(resourcePath, CLIENT_PATH_PATTERN);
        if (principal == null) {
            LOG.warnf("Failed to extract client ID from resourcePath: %s", resourcePath);
            return Optional.empty();
        }

        switch (operationType.toUpperCase()) {
            case "CREATE":
            case "UPDATE":
                // Client created/updated → UPSERT credentials
                LOG.debugf("Mapping CLIENT %s to UPSERT: principal=%s, realm=%s", operationType, principal, realmId);
                return Optional.of(new SyncOperation(SyncOperation.Type.UPSERT, realmId, principal, false));

            case "DELETE":
                // Client deleted → DELETE credentials
                LOG.debugf("Mapping CLIENT DELETE to DELETE: principal=%s, realm=%s", principal, realmId);
                return Optional.of(new SyncOperation(SyncOperation.Type.DELETE, realmId, principal, false));

            default:
                LOG.debugf("Ignoring unsupported CLIENT operation: %s", operationType);
                return Optional.empty();
        }
    }

    /**
     * Extract principal (username or client ID) from resource path.
     *
     * @param resourcePath the resource path
     * @param pattern the regex pattern to match
     * @return principal, or null if not found
     */
    private String extractPrincipalFromPath(String resourcePath, Pattern pattern) {
        if (resourcePath == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(resourcePath);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
