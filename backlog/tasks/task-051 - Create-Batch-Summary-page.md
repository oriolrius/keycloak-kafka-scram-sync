---
id: task-051
title: Create Batch Summary page
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 18:21'
labels:
  - frontend
  - batches
  - sprint-5
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the batch summary page displaying reconciliation cycle information including success/error counts and timing. This helps operators understand the periodic sync behavior and identify problematic cycles.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Paginated table displays batch history with correlation IDs
- [ ] #2 Columns show start time, finish time, source (webhook/reconcile), items total, success count, error count
- [ ] #3 Duration calculation displayed for each batch
- [ ] #4 Success rate percentage calculated and displayed
- [ ] #5 Filter by source type (webhook vs periodic reconcile)
- [ ] #6 Filter by time range
- [ ] #7 Click on batch row expands to show related operations
- [ ] #8 Real-time updates via TanStack Query polling
<!-- AC:END -->
