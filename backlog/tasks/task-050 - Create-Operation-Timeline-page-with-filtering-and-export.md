---
id: task-050
title: Create Operation Timeline page with filtering and export
status: In Progress
assignee:
  - '@assistant'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 17:52'
labels:
  - frontend
  - operations
  - sprint-5
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Build the operation timeline page showing detailed history of all sync operations with filtering, sorting, and CSV export capabilities. This allows operators to audit and troubleshoot sync activities.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Paginated table displays operation history with shadcn/ui Table component
- [ ] #2 Columns show timestamp, realm, principal, operation type, result, duration, and error details
- [ ] #3 Filter controls for time range, principal, op_type, and result status
- [ ] #4 Sorting capability on key columns (timestamp, duration, principal)
- [ ] #5 Pagination controls with page size selector
- [ ] #6 CSV export button downloads filtered results
- [ ] #7 Error details expandable for failed operations
- [ ] #8 Real-time updates via TanStack Query polling
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Explore existing frontend structure and API endpoints
2. Create OperationsPage component with shadcn/ui Table
3. Implement filtering controls (time range, principal, op_type, result)
4. Add sorting functionality on key columns
5. Implement pagination with page size selector
6. Add CSV export functionality
7. Create expandable error details for failed operations
8. Configure TanStack Query with polling for real-time updates
9. Add routing and navigation
10. Test all features
<!-- SECTION:PLAN:END -->
