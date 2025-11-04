---
id: doc-001
title: 'Functional Analysis: Keycloak → Kafka Sync Agent'
type: other
created_date: '2025-11-04 13:46'
---

## Functional Analysis: Keycloak → Kafka Sync Agent

### 1. Purpose

The Sync Agent ensures alignment between Keycloak identities (users, clients, roles, groups) and Kafka security entities (SCRAM credentials, ACLs) to enable centralized authentication and authorization management without manual Kafka administration.

### 2. Core Functions

* **Identity Synchronization:** Replicates Keycloak users and service accounts into Kafka as principals.
* **Credential Management:** Generates and updates SCRAM verifiers (`storedKey`, `serverKey`, `salt`, `iterations`) in Kafka via the Admin API.
* **Authorization Mapping:** Translates Keycloak roles and groups into Kafka ACLs using a policy file.
* **Event Handling:** Reacts to Keycloak admin events (create, update, delete, password change) and performs partial synchronization.
* **Periodic Reconciliation:** Validates consistency between Keycloak and Kafka at scheduled intervals.
* **Retention Management:** Maintains an operation history database with space and time limits, automatically purging old records.
* **Telemetry and Observability:** Exposes Prometheus-compatible metrics and a health endpoint.
* **User Interface:** Provides an HTML/React dashboard to visualize metrics, synchronization history, and retention status.

### 3. Actors and Interfaces

* **Keycloak:** Source of truth for user identities, roles, and credentials.
* **Kafka Cluster:** Destination system where SCRAM credentials and ACLs are stored.
* **Admin (Operator):** Accesses the dashboard for monitoring, configuration, and troubleshooting.
* **Prometheus/Grafana:** Collects metrics from the Sync Agent.

### 4. Use Cases

1. **Initial Synchronization:** Populate Kafka with all existing Keycloak users and credentials.
2. **Password Change Event:** On Keycloak password reset, regenerate and update the user’s SCRAM verifiers.
3. **Role Update:** Modify Kafka ACLs when Keycloak roles or group memberships change.
4. **User Deactivation:** Remove the user’s credentials and ACLs from Kafka.
5. **Periodic Health Check:** Validate connectivity and consistency with Keycloak and Kafka.
6. **Telemetry Review:** Admin views system performance and synchronization statistics via the dashboard.

### 5. Data Retention Logic

* **SQLite Database:** Maintains a circular log of synchronization operations.
* **Policies:** Defined by `max_bytes` and/or `max_age_days`.
* **Automatic Purge:** Triggered after each batch or at periodic intervals.

### 6. Expected Outcomes

* Centralized identity management for Kafka.
* Reduced operational overhead.
* Real-time visibility into synchronization and error rates.
* Controlled storage footprint for historical logs.

### 7. Non-functional Requirements

* **Performance:** Handle thousands of users and ACLs with <1 minute reconcile cycles.
* **Reliability:** No broker restart required after synchronization.
* **Security:** TLS/mTLS for all communications; no password in plaintext.
* **Maintainability:** Modular Java codebase with clear APIs and policy-driven mappings.
* **Scalability:** Support multi-realm Keycloak deployments and multi-cluster Kafka environments.
* **Auditability:** Historical records of all synchronization and error events.

