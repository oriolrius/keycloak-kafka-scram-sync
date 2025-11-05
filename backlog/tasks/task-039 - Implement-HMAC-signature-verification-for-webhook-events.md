---
id: task-039
title: Implement HMAC signature verification for webhook events
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:28'
labels:
  - sprint-4
  - webhook
  - security
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add security validation for incoming webhook events by verifying HMAC signatures. Keycloak sends a signature header, and the service must validate it using the configured secret (KC_WEBHOOK_HMAC_SECRET) to prevent unauthorized event injection.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 HMAC-SHA256 signature verification implemented
- [x] #2 Configuration supports KC_WEBHOOK_HMAC_SECRET environment variable
- [x] #3 Invalid signatures return 401 Unauthorized
- [x] #4 Missing signature header returns 401 Unauthorized
- [x] #5 Valid signatures allow event processing to proceed
- [x] #6 Unit tests cover signature validation logic
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing KeycloakConfig - webhookHmacSecret() already exists
2. Create WebhookSignatureValidator service for HMAC-SHA256 verification
3. Implement signature validation logic with proper error handling
4. Update KeycloakWebhookResource to verify X-Keycloak-Signature header
5. Return 401 Unauthorized for missing or invalid signatures
6. Create comprehensive unit tests for signature validation scenarios
7. Create integration tests with valid/invalid signatures
8. Run all tests and verify acceptance criteria
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Summary

Implemented HMAC-SHA256 signature verification for webhook events to prevent unauthorized event injection and ensure webhook authenticity.

## Changes

### New Files Created

1. **WebhookSignatureValidator.java** - Service for validating HMAC-SHA256 signatures
   - Computes expected signature using configured secret
   - Performs timing-safe comparison to prevent timing attacks
   - Handles missing/invalid signatures with appropriate error messages
   - Supports optional configuration (disabled when secret not set)

2. **WebhookSignatureValidatorTest.java** - Comprehensive unit tests (12 test cases)

3. **WebhookSignatureIntegrationTest.java** - Integration tests with signature validation enabled (6 test cases)

### Modified Files

1. **KeycloakWebhookResource.java** - Enhanced with signature verification
   - Added signature validation before event processing
   - Changed method signature to accept raw String payload for validation
   - Manually deserialize JSON after signature validation
   - Return 401 Unauthorized for invalid/missing signatures
   - Maintain backward compatibility when secret not configured

2. **KeycloakConfig.java** - Already had webhookHmacSecret() configuration (no changes needed)

### Key Features

- **HMAC-SHA256 signature verification** using javax.crypto.Mac
- **X-Keycloak-Signature header validation** from incoming requests
- **Timing-safe comparison** using MessageDigest.isEqual() to prevent timing attacks
- **Optional configuration** - signature validation disabled when KC_WEBHOOK_HMAC_SECRET not set
- **Proper HTTP status codes** - 401 Unauthorized for auth failures, 400 Bad Request for malformed payloads
- **Signature masking in logs** for security (shows only first/last 4 characters)

### Testing

Created comprehensive test coverage:

**Unit Tests (12 tests):**
- Valid signature acceptance
- Invalid signature rejection
- Missing signature header handling
- Null payload validation
- Unconfigured secret bypass
- Different payloads produce different signatures
- Cross-payload signature validation
- Different secrets produce different signatures
- Case sensitivity validation
- Empty payload handling

**Integration Tests (6 tests with signature enabled):**
- Valid signature allows processing
- Invalid signature returns 401
- Missing signature returns 401
- Tampered payload detection
- Wrong secret rejection
- Empty signature header handling

**Existing Tests (10 tests):**
- All existing webhook tests continue to pass
- Signature validation gracefully disabled when not configured

All 28 tests pass successfully.

## Technical Details

- Follows existing codebase patterns (CDI beans, Logger, validation patterns)
- Configuration already existed in KeycloakConfig via keycloak.webhook-hmac-secret property
- Can be configured via KC_WEBHOOK_HMAC_SECRET environment variable
- Uses standard Java cryptography libraries (javax.crypto)
- Hex encoding/decoding via HexFormat (Java 17+)
- Backward compatible - existing deployments without HMAC secret continue to work

## Security Considerations

- Timing-safe comparison prevents timing attack vectors
- Signature validation occurs before JSON deserialization
- Logs mask sensitive signature data
- Properly handles edge cases (null, empty, malformed data)
<!-- SECTION:NOTES:END -->
