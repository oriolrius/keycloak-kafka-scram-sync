---
id: decision-003
title: 'Direct Kafka SPI Architecture: Eliminating Webhook and Cache Dependencies'
date: '2025-11-09'
status: accepted
supersedes: decision-002
---

# Direct Kafka SPI Architecture: Eliminating Webhook and Cache Dependencies

## Table of Contents
- [Executive Summary](#executive-summary)
- [Context and Problem](#context-and-problem)
- [Decision](#decision)
- [Architecture Comparison](#architecture-comparison)
- [Rationale](#rationale)
- [Trade-offs](#trade-offs)
- [Implementation Details](#implementation-details)
- [Failure Scenarios](#failure-scenarios)
- [Migration Path](#migration-path)
- [References](#references)

---

## Executive Summary

**Decision**: Migrate from webhook/cache-based password synchronization to direct Kafka synchronization within the Keycloak SPI.

**Impact**:
- Password changes sync to Kafka **immediately** (real-time)
- Eliminates webhook endpoint and password cache
- Simplifies architecture (removes 2-hop network dependency)
- Scheduled reconciliation becomes manual-only safety net
- Kafka availability now critical for password changes

**Status**: Implemented in `feature/direct-kafka-spi` branch

---

## Context and Problem

### Original Architecture (decision-002)

The initial implementation used a two-component architecture:

```
┌──────────────┐
│   Keycloak   │
│     SPI      │
└──────┬───────┘
       │ HTTP POST (webhook)
       ▼
┌──────────────┐
│  Sync-Agent  │
│   Password   │
│    Cache     │
└──────┬───────┘
       │ (periodic reconciliation)
       ▼
┌──────────────┐
│    Kafka     │
│  AdminClient │
└──────────────┘
```

**Flow**:
1. Keycloak SPI intercepts password during hash operation
2. SPI sends password to sync-agent webhook endpoint via HTTP
3. Sync-agent stores password in memory cache (ConcurrentHashMap)
4. Scheduled reconciliation (every 120s) retrieves passwords from cache
5. Reconciliation creates SCRAM credentials in Kafka

### Problems with Original Approach

1. **Eventual Consistency**: Password changes not immediately available in Kafka
   - Users must wait up to 120 seconds for credentials to sync
   - Cache expiration could lose passwords if reconciliation fails

2. **Architectural Complexity**:
   - Two network hops: Keycloak → Sync-Agent → Kafka
   - Additional failure point (webhook endpoint)
   - In-memory cache requires careful memory management

3. **Security Concerns**:
   - Passwords stored in memory cache for extended periods
   - Network transmission of plaintext passwords (even in local network)
   - Cache could grow unbounded with high user creation rates

4. **Operational Issues**:
   - Webhook endpoint configuration required
   - Network connectivity between Keycloak and sync-agent
   - Cache invalidation and cleanup complexity

---

## Decision

**Adopt direct Kafka synchronization from within Keycloak SPI**

### New Architecture

```
┌──────────────────────────────────────┐
│           Keycloak SPI               │
│                                      │
│  ┌─────────────────────────────┐    │
│  │  PasswordHashProvider       │    │
│  │  (intercepts password)      │    │
│  └────────────┬────────────────┘    │
│               │ ThreadLocal          │
│               ▼                      │
│  ┌─────────────────────────────┐    │
│  │  EventListener              │    │
│  │  (retrieves password)       │    │
│  └────────────┬────────────────┘    │
│               │                      │
│               │ Kafka AdminClient    │
│               │ (direct)             │
└───────────────┼──────────────────────┘
                ▼
         ┌──────────────┐
         │    Kafka     │
         │   SCRAM      │
         │ Credentials  │
         └──────────────┘
```

### Key Changes

1. **SPI directly communicates with Kafka**: No intermediate webhook
2. **Immediate sync**: Password changes reflected in Kafka instantly
3. **No password cache**: ThreadLocal clears after each request
4. **Simplified deployment**: Only Keycloak needs Kafka connectivity
5. **Reconciliation optional**: Disabled by default, manual trigger only

---

## Rationale

### Why Direct Kafka Sync?

#### 1. **Real-Time Consistency**
- **Before**: Eventual consistency with up to 120-second lag
- **After**: Immediate consistency - password change and Kafka sync are atomic
- **Benefit**: Users can authenticate to Kafka immediately after password change

#### 2. **Simplified Architecture**
- **Removed**: Webhook endpoint, password cache, HTTP client configuration
- **Result**: Fewer moving parts, less configuration, easier to reason about
- **Benefit**: Reduced operational complexity and maintenance burden

#### 3. **Better Security**
- **Before**: Passwords in memory cache for up to 120 seconds
- **After**: Passwords only in ThreadLocal for duration of single request
- **Benefit**: Minimal password exposure window (milliseconds vs. minutes)

#### 4. **Reduced Network Dependencies**
- **Before**: Keycloak → Sync-Agent (HTTP) → Kafka (Kafka protocol)
- **After**: Keycloak → Kafka (Kafka protocol, direct)
- **Benefit**: One network hop instead of two, fewer failure points

#### 5. **Predictable Behavior**
- **Before**: Async with timing dependencies, cache eviction concerns
- **After**: Synchronous operation with immediate error feedback
- **Benefit**: Easier troubleshooting, clearer failure modes

---

## Trade-offs

### Advantages ✅

| Aspect | Benefit |
|--------|---------|
| **Latency** | Immediate sync (milliseconds) vs. eventual (up to 120s) |
| **Simplicity** | Single component vs. two components with webhook |
| **Security** | Minimal password exposure (request duration only) |
| **Reliability** | Direct operation vs. multi-hop with caching |
| **Operations** | Less configuration, fewer services to monitor |
| **Testing** | Simpler E2E tests (no reconciliation wait) |

### Disadvantages ⚠️

| Aspect | Trade-off | Mitigation |
|--------|-----------|------------|
| **Kafka Availability** | Password changes fail if Kafka is down | Manual reconciliation available as recovery, Kafka SLA monitoring |
| **SPI Complexity** | More code in Keycloak SPI | Well-tested, isolated Kafka client library |
| **Network Dependency** | Keycloak directly depends on Kafka | Acceptable - Kafka is core infrastructure |
| **Error Handling** | User sees Kafka errors during password change | Clear error messages, operational runbooks |

---

## Implementation Details

### SPI Components

#### 1. PasswordHashProvider
- Intercepts password before hashing
- Stores plaintext in ThreadLocal (`PasswordCorrelationContext`)
- Performs PBKDF2-SHA256 hashing as usual

#### 2. EventListener
- Triggered on `UPDATE_PASSWORD` admin event
- Retrieves password from ThreadLocal
- Obtains username from Keycloak (via userId in event)
- Calls `KafkaScramSync.syncPasswordToKafka(username, password)`
- Clears ThreadLocal after sync

#### 3. KafkaScramSync
- Creates Kafka AdminClient (connection pooling)
- Generates SCRAM-SHA-256 credentials
- Uses `AlterUserScramCredentialsOptions` API
- Handles errors and logs appropriately

### Configuration

New environment variables for Keycloak SPI:

```bash
# Kafka connection (required)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Optional: Authentication
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_USERNAME=admin
KAFKA_SASL_PASSWORD=admin-secret

# Optional: Performance tuning
KAFKA_DEFAULT_API_TIMEOUT_MS=60000
KAFKA_REQUEST_TIMEOUT_MS=30000
```

### Sync-Agent Changes

Components removed:
- `PasswordWebhookResource.java` - REST endpoint
- Password cache from `ReconciliationService`
- Webhook-related configuration

Components modified:
- `ReconcileConfig.schedulerEnabled()` - default changed to `false`
- `ReconciliationService` - simplified to use random passwords only

Components kept:
- Manual reconciliation trigger (safety net)
- Sync history tracking
- Metrics and monitoring

---

## Failure Scenarios

### Scenario 1: Kafka Temporarily Unavailable

**Situation**: Kafka cluster is down or unreachable during password change

**Behavior**:
- Password change operation **fails** in Keycloak
- User receives error message
- Keycloak password is **not updated** (transaction rollback)

**Recovery**:
1. Fix Kafka connectivity
2. User retries password change
3. Operation succeeds, Kafka credential created

**Alternative**:
- Run manual reconciliation after Kafka recovery
- Reconciliation creates credentials for users missing them in Kafka

### Scenario 2: Kafka Slow Response

**Situation**: Kafka AdminClient operation takes longer than timeout

**Behavior**:
- EventListener logs timeout warning
- Password change may still succeed in Keycloak (depends on timing)
- Kafka credential creation uncertain

**Recovery**:
- Check Kafka logs to verify credential creation
- Run manual reconciliation if needed
- Tune `KAFKA_REQUEST_TIMEOUT_MS` if systematic

### Scenario 3: Network Partition

**Situation**: Network split between Keycloak and Kafka

**Behavior**:
- Same as Scenario 1 (Kafka unavailable)
- Password changes blocked until connectivity restored

**Recovery**:
- Restore network connectivity
- Users retry password changes
- Or run manual reconciliation

### Scenario 4: Kafka Authentication Failure

**Situation**: Keycloak SPI has incorrect Kafka credentials

**Behavior**:
- All password changes fail with authentication error
- Error logged in Keycloak logs

**Recovery**:
1. Fix KAFKA_SASL_USERNAME/PASSWORD environment variables
2. Restart Keycloak to reload configuration
3. Verify connectivity with manual test
4. Run manual reconciliation for any missed passwords

---

## Migration Path

### Phase 1: Implement Direct Kafka Sync (task-063)
- ✅ Add Kafka AdminClient to Keycloak SPI
- ✅ Implement KafkaScramSync service
- ✅ Restore ThreadLocal password correlation
- ✅ Update EventListener to call Kafka directly
- ✅ Test E2E with direct sync

### Phase 2: Update E2E Tests (task-064)
- ✅ Remove reconciliation trigger from tests
- ✅ Remove wait periods (test immediate sync)
- ✅ Update test documentation
- ✅ Verify all tests pass

### Phase 3: Clean Up Sync-Agent (task-065)
- ✅ Remove PasswordWebhookResource
- ✅ Remove password cache from ReconciliationService
- ✅ Disable scheduled reconciliation (default: false)
- ✅ Keep manual reconciliation as safety net

### Phase 4: Documentation (task-066)
- ✅ Create this architecture decision record
- 🔄 Update README with new architecture
- 🔄 Document new environment variables
- 🔄 Update deployment guides

---

## References

### Related Decisions
- **decision-002**: SCRAM Password Interception Technical Implementation
  - Describes ThreadLocal correlation mechanism
  - Explains PasswordHashProvider and EventListener SPIs
  - Documents original webhook/cache architecture

### Related Tasks
- **task-063**: Modify Keycloak SPI to sync directly to Kafka
- **task-064**: Adapt E2E tests for direct SPI architecture
- **task-065**: Remove sync-agent components not needed for direct SPI
- **task-066**: Document direct Kafka SPI architecture decision (this document)

### Technical Documentation
- Kafka AdminClient API: https://kafka.apache.org/documentation/#adminapi
- Keycloak SPI Development: https://www.keycloak.org/docs/latest/server_development/
- SCRAM-SHA-256 RFC: https://tools.ietf.org/html/rfc7677

---

## Conclusion

The migration to direct Kafka synchronization represents a significant architectural improvement:

**Gains**:
- ✅ Real-time consistency
- ✅ Simplified architecture
- ✅ Better security
- ✅ Fewer failure points
- ✅ Easier operations

**Trade-offs**:
- ⚠️ Kafka availability critical for password changes
- ⚠️ Network dependency from Keycloak to Kafka

The trade-off is acceptable because:
1. Kafka is already critical infrastructure (users can't authenticate without it)
2. Immediate error feedback is better than silent cache failures
3. Operational simplicity outweighs additional network dependency
4. Manual reconciliation provides recovery mechanism

**Recommendation**: Adopt direct Kafka SPI architecture as the standard approach for password synchronization.
