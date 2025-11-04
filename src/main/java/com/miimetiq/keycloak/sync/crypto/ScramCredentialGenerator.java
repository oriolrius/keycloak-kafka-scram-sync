package com.miimetiq.keycloak.sync.crypto;

import com.miimetiq.keycloak.sync.domain.ScramCredential;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates SCRAM credentials following RFC 5802 specification.
 * <p>
 * This service implements the SCRAM (Salted Challenge Response Authentication Mechanism)
 * credential generation algorithm as defined in RFC 5802. It supports both SCRAM-SHA-256
 * and SCRAM-SHA-512 mechanisms.
 * <p>
 * The SCRAM credential generation process:
 * 1. Generate random salt (32 bytes)
 * 2. Compute SaltedPassword = PBKDF2(password, salt, iterations)
 * 3. Compute ClientKey = HMAC(SaltedPassword, "Client Key")
 * 4. Compute StoredKey = H(ClientKey)
 * 5. Compute ServerKey = HMAC(SaltedPassword, "Server Key")
 *
 * @see <a href="https://tools.ietf.org/html/rfc5802">RFC 5802</a>
 */
@ApplicationScoped
public class ScramCredentialGenerator {

    private static final int DEFAULT_ITERATIONS = 4096;
    private static final int SALT_LENGTH_BYTES = 32;
    private static final String CLIENT_KEY_TEXT = "Client Key";
    private static final String SERVER_KEY_TEXT = "Server Key";

    private final SecureRandom secureRandom;

    /**
     * Creates a new SCRAM credential generator with a secure random number generator.
     */
    public ScramCredentialGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a SCRAM-SHA-256 credential from a plaintext password.
     *
     * @param password the plaintext password
     * @return the SCRAM credential with SHA-256 mechanism
     */
    public ScramCredential generateScramSha256(String password) {
        return generateScramSha256(password, DEFAULT_ITERATIONS);
    }

    /**
     * Generates a SCRAM-SHA-256 credential from a plaintext password with custom iterations.
     *
     * @param password   the plaintext password
     * @param iterations the number of PBKDF2 iterations
     * @return the SCRAM credential with SHA-256 mechanism
     */
    public ScramCredential generateScramSha256(String password, int iterations) {
        Objects.requireNonNull(password, "password must not be null");
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        return generate(password, iterations, ScramMechanism.SCRAM_SHA_256, "SHA-256", "HmacSHA256");
    }

    /**
     * Generates a SCRAM-SHA-512 credential from a plaintext password.
     *
     * @param password the plaintext password
     * @return the SCRAM credential with SHA-512 mechanism
     */
    public ScramCredential generateScramSha512(String password) {
        return generateScramSha512(password, DEFAULT_ITERATIONS);
    }

    /**
     * Generates a SCRAM-SHA-512 credential from a plaintext password with custom iterations.
     *
     * @param password   the plaintext password
     * @param iterations the number of PBKDF2 iterations
     * @return the SCRAM credential with SHA-512 mechanism
     */
    public ScramCredential generateScramSha512(String password, int iterations) {
        Objects.requireNonNull(password, "password must not be null");
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        return generate(password, iterations, ScramMechanism.SCRAM_SHA_512, "SHA-512", "HmacSHA512");
    }

    /**
     * Generates a SCRAM credential with specified mechanism and algorithms.
     */
    private ScramCredential generate(String password, int iterations, ScramMechanism mechanism,
                                     String hashAlgorithm, String hmacAlgorithm) {
        try {
            // Step 1: Generate random salt
            byte[] salt = generateSalt();

            // Step 2: Compute SaltedPassword using PBKDF2
            byte[] saltedPassword = pbkdf2(password, salt, iterations, hashAlgorithm);

            // Step 3: Compute ClientKey = HMAC(SaltedPassword, "Client Key")
            byte[] clientKey = hmac(saltedPassword, CLIENT_KEY_TEXT, hmacAlgorithm);

            // Step 4: Compute StoredKey = H(ClientKey)
            byte[] storedKey = hash(clientKey, hashAlgorithm);

            // Step 5: Compute ServerKey = HMAC(SaltedPassword, "Server Key")
            byte[] serverKey = hmac(saltedPassword, SERVER_KEY_TEXT, hmacAlgorithm);

            // Encode all values to Base64
            String storedKeyBase64 = Base64.getEncoder().encodeToString(storedKey);
            String serverKeyBase64 = Base64.getEncoder().encodeToString(serverKey);
            String saltBase64 = Base64.getEncoder().encodeToString(salt);

            return new ScramCredential(mechanism, storedKeyBase64, serverKeyBase64, saltBase64, iterations);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new ScramGenerationException("Failed to generate SCRAM credential", e);
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     *
     * @return random salt bytes
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        return salt;
    }

    /**
     * Computes PBKDF2 (Password-Based Key Derivation Function 2) hash.
     *
     * @param password      the password to hash
     * @param salt          the salt
     * @param iterations    the number of iterations
     * @param hashAlgorithm the hash algorithm (SHA-256 or SHA-512)
     * @return the derived key
     */
    private byte[] pbkdf2(String password, byte[] salt, int iterations, String hashAlgorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        String pbkdf2Algorithm = "PBKDF2WithHmac" + hashAlgorithm.replace("-", "");
        SecretKeyFactory factory = SecretKeyFactory.getInstance(pbkdf2Algorithm);

        // Key length should match the hash algorithm output size
        int keyLength = hashAlgorithm.equals("SHA-256") ? 256 : 512;

        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
        try {
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Computes HMAC (Hash-based Message Authentication Code).
     *
     * @param key           the key
     * @param message       the message to authenticate
     * @param hmacAlgorithm the HMAC algorithm (HmacSHA256 or HmacSHA512)
     * @return the HMAC result
     */
    private byte[] hmac(byte[] key, String message, String hmacAlgorithm)
            throws NoSuchAlgorithmException, InvalidKeyException {

        Mac mac = Mac.getInstance(hmacAlgorithm);
        SecretKeySpec keySpec = new SecretKeySpec(key, hmacAlgorithm);
        mac.init(keySpec);
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes a cryptographic hash.
     *
     * @param data          the data to hash
     * @param hashAlgorithm the hash algorithm (SHA-256 or SHA-512)
     * @return the hash result
     */
    private byte[] hash(byte[] data, String hashAlgorithm) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
        return digest.digest(data);
    }

    /**
     * Exception thrown when SCRAM credential generation fails.
     */
    public static class ScramGenerationException extends RuntimeException {
        public ScramGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
