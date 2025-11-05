package com.miimetiq.keycloak.sync.repository;

import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing SyncOperation entities.
 * <p>
 * Provides data access methods for querying and persisting synchronization operations.
 * Uses Quarkus Panache for simplified repository implementation.
 */
@ApplicationScoped
public class SyncOperationRepository implements PanacheRepository<SyncOperation> {

    /**
     * Finds all operations by correlation ID.
     *
     * @param correlationId the correlation ID to search for
     * @return list of operations with the given correlation ID
     */
    public List<SyncOperation> findByCorrelationId(String correlationId) {
        return list("correlationId", correlationId);
    }

    /**
     * Finds operations within a time range.
     *
     * @param start start of the time range (inclusive)
     * @param end   end of the time range (inclusive)
     * @return list of operations within the time range
     */
    public List<SyncOperation> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        return list("occurredAt >= ?1 and occurredAt <= ?2", start, end);
    }

    /**
     * Finds operations by principal name.
     *
     * @param principal the principal name to search for
     * @return list of operations for the given principal
     */
    public List<SyncOperation> findByPrincipal(String principal) {
        return list("principal", principal);
    }

    /**
     * Finds operations by operation type.
     *
     * @param opType the operation type to search for
     * @return list of operations of the given type
     */
    public List<SyncOperation> findByOpType(OpType opType) {
        return list("opType", opType);
    }

    /**
     * Finds operations by result status.
     *
     * @param result the operation result to search for
     * @return list of operations with the given result
     */
    public List<SyncOperation> findByResult(OperationResult result) {
        return list("result", result);
    }

    /**
     * Finds operations by principal and time range.
     *
     * @param principal the principal name to search for
     * @param start     start of the time range (inclusive)
     * @param end       end of the time range (inclusive)
     * @return list of operations matching the criteria
     */
    public List<SyncOperation> findByPrincipalAndTimeRange(String principal, LocalDateTime start, LocalDateTime end) {
        return list("principal = ?1 and occurredAt >= ?2 and occurredAt <= ?3", principal, start, end);
    }

    /**
     * Finds operations by type, result, and time range.
     *
     * @param opType the operation type
     * @param result the operation result
     * @param start  start of the time range (inclusive)
     * @param end    end of the time range (inclusive)
     * @return list of operations matching the criteria
     */
    public List<SyncOperation> findByTypeResultAndTimeRange(
            OpType opType, OperationResult result, LocalDateTime start, LocalDateTime end) {
        return list("opType = ?1 and result = ?2 and occurredAt >= ?3 and occurredAt <= ?4",
                opType, result, start, end);
    }

    /**
     * Counts operations by correlation ID and result.
     *
     * @param correlationId the correlation ID
     * @param result        the operation result
     * @return count of operations matching the criteria
     */
    public long countByCorrelationIdAndResult(String correlationId, OperationResult result) {
        return count("correlationId = ?1 and result = ?2", correlationId, result);
    }
}
