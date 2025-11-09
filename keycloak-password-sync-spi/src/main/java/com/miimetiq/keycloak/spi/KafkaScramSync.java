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

import java.util.Collections;
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
     *
     * @param username the Kafka principal/username
     * @param password the plaintext password
     * @throws KafkaSyncException if the sync fails
     */
    public void syncPasswordToKafka(String username, String password) {
        LOG.infof("Syncing password to Kafka for user: %s", username);

        try {
            // Step 1: Generate SCRAM-SHA-256 credentials from password
            ScramCredential scramCredential = scramGenerator.generateScramSha256(password, DEFAULT_SCRAM_ITERATIONS);
            LOG.debugf("Generated SCRAM-SHA-256 credentials for user: %s", username);

            // Step 2: Get Kafka AdminClient
            AdminClient adminClient;
            try {
                adminClient = KafkaAdminClientFactory.getAdminClient();
            } catch (Exception e) {
                throw new KafkaSyncException("Failed to connect to Kafka: " + e.getMessage(), e);
            }

            // Step 3: Convert our ScramCredential to Kafka's format
            org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism = convertToKafkaScramMechanism(scramCredential.getMechanism());
            ScramCredentialInfo credentialInfo = new ScramCredentialInfo(
                    kafkaMechanism,
                    scramCredential.getIterations()
            );

            // Step 4: Create upsertion alteration with password
            UserScramCredentialUpsertion upsertion = new UserScramCredentialUpsertion(
                    username,
                    credentialInfo,
                    password
            );

            // Step 5: Execute upsert to Kafka
            AlterUserScramCredentialsResult result = adminClient.alterUserScramCredentials(
                    Collections.singletonList(upsertion)
            );

            // Step 6: Wait for completion and check for errors
            try {
                result.all().get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                LOG.infof("Successfully synced password to Kafka for user: %s", username);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new KafkaSyncException("Kafka rejected the credential update: " + cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new KafkaSyncException("Kafka sync timed out after " + KAFKA_TIMEOUT_SECONDS + " seconds", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KafkaSyncException("Kafka sync was interrupted", e);
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
