package com.miimetiq.keycloak.sync.kafka;

import com.miimetiq.keycloak.sync.domain.ScramCredential;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsResult;
import org.apache.kafka.clients.admin.DescribeUserScramCredentialsResult;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.UserScramCredentialAlteration;
import org.apache.kafka.clients.admin.UserScramCredentialDeletion;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing SCRAM credentials in Kafka using the AdminClient API.
 * <p>
 * This service provides methods to describe, create, update, and delete SCRAM credentials
 * for Kafka users. It supports batch operations for efficient management of multiple credentials.
 * <p>
 * Features:
 * - Describe existing SCRAM credentials for users
 * - Upsert (create/update) SCRAM credentials with SHA-256 or SHA-512
 * - Delete SCRAM credentials by mechanism
 * - Batch operations for multiple principals
 * - Comprehensive error handling and logging
 */
@ApplicationScoped
public class KafkaScramManager {

    private static final Logger LOG = Logger.getLogger(KafkaScramManager.class);

    @Inject
    AdminClient adminClient;

    /**
     * Describes SCRAM credentials for all users in Kafka.
     * <p>
     * Returns a map of principal names to their SCRAM credential information,
     * including the mechanisms (SHA-256, SHA-512) and iteration counts.
     *
     * @return map of principal to list of SCRAM credential info
     * @throws KafkaScramException if the operation fails
     */
    public Map<String, List<ScramCredentialInfo>> describeUserScramCredentials() {
        return describeUserScramCredentials(null);
    }

