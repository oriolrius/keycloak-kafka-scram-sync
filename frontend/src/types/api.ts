// API Response Types based on backend DTOs

export interface SummaryResponse {
  opsPerHour: number;
  errorRatePercent: number;
  latencyP95Ms: number;
  latencyP99Ms: number;
  dbUsageMb: number;
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
  startTime: string; // ISO 8601 datetime
  endTime?: string; // ISO 8601 datetime
  opsProcessed: number;
  completed: boolean;
  durationMs?: number;
}

export interface BatchesPageResponse {
  batches: BatchResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface RetentionConfig {
  maxAgeDays: number;
  maxRecords: number;
  cleanupIntervalHours: number;
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
}
