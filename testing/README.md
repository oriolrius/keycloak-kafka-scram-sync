# Keycloak-Kafka Testing Environment

Complete Docker Compose stack with **KMS** (PKI/Certificate Authority), **Keycloak** (Identity Provider), and **Kafka 4.1.0** (KRaft mode).

## Quick Start

```bash
# Start everything
make start

# Check status
make health

# View logs
make logs

# Stop everything
make stop

# Full cleanup
make clean
```

## What You Get

- **KMS (Cosmian)** - Certificate Authority on port `57001`
- **Keycloak** - Identity provider on port `57002` (HTTP) and `57003` (HTTPS)
- **Kafka 4.1.0** - Message broker on ports `57004` (PLAINTEXT) and `57005` (SSL)
- **Network**: `keycloak-kafka-backbone`
- **SSL/TLS**: All certificates generated via KMS
- **Data persistence**: `./data/` (bind mounts)
- **Credentials**: `admin` / `The2password.`

## Prerequisites

```bash
# Required tools
ckms          # Cosmian KMS CLI - included in ../contrib/ckms or https://github.com/Cosmian/kms/releases
              # Add to PATH: export PATH="$HOME/.local/bin:$PATH"
keytool       # Java keystore tool (comes with JDK)
openssl       # SSL/TLS toolkit
docker        # Container runtime
make          # Build automation
```

**Note**: The `ckms` binary is provided in `../contrib/ckms`. To use it globally, create a symlink:
```bash
mkdir -p ~/.local/bin
ln -sf $(pwd)/../contrib/ckms ~/.local/bin/ckms
export PATH="$HOME/.local/bin:$PATH"
```

## Architecture

```
┌─────────────┐
│     KMS     │  Certificate Authority
│  (57001)    │  Generates all SSL certificates
└──────┬──────┘
       │
       ├──────────┐
       │          │
┌──────▼──────┐  ┌▼────────────┐
│  Keycloak   │  │    Kafka    │
│ (57002/03)  │  │ (57004/05)  │
│   SQLite    │  │    KRaft    │
└─────────────┘  └─────────────┘
```

## Configuration

### Makefile Variables

The Makefile uses variables for domains, hostnames, and URLs:

```bash
# View current configuration
make config

# Override domain for all hostnames
make start DOMAIN=mycompany.local

# Override specific variables
make start KMS_HOSTNAME=kms.prod.local CERT_PASSWORD=MySecurePass
```

**Available variables:**
- `DOMAIN` - Base domain (default: `example`)
- `KMS_HOSTNAME`, `KEYCLOAK_HOSTNAME`, `KAFKA_HOSTNAME`
- `KMS_URL`, `KEYCLOAK_URL`, `KAFKA_SSL`
- `NETWORK_NAME` - Docker network (default: `keycloak-kafka-backbone`)
- `CERT_PASSWORD`, `CERT_ORG`, `CERT_COUNTRY`, `CERT_DIR`

### Docker Compose Variables

All services use environment variables with defaults:

```yaml
${VARIABLE:-default_value}
```

To override defaults, create `.env` file (optional):

```bash
cp env.example .env
# Edit .env with your values
```

See `env.example` for all available options.

## Commands

```bash
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
make logs-kafka   # Kafka logs
make logs-keycloak # Keycloak logs
make logs-kms     # KMS logs

# Kafka
make test-topic   # Create test topic
make list-topics  # List topics
make producer     # Console producer
make consumer     # Console consumer

# Utilities
make shell-kafka  # Shell into Kafka
make shell-keycloak # Shell into Keycloak
make shell-kms    # Shell into KMS
make inspect-certs # View certificate details
make kms-list     # List KMS certificates
make network      # Inspect network
make resources    # Resource usage

# Configuration
make config       # Show current configuration
```

## Service Access

| Service | URL | Credentials |
|---------|-----|-------------|
| KMS | http://localhost:57001 | - |
| Keycloak (HTTP) | http://localhost:57002 | admin / The2password. |
| Keycloak (HTTPS) | https://localhost:57003 | admin / The2password. |
| Kafka (PLAINTEXT) | localhost:57004 | - |
| Kafka (SSL) | localhost:57005 | - |

## Hostnames

Add to `/etc/hosts`:

```
127.0.0.1  kms.example keycloak.example kafka.example
```

## SSL/TLS Setup

Certificates are generated automatically during `make start`. If you need to regenerate:

```bash
make kms-only
make certs
```

Certificate files in `./certs/`:
- `ca-root.pem` - Root CA
- `keycloak_server.pem/p12` - Keycloak certificate
- `kafka_broker.pem/p12` - Kafka certificate
- `*.jks` - Java keystores for Kafka

## Data Persistence

Data stored in `./data/` using bind mounts:
- `./data/kms/` - KMS database and keys
- `./data/keycloak/` - Keycloak SQLite database
- `./data/kafka/` - Kafka logs and state

## Troubleshooting

### KMS not starting
```bash
docker logs kms
# Check if port 57001 is available
```

### Keycloak not starting
```bash
docker logs keycloak
# Check KMS is healthy first
make health
```

### Kafka not connecting
```bash
docker logs kafka
# Verify certificates exist
ls -la certs/*.jks
```

### Certificate errors
```bash
# Regenerate certificates
make clean  # Answer 'y'
make start
```

### Network issues
```bash
# Check network exists
docker network ls | grep keycloak-kafka-backbone

# Recreate if needed
docker network rm keycloak-kafka-backbone
docker compose up -d
```

### Clean slate
```bash
make clean  # Removes everything
make start  # Fresh start
```

## Project Structure

```
testing/
├── docker-compose.yml   # Service definitions
├── env.example         # Configuration documentation
├── Makefile            # All commands
├── README.md           # This file
├── .gitignore
├── data/               # Persistent data (gitignored)
│   ├── kms/
│   ├── keycloak/
│   └── kafka/
└── certs/              # Certificates
    ├── ca.ext         # Root CA extensions
    ├── keycloak.ext   # Keycloak cert extensions
    ├── kafka.ext      # Kafka cert extensions
    └── agent.ext      # Agent cert extensions
```

## Important Notes

⚠️ **Testing Only**: Password `The2password.` is for testing. Never use in production.

⚠️ **SQLite**: Keycloak uses SQLite for simplicity. For production, use PostgreSQL.

⚠️ **Ports**: All ports in 57000 range to avoid conflicts. Change in `docker-compose.yml` if needed.

⚠️ **Logs**: All logs go to Docker console (not disk). View with `make logs`.

## License

Testing environment for development purposes.
