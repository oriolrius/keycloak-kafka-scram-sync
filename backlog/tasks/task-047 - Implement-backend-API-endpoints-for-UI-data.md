---
id: task-047
title: Implement backend API endpoints for UI data
status: To Do
assignee: []
created_date: '2025-11-05 16:55'
labels:
  - backend
  - api
  - sprint-5
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the RESTful API endpoints that the frontend will consume for displaying sync operations, batches, and configuration. These endpoints provide summary statistics, paginated operation history, and retention management.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/summary returns summary statistics (ops/hour, error rate, latency p95/p99, DB usage)
- [ ] #2 GET /api/operations returns paginated operation timeline with filters (time range, principal, op_type, result)
- [ ] #3 GET /api/batches returns paginated batch summaries
- [ ] #4 GET /api/config/retention returns current retention policies
- [ ] #5 PUT /api/config/retention updates retention policies with validation
- [ ] #6 All endpoints documented with OpenAPI annotations
- [ ] #7 Endpoints return proper HTTP status codes and error messages
<!-- AC:END -->
