package com.miimetiq.keycloak.sync.service;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import com.miimetiq.keycloak.sync.repository.RetentionRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing retention policies and purging old sync operation data.
 * <p>
 * This service handles time-based (TTL) and space-based retention strategies
 * to prevent unbounded database growth. It reads retention configuration from
 * the retention_state singleton table and executes purge operations accordingly.
 * <p>
 * Key responsibilities:
 * - Executing TTL-based purges (delete operations older than max_age_days)
 * - Executing space-based purges (delete oldest operations when size exceeds max_bytes)
 * - Updating retention_state metadata after purge operations
 * - Providing purge statistics and status information
 */
@ApplicationScoped
public class RetentionService {

    private static final Logger LOG = Logger.getLogger(RetentionService.class);

    @Inject
    RetentionRepository retentionRepository;

    @Inject
    SyncOperationRepository operationRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    SyncMetrics syncMetrics;

    /**
     * Executes a time-based purge of sync operations older than the configured TTL.
     * <p>
     * This method reads the max_age_days configuration from retention_state,
     * calculates the cutoff date (now - max_age_days), and deletes all
     * sync_operation records with occurred_at before the cutoff.
     * <p>
     * The operation is transactional and updates retention_state.updated_at
     * upon completion.
     * <p>
     * Edge cases handled:
     * - If max_age_days is null, no purge is performed (returns 0)
     * - If no records match the criteria, the operation completes successfully (returns 0)
     * - If all records are older than the cutoff, all are deleted
     *
     * @return the number of records deleted
     */
    @Transactional
    public long purgeTtl() {
        // Read retention state
        RetentionState retentionState = retentionRepository.getOrThrow();

        // Check if TTL is configured
        if (!retentionState.hasMaxAgeLimit()) {
            LOG.debug("TTL purge skipped: max_age_days not configured");
            return 0;
        }

        Integer maxAgeDays = retentionState.getMaxAgeDays();

        // Calculate cutoff date
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(maxAgeDays);

        LOG.infof("Starting TTL purge: max_age_days=%d, cutoff_date=%s", maxAgeDays, cutoffDate);

        // Delete old records
        long deletedCount = operationRepository.delete("occurredAt < ?1", cutoffDate);

        // Update retention state timestamp
        retentionState.setUpdatedAt(LocalDateTime.now());
        retentionRepository.persist(retentionState);

        // Update database size metric
        syncMetrics.updateDatabaseSize();

        LOG.infof("TTL purge completed: deleted_count=%d", deletedCount);

        return deletedCount;
    }

    /**
     * Retrieves the current retention state.
     *
     * @return the retention state singleton
     */
    public RetentionState getRetentionState() {
        return retentionRepository.getOrThrow();
    }

    /**
     * Updates the retention configuration.
     * This method can be used to modify max_age_days and max_bytes settings.
     *
     * @param maxBytes the maximum database size in bytes (null to disable)
     * @param maxAgeDays the maximum age in days (null to disable)
     */
    @Transactional
    public void updateRetentionConfig(Long maxBytes, Integer maxAgeDays) {
        RetentionState retentionState = retentionRepository.getOrThrow();

        retentionState.setMaxBytes(maxBytes);
        retentionState.setMaxAgeDays(maxAgeDays);
        retentionState.setUpdatedAt(LocalDateTime.now());

        retentionRepository.persist(retentionState);

        // Update retention config metrics
        syncMetrics.updateRetentionConfig(maxBytes, maxAgeDays);

        LOG.infof("Retention config updated: max_bytes=%d, max_age_days=%d", maxBytes, maxAgeDays);
    }

    /**
     * Calculates the current database size using SQLite PRAGMA commands.
     * <p>
     * Uses PRAGMA page_count and PRAGMA page_size to calculate total database size.
     * Formula: db_bytes = page_count * page_size
     *
     * @return the current database size in bytes
     */
    public long calculateDatabaseSize() {
        // Get page count
        Integer pageCount = (Integer) entityManager
                .createNativeQuery("PRAGMA page_count")
                .getSingleResult();

        // Get page size
        Integer pageSize = (Integer) entityManager
                .createNativeQuery("PRAGMA page_size")
                .getSingleResult();

        long dbBytes = (long) pageCount * pageSize;

        LOG.debugf("Database size calculated: page_count=%d, page_size=%d, db_bytes=%d",
                (Object) pageCount, (Object) pageSize, (Object) dbBytes);

        return dbBytes;
    }

