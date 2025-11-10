# E2E Tests: Keycloak → Kafka SCRAM Synchronization

This directory contains end-to-end tests that verify the complete flow of password synchronization from Keycloak to Kafka SCRAM credentials.

## Table of Contents

- [Overview](#overview)
- [Test Architecture](#test-architecture)
- [Running the Tests](#running-the-tests)
- [Understanding the Test Flow](#understanding-the-test-flow)
- [About Transient Errors (Don't Worry!)](#about-transient-errors-dont-worry)
- [Log Parsing for Consumer Group Readiness](#log-parsing-for-consumer-group-readiness)
- [Files Description](#files-description)
- [Troubleshooting](#troubleshooting)

---

## Overview

The E2E tests validate the entire password synchronization pipeline:

1. **Keycloak**: User password is set via Admin API
2. **SPI**: Keycloak Password Sync SPI intercepts the password event
3. **Kafka**: SPI creates SCRAM credentials directly in Kafka
4. **Verification**: Kafka producer and consumer authenticate using the synced credentials

Tests are run for both **SCRAM-SHA-256** and **SCRAM-SHA-512** mechanisms.

---

## Test Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         test-both-mechanisms.sh                      │
│  Orchestrates both SCRAM-SHA-256 and SCRAM-SHA-512 test scenarios   │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ├─── SCENARIO 1: SCRAM-SHA-256
                              │    ├── Build SPI
                              │    ├── Start services (Keycloak, Kafka, KMS)
                              │    ├── Enable event listener
                              │    └── Run: scram-sync-e2e.test.js
                              │
                              └─── SCENARIO 2: SCRAM-SHA-512
                                   ├── Cleanup & restart services
                                   └── Run: scram-sync-e2e.test.js


┌─────────────────────────────────────────────────────────────────────┐
│                        scram-sync-e2e.test.js                        │
│              Single test file, mechanism selected by env var         │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ├── STEP 1: Authenticate to Keycloak
                              │    └── Get admin token via REST API
                              │
                              ├── STEP 2: Create User & Set Password
                              │    ├── Create user via Admin API
                              │    ├── Set password (triggers SPI)
                              │    └── Wait 2 seconds for sync
                              │
                              ├── STEP 3: Test Producer
                              │    ├── Wait for Kafka cluster ready
                              │    ├── Create topic with leader election
                              │    ├── Authenticate with SCRAM
                              │    └── Publish message
                              │
                              └── STEP 4: Test Consumer
                                   ├── Wait for Kafka cluster ready
                                   ├── Authenticate with SCRAM
                                   ├── Subscribe to topic
                                   ├── Wait for consumer group join ⭐
                                   └── Consume message
```

---

## Running the Tests

### Prerequisites

- Docker and Docker Compose installed
- Node.js 18+ installed
- Maven for building the SPI
- Ports available: 57001 (KMS), 57003 (Keycloak), 57005 (Kafka)

### Run All Tests

From the `e2e` directory:

```bash
./test-both-mechanisms.sh
```

This script:
1. Builds the Keycloak SPI
2. Cleans up any existing services and data
3. Runs SCRAM-SHA-256 scenario
4. Cleans up and restarts services
5. Runs SCRAM-SHA-512 scenario
6. Reports final results

### Run Single Mechanism Test

For testing a specific SCRAM mechanism:

```bash
# SCRAM-SHA-256
export TEST_SCRAM_MECHANISM=256
node scram-sync-e2e.test.js

# SCRAM-SHA-512
export TEST_SCRAM_MECHANISM=512
node scram-sync-e2e.test.js
```

**Note**: Services must already be running with the correct mechanism configured.

### Demonstration: Log Parsing Only

To see just the log parsing mechanism in action:

```bash
node test-log-parsing.js
```

This demonstrates how we detect consumer group readiness without running the full E2E flow.

---

## Understanding the Test Flow

### STEP 1: Authenticate to Keycloak

```
┌──────────┐     POST /realms/master/protocol/openid-connect/token     ┌──────────┐
│   Test   │ ────────────────────────────────────────────────────────> │ Keycloak │
│          │ <──────────────────────────────────────────────────────── │          │
└──────────┘                    Admin Access Token                      └──────────┘
```

Gets an admin access token for subsequent API calls.

### STEP 2: Create User & Set Password

```
┌──────────┐  POST /admin/realms/master/users                ┌──────────┐
│   Test   │ ─────────────────────────────────────────────> │ Keycloak │
│          │                                                  │          │
│          │  PUT /admin/realms/master/users/{id}/reset-pwd  │          │
│          │ ─────────────────────────────────────────────> │    ↓     │
│          │                                                  │  [SPI]   │
│          │                                                  │    ↓     │
│          │                                                  └────┼─────┘
│          │                                                       │
│          │                                          kafka-configs --alter
│          │                                          SCRAM credentials
│          │                                                       │
│          │                                                       ↓
│          │                                                  ┌──────────┐
│          │                                                  │  Kafka   │
└──────────┘                                                  └──────────┘
```

The password reset triggers the SPI, which directly creates SCRAM credentials in Kafka.

### STEP 3: Test Producer

```
┌──────────┐                                                  ┌──────────┐
│ Producer │  1. waitKafkaReady()                             │  Kafka   │
│          │ ─────────────────────────────────────────────> │          │
│          │ <───────────────────────────────────────────── │          │
│          │         describeCluster() → brokers ready        │          │
│          │                                                  │          │
│          │  2. ensureTopicWithLeaders()                     │          │
│          │ ─────────────────────────────────────────────> │          │
│          │ <───────────────────────────────────────────── │          │
│          │         createTopics(waitForLeaders: true)       │          │
│          │                                                  │          │
│          │  3. Connect with SCRAM authentication            │          │
│          │ ─────────────────────────────────────────────> │          │
│          │ <───────────────────────────────────────────── │          │
│          │              SASL handshake OK                   │          │
│          │                                                  │          │
│          │  4. Publish message                              │          │
│          │ ─────────────────────────────────────────────> │          │
└──────────┘                                                  └──────────┘
```

### STEP 4: Test Consumer

```
┌──────────┐                                                  ┌──────────┐
│ Consumer │  1. waitKafkaReady()                             │  Kafka   │
│          │ ─────────────────────────────────────────────> │          │
│          │                                                  │          │
│          │  2. Connect with SCRAM authentication            │          │
│          │ ─────────────────────────────────────────────> │          │
│          │                                                  │          │
│          │  3. Subscribe to topic                           │          │
│          │ ─────────────────────────────────────────────> │          │
│          │                                                  │          │
│          │  4. consumer.run() - triggers group join         │          │
│          │ ─────────────────────────────────────────────> │          │
│          │ <───────────────────────────────────────────── │          │
│          │     ⚠️  "Group coordinator not available"        │          │
│          │                                                  │          │
│          │  5. Retry with backoff (300ms)                   │          │
│          │ ─────────────────────────────────────────────> │          │
│          │ <───────────────────────────────────────────── │          │
│          │     ⚠️  "Coordinator is loading"                 │          │
│          │                                                  │          │
│          │  6. Retry again                                  │          │
│          │ ─────────────────────────────────────────────> │          │
│          │ <───────────────────────────────────────────── │          │
│          │     ✅ "Consumer has joined the group" ⭐        │          │
│          │                                                  │          │
│          │  7. Consume message                              │          │
│          │ <───────────────────────────────────────────── │          │
└──────────┘                                                  └──────────┘
```

---

## About Transient Errors (Don't Worry!)

### ⚠️ Expected ERROR Logs During Consumer Startup

When running the tests, you will see ERROR logs like these:

```json
{"level":"ERROR","timestamp":"...","message":"The group coordinator is not available"}
{"level":"ERROR","timestamp":"...","message":"The coordinator is loading and hence can't process requests"}
{"level":"ERROR","timestamp":"...","message":"Client network socket disconnected before secure TLS connection"}
```

### 🎯 This is COMPLETELY NORMAL and EXPECTED!

Here's why:

#### 1. Consumer Group Initialization Takes Time

When a Kafka consumer first connects, it goes through a multi-step initialization process:

```
Consumer Start
     ↓
Find Group Coordinator
     ↓
Coordinator Election (if needed)  ← Can take 1-3 seconds
     ↓
Join Group Protocol
     ↓
Partition Assignment
     ↓
Consumer Ready ✅
```

#### 2. Race Conditions During Coordinator Election

The **group coordinator** is a Kafka broker responsible for managing consumer group membership. When a new consumer group is created:

1. Kafka must elect a coordinator for this group
2. The coordinator must load group metadata
3. This process is **not instantaneous**

If the consumer tries to join before the coordinator is ready, it gets these errors:
- `"The group coordinator is not available"` - Coordinator not yet elected
- `"The coordinator is loading"` - Coordinator elected but still initializing

#### 3. KafkaJS Handles This Automatically

The KafkaJS library has **built-in retry logic** (configured in the tests):

```javascript
retry: {
  initialRetryTime: 300,  // Wait 300ms before first retry
  retries: 8              // Try up to 8 times
}
```

The library automatically:
1. Catches these errors
2. Waits with exponential backoff
3. Retries the operation
4. Succeeds once the coordinator is ready

#### 4. Tests Still Pass Successfully

Despite the ERROR logs, the tests complete successfully because:

- ✅ Retry logic handles the transient errors
- ✅ Consumer group eventually joins (within 3-5 seconds)
- ✅ Messages are consumed successfully
- ✅ All acceptance criteria are met

### What You Should See

**Normal, successful test output:**

```
⏳ Waiting for consumer group to initialize...
{"level":"ERROR",...,"message":"The group coordinator is not available"}  ← Expected!
{"level":"ERROR",...,"message":"Restarting the consumer in 300ms"}        ← Expected!
{"level":"DEBUG",...,"message":"Consumer has joined the group"}           ← Success!
✅ Consumer group ready
✅ Message received
✅ E2E TEST PASSED
```

### When to Actually Worry

You should investigate if:

❌ Tests fail completely and don't recover
❌ Timeout errors after 15+ seconds
❌ Authentication errors (incorrect credentials)
❌ Connection refused (services not running)

But transient coordinator errors that resolve within a few seconds are **part of normal Kafka consumer group behavior**.

---

## Log Parsing for Consumer Group Readiness

### The Problem

Previously, we used arbitrary delays (`setTimeout`) to wait for consumer initialization:

```javascript
// ❌ Old approach: Guess and wait
await new Promise(resolve => setTimeout(resolve, 5000));
```

**Problems with this approach:**
- ⏱️ Wastes time if consumer is ready sooner
- ❌ May not wait long enough under heavy load
- 🎲 Non-deterministic behavior
- 🐛 Hard to debug timing issues

### The Solution: Event-Driven Waiting

We now use **log parsing** to detect the exact moment when the consumer group is ready:

```javascript
// ✅ New approach: Wait for actual event
const { logCreator, groupReadyPromise } = createConsumerGroupReadyWatcher();

const kafka = new Kafka({
  // ... config
  logCreator  // Custom log handler
});

// Start consumer (triggers initialization)
consumer.run({ ... });

// Wait for the actual "consumer joined group" event
await groupReadyPromise;
console.log(`✅ Consumer group ready`);
```

### How It Works

1. **Custom Log Creator**: We provide a custom log handler to KafkaJS
2. **Event Detection**: The handler watches for the message `"Consumer has joined the group"`
3. **Promise Resolution**: When detected, it resolves a promise
4. **Precise Timing**: Test proceeds immediately when consumer is actually ready

**Code reference:** `scram-sync-e2e.test.js:248-275` (createConsumerGroupReadyWatcher)

### Benefits

- ⚡ **Faster**: No unnecessary waiting
- 🎯 **Precise**: Waits for actual readiness, not a guess
- 🔍 **Visible**: All logs still displayed for debugging
- ✅ **Reliable**: Works regardless of system load

### Log Output Format

Logs are output in JSON format for easy parsing:

```json
{
  "level": "DEBUG",
  "timestamp": "2025-11-10T12:57:21.743Z",
  "logger": "kafkajs",
  "message": "Consumer has joined the group",
  "groupId": "e2e-test-1762779437488",
  "memberId": "e2e-consumer-b472c7e0-1e8f-414a-8c83-c302bc946ebd",
  "isLeader": true,
  "memberAssignment": {"test-topic-1762779432801": [0]},
  "groupProtocol": "RoundRobinAssigner",
  "duration": 4222
}
```

The `duration` field shows how long the group join took (in milliseconds).

---

## Files Description

### Test Files

| File | Purpose |
|------|---------|
| `scram-sync-e2e.test.js` | Main E2E test - validates complete sync flow |
| `test-both-mechanisms.sh` | Orchestration script - runs both SCRAM-SHA-256 and SCRAM-SHA-512 |
| `test-log-parsing.js` | Standalone demo of log parsing for consumer group readiness |
| `package.json` | Node.js dependencies (kafkajs, node-fetch) |

### Key Functions in `scram-sync-e2e.test.js`

| Function | Purpose |
|----------|---------|
| `getAdminToken()` | Authenticate to Keycloak Admin API |
| `createKeycloakUser()` | Create user via REST API |
| `setUserPassword()` | Set password (triggers SPI sync) |
| `waitKafkaReady()` | Poll until Kafka cluster is ready |
| `ensureTopicWithLeaders()` | Create topic and wait for leader election |
| `testKafkaProducer()` | Authenticate and publish message |
| `testKafkaConsumer()` | Authenticate, wait for group, consume message |
| `createConsumerGroupReadyWatcher()` | Custom log parser for consumer readiness |

### Configuration

Configuration is done via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `TEST_SCRAM_MECHANISM` | `256` | SCRAM mechanism: `256` or `512` |
| `KEYCLOAK_URL` | `https://localhost:57003` | Keycloak base URL |
| `KEYCLOAK_ADMIN` | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | `The2password.` | Keycloak admin password |
| `KAFKA_BROKERS` | `localhost:57005` | Kafka broker addresses |

---

## Troubleshooting

### Tests Fail: "SASL authentication failed"

**Cause**: SPI hasn't created SCRAM credentials yet

**Solutions**:
1. Check Keycloak logs: `docker logs keycloak`
2. Verify SPI is loaded: `docker exec keycloak ls -la /opt/keycloak/providers/`
3. Verify event listener is enabled: Check test output for "✅ Event listener enabled"
4. Increase sync wait time in test (currently 2 seconds at line 167)

### Tests Fail: "Connection refused"

**Cause**: Services not running or ports blocked

**Solutions**:
1. Check services: `docker compose ps`
2. Check port availability: `netstat -tuln | grep -E '57001|57003|57005'`
3. Restart services: `docker compose restart`

### Tests Fail: "Topic metadata not available"

**Cause**: Topic leadership election timing issue

**Solutions**:
1. The test already handles this with `ensureTopicWithLeaders()`
2. If it persists, increase timeout in `ensureTopicWithLeaders()` (line 202)

### Tests Timeout: "No message received within 15 seconds"

**Cause**: Consumer not receiving messages

**Debug steps**:
1. Check producer logs - was message published?
2. Check consumer logs - did it subscribe?
3. Verify topic name matches in both producer and consumer
4. Check Kafka logs: `docker logs kafka`

### Errors Persist Beyond Retry Limit

**Cause**: Kafka coordinator genuinely unavailable

**Solutions**:
1. Check Kafka logs: `docker logs kafka`
2. Verify Kafka is healthy: `docker exec kafka nc -z localhost 9093`
3. Restart Kafka: `docker compose restart kafka`
4. Check disk space and memory

---

## Additional Resources

- [KafkaJS Documentation](https://kafka.js.org/)
- [Kafka Consumer Groups](https://kafka.apache.org/documentation/#consumergroups)
- [SCRAM Authentication](https://kafka.apache.org/documentation/#security_sasl_scram)
- [Keycloak Event SPI](https://www.keycloak.org/docs/latest/server_development/#_events)

---

## Summary

These E2E tests validate the complete password synchronization flow from Keycloak to Kafka SCRAM credentials. **Transient ERROR logs during consumer group initialization are expected and handled automatically by the retry logic.** The tests use log parsing to precisely detect when the consumer group is ready, eliminating the need for arbitrary timeouts.

**Both SCRAM-SHA-256 and SCRAM-SHA-512 are fully tested and supported.**
