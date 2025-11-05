import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { BatchesPageResponse, BatchesQueryParams } from '../types/api'

export function useBatches(params: BatchesQueryParams = {}) {
  return useQuery<BatchesPageResponse, Error>({
    queryKey: ['batches', params],
    queryFn: () => apiClient.getBatches(params),
    // Inherits default options from QueryClient:
    // - staleTime: 10s
    // - refetchInterval: 10s
    // - refetchOnWindowFocus: true
  })
}
