---
id: task-066
title: Document direct Kafka SPI architecture decision
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:19'
updated_date: '2025-11-09 13:09'
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
- [ ] #1 Decision document created in backlog/decisions/
- [ ] #2 Document explains why direct SPI approach was chosen
- [ ] #3 Trade-offs and failure scenarios documented
- [ ] #4 Architecture diagram shows direct Keycloak→Kafka flow
- [ ] #5 README updated with new ENV variables for SPI Kafka config
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
