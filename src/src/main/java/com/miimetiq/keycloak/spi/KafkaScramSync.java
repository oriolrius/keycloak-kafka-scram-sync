package com.miimetiq.keycloak.spi;

import com.miimetiq.keycloak.spi.crypto.ScramCredentialGenerator;
import com.miimetiq.keycloak.spi.domain.ScramCredential;
import com.miimetiq.keycloak.spi.domain.enums.ScramMechanism;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsResult;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.UserScramCredentialAlteration;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for synchronizing passwords to Kafka SCRAM credentials.
 * <p>
 * This class handles the complete flow:
 * 1. Generate SCRAM credentials from plaintext password
 * 2. Upsert credentials to Kafka using AdminClient API
 * 3. Handle errors and provide clear feedback
 */
public class KafkaScramSync {

    private static final Logger LOG = Logger.getLogger(KafkaScramSync.class);
    private static final int DEFAULT_SCRAM_ITERATIONS = 4096;
    private static final int KAFKA_TIMEOUT_SECONDS = 30;

    private final ScramCredentialGenerator scramGenerator;

    public KafkaScramSync() {
        this.scramGenerator = new ScramCredentialGenerator();
    }

    /**
     * Synchronizes a password to Kafka by generating SCRAM credentials and upserting them.
     * Creates credentials for both SCRAM-SHA-256 and SCRAM-SHA-512 mechanisms.
     *
     * @param username the Kafka principal/username
     * @param password the plaintext password
     * @throws KafkaSyncException if the sync fails
     */
    public void syncPasswordToKafka(String username, String password) {
        LOG.infof("Syncing password to Kafka for user: %s", username);

        try {
            // Step 1: Generate SCRAM credentials for both SHA-256 and SHA-512
            ScramCredential scram256 = scramGenerator.generateScramSha256(password, DEFAULT_SCRAM_ITERATIONS);
            ScramCredential scram512 = scramGenerator.generateScramSha512(password, DEFAULT_SCRAM_ITERATIONS);
            LOG.debugf("Generated SCRAM-SHA-256 and SCRAM-SHA-512 credentials for user: %s", username);

            // Step 2: Get Kafka AdminClient
            AdminClient adminClient;
            try {
                adminClient = KafkaAdminClientFactory.getAdminClient();
            } catch (Exception e) {
                throw new KafkaSyncException("Failed to connect to Kafka: " + e.getMessage(), e);
            }

            // Step 3: Upsert SCRAM-SHA-256 credentials
            org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism256 = convertToKafkaScramMechanism(scram256.getMechanism());
            ScramCredentialInfo credentialInfo256 = new ScramCredentialInfo(
                    kafkaMechanism256,
                    scram256.getIterations()
            );
            UserScramCredentialUpsertion upsertion256 = new UserScramCredentialUpsertion(
                    username,
                    credentialInfo256,
                    password
            );

            AlterUserScramCredentialsResult result256 = adminClient.alterUserScramCredentials(
                    Collections.singletonList(upsertion256)
            );

            // Step 4: Wait for SCRAM-SHA-256 to complete
            try {
                result256.all().get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                LOG.debugf("Successfully synced SCRAM-SHA-256 for user: %s", username);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new KafkaSyncException("Kafka rejected SCRAM-SHA-256 credential update: " + cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new KafkaSyncException("SCRAM-SHA-256 sync timed out after " + KAFKA_TIMEOUT_SECONDS + " seconds", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KafkaSyncException("SCRAM-SHA-256 sync was interrupted", e);
            }

            // Step 5: Upsert SCRAM-SHA-512 credentials (separate request)
            org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism512 = convertToKafkaScramMechanism(scram512.getMechanism());
            ScramCredentialInfo credentialInfo512 = new ScramCredentialInfo(
                    kafkaMechanism512,
                    scram512.getIterations()
            );
            UserScramCredentialUpsertion upsertion512 = new UserScramCredentialUpsertion(
                    username,
                    credentialInfo512,
                    password
            );

            AlterUserScramCredentialsResult result512 = adminClient.alterUserScramCredentials(
                    Collections.singletonList(upsertion512)
            );

            // Step 6: Wait for SCRAM-SHA-512 to complete
            try {
                result512.all().get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                LOG.infof("Successfully synced password to Kafka for user: %s (SCRAM-SHA-256 and SCRAM-SHA-512)", username);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new KafkaSyncException("Kafka rejected SCRAM-SHA-512 credential update: " + cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new KafkaSyncException("SCRAM-SHA-512 sync timed out after " + KAFKA_TIMEOUT_SECONDS + " seconds", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KafkaSyncException("SCRAM-SHA-512 sync was interrupted", e);
            }

        } catch (ScramCredentialGenerator.ScramGenerationException e) {
            throw new KafkaSyncException("Failed to generate SCRAM credentials: " + e.getMessage(), e);
        } catch (KafkaSyncException e) {
            // Re-throw our own exceptions
            throw e;
        } catch (Exception e) {
            throw new KafkaSyncException("Unexpected error during Kafka sync: " + e.getMessage(), e);
        }
    }

    /**
     * Converts our ScramMechanism enum to Kafka's ScramMechanism enum.
     */
    private org.apache.kafka.clients.admin.ScramMechanism convertToKafkaScramMechanism(ScramMechanism mechanism) {
        switch (mechanism) {
            case SCRAM_SHA_256:
                return org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256;
            case SCRAM_SHA_512:
                return org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_512;
            default:
                throw new IllegalArgumentException("Unsupported SCRAM mechanism: " + mechanism);
        }
    }

    /**
     * Exception thrown when Kafka synchronization fails.
     */
    public static class KafkaSyncException extends RuntimeException {
        public KafkaSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
