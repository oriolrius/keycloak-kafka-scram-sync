package com.miimetiq.keycloak.sync.repository;

import com.miimetiq.keycloak.sync.domain.entity.SyncBatch;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing SyncBatch entities.
 * <p>
 * Provides data access methods for querying and persisting synchronization batches.
 * Uses Quarkus Panache for simplified repository implementation.
 */
@ApplicationScoped
public class SyncBatchRepository implements PanacheRepository<SyncBatch> {

    /**
     * Finds a batch by its correlation ID.
     *
     * @param correlationId the correlation ID to search for
     * @return optional containing the batch if found
     */
    public Optional<SyncBatch> findByCorrelationId(String correlationId) {
        return find("correlationId", correlationId).firstResultOptional();
    }

    /**
     * Finds batches within a time range, ordered by start time descending.
     *
     * @param start start of the time range (inclusive)
     * @param end   end of the time range (inclusive)
     * @return list of batches within the time range
     */
    public List<SyncBatch> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        return list("startedAt >= ?1 and startedAt <= ?2", Sort.by("startedAt").descending(), start, end);
    }

    /**
     * Finds batches by source, ordered by start time descending.
     *
     * @param source the source to search for (e.g., SCHEDULED, MANUAL, WEBHOOK)
     * @return list of batches from the given source
     */
    public List<SyncBatch> findBySource(String source) {
        return list("source", Sort.by("startedAt").descending(), source);
    }

    /**
     * Finds batches that are not yet complete (finishedAt is null).
     *
     * @return list of incomplete batches
     */
    public List<SyncBatch> findIncomplete() {
        return list("finishedAt is null", Sort.by("startedAt").descending());
    }

    /**
     * Finds batches that are complete (finishedAt is not null).
     *
     * @return list of complete batches
     */
    public List<SyncBatch> findComplete() {
        return list("finishedAt is not null", Sort.by("startedAt").descending());
    }

    /**
     * Finds all batches with pagination, ordered by start time descending.
     *
     * @param page the page to retrieve (0-indexed)
     * @param size the page size
     * @return list of batches for the given page
     */
    public List<SyncBatch> findAllPaged(int page, int size) {
        return findAll(Sort.by("startedAt").descending()).page(Page.of(page, size)).list();
    }

    /**
     * Finds the most recent N batches, ordered by start time descending.
     *
     * @param limit the maximum number of batches to return
     * @return list of recent batches
     */
    public List<SyncBatch> findRecent(int limit) {
        return findAll(Sort.by("startedAt").descending()).page(Page.ofSize(limit)).list();
    }

    /**
     * Finds batches by source and time range with pagination.
     *
     * @param source the source to filter by
     * @param start  start of the time range (inclusive)
     * @param end    end of the time range (inclusive)
     * @param page   the page to retrieve (0-indexed)
     * @param size   the page size
     * @return list of batches matching the criteria
     */
    public List<SyncBatch> findBySourceAndTimeRangePaged(
            String source, LocalDateTime start, LocalDateTime end, int page, int size) {
        return find("source = ?1 and startedAt >= ?2 and startedAt <= ?3",
                Sort.by("startedAt").descending(), source, start, end)
                .page(Page.of(page, size))
                .list();
    }

    /**
     * Counts total batches by source.
     *
     * @param source the source to count
     * @return count of batches from the given source
     */
    public long countBySource(String source) {
        return count("source", source);
    }

    /**
     * Counts batches with errors (itemsError > 0).
     *
     * @return count of batches with errors
     */
    public long countWithErrors() {
        return count("itemsError > 0");
    }

    /**
     * Deletes batches older than a given date.
     * <p>
     * This is useful for implementing retention policies.
     *
     * @param before the cutoff date (batches started before this date will be deleted)
     * @return number of batches deleted
     */
    public long deleteOlderThan(LocalDateTime before) {
        return delete("startedAt < ?1", before);
    }
}
