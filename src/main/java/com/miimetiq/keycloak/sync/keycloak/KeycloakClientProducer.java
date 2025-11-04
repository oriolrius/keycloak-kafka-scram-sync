package com.miimetiq.keycloak.sync.keycloak;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * Produces Keycloak Admin client bean configured from application properties and environment variables.
 * Supports both OAuth2 client credentials flow and username/password authentication.
 */
@ApplicationScoped
public class KeycloakClientProducer {

    private static final Logger LOG = Logger.getLogger(KeycloakClientProducer.class);

    @Inject
    KeycloakConfig config;

    @Produces
    @ApplicationScoped
    public Keycloak produceKeycloakClient() {
        LOG.info("Creating Keycloak Admin client with configuration");

        try {
            // Create a trust manager that accepts all certificates (for dev/testing with self-signed certs)
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create HTTP client with SSL context and timeouts
            ResteasyClient resteasyClient = ((ResteasyClientBuilder) ResteasyClientBuilder.newBuilder())
                    .sslContext(sslContext)
                    .hostnameVerifier((hostname, session) -> true) // Disable hostname verification for dev/testing
                    .connectionCheckoutTimeout(config.connectionTimeoutMs(), TimeUnit.MILLISECONDS)
                    .connectTimeout(config.connectionTimeoutMs(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.readTimeoutMs(), TimeUnit.MILLISECONDS)
                    .build();

            KeycloakBuilder builder = KeycloakBuilder.builder()
                    .serverUrl(config.url())
                    .realm(config.realm())
                    .clientId(config.clientId())
                    .resteasyClient(resteasyClient);

            // Determine authentication method
            if (config.clientSecret().isPresent()) {
                // Use OAuth2 client credentials flow
                LOG.info("Using OAuth2 client credentials flow");
                builder.grantType("client_credentials")
                        .clientSecret(config.clientSecret().get());
            } else if (config.adminUsername().isPresent() && config.adminPassword().isPresent()) {
                // Use username/password authentication
                LOG.info("Using username/password authentication");
                builder.grantType("password")
                        .username(config.adminUsername().get())
                        .password(config.adminPassword().get());
            } else {
                throw new IllegalStateException(
                        "Keycloak authentication not configured. " +
                        "Either set client-secret (for client credentials) or admin-username/admin-password (for password grant)."
                );
            }

            Keycloak keycloak = builder.build();

            // Verify connectivity by getting a token
            try {
                String token = keycloak.tokenManager().getAccessTokenString();
                LOG.info("Keycloak Admin client created successfully and authenticated");
                LOG.info("Server URL: " + config.url());
                LOG.info("Realm: " + config.realm());
                LOG.debug("Token obtained, length: " + (token != null ? token.length() : 0));
            } catch (Exception e) {
                LOG.error("Failed to authenticate with Keycloak", e);
                throw new RuntimeException("Failed to authenticate with Keycloak: " + e.getMessage(), e);
            }

            return keycloak;
        } catch (Exception e) {
            LOG.error("Failed to create Keycloak Admin client", e);
            throw new RuntimeException("Failed to create Keycloak Admin client: " + e.getMessage(), e);
        }
    }

    public void closeKeycloakClient(@Disposes Keycloak keycloak) {
        if (keycloak != null) {
            LOG.info("Closing Keycloak Admin client");
            try {
                keycloak.close();
                LOG.info("Keycloak Admin client closed successfully");
            } catch (Exception e) {
                LOG.error("Error closing Keycloak Admin client", e);
            }
        }
    }
}
