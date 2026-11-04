import { useEffect, useState } from 'react'
import { fetchHealth } from './api'

/**
 * Skeleton page: asks the backend for its health and prints the answer.
 * It proves the frontend → Vite proxy → Spring Boot path works end to end.
 */
export default function App() {
  const [status, setStatus] = useState<string>('checking…')

  useEffect(() => {
    fetchHealth()
      .then((health) => setStatus(health.status))
      .catch((error: Error) => setStatus(`unreachable (${error.message})`))
  }, [])

  return (
    <main style={{ fontFamily: 'Georgia, serif', padding: '2rem' }}>
      <h1>DalalX</h1>
      <p>
        Server status: <strong data-testid="health-status">{status}</strong>
      </p>
    </main>
  )
}
