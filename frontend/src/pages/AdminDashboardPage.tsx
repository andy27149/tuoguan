import { useEffect, useState, type FormEvent } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { todayDateString } from '../kanban/date'

interface AdminDashboardPageProps {
  onBack: () => void
}

export function AdminDashboardPage({ onBack }: AdminDashboardPageProps) {
  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)
  const [teacherLoadError, setTeacherLoadError] = useState<string | null>(null)

  const [newPhone, setNewPhone] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [creatingTeacher, setCreatingTeacher] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createdNotice, setCreatedNotice] = useState<{ phone: string; password: string } | null>(null)

  const [date, setDate] = useState(todayDateString())
  const [dashboard, setDashboard] = useState<adminApi.AdminDashboard | null>(null)
  const [loadingDashboard, setLoadingDashboard] = useState(true)
  const [dashboardLoadError, setDashboardLoadError] = useState<string | null>(null)

  function loadTeachers() {
    setLoadingTeachers(true)
    setTeacherLoadError(null)
    adminApi
      .fetchTeachers()
      .then(setTeachers)
      .catch(() => setTeacherLoadError('加载教师列表失败，请刷新重试'))
      .finally(() => setLoadingTeachers(false))
  }

  useEffect(() => {
    loadTeachers()
  }, [])

  useEffect(() => {
    setLoadingDashboard(true)
    setDashboardLoadError(null)
    adminApi
      .fetchAdminDashboard(date)
      .then(setDashboard)
      .catch(() => setDashboardLoadError('加载看板数据失败，请刷新重试'))
      .finally(() => setLoadingDashboard(false))
  }, [date])

  async function handleCreateTeacher(e: FormEvent) {
    e.preventDefault()
    const phone = newPhone.trim()
    const password = newPassword.trim()
    if (!phone || !password) return
    setCreatingTeacher(true)
    setCreateError(null)
    try {
      await adminApi.createTeacher(phone, password)
      setCreatedNotice({ phone, password })
      setNewPhone('')
      setNewPassword('')
      loadTeachers()
    } catch (err) {
      setCreateError(err instanceof ApiError && err.status === 409 ? '该手机号已注册' : '创建失败，请重试')
    } finally {
      setCreatingTeacher(false)
    }
  }

  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">机构管理</h1>
          <button type="button" onClick={onBack} className="logout-btn">
            返回看板
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-2xl space-y-4 px-4 pt-4">
        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <h2 className="text-sm font-medium text-gray-700">新增教师</h2>
          <form onSubmit={handleCreateTeacher} className="mt-2 flex flex-wrap gap-2">
            <input
              placeholder="手机号"
              value={newPhone}
              onChange={(e) => setNewPhone(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <input
              placeholder="初始密码"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <button
              type="submit"
              disabled={creatingTeacher || !newPhone.trim() || !newPassword.trim()}
              className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
            >
              创建
            </button>
          </form>
          {createError && (
            <p role="alert" className="mt-1 text-xs text-red-600">
              {createError}
            </p>
          )}
          {createdNotice && (
            <div className="mt-2 flex items-start justify-between gap-2 rounded border border-green-200 bg-green-50 p-2 text-xs text-green-800">
              <span>
                教师账号创建成功：手机号 {createdNotice.phone}，初始密码 {createdNotice.password}
                （请尽快告知该教师，此密码仅显示一次）
              </span>
              <button
                type="button"
                onClick={() => setCreatedNotice(null)}
                className="shrink-0 rounded border border-green-300 px-1.5 py-0.5 text-green-700"
              >
                知道了
              </button>
            </div>
          )}
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <h2 className="text-sm font-medium text-gray-700">教师列表（{teachers.length}）</h2>
          {teacherLoadError && <p className="mt-1 text-sm text-red-600">{teacherLoadError}</p>}
          {loadingTeachers && <p className="mt-1 text-sm text-gray-400">加载中...</p>}
          {!loadingTeachers && (
            <ul className="mt-2 space-y-2">
              {teachers.map((teacher) => (
                <li key={teacher.id} className="rounded border border-gray-100 p-2 text-sm">
                  {teacher.phone} · {teacher.role === 'ADMIN' ? '管理员' : '教师'} ·{' '}
                  {teacher.mustChangePassword ? '待修改初始密码' : '已启用'}
                </li>
              ))}
              {teachers.length === 0 && <li className="text-xs text-gray-400">暂无教师</li>}
            </ul>
          )}
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-sm font-medium text-gray-700">班级任务完成情况</h2>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="rounded border px-2 py-1 text-sm"
            />
          </div>
          {dashboardLoadError && <p className="mt-1 text-sm text-red-600">{dashboardLoadError}</p>}
          {loadingDashboard && <p className="mt-1 text-sm text-gray-400">加载中...</p>}
          {!loadingDashboard && dashboard && (
            <table className="mt-2 w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-gray-500">
                  <th className="py-1">班级</th>
                  <th className="py-1">学生数</th>
                  <th className="py-1">已完成人数</th>
                </tr>
              </thead>
              <tbody>
                {dashboard.classes.map((c) => (
                  <tr key={c.classRoomId} className="border-t border-gray-100">
                    <td className="py-1">{c.className}</td>
                    <td className="py-1">{c.studentCount}</td>
                    <td className="py-1">{c.completedStudentCount}</td>
                  </tr>
                ))}
                {dashboard.classes.length === 0 && (
                  <tr>
                    <td colSpan={3} className="py-1 text-xs text-gray-400">
                      暂无班级数据
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </main>
    </div>
  )
}
