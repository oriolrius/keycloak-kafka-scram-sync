package com.miimetiq.keycloak.sync.reconcile;

import com.miimetiq.keycloak.sync.domain.KeycloakUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine for computing synchronization differences between Keycloak users and Kafka SCRAM principals.
 * <p>
 * This service implements the core reconciliation logic that determines which operations
 * need to be performed to bring Kafka in sync with Keycloak. It compares the current
 * state of both systems and generates a {@link SyncPlan} containing the required changes.
 * <p>
 * Diff logic:
 * - Upsert: User exists in Keycloak but not in Kafka, or credentials may be stale
 * - Delete: Principal exists in Kafka but not in Keycloak (orphaned account)
 * <p>
 * Features:
 * - Configurable exclusion patterns for system accounts
 * - Optional "always upsert" mode to refresh all credentials
 * - Dry-run mode for validation without execution
 * - Performance optimized for large user bases (10,000+ users)
 */
@ApplicationScoped
public class SyncDiffEngine {

    private static final Logger LOG = Logger.getLogger(SyncDiffEngine.class);

    // Default exclusion patterns for system accounts that should never be synced
    private static final Set<String> DEFAULT_EXCLUSIONS = Set.of(
            "admin",
            "kafka",
            "zookeeper",
            "system"
    );

    @ConfigProperty(name = "reconcile.excluded-principals", defaultValue = "")
    Optional<String> excludedPrincipalsConfig;

    @ConfigProperty(name = "reconcile.always-upsert", defaultValue = "true")
    boolean alwaysUpsert;

    private Set<String> excludedPrincipals;

    @Inject
    void init() {
        // Combine default exclusions with configured exclusions
        excludedPrincipals = new HashSet<>(DEFAULT_EXCLUSIONS);

        if (excludedPrincipalsConfig.isPresent() && !excludedPrincipalsConfig.get().isBlank()) {
            String[] configured = excludedPrincipalsConfig.get().split(",");
            for (String principal : configured) {
                String trimmed = principal.trim();
                if (!trimmed.isEmpty()) {
                    excludedPrincipals.add(trimmed);
                }
            }
        }

        LOG.infof("SyncDiffEngine initialized: excludedPrincipals=%s, alwaysUpsert=%s",
                excludedPrincipals, alwaysUpsert);
    }

