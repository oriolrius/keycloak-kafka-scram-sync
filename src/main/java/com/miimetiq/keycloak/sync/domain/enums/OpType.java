package com.miimetiq.keycloak.sync.domain.enums;

/**
 * Enumeration of operation types for synchronization between Keycloak and Kafka.
 * These types represent the different kinds of operations that can be performed
 * during the synchronization process.
 */
public enum OpType {
    /**
     * Create or update a SCRAM credential for a user in Kafka.
     */
    SCRAM_UPSERT,

    /**
     * Delete a SCRAM credential for a user from Kafka.
     */
    SCRAM_DELETE,

    /**
     * Create an ACL entry in Kafka.
     */
    ACL_CREATE,

    /**
     * Delete an ACL entry from Kafka.
     */
    ACL_DELETE
}
