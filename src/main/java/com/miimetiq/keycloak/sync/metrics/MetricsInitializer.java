package com.miimetiq.keycloak.sync.metrics;

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

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Initializing custom metrics...");
        syncMetrics.init();
        LOG.info("Custom metrics initialized successfully");
    }
}
