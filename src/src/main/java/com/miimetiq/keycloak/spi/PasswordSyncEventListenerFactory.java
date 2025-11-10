package com.miimetiq.keycloak.spi;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory for PasswordSyncEventListener.
 *
 * This factory creates event listener instances that intercept password-related
 * admin events and synchronize passwords directly to Kafka SCRAM credentials.
 */
public class PasswordSyncEventListenerFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(PasswordSyncEventListenerFactory.class);
    private static final String PROVIDER_ID = "password-sync-listener";

    // Configuration for realm filtering
    private static Set<String> allowedRealms = Collections.emptySet();
    private static boolean realmFilteringEnabled = false;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        // Pass session and realm configuration to enable querying Keycloak for usernames
        return new PasswordSyncEventListener(session, allowedRealms, realmFilteringEnabled);
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Initializing PasswordSyncEventListener SPI");

        // Read realm list configuration
        // Priority: 1. Config.Scope, 2. System property, 3. Environment variable
        String realmListConfig = config.get("realms");
        if (realmListConfig == null || realmListConfig.trim().isEmpty()) {
            realmListConfig = System.getProperty("password.sync.realms");
        }
        if (realmListConfig == null || realmListConfig.trim().isEmpty()) {
            realmListConfig = System.getenv("PASSWORD_SYNC_REALMS");
        }

        // Parse and validate realm list
        if (realmListConfig != null && !realmListConfig.trim().isEmpty()) {
            allowedRealms = Arrays.stream(realmListConfig.split(","))
                    .map(String::trim)
                    .filter(realm -> !realm.isEmpty())
                    .collect(Collectors.toSet());

            realmFilteringEnabled = !allowedRealms.isEmpty();

            if (realmFilteringEnabled) {
                LOG.infof("Realm filtering ENABLED. Password sync will be restricted to realms: %s",
                        String.join(", ", allowedRealms));
            } else {
                LOG.info("Realm filtering is DISABLED (empty realm list). All realms will be synced.");
            }
        } else {
            realmFilteringEnabled = false;
            allowedRealms = Collections.emptySet();
            LOG.info("Realm filtering is DISABLED (no configuration found). All realms will be synced.");
        }

        // Initialize Kafka AdminClient on SPI startup
        try {
            KafkaAdminClientFactory.getAdminClient();
            LOG.info("Kafka AdminClient initialized successfully");
        } catch (Exception e) {
            LOG.error("Failed to initialize Kafka AdminClient - password sync may not work", e);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        LOG.info("Closing PasswordSyncEventListener SPI");
        // Close Kafka AdminClient on SPI shutdown
        KafkaAdminClientFactory.close();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
