# Testing Guide: Keycloak Password Sync SPI for Kafka SCRAM

Complete testing documentation covering infrastructure setup, E2E tests, and validation procedures.

---

## Table of Contents

- [Overview](#overview)
- [Directory Structure](#directory-structure)
- [Quick Start](#quick-start)
- [Testing Infrastructure](#testing-infrastructure)
- [E2E Tests](#e2e-tests)
- [Test Validation](#test-validation)
- [Troubleshooting](#troubleshooting)

---

## Overview

This directory contains all testing infrastructure and end-to-end tests for the Keycloak Password Sync SPI:

### What We Test

1. **Infrastructure**: Docker Compose stack with KMS, Keycloak, and Kafka
2. **SPI Functionality**: Password synchronization from Keycloak to Kafka SCRAM credentials
3. **SCRAM Authentication**: Both SCRAM-SHA-256 and SCRAM-SHA-512 mechanisms
4. **End-to-End Flow**: Complete validation from user password change to Kafka client authentication

### Test Coverage

- ✅ SCRAM-SHA-256 credential generation and synchronization
- ✅ SCRAM-SHA-512 credential generation and synchronization
- ✅ Kafka producer authentication with synced credentials
- ✅ Kafka consumer authentication with synced credentials
- ✅ Message production and consumption with authenticated clients
- ✅ SSL/TLS encrypted communication
- ✅ Certificate management with Cosmian KMS
- ✅ **Realm filtering** - selective sync based on realm configuration

---

## Directory Structure

```
tests/
├── README.md                          # This file
│
├── infrastructure/                     # Docker Compose testing stack
│   ├── docker-compose.yml             # Service definitions
│   ├── Makefile                       # Infrastructure commands
│   ├── README.md                      # Infrastructure details
│   ├── env.example                    # Configuration docs
│   ├── data/                          # Persistent data (gitignored)
│   │   ├── kms/                       # KMS database
│   │   ├── keycloak/                  # Keycloak SQLite DB
│   │   └── kafka/                     # Kafka logs and state
│   ├── certs/                         # SSL certificates
│   ├── kafka-config/                  # Kafka configuration
│   │   ├── kafka-entrypoint.sh        # Custom entrypoint
│   │   ├── kafka_server_jaas.conf     # SASL configuration
│   │   └── server.properties          # Kafka properties
│   ├── scripts/                       # Helper scripts
│   │   ├── configure-keycloak-realm.sh
│   │   └── enable-event-listener.sh
│   └── dockerfiles/                   # Custom Dockerfiles
│
└── e2e/                               # End-to-end tests
    ├── README.md                      # E2E test details
    ├── scram-sync-e2e.test.js         # Main E2E test
    ├── test-both-mechanisms.sh        # Test orchestration (SHA-256 & SHA-512)
    ├── realm-filtering-e2e.test.js    # Realm filtering E2E test
    ├── test-realm-filtering.sh        # Realm filtering test orchestration
    ├── test-log-parsing.js            # Consumer readiness demo
    ├── package.json                   # NPM dependencies
    └── package-lock.json
```

---

## Quick Start

### 1. Start Testing Infrastructure

```bash
cd tests/infrastructure
make start
```

This starts:
- **KMS** (Certificate Authority) on port `57001`
- **Keycloak** (with SPI) on port `57003` (HTTPS)
- **Kafka** on port `57005` (SSL)

### 2. Run E2E Tests

**SCRAM mechanism tests (SHA-256 & SHA-512):**
```bash
cd tests/e2e
./test-both-mechanisms.sh
```

This automatically:
1. Builds the Keycloak SPI
2. Tests SCRAM-SHA-256 mechanism
3. Cleans up and restarts services
4. Tests SCRAM-SHA-512 mechanism
5. Reports results for both

**Realm filtering test:**
```bash
cd tests/e2e
./test-realm-filtering.sh
```

This validates selective password synchronization based on realm configuration

### 3. Expected Output

```
═══════════════════════════════════════════════════════
   SCENARIO 1: Testing SCRAM-SHA-256
═══════════════════════════════════════════════════════
✅ SPI built successfully
✅ Keycloak is ready
✅ Kafka is ready
✅ Event listener enabled
✅ User created in Keycloak
✅ Producer connected with SCRAM-SHA-256
✅ Consumer connected with SCRAM-SHA-256
✅ Message published and received

═══════════════════════════════════════════════════════
   SCENARIO 2: Testing SCRAM-SHA-512
═══════════════════════════════════════════════════════
✅ SPI built successfully
✅ Keycloak is ready
✅ Kafka is ready
✅ Event listener enabled
✅ User created with SCRAM-SHA-512
✅ Producer connected with SCRAM-SHA-512
✅ Consumer connected with SCRAM-SHA-512
✅ Message published and received

═══════════════════════════════════════════════════════
   🎉 ALL TESTS PASSED
═══════════════════════════════════════════════════════
```

---

## Testing Infrastructure

### Services

The testing infrastructure provides a complete stack for local development and testing:

| Service | Purpose | Ports | Credentials |
|---------|---------|-------|-------------|
| **KMS** | Certificate Authority (Cosmian) | 57001 | - |
| **Keycloak** | Identity Provider with SPI | 57003 (HTTPS) | admin / The2password. |
| **Kafka** | Message Broker (KRaft mode) | 57005 (SSL) | admin / The2password. |

### Network

All services run on the `keycloak-kafka-backbone` Docker network.

### SSL/TLS Setup

Certificates are automatically generated using Cosmian KMS:

- **Root CA**: `tests/infrastructure/certs/ca-root.pem`
- **Keycloak**: `tests/infrastructure/certs/keycloak_server.pem/p12`
- **Kafka**: `tests/infrastructure/certs/kafka_broker.pem/p12`
- **Java Keystores**: `tests/infrastructure/certs/*.jks` (for Kafka)

### Data Persistence

Data is stored in `tests/infrastructure/data/` using bind mounts:
- KMS database and keys
- Keycloak SQLite database
- Kafka logs and state

### Infrastructure Commands

```bash
cd tests/infrastructure

# Setup
make start        # Start all services
make kms-only     # Start only KMS
make certs        # Generate certificates

# Control
make stop         # Stop services
make down         # Stop and remove containers
make restart      # Restart services
make clean        # Full cleanup (interactive)

# Monitoring
make status       # Service status
make health       # Health check
make logs         # All logs
make logs-kafka   # Kafka logs only
make logs-keycloak # Keycloak logs only

# Kafka
make test-topic   # Create test topic
make list-topics  # List topics
make producer     # Console producer
make consumer     # Console consumer

# Utilities
make shell-kafka  # Shell into Kafka
make shell-keycloak # Shell into Keycloak
make inspect-certs # View certificate details
make config       # Show current configuration
```

### Prerequisites

Required tools:
```bash
ckms          # Cosmian KMS CLI (in ../contrib/ckms)
keytool       # Java keystore tool
openssl       # SSL/TLS toolkit
docker        # Container runtime
make          # Build automation
```

To use `ckms` globally:
```bash
mkdir -p ~/.local/bin
ln -sf $(pwd)/../contrib/ckms ~/.local/bin/ckms
export PATH="$HOME/.local/bin:$PATH"
```

---

## E2E Tests

### Test Architecture

The E2E tests validate the complete password synchronization flow:

```
┌─────────────────┐
│  1. CREATE USER │  Create user in Keycloak with password
│   IN KEYCLOAK   │  Realm: master
└────────┬────────┘  Username: test-user-<timestamp>
         │           Password: TestPassword123!
         ▼
┌─────────────────┐
│  2. SPI SYNCS   │  Password event triggers SPI
│  TO KAFKA       │  - Generates SCRAM credentials
└────────┬────────┘  - Upserts to Kafka via AdminClient
         │           - Wait 2 seconds for sync
         ▼
┌─────────────────┐
│  3. PRODUCER    │  Authenticate to Kafka with SCRAM
│  AUTHENTICATES  │  - Connect with SSL/TLS
└────────┬────────┘  - Authenticate with SCRAM-SHA-256/512
         │           - Create topic with leader election
         │           - Publish message
         ▼
┌─────────────────┐
│  4. CONSUMER    │  🎯 CRITICAL VALIDATION 🎯
│  AUTHENTICATES  │  - Connect with SSL/TLS
└────────┬────────┘  - Authenticate with SCRAM-SHA-256/512
         │           - Wait for consumer group ready
         │           - Consume message
         ▼
         ✅
    COMPLETE!
```

### What the Tests Validate

#### ✅ Password Synchronization
- User password is intercepted by SPI before Keycloak hashing
- SCRAM credentials are generated (RFC 5802 compliant)
- Credentials are synced to Kafka via AdminClient API

#### ✅ SCRAM Authentication
- **Producer** authenticates with synced credentials
- **Consumer** authenticates with synced credentials
- Both SCRAM-SHA-256 and SCRAM-SHA-512 work

#### ✅ End-to-End Functionality
- Topic creation and leader election
- Message production
- Consumer group coordination
- Message consumption

### Running Individual Tests

**Test specific mechanism:**
```bash
cd tests/e2e

# SCRAM-SHA-256 only
export TEST_SCRAM_MECHANISM=256
node scram-sync-e2e.test.js

# SCRAM-SHA-512 only
export TEST_SCRAM_MECHANISM=512
node scram-sync-e2e.test.js
```

**Note**: Services must already be running with the correct mechanism configured.

### Realm Filtering Test

The realm filtering E2E test validates that the SPI correctly filters password synchronization based on configured realms.

**Test Flow:**
```
1. Configure SPI to sync only "test-realm" (via PASSWORD_SYNC_REALMS env var)
2. Create "test-realm" in Keycloak
3. Create user-master in "master" realm (should NOT sync)
4. Create user-test-realm in "test-realm" (SHOULD sync)
5. Set passwords for both users
6. Verify logs show realm filtering is active
7. Test Kafka authentication:
   - user-test-realm SUCCEEDS (was synced)
   - user-master FAILS (was filtered out)
```

**Run the realm filtering test:**
```bash
cd tests/e2e
./test-realm-filtering.sh
```

This automatically:
1. Builds the SPI
2. Configures realm filtering (PASSWORD_SYNC_REALMS=test-realm)
3. Starts services with filtering enabled
4. Creates both realms and users
5. Validates sync behavior
6. Tests authentication for both users
7. Verifies logs confirm filtering

**Expected Output:**
```
═══════════════════════════════════════════════════════
   Realm Filtering E2E Test
═══════════════════════════════════════════════════════
✅ test-realm created
✅ Users created in both realms
✅ Realm filtering is ENABLED for: test-realm
✅ Master realm user was NOT synced (auth failed)
✅ Test realm user WAS synced (auth succeeded)
✅ Logs confirmed realm filtering behavior
```

**What This Validates:**
- ✅ Realm filtering configuration via environment variable
- ✅ Multiple realm support
- ✅ Selective password synchronization
- ✅ Users in non-allowed realms are filtered out
- ✅ Users in allowed realms are synced correctly
- ✅ Proper logging of filtering decisions

### Log Parsing for Consumer Readiness

The tests use a custom log parser to detect when the Kafka consumer group is ready:

```javascript
const { logCreator, groupReadyPromise } = createConsumerGroupReadyWatcher();

// Wait for consumer group to initialize
await groupReadyPromise;
console.log(`✅ Consumer group ready`);
```

This eliminates arbitrary timeouts and provides precise, event-driven synchronization.

**Demo the log parsing:**
```bash
cd tests/e2e
node test-log-parsing.js
```

### About Transient Errors

⚠️ **Expected ERROR Logs During Consumer Startup**

You will see ERROR logs like:
```json
{"level":"ERROR","message":"The group coordinator is not available"}
{"level":"ERROR","message":"The coordinator is loading and hence can't process requests"}
```

**This is COMPLETELY NORMAL!**

Why:
1. Consumer group initialization takes 1-3 seconds
2. Coordinator must be elected and load metadata
3. KafkaJS has built-in retry logic (300ms initial, 8 retries)
4. Tests wait for the exact moment consumer is ready

Tests still pass successfully because the retry logic handles these transient errors.

---

## Test Validation

### What This Proves

#### ✅ RFC 5802 Compliance
- SCRAM credential generation follows RFC 5802
- PBKDF2 parameters are correct
- Base64 encoding is proper

#### ✅ Kafka Integration
- AdminClient API usage is correct
- Credentials are stored in Kafka's internal store
- Both SHA-256 and SHA-512 mechanisms work

#### ✅ Password Flow
- Passwords are intercepted before Keycloak hashing
- ThreadLocal correlation works correctly
- No password leakage or persistence

#### ✅ Client Authentication (Critical!)
- **Kafka producers authenticate with synced credentials**
- **Kafka consumers authenticate with synced credentials**
- **SCRAM handshake succeeds**
- **SSL/TLS + SCRAM work together**
- **Full message flow works**

### Evidence Points

Each test step provides specific evidence:

**STEP 1: User Created**
- HTTP 201 Created from Keycloak API
- Password set successfully (HTTP 204)

**STEP 2: Kafka Cluster Ready**
- `describeCluster()` returns broker info
- Topic created with leader elected

**STEP 3: Producer Authentication**
- Producer connects without errors
- Message published successfully

**STEP 4: Consumer Authentication (CRITICAL!)**
- Consumer connects without errors
- Consumer group joins successfully
- Message consumed successfully
- All messages match

---

## Troubleshooting

### Services Not Starting

**KMS not starting:**
```bash
cd tests/infrastructure
docker logs kms
# Check if port 57001 is available
```

**Keycloak not starting:**
```bash
cd tests/infrastructure
docker logs keycloak
# Verify KMS is healthy first
make health
```

**Kafka not connecting:**
```bash
cd tests/infrastructure
docker logs kafka
# Verify certificates exist
ls -la certs/*.jks
```

### Tests Failing

**"SASL authentication failed":**
- SPI hasn't created credentials yet
- Check Keycloak logs: `docker logs keycloak`
- Verify event listener enabled: Check test output
- Increase sync wait time in test (currently 2 seconds)

**"Connection refused":**
- Services not running
- Check: `docker compose ps` from `tests/infrastructure/`
- Check ports: `netstat -tuln | grep -E '57001|57003|57005'`

**"Topic metadata not available":**
- Topic leadership not ready
- Test already handles this with `ensureTopicWithLeaders()`
- If persists, increase timeout (line 202 in scram-sync-e2e.test.js)

**"No message received within 15 seconds":**
- Producer didn't publish or consumer didn't subscribe
- Check producer logs - was message published?
- Check consumer logs - did it subscribe?
- Verify topic name matches

**Transient errors persist:**
- Kafka coordinator genuinely unavailable
- Check: `docker logs kafka`
- Restart: `make restart` from `tests/infrastructure/`
- Clean slate: `make clean && make start`

### Certificate Errors

```bash
cd tests/infrastructure

# Regenerate certificates
make clean  # Answer 'y' when prompted
make start
```

### Network Issues

```bash
# Check network exists
docker network ls | grep keycloak-kafka-backbone

# Recreate if needed
docker network rm keycloak-kafka-backbone
cd tests/infrastructure
docker compose up -d
```

### Clean Slate

```bash
cd tests/infrastructure
make clean  # Removes everything
make start  # Fresh start
```

---

## Configuration

### Environment Variables

The infrastructure uses environment variables with defaults:

```bash
# Kafka SCRAM mechanism (256 or 512)
KAFKA_SCRAM_MECHANISM=256

# Realm filtering (optional - comma-separated list of realm names)
PASSWORD_SYNC_REALMS=test-realm,production

# Service URLs
KEYCLOAK_URL=https://localhost:57003
KAFKA_BROKERS=localhost:57005

# Credentials
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=The2password.
```

To override defaults, create `tests/infrastructure/.env`:
```bash
cd tests/infrastructure
cp env.example .env
# Edit .env with your values
```

See `tests/infrastructure/env.example` for all available options.

### Makefile Variables

Override infrastructure settings:
```bash
cd tests/infrastructure

# View current configuration
make config

# Override domain
make start DOMAIN=mycompany.local

# Override specific variables
make start CERT_PASSWORD=MySecurePass
```

---

## Important Notes

⚠️ **For Testing Only**

- Password `The2password.` is for testing only
- SQLite is used for simplicity (use PostgreSQL in production)
- Self-signed certificates (replace with proper CA in production)
- All ports in 57000 range to avoid conflicts

⚠️ **Security in Production**

For production deployments:
1. Use TLS for all Kafka connections
2. Secure credentials with environment secrets management
3. Enable audit logging for password sync events
4. Implement network isolation between Keycloak and Kafka
5. Keep Keycloak and Kafka updated

---

## Additional Resources

- [KafkaJS Documentation](https://kafka.js.org/)
- [Kafka SCRAM Authentication](https://kafka.apache.org/documentation/#security_sasl_scram)
- [RFC 5802 - SCRAM](https://tools.ietf.org/html/rfc5802)
- [Keycloak Event SPI](https://www.keycloak.org/docs/latest/server_development/#_events)
- [Cosmian KMS](https://github.com/Cosmian/kms)

---

## Summary

This testing suite provides **complete validation** of the Keycloak Password Sync SPI:

1. ✅ Infrastructure setup with Docker Compose
2. ✅ SSL/TLS certificate generation with KMS
3. ✅ SCRAM credential generation and synchronization
4. ✅ End-to-end authentication flow validation
5. ✅ Both SCRAM-SHA-256 and SCRAM-SHA-512 tested

**The tests prove that passwords synced from Keycloak can be used to successfully authenticate Kafka clients with SCRAM.**

For detailed infrastructure documentation, see `tests/infrastructure/README.md`.
For detailed E2E test documentation, see `tests/e2e/README.md`.
