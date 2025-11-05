package com.miimetiq.keycloak.sync.service;

import com.miimetiq.keycloak.sync.domain.entity.SyncBatch;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.repository.SyncBatchRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for persisting and managing synchronization batches and operations.
 * <p>
 * This service provides a high-level API for creating, updating, and querying
 * synchronization audit data. It orchestrates operations across both SyncBatch
 * and SyncOperation repositories, ensuring consistent state management.
 * <p>
 * Key responsibilities:
 * - Creating and managing sync batches (reconciliation cycles)
 * - Recording individual sync operations within batches
 * - Updating batch statistics (success/error counts)
 * - Providing query methods for audit trail analysis
 */
@ApplicationScoped
public class SyncPersistenceService {

    private static final Logger LOG = Logger.getLogger(SyncPersistenceService.class);

    @Inject
    SyncBatchRepository batchRepository;

    @Inject
    SyncOperationRepository operationRepository;

    /**
     * Creates a new sync batch to track a reconciliation cycle.
     * <p>
     * Generates a unique correlation ID and persists the batch record.
     * The batch is created in an "in-progress" state (finishedAt = null).
     *
     * @param source     the source triggering this batch (SCHEDULED, MANUAL, WEBHOOK)
     * @param itemsTotal total number of items to be processed in this batch
     * @return the correlation ID for this batch
     */
    @Transactional
    public String createBatch(String source, int itemsTotal) {
        String correlationId = generateCorrelationId();
        LocalDateTime startedAt = LocalDateTime.now();

        SyncBatch batch = new SyncBatch(correlationId, startedAt, source, itemsTotal);
        batchRepository.persist(batch);

        LOG.infof("Created sync batch: correlationId=%s, source=%s, itemsTotal=%d",
                correlationId, source, itemsTotal);

        return correlationId;
    }

    /**
     * Records a single sync operation within a batch.
     * <p>
     * This method persists an operation record and does NOT update the batch counters.
     * Use {@link #recordOperationAndUpdateBatch} if you want automatic batch counter updates.
     *
     * @param operation the sync operation to record
     */
    @Transactional
    public void recordOperation(SyncOperation operation) {
        operationRepository.persist(operation);

        LOG.debugf("Recorded sync operation: correlationId=%s, principal=%s, result=%s",
                operation.getCorrelationId(), operation.getPrincipal(), operation.getResult());
    }

    /**
     * Records a sync operation and automatically updates the parent batch counters.
     * <p>
     * This method is a convenience method that combines recording an operation
     * with updating the batch's success/error counters based on the operation result.
     *
     * @param operation the sync operation to record
     */
    @Transactional
    public void recordOperationAndUpdateBatch(SyncOperation operation) {
        // Persist the operation
        operationRepository.persist(operation);

        // Find and update the batch
        Optional<SyncBatch> batchOpt = batchRepository.findByCorrelationId(operation.getCorrelationId());
        if (batchOpt.isPresent()) {
            SyncBatch batch = batchOpt.get();

            switch (operation.getResult()) {
                case SUCCESS -> batch.incrementSuccess();
                case ERROR, SKIPPED -> batch.incrementError();
            }

            batchRepository.persist(batch);

            LOG.debugf("Recorded operation and updated batch: correlationId=%s, principal=%s, result=%s",
                    operation.getCorrelationId(), operation.getPrincipal(), operation.getResult());
        } else {
            LOG.warnf("Batch not found for correlationId=%s when recording operation", operation.getCorrelationId());
        }
    }

    /**
     * Records multiple sync operations in a batch transaction.
     * <p>
     * This is more efficient than calling {@link #recordOperation} multiple times
     * as it uses a single transaction for all operations.
     *
     * @param operations the list of operations to record
     */
    @Transactional
    public void recordOperations(List<SyncOperation> operations) {
        for (SyncOperation operation : operations) {
            operationRepository.persist(operation);
        }

        LOG.infof("Recorded %d sync operations", operations.size());
    }

