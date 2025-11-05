---
id: task-049
title: Create Dashboard page with summary cards and charts
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 17:49'
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
- [x] #1 Dashboard layout created with shadcn/ui Card components
- [x] #2 Summary cards display ops/hour, error rate, and latency metrics
- [x] #3 Connection status indicators show Kafka and Keycloak connectivity with timestamps
- [x] #4 Manual 'Force Reconcile' button triggers immediate sync via API
- [x] #5 Trend charts display 24h/72h operations volume using Recharts or Chart.js
- [x] #6 Error rate chart shows historical error trends
- [x] #7 All data updates automatically via TanStack Query polling
- [x] #8 Loading and error states display appropriately
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Explore existing frontend structure and available components
2. Check API endpoints available for dashboard data (metrics, health, operations)
3. Create dashboard page component at frontend/src/pages/Dashboard.tsx
4. Create API query hooks for:
   - Health/connection status
   - Metrics (ops/hour, error rate, latency)
   - Historical data for charts
   - Force reconcile trigger
5. Install and configure Recharts library
6. Implement summary cards section with shadcn/ui Card components
7. Implement connection status indicators section
8. Implement Force Reconcile button with mutation
9. Create chart components for operations volume and error rate trends
10. Add TanStack Query polling configuration for auto-updates
11. Implement loading skeletons and error states
12. Test all functionality and verify data updates
<!-- SECTION:PLAN:END -->
