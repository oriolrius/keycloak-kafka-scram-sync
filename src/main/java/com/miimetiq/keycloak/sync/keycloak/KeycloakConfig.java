package com.miimetiq.keycloak.sync.keycloak;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Keycloak configuration properties.
 * Reads from application.properties and environment variables.
 */
@ConfigMapping(prefix = "keycloak")
public interface KeycloakConfig {

    /**
     * Keycloak base URL.
     * Can be overridden with KC_BASE_URL environment variable.
     */
    @WithDefault("https://localhost:57003")
    String url();

    /**
     * Keycloak realm name.
     * Can be overridden with KC_REALM environment variable.
     */
    @WithDefault("master")
    String realm();

    /**
     * OAuth2 client ID for client credentials flow.
     * Can be overridden with KC_CLIENT_ID environment variable.
     */
    @WithDefault("admin-cli")
    String clientId();

    /**
     * OAuth2 client secret for client credentials flow.
     * Can be overridden with KC_CLIENT_SECRET environment variable.
     */
    Optional<String> clientSecret();

    /**
     * Admin username (alternative to client credentials).
     * Can be overridden with KC_ADMIN_USERNAME environment variable.
     */
    Optional<String> adminUsername();

    /**
     * Admin password (alternative to client credentials).
     * Can be overridden with KC_ADMIN_PASSWORD environment variable.
     */
    Optional<String> adminPassword();

    /**
     * Connection timeout in milliseconds.
     */
    @WithDefault("10000")
    int connectionTimeoutMs();

    /**
     * Read timeout in milliseconds.
     */
    @WithDefault("30000")
    int readTimeoutMs();
}
