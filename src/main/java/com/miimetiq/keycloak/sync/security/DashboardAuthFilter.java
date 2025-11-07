package com.miimetiq.keycloak.sync.security;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * Authentication filter for dashboard API endpoints.
 * Supports both Basic Authentication and Keycloak OIDC with role-based access control.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class DashboardAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(DashboardAuthFilter.class);

    @ConfigProperty(name = "dashboard.basic-auth")
    Optional<String> basicAuth;

    @ConfigProperty(name = "dashboard.oidc-enabled", defaultValue = "false")
    boolean oidcEnabled;

    @ConfigProperty(name = "dashboard.oidc-required-role", defaultValue = "dashboard-admin")
    String oidcRequiredRole;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        // Only apply authentication to /api/* endpoints (dashboard APIs)
        // Exclude health endpoints
        if (!path.startsWith("api/") || path.startsWith("health")) {
            return;
        }

        // If no auth configured, allow all requests (backward compatibility)
        if (basicAuth.isEmpty() && !oidcEnabled) {
            LOG.trace("Dashboard authentication is disabled (no DASHBOARD_BASIC_AUTH configured)");
            return;
        }

        // Check for Authorization header
        String authHeader = requestContext.getHeaderString("Authorization");

        // Handle OIDC authentication
        if (oidcEnabled) {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // OIDC token present - validate it
                if (!validateOidcToken()) {
                    LOG.debugf("Invalid OIDC token for path: %s", path);
                    abortWithUnauthorized(requestContext);
                    return;
                }

                // Check role-based access control
                if (!validateOidcRole()) {
                    LOG.debugf("User does not have required role '%s' for path: %s",
                              oidcRequiredRole, path);
                    abortWithForbidden(requestContext);
                    return;
                }

                LOG.tracef("OIDC authentication successful for path: %s", path);
                return;
            }

            // If OIDC is enabled but no Bearer token, try Basic Auth as fallback
            if (basicAuth.isEmpty()) {
                LOG.debugf("Missing Bearer token and no Basic Auth configured for path: %s", path);
                abortWithUnauthorized(requestContext);
                return;
            }
        }

        // Handle Basic Auth (either as primary or fallback)
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            LOG.debugf("Missing or invalid Authorization header for path: %s", path);
            abortWithUnauthorized(requestContext);
            return;
        }

        // Validate Basic Auth credentials
        if (!validateBasicAuth(authHeader)) {
            LOG.debugf("Invalid credentials for path: %s", path);
            abortWithUnauthorized(requestContext);
            return;
        }

        // Authentication successful, proceed with request
        LOG.tracef("Basic authentication successful for path: %s", path);
    }

    /**
     * Validate Basic Authentication credentials.
     *
     * @param authHeader Authorization header value
     * @return true if credentials are valid
     */
    private boolean validateBasicAuth(String authHeader) {
        if (basicAuth.isEmpty()) {
            return false;
        }

        try {
            // Extract credentials from header
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);

            // Compare with configured credentials
            String expectedCredentials = basicAuth.get();

            return credentials.equals(expectedCredentials);

        } catch (IllegalArgumentException e) {
            LOG.debugf("Failed to decode Authorization header: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Validate OIDC token from SecurityIdentity.
     *
     * @return true if token is valid and authenticated
     */
    private boolean validateOidcToken() {
        try {
            // Check if user is authenticated via OIDC
            if (securityIdentity == null || securityIdentity.isAnonymous()) {
                LOG.debug("SecurityIdentity is null or anonymous");
                return false;
            }

            // Verify JWT token is present
            if (jwt == null || jwt.getName() == null) {
                LOG.debug("JWT token is null or has no name claim");
                return false;
            }

            LOG.tracef("OIDC token validated for user: %s", jwt.getName());
            return true;

        } catch (Exception e) {
            LOG.debugf("OIDC token validation failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Validate that the user has the required role for dashboard access.
     *
     * @return true if user has the required role
     */
    private boolean validateOidcRole() {
        try {
            // Check if user has the required role
            boolean hasRole = securityIdentity.hasRole(oidcRequiredRole);

            if (hasRole) {
                LOG.tracef("User '%s' has required role '%s'", jwt.getName(), oidcRequiredRole);
            } else {
                LOG.debugf("User '%s' missing required role '%s'. User roles: %s",
                          jwt.getName(), oidcRequiredRole, securityIdentity.getRoles());
            }

            return hasRole;

        } catch (Exception e) {
            LOG.debugf("Role validation failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Abort the request with 401 Unauthorized response.
     *
     * @param requestContext request context
     */
    private void abortWithUnauthorized(ContainerRequestContext requestContext) {
        String authHeader = oidcEnabled ?
            "Bearer realm=\"Dashboard\"" : "Basic realm=\"Dashboard\"";

        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", authHeader)
                        .entity("{\"error\": \"Authentication required\"}")
                        .build()
        );
    }

    /**
     * Abort the request with 403 Forbidden response.
     *
     * @param requestContext request context
     */
    private void abortWithForbidden(ContainerRequestContext requestContext) {
        requestContext.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\": \"Insufficient permissions. Required role: " +
                               oidcRequiredRole + "\"}")
                        .build()
        );
    }
}
