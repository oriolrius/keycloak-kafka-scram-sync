# TanStack Query Hooks

This directory contains custom React hooks for fetching data from the backend API using TanStack Query (React Query).

## Features

- **Automatic caching**: Data is cached for 10 seconds (staleTime)
- **Automatic polling**: Data refreshes every 10 seconds automatically
- **Window focus refetch**: Data refetches when the user returns to the tab
- **Error handling**: Built-in error handling with retry logic
- **Loading states**: Automatic loading state management

## Available Hooks

### `useSummary()`

Fetches dashboard summary statistics.

```tsx
import { useSummary } from '@/hooks'

function SummaryCard() {
  const { data, isLoading, error } = useSummary()

  if (isLoading) return <div>Loading...</div>
  if (error) return <div>Error: {error.message}</div>

  return (
    <div>
      <p>Ops/Hour: {data.opsPerHour}</p>
      <p>Error Rate: {data.errorRatePercent}%</p>
      <p>P95 Latency: {data.latencyP95Ms}ms</p>
      <p>P99 Latency: {data.latencyP99Ms}ms</p>
      <p>DB Usage: {data.dbUsageMb}MB</p>
    </div>
  )
}
```

### `useOperations(params?)`

Fetches paginated operations with optional filters.

```tsx
import { useOperations } from '@/hooks'
import { OperationType, OperationResult } from '@/types/api'

function OperationsList() {
  const { data, isLoading, error } = useOperations({
    page: 0,
    pageSize: 20,
    opType: OperationType.SCRAM_UPSERT,
    result: OperationResult.SUCCESS,
  })

  if (isLoading) return <div>Loading...</div>
  if (error) return <div>Error: {error.message}</div>

  return (
    <div>
      <p>Total: {data.totalElements} operations</p>
      {data.operations.map(op => (
        <div key={op.id}>
          {op.principal} - {op.opType} - {op.result}
        </div>
      ))}
    </div>
  )
}
```

### `useBatches(params?)`

Fetches paginated batch summaries.

```tsx
import { useBatches } from '@/hooks'

function BatchesList() {
  const { data, isLoading, error } = useBatches({
    page: 0,
    pageSize: 10,
  })

  if (isLoading) return <div>Loading...</div>
  if (error) return <div>Error: {error.message}</div>

  return (
    <div>
      {data.batches.map(batch => (
        <div key={batch.id}>
          Batch {batch.id} - {batch.opsProcessed} ops
          {batch.durationMs && ` - ${batch.durationMs}ms`}
        </div>
      ))}
    </div>
  )
}
```

### `useRetentionConfig()` and `useUpdateRetentionConfig()`

Fetches and updates retention configuration.

```tsx
import { useRetentionConfig, useUpdateRetentionConfig } from '@/hooks'

function RetentionSettings() {
  const { data, isLoading } = useRetentionConfig()
  const updateMutation = useUpdateRetentionConfig()

  const handleUpdate = () => {
    updateMutation.mutate({
      maxAgeDays: 30,
      maxRecords: 10000,
      cleanupIntervalHours: 24,
    })
  }

  if (isLoading) return <div>Loading...</div>

  return (
    <div>
      <p>Max Age: {data.maxAgeDays} days</p>
      <p>Max Records: {data.maxRecords}</p>
      <button
        onClick={handleUpdate}
        disabled={updateMutation.isPending}
      >
        Update
      </button>
      {updateMutation.isError && (
        <p>Error: {updateMutation.error.message}</p>
      )}
      {updateMutation.isSuccess && <p>Updated successfully!</p>}
    </div>
  )
}
```

## Configuration

The QueryClient is configured in `src/main.tsx` with the following defaults:

- **staleTime**: 10 seconds
- **refetchInterval**: 10 seconds (automatic polling)
- **refetchOnWindowFocus**: true
- **retry**: 1 attempt on error

## Cache Invalidation

The `useUpdateRetentionConfig` mutation automatically invalidates the retention config cache after a successful update, ensuring the UI stays in sync with the backend.
