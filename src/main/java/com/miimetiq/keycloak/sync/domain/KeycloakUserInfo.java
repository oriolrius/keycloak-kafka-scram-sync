package com.miimetiq.keycloak.sync.domain;

import java.util.Objects;

/**
 * Represents essential user information fetched from Keycloak.
 * <p>
 * Contains the minimal set of fields needed for reconciliation with Kafka SCRAM credentials.
 * This is an immutable value object that represents a snapshot of user data at fetch time.
 */
public class KeycloakUserInfo {

    private final String id;
    private final String username;
    private final String email;
    private final boolean enabled;
    private final Long createdTimestamp;

    /**
     * Creates a new Keycloak user info object.
     *
     * @param id               the unique user ID in Keycloak (UUID)
     * @param username         the username (used as Kafka principal)
     * @param email            the user's email address (may be null)
     * @param enabled          whether the user account is enabled
     * @param createdTimestamp the user creation timestamp in milliseconds (may be null)
     */
    public KeycloakUserInfo(String id, String username, String email, boolean enabled, Long createdTimestamp) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.email = email; // Email can be null
        this.enabled = enabled;
        this.createdTimestamp = createdTimestamp; // Can be null for legacy users
    }

    /**
     * Gets the unique user ID from Keycloak.
     *
     * @return the user ID (UUID string)
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the username.
     * <p>
     * This value is used as the Kafka SCRAM principal name.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the user's email address.
     *
     * @return the email address, or null if not set
     */
    public String getEmail() {
        return email;
    }

    /**
     * Checks if the user account is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the user creation timestamp.
     *
     * @return the creation timestamp in milliseconds since epoch, or null if not available
     */
    public Long getCreatedTimestamp() {
        return createdTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeycloakUserInfo that = (KeycloakUserInfo) o;
        return enabled == that.enabled &&
                Objects.equals(id, that.id) &&
                Objects.equals(username, that.username) &&
                Objects.equals(email, that.email) &&
                Objects.equals(createdTimestamp, that.createdTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, email, enabled, createdTimestamp);
    }

    @Override
    public String toString() {
        return "KeycloakUserInfo{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", enabled=" + enabled +
                ", createdTimestamp=" + createdTimestamp +
                '}';
    }
}
