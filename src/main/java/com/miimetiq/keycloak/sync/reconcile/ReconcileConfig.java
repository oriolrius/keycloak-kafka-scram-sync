package com.miimetiq.keycloak.sync.reconcile;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Reconciliation configuration properties.
 * Reads from application.properties and environment variables.
 */
@ConfigMapping(prefix = "reconcile")
public interface ReconcileConfig {

    /**
     * Reconciliation interval in seconds.
     * How often to fetch all Keycloak users and sync with Kafka.
     * Can be overridden with RECONCILE_INTERVAL_SECONDS environment variable.
     */
    @WithDefault("120")
    int intervalSeconds();

    /**
     * Page size for fetching users from Keycloak.
     * Limits the number of users fetched per API call.
     * Can be overridden with RECONCILE_PAGE_SIZE environment variable.
     */
    @WithDefault("500")
    int pageSize();

    /**
     * Enable or disable scheduled reconciliation.
     * When disabled, reconciliation can only be triggered manually via REST endpoint.
     * Can be overridden with RECONCILE_SCHEDULER_ENABLED environment variable.
     *
     * Note: With direct Kafka SPI, passwords are synced immediately on changes.
     * Scheduled reconciliation is now disabled by default and serves as a manual safety net only.
     */
    @WithDefault("false")
    boolean schedulerEnabled();
}
