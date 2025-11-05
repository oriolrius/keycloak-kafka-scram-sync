package com.miimetiq.keycloak.sync.retention;

import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import com.miimetiq.keycloak.sync.service.RetentionService;
import io.micrometer.core.instrument.Timer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler for periodic retention purge operations.
 * <p>
 * This service triggers retention purge at a configured interval to prevent
 * unbounded database growth. It executes both TTL-based and space-based purges
 * and implements overlap prevention to avoid concurrent purge operations.
 * <p>
 * Configuration:
 * - retention.purge-interval-seconds: interval between purge cycles (default: 300s)
 */
@ApplicationScoped
public class RetentionScheduler {

    private static final Logger LOG = Logger.getLogger(RetentionScheduler.class);

    @Inject
    RetentionService retentionService;

    @Inject
    RetentionConfig retentionConfig;

    @Inject
    SyncMetrics syncMetrics;

    // Flag to prevent overlapping executions
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Scheduled retention purge job.
     * <p>
     * Runs every retention.purge-interval-seconds (default: 300s / 5 minutes).
     * Skips execution if a previous purge is still running.
     * <p>
     * Executes both TTL-based and space-based purge operations, then runs VACUUM
     * to reclaim disk space.
     */
    @Scheduled(
            every = "${retention.purge-interval-seconds}s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "retention-purge-scheduler"
    )
    void scheduledPurge() {
        // Check if already running (double-check even with SKIP policy)
        if (!isRunning.compareAndSet(false, true)) {
            LOG.warn("Skipping scheduled purge - previous execution still running");
            return;
        }

        try {
            LOG.info("Starting scheduled retention purge");
            long startTime = System.currentTimeMillis();

            // Execute purge operations with reason="scheduled"
            PurgeResult result = executePurge("scheduled");

            long duration = System.currentTimeMillis() - startTime;
            LOG.infof("Scheduled retention purge completed: ttl_deleted=%d, size_deleted=%d, duration=%dms",
                    result.ttlDeleted, result.sizeDeleted, duration);

        } catch (Exception e) {
            LOG.errorf(e, "Scheduled retention purge failed: %s", e.getMessage());
            // Don't rethrow - we want the scheduler to continue running
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Execute retention purge operations (TTL + size-based).
     * <p>
     * This method can be called by the scheduler or triggered after sync batch completion.
     * It executes both purge strategies and runs VACUUM to reclaim disk space.
     *
     * @param reason the purge reason (scheduled, post-batch)
     * @return purge result with deletion counts
     */
    public PurgeResult executePurge(String reason) {
        // Start timer for purge duration
        Timer.Sample timerSample = syncMetrics.startPurgeTimer();

        // Increment purge counter
        syncMetrics.incrementPurgeRuns(reason);

        long ttlDeleted = 0;
        long sizeDeleted = 0;

        try {
            // Execute TTL-based purge
            LOG.debug("Executing TTL-based purge...");
            ttlDeleted = retentionService.purgeTtl();
            LOG.debugf("TTL purge deleted %d record(s)", ttlDeleted);

        } catch (Exception e) {
            LOG.errorf(e, "TTL purge failed: %s", e.getMessage());
            // Continue with size-based purge even if TTL purge fails
        }

        try {
            // Execute space-based purge
            LOG.debug("Executing space-based purge...");
            sizeDeleted = retentionService.purgeBySize();
            LOG.debugf("Size-based purge deleted %d record(s)", sizeDeleted);

        } catch (Exception e) {
            LOG.errorf(e, "Space-based purge failed: %s", e.getMessage());
            // Continue with VACUUM even if size purge fails
        }

        // Run VACUUM if any records were deleted
        if (ttlDeleted > 0 || sizeDeleted > 0) {
            try {
                LOG.debug("Running VACUUM to reclaim disk space...");
                boolean vacuumSuccess = retentionService.executeVacuum();
                if (vacuumSuccess) {
                    LOG.debug("VACUUM completed successfully");
                } else {
                    LOG.warn("VACUUM failed (may be due to transaction context)");
                }
            } catch (Exception e) {
                LOG.warnf(e, "VACUUM failed: %s", e.getMessage());
                // Not fatal - continue
            }
        }

        // Record purge duration
        syncMetrics.recordPurgeDuration(timerSample);

        return new PurgeResult(ttlDeleted, sizeDeleted);
    }

    /**
     * Trigger purge after sync batch completion.
     * <p>
     * This method is called after each sync batch completes to maintain database
     * size limits. It runs asynchronously to not block the sync process.
     */
    public void triggerPostSyncPurge() {
        // Check if already running - skip if purge is in progress
        if (isRunning.get()) {
            LOG.debug("Skipping post-sync purge - scheduled purge already running");
            return;
        }

        try {
            LOG.debug("Triggering post-sync retention purge");
            PurgeResult result = executePurge("post-batch");

            if (result.ttlDeleted > 0 || result.sizeDeleted > 0) {
                LOG.infof("Post-sync purge completed: ttl_deleted=%d, size_deleted=%d",
                        result.ttlDeleted, result.sizeDeleted);
            } else {
                LOG.debug("Post-sync purge completed: no records deleted");
            }

        } catch (Exception e) {
            LOG.errorf(e, "Post-sync purge failed: %s", e.getMessage());
            // Don't rethrow - purge failure should not fail the sync
        }
    }

    /**
     * Check if a purge operation is currently running.
     *
     * @return true if purge is in progress
     */
    public boolean isPurgeRunning() {
        return isRunning.get();
    }

    /**
     * Result of a purge operation.
     */
    public static class PurgeResult {
        public final long ttlDeleted;
        public final long sizeDeleted;

        public PurgeResult(long ttlDeleted, long sizeDeleted) {
            this.ttlDeleted = ttlDeleted;
            this.sizeDeleted = sizeDeleted;
        }
    }
}
