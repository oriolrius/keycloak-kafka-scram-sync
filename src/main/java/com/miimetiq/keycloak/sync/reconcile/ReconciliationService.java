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
import org.apache.kafka.common.KafkaFuture;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            List<KeycloakUserInfo> users = keycloakUserFetcher.fetchAllUsers();
            LOG.infof("Fetched %d users from Keycloak", users.size());

            // Record Keycloak fetch metric
            syncMetrics.incrementKeycloakFetch(realm, source);

            // Step 3: Create sync_batch record
            SyncBatch batch = createSyncBatch(correlationId, startedAt, source, users.size());
            entityManager.persist(batch);
            entityManager.flush(); // Ensure batch ID is available

            // Step 4: Generate credentials and prepare upsert map
            LOG.info("Generating SCRAM credentials for users...");
            Map<String, CredentialSpec> credentialSpecs = new HashMap<>();

            for (KeycloakUserInfo user : users) {
                String password = generateRandomPassword();
                credentialSpecs.put(user.getUsername(), new CredentialSpec(DEFAULT_MECHANISM, password, DEFAULT_ITERATIONS));
            }

            LOG.infof("Generated %d SCRAM credentials", credentialSpecs.size());

            // Step 5: Execute batch upsert to Kafka
            LOG.info("Upserting SCRAM credentials to Kafka...");
            AlterUserScramCredentialsResult result = kafkaScramManager.upsertUserScramCredentials(credentialSpecs);

            // Step 6: Wait for results and persist operations
            LOG.info("Waiting for Kafka operations to complete...");
            Map<String, Throwable> errors = kafkaScramManager.waitForAlterations(result);

            // Step 7: Persist each operation result
            int successCount = 0;
            int errorCount = 0;

            for (KeycloakUserInfo user : users) {
                String principal = user.getUsername();
                Throwable error = errors.get(principal);

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
                    // Record successful SCRAM upsert metric
                    syncMetrics.incrementKafkaScramUpsert(clusterId, DEFAULT_MECHANISM.name(), "SUCCESS");
                } else {
                    errorCount++;
                    batch.incrementError();
                    // Record failed SCRAM upsert metric
                    syncMetrics.incrementKafkaScramUpsert(clusterId, DEFAULT_MECHANISM.name(), "ERROR");
                    LOG.warnf("Failed to upsert SCRAM credential for principal '%s': %s",
                            principal, error.getMessage());
                }
            }

            // Step 8: Finalize batch
            LocalDateTime finishedAt = LocalDateTime.now();
            batch.setFinishedAt(finishedAt);
            entityManager.merge(batch);

            LOG.infof("Reconciliation cycle completed: correlation_id=%s, success=%d, errors=%d, duration=%dms",
                    correlationId, successCount, errorCount,
                    java.time.Duration.between(startedAt, finishedAt).toMillis());

            // Step 9: Record metrics
            syncMetrics.recordReconciliationDuration(reconciliationTimer, realm, clusterId, source);
            syncMetrics.updateLastSuccessEpoch();
            syncMetrics.updateDatabaseSize();

            // Step 10: Return result summary
            return new ReconciliationResult(
                    correlationId,
                    startedAt,
                    finishedAt,
                    source,
                    users.size(),
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
