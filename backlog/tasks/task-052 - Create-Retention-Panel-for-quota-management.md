---
id: task-052
title: Create Retention Panel for quota management
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 18:31'
labels:
  - frontend
  - retention
  - sprint-5
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Build the retention management interface showing current database usage, configured policies, and controls for adjusting retention settings. This enables operators to manage storage limits and data retention policies.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Current database size displayed with visual progress bar
- [ ] #2 Percentage of max_bytes quota shown if configured
- [ ] #3 Current TTL (max_age_days) displayed
- [ ] #4 Editable form for updating max_bytes and max_age_days
- [ ] #5 Form validation ensures positive values
- [ ] #6 Save button calls PUT /api/config/retention
- [ ] #7 Success/error feedback on save attempts
- [ ] #8 Visual warning when approaching storage limits (>80%)
- [ ] #9 Last purge timestamp and statistics displayed
<!-- AC:END -->
