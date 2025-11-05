package com.miimetiq.keycloak.sync.webhook;

import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing the webhook event processing queue.
 * <p>
 * Provides a bounded in-memory queue that decouples webhook ingestion
 * from event processing. Supports configurable capacity and overflow behavior.
 */
@ApplicationScoped
public class EventQueueService {

    private static final Logger LOG = Logger.getLogger(EventQueueService.class);

    @ConfigProperty(name = "webhook.queue.capacity", defaultValue = "1000")
    int queueCapacity;

    @ConfigProperty(name = "webhook.queue.overflow-strategy", defaultValue = "REJECT")
    String overflowStrategy;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    SyncMetrics metrics;

    private BlockingQueue<WebhookEvent> queue;
    private final AtomicInteger droppedEvents = new AtomicInteger(0);

    /**
     * Initialize the queue and metrics on startup.
     */
    @PostConstruct
    public void init() {
        // Initialize queue with configured capacity
        this.queue = new LinkedBlockingQueue<>(queueCapacity);

        // Register queue backlog gauge
        Gauge.builder("sync_queue_backlog", queue, BlockingQueue::size)
                .description("Number of webhook events waiting in the processing queue")
                .register(meterRegistry);

        // Register dropped events counter
        Gauge.builder("sync_queue_dropped_total", droppedEvents, AtomicInteger::get)
                .description("Total number of events dropped due to queue overflow")
                .register(meterRegistry);

        LOG.infof("Event queue initialized: capacity=%d, overflow-strategy=%s",
                queueCapacity, overflowStrategy);
    }

    /**
     * Enqueue a webhook event for processing.
     * <p>
     * Behavior depends on overflow strategy:
     * - REJECT: Reject new events when queue is full (returns false)
     * - DROP_OLDEST: Remove oldest event and add new one (always returns true)
     *
     * @param event the webhook event to enqueue
     * @return true if event was enqueued, false if rejected
     */
    public boolean enqueue(WebhookEvent event) {
        if (event == null) {
            LOG.warn("Attempted to enqueue null event");
            return false;
        }

        boolean enqueued;

        if ("DROP_OLDEST".equalsIgnoreCase(overflowStrategy)) {
            // Try to offer, if full, remove oldest and try again
            enqueued = queue.offer(event);
            if (!enqueued) {
                WebhookEvent dropped = queue.poll();
                if (dropped != null) {
                    droppedEvents.incrementAndGet();
                    LOG.warnf("[%s] Queue full, dropped oldest event [%s] to make room",
                            event.getCorrelationId(), dropped.getCorrelationId());
                }
                enqueued = queue.offer(event);
            }
        } else {
            // REJECT strategy: fail if queue is full
            enqueued = queue.offer(event);
            if (!enqueued) {
                LOG.warnf("[%s] Queue full (capacity=%d), rejecting event",
                        event.getCorrelationId(), queueCapacity);
            }
        }

        if (enqueued) {
            LOG.debugf("[%s] Event enqueued, queue size: %d",
                    event.getCorrelationId(), queue.size());
        }

        return enqueued;
    }

    /**
     * Poll an event from the queue with timeout.
     * <p>
     * Blocks until an event is available or timeout expires.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return the event, or empty if timeout expires
     */
    public Optional<WebhookEvent> poll(long timeout, TimeUnit unit) {
        try {
            WebhookEvent event = queue.poll(timeout, unit);
            return Optional.ofNullable(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while polling queue", e);
            return Optional.empty();
        }
    }

    /**
     * Get current queue size.
     *
     * @return number of events in queue
     */
    public int size() {
        return queue.size();
    }

    /**
     * Get queue capacity.
     *
     * @return maximum queue capacity
     */
    public int capacity() {
        return queueCapacity;
    }

    /**
     * Get overflow strategy.
     *
     * @return configured overflow strategy (REJECT or DROP_OLDEST)
     */
    public String getOverflowStrategy() {
        return overflowStrategy;
    }

    /**
     * Get total number of dropped events.
     *
     * @return count of events dropped due to overflow
     */
    public int getDroppedCount() {
        return droppedEvents.get();
    }

    /**
     * Clear all events from the queue (for testing).
     */
    public void clear() {
        queue.clear();
        LOG.debug("Queue cleared");
    }
}
