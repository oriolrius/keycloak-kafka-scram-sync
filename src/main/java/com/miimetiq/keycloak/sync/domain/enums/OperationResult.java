package com.miimetiq.keycloak.sync.domain.enums;

/**
 * Enumeration of possible results for a synchronization operation.
 * Indicates whether the operation completed successfully, failed, or was skipped.
 */
public enum OperationResult {
    /**
     * The operation completed successfully.
     */
    SUCCESS,

    /**
     * The operation encountered an error and failed.
     */
    ERROR,

    /**
     * The operation was skipped (e.g., no changes needed, preconditions not met).
     */
    SKIPPED
}
