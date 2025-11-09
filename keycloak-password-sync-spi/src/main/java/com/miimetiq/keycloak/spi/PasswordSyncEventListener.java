package com.miimetiq.keycloak.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Event listener that intercepts password-related admin events and syncs
 * passwords directly to Kafka SCRAM credentials.
 *
 * This listener retrieves passwords from the ThreadLocal context set by
 * PasswordSyncHashProviderSimple, queries Keycloak for usernames, and
 * synchronizes to Kafka using AdminClient API.
 */
public class PasswordSyncEventListener implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(PasswordSyncEventListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeycloakSession session;
    private final KafkaScramSync kafkaSync;
    private final boolean kafkaSyncEnabled;

    public PasswordSyncEventListener(KeycloakSession session) {
        this.session = session;

        // Check if Kafka sync is enabled (default: true)
        String kafkaEnabled = System.getProperty("password.sync.kafka.enabled", "true");
        this.kafkaSyncEnabled = Boolean.parseBoolean(kafkaEnabled);

        // Initialize Kafka sync utility
        if (kafkaSyncEnabled) {
            this.kafkaSync = new KafkaScramSync();
            LOG.info("PasswordSyncEventListener initialized with direct Kafka sync enabled");
        } else {
            this.kafkaSync = null;
            LOG.warn("PasswordSyncEventListener initialized with Kafka sync DISABLED");
        }
    }

    @Override
    public void onEvent(Event event) {
        // We don't handle user events, only admin events
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        try {
            // Handle USER_CREATE and PASSWORD_RESET events
            boolean isUserCreate = event.getOperationType() == OperationType.CREATE &&
                    event.getResourceType() == ResourceType.USER;
            boolean isPasswordReset = event.getOperationType() == OperationType.ACTION &&
                    event.getResourcePath() != null &&
                    event.getResourcePath().contains("/reset-password");

            if (isUserCreate || isPasswordReset) {
                handlePasswordEvent(event, isUserCreate);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error handling admin event: %s", event.getResourcePath());
        }
    }

    private void handlePasswordEvent(AdminEvent event, boolean isUserCreate) {
        String userId = extractUserIdFromPath(event.getResourcePath());
        String username = null;
        String password = null;

        if (isUserCreate && event.getRepresentation() != null) {
            // Try to extract from representation JSON
            username = extractUsernameFromRepresentation(event.getRepresentation());
            password = extractPasswordFromCredentials(event.getRepresentation());
        }

        // If username not found, query Keycloak
        if (username == null || username.isEmpty()) {
            username = extractUsernameFromUserId(event, userId);
        }

        // Retrieve password from correlation context if not found in representation
        if (password == null || password.isEmpty()) {
            password = PasswordCorrelationContext.getAndClearPassword();
        }

        // Sync to Kafka if we have both username and password
        if (password != null && !password.isEmpty() &&
                username != null && !username.isEmpty()) {
            LOG.infof("Retrieved password from correlation context for user: %s", username);
            syncPasswordToKafka(username, password);
        } else {
            LOG.warnf("No password found for event type=%s, path=%s",
                    event.getOperationType(), event.getResourcePath());
        }
    }

    private String extractUserIdFromPath(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }

        // Extract userId from paths like "users/{userId}" or "users/{userId}/reset-password"
        String[] parts = resourcePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("users".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }

        return null;
    }

    private String extractUsernameFromRepresentation(String representation) {
        try {
            JsonNode root = MAPPER.readTree(representation);
            JsonNode usernameNode = root.get("username");
            if (usernameNode != null) {
                return usernameNode.asText();
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract username from representation: %s", e.getMessage());
        }
        return null;
    }

    private String extractPasswordFromCredentials(String representation) {
        try {
            JsonNode root = MAPPER.readTree(representation);
            JsonNode credentialsNode = root.get("credentials");

            if (credentialsNode != null && credentialsNode.isArray()) {
                for (JsonNode credNode : credentialsNode) {
                    JsonNode typeNode = credNode.get("type");
                    JsonNode valueNode = credNode.get("value");

                    if (typeNode != null && "password".equals(typeNode.asText()) &&
                            valueNode != null) {
                        return valueNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract password from credentials: %s", e.getMessage());
        }
        return null;
    }

    private String extractUsernameFromUserId(AdminEvent event, String userId) {
        // CRITICAL: Query Keycloak to get real username from userId
        // This is necessary because PASSWORD_RESET events don't include username in representation
        try {
            String realmId = event.getRealmId();
            RealmModel realm = session.realms().getRealm(realmId);

            if (realm != null && userId != null) {
                UserModel user = session.users().getUserById(realm, userId);

                if (user != null) {
                    String username = user.getUsername();
                    LOG.infof("Successfully retrieved username=%s for userId=%s", username, userId);
                    return username;
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error querying username for userId=%s", userId);
        }

        // Fallback to userId if query fails
        return userId;
    }

    /**
     * Synchronizes a password to Kafka SCRAM credentials.
     * If Kafka sync is disabled, this method does nothing.
     * If Kafka sync fails, the password change operation will fail atomically.
     *
     * @param username the Kafka principal/username
     * @param password the plaintext password
     * @throws RuntimeException if Kafka sync fails (to fail the password change atomically)
     */
    private void syncPasswordToKafka(String username, String password) {
        if (!kafkaSyncEnabled) {
            LOG.debugf("Kafka sync is disabled, skipping sync for user: %s", username);
            return;
        }

        try {
            kafkaSync.syncPasswordToKafka(username, password);
            LOG.infof("Successfully synced password to Kafka for user: %s", username);
        } catch (KafkaScramSync.KafkaSyncException e) {
            // Re-throw the exception to fail the password change atomically
            // This ensures password changes only succeed if both Keycloak and Kafka succeed
            LOG.errorf(e, "CRITICAL: Failed to sync password to Kafka for user: %s - %s. " +
                    "Password change will be rejected to maintain consistency.",
                    username, e.getMessage());
            throw new RuntimeException("Failed to sync password to Kafka: " + e.getMessage() +
                    ". Please ensure Kafka cluster is available and try again.", e);
        } catch (Exception e) {
            LOG.errorf(e, "CRITICAL: Unexpected error syncing password to Kafka for user: %s. " +
                    "Password change will be rejected to maintain consistency.", username);
            throw new RuntimeException("Unexpected error syncing password to Kafka: " + e.getMessage() +
                    ". Please ensure Kafka cluster is available and try again.", e);
        }
    }

    @Override
    public void close() {
        // No resources to close
    }
}
