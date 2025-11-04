# Keycloak ➡️ Kafka Sync Agent

The Keycloak → Kafka Sync Agent acts as a real-time identity and authorization bridge. Built on Quarkus, it synchronizes users, clients, and roles from Keycloak into Kafka’s metadata store—managing SCRAM verifiers and ACLs dynamically, recording every operation in SQLite, and exposing telemetry and a dashboard for full operational transparency.

## Features

- 🔄 **Real-time Event Processing**: Listens to Keycloak admin events via webhooks
- 📊 **Periodic Reconciliation**: Regularly syncs all users to ensure consistency
- 💾 **Event Persistence**: SQLite-based event storage with automatic retention management
- 📈 **Metrics & Health**: Prometheus metrics and health check endpoints
- 🐳 **Docker Ready**: Multi-stage optimized Docker builds
- 🔧 **Flexible Configuration**: Environment variable-based configuration

## Quick Start (Docker Compose)

The fastest way to run the complete stack (Keycloak, Kafka, and Sync Agent):

```bash
cd testing/
make start
```

This starts:

- **KMS** (Certificate Authority) on port `57001`
- **Keycloak** on ports `57002` (HTTP) and `57003` (HTTPS)
- **Kafka** on ports `57004` (PLAINTEXT) and `57005` (SSL)
- **Sync Agent** on port `57010`

Access the sync agent:

- Health: http://localhost:57010/health
- Metrics: http://localhost:57010/metrics

See the [testing/README.md](testing/README.md) for detailed infrastructure documentation.

## Docker

### Building the Docker Image

The project includes a multi-stage Dockerfile optimized for production use:

```bash
# Build the image
docker build -f docker/Dockerfile -t keycloak-kafka-sync-agent:latest .

# Run the container
docker run -p 57010:57010 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e KEYCLOAK_URL=https://keycloak:8443 \
  keycloak-kafka-sync-agent:latest
```

The Dockerfile:

- Uses **multi-stage build** for minimal image size
- Based on **Alpine Linux** with Java 21 JRE
- Runs as **non-root user** for security
- Includes **health check** on `/health/ready`
- Final image size: ~200MB

### Running with Docker Compose

The complete development stack is available in `testing/`:

```bash
cd testing/
make start        # Start all services
make status       # Check service status
make logs         # View all logs
make stop         # Stop services
make clean        # Full cleanup
```

For detailed Docker Compose configuration, see [testing/docker-compose.yml](testing/docker-compose.yml).

## Configuration

### Environment Variables

All configuration can be overridden with environment variables:

#### HTTP Server

- `QUARKUS_HTTP_PORT` - Application HTTP port (default: `57010`)

#### Database

- `SQLITE_DB_PATH` - SQLite database file path (default: `sync-agent.db`)

#### Kafka Connection

- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses (default: `localhost:9092`)
- `KAFKA_SECURITY_PROTOCOL` - Security protocol: `PLAINTEXT`, `SSL`, `SASL_SSL` (default: `PLAINTEXT`)
- `KAFKA_REQUEST_TIMEOUT_MS` - Request timeout in ms (default: `30000`)
- `KAFKA_CONNECTION_TIMEOUT_MS` - Connection timeout in ms (default: `10000`)

#### Kafka SSL (when using SSL or SASL_SSL)

- `KAFKA_SSL_TRUSTSTORE_LOCATION` - Truststore file path
- `KAFKA_SSL_TRUSTSTORE_PASSWORD` - Truststore password
- `KAFKA_SSL_KEYSTORE_LOCATION` - Keystore file path (for client auth)
- `KAFKA_SSL_KEYSTORE_PASSWORD` - Keystore password
- `KAFKA_SSL_KEY_PASSWORD` - Private key password

#### Keycloak Connection

- `KEYCLOAK_URL` - Keycloak base URL (default: `https://localhost:57003`)
- `KEYCLOAK_REALM` - Realm name (default: `master`)
- `KEYCLOAK_CLIENT_ID` - Client ID (default: `admin-cli`)
- `KEYCLOAK_CLIENT_SECRET` - Client secret (if using confidential client)
- `KEYCLOAK_ADMIN_USERNAME` - Admin username (default: `admin`)
- `KEYCLOAK_ADMIN_PASSWORD` - Admin password (default: `The2password.`)
- `KEYCLOAK_CONNECTION_TIMEOUT_MS` - Connection timeout (default: `10000`)
- `KEYCLOAK_READ_TIMEOUT_MS` - Read timeout (default: `30000`)
- `KEYCLOAK_WEBHOOK_HMAC_SECRET` - HMAC secret for webhook validation

#### Reconciliation

- `RECONCILE_INTERVAL_SECONDS` - How often to sync all users (default: `120`)
- `RECONCILE_PAGE_SIZE` - Users per page for bulk sync (default: `500`)

#### Retention

- `RETENTION_MAX_BYTES` - Max database size in bytes (default: `268435456` = 256MB)
- `RETENTION_MAX_AGE_DAYS` - Max age for records in days (default: `30`)
- `RETENTION_PURGE_INTERVAL_SECONDS` - How often to run cleanup (default: `300`)

#### Logging

- `QUARKUS_LOG_LEVEL` - Global log level: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` (default: `INFO`)

### Configuration File

Alternatively, edit `src/main/resources/application.properties` for default values:

```properties
# Example: Change Kafka connection
kafka.bootstrap-servers=kafka.example:9092
kafka.security-protocol=PLAINTEXT

# Example: Change Keycloak URL
keycloak.url=https://keycloak.example:8443
keycloak.realm=master
```

See [application.properties](src/main/resources/application.properties) for all available options.

## Quick Start (Local Development)

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/).

## Packaging and running the application

The application can be packaged using:

```shell
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/keycloak-kafka-sync-agent-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult [https://quarkus.io/guides/maven-tooling](https://quarkus.io/guides/maven-tooling).

## Related Guides

- Apache Kafka Client ([guide](https://quarkus.io/guides/kafka)): Connect to Apache Kafka with its native API
- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- SmallRye Health ([guide](https://quarkus.io/guides/smallrye-health)): Monitor service health
- Hibernate ORM ([guide](https://quarkus.io/guides/hibernate-orm)): Define your persistent model with Hibernate ORM and Jakarta Persistence
- Flyway ([guide](https://quarkus.io/guides/flyway)): Handle your database schema migrations
- YAML Configuration ([guide](https://quarkus.io/guides/config-yaml)): Use YAML to configure your Quarkus application
- Micrometer Registry Prometheus ([guide](https://quarkus.io/guides/micrometer)): Enable Prometheus support for Micrometer
