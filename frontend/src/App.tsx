import { useState } from 'react'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { KanbanPage } from './pages/KanbanPage'
import { RosterPage } from './pages/RosterPage'
import { AdminDashboardPage } from './pages/AdminDashboardPage'
import { PlatformAdminPage } from './pages/PlatformAdminPage'

function AuthenticatedApp() {
  const [view, setView] = useState<'kanban' | 'roster' | 'admin'>('kanban')

  if (view === 'roster') {
    return <RosterPage onBack={() => setView('kanban')} />
  }
  if (view === 'admin') {
    return <AdminDashboardPage onBack={() => setView('kanban')} />
  }
  return (
    <KanbanPage onOpenRoster={() => setView('roster')} onOpenAdmin={() => setView('admin')} />
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
      if (state.teacher.role === 'PLATFORM_ADMIN') {
        return <PlatformAdminPage />
      }
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
