package com.miimetiq.keycloak.sync.domain;

import com.miimetiq.keycloak.sync.domain.enums.ScramMechanism;

import java.util.Objects;

/**
 * Represents a SCRAM credential containing the cryptographic elements required
 * for SCRAM authentication as defined in RFC 5802.
 * <p>
 * Contains the stored key, server key, salt, and iteration count in Base64 format,
 * ready to be stored in Kafka's credential store.
 */
public class ScramCredential {

    private final ScramMechanism mechanism;
    private final String storedKey;
    private final String serverKey;
    private final String salt;
    private final int iterations;

    /**
     * Creates a new SCRAM credential.
     *
     * @param mechanism  the SCRAM mechanism (SHA-256 or SHA-512)
     * @param storedKey  the stored key in Base64 format
     * @param serverKey  the server key in Base64 format
     * @param salt       the salt in Base64 format
     * @param iterations the number of PBKDF2 iterations used
     */
    public ScramCredential(ScramMechanism mechanism, String storedKey, String serverKey,
                          String salt, int iterations) {
        this.mechanism = Objects.requireNonNull(mechanism, "mechanism must not be null");
        this.storedKey = Objects.requireNonNull(storedKey, "storedKey must not be null");
        this.serverKey = Objects.requireNonNull(serverKey, "serverKey must not be null");
        this.salt = Objects.requireNonNull(salt, "salt must not be null");
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        this.iterations = iterations;
    }

    public ScramMechanism getMechanism() {
        return mechanism;
    }

    public String getStoredKey() {
        return storedKey;
    }

    public String getServerKey() {
        return serverKey;
    }

    public String getSalt() {
        return salt;
    }

    public int getIterations() {
        return iterations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScramCredential that = (ScramCredential) o;
        return iterations == that.iterations &&
                mechanism == that.mechanism &&
                Objects.equals(storedKey, that.storedKey) &&
                Objects.equals(serverKey, that.serverKey) &&
                Objects.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mechanism, storedKey, serverKey, salt, iterations);
    }

    @Override
    public String toString() {
        return "ScramCredential{" +
                "mechanism=" + mechanism +
                ", storedKey='[REDACTED]'" +
                ", serverKey='[REDACTED]'" +
                ", salt='" + salt + '\'' +
                ", iterations=" + iterations +
                '}';
    }
}
