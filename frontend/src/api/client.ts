// API Client for backend endpoints

import type {
  SummaryResponse,
  OperationsPageResponse,
  OperationsQueryParams,
  BatchesPageResponse,
  BatchesQueryParams,
  RetentionConfig,
} from '../types/api'

// Base API URL - will be proxied by Vite dev server
const API_BASE_URL = '/api'

// Helper function to build query string
function buildQueryString(params: Record<string, any>): string {
  const searchParams = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      searchParams.append(key, String(value))
    }
  })

  const queryString = searchParams.toString()
  return queryString ? `?${queryString}` : ''
}

// Generic fetch wrapper with error handling
async function fetchJSON<T>(url: string): Promise<T> {
  const response = await fetch(url)

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  return response.json()
}

// API Client functions
export const apiClient = {
  // GET /api/summary
  async getSummary(): Promise<SummaryResponse> {
    return fetchJSON<SummaryResponse>(`${API_BASE_URL}/summary`)
  },

  // GET /api/operations
  async getOperations(params: OperationsQueryParams = {}): Promise<OperationsPageResponse> {
    const queryString = buildQueryString(params)
    return fetchJSON<OperationsPageResponse>(`${API_BASE_URL}/operations${queryString}`)
  },

  // GET /api/batches
  async getBatches(params: BatchesQueryParams = {}): Promise<BatchesPageResponse> {
    const queryString = buildQueryString(params)
    return fetchJSON<BatchesPageResponse>(`${API_BASE_URL}/batches${queryString}`)
  },

  // GET /api/config/retention
  async getRetentionConfig(): Promise<RetentionConfig> {
    return fetchJSON<RetentionConfig>(`${API_BASE_URL}/config/retention`)
  },

  // PUT /api/config/retention
  async updateRetentionConfig(config: RetentionConfig): Promise<RetentionConfig> {
    const response = await fetch(`${API_BASE_URL}/config/retention`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(config),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    return response.json()
  },
}
