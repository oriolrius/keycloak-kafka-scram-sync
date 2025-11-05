import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { OperationsPageResponse, OperationsQueryParams } from '../types/api'

export function useOperations(params: OperationsQueryParams = {}) {
  return useQuery<OperationsPageResponse, Error>({
    queryKey: ['operations', params],
    queryFn: () => apiClient.getOperations(params),
    // Inherits default options from QueryClient:
    // - staleTime: 10s
    // - refetchInterval: 10s
    // - refetchOnWindowFocus: true
  })
}
