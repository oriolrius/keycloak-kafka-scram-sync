---
id: task-054
title: Add force reconcile functionality to dashboard
status: Done
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 20:40'
labels:
  - backend
  - frontend
  - reconciliation
  - sprint-5
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the manual sync trigger button on the dashboard that allows operators to force an immediate reconciliation cycle outside of the normal schedule. This is useful for testing or responding to urgent sync requirements.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 POST /api/reconcile/trigger endpoint implemented in backend
- [x] #2 Endpoint validates authentication and authorization
- [x] #3 Endpoint enqueues immediate reconciliation job
- [x] #4 Returns correlation_id for tracking the triggered reconciliation
- [x] #5 Force Reconcile button visible on dashboard
- [x] #6 Button disabled during active reconciliation
- [x] #7 Success notification shows correlation_id
- [x] #8 Error notification on failure with error details
- [x] #9 Button re-enables after reconciliation completes
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing codebase to understand the reconciliation architecture
2. Verify backend API endpoint (/api/reconcile/trigger) implementation
3. Verify authentication and authorization in DashboardAuthFilter
4. Verify frontend Force Reconcile button and status polling
5. Ensure success notification displays correlation_id
6. Test the complete flow (button → API → reconciliation → notification)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

The force reconcile functionality was already fully implemented in the codebase. I verified all components and made one enhancement to ensure complete compliance with acceptance criteria.

## Changes Made

### Frontend Enhancement
- **File**: `frontend/src/pages/Dashboard.tsx:110`
- **Change**: Updated success notification to display the `correlationId` in the message
- **Before**: Showed only successful ops, failed ops, and duration
- **After**: Now includes "Correlation ID: {correlationId}" for full traceability

## Verification of Existing Implementation

### Backend Components (Already Implemented)
1. **POST /api/reconcile/trigger endpoint** - `ReconciliationResource.java:34-67`
   - Returns 202 Accepted with TriggerResponse containing correlation_id, operation counts, and duration
   - Returns 409 Conflict if reconciliation already running
   - Returns 500 Internal Server Error on failure

2. **Authentication & Authorization** - `DashboardAuthFilter.java:39-103`
   - Supports both OIDC (Bearer token) and Basic Auth
   - Role-based access control with configurable required role (default: "dashboard-admin")
   - Applied to all /api/* endpoints

3. **Reconciliation Execution** - `ReconciliationScheduler.java`
   - `triggerManualReconciliation()` method with overlap prevention using AtomicBoolean
   - Throws ReconciliationInProgressException if already running
   - Calls `ReconciliationService.performReconciliation("MANUAL")`

4. **Correlation ID Generation** - `ReconciliationService.java:320`
   - Generated using `UUID.randomUUID()` 
   - Stored with all SyncBatch and SyncOperation records for audit trail

### Frontend Components (Already Implemented)
1. **Force Reconcile Button** - `Dashboard.tsx:93-100`
   - Visible on main dashboard
   - Disabled when `reconcileTrigger.isPending` or `reconcileStatus.running`
   - Shows spinner icon and "Reconciliation Running..." text when active

2. **Status Polling** - `useReconcile.ts:5-12`
   - Polls `/api/reconcile/status` every 5 seconds
   - Updates button state in real-time

3. **Success Notification** - `Dashboard.tsx:103-114`
   - Green card displayed on successful reconciliation
   - Shows correlation ID, operation counts, and duration

4. **Error Notification** - `Dashboard.tsx:116-128`
   - Red card displayed on failure
   - Shows detailed error message from backend

5. **Query Invalidation** - `useReconcile.ts:19-24`
   - Invalidates ['reconcile', 'status'], ['summary'], ['operations'], ['batches'] queries
   - Ensures dashboard refreshes with latest data after reconciliation

## Testing Notes

All acceptance criteria verified:
- ✅ Backend endpoint implemented with proper response codes
- ✅ Authentication and authorization enforced via DashboardAuthFilter
- ✅ Reconciliation job execution with overlap prevention
- ✅ Correlation ID generation and tracking
- ✅ UI button with proper state management
- ✅ Button disabled during active reconciliation
- ✅ Success notification with correlation ID display
- ✅ Error notification with detailed error messages
- ✅ Button re-enables after completion via status polling

The functionality is production-ready and requires no additional work beyond the correlation ID display enhancement.
<!-- SECTION:NOTES:END -->
