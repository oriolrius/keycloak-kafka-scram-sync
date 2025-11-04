---
id: task-006
title: Initialize SQLite database with Flyway migrations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:29'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add Flyway and SQLite JDBC dependencies to pom.xml
2. Configure Flyway with SQLite datasource in application.properties
3. Make database file location configurable via SQLITE_DB_PATH environment variable
4. Create db/migration/V1__init.sql with all three tables and indexes
5. Enhance health check to validate SQLite connection
6. Test that migrations execute successfully on startup
7. Verify schema matches technical specification
<!-- SECTION:PLAN:END -->
