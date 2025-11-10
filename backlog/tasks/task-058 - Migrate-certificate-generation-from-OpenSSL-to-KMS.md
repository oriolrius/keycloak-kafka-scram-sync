---
id: task-058
title: Migrate certificate generation from OpenSSL to KMS
status: To Do
assignee: []
created_date: '2025-11-07 14:32'
updated_date: '2025-11-10 16:14'
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
- [ ] #1 New script generates CA certificate using KMS instead of OpenSSL
- [ ] #2 New script generates server certificates using KMS with proper extensions
- [ ] #3 New script generates client certificates using KMS with proper extensions
- [ ] #4 KMS database files are stored in testing/data/kms directory
- [ ] #5 Generated certificates are placed in testing/certs directory
- [ ] #6 Certificate extensions (.ext files) are properly applied during generation
- [ ] #7 All generated certificates have the same structure and properties as OpenSSL-generated ones
- [ ] #8 CA certificate is properly configured and can sign other certificates
- [ ] #9 Testing validates that KMS-generated certificates work with Kafka and Keycloak
- [ ] #10 Old OpenSSL-based regenerate-certs.sh is replaced or removed
<!-- AC:END -->