    /**
     * Completes a sync batch by setting the finish timestamp and final counts.
     * <p>
     * Updates the batch with the finished_at timestamp and ensures the success/error
     * counts are accurate. The counts can be provided explicitly or calculated from
     * the existing batch state.
     *
     * @param correlationId the correlation ID of the batch to complete
     * @param itemsSuccess  final count of successful operations
     * @param itemsError    final count of failed operations
     * @return true if the batch was found and updated, false otherwise
     */
    @Transactional
    public boolean completeBatch(String correlationId, int itemsSuccess, int itemsError) {
        Optional<SyncBatch> batchOpt = batchRepository.findByCorrelationId(correlationId);

        if (batchOpt.isEmpty()) {
            LOG.warnf("Cannot complete batch: correlationId=%s not found", correlationId);
            return false;
        }

        SyncBatch batch = batchOpt.get();
        batch.setFinishedAt(LocalDateTime.now());
        batch.setItemsSuccess(itemsSuccess);
        batch.setItemsError(itemsError);

        batchRepository.persist(batch);

        LOG.infof("Completed sync batch: correlationId=%s, success=%d, errors=%d, duration=%dms",
                correlationId, itemsSuccess, itemsError,
                java.time.Duration.between(batch.getStartedAt(), batch.getFinishedAt()).toMillis());

        return true;
    }

    /**
     * Completes a sync batch using the current counter values.
     * <p>
     * This is a convenience method that completes the batch without explicitly
     * providing counts. It uses the current itemsSuccess and itemsError values
     * from the batch entity.
     *
     * @param correlationId the correlation ID of the batch to complete
     * @return true if the batch was found and updated, false otherwise
     */
    @Transactional
    public boolean completeBatch(String correlationId) {
        Optional<SyncBatch> batchOpt = batchRepository.findByCorrelationId(correlationId);

        if (batchOpt.isEmpty()) {
            LOG.warnf("Cannot complete batch: correlationId=%s not found", correlationId);
            return false;
        }

        SyncBatch batch = batchOpt.get();
        batch.setFinishedAt(LocalDateTime.now());
        batchRepository.persist(batch);

        LOG.infof("Completed sync batch: correlationId=%s, success=%d, errors=%d",
                correlationId, batch.getItemsSuccess(), batch.getItemsError());

        return true;
    }

    /**
     * Retrieves a batch by its correlation ID.
     *
     * @param correlationId the correlation ID to search for
     * @return optional containing the batch if found
     */
    public Optional<SyncBatch> getBatch(String correlationId) {
        return batchRepository.findByCorrelationId(correlationId);
    }

    /**
     * Retrieves all operations for a given batch.
     *
     * @param correlationId the correlation ID of the batch
     * @return list of operations belonging to the batch
     */
    public List<SyncOperation> getOperations(String correlationId) {
        return operationRepository.findByCorrelationId(correlationId);
    }

    /**
     * Retrieves the most recent N batches.
     *
     * @param limit the maximum number of batches to return
     * @return list of recent batches, ordered by start time descending
     */
    public List<SyncBatch> getRecentBatches(int limit) {
        return batchRepository.findRecent(limit);
    }

    /**
     * Retrieves batches within a time range.
     *
     * @param start start of the time range (inclusive)
     * @param end   end of the time range (inclusive)
     * @return list of batches within the time range
     */
    public List<SyncBatch> getBatchesByTimeRange(LocalDateTime start, LocalDateTime end) {
        return batchRepository.findByTimeRange(start, end);
    }

    /**
     * Retrieves operations for a specific principal.
     *
     * @param principal the principal name to search for
     * @return list of operations for the given principal
     */
    public List<SyncOperation> getOperationsByPrincipal(String principal) {
        return operationRepository.findByPrincipal(principal);
    }

    /**
     * Generates a unique correlation ID for a new batch.
     *
     * @return UUID-based correlation ID
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
