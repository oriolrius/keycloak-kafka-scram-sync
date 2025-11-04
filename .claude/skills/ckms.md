# CKMS (Cosmian KMS CLI) Skill

## Overview
You are an expert in using the Cosmian KMS (Key Management Service) CLI tool (`ckms`) for certificate and key management operations. This skill provides comprehensive knowledge for PKI operations, certificate lifecycle management, and secure key storage.

## Tool Location
- **Binary**: Available at `contrib/ckms` in the project root
- **Global Access**: Symlink to `~/.local/bin/ckms` (must be in PATH)
- **Version**: 5.9.0+ (check with `ckms --help`)

## Core Command Structure

```bash
ckms [OPTIONS] <COMMAND>

# Main commands:
ckms kms <subcommand>     # KMS operations
ckms findex <subcommand>  # Findex server operations
```

### Global Options
- `--kms-url <URL>`: KMS server URL (e.g., http://localhost:9998)
- `--conf-path <PATH>`: Configuration file location
- `--accept-invalid-certs`: Allow self-signed certificates
- `--kms-print-json`: Show JSON KMIP request/response

## Certificate Management

### Creating Certificates

#### Root CA (Self-Signed)
```bash
ckms --kms-url http://localhost:57001 kms certificates certify \
  --certificate-id ca-root \
  --generate-key-pair \
  --subject-name "CN=Root CA,O=Organization,C=US" \
  --algorithm rsa4096 \
  --days 3650 \
  --tag production
```

#### Server Certificate (Signed by CA)
```bash
ckms --kms-url http://localhost:57001 kms certificates certify \
  --certificate-id server-cert \
  --issuer-certificate-id ca-root \
  --generate-key-pair \
  --subject-name "CN=server.example.com,O=Organization,C=US" \
  --algorithm rsa2048 \
  --days 365 \
  --tag production
```

### Key Parameters
- `--certificate-id`: Unique identifier for the certificate
- `--issuer-certificate-id`: Parent CA certificate ID (omit for self-signed)
- `--generate-key-pair`: Automatically generate private/public key pair
- `--subject-name`: X.509 subject DN (e.g., "CN=name,O=org,C=country")
- `--algorithm`: Key algorithm (rsa2048, rsa4096, ec256, ec384, etc.)
- `--days`: Certificate validity period
- `--tag`: Metadata tag for grouping/searching certificates

### Exporting Certificates

#### PEM Format
```bash
ckms --kms-url http://localhost:57001 kms certificates export \
  --certificate-id server-cert \
  --format pem \
  /path/to/output.pem
```

#### PKCS12 Format (with password)
```bash
ckms --kms-url http://localhost:57001 kms certificates export \
  --certificate-id server-cert \
  --format pkcs12 \
  --pkcs12-password "SecurePassword123" \
  /path/to/output.p12
```

### Certificate Lifecycle

#### List Certificates
```bash
ckms --kms-url http://localhost:57001 kms certificates list
```

#### Locate by Tag
```bash
ckms --kms-url http://localhost:57001 kms locate --tag production
```

#### Revoke Certificate
```bash
ckms --kms-url http://localhost:57001 kms certificates revoke \
  --certificate-id server-cert
```

#### Destroy Certificate
```bash
ckms --kms-url http://localhost:57001 kms certificates destroy \
  --certificate-id server-cert
```

## Common Workflows

### Complete CA Setup
```bash
KMS_URL="http://localhost:57001"

# 1. Create Root CA
ckms --kms-url $KMS_URL kms certificates certify \
  --certificate-id root-ca \
  --generate-key-pair \
  --subject-name "CN=Root CA,O=MyOrg,C=US" \
  --algorithm rsa4096 \
  --days 3650 \
  --tag ca-infrastructure

# 2. Export Root CA
ckms --kms-url $KMS_URL kms certificates export \
  --certificate-id root-ca \
  --format pem \
  ./certs/root-ca.pem

# 3. Create Server Certificate
ckms --kms-url $KMS_URL kms certificates certify \
  --certificate-id web-server \
  --issuer-certificate-id root-ca \
  --generate-key-pair \
  --subject-name "CN=web.example.com,O=MyOrg,C=US" \
  --algorithm rsa2048 \
  --days 365 \
  --tag servers

# 4. Export Server Certificate
ckms --kms-url $KMS_URL kms certificates export \
  --certificate-id web-server \
  --format pkcs12 \
  --pkcs12-password "password" \
  ./certs/web-server.p12
```

### Certificate Rotation
```bash
# 1. Revoke old certificate
ckms --kms-url $KMS_URL kms certificates revoke --certificate-id old-cert

# 2. Create new certificate
ckms --kms-url $KMS_URL kms certificates certify \
  --certificate-id new-cert \
  --issuer-certificate-id ca-root \
  --generate-key-pair \
  --subject-name "CN=service.example.com" \
  --algorithm rsa2048 \
  --days 365

# 3. Export new certificate
ckms --kms-url $KMS_URL kms certificates export \
  --certificate-id new-cert \
  --format pkcs12 \
  --pkcs12-password "password" \
  ./certs/new-cert.p12

# 4. Destroy old certificate (optional, after verification)
ckms --kms-url $KMS_URL kms certificates destroy --certificate-id old-cert
```

## Integration with Docker Compose

When working with Docker Compose services:

```bash
# Always check KMS is running first
curl -sf http://localhost:57001/version || echo "KMS not running"

# Use consistent naming
CERT_ID="service-name_component"  # e.g., kafka_broker, keycloak_server

# Clean up existing certificates before creating new ones
ckms --kms-url $KMS_URL kms certificates revoke --certificate-id $CERT_ID 2>/dev/null || true
ckms --kms-url $KMS_URL kms certificates destroy --certificate-id $CERT_ID 2>/dev/null || true

# Create fresh certificate
ckms --kms-url $KMS_URL kms certificates certify ...
```

## Best Practices

### 1. Certificate ID Naming
- Use descriptive, hierarchical names: `service_component`
- Examples: `kafka_broker`, `keycloak_server`, `sync_agent`
- Avoid spaces and special characters

### 2. Tag Management
- Use tags for logical grouping: `--tag production`, `--tag testing-env`
- Tag by purpose: `--tag ca-infrastructure`, `--tag servers`, `--tag clients`
- Use tags for bulk operations and auditing

### 3. Key Algorithms
- **RSA 4096**: Root CAs (long-term trust anchors)
- **RSA 2048**: Server/client certificates (good balance)
- **EC 256/384**: Modern alternatives, smaller keys, same security

### 4. Certificate Lifetimes
- **Root CA**: 10+ years (3650 days)
- **Intermediate CA**: 5 years (1825 days)
- **Server Certificates**: 1 year (365 days)
- **Client Certificates**: 90-365 days

### 5. Error Handling
```bash
# Always handle errors gracefully
(ckms --kms-url $KMS_URL kms certificates revoke --certificate-id $ID 2>/dev/null || true)

# Check command success
if ckms --kms-url $KMS_URL kms certificates export ...; then
  echo "✓ Export successful"
else
  echo "✗ Export failed"
  exit 1
fi
```

### 6. Security
- **Never** commit private keys or PKCS12 files to Git
- Use environment variables for passwords: `--pkcs12-password "$CERT_PASSWORD"`
- Store credentials in secure locations with restricted permissions
- Rotate certificates regularly

## Docker Compose Considerations

### Important Rules
1. **Use docker compose commands**: `docker compose ps`, not `docker ps`
2. **Don't affect other containers**: Only operate on containers in current compose project
3. **Check KMS availability**: Verify KMS is responding before certificate operations
4. **Path considerations**: Use relative paths from working directory

### Example Docker Compose Integration
```yaml
services:
  kms:
    image: ghcr.io/cosmian/kms:latest
    ports:
      - "57001:9998"
    # No healthcheck needed if using external verification

  service:
    depends_on:
      - kms  # Simple dependency, no health check
    volumes:
      - ./certs:/etc/certs:ro
```

## Troubleshooting

### KMS Not Responding
```bash
# Check KMS is running
curl -s http://localhost:57001/version

# Check Docker Compose status
docker compose ps

# View KMS logs
docker compose logs kms --tail 50
```

### Certificate Not Found
```bash
# List all certificates
ckms --kms-url http://localhost:57001 kms certificates list

# Search by tag
ckms --kms-url http://localhost:57001 kms locate --tag your-tag
```

### Export Failures
- Verify certificate exists: `ckms kms certificates list`
- Check output directory exists and is writable
- Ensure KMS URL is correct
- For PKCS12, verify password is provided

### Certificate Validation Errors
```bash
# Verify certificate chain with openssl
openssl verify -CAfile root-ca.pem server-cert.pem

# Check certificate details
openssl x509 -in cert.pem -text -noout
```

## Common Errors and Solutions

| Error | Solution |
|-------|----------|
| `certificate not found` | Verify cert ID with `kms certificates list` |
| `connection refused` | Check KMS is running: `curl http://localhost:57001/version` |
| `command not found: ckms` | Add to PATH: `export PATH="$HOME/.local/bin:$PATH"` |
| `invalid algorithm` | Use: rsa2048, rsa4096, ec256, ec384 |
| `pkcs12 password required` | Add `--pkcs12-password "password"` flag |

## Quick Reference Card

```bash
# Certificate Lifecycle
Create:   ckms --kms-url URL kms certificates certify --certificate-id ID --generate-key-pair ...
List:     ckms --kms-url URL kms certificates list
Export:   ckms --kms-url URL kms certificates export --certificate-id ID --format pem OUTPUT
Revoke:   ckms --kms-url URL kms certificates revoke --certificate-id ID
Destroy:  ckms --kms-url URL kms certificates destroy --certificate-id ID

# Search & Locate
By Tag:   ckms --kms-url URL kms locate --tag TAG

# Common Flags
--generate-key-pair               Generate new key pair
--issuer-certificate-id ID        Parent CA (omit for self-signed)
--subject-name "CN=...,O=...,C=.."  X.509 subject
--algorithm rsa2048               Key algorithm
--days 365                        Validity period
--tag TAG                         Metadata tag
--format pem|pkcs12               Export format
--pkcs12-password PASS            PKCS12 password
```

## Related Documentation
- KMS Server: http://localhost:57001 (when running)
- Cosmian Docs: https://docs.cosmian.com
- Testing Workflow: `/home/oriol/miimetiq3/server/tests/kms/test-cert-workflow.sh`
