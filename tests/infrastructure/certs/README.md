# Certificate Management with Cosmian KMS

This directory contains certificates for the Keycloak-Kafka testing environment, generated using the Cosmian Key Management Service (KMS).

## Overview

All certificates are generated using the `regenerate-certs.sh` script, which leverages the Cosmian KMS for secure certificate generation with proper X.509 extensions.

## Files

### Certificates
- `ca-root.pem` - Root CA certificate (RSA 4096-bit, 10 years validity)
- `keycloak_server.pem` / `keycloak_server.p12` - Keycloak server certificate
- `keycloak.crt` / `keycloak.key` - Keycloak certificate in separate files
- `kafka_broker.pem` / `kafka_broker.p12` - Kafka broker certificate

### Keystores
- `kafka_broker.keystore.jks` - Kafka broker keystore (JKS format)
- `kafka.truststore.jks` - Kafka truststore with CA certificate

### Credentials
- `kafka_keystore_creds` - Keystore password file
- `kafka_ssl_key_creds` - SSL key password file
- `kafka_truststore_creds` - Truststore password file

### Extension Files
- `ca.ext` - X.509 extensions for CA certificate
- `keycloak.ext` - X.509 extensions for Keycloak certificate (SANs, key usage)
- `kafka.ext` - X.509 extensions for Kafka certificate (SANs, key usage)

## Certificate Generation

### Prerequisites

1. KMS service must be running:
   ```bash
   docker compose up -d kms
   ```

2. Required tools:
   - `ckms` CLI (Cosmian KMS client)
   - `keytool` (Java JRE/JDK)
   - `openssl`

### Generate Certificates

Using the script directly:
```bash
./certs/regenerate-certs.sh
```

Or via Makefile:
```bash
make certs
```

### Script Options

```bash
./regenerate-certs.sh [OPTIONS]

Options:
  --kms-url URL      KMS server URL (default: http://localhost:57001)
  --password PASS    Certificate password (default: The2password.)
  --help             Show help message
```

### Environment Variables

- `KMS_URL` - KMS server URL
- `CERT_PASSWORD` - Certificate password
- `CERT_ORG` - Organization name
- `CERT_COUNTRY` - Country code
- `DOMAIN` - Domain suffix

## Certificate Details

### Root CA
- **Algorithm**: RSA 4096
- **Validity**: 10 years (3650 days)
- **Extensions**:
  - basicConstraints=CA:TRUE
  - keyUsage=critical,keyCertSign,digitalSignature
  - subjectKeyIdentifier=hash

### Server Certificates (Keycloak & Kafka)
- **Algorithm**: RSA 2048
- **Validity**: 1 year (365 days)
- **Extensions**:
  - basicConstraints=CA:FALSE
  - keyUsage=digitalSignature,keyEncipherment
  - extendedKeyUsage=serverAuth,clientAuth
  - subjectAltName=DNS:...,IP:...

### Subject Alternative Names (SANs)

**Kafka**:
- DNS: kafka.example, kafka, broker, localhost
- IP: 127.0.0.1

**Keycloak**:
- DNS: keycloak.example, keycloak, localhost
- IP: 127.0.0.1

## Verification

### Verify Certificate Chain
```bash
openssl verify -CAfile ca-root.pem kafka_broker.pem
openssl verify -CAfile ca-root.pem keycloak_server.pem
```

### Inspect Certificate Details
```bash
openssl x509 -in kafka_broker.pem -text -noout
openssl x509 -in keycloak_server.pem -text -noout
```

### Check Extensions
```bash
openssl x509 -in kafka_broker.pem -text -noout | grep -A3 "Extended Key Usage"
openssl x509 -in kafka_broker.pem -text -noout | grep -A5 "Subject Alternative Name"
```

### Verify Keystore Contents
```bash
keytool -list -v -keystore kafka_broker.keystore.jks -storepass The2password.
keytool -list -v -keystore kafka.truststore.jks -storepass The2password.
```

## KMS Certificate Management

### List Certificates in KMS
```bash
ckms --kms-url http://localhost:57001 kms certificates list
```

### Export Certificate
```bash
ckms --kms-url http://localhost:57001 kms certificates export \
  --certificate-id ca-root \
  --format pem \
  ca-root.pem
```

### Revoke Certificate
```bash
ckms --kms-url http://localhost:57001 kms certificates revoke \
  --certificate-id kafka_broker
```

## Security Notes

- All private keys are generated and stored in KMS
- Default password: `The2password.` (for development only)
- Certificates are tagged with `testing-env` for easy management
- Never commit credential files to version control
- Rotate certificates regularly in production environments

## Troubleshooting

### KMS Not Running
```bash
curl -sf http://localhost:57001/version
docker compose ps kms
docker compose logs kms
```

### Certificate Not Found
```bash
ckms --kms-url http://localhost:57001 kms certificates list
```

### Regeneration Issues
1. Ensure KMS is running
2. Check that all required tools are installed
3. Verify .ext files exist and are properly formatted
4. Check logs for detailed error messages

## Migration from OpenSSL

This certificate generation system has been migrated from OpenSSL-based scripts to KMS-based generation:

**Benefits**:
- Centralized key management
- Better security with keys stored in KMS
- Automated certificate lifecycle management
- Proper X.509 extension support via .ext files
- Easier certificate rotation

**Compatibility**:
- All certificates maintain the same structure and properties
- Existing services (Kafka, Keycloak) work without changes
- Certificate formats (PEM, PKCS12, JKS) remain the same
