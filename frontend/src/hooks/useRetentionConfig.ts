import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { RetentionConfig } from '../types/api'

export function useRetentionConfig() {
  return useQuery<RetentionConfig, Error>({
    queryKey: ['retentionConfig'],
    queryFn: () => apiClient.getRetentionConfig(),
    // Inherits default options from QueryClient:
    // - staleTime: 10s
    // - refetchInterval: 10s
    // - refetchOnWindowFocus: true
  })
}

export function useUpdateRetentionConfig() {
  const queryClient = useQueryClient()

  return useMutation<RetentionConfig, Error, RetentionConfig>({
    mutationFn: (config: RetentionConfig) => apiClient.updateRetentionConfig(config),
    onSuccess: () => {
      // Invalidate and refetch retention config after successful update
      queryClient.invalidateQueries({ queryKey: ['retentionConfig'] })
    },
  })
}
