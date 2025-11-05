---
id: task-049
title: Create Dashboard page with summary cards and charts
status: To Do
assignee: []
created_date: '2025-11-05 16:55'
labels:
  - frontend
  - dashboard
  - sprint-5
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the main dashboard page showing key performance indicators, connection status, and trend visualizations. This is the landing page that provides at-a-glance insights into sync agent health and performance.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Dashboard layout created with shadcn/ui Card components
- [ ] #2 Summary cards display ops/hour, error rate, and latency metrics
- [ ] #3 Connection status indicators show Kafka and Keycloak connectivity with timestamps
- [ ] #4 Manual 'Force Reconcile' button triggers immediate sync via API
- [ ] #5 Trend charts display 24h/72h operations volume using Recharts or Chart.js
- [ ] #6 Error rate chart shows historical error trends
- [ ] #7 All data updates automatically via TanStack Query polling
- [ ] #8 Loading and error states display appropriately
<!-- AC:END -->
