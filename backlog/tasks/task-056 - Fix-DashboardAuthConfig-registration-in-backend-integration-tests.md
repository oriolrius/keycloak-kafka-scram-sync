---
id: task-056
title: Fix DashboardAuthConfig registration in backend integration tests
status: In Progress
assignee:
  - '@assistant'
created_date: '2025-11-05 20:49'
updated_date: '2025-11-05 20:55'
labels:
  - bug
  - testing
  - configuration
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
All backend integration tests are currently failing with "Could not find a mapping for com.miimetiq.keycloak.sync.security.DashboardAuthConfig". The @ConfigMapping annotation requires proper registration in the Quarkus test configuration. This blocks all backend integration test execution.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend integration tests can start successfully
- [ ] #2 DashboardAuthConfig is properly registered in test environment
- [ ] #3 All previously passing backend integration tests pass again
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Identify the root cause of DashboardAuthConfig registration failure
2. Try adding complete dashboard configuration properties to test resources
3. Test if empty basic-auth property helps register ConfigMapping
4. Research Quarkus @ConfigMapping test configuration patterns
5. Consider alternative solutions (conditional bean, different injection pattern)
6. Document findings and recommended fixes for maintainer
<!-- SECTION:PLAN:END -->