    /**
     * Computes the synchronization diff between Keycloak users and Kafka principals.
     * <p>
     * This method compares the two states and determines which operations need to be
     * performed to bring Kafka in sync with Keycloak. It filters out excluded principals
     * and generates a complete sync plan.
     *
     * @param keycloakUsers list of users from Keycloak (source of truth)
     * @param kafkaPrincipals set of SCRAM principal names in Kafka (current state)
     * @param dryRun        whether this is a dry-run (validation only)
     * @return SyncPlan containing upserts and deletes
     */
    public SyncPlan computeDiff(List<KeycloakUserInfo> keycloakUsers,
                                Set<String> kafkaPrincipals,
                                boolean dryRun) {

        LOG.infof("Computing sync diff: keycloakUsers=%d, kafkaPrincipals=%d, dryRun=%s",
                keycloakUsers.size(), kafkaPrincipals.size(), dryRun);

        long startTime = System.currentTimeMillis();

        // Step 1: Build username set from Keycloak users for fast lookups
        Set<String> keycloakUsernames = keycloakUsers.stream()
                .map(KeycloakUserInfo::getUsername)
                .collect(Collectors.toSet());

        LOG.debugf("Built Keycloak username set: %d users", keycloakUsernames.size());

        // Step 2: Filter Kafka principals to exclude system accounts
        Set<String> filteredKafkaPrincipals = kafkaPrincipals.stream()
                .filter(this::shouldIncludePrincipal)
                .collect(Collectors.toSet());

        int filteredCount = kafkaPrincipals.size() - filteredKafkaPrincipals.size();
        if (filteredCount > 0) {
            LOG.debugf("Filtered out %d excluded principals from Kafka", filteredCount);
        }

        // Step 3: Compute upserts
        // Upsert strategy depends on alwaysUpsert configuration:
        // - If alwaysUpsert=true: all Keycloak users (refreshes credentials for everyone)
        // - If alwaysUpsert=false: only users not in Kafka (new users only)
        List<KeycloakUserInfo> upserts;

        if (alwaysUpsert) {
            // Upsert all Keycloak users (refresh all credentials)
            upserts = new ArrayList<>(keycloakUsers);
            LOG.debugf("Upsert mode: ALWAYS_UPSERT - including all %d Keycloak users", upserts.size());
        } else {
            // Upsert only users that don't exist in Kafka (new users)
            upserts = keycloakUsers.stream()
                    .filter(user -> !filteredKafkaPrincipals.contains(user.getUsername()))
                    .collect(Collectors.toList());
            LOG.debugf("Upsert mode: NEW_ONLY - found %d new users not in Kafka", upserts.size());
        }

        // Step 4: Compute deletes
        // Deletes are principals in Kafka that don't exist in Keycloak (orphaned accounts)
        List<String> deletes = filteredKafkaPrincipals.stream()
                .filter(principal -> !keycloakUsernames.contains(principal))
                .sorted() // Sort for deterministic order
                .collect(Collectors.toList());

        LOG.debugf("Computed deletes: %d orphaned principals in Kafka", deletes.size());

        // Step 5: Build sync plan
        SyncPlan plan = new SyncPlan(upserts, deletes, dryRun);

        long durationMs = System.currentTimeMillis() - startTime;
        LOG.infof("Computed sync diff in %dms: %s", durationMs, plan.getSummary());

        // Log details if there are operations
        if (!plan.isEmpty()) {
            LOG.infof("Sync plan details: %d upsert(s), %d delete(s)",
                    plan.getUpsertCount(), plan.getDeleteCount());

            if (LOG.isDebugEnabled() && plan.getDeleteCount() > 0) {
                LOG.debugf("Principals to delete: %s", deletes);
            }
        }

        return plan;
    }

    /**
     * Computes a diff with default dry-run mode (false).
     *
     * @param keycloakUsers list of users from Keycloak
     * @param kafkaPrincipals set of SCRAM principal names in Kafka
     * @return SyncPlan containing upserts and deletes
     */
    public SyncPlan computeDiff(List<KeycloakUserInfo> keycloakUsers, Set<String> kafkaPrincipals) {
        return computeDiff(keycloakUsers, kafkaPrincipals, false);
    }

    /**
     * Checks if a principal should be included in sync operations.
     * <p>
     * Filters out excluded principals (admin accounts, system accounts, etc.)
     * based on the configured exclusion list.
     *
     * @param principal the principal name to check
     * @return true if the principal should be included, false if excluded
     */
    private boolean shouldIncludePrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return false;
        }

        // Check exact matches
        if (excludedPrincipals.contains(principal)) {
            LOG.tracef("Excluding principal (exact match): %s", principal);
            return false;
        }

        // Check prefix matches (e.g., "admin" excludes "admin-user")
        for (String excluded : excludedPrincipals) {
            if (principal.startsWith(excluded + "-")) {
                LOG.tracef("Excluding principal (prefix match): %s", principal);
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the current set of excluded principals.
     * <p>
     * This includes both default exclusions and configured exclusions.
     *
     * @return immutable set of excluded principal names
     */
    public Set<String> getExcludedPrincipals() {
        return Collections.unmodifiableSet(excludedPrincipals);
    }

    /**
     * Checks if the engine is configured to always upsert all users.
     *
     * @return true if alwaysUpsert mode is enabled, false otherwise
     */
    public boolean isAlwaysUpsert() {
        return alwaysUpsert;
    }
}
