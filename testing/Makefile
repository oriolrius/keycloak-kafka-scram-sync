.PHONY: help start stop restart down clean logs status health certs kms-only config

# Configuration
CERT_PASSWORD := The2password.
CERT_ORG := Cosmian Test
CERT_COUNTRY := ES
CERT_DIR := certs

# Domains and Hostnames
DOMAIN := example
KMS_HOSTNAME := kms.$(DOMAIN)
KEYCLOAK_HOSTNAME := keycloak.$(DOMAIN)
KAFKA_HOSTNAME := kafka.$(DOMAIN)

# Service URLs (HTTPS only)
KMS_URL := http://localhost:57001
KEYCLOAK_URL := https://localhost:57003
KAFKA_SSL := localhost:57005

# Network
NETWORK_NAME := keycloak-kafka-backbone

# Default target
help:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "Keycloak-Kafka Testing Environment"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo ""
	@echo "📦 Setup Commands:"
	@echo "  make start        - Start all services"
	@echo "  make kms-only     - Start only KMS (for certificate generation)"
	@echo "  make certs        - Generate certificates (requires KMS running)"
	@echo ""
	@echo "🔄 Control Commands:"
	@echo "  make stop         - Stop all services (keeps data)"
	@echo "  make down         - Stop and remove containers (keeps data)"
	@echo "  make restart      - Restart all services"
	@echo "  make clean        - Full cleanup (removes containers, data, certs)"
	@echo ""
	@echo "📊 Monitoring Commands:"
	@echo "  make status       - Show service status"
	@echo "  make health       - Check service health"
	@echo "  make logs         - View all logs"
	@echo "  make logs-kms     - View KMS logs"
	@echo "  make logs-keycloak - View Keycloak logs"
	@echo "  make logs-kafka   - View Kafka logs"
	@echo ""
	@echo "🔧 Utility Commands:"
	@echo "  make shell-kms    - Shell into KMS container"
	@echo "  make shell-keycloak - Shell into Keycloak container"
	@echo "  make shell-kafka  - Shell into Kafka container"
	@echo "  make network      - Inspect network"
	@echo "  make resources    - Show resource usage"
	@echo ""
	@echo "📜 Kafka Commands:"
	@echo "  make test-topic   - Create test topic"
	@echo "  make list-topics  - List all topics"
	@echo "  make producer     - Start console producer"
	@echo "  make consumer     - Start console consumer"
	@echo ""
	@echo "🔐 Certificate Commands:"
	@echo "  make inspect-certs - Inspect certificate details"
	@echo "  make kms-list     - List certificates in KMS"
	@echo ""
	@echo "⚙️  Configuration:"
	@echo "  make config       - Show current configuration"
	@echo ""

# Start all services
start:
	@echo "🚀 Starting Keycloak-Kafka testing environment..."
	@echo ""
	@echo "Step 1/3: Starting KMS..."
	@docker compose up -d kms
	@echo "Waiting for KMS to be healthy..."
	@sleep 5
	@echo ""
	@echo "Step 2/3: Generating certificates..."
	@$(MAKE) -s certs || (echo "❌ Certificate generation failed"; exit 1)
	@echo ""
	@echo "Step 3/3: Starting all services..."
	@docker compose up -d
	@echo ""
	@echo "✅ Environment started!"
	@echo ""
	@echo "Services available at (HTTPS only):"
	@echo "  - KMS:      $(KMS_URL) ($(KMS_HOSTNAME))"
	@echo "  - Keycloak: $(KEYCLOAK_URL) ($(KEYCLOAK_HOSTNAME)) [admin/$(CERT_PASSWORD)]"
	@echo "  - Kafka:    $(KAFKA_SSL) SSL ($(KAFKA_HOSTNAME))"
	@echo ""
	@echo "Hostnames configured in /etc/hosts:"
	@echo "  127.0.0.1  $(KMS_HOSTNAME) $(KEYCLOAK_HOSTNAME) $(KAFKA_HOSTNAME)"
	@echo ""
	@echo "Run 'make health' to check service health"

# Start only KMS
kms-only:
	@echo "Starting KMS only..."
	@docker compose up -d kms
	@echo "Waiting for KMS to be healthy..."
	@sleep 5
	@docker compose ps kms

