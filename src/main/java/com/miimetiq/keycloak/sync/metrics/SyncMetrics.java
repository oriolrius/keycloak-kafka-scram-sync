package com.miimetiq.keycloak.sync.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

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

    // Counters for sync operations
    private Counter usersSyncedCounter;
    private Counter syncErrorsCounter;
    private Counter kafkaPublishCounter;
    private Counter keycloakEventCounter;

    // Timers for operation duration
    private Timer syncOperationTimer;
    private Timer kafkaPublishTimer;

    // Gauges for current state
    private final AtomicLong lastSyncTimestamp = new AtomicLong(0);
    private final AtomicLong activeSync = new AtomicLong(0);

    /**
     * Initialize metrics on startup.
     */
    public void init() {
        // Counters
        usersSyncedCounter = Counter.builder("sync.users.total")
                .description("Total number of users synced from Keycloak to Kafka")
                .register(registry);

        syncErrorsCounter = Counter.builder("sync.errors.total")
                .description("Total number of sync errors")
                .register(registry);

        kafkaPublishCounter = Counter.builder("sync.kafka.published.total")
                .description("Total number of events published to Kafka")
                .register(registry);

        keycloakEventCounter = Counter.builder("sync.keycloak.events.total")
                .description("Total number of events received from Keycloak")
                .register(registry);

        // Timers
        syncOperationTimer = Timer.builder("sync.operation.duration")
                .description("Duration of sync operations")
                .register(registry);

        kafkaPublishTimer = Timer.builder("sync.kafka.publish.duration")
                .description("Duration of Kafka publish operations")
                .register(registry);

        // Gauges
        registry.gauge("sync.last.timestamp", lastSyncTimestamp);
        registry.gauge("sync.active.operations", activeSync);

        LOG.info("Sync metrics initialized");
    }

    /**
     * Increment users synced counter.
     */
    public void incrementUsersSynced() {
        usersSyncedCounter.increment();
    }

    /**
     * Increment users synced counter by a specific amount.
     */
    public void incrementUsersSynced(long count) {
        usersSyncedCounter.increment(count);
    }

    /**
     * Increment sync errors counter.
     */
    public void incrementSyncErrors() {
        syncErrorsCounter.increment();
    }

    /**
     * Increment Kafka published events counter.
     */
    public void incrementKafkaPublished() {
        kafkaPublishCounter.increment();
    }

    /**
     * Increment Keycloak events counter.
     */
    public void incrementKeycloakEvents() {
        keycloakEventCounter.increment();
    }

    /**
     * Get sync operation timer for recording duration.
     */
    public Timer getSyncOperationTimer() {
        return syncOperationTimer;
    }

    /**
     * Get Kafka publish timer for recording duration.
     */
    public Timer getKafkaPublishTimer() {
        return kafkaPublishTimer;
    }

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
