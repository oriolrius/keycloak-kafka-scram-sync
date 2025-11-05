package com.miimetiq.keycloak.sync.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SyncMetrics custom Prometheus metrics.
 * <p>
 * Verifies that retention metrics are properly registered and updated.
 */
@QuarkusTest
@DisplayName("SyncMetrics Tests")
class SyncMetricsTest {

    @Inject
    SyncMetrics syncMetrics;

    @Inject
    MeterRegistry registry;

    @BeforeEach
    void setUp() {
        // Ensure metrics are initialized
        syncMetrics.init();
    }

    @Test
    @DisplayName("Retention config gauges should be registered")
    void testRetentionConfigGaugesRegistered() {
        // When: checking for gauge registration
        Gauge maxBytesGauge = registry.find("sync_retention_max_bytes").gauge();
        Gauge maxAgeDaysGauge = registry.find("sync_retention_max_age_days").gauge();

        // Then: gauges should exist
        assertNotNull(maxBytesGauge, "sync_retention_max_bytes gauge should be registered");
        assertNotNull(maxAgeDaysGauge, "sync_retention_max_age_days gauge should be registered");
    }

    @Test
    @DisplayName("Database size gauge should be registered")
    void testDatabaseSizeGaugeRegistered() {
        // When: checking for gauge registration
        Gauge dbSizeGauge = registry.find("sync_db_size_bytes").gauge();

        // Then: gauge should exist
        assertNotNull(dbSizeGauge, "sync_db_size_bytes gauge should be registered");
    }

    @Test
    @DisplayName("updateRetentionConfig should update gauge values")
    void testUpdateRetentionConfig() {
        // Given: new retention config values
        Long maxBytes = 268435456L; // 256 MB
        Integer maxAgeDays = 30;

        // When: updating retention config
        syncMetrics.updateRetentionConfig(maxBytes, maxAgeDays);

        // Then: gauges should report new values
        Gauge maxBytesGauge = registry.find("sync_retention_max_bytes").gauge();
        Gauge maxAgeDaysGauge = registry.find("sync_retention_max_age_days").gauge();

        assertNotNull(maxBytesGauge);
        assertNotNull(maxAgeDaysGauge);

        assertEquals(maxBytes.doubleValue(), maxBytesGauge.value(), 0.001,
                "max_bytes gauge should reflect configured value");
        assertEquals(maxAgeDays.doubleValue(), maxAgeDaysGauge.value(), 0.001,
                "max_age_days gauge should reflect configured value");
    }

    @Test
    @DisplayName("updateRetentionConfig with null values should set gauges to 0")
    void testUpdateRetentionConfig_NullValues() {
        // Given: null values (no limits)

        // When: updating with null
        syncMetrics.updateRetentionConfig(null, null);

        // Then: gauges should report 0
        Gauge maxBytesGauge = registry.find("sync_retention_max_bytes").gauge();
        Gauge maxAgeDaysGauge = registry.find("sync_retention_max_age_days").gauge();

        assertNotNull(maxBytesGauge);
        assertNotNull(maxAgeDaysGauge);

        assertEquals(0.0, maxBytesGauge.value(), 0.001,
                "max_bytes gauge should be 0 when null");
        assertEquals(0.0, maxAgeDaysGauge.value(), 0.001,
                "max_age_days gauge should be 0 when null");
    }

    @Test
    @DisplayName("incrementPurgeRuns should create counter with reason tag")
    void testIncrementPurgeRuns() {
        // Given: initial state
        Counter counterBefore = registry.find("sync_purge_runs_total")
                .tag("reason", "scheduled")
                .counter();
        double initialCount = counterBefore != null ? counterBefore.count() : 0.0;

        // When: incrementing purge counter
        syncMetrics.incrementPurgeRuns("scheduled");

        // Then: counter should be incremented
        Counter counterAfter = registry.find("sync_purge_runs_total")
                .tag("reason", "scheduled")
                .counter();

        assertNotNull(counterAfter, "sync_purge_runs_total counter should exist");
        assertEquals(initialCount + 1.0, counterAfter.count(), 0.001,
                "Counter should be incremented by 1");
    }