# Generate certificates
certs:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "Certificate Generation"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo ""
	@# Check prerequisites
	@command -v ckms >/dev/null 2>&1 || (echo "❌ ERROR: ckms CLI not found! Try: export PATH=\"\$$HOME/.local/bin:\$$PATH\""; exit 1)
	@command -v keytool >/dev/null 2>&1 || (echo "❌ ERROR: keytool not found! Install Java JRE/JDK"; exit 1)
	@command -v openssl >/dev/null 2>&1 || (echo "❌ ERROR: openssl not found!"; exit 1)
	@# Check KMS is running
	@curl -sf $(KMS_URL)/version >/dev/null || (echo "❌ ERROR: KMS not running! Run: make kms-only"; exit 1)
	@echo "✓ Prerequisites OK"
	@echo ""
	@# Configure ckms and create certificates
	@mkdir -p $(CERT_DIR)
	@mkdir -p $(CERT_DIR)/.credentials
	@echo "$(CERT_PASSWORD)" > $(CERT_DIR)/.credentials/keystore_password
	@echo "$(CERT_PASSWORD)" > $(CERT_DIR)/.credentials/truststore_password
	@echo "$(CERT_PASSWORD)" > $(CERT_DIR)/.credentials/sslkey_password
	@echo "Step 1/3: Creating Root CA..." && \
	(ckms --kms-url $(KMS_URL) kms certificates revoke --certificate-id ca-root 2>/dev/null || true) && \
	(ckms --kms-url $(KMS_URL) kms certificates destroy --certificate-id ca-root 2>/dev/null || true) && \
	ckms --kms-url $(KMS_URL) kms certificates certify \
		--certificate-id ca-root \
		--generate-key-pair \
		--subject-name "CN=Testing Root CA,O=$(CERT_ORG),C=$(CERT_COUNTRY)" \
		--algorithm rsa4096 \
		--days 3650 \
		--tag testing-env && \
	ckms --kms-url $(KMS_URL) kms certificates export --certificate-id ca-root --format pem $(CERT_DIR)/ca-root.pem && \
	echo "✓ Root CA created" && \
	echo "" && \
	echo "Step 2/3: Creating Keycloak certificate..." && \
	(ckms --kms-url $(KMS_URL) kms certificates revoke --certificate-id keycloak_server 2>/dev/null || true) && \
	(ckms --kms-url $(KMS_URL) kms certificates destroy --certificate-id keycloak_server 2>/dev/null || true) && \
	ckms --kms-url $(KMS_URL) kms certificates certify \
		--certificate-id keycloak_server \
		--issuer-certificate-id ca-root \
		--generate-key-pair \
		--subject-name "CN=$(KEYCLOAK_HOSTNAME),O=$(CERT_ORG),C=$(CERT_COUNTRY)" \
		--algorithm rsa2048 \
		--days 365 \
		--tag testing-env && \
	ckms --kms-url $(KMS_URL) kms certificates export --certificate-id keycloak_server --format pkcs12 --pkcs12-password $(CERT_PASSWORD) $(CERT_DIR)/keycloak_server.p12 && \
	ckms --kms-url $(KMS_URL) kms certificates export --certificate-id keycloak_server --format pem $(CERT_DIR)/keycloak_server.pem && \
	openssl pkcs12 -in $(CERT_DIR)/keycloak_server.p12 -passin pass:$(CERT_PASSWORD) -nocerts -nodes -out $(CERT_DIR)/keycloak.key && \
	openssl pkcs12 -in $(CERT_DIR)/keycloak_server.p12 -passin pass:$(CERT_PASSWORD) -nokeys -clcerts -out $(CERT_DIR)/keycloak.crt && \
	echo "✓ Keycloak certificate created" && \
	echo "" && \
	echo "Step 3/3: Creating Kafka certificate..." && \
	(ckms --kms-url $(KMS_URL) kms certificates revoke --certificate-id kafka_broker 2>/dev/null || true) && \
	(ckms --kms-url $(KMS_URL) kms certificates destroy --certificate-id kafka_broker 2>/dev/null || true) && \
	ckms --kms-url $(KMS_URL) kms certificates certify \
		--certificate-id kafka_broker \
		--issuer-certificate-id ca-root \
		--generate-key-pair \
		--subject-name "CN=$(KAFKA_HOSTNAME),O=$(CERT_ORG),C=$(CERT_COUNTRY)" \
		--algorithm rsa2048 \
		--days 365 \
		--tag testing-env && \
	ckms --kms-url $(KMS_URL) kms certificates export --certificate-id kafka_broker --format pkcs12 --pkcs12-password $(CERT_PASSWORD) $(CERT_DIR)/kafka_broker.p12 && \
	ckms --kms-url $(KMS_URL) kms certificates export --certificate-id kafka_broker --format pem $(CERT_DIR)/kafka_broker.pem && \
	keytool -importkeystore -srckeystore $(CERT_DIR)/kafka_broker.p12 -srcstoretype PKCS12 -srcstorepass $(CERT_PASSWORD) -destkeystore $(CERT_DIR)/kafka_broker.keystore.jks -deststoretype JKS -deststorepass $(CERT_PASSWORD) -noprompt && \
	keytool -import -trustcacerts -alias ca-root -file $(CERT_DIR)/ca-root.pem -keystore $(CERT_DIR)/kafka.truststore.jks -storepass $(CERT_PASSWORD) -noprompt && \
	echo "$(CERT_PASSWORD)" > $(CERT_DIR)/kafka_keystore_creds && \
	echo "$(CERT_PASSWORD)" > $(CERT_DIR)/kafka_ssl_key_creds && \
	echo "$(CERT_PASSWORD)" > $(CERT_DIR)/kafka_truststore_creds && \
	echo "✓ Kafka certificate created" && \
	echo "" && \
	echo "Verifying certificates..." && \
	openssl verify -CAfile $(CERT_DIR)/ca-root.pem $(CERT_DIR)/kafka_broker.pem >/dev/null 2>&1 && echo "✓ Kafka cert valid" || echo "✗ Kafka cert invalid" && \
	openssl verify -CAfile $(CERT_DIR)/ca-root.pem $(CERT_DIR)/keycloak_server.pem >/dev/null 2>&1 && echo "✓ Keycloak cert valid" || echo "✗ Keycloak cert invalid" && \
	echo "" && \
	echo "✅ Certificate generation complete!"

