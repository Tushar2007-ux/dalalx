/** Shape of the JSON returned by GET /api/health on the Spring Boot server. */
export interface HealthResponse {
  status: string
}

/**
 * Calls the backend health endpoint.
 * The relative URL goes through the Vite dev proxy (see vite.config.ts) to :8080.
 */
export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return (await response.json()) as HealthResponse
}