    /**
     * Describes SCRAM credentials for specific users in Kafka.
     * <p>
     * If principals is null or empty, describes all users.
     *
     * @param principals list of principal names to describe, or null for all users
     * @return map of principal to list of SCRAM credential info
     * @throws KafkaScramException if the operation fails
     */
    public Map<String, List<ScramCredentialInfo>> describeUserScramCredentials(List<String> principals) {
        LOG.infof("Describing SCRAM credentials for %s",
                principals == null || principals.isEmpty() ? "all users" : principals.size() + " users");

        try {
            DescribeUserScramCredentialsResult result;
            if (principals == null || principals.isEmpty()) {
                result = adminClient.describeUserScramCredentials();
            } else {
                result = adminClient.describeUserScramCredentials(principals);
            }

            Map<String, List<ScramCredentialInfo>> credentials = new HashMap<>();
            result.all().get().forEach((user, userScramCredentialsInfo) -> {
                List<ScramCredentialInfo> credList = new ArrayList<>(userScramCredentialsInfo.credentialInfos());
                credentials.put(user, credList);
                LOG.debugf("User '%s' has %d SCRAM credential(s): %s",
                        user, credList.size(),
                        credList.stream().map(c -> c.mechanism().mechanismName()).toList());
            });

            LOG.infof("Successfully described SCRAM credentials for %d users", credentials.size());
            return credentials;

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsupportedVersionException) {
                LOG.error("Kafka broker does not support SCRAM credential management", e);
                throw new KafkaScramException("Kafka broker does not support SCRAM credential management", cause);
            }
            LOG.errorf(e, "Failed to describe SCRAM credentials");
            throw new KafkaScramException("Failed to describe SCRAM credentials: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaScramException("Operation interrupted while describing SCRAM credentials", e);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error describing SCRAM credentials");
            throw new KafkaScramException("Unexpected error describing SCRAM credentials: " + e.getMessage(), e);
        }
    }

    /**
     * Upserts (creates or updates) a SCRAM credential for a single user with a password.
     *
     * @param principal  the user principal name
     * @param mechanism  the SCRAM mechanism (SHA-256 or SHA-512)
     * @param password   the user's password
     * @param iterations the number of PBKDF2 iterations (default: 4096)
     * @return the result of the operation with per-principal futures
     * @throws KafkaScramException if the operation fails
     */
    public AlterUserScramCredentialsResult upsertUserScramCredential(
            String principal, ScramMechanism mechanism, String password, int iterations) {
        Map<String, CredentialSpec> credentials = Collections.singletonMap(
                principal, new CredentialSpec(mechanism, password, iterations)
        );
        return upsertUserScramCredentials(credentials);
    }

    /**
     * Upserts (creates or updates) SCRAM credentials for multiple users in a batch operation.
     *
     * @param credentials map of principal to credential spec (mechanism, password, iterations)
     * @return the result of the operation with per-principal futures
     * @throws KafkaScramException if the operation fails
     */
    public AlterUserScramCredentialsResult upsertUserScramCredentials(Map<String, CredentialSpec> credentials) {
        LOG.infof("Upserting SCRAM credentials for %d user(s)", credentials.size());

        List<UserScramCredentialAlteration> alterations = new ArrayList<>();

        for (Map.Entry<String, CredentialSpec> entry : credentials.entrySet()) {
            String principal = entry.getKey();
            CredentialSpec spec = entry.getValue();

            // Convert our ScramMechanism enum to Kafka's mechanism type
            org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism =
                    convertToKafkaScramMechanism(spec.mechanism);

            ScramCredentialInfo credentialInfo = new ScramCredentialInfo(kafkaMechanism, spec.iterations);

            UserScramCredentialUpsertion upsertion = new UserScramCredentialUpsertion(
                    principal,
                    credentialInfo,
                    spec.password
            );

            alterations.add(upsertion);

            LOG.debugf("Prepared upsert for principal '%s' with mechanism %s, iterations %d",
                    principal, spec.mechanism, spec.iterations);
        }

        return alterUserScramCredentials(alterations);
    }

    /**
     * Specification for a SCRAM credential to be created/updated.
     */
    public static class CredentialSpec {
        public final ScramMechanism mechanism;
        public final String password;
        public final int iterations;

        public CredentialSpec(ScramMechanism mechanism, String password, int iterations) {
            this.mechanism = mechanism;
            this.password = password;
            this.iterations = iterations;
        }
    }

    /**
     * Deletes a SCRAM credential for a single user.
     *
     * @param principal the user principal name
     * @param mechanism the SCRAM mechanism to delete
     * @return the result of the operation with per-principal futures
     * @throws KafkaScramException if the operation fails
     */
    public AlterUserScramCredentialsResult deleteUserScramCredential(String principal, ScramMechanism mechanism) {
        return deleteUserScramCredentials(Collections.singletonMap(principal, Collections.singletonList(mechanism)));
    }

    /**
     * Deletes SCRAM credentials for multiple users in a batch operation.
     *
     * @param deletions map of principal to list of mechanisms to delete
     * @return the result of the operation with per-principal futures
     * @throws KafkaScramException if the operation fails
     */
    public AlterUserScramCredentialsResult deleteUserScramCredentials(Map<String, List<ScramMechanism>> deletions) {
        LOG.infof("Deleting SCRAM credentials for %d user(s)", deletions.size());

        List<UserScramCredentialAlteration> alterations = new ArrayList<>();

        for (Map.Entry<String, List<ScramMechanism>> entry : deletions.entrySet()) {
            String principal = entry.getKey();
            List<ScramMechanism> mechanisms = entry.getValue();

            for (ScramMechanism mechanism : mechanisms) {
                org.apache.kafka.clients.admin.ScramMechanism kafkaMechanism =
                        convertToKafkaScramMechanism(mechanism);

                UserScramCredentialDeletion deletion = new UserScramCredentialDeletion(principal, kafkaMechanism);
                alterations.add(deletion);

                LOG.debugf("Prepared deletion for principal '%s' with mechanism %s",
                        principal, mechanism);
            }
        }

        return alterUserScramCredentials(alterations);
    }

    /**
     * Alters (upserts or deletes) SCRAM credentials using a list of alterations.
     * <p>
     * This is the core method that executes batch operations against Kafka.
     * Returns a result object containing futures for each principal, allowing
     * per-principal error handling.
     *
     * @param alterations list of credential alterations (upserts or deletions)
     * @return the result of the operation with per-principal futures
     * @throws KafkaScramException if the operation fails at a non-principal level
     */
    public AlterUserScramCredentialsResult alterUserScramCredentials(List<UserScramCredentialAlteration> alterations) {
        if (alterations == null || alterations.isEmpty()) {
            LOG.warn("No alterations provided, returning empty result");
            return null;
        }

        LOG.infof("Executing %d SCRAM credential alteration(s)", alterations.size());

        try {
            AlterUserScramCredentialsResult result = adminClient.alterUserScramCredentials(alterations);

            // Log the operation types
            long upserts = alterations.stream()
                    .filter(a -> a instanceof UserScramCredentialUpsertion)
                    .count();
            long deletes = alterations.stream()
                    .filter(a -> a instanceof UserScramCredentialDeletion)
                    .count();

            LOG.infof("Submitted %d upsert(s) and %d deletion(s) to Kafka", upserts, deletes);

            return result;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to alter SCRAM credentials");
            throw new KafkaScramException("Failed to alter SCRAM credentials: " + e.getMessage(), e);
        }
    }

    /**
     * Waits for all alterations in a result to complete and checks for errors.
     * <p>
     * This is a helper method to block until all operations complete and
     * collect any per-principal errors.
     *
     * @param result the alteration result from a previous operation
     * @return map of principal to any error that occurred (empty if all succeeded)
     */
    public Map<String, Throwable> waitForAlterations(AlterUserScramCredentialsResult result) {
        if (result == null) {
            return Collections.emptyMap();
        }

        Map<String, Throwable> errors = new HashMap<>();

        try {
            Map<String, KafkaFuture<Void>> futures = result.values();

            for (Map.Entry<String, KafkaFuture<Void>> entry : futures.entrySet()) {
                String principal = entry.getKey();
                try {
                    entry.getValue().get();
                    LOG.debugf("SCRAM credential alteration succeeded for principal '%s'", principal);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    LOG.warnf(cause, "SCRAM credential alteration failed for principal '%s'", principal);
                    errors.put(principal, cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warnf(e, "SCRAM credential alteration interrupted for principal '%s'", principal);
                    errors.put(principal, e);
                }
            }

            if (errors.isEmpty()) {
                LOG.infof("All %d SCRAM credential alteration(s) completed successfully", futures.size());
            } else {
                LOG.warnf("%d out of %d SCRAM credential alteration(s) failed",
                        errors.size(), futures.size());
            }

        } catch (Exception e) {
            LOG.errorf(e, "Error waiting for SCRAM credential alterations");
            throw new KafkaScramException("Error waiting for SCRAM credential alterations: " + e.getMessage(), e);
        }

        return errors;
    }

    /**
     * Converts our domain ScramMechanism enum to Kafka's ScramMechanism enum.
     *
     * @param mechanism our domain mechanism
     * @return Kafka's ScramMechanism
     */
    private org.apache.kafka.clients.admin.ScramMechanism convertToKafkaScramMechanism(ScramMechanism mechanism) {
        return switch (mechanism) {
            case SCRAM_SHA_256 -> org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256;
            case SCRAM_SHA_512 -> org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_512;
        };
    }

    /**
     * Exception thrown when SCRAM credential operations fail.
     */
    public static class KafkaScramException extends RuntimeException {
        public KafkaScramException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
