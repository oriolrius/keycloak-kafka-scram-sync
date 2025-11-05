package com.miimetiq.keycloak.sync.reconcile;

import com.miimetiq.keycloak.sync.crypto.ScramCredentialGenerator;
import com.miimetiq.keycloak.sync.domain.KeycloakUserInfo;
import com.miimetiq.keycloak.sync.domain.ScramCredential;
import com.miimetiq.keycloak.sync.domain.entity.SyncBatch;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import com.miimetiq.keycloak.sync.kafka.KafkaConfig;
import com.miimetiq.keycloak.sync.kafka.KafkaScramManager;
import com.miimetiq.keycloak.sync.kafka.KafkaScramManager.CredentialSpec;
import com.miimetiq.keycloak.sync.keycloak.KeycloakConfig;
import com.miimetiq.keycloak.sync.keycloak.KeycloakUserFetcher;
import com.miimetiq.keycloak.sync.metrics.SyncMetrics;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsResult;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.common.KafkaFuture;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core service that orchestrates the complete reconciliation cycle.
 * <p>
 * This service coordinates fetching users from Keycloak, generating SCRAM credentials,
 * synchronizing them to Kafka, and persisting operation results to the database.
 * <p>
 * The reconciliation flow:
 * 1. Generate correlation ID and create sync_batch record
 * 2. Fetch all enabled users from Keycloak
 * 3. Generate random passwords and SCRAM credentials for each user
 * 4. Upsert credentials to Kafka in batch
 * 5. Wait for results and persist each operation (success/error)
 * 6. Update sync_batch with final counts
 * 7. Return ReconciliationResult summary
 */
@ApplicationScoped
public class ReconciliationService {

    private static final Logger LOG = Logger.getLogger(ReconciliationService.class);

    // Password generation parameters
    private static final int PASSWORD_LENGTH = 32;
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    // Default SCRAM mechanism and iterations
    private static final ScramMechanism DEFAULT_MECHANISM = ScramMechanism.SCRAM_SHA_256;
    private static final int DEFAULT_ITERATIONS = 4096;

    @Inject
    KeycloakUserFetcher keycloakUserFetcher;

    @Inject
    KafkaScramManager kafkaScramManager;

    @Inject
    ScramCredentialGenerator scramCredentialGenerator;

    @Inject
    KeycloakConfig keycloakConfig;

    @Inject
    KafkaConfig kafkaConfig;

    @Inject
    EntityManager entityManager;

    @Inject
    SyncMetrics syncMetrics;

    @Inject
    SyncDiffEngine syncDiffEngine;

    @Inject
    com.miimetiq.keycloak.sync.retention.RetentionScheduler retentionScheduler;

