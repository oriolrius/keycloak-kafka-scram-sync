package com.miimetiq.keycloak.spi;

import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.credential.PasswordCredentialModel;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Password hash provider that implements PBKDF2-SHA256 for Keycloak password hashing.
 *
 * This provider implements the same PBKDF2-SHA256 algorithm as Keycloak's
 * built-in provider, ensuring password verification works correctly.
 */
public class PasswordSyncHashProviderSimple implements PasswordHashProvider {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTE_SIZE = 16;
    private static final int HASH_BYTE_SIZE = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String providerId;
    private final int defaultIterations;

    public PasswordSyncHashProviderSimple(String providerId, int defaultIterations) {
        this.providerId = providerId;
        this.defaultIterations = defaultIterations;
    }

    @Override
    public boolean policyCheck(PasswordPolicy policy, PasswordCredentialModel credential) {
        int policyHashIterations = policy.getHashIterations();
        if (policyHashIterations == -1) {
            policyHashIterations = defaultIterations;
        }

        return credential.getPasswordCredentialData().getHashIterations() == policyHashIterations
                && providerId.equals(credential.getPasswordCredentialData().getAlgorithm());
    }

    @Override
    public PasswordCredentialModel encodedCredential(String rawPassword, int iterations) {
        if (iterations == -1) {
            iterations = defaultIterations;
        }

        // Store plaintext password in ThreadLocal for EventListener to retrieve
        PasswordCorrelationContext.setPassword(rawPassword);

        byte[] salt = getSalt();
        String encodedPassword = encode(rawPassword, iterations, salt);

        return PasswordCredentialModel.createFromValues(providerId, salt, iterations, encodedPassword);
    }

    @Override
    public boolean verify(String rawPassword, PasswordCredentialModel credential) {
        byte[] salt = credential.getPasswordSecretData().getSalt();
        int iterations = credential.getPasswordCredentialData().getHashIterations();

        String encodedPassword = encode(rawPassword, iterations, salt);
        return encodedPassword.equals(credential.getPasswordSecretData().getValue());
    }

    @Override
    public void close() {
        // No resources to close
    }

    /**
     * Generate a random salt for password hashing.
     */
    private byte[] getSalt() {
        byte[] salt = new byte[SALT_BYTE_SIZE];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Encode password using PBKDF2-SHA256.
     */
    private String encode(String rawPassword, int iterations, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(
                    rawPassword.toCharArray(),
                    salt,
                    iterations,
                    HASH_BYTE_SIZE * 8
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error encoding password", e);
        }
    }
}