    /**
     * Executes a size-based purge of sync operations when database exceeds max_bytes.
     * <p>
     * This method calculates the current database size, and if it exceeds max_bytes,
     * deletes the oldest sync_operation records (by occurred_at) in batches until
     * the database is under the limit.
     * <p>
     * The operation is transactional and updates retention_state.approx_db_bytes
     * and retention_state.updated_at upon completion.
     * <p>
     * Edge cases handled:
     * - If max_bytes is null, no purge is performed (returns 0)
     * - If database is already under limit, no purge is performed (returns 0)
     * - Deletes in batches of 100 records to avoid long-running transactions
     *
     * @return the number of records deleted
     */
    @Transactional
    public long purgeBySize() {
        // Read retention state
        RetentionState retentionState = retentionRepository.getOrThrow();

        // Check if size limit is configured
        if (!retentionState.hasMaxBytesLimit()) {
            LOG.debug("Size-based purge skipped: max_bytes not configured");
            return 0;
        }

        Long maxBytes = retentionState.getMaxBytes();
        long currentSize = calculateDatabaseSize();

        LOG.infof("Starting size-based purge: max_bytes=%d, current_bytes=%d", maxBytes, currentSize);

        // Check if purge is needed
        if (currentSize <= maxBytes) {
            LOG.debug("Database size is within limit, no purge needed");

            // Update approx_db_bytes even if no purge needed
            retentionState.setApproxDbBytes(currentSize);
            retentionState.setUpdatedAt(LocalDateTime.now());
            retentionRepository.persist(retentionState);

            return 0;
        }

        // Delete oldest records in batches until under limit
        long totalDeleted = 0;
        int batchSize = 100;

        while (currentSize > maxBytes) {
            // Find oldest records
            @SuppressWarnings("unchecked")
            List<Long> oldestIds = entityManager
                    .createQuery("SELECT s.id FROM SyncOperation s ORDER BY s.occurredAt ASC")
                    .setMaxResults(batchSize)
                    .getResultList();

            if (oldestIds.isEmpty()) {
                LOG.warn("No more records to delete, but database still exceeds limit");
                break;
            }

            // Delete batch
            long deleted = operationRepository.delete("id IN ?1", oldestIds);
            totalDeleted += deleted;

            // Recalculate size
            currentSize = calculateDatabaseSize();

            LOG.debugf("Deleted batch: count=%d, new_size=%d", deleted, currentSize);
        }

        // Update retention state with new size
        retentionState.setApproxDbBytes(currentSize);
        retentionState.setUpdatedAt(LocalDateTime.now());
        retentionRepository.persist(retentionState);

        // Update database size metric
        syncMetrics.updateDatabaseSize();

        LOG.infof("Size-based purge completed: deleted_count=%d, final_size=%d", totalDeleted, currentSize);

        return totalDeleted;
    }

    /**
     * Executes SQLite VACUUM to reclaim disk space after deletions.
     * <p>
     * VACUUM rebuilds the database file, repacking it into a minimal amount of disk space.
     * This should be called after large purge operations to actually free up disk space.
     * <p>
     * Note: VACUUM cannot run inside a transaction in SQLite. This method attempts to execute
     * VACUUM but may fail in transactional contexts. In production, this should be called
     * outside of transaction boundaries or in a separate connection.
     *
     * @return true if VACUUM succeeded, false if it failed (e.g., due to transaction constraints)
     */
    public boolean executeVacuum() {
        LOG.info("Executing VACUUM to reclaim disk space...");

        try {
            entityManager.createNativeQuery("VACUUM").executeUpdate();
            LOG.info("VACUUM completed successfully");
            return true;
        } catch (Exception e) {
            LOG.warnf("VACUUM failed (may be due to transaction context): %s", e.getMessage());
            return false;
        }
    }
}
