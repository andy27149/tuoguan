import { useState } from 'react'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { KanbanPage } from './pages/KanbanPage'
import { RosterPage } from './pages/RosterPage'

function AuthenticatedApp() {
  const [view, setView] = useState<'kanban' | 'roster'>('kanban')

  return view === 'kanban' ? (
    <KanbanPage onOpenRoster={() => setView('roster')} />
  ) : (
    <RosterPage onBack={() => setView('kanban')} />
  )
}

function AppShell() {
  const { state } = useAuth()

  switch (state.status) {
    case 'loading':
      return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50">
          <p className="text-gray-400">加载中...</p>
        </div>
      )
    case 'anonymous':
      return <LoginPage />
    case 'mustChangePassword':
      return <ChangePasswordPage />
    case 'authenticated':
      return <AuthenticatedApp />
  }
}

function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  )
}

export default App
