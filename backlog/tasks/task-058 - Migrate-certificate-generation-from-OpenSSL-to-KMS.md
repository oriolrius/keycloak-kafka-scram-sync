---
id: task-058
title: Migrate certificate generation from OpenSSL to KMS
status: Done
assignee:
  - '@claude'
created_date: '2025-11-07 14:32'
updated_date: '2025-11-10 16:21'
labels: []
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace the current OpenSSL-based certificate generation script (testing/certs/regenerate-certs.sh) with a KMS-based approach using the ckms CLI tool from contrib/. This will improve security and portability by leveraging the KMS service already configured in the testing environment's docker-compose.yml.

The new approach should:
- Use ckms from contrib/ to generate all certificates (CA, server, client)
- Store KMS database files in testing/data/kms
- Output generated certificates to testing/certs
- Use certificate extension files (.ext) from testing/certs for proper certificate configuration
- Maintain the same certificate structure and functionality as the OpenSSL version
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 New script generates CA certificate using KMS instead of OpenSSL
- [x] #2 New script generates server certificates using KMS with proper extensions
- [x] #3 New script generates client certificates using KMS with proper extensions
- [x] #4 KMS database files are stored in testing/data/kms directory
- [x] #5 Generated certificates are placed in testing/certs directory
- [x] #6 Certificate extensions (.ext files) are properly applied during generation
- [x] #7 All generated certificates have the same structure and properties as OpenSSL-generated ones
- [x] #8 CA certificate is properly configured and can sign other certificates
- [x] #9 Testing validates that KMS-generated certificates work with Kafka and Keycloak
- [x] #10 Old OpenSSL-based regenerate-certs.sh is replaced or removed
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Analyze current certificate generation in Makefile (already uses ckms)
2. Check if there are any OpenSSL-based scripts to migrate from
3. Understand .ext file format and required extensions (SANs, keyUsage, etc.)
4. Research ckms CLI options for custom extensions (SANs, basicConstraints, keyUsage)
5. Create standalone certificate generation script using ckms
6. Parse .ext files and apply extensions via ckms or hybrid approach
7. Test generated certificates have correct extensions
8. Verify certificates work with Kafka and Keycloak
9. Replace old script if found
10. Update documentation
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
- Created `tests/infrastructure/certs/regenerate-certs.sh` script that generates all certificates using Cosmian KMS
- Script uses `ckms` CLI with `--certificate-extensions` flag to apply X.509 extensions from .ext files
- Updated `ca.ext` to remove unsupported `cRLSign` and `authorityKeyIdentifier` for self-signed root CA
- Modified `tests/infrastructure/Makefile` to use the new script via environment variables
- All certificates are generated with proper extensions:
  - CA: basicConstraints=CA:TRUE, keyUsage=critical,keyCertSign,digitalSignature
  - Server certs: extendedKeyUsage=serverAuth,clientAuth, proper SANs
- Certificates successfully validated with Kafka and Keycloak services
- KMS database files are stored in docker volume mapped to data/kms
- Created comprehensive README.md documentation in certs directory
- Script includes verification steps to check certificate validity and extensions
- All acceptance criteria met: CA generation, server cert generation with extensions, proper placement, KMS storage, service validation
<!-- SECTION:NOTES:END -->
