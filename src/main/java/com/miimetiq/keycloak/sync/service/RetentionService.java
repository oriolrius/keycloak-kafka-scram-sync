package com.miimetiq.keycloak.sync.service;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.repository.RetentionRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

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

        LOG.infof("Retention config updated: max_bytes=%d, max_age_days=%d", maxBytes, maxAgeDays);
    }
}
