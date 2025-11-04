package com.miimetiq.keycloak.sync.domain.enums;

/**
 * Enumeration of SCRAM (Salted Challenge Response Authentication Mechanism) types
 * supported for Kafka authentication. These correspond to the SCRAM mechanisms
 * available in Kafka.
 */
public enum ScramMechanism {
    /**
     * SCRAM-SHA-256 authentication mechanism.
     * Uses SHA-256 for password hashing.
     */
    SCRAM_SHA_256,

    /**
     * SCRAM-SHA-512 authentication mechanism.
     * Uses SHA-512 for password hashing (more secure than SHA-256).
     */
    SCRAM_SHA_512
}