# Stop all services
stop:
	@echo "Stopping all services..."
	@docker compose stop

# Stop and remove containers
down:
	@echo "Stopping and removing containers..."
	@docker compose down

# Restart all services
restart:
	@echo "Restarting all services..."
	@docker compose restart

# Full cleanup
clean:
	@echo "⚠️  Full cleanup - This will remove:"
	@echo "  - All containers"
	@echo "  - All data (./data/*)"
	@echo "  - All certificates (./certs/*)"
	@echo "  - Network"
	@echo ""
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "Stopping and removing containers..."; \
		docker compose down -v 2>/dev/null || true; \
		echo "Removing data directories..."; \
		rm -rf data/kms/* data/keycloak/* data/kafka/* 2>/dev/null || true; \
		echo "Removing certificates..."; \
		rm -f certs/*.pem certs/*.key certs/*.crt certs/*.p12 certs/*.jks 2>/dev/null || true; \
		rm -f kms-config.json 2>/dev/null || true; \
		echo "✅ Cleanup complete"; \
	else \
		echo "Cleanup cancelled"; \
	fi

# View all logs
logs:
	@docker compose logs -f

# View individual service logs
logs-kms:
	@docker compose logs -f kms

logs-keycloak:
	@docker compose logs -f keycloak

logs-kafka:
	@docker compose logs -f kafka

# Show service status
status:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "Service Status"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@docker compose ps
	@echo ""
	@echo "Certificate Files:"
	@ls -lh certs/*.pem certs/*.jks 2>/dev/null || echo "(No certificates generated yet)"

# Health check all services
health:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "Health Check"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo -n "KMS:       "
	@curl -sf $(KMS_URL)/version >/dev/null 2>&1 && echo "✓ OK" || echo "✗ FAILED"
	@echo -n "Keycloak:  "
	@curl -kfs $(KEYCLOAK_URL)/ >/dev/null 2>&1 && echo "✓ OK (HTTPS)" || echo "✗ FAILED"
	@echo -n "Kafka:     "
	@docker compose exec -T kafka nc -z localhost 9093 >/dev/null 2>&1 && echo "✓ OK (SSL)" || echo "✗ FAILED"
	@echo ""
	@echo -n "Network:   "
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 && echo "✓ OK" || echo "✗ FAILED"

# Create test Kafka topic
test-topic:
	@echo "Creating test topic 'test-events'..."
	@docker exec kafka kafka-topics \
		--create \
		--topic test-events \
		--bootstrap-server localhost:9092 \
		--partitions 3 \
		--replication-factor 1
	@echo "✓ Topic created successfully"

# List Kafka topics
list-topics:
	@echo "Kafka Topics:"
	@docker exec kafka kafka-topics \
		--list \
		--bootstrap-server localhost:9092

# Kafka console producer
producer:
	@echo "Starting Kafka console producer (Ctrl+C to exit)..."
	@docker exec -it kafka kafka-console-producer \
		--broker-list localhost:9092 \
		--topic test-events

# Kafka console consumer
consumer:
	@echo "Starting Kafka console consumer (Ctrl+C to exit)..."
	@docker exec -it kafka kafka-console-consumer \
		--bootstrap-server localhost:9092 \
		--topic test-events \
		--from-beginning

# Shell into containers
shell-kms:
	@docker exec -it kms /bin/sh

shell-keycloak:
	@docker exec -it keycloak /bin/bash

shell-kafka:
	@docker exec -it kafka /bin/bash

# Certificate inspection
inspect-certs:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "Certificate Details"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo ""
	@echo "=== Root CA ==="
	@[ -f certs/ca-root.pem ] && openssl x509 -in certs/ca-root.pem -text -noout | grep -A2 "Subject:" || echo "Not found"
	@echo ""
	@echo "=== Keycloak Certificate ==="
	@[ -f certs/keycloak_server.pem ] && openssl x509 -in certs/keycloak_server.pem -text -noout | grep -A2 "Subject:" || echo "Not found"
	@echo ""
	@echo "=== Kafka Certificate ==="
	@[ -f certs/kafka_broker.pem ] && openssl x509 -in certs/kafka_broker.pem -text -noout | grep -A2 "Subject:" || echo "Not found"

# List certificates in KMS
kms-list:
	@export KMS_CLI_CONF_PATH=$(PWD)/kms-config.json && ckms certificates list

# Network inspection
network:
	@docker network inspect $(NETWORK_NAME)

# Resource usage
resources:
	@docker stats --no-stream kms keycloak kafka

# Show current configuration
config:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "Current Configuration"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo ""
	@echo "Domain & Hostnames:"
	@echo "  DOMAIN:             $(DOMAIN)"
	@echo "  KMS_HOSTNAME:       $(KMS_HOSTNAME)"
	@echo "  KEYCLOAK_HOSTNAME:  $(KEYCLOAK_HOSTNAME)"
	@echo "  KAFKA_HOSTNAME:     $(KAFKA_HOSTNAME)"
	@echo "  AGENT_HOSTNAME:     $(AGENT_HOSTNAME)"
	@echo ""
	@echo "Service URLs:"
	@echo "  KMS_URL:            $(KMS_URL)"
	@echo "  KEYCLOAK_URL:       $(KEYCLOAK_URL)"
	@echo "  KAFKA_PLAINTEXT:    $(KAFKA_PLAINTEXT)"
	@echo "  KAFKA_SSL:          $(KAFKA_SSL)"
	@echo ""
	@echo "Network:"
	@echo "  NETWORK_NAME:       $(NETWORK_NAME)"
	@echo ""
	@echo "Certificates:"
	@echo "  CERT_PASSWORD:      $(CERT_PASSWORD)"
	@echo "  CERT_ORG:           $(CERT_ORG)"
	@echo "  CERT_COUNTRY:       $(CERT_COUNTRY)"
	@echo "  CERT_DIR:           $(CERT_DIR)"
	@echo ""
	@echo "To override: make <target> DOMAIN=mycompany.local"
