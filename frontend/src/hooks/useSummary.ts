import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { SummaryResponse } from '../types/api'

export function useSummary() {
  return useQuery<SummaryResponse, Error>({
    queryKey: ['summary'],
    queryFn: () => apiClient.getSummary(),
    // Inherits default options from QueryClient:
    // - staleTime: 10s
    // - refetchInterval: 10s
    // - refetchOnWindowFocus: true
  })
}
