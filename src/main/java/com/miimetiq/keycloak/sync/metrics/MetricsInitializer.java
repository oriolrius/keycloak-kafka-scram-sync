package com.miimetiq.keycloak.sync.metrics;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.repository.RetentionRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Initializes custom metrics on application startup.
 */
@ApplicationScoped
public class MetricsInitializer {

    private static final Logger LOG = Logger.getLogger(MetricsInitializer.class);

    @Inject
    SyncMetrics syncMetrics;

    @Inject
    RetentionRepository retentionRepository;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Initializing custom metrics...");
        syncMetrics.init();

        // Load initial retention config values
        try {
            RetentionState retentionState = retentionRepository.getOrThrow();
            syncMetrics.updateRetentionConfig(retentionState.getMaxBytes(), retentionState.getMaxAgeDays());
            LOG.debug("Loaded initial retention config for metrics");
        } catch (Exception e) {
            LOG.warnf(e, "Failed to load initial retention config for metrics: %s", e.getMessage());
        }

        LOG.info("Custom metrics initialized successfully");
    }
}
