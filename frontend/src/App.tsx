import { useEffect, useState } from 'react'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { KanbanPage } from './pages/KanbanPage'
import { CourseConsumptionPage } from './pages/CourseConsumptionPage'
import { AdminDashboardPage } from './pages/AdminDashboardPage'
import { PlatformAdminPage } from './pages/PlatformAdminPage'
import { EmptyTeacherState } from './components/EmptyTeacherState'
import { ErrorBoundary } from './components/ErrorBoundary'
import * as classesApi from './api/classes'
import * as courseApi from './api/course'

function AuthenticatedApp() {
  const { logout, state } = useAuth()
  const isAdmin = state.status === 'authenticated' && state.teacher.role === 'ADMIN'
  // 机构管理员默认落地机构管理后台（桌面端仪表盘），而不是为手机单班级场景设计的只读看板；
  // 教师仍然默认落地看板，这是他们的日常工作台。
  const [view, setView] = useState<'kanban' | 'consumption' | 'admin'>(isAdmin ? 'admin' : 'kanban')
  const [jumpToClassId, setJumpToClassId] = useState<number | null>(null)
  const [resourcesLoading, setResourcesLoading] = useState(!isAdmin)
  const [hasClasses, setHasClasses] = useState(true)
  const [hasCourses, setHasCourses] = useState(true)

  useEffect(() => {
    if (isAdmin) {
      setResourcesLoading(false)
      return
    }
    let cancelled = false
    Promise.all([classesApi.fetchClasses(), courseApi.fetchMyCourses()])
      .then(([classList, courseList]) => {
        if (cancelled) return
        setHasClasses(classList.length > 0)
        setHasCourses(courseList.length > 0)
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setResourcesLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [isAdmin])

  function handleOpenClassKanban(classId: number) {
    setJumpToClassId(classId)
    setView('kanban')
  }

  function renderView() {
    if (view === 'consumption') {
      return <CourseConsumptionPage onBack={() => setView('kanban')} />
    }
    if (view === 'admin') {
      return <AdminDashboardPage onBack={() => setView('kanban')} onOpenClassKanban={handleOpenClassKanban} />
    }
    if (!isAdmin && !resourcesLoading && !hasClasses && !hasCourses) {
      return <EmptyTeacherState onLogout={logout} />
    }
    return (
      <KanbanPage
        onOpenConsumption={() => setView('consumption')}
        onOpenAdmin={() => setView('admin')}
        initialClassId={jumpToClassId ?? undefined}
        hasClasses={isAdmin || hasClasses}
        hasCourses={isAdmin || hasCourses}
      />
    )
  }

  return (
    <ErrorBoundary
      key={view}
      resetLabel={view === 'kanban' ? '刷新页面' : '返回看板'}
      onReset={view === 'kanban' ? undefined : () => setView('kanban')}
    >
      {renderView()}
    </ErrorBoundary>
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
      <ErrorBoundary>
        <AppShell />
      </ErrorBoundary>
    </AuthProvider>
  )
}

export default App
