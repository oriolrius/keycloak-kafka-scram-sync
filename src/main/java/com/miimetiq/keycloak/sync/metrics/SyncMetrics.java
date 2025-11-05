package com.miimetiq.keycloak.sync.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom metrics for Keycloak-Kafka sync operations.
 * Provides counters, timers, and gauges for tracking sync performance and health.
 */
@ApplicationScoped
public class SyncMetrics {

    private static final Logger LOG = Logger.getLogger(SyncMetrics.class);

    @Inject
    MeterRegistry registry;

    // Gauges for current state
    private final AtomicLong lastSyncTimestamp = new AtomicLong(0);
    private final AtomicLong activeSync = new AtomicLong(0);
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0);
    private final AtomicLong dbSizeBytes = new AtomicLong(0);

    // Retention configuration gauges
    private final AtomicLong retentionMaxBytes = new AtomicLong(0);
    private final AtomicLong retentionMaxAgeDays = new AtomicLong(0);

    // Database path for size tracking
    private volatile String databasePath = "sync-agent.db";

    /**
     * Initialize metrics on startup.
     */
    public void init() {
        // Gauges for last successful reconciliation and database size
        registry.gauge("sync_last_success_epoch_seconds", lastSuccessEpochSeconds);
        registry.gauge("sync_db_size_bytes", dbSizeBytes, AtomicLong::get);

        // Retention configuration gauges
        registry.gauge("sync_retention_max_bytes", retentionMaxBytes, AtomicLong::get);
        registry.gauge("sync_retention_max_age_days", retentionMaxAgeDays, AtomicLong::get);

        // Legacy gauges (kept for backward compatibility)
        registry.gauge("sync.last.timestamp", lastSyncTimestamp);
        registry.gauge("sync.active.operations", activeSync);

        LOG.info("Sync metrics initialized");
    }

    // ========== Keycloak Fetch Metrics ==========

    /**
     * Increment counter for Keycloak user fetches.
     *
     * @param realm the Keycloak realm
     * @param source the reconciliation source (SCHEDULED, MANUAL, WEBHOOK)
     */
    public void incrementKeycloakFetch(String realm, String source) {
        Counter.builder("sync_kc_fetch_total")
                .description("Total number of Keycloak user fetches")
                .tag("realm", realm)
                .tag("source", source)
                .register(registry)
                .increment();
    }

    // ========== Kafka SCRAM Operation Metrics ==========

    /**
     * Increment counter for Kafka SCRAM upsert operations.
     *
     * @param clusterId the Kafka cluster ID (bootstrap servers)
     * @param mechanism the SCRAM mechanism (SCRAM-SHA-256, SCRAM-SHA-512)
     * @param result the operation result (SUCCESS, ERROR)
     */
    public void incrementKafkaScramUpsert(String clusterId, String mechanism, String result) {
        Counter.builder("sync_kafka_scram_upserts_total")
                .description("Total number of Kafka SCRAM credential upserts")
                .tag("cluster_id", clusterId)
                .tag("mechanism", mechanism)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Increment counter for Kafka SCRAM delete operations.
     *
     * @param clusterId the Kafka cluster ID (bootstrap servers)
     * @param result the operation result (SUCCESS, ERROR)
     */
    public void incrementKafkaScramDelete(String clusterId, String result) {
        Counter.builder("sync_kafka_scram_deletes_total")
                .description("Total number of Kafka SCRAM credential deletes")
                .tag("cluster_id", clusterId)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    // ========== Timers ==========

    /**
     * Record reconciliation duration.
     *
     * @param realm the Keycloak realm
     * @param clusterId the Kafka cluster ID
     * @param source the reconciliation source
     * @return Timer.Sample to stop timing later
     */
    public Timer.Sample startReconciliationTimer() {
        return Timer.start(registry);
    }

    /**
     * Stop and record reconciliation duration.
     *
     * @param sample the timer sample from startReconciliationTimer()
     * @param realm the Keycloak realm
     * @param clusterId the Kafka cluster ID
     * @param source the reconciliation source
     */
    public void recordReconciliationDuration(Timer.Sample sample, String realm, String clusterId, String source) {
        sample.stop(Timer.builder("sync_reconcile_duration_seconds")
                .description("Duration of reconciliation operations")
                .tag("realm", realm)
                .tag("cluster_id", clusterId)
                .tag("source", source)
                .register(registry));
    }

    /**
     * Record admin operation duration.
     *
     * @param sample the timer sample
     * @param op the operation name (upsert, delete, describe)
     */
    public void recordAdminOpDuration(Timer.Sample sample, String op) {
        sample.stop(Timer.builder("sync_admin_op_duration_seconds")
                .description("Duration of Kafka admin operations")
                .tag("op", op)
                .register(registry));
    }

    /**
     * Start a timer for admin operations.
     */
    public Timer.Sample startAdminOpTimer() {
        return Timer.start(registry);
    }

    // ========== Gauges ==========

    /**
     * Update last successful reconciliation timestamp.
     */
    public void updateLastSuccessEpoch() {
        lastSuccessEpochSeconds.set(System.currentTimeMillis() / 1000);
        LOG.debug("Updated last success epoch seconds");
    }

    /**
     * Update database size in bytes.
     */
    public void updateDatabaseSize() {
        try {
            File dbFile = new File(databasePath);
            if (dbFile.exists()) {
                long size = dbFile.length();
                dbSizeBytes.set(size);
                LOG.debugf("Updated database size: %d bytes", size);
            } else {
                LOG.tracef("Database file not found: %s", databasePath);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to update database size metric");
        }
    }

    /**
     * Set the database path for size tracking.
     *
     * @param path the database file path
     */
    public void setDatabasePath(String path) {
        this.databasePath = path;
    }

    // ========== Webhook Event Retry Metrics ==========

    /**
     * Increment counter for webhook event retry attempts.
     *
     * @param result the retry result (SUCCESS, ERROR, MAX_RETRIES_EXCEEDED)
     */
    public void incrementRetryAttempts(String result) {
        Counter.builder("sync_retry_total")
                .description("Total number of webhook event retry attempts")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    // ========== Retention Metrics ==========

    /**
     * Increment counter for purge operations.
     *
     * @param reason the purge reason (scheduled, post-batch)
     */
    public void incrementPurgeRuns(String reason) {
        Counter.builder("sync_purge_runs_total")
                .description("Total number of retention purge operations")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Start a timer for purge operations.
     *
     * @return Timer.Sample to stop timing later
     */
    public Timer.Sample startPurgeTimer() {
        return Timer.start(registry);
    }

    /**
     * Stop and record purge operation duration.
     *
     * @param sample the timer sample from startPurgeTimer()
     */
    public void recordPurgeDuration(Timer.Sample sample) {
        sample.stop(Timer.builder("sync_purge_duration_seconds")
                .description("Duration of retention purge operations")
                .register(registry));
    }

    /**
     * Update retention configuration gauges.
     *
     * @param maxBytes the max bytes limit (null means no limit)
     * @param maxAgeDays the max age in days (null means no limit)
     */
    public void updateRetentionConfig(Long maxBytes, Integer maxAgeDays) {
        retentionMaxBytes.set(maxBytes != null ? maxBytes : 0);
        retentionMaxAgeDays.set(maxAgeDays != null ? maxAgeDays : 0);
        LOG.debugf("Updated retention config metrics: maxBytes=%d, maxAgeDays=%d",
                retentionMaxBytes.get(), retentionMaxAgeDays.get());
    }

    // ========== Legacy Methods (Backward Compatibility) ==========

    /**
     * Update last sync timestamp.
     */
    public void updateLastSyncTimestamp() {
        lastSyncTimestamp.set(System.currentTimeMillis());
    }

    /**
     * Increment active sync operations.
     */
    public void incrementActiveSyncOperations() {
        activeSync.incrementAndGet();
    }

    /**
     * Decrement active sync operations.
     */
    public void decrementActiveSyncOperations() {
        activeSync.decrementAndGet();
    }

    /**
     * Get current count of active sync operations.
     */
    public long getActiveSyncOperations() {
        return activeSync.get();
    }
}
