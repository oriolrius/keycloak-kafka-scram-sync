package com.miimetiq.keycloak.sync.health;

import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;

/**
 * Health check for Keycloak Admin client connectivity.
 * Validates that the client can authenticate and interact with the Keycloak API.
 */
@Readiness
@ApplicationScoped
public class KeycloakHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(KeycloakHealthCheck.class);

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakConfig config;

    @Override
    public HealthCheckResponse call() {
        try {
            // Fetch realm information to validate connectivity and authentication
            RealmRepresentation realm = keycloak.realm(config.realm()).toRepresentation();

            if (realm != null && realm.getRealm() != null) {
                LOG.debug("Keycloak health check passed - realm info retrieved successfully");
                return HealthCheckResponse
                        .named("keycloak-admin-client")
                        .up()
                        .withData("url", config.url())
                        .withData("realm", realm.getRealm())
                        .withData("realm_enabled", realm.isEnabled())
                        .withData("client_id", config.clientId())
                        .build();
            } else {
                LOG.warn("Keycloak health check failed - realm info is null");
                return HealthCheckResponse
                        .named("keycloak-admin-client")
                        .down()
                        .withData("url", config.url())
                        .withData("realm", config.realm())
                        .withData("error", "Realm information is null")
                        .build();
            }
        } catch (Exception e) {
            LOG.error("Keycloak health check failed - unable to connect or authenticate", e);
            return HealthCheckResponse
                    .named("keycloak-admin-client")
                    .down()
                    .withData("url", config.url())
                    .withData("realm", config.realm())
                    .withData("client_id", config.clientId())
                    .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
