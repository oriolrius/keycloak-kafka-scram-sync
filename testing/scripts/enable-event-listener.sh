#!/usr/bin/env bash
set -e

# Enable the password-sync-listener event listener in Keycloak

KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:57003}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-The2password.}"
REALM="${REALM:-master}"

echo "Enabling password-sync-listener in Keycloak..."

# Get admin token
TOKEN=$(curl -k -sf -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$KEYCLOAK_ADMIN" \
  -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  -d 'grant_type=password' \
  -d 'client_id=admin-cli' | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Failed to get admin token"
  exit 1
fi

# Get current realm configuration
REALM_CONFIG=$(curl -k -sf -X GET "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN")

# Update realm to enable event listener
curl -k -sf -X PUT "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$(echo "$REALM_CONFIG" | jq '.eventsListeners += ["password-sync-listener"] | .eventsListeners |= unique')"

echo "Event listener enabled successfully"
