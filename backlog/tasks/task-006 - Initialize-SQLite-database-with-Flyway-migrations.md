---
id: task-006
title: Initialize SQLite database with Flyway migrations
status: To Do
assignee: []
created_date: '2025-11-04 14:34'
labels:
  - backend
  - database
  - setup
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Set up SQLite database with Flyway for schema versioning. Create the initial migration (V1__init.sql) with tables for sync_operation, sync_batch, and retention_state as specified in the technical design.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Flyway is configured with SQLite JDBC driver
- [ ] #2 Database file location is configurable via environment variable
- [ ] #3 V1__init.sql migration creates sync_operation table with all columns and indexes
- [ ] #4 V1__init.sql migration creates sync_batch table with all columns and indexes
- [ ] #5 V1__init.sql migration creates retention_state table with initial row
- [ ] #6 Migrations execute successfully on application startup
- [ ] #7 Database schema matches specification in technical analysis
- [ ] #8 SQLite connection is validated in health check
<!-- AC:END -->
