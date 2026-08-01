export type HealthStatus = 'connected' | 'disconnected'

export async function fetchHealth(): Promise<HealthStatus> {
  try {
    const res = await fetch('/api/health')
    if (!res.ok) {
      return 'disconnected'
    }
    const data = await res.json()
    return data.status === 'ok' ? 'connected' : 'disconnected'
  } catch {
    return 'disconnected'
  }
}
