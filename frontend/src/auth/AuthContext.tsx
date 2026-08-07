import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { getToken, setToken as persistToken } from '../api/client'

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'mustChangePassword' }
  | { status: 'authenticated'; teacher: authApi.TeacherMe }

interface AuthContextValue {
  state: AuthState
  login: (phone: string, password: string) => Promise<void>
  completeChangePassword: (oldPassword: string, newPassword: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const loadTeacher = useCallback(async () => {
    const teacher = await authApi.fetchMe()
    setState({ status: 'authenticated', teacher })
  }, [])

  useEffect(() => {
    if (!getToken()) {
      setState({ status: 'anonymous' })
      return
    }
    loadTeacher().catch(() => {
      persistToken(null)
      setState({ status: 'anonymous' })
    })
  }, [loadTeacher])

  const login = useCallback(
    async (phone: string, password: string) => {
      const result = await authApi.login(phone, password)
      persistToken(result.token)
      if (result.mustChangePassword) {
        setState({ status: 'mustChangePassword' })
      } else {
        await loadTeacher()
      }
    },
    [loadTeacher],
  )

  const completeChangePassword = useCallback(
    async (oldPassword: string, newPassword: string) => {
      await authApi.changePassword(oldPassword, newPassword)
      await loadTeacher()
    },
    [loadTeacher],
  )

  const logout = useCallback(() => {
    persistToken(null)
    setState({ status: 'anonymous' })
  }, [])

  return (
    <AuthContext.Provider value={{ state, login, completeChangePassword, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
