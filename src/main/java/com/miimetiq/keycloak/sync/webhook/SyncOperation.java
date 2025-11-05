package com.miimetiq.keycloak.sync.webhook;

/**
 * Represents a synchronization operation to be performed.
 * <p>
 * Mapped from Keycloak admin events to internal sync operations.
 */
public class SyncOperation {

    public enum Type {
        UPSERT,  // Create or update SCRAM credentials
        DELETE   // Delete SCRAM credentials
    }

    private final Type type;
    private final String realm;
    private final String principal;
    private final boolean isPasswordChange;

    public SyncOperation(Type type, String realm, String principal, boolean isPasswordChange) {
        this.type = type;
        this.realm = realm;
        this.principal = principal;
        this.isPasswordChange = isPasswordChange;
    }

    public Type getType() {
        return type;
    }

    public String getRealm() {
        return realm;
    }

    public String getPrincipal() {
        return principal;
    }

    public boolean isPasswordChange() {
        return isPasswordChange;
    }

    @Override
    public String toString() {
        return "SyncOperation{" +
                "type=" + type +
                ", realm='" + realm + '\'' +
                ", principal='" + principal + '\'' +
                ", isPasswordChange=" + isPasswordChange +
                '}';
    }
}
