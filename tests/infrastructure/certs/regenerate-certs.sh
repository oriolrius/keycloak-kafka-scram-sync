#!/usr/bin/env bash
#
# Certificate Generation Script using Cosmian KMS
#
# This script generates all certificates needed for the Keycloak-Kafka
# testing environment using the Cosmian KMS (ckms CLI) with proper
# X.509 extensions defined in .ext files.
#
# Prerequisites:
# - KMS service must be running (docker compose up -d kms)
# - ckms CLI tool must be available in PATH
# - keytool (Java) and openssl must be installed
#
# Usage:
#   ./regenerate-certs.sh [--kms-url URL] [--password PASSWORD]
#

set -euo pipefail

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="$SCRIPT_DIR"

# Default configuration
KMS_URL="${KMS_URL:-http://localhost:57001}"
CERT_PASSWORD="${CERT_PASSWORD:-The2password.}"
CERT_ORG="${CERT_ORG:-Cosmian Test}"
CERT_COUNTRY="${CERT_COUNTRY:-ES}"
DOMAIN="${DOMAIN:-example}"

# Hostnames
KMS_HOSTNAME="kms.${DOMAIN}"
KEYCLOAK_HOSTNAME="keycloak.${DOMAIN}"
KAFKA_HOSTNAME="kafka.${DOMAIN}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --kms-url)
      KMS_URL="$2"
      shift 2
      ;;
    --password)
      CERT_PASSWORD="$2"
      shift 2
      ;;
    --help)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --kms-url URL      KMS server URL (default: http://localhost:57001)"
      echo "  --password PASS    Certificate password (default: The2password.)"
      echo "  --help             Show this help message"
      echo ""
      echo "Environment variables:"
      echo "  KMS_URL            Alternative to --kms-url"
      echo "  CERT_PASSWORD      Alternative to --password"
      echo "  CERT_ORG           Organization name (default: Cosmian Test)"
      echo "  CERT_COUNTRY       Country code (default: ES)"
      echo "  DOMAIN             Domain suffix (default: example)"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Helper functions
log_info() {
  echo -e "${BLUE}ℹ${NC} $*"
}

log_success() {
  echo -e "${GREEN}✓${NC} $*"
}

log_error() {
  echo -e "${RED}✗${NC} $*" >&2
}

log_warning() {
  echo -e "${YELLOW}⚠${NC} $*"
}

# Check prerequisites
check_prerequisites() {
  log_info "Checking prerequisites..."

  local missing=0

  if ! command -v ckms >/dev/null 2>&1; then
    log_error "ckms CLI not found! Try: export PATH=\"\$HOME/.local/bin:\$PATH\""
    missing=1
  fi

  if ! command -v keytool >/dev/null 2>&1; then
    log_error "keytool not found! Install Java JRE/JDK"
    missing=1
  fi

  if ! command -v openssl >/dev/null 2>&1; then
    log_error "openssl not found!"
    missing=1
  fi

  if ! curl -sf "$KMS_URL/version" >/dev/null 2>&1; then
    log_error "KMS not running at $KMS_URL! Run: docker compose up -d kms"
    missing=1
  fi

  # Check .ext files exist
  for ext_file in ca.ext keycloak.ext kafka.ext; do
    if [[ ! -f "$CERT_DIR/$ext_file" ]]; then
      log_error "Extension file not found: $CERT_DIR/$ext_file"
      missing=1
    fi
  done

  if [[ $missing -eq 1 ]]; then
    exit 1
  fi

  log_success "Prerequisites OK"
  echo
}

# Clean up existing certificate in KMS
cleanup_certificate() {
  local cert_id="$1"

  # Revoke if exists (ignore errors)
  ckms --kms-url "$KMS_URL" kms certificates revoke --certificate-id "$cert_id" 2>/dev/null || true

  # Destroy if exists (ignore errors)
  ckms --kms-url "$KMS_URL" kms certificates destroy --certificate-id "$cert_id" 2>/dev/null || true
}

# Create Root CA certificate
create_root_ca() {
  log_info "Step 1/3: Creating Root CA..."

  cleanup_certificate "ca-root"

  ckms --kms-url "$KMS_URL" kms certificates certify \
    --certificate-id ca-root \
    --generate-key-pair \
    --subject-name "CN=Testing Root CA,O=${CERT_ORG},C=${CERT_COUNTRY}" \
    --algorithm rsa4096 \
    --days 3650 \
    --certificate-extensions "$CERT_DIR/ca.ext" \
    --tag testing-env

  ckms --kms-url "$KMS_URL" kms certificates export \
    --certificate-id ca-root \
    --format pem \
    "$CERT_DIR/ca-root.pem"

  log_success "Root CA created"
  echo
}

# Create Keycloak server certificate
create_keycloak_cert() {
  log_info "Step 2/3: Creating Keycloak certificate..."

  cleanup_certificate "keycloak_server"

  ckms --kms-url "$KMS_URL" kms certificates certify \
    --certificate-id keycloak_server \
    --issuer-certificate-id ca-root \
    --generate-key-pair \
    --subject-name "CN=${KEYCLOAK_HOSTNAME},O=${CERT_ORG},C=${CERT_COUNTRY}" \
    --algorithm rsa2048 \
    --days 365 \
    --certificate-extensions "$CERT_DIR/keycloak.ext" \
    --tag testing-env

  # Export as PKCS12
  ckms --kms-url "$KMS_URL" kms certificates export \
    --certificate-id keycloak_server \
    --format pkcs12 \
    --pkcs12-password "$CERT_PASSWORD" \
    "$CERT_DIR/keycloak_server.p12"

  # Export as PEM
  ckms --kms-url "$KMS_URL" kms certificates export \
    --certificate-id keycloak_server \
    --format pem \
    "$CERT_DIR/keycloak_server.pem"

  # Extract key and certificate for Keycloak
  openssl pkcs12 -in "$CERT_DIR/keycloak_server.p12" \
    -passin "pass:$CERT_PASSWORD" \
    -nocerts -nodes \
    -out "$CERT_DIR/keycloak.key"

  openssl pkcs12 -in "$CERT_DIR/keycloak_server.p12" \
    -passin "pass:$CERT_PASSWORD" \
    -nokeys -clcerts \
    -out "$CERT_DIR/keycloak.crt"

  log_success "Keycloak certificate created"
  echo
}

# Create Kafka broker certificate
create_kafka_cert() {
  log_info "Step 3/3: Creating Kafka certificate..."

  cleanup_certificate "kafka_broker"

  ckms --kms-url "$KMS_URL" kms certificates certify \
    --certificate-id kafka_broker \
    --issuer-certificate-id ca-root \
    --generate-key-pair \
    --subject-name "CN=${KAFKA_HOSTNAME},O=${CERT_ORG},C=${CERT_COUNTRY}" \
    --algorithm rsa2048 \
    --days 365 \
    --certificate-extensions "$CERT_DIR/kafka.ext" \
    --tag testing-env

  # Export as PKCS12
  ckms --kms-url "$KMS_URL" kms certificates export \
    --certificate-id kafka_broker \
    --format pkcs12 \
    --pkcs12-password "$CERT_PASSWORD" \
    "$CERT_DIR/kafka_broker.p12"

  # Export as PEM
  ckms --kms-url "$KMS_URL" kms certificates export \
    --certificate-id kafka_broker \
    --format pem \
    "$CERT_DIR/kafka_broker.pem"

  # Create JKS keystore for Kafka
  keytool -importkeystore \
    -srckeystore "$CERT_DIR/kafka_broker.p12" \
    -srcstoretype PKCS12 \
    -srcstorepass "$CERT_PASSWORD" \
    -destkeystore "$CERT_DIR/kafka_broker.keystore.jks" \
    -deststoretype JKS \
    -deststorepass "$CERT_PASSWORD" \
    -noprompt

  # Create truststore with CA certificate (remove old one first)
  rm -f "$CERT_DIR/kafka.truststore.jks"
  keytool -import -trustcacerts \
    -alias ca-root \
    -file "$CERT_DIR/ca-root.pem" \
    -keystore "$CERT_DIR/kafka.truststore.jks" \
    -storepass "$CERT_PASSWORD" \
    -noprompt

  # Create credential files for Kafka
  echo "$CERT_PASSWORD" > "$CERT_DIR/kafka_keystore_creds"
  echo "$CERT_PASSWORD" > "$CERT_DIR/kafka_ssl_key_creds"
  echo "$CERT_PASSWORD" > "$CERT_DIR/kafka_truststore_creds"

  log_success "Kafka certificate created"
  echo
}

# Verify certificates
verify_certificates() {
  log_info "Verifying certificates..."

  local all_valid=1

  # Verify Kafka certificate
  if openssl verify -CAfile "$CERT_DIR/ca-root.pem" "$CERT_DIR/kafka_broker.pem" >/dev/null 2>&1; then
    log_success "Kafka certificate valid"
  else
    log_error "Kafka certificate invalid"
    all_valid=0
  fi

  # Verify Keycloak certificate
  if openssl verify -CAfile "$CERT_DIR/ca-root.pem" "$CERT_DIR/keycloak_server.pem" >/dev/null 2>&1; then
    log_success "Keycloak certificate valid"
  else
    log_error "Keycloak certificate invalid"
    all_valid=0
  fi

  # Check certificate extensions
  log_info "Checking certificate extensions..."

  # Check CA extensions
  if openssl x509 -in "$CERT_DIR/ca-root.pem" -text -noout | grep -q "CA:TRUE"; then
    log_success "CA certificate has basicConstraints=CA:TRUE"
  else
    log_error "CA certificate missing CA constraint"
    all_valid=0
  fi

  # Check Kafka SANs
  if openssl x509 -in "$CERT_DIR/kafka_broker.pem" -text -noout | grep -q "DNS:kafka.example"; then
    log_success "Kafka certificate has Subject Alternative Names"
  else
    log_error "Kafka certificate missing SANs"
    all_valid=0
  fi

  # Check Keycloak SANs
  if openssl x509 -in "$CERT_DIR/keycloak_server.pem" -text -noout | grep -q "DNS:keycloak.example"; then
    log_success "Keycloak certificate has Subject Alternative Names"
  else
    log_error "Keycloak certificate missing SANs"
    all_valid=0
  fi

  echo

  if [[ $all_valid -eq 1 ]]; then
    return 0
  else
    return 1
  fi
}

# Main execution
main() {
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Certificate Generation using Cosmian KMS"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo

  check_prerequisites
  create_root_ca
  create_keycloak_cert
  create_kafka_cert

  if verify_certificates; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_success "Certificate generation complete!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 0
  else
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_error "Certificate generation completed with warnings"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 1
  fi
}

main "$@"
