package com.miimetiq.keycloak.sync.keycloak;

import com.miimetiq.keycloak.sync.domain.KeycloakUserInfo;
import com.miimetiq.keycloak.sync.reconcile.ReconcileConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for fetching user information from Keycloak with pagination support.
 * <p>
 * This service retrieves all users from the configured Keycloak realm and converts them
 * into {@link KeycloakUserInfo} objects for use in reconciliation operations.
 * <p>
 * Features:
 * - Pagination with configurable page size
 * - Automatic retry with exponential backoff for transient failures
 * - Filtering of service accounts and technical users
 * - Comprehensive logging of fetch operations
 */
@ApplicationScoped
public class KeycloakUserFetcher {

    private static final Logger LOG = Logger.getLogger(KeycloakUserFetcher.class);

    // Service account patterns to filter out
    private static final String[] SERVICE_ACCOUNT_PREFIXES = {
        "service-account-",
        "system-",
        "admin-"
    };

    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakConfig keycloakConfig;

    @Inject
    ReconcileConfig reconcileConfig;

    /**
     * Fetches all users from Keycloak with pagination.
     * <p>
     * This method retrieves all users from the configured realm, filtering out service accounts
     * and technical users based on username patterns. It uses pagination to handle large user bases
     * efficiently and implements retry logic for transient failures.
     *
     * @return list of all users as KeycloakUserInfo objects
     * @throws KeycloakFetchException if fetching fails after all retries
     */
    public List<KeycloakUserInfo> fetchAllUsers() {
        String realm = keycloakConfig.realm();
        int pageSize = reconcileConfig.pageSize();

        LOG.infof("Starting user fetch from Keycloak realm '%s' with page size %d", realm, pageSize);

        return retryWithBackoff(() -> {
            try {
                RealmResource realmResource = keycloak.realm(realm);
                UsersResource usersResource = realmResource.users();

                List<KeycloakUserInfo> allUsers = new ArrayList<>();
                int offset = 0;
                int totalFetched = 0;

                while (true) {
                    LOG.debugf("Fetching users: offset=%d, pageSize=%d", offset, pageSize);

                    // Fetch page of users
                    List<UserRepresentation> usersPage = usersResource.list(offset, pageSize);

                    if (usersPage == null || usersPage.isEmpty()) {
                        LOG.debugf("No more users to fetch at offset %d", offset);
                        break;
                    }

                    LOG.debugf("Fetched %d users in current page", usersPage.size());

                    // Convert and filter users
                    List<KeycloakUserInfo> convertedUsers = usersPage.stream()
                            .map(this::convertToUserInfo)
                            .filter(this::shouldIncludeUser)
                            .collect(Collectors.toList());

                    int filtered = usersPage.size() - convertedUsers.size();
                    if (filtered > 0) {
                        LOG.debugf("Filtered out %d service accounts/technical users", filtered);
                    }

                    allUsers.addAll(convertedUsers);
                    totalFetched += usersPage.size();

                    // If we got fewer users than page size, we've reached the end
                    if (usersPage.size() < pageSize) {
                        LOG.debugf("Reached end of user list (page size %d < %d)", usersPage.size(), pageSize);
                        break;
                    }

                    offset += pageSize;
                }

                LOG.infof("Successfully fetched %d users from Keycloak realm '%s' (total processed: %d, filtered: %d)",
                        allUsers.size(), realm, totalFetched, totalFetched - allUsers.size());

                return allUsers;

            } catch (Exception e) {
                LOG.errorf(e, "Error fetching users from Keycloak realm '%s'", realm);
                throw new KeycloakFetchException("Failed to fetch users from Keycloak: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Converts a Keycloak UserRepresentation to our domain model.
     *
     * @param user the Keycloak user representation
     * @return the converted KeycloakUserInfo object
     */
    private KeycloakUserInfo convertToUserInfo(UserRepresentation user) {
        return new KeycloakUserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled() != null ? user.isEnabled() : true,
                user.getCreatedTimestamp()
        );
    }

    /**
     * Determines if a user should be included in the results.
     * <p>
     * Filters out service accounts and technical users based on username patterns.
     *
     * @param userInfo the user to check
     * @return true if the user should be included, false otherwise
     */
    private boolean shouldIncludeUser(KeycloakUserInfo userInfo) {
        String username = userInfo.getUsername();

        // Filter out service accounts based on username prefixes
        for (String prefix : SERVICE_ACCOUNT_PREFIXES) {
            if (username.startsWith(prefix)) {
                LOG.tracef("Filtering out service account: %s", username);
                return false;
            }
        }

        // Only include enabled users
        if (!userInfo.isEnabled()) {
            LOG.tracef("Filtering out disabled user: %s", username);
            return false;
        }

        return true;
    }

    /**
     * Executes an operation with retry and exponential backoff.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the result of the operation
     * @throws KeycloakFetchException if all retries fail
     */
    private <T> T retryWithBackoff(ThrowingSupplier<T> operation) {
        Exception lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                if (attempt < MAX_RETRIES) {
                    LOG.warnf("Attempt %d/%d failed, retrying in %d ms: %s",
                            attempt, MAX_RETRIES, backoffMs, e.getMessage());

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new KeycloakFetchException("Retry interrupted", ie);
                    }

                    backoffMs = (long) (backoffMs * BACKOFF_MULTIPLIER);
                } else {
                    LOG.errorf(e, "All %d attempts failed", MAX_RETRIES);
                }
            }
        }

        throw new KeycloakFetchException(
                "Failed after " + MAX_RETRIES + " attempts: " + lastException.getMessage(),
                lastException
        );
    }

    /**
     * Functional interface for operations that may throw exceptions.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Exception thrown when fetching users from Keycloak fails.
     */
    public static class KeycloakFetchException extends RuntimeException {
        public KeycloakFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
