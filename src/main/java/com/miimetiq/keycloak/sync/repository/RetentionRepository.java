package com.miimetiq.keycloak.sync.repository;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Repository for managing RetentionState entities.
 * <p>
 * Provides data access methods for the retention_state singleton table.
 * This table contains exactly one row (id=1) with retention configuration.
 * Uses Quarkus Panache for simplified repository implementation.
 */
@ApplicationScoped
public class RetentionRepository implements PanacheRepository<RetentionState> {

    /**
     * Finds the singleton retention state row.
     * The retention_state table should only have one row with id=1.
     *
     * @return Optional containing the retention state if found, empty otherwise
     */
    public Optional<RetentionState> findSingleton() {
        return find("id", RetentionState.SINGLETON_ID).firstResultOptional();
    }

    /**
     * Gets or creates the singleton retention state.
     * If the retention state doesn't exist, this is a critical error
     * as it should be created by the database migration.
     *
     * @return the retention state
     * @throws IllegalStateException if the retention state doesn't exist
     */
    public RetentionState getOrThrow() {
        return findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Retention state not found. Database migration may have failed."));
    }
}