    /**
     * Performs a complete reconciliation cycle.
     * <p>
     * This method orchestrates fetching users from Keycloak, generating credentials,
     * synchronizing to Kafka, and persisting results.
     *
     * @param source the source triggering this reconciliation (SCHEDULED, MANUAL, WEBHOOK)
     * @return summary result with statistics and timing
     */
    @Transactional
    public ReconciliationResult performReconciliation(String source) {
        // Step 1: Generate correlation ID and start timing
        String correlationId = generateCorrelationId();
        LocalDateTime startedAt = LocalDateTime.now();
        Timer.Sample reconciliationTimer = syncMetrics.startReconciliationTimer();

        String realm = keycloakConfig.realm();
        String clusterId = kafkaConfig.bootstrapServers();

        LOG.infof("Starting reconciliation cycle with correlation_id=%s, source=%s", correlationId, source);

        try {
            // Step 2: Fetch all users from Keycloak
            LOG.info("Fetching users from Keycloak...");
            List<KeycloakUserInfo> keycloakUsers = keycloakUserFetcher.fetchAllUsers();
            LOG.infof("Fetched %d users from Keycloak", keycloakUsers.size());

            // Record Keycloak fetch metric
            syncMetrics.incrementKeycloakFetch(realm, source);

            // Step 3: Fetch all SCRAM principals from Kafka
            LOG.info("Fetching SCRAM principals from Kafka...");
            Map<String, List<ScramCredentialInfo>> kafkaCredentials = kafkaScramManager.describeUserScramCredentials();
            Set<String> kafkaPrincipals = kafkaCredentials.keySet();
            LOG.infof("Fetched %d principals from Kafka", kafkaPrincipals.size());

            // Step 4: Compute diff using SyncDiffEngine
            LOG.info("Computing synchronization diff...");
            SyncPlan syncPlan = syncDiffEngine.computeDiff(keycloakUsers, kafkaPrincipals);
            LOG.infof("Sync plan: %d upsert(s), %d delete(s)", syncPlan.getUpsertCount(), syncPlan.getDeleteCount());

            // If plan is empty, log and return early
            if (syncPlan.isEmpty()) {
                LOG.info("No synchronization operations needed - systems are in sync");
                LocalDateTime finishedAt = LocalDateTime.now();

                // Create empty batch record for audit trail
                SyncBatch batch = createSyncBatch(correlationId, startedAt, source, 0);
                batch.setFinishedAt(finishedAt);
                entityManager.persist(batch);

                syncMetrics.recordReconciliationDuration(reconciliationTimer, realm, clusterId, source);
                syncMetrics.updateLastSuccessEpoch();

                return new ReconciliationResult(correlationId, startedAt, finishedAt, source, 0, 0, 0);
            }

            // Step 5: Create sync_batch record
            int totalOperations = syncPlan.getTotalOperations();
            SyncBatch batch = createSyncBatch(correlationId, startedAt, source, totalOperations);
            entityManager.persist(batch);
            entityManager.flush(); // Ensure batch ID is available

            int successCount = 0;
            int errorCount = 0;

            // Step 6: Process upserts
            if (syncPlan.getUpsertCount() > 0) {
                LOG.infof("Processing %d upsert operation(s)...", syncPlan.getUpsertCount());

                // Generate credentials for upserts
                Map<String, CredentialSpec> credentialSpecs = new HashMap<>();
                for (KeycloakUserInfo user : syncPlan.getUpserts()) {
                    String password = generateRandomPassword();
                    credentialSpecs.put(user.getUsername(), new CredentialSpec(DEFAULT_MECHANISM, password, DEFAULT_ITERATIONS));
                }

                // Execute batch upsert to Kafka
                AlterUserScramCredentialsResult upsertResult = kafkaScramManager.upsertUserScramCredentials(credentialSpecs);
                Map<String, Throwable> upsertErrors = kafkaScramManager.waitForAlterations(upsertResult);

                // Persist each upsert operation result
                for (KeycloakUserInfo user : syncPlan.getUpserts()) {
                    String principal = user.getUsername();
                    Throwable error = upsertErrors.get(principal);

                    SyncOperation operation = createSyncOperation(
                            correlationId,
                            principal,
                            OpType.SCRAM_UPSERT,
                            DEFAULT_MECHANISM,
                            error == null ? OperationResult.SUCCESS : OperationResult.ERROR,
                            error
                    );

                    entityManager.persist(operation);

                    if (error == null) {
                        successCount++;
                        batch.incrementSuccess();
                        syncMetrics.incrementKafkaScramUpsert(clusterId, DEFAULT_MECHANISM.name(), "SUCCESS");
                    } else {
                        errorCount++;
                        batch.incrementError();
                        syncMetrics.incrementKafkaScramUpsert(clusterId, DEFAULT_MECHANISM.name(), "ERROR");
                        LOG.warnf("Failed to upsert SCRAM credential for principal '%s': %s",
                                principal, error.getMessage());
                    }
                }

                LOG.infof("Completed upsert operations: %d success, %d errors",
                        successCount, upsertErrors.size());
            }

            // Step 7: Process deletes
            if (syncPlan.getDeleteCount() > 0) {
                LOG.infof("Processing %d delete operation(s)...", syncPlan.getDeleteCount());

                // Build deletion map (principal -> list of mechanisms to delete)
                Map<String, List<ScramMechanism>> deletionMap = new HashMap<>();
                for (String principal : syncPlan.getDeletes()) {
                    // Delete all SCRAM mechanisms for this principal
                    List<ScramCredentialInfo> credentials = kafkaCredentials.get(principal);
                    List<ScramMechanism> mechanismsToDelete = new ArrayList<>();

                    if (credentials != null) {
                        for (ScramCredentialInfo credInfo : credentials) {
                            // Convert Kafka mechanism to our domain mechanism
                            ScramMechanism mechanism = convertFromKafkaScramMechanism(credInfo.mechanism());
                            mechanismsToDelete.add(mechanism);
                        }
                    }

                    // If no credentials found, still attempt delete with default mechanism
                    if (mechanismsToDelete.isEmpty()) {
                        mechanismsToDelete.add(DEFAULT_MECHANISM);
                    }

                    deletionMap.put(principal, mechanismsToDelete);
                }

                // Execute batch delete to Kafka
                AlterUserScramCredentialsResult deleteResult = kafkaScramManager.deleteUserScramCredentials(deletionMap);
                Map<String, Throwable> deleteErrors = kafkaScramManager.waitForAlterations(deleteResult);

                // Persist each delete operation result
                for (String principal : syncPlan.getDeletes()) {
                    Throwable error = deleteErrors.get(principal);

                    // Use the first mechanism we attempted to delete for the operation record
                    ScramMechanism mechanism = deletionMap.get(principal).get(0);

                    SyncOperation operation = createSyncOperation(
                            correlationId,
                            principal,
                            OpType.SCRAM_DELETE,
                            mechanism,
                            error == null ? OperationResult.SUCCESS : OperationResult.ERROR,
                            error
                    );

                    entityManager.persist(operation);

                    if (error == null) {
                        successCount++;
                        batch.incrementSuccess();
                        syncMetrics.incrementKafkaScramDelete(clusterId, "SUCCESS");
                    } else {
                        errorCount++;
                        batch.incrementError();
                        syncMetrics.incrementKafkaScramDelete(clusterId, "ERROR");
                        LOG.warnf("Failed to delete SCRAM credential for principal '%s': %s",
                                principal, error.getMessage());
                    }
                }

                LOG.infof("Completed delete operations: %d success, %d errors",
                        syncPlan.getDeleteCount() - deleteErrors.size(), deleteErrors.size());
            }

            // Step 8: Finalize batch
            LocalDateTime finishedAt = LocalDateTime.now();
            batch.setFinishedAt(finishedAt);
            entityManager.merge(batch);

            LOG.infof("Reconciliation cycle completed: correlation_id=%s, total=%d, success=%d, errors=%d, duration=%dms",
                    correlationId, totalOperations, successCount, errorCount,
                    java.time.Duration.between(startedAt, finishedAt).toMillis());

            // Step 9: Record metrics
            syncMetrics.recordReconciliationDuration(reconciliationTimer, realm, clusterId, source);
            syncMetrics.updateLastSuccessEpoch();
            syncMetrics.updateDatabaseSize();

            // Step 10: Trigger retention purge after batch completion
            try {
                retentionScheduler.triggerPostSyncPurge();
            } catch (Exception e) {
                LOG.warnf(e, "Post-sync retention purge failed, but will continue: %s", e.getMessage());
                // Don't fail the reconciliation if purge fails
            }

            // Step 11: Return result summary
            return new ReconciliationResult(
                    correlationId,
                    startedAt,
                    finishedAt,
                    source,
                    totalOperations,
                    successCount,
                    errorCount
            );

        } catch (Exception e) {
            LOG.errorf(e, "Reconciliation cycle failed with correlation_id=%s", correlationId);
            // Still record the timer even on failure
            syncMetrics.recordReconciliationDuration(reconciliationTimer, realm, clusterId, source);
            throw new ReconciliationException("Reconciliation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a unique correlation ID for this reconciliation run.
     *
     * @return UUID-based correlation ID
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Creates a new SyncBatch entity for tracking this reconciliation cycle.
     *
     * @param correlationId unique identifier for this cycle
     * @param startedAt     when the cycle started
     * @param source        the trigger source (SCHEDULED, MANUAL, WEBHOOK)
     * @param itemsTotal    total number of items to process
     * @return initialized SyncBatch entity
     */
    private SyncBatch createSyncBatch(String correlationId, LocalDateTime startedAt, String source, int itemsTotal) {
        return new SyncBatch(correlationId, startedAt, source, itemsTotal);
    }

    /**
     * Creates a SyncOperation entity for a single operation result.
     *
     * @param correlationId correlation ID for this batch
     * @param principal     the user principal name
     * @param opType        operation type (SCRAM_UPSERT, SCRAM_DELETE, etc.)
     * @param mechanism     SCRAM mechanism used
     * @param result        operation result (SUCCESS or ERROR)
     * @param error         error throwable if operation failed, null otherwise
     * @return initialized SyncOperation entity
     */
    private SyncOperation createSyncOperation(String correlationId, String principal,
                                               OpType opType, ScramMechanism mechanism,
                                               OperationResult result, Throwable error) {
        SyncOperation operation = new SyncOperation(
                correlationId,
                LocalDateTime.now(),
                keycloakConfig.realm(),
                kafkaConfig.bootstrapServers(),
                principal,
                opType,
                result,
                0 // duration set to 0 for now (could be tracked per-operation if needed)
        );

        operation.setMechanism(mechanism);

        if (error != null) {
            operation.setErrorCode(error.getClass().getSimpleName());
            operation.setErrorMessage(truncateErrorMessage(error.getMessage()));
        }

        return operation;
    }

    /**
     * Generates a cryptographically secure random password.
     *
     * @return random password string
     */
    private String generateRandomPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    /**
     * Converts Kafka's ScramMechanism enum to our domain ScramMechanism enum.
     *
     * @param kafkaMechanism Kafka's ScramMechanism
     * @return our domain ScramMechanism
     */
    private ScramMechanism convertFromKafkaScramMechanism(org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism) {
        return switch (kafkaMechanism) {
            case SCRAM_SHA_256 -> ScramMechanism.SCRAM_SHA_256;
            case SCRAM_SHA_512 -> ScramMechanism.SCRAM_SHA_512;
            default -> throw new IllegalArgumentException("Unsupported SCRAM mechanism: " + kafkaMechanism);
        };
    }

    /**
     * Truncates error messages to prevent database issues with very long messages.
     *
     * @param message the error message
     * @return truncated message (max 500 chars)
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }

    /**
     * Exception thrown when reconciliation fails.
     */
    public static class ReconciliationException extends RuntimeException {
        public ReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
