// API Response Types based on backend DTOs

export interface SummaryResponse {
  opsPerHour: number;
  errorRate: number;
  latencyP95: number | null;
  latencyP99: number | null;
  dbUsageBytes: number;
  timestamp: string; // ISO 8601 datetime
}

export interface HealthCheck {
  name: string;
  status: 'UP' | 'DOWN';
  data?: Record<string, any>;
}

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  checks: HealthCheck[];
}

export interface ReconcileStatusResponse {
  running: boolean;
}

export interface ReconcileTriggerResponse {
  message: string;
  correlationId: string;
  successfulOperations: number;
  failedOperations: number;
  durationMs: number;
}

export interface ReconcileErrorResponse {
  error: string;
}

export interface OperationResponse {
  id: number;
  occurredAt: string; // ISO 8601 datetime
  principal: string;
  opType: OperationType;
  entityId: string;
  entityType: string;
  result: OperationResult;
  errorMessage?: string;
  durationMs: number;
}

export interface OperationsPageResponse {
  operations: OperationResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface BatchResponse {
  id: number;
  correlationId: string;
  startedAt: string; // ISO 8601 datetime
  finishedAt?: string; // ISO 8601 datetime
  source: string; // SCHEDULED, MANUAL, WEBHOOK
  itemsTotal: number;
  itemsSuccess: number;
  itemsError: number;
  durationMs?: number;
  complete: boolean;
}

export interface BatchesPageResponse {
  batches: BatchResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface RetentionConfig {
  maxBytes?: number | null;       // Maximum database size in bytes (nullable, can be disabled)
  maxAgeDays?: number | null;     // Maximum age of records in days (nullable, can be disabled)
  approxDbBytes: number;           // Approximate current database size in bytes
  updatedAt: string;               // Last update timestamp (ISO 8601 datetime)
}

export enum OperationType {
  SCRAM_UPSERT = 'SCRAM_UPSERT',
  SCRAM_DELETE = 'SCRAM_DELETE',
  ACL_CREATE = 'ACL_CREATE',
  ACL_DELETE = 'ACL_DELETE',
}

export enum OperationResult {
  SUCCESS = 'SUCCESS',
  ERROR = 'ERROR',
  SKIPPED = 'SKIPPED',
}

// Query parameters for operations endpoint
export interface OperationsQueryParams {
  page?: number;
  pageSize?: number;
  startTime?: string; // ISO 8601 datetime
  endTime?: string; // ISO 8601 datetime
  principal?: string;
  opType?: OperationType;
  result?: OperationResult;
}

// Query parameters for batches endpoint
export interface BatchesQueryParams {
  page?: number;
  pageSize?: number;
  startTime?: string; // ISO 8601 datetime
  endTime?: string; // ISO 8601 datetime
  source?: string; // SCHEDULED, MANUAL, WEBHOOK
}
