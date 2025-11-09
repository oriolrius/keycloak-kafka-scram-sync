---
id: task-066
title: Document direct Kafka SPI architecture decision
status: Done
assignee:
  - '@claude'
created_date: '2025-11-09 11:19'
updated_date: '2025-11-09 13:12'
labels:
  - documentation
  - architecture
dependencies:
  - task-065
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create architectural decision record documenting the shift from webhook/cache to direct Kafka SPI. Capture rationale (real-time sync, no cache expiration, simpler architecture), trade-offs (Kafka downtime affects password changes, network dependency), and comparison with original approach. Update README with new architecture diagram and configuration.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Decision document created in backlog/decisions/
- [x] #2 Document explains why direct SPI approach was chosen
- [x] #3 Trade-offs and failure scenarios documented
- [x] #4 Architecture diagram shows direct Keycloak→Kafka flow
- [x] #5 README updated with new ENV variables for SPI Kafka config
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing decision documents to understand format
2. Create new decision document for direct Kafka SPI architecture
3. Document rationale (why we chose direct SPI over webhook/cache)
4. Document trade-offs and failure scenarios
5. Create architecture diagram showing Keycloak→Kafka flow
6. Read current README to understand structure
7. Update README with new architecture explanation
8. Document new SPI environment variables in README
9. Mark all acceptance criteria as complete
10. Add implementation notes
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Successfully documented the direct Kafka SPI architecture decision and updated project documentation.

**Decision Document Created:**

Created `decision-003 - Direct Kafka SPI Architecture.md` in `backlog/decisions/`:
- Comprehensive architecture decision record following established format
- Documented the evolution from webhook/cache to direct Kafka sync
- Explained rationale: real-time consistency, simplified architecture, better security
- Documented trade-offs: Kafka availability critical, network dependency
- Included detailed failure scenarios and recovery procedures
- Added architecture diagrams comparing old vs. new approach
- Referenced related tasks and decision-002

**Key Sections in Decision Document:**
1. Executive Summary - Quick overview of decision and impact
2. Context and Problem - Problems with original webhook/cache approach
3. Decision - New direct Kafka sync architecture
4. Rationale - Why this approach is better
5. Trade-offs - Advantages and disadvantages with mitigations
6. Implementation Details - SPI components and configuration
7. Failure Scenarios - How system behaves when Kafka is unavailable
8. Migration Path - Tasks 063-066 implementation phases

**README Updates:**

Updated `README.md` with new architecture information:

1. **Introduction Section**:
   - Added architecture note explaining immediate sync via direct Kafka connection
   - Referenced decision-003 for detailed architecture decision record

2. **Features Section**:
   - Changed "Real-time Event Processing" to "Immediate Password Sync"
   - Changed "Periodic Reconciliation" to "Manual Reconciliation"
   - Emphasized direct Kafka sync with no delays

3. **Configuration Section**:
   - Added new "Keycloak SPI (Direct Kafka Sync)" section (line 118)
   - Documented all Kafka-related environment variables for SPI:
     - KAFKA_BOOTSTRAP_SERVERS
     - KAFKA_SASL_MECHANISM
     - KAFKA_SASL_USERNAME/PASSWORD
     - KAFKA_DEFAULT_API_TIMEOUT_MS
     - KAFKA_REQUEST_TIMEOUT_MS
   - Added note explaining immediate sync behavior

4. **Reconciliation Section**:
   - Added RECONCILE_SCHEDULER_ENABLED variable (default: false)
   - Documented that scheduled reconciliation is disabled by default
   - Explained manual reconciliation is available as safety net

**Architecture Diagram:**

Created ASCII diagram showing direct flow:
```
Keycloak SPI → Kafka SCRAM Credentials
(ThreadLocal password correlation)
```

Compared to old architecture:
```
Keycloak SPI → Webhook → Cache → Reconciliation → Kafka
```

**Documentation Quality:**
- Clear explanation of architectural shift
- Comprehensive failure scenario coverage
- Migration path documented
- Environment variables fully documented
- Links to related decisions and tasks
<!-- SECTION:NOTES:END -->
