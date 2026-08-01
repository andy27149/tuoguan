import { useEffect, useState } from 'react'
import { fetchHealth, type HealthStatus } from './api/health'

function App() {
  const [status, setStatus] = useState<HealthStatus | 'checking'>('checking')

  useEffect(() => {
    fetchHealth().then(setStatus)
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <p className="text-lg font-medium" data-testid="health-status">
        {status === 'checking' && '正在连接后端...'}
        {status === 'connected' && '✅ 后端已连接'}
        {status === 'disconnected' && '❌ 后端未连接'}
      </p>
    </div>
  )
}

export default App
