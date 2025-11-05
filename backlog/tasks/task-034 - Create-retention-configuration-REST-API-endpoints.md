---
id: task-034
title: Create retention configuration REST API endpoints
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 09:38'
labels:
  - sprint-3
  - retention
  - api
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement GET and PUT endpoints at /api/config/retention for reading and updating retention policies (max_bytes, max_age_days). These endpoints allow operators to dynamically adjust retention settings without restarting the service.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET /api/config/retention returns current retention_state (max_bytes, max_age_days, approx_db_bytes, updated_at)
- [x] #2 PUT /api/config/retention accepts JSON with max_bytes and/or max_age_days
- [x] #3 PUT endpoint validates input (non-negative values, reasonable limits)
- [x] #4 PUT endpoint updates retention_state table and returns updated config
- [x] #5 Endpoints documented in OpenAPI specification
- [x] #6 Integration tests verify GET and PUT operations
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create RetentionConfigResource.java following ReconciliationResource pattern
   - Use @Path("/api/config/retention")
   - Inject RetentionService via CDI
   
2. Implement GET endpoint
   - Call retentionService.getRetentionState()
   - Return DTO with max_bytes, max_age_days, approx_db_bytes, updated_at
   
3. Implement PUT endpoint with validation
   - Accept RetentionConfigUpdateRequest DTO
   - Validate: non-negative values, reasonable limits (max_bytes < 10GB, max_age_days < 3650)
   - Call retentionService.updateRetentionConfig()
   - Return updated configuration
   
4. Add OpenAPI annotations (@Operation, @APIResponse)
   
5. Create integration test RetentionConfigResourceIntegrationTest
   - Test GET returns current config
   - Test PUT updates config
   - Test PUT validation (negative values, unreasonable limits)
   - Test PUT partial updates (only max_bytes or only max_age_days)
<!-- SECTION:PLAN:END -->
