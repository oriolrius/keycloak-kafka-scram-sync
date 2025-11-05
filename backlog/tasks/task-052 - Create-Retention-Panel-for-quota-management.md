---
id: task-052
title: Create Retention Panel for quota management
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 18:33'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review backend API at /api/config/retention (GET and PUT endpoints) - already implemented with validation
2. Review existing frontend infrastructure - useRetentionConfig() hook, apiClient methods exist
3. Fix RetentionConfig type in frontend/src/types/api.ts to match backend:
   - Replace maxRecords and cleanupIntervalHours with maxBytes, approxDbBytes, updatedAt
4. Create RetentionPanel component at frontend/src/components/RetentionPanel.tsx:
   - Display current DB size with progress bar
   - Show percentage of max_bytes quota if configured
   - Show max_age_days (TTL) if configured
   - Editable form for maxBytes and maxAgeDays
   - Form validation (positive values, maxBytes <= 10GB, maxAgeDays <= 3650)
   - Save button calls PUT /api/config/retention via useUpdateRetentionConfig()
   - Success/error toast feedback
   - Warning badge when >80% of storage limit
   - Display last update timestamp
5. Integrate RetentionPanel into Dashboard page (add new card section)
6. Test manually with dev server
7. Create Playwright UI tests for RetentionPanel
8. Run tests and fix any issues
<!-- SECTION:PLAN:END -->