    @Test
    @DisplayName("incrementPurgeRuns should track different reasons separately")
    void testIncrementPurgeRuns_DifferentReasons() {
        // When: incrementing different reasons
        syncMetrics.incrementPurgeRuns("scheduled");
        syncMetrics.incrementPurgeRuns("post-batch");
        syncMetrics.incrementPurgeRuns("scheduled");

        // Then: counters should be tracked separately
        Counter scheduledCounter = registry.find("sync_purge_runs_total")
                .tag("reason", "scheduled")
                .counter();
        Counter postBatchCounter = registry.find("sync_purge_runs_total")
                .tag("reason", "post-batch")
                .counter();

        assertNotNull(scheduledCounter);
        assertNotNull(postBatchCounter);

        // Note: Can't assert exact counts since other tests may have incremented
        assertTrue(scheduledCounter.count() >= 2.0,
                "scheduled counter should have at least 2 increments");
        assertTrue(postBatchCounter.count() >= 1.0,
                "post-batch counter should have at least 1 increment");
    }

    @Test
    @DisplayName("purge timer should record duration")
    void testPurgeTimer() {
        // Given: a timer sample
        Timer.Sample sample = syncMetrics.startPurgeTimer();

        // When: simulating work and recording duration
        try {
            Thread.sleep(10); // Simulate work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        syncMetrics.recordPurgeDuration(sample);

        // Then: timer should have recorded a value
        Timer timer = registry.find("sync_purge_duration_seconds").timer();
        assertNotNull(timer, "sync_purge_duration_seconds timer should exist");
        assertTrue(timer.count() >= 1, "Timer should have at least one recording");
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0,
                "Timer should have recorded positive duration");
    }

    @Test
    @DisplayName("updateDatabaseSize should update gauge from file")
    void testUpdateDatabaseSize() throws IOException {
        // Given: a database file path
        Path tempFile = Files.createTempFile("test-db", ".db");
        Files.write(tempFile, "test data".getBytes());
        syncMetrics.setDatabasePath(tempFile.toString());

        // When: updating database size
        syncMetrics.updateDatabaseSize();

        // Then: gauge should reflect file size
        Gauge dbSizeGauge = registry.find("sync_db_size_bytes").gauge();
        assertNotNull(dbSizeGauge);
        assertTrue(dbSizeGauge.value() > 0, "Database size should be positive");

        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("updateDatabaseSize should handle missing file gracefully")
    void testUpdateDatabaseSize_MissingFile() {
        // Given: a non-existent database file path
        syncMetrics.setDatabasePath("/non/existent/path/database.db");

        // When: updating database size (should not throw exception)
        assertDoesNotThrow(() -> syncMetrics.updateDatabaseSize(),
                "Should handle missing file gracefully");

        // Then: gauge value should remain unchanged (not crash)
        Gauge dbSizeGauge = registry.find("sync_db_size_bytes").gauge();
        assertNotNull(dbSizeGauge);
        // Value might be 0 or previous value - just verify it doesn't crash
    }

    @Test
    @DisplayName("updateLastSuccessEpoch should update gauge")
    void testUpdateLastSuccessEpoch() {
        // Given: initial state
        Gauge lastSuccessGauge = registry.find("sync_last_success_epoch_seconds").gauge();
        double valueBefore = lastSuccessGauge != null ? lastSuccessGauge.value() : 0.0;

        // When: updating last success epoch
        syncMetrics.updateLastSuccessEpoch();

        // Then: gauge should be updated to current time
        Gauge lastSuccessGaugeAfter = registry.find("sync_last_success_epoch_seconds").gauge();
        assertNotNull(lastSuccessGaugeAfter);
        assertTrue(lastSuccessGaugeAfter.value() > valueBefore,
                "Last success epoch should be updated to current time");

        long currentEpoch = System.currentTimeMillis() / 1000;
        assertTrue(Math.abs(lastSuccessGaugeAfter.value() - currentEpoch) < 5,
                "Last success epoch should be close to current time (within 5 seconds)");
    }
}
