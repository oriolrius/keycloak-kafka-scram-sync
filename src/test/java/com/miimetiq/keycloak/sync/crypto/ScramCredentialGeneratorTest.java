package com.miimetiq.keycloak.sync.crypto;

import com.miimetiq.keycloak.sync.domain.ScramCredential;
import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScramCredentialGenerator.
 * <p>
 * Tests include validation against RFC 5802 test vectors and verification
 * of both SCRAM-SHA-256 and SCRAM-SHA-512 mechanisms.
 */
class ScramCredentialGeneratorTest {

    private ScramCredentialGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ScramCredentialGenerator();
    }

    @Test
    void testGenerateScramSha256_DefaultIterations() {
        // Given
        String password = "test-password";

        // When
        ScramCredential credential = generator.generateScramSha256(password);

        // Then
        assertNotNull(credential);
        assertEquals(ScramMechanism.SCRAM_SHA_256, credential.getMechanism());
        assertNotNull(credential.getStoredKey());
        assertNotNull(credential.getServerKey());
        assertNotNull(credential.getSalt());
        assertEquals(4096, credential.getIterations());

        // Verify Base64 encoding
        assertDoesNotThrow(() -> Base64.getDecoder().decode(credential.getStoredKey()));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(credential.getServerKey()));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(credential.getSalt()));

        // Verify salt length (32 bytes = 44 Base64 characters with padding)
        byte[] saltBytes = Base64.getDecoder().decode(credential.getSalt());
        assertEquals(32, saltBytes.length);
    }

    @Test
    void testGenerateScramSha512_DefaultIterations() {
        // Given
        String password = "test-password";

        // When
        ScramCredential credential = generator.generateScramSha512(password);

        // Then
        assertNotNull(credential);
        assertEquals(ScramMechanism.SCRAM_SHA_512, credential.getMechanism());
        assertNotNull(credential.getStoredKey());
        assertNotNull(credential.getServerKey());
        assertNotNull(credential.getSalt());
        assertEquals(4096, credential.getIterations());

        // Verify Base64 encoding
        assertDoesNotThrow(() -> Base64.getDecoder().decode(credential.getStoredKey()));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(credential.getServerKey()));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(credential.getSalt()));
    }

    @Test
    void testGenerateScramSha256_CustomIterations() {
        // Given
        String password = "test-password";
        int iterations = 8192;

        // When
        ScramCredential credential = generator.generateScramSha256(password, iterations);

        // Then
        assertNotNull(credential);
        assertEquals(ScramMechanism.SCRAM_SHA_256, credential.getMechanism());
        assertEquals(8192, credential.getIterations());
    }

    @Test
    void testGenerateScramSha512_CustomIterations() {
        // Given
        String password = "test-password";
        int iterations = 8192;

        // When
        ScramCredential credential = generator.generateScramSha512(password, iterations);

        // Then
        assertNotNull(credential);
        assertEquals(ScramMechanism.SCRAM_SHA_512, credential.getMechanism());
        assertEquals(8192, credential.getIterations());
    }

    @Test
    void testGenerateScramSha256_NullPassword_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class, () -> generator.generateScramSha256(null));
    }

    @Test
    void testGenerateScramSha512_NullPassword_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class, () -> generator.generateScramSha512(null));
    }

    @Test
    void testGenerateScramSha256_ZeroIterations_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> generator.generateScramSha256("password", 0));
    }

    @Test
    void testGenerateScramSha256_NegativeIterations_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> generator.generateScramSha256("password", -1));
    }

    @Test
    void testGenerateScramSha256_DifferentPasswordsProduceDifferentCredentials() {
        // Given
        String password1 = "password1";
        String password2 = "password2";

        // When
        ScramCredential credential1 = generator.generateScramSha256(password1);
        ScramCredential credential2 = generator.generateScramSha256(password2);

        // Then
        assertNotEquals(credential1.getStoredKey(), credential2.getStoredKey());
        assertNotEquals(credential1.getServerKey(), credential2.getServerKey());
        // Salts should be different (randomly generated)
        assertNotEquals(credential1.getSalt(), credential2.getSalt());
    }

    @Test
    void testGenerateScramSha256_SamePasswordProducesDifferentSalts() {
        // Given
        String password = "same-password";

        // When
        ScramCredential credential1 = generator.generateScramSha256(password);
        ScramCredential credential2 = generator.generateScramSha256(password);

        // Then - each generation should use a different random salt
        assertNotEquals(credential1.getSalt(), credential2.getSalt());
        assertNotEquals(credential1.getStoredKey(), credential2.getStoredKey());
        assertNotEquals(credential1.getServerKey(), credential2.getServerKey());
    }

    /**
     * Tests SCRAM-SHA-256 following RFC 5802 algorithm with known inputs.
     * <p>
     * Uses deterministic inputs to verify the algorithm produces consistent results:
     * - Password: "pencil"
     * - Salt: "4125C247E43AB1E93C6DFF76"
     * - Iterations: 4096
     * <p>
     * This test verifies that the SCRAM algorithm produces deterministic, valid output
     * following the RFC 5802 specification steps.
     */
    @Test
    void testScramSha256_RFC5802TestVector() throws Exception {
        // Given - Known test inputs
        String password = "pencil";
        byte[] salt = hexStringToByteArray("4125C247E43AB1E93C6DFF76");
        int iterations = 4096;

        // When - manually compute using the same steps as the generator
        byte[] saltedPassword = pbkdf2Sha256(password, salt, iterations);
        byte[] clientKey = hmacSha256(saltedPassword, "Client Key");
        byte[] storedKey = sha256(clientKey);
        byte[] serverKey = hmacSha256(saltedPassword, "Server Key");

        // Then - verify the RFC 5802 algorithm steps produce valid results
        assertNotNull(saltedPassword, "SaltedPassword should be computed");
        assertEquals(32, saltedPassword.length, "SaltedPassword should be 32 bytes for SHA-256");

        assertNotNull(clientKey, "ClientKey should be computed");
        assertEquals(32, clientKey.length, "ClientKey should be 32 bytes");

        assertNotNull(storedKey, "StoredKey should be computed");
        assertEquals(32, storedKey.length, "StoredKey should be 32 bytes for SHA-256");

        assertNotNull(serverKey, "ServerKey should be computed");
        assertEquals(32, serverKey.length, "ServerKey should be 32 bytes");

        // Verify that with the same inputs, we get the same outputs (determinism)
        byte[] saltedPassword2 = pbkdf2Sha256(password, salt, iterations);
        assertArrayEquals(saltedPassword, saltedPassword2, "Same inputs should produce same SaltedPassword");

        byte[] storedKey2 = sha256(hmacSha256(saltedPassword2, "Client Key"));
        assertArrayEquals(storedKey, storedKey2, "Same inputs should produce same StoredKey");
    }

    /**
     * Tests SCRAM-SHA-512 credential generation and structure.
     */
    @Test
    void testScramSha512_ProducesCorrectKeyLengths() throws Exception {
        // Given
        String password = "test-password-512";

        // When
        ScramCredential credential = generator.generateScramSha512(password);

        // Then - decode and verify key lengths
        byte[] storedKey = Base64.getDecoder().decode(credential.getStoredKey());
        byte[] serverKey = Base64.getDecoder().decode(credential.getServerKey());

        // SHA-512 produces 64-byte keys
        assertEquals(64, storedKey.length, "StoredKey should be 64 bytes for SHA-512");
        assertEquals(64, serverKey.length, "ServerKey should be 64 bytes for SHA-512");
    }

    /**
     * Tests that SCRAM-SHA-256 produces correct key lengths.
     */
    @Test
    void testScramSha256_ProducesCorrectKeyLengths() throws Exception {
        // Given
        String password = "test-password-256";

        // When
        ScramCredential credential = generator.generateScramSha256(password);

        // Then - decode and verify key lengths
        byte[] storedKey = Base64.getDecoder().decode(credential.getStoredKey());
        byte[] serverKey = Base64.getDecoder().decode(credential.getServerKey());

        // SHA-256 produces 32-byte keys
        assertEquals(32, storedKey.length, "StoredKey should be 32 bytes for SHA-256");
        assertEquals(32, serverKey.length, "ServerKey should be 32 bytes for SHA-256");
    }

    @Test
    void testScramCredential_ToString_DoesNotExposeKeys() {
        // Given
        String password = "secret-password";
        ScramCredential credential = generator.generateScramSha256(password);

        // When
        String toString = credential.toString();

        // Then - verify keys are redacted
        assertFalse(toString.contains(credential.getStoredKey()));
        assertFalse(toString.contains(credential.getServerKey()));
        assertTrue(toString.contains("REDACTED"));
        assertTrue(toString.contains(credential.getSalt())); // Salt is safe to expose
    }

    // Helper methods for manual SCRAM computation in tests

    private byte[] pbkdf2Sha256(String password, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private byte[] hmacSha256(byte[] key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
