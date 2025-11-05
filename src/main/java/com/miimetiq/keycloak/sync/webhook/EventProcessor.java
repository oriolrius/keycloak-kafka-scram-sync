package com.miimetiq.keycloak.sync.webhook;

import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous processor for webhook events.
 * <p>
 * Runs worker threads that continuously poll the event queue and process
 * events asynchronously. Supports graceful shutdown and configurable worker count.
 */
@ApplicationScoped
public class EventProcessor {

    private static final Logger LOG = Logger.getLogger(EventProcessor.class);

    @ConfigProperty(name = "webhook.queue.worker-threads", defaultValue = "2")
    int workerThreadCount;

    @Inject
    EventQueueService queueService;

    @Inject
    RetryPolicy retryPolicy;

    @Inject
    SyncMetrics metrics;

    @Inject
    EventMapper eventMapper;

    private ExecutorService executorService;
    private ScheduledExecutorService retryExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Worker> workers = new ArrayList<>();

    /**
     * Start worker threads on application startup.
     */
    void onStart(@Observes StartupEvent event) {
        LOG.infof("Starting EventProcessor with %d worker threads", workerThreadCount);

        executorService = Executors.newFixedThreadPool(workerThreadCount);
        retryExecutor = Executors.newScheduledThreadPool(1);
        running.set(true);

        // Start worker threads
        for (int i = 0; i < workerThreadCount; i++) {
            Worker worker = new Worker(i);
            workers.add(worker);
            executorService.submit(worker);
        }

        LOG.info("EventProcessor started successfully");
    }

    /**
     * Stop worker threads on application shutdown.
     */
    void onShutdown(@Observes ShutdownEvent event) {
        LOG.info("Shutting down EventProcessor");
        running.set(false);

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.warn("EventProcessor did not terminate in time, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.error("Interrupted during shutdown", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (retryExecutor != null) {
            retryExecutor.shutdown();
            try {
                if (!retryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.warn("Retry executor did not terminate in time, forcing shutdown");
                    retryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.error("Interrupted during retry executor shutdown", e);
                retryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        LOG.info("EventProcessor stopped");
    }

    /**
     * Worker thread that polls and processes events.
     */
    private class Worker implements Runnable {
        private final int workerId;

        Worker(int workerId) {
            this.workerId = workerId;
        }

        @Override
        public void run() {
            LOG.infof("Worker %d started", workerId);

            while (running.get()) {
                try {
                    // Poll queue with timeout to allow periodic checks of running flag
                    Optional<WebhookEvent> eventOpt = queueService.poll(1, TimeUnit.SECONDS);

                    if (eventOpt.isPresent()) {
                        WebhookEvent webhookEvent = eventOpt.get();
                        processEvent(webhookEvent);
                    }

                } catch (Exception e) {
                    LOG.errorf(e, "Worker %d encountered error: %s", workerId, e.getMessage());
                    // Continue running despite errors
                }
            }

            LOG.infof("Worker %d stopped", workerId);
        }

        /**
         * Process a webhook event with retry logic.
         * <p>
         * Maps the Keycloak admin event to a sync operation and performs
         * the appropriate action (UPSERT or DELETE credentials).
         *
         * @param webhookEvent the event to process
         */
        private void processEvent(WebhookEvent webhookEvent) {
            String correlationId = webhookEvent.getCorrelationId();
            KeycloakAdminEvent event = webhookEvent.getEvent();
            int retryCount = webhookEvent.getRetryCount();

            try {
                LOG.infof("[%s] Worker %d processing event (attempt %d/%d): resourceType=%s, operationType=%s",
                        correlationId, workerId, retryCount + 1, retryPolicy.getMaxAttempts(),
                        event.getResourceType(), event.getOperationType());

                // Map Keycloak event to sync operation
                Optional<SyncOperation> syncOpOpt = eventMapper.mapEvent(event);

                if (syncOpOpt.isEmpty()) {
                    LOG.infof("[%s] Event ignored (unsupported or unmappable): resourceType=%s, operationType=%s, resourcePath=%s",
                            correlationId, event.getResourceType(), event.getOperationType(), event.getResourcePath());
                    return;
                }

                SyncOperation syncOp = syncOpOpt.get();
                LOG.infof("[%s] Mapped to sync operation: type=%s, realm=%s, principal=%s, passwordChange=%s",
                        correlationId, syncOp.getType(), syncOp.getRealm(), syncOp.getPrincipal(), syncOp.isPasswordChange());

                // TODO: Execute sync operation (integrate with existing sync logic)
                // For now, just log the operation
                // This will be integrated with KafkaScramManager and ReconciliationService
                // in a future task to avoid duplication with scheduled reconciliation

                LOG.infof("[%s] Worker %d completed processing successfully: %s", correlationId, workerId, syncOp);

                // Record successful retry if this was a retry attempt
                if (retryCount > 0) {
                    metrics.incrementRetryAttempts("SUCCESS");
                }

            } catch (Exception e) {
                LOG.errorf(e, "[%s] Worker %d failed to process event (attempt %d/%d): %s",
                        correlationId, workerId, retryCount + 1, retryPolicy.getMaxAttempts(), e.getMessage());
                handleFailure(webhookEvent, e);
            }
        }

        /**
         * Handle event processing failure with retry logic.
         *
         * @param webhookEvent the failed event
         * @param error the error that occurred
         */
        private void handleFailure(WebhookEvent webhookEvent, Exception error) {
            String correlationId = webhookEvent.getCorrelationId();
            int retryCount = webhookEvent.getRetryCount();

            if (retryPolicy.shouldRetry(retryCount)) {
                // Increment retry count
                webhookEvent.incrementRetryCount();
                int newRetryCount = webhookEvent.getRetryCount();

                // Calculate backoff delay
                long delayMs = retryPolicy.calculateBackoffDelay(newRetryCount);

                LOG.warnf("[%s] Scheduling retry attempt %d/%d after %dms delay",
                        correlationId, newRetryCount + 1, retryPolicy.getMaxAttempts(), delayMs);

                // Schedule retry with exponential backoff
                retryExecutor.schedule(
                        () -> {
                            try {
                                queueService.enqueue(webhookEvent);
                                metrics.incrementRetryAttempts("SCHEDULED");
                            } catch (Exception e) {
                                LOG.errorf(e, "[%s] Failed to re-enqueue event for retry", correlationId);
                                metrics.incrementRetryAttempts("ERROR");
                            }
                        },
                        delayMs,
                        TimeUnit.MILLISECONDS
                );
            } else {
                // Max retries exceeded - log permanent failure
                LOG.errorf(error, "[%s] Event processing failed permanently after %d attempts: %s",
                        correlationId, retryCount + 1, error.getMessage());
                metrics.incrementRetryAttempts("MAX_RETRIES_EXCEEDED");

                // TODO: Store failed event for later analysis/manual intervention
            }
        }
    }

    /**
     * Check if processor is running.
     *
     * @return true if workers are active
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get worker thread count.
     *
     * @return number of worker threads
     */
    public int getWorkerCount() {
        return workerThreadCount;
    }
}
