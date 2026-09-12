import { useEffect, useState, type FormEvent } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { todayDateString } from '../kanban/date'
import { useAuth } from '../auth/AuthContext'
import { DeleteTeacherModal } from '../components/DeleteTeacherModal'
import { ConfirmDialog } from '../components/ConfirmDialog'

interface AdminDashboardPageProps {
  onBack: () => void
}

export function AdminDashboardPage({ onBack }: AdminDashboardPageProps) {
  const { logout } = useAuth()
  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)
  const [teacherLoadError, setTeacherLoadError] = useState<string | null>(null)

  const [newPhone, setNewPhone] = useState('')
  const [newName, setNewName] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [creatingTeacher, setCreatingTeacher] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createdNotice, setCreatedNotice] = useState<{ phone: string; password: string } | null>(null)

  const [classes, setClasses] = useState<adminApi.AdminClassRoom[]>([])
  const [loadingClasses, setLoadingClasses] = useState(true)
  const [classLoadError, setClassLoadError] = useState<string | null>(null)

  const [date, setDate] = useState(todayDateString())
  const [dashboard, setDashboard] = useState<adminApi.AdminDashboard | null>(null)
  const [loadingDashboard, setLoadingDashboard] = useState(true)
  const [dashboardLoadError, setDashboardLoadError] = useState<string | null>(null)

  const [deletingTeacher, setDeletingTeacher] = useState<adminApi.Teacher | null>(null)
  const [deletionImpact, setDeletionImpact] = useState<adminApi.TeacherDeletionImpact | null>(null)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const [deletingClassRoom, setDeletingClassRoom] = useState<adminApi.AdminClassRoom | null>(null)
  const [classDeletionImpact, setClassDeletionImpact] = useState<adminApi.ClassRoomDeletionImpact | null>(null)
  const [classDeleteSubmitting, setClassDeleteSubmitting] = useState(false)
  const [classDeleteError, setClassDeleteError] = useState<string | null>(null)

  const [editingTeacherId, setEditingTeacherId] = useState<number | null>(null)
  const [editingTeacherName, setEditingTeacherName] = useState('')
  const [teacherEditSubmitting, setTeacherEditSubmitting] = useState(false)
  const [teacherEditError, setTeacherEditError] = useState<string | null>(null)

  const [editingClassRoomId, setEditingClassRoomId] = useState<number | null>(null)
  const [editingClassName, setEditingClassName] = useState('')
  const [editingClassTeacherId, setEditingClassTeacherId] = useState<number | null>(null)
  const [classEditSubmitting, setClassEditSubmitting] = useState(false)
  const [classEditError, setClassEditError] = useState<string | null>(null)

  function loadTeachers() {
    setLoadingTeachers(true)
    setTeacherLoadError(null)
    adminApi
      .fetchTeachers()
      .then(setTeachers)
      .catch(() => setTeacherLoadError('加载教师列表失败，请刷新重试'))
      .finally(() => setLoadingTeachers(false))
  }

  function loadClasses() {
    setLoadingClasses(true)
    setClassLoadError(null)
    adminApi
      .fetchAdminClasses()
      .then(setClasses)
      .catch(() => setClassLoadError('加载班级列表失败，请刷新重试'))
      .finally(() => setLoadingClasses(false))
  }

  function loadDashboard() {
    setLoadingDashboard(true)
    setDashboardLoadError(null)
    adminApi
      .fetchAdminDashboard(date)
      .then(setDashboard)
      .catch(() => setDashboardLoadError('加载看板数据失败，请刷新重试'))
      .finally(() => setLoadingDashboard(false))
  }

  useEffect(() => {
    loadTeachers()
    loadClasses()
  }, [])

  useEffect(() => {
    loadDashboard()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date])

  async function handleCreateTeacher(e: FormEvent) {
    e.preventDefault()
    const phone = newPhone.trim()
    const name = newName.trim()
    const password = newPassword.trim()
    if (!phone || !name || !password) return
    setCreatingTeacher(true)
    setCreateError(null)
    try {
      await adminApi.createTeacher(phone, name, password)
      setCreatedNotice({ phone, password })
      setNewPhone('')
      setNewName('')
      setNewPassword('')
      loadTeachers()
      loadDashboard()
    } catch (err) {
      setCreateError(err instanceof ApiError && err.status === 409 ? '该手机号已注册' : '创建失败，请重试')
    } finally {
      setCreatingTeacher(false)
    }
  }

  function handleStartEditTeacher(teacher: adminApi.Teacher) {
    setEditingTeacherId(teacher.id)
    setEditingTeacherName(teacher.name)
    setTeacherEditError(null)
  }

  function handleCancelEditTeacher() {
    setEditingTeacherId(null)
    setTeacherEditError(null)
  }

  async function handleSaveTeacherName() {
    if (editingTeacherId === null) return
    const name = editingTeacherName.trim()
    if (!name) return
    setTeacherEditSubmitting(true)
    setTeacherEditError(null)
    try {
      await adminApi.updateTeacherName(editingTeacherId, name)
      setEditingTeacherId(null)
      loadTeachers()
      loadClasses()
    } catch {
      setTeacherEditError('保存失败，请重试')
    } finally {
      setTeacherEditSubmitting(false)
    }
  }

  function handleStartEditClassRoom(classRoom: adminApi.AdminClassRoom) {
    setEditingClassRoomId(classRoom.id)
    setEditingClassName(classRoom.name)
    setEditingClassTeacherId(classRoom.teacherId)
    setClassEditError(null)
  }

  function handleCancelEditClassRoom() {
    setEditingClassRoomId(null)
    setClassEditError(null)
  }

  async function handleSaveClassRoom() {
    if (editingClassRoomId === null || editingClassTeacherId === null) return
    const name = editingClassName.trim()
    if (!name) return
    setClassEditSubmitting(true)
    setClassEditError(null)
    try {
      await adminApi.updateClassRoom(editingClassRoomId, name, editingClassTeacherId)
      setEditingClassRoomId(null)
      loadClasses()
      loadDashboard()
    } catch (err) {
      setClassEditError(err instanceof ApiError && err.status === 409 ? '该教师下已有同名班级' : '保存失败，请重试')
    } finally {
      setClassEditSubmitting(false)
    }
  }

  function handleOpenDeleteTeacher(teacher: adminApi.Teacher) {
    setDeletingTeacher(teacher)
    setDeletionImpact(null)
    setDeleteError(null)
    adminApi
      .fetchTeacherDeletionImpact(teacher.id)
      .then(setDeletionImpact)
      .catch(() => {
        setDeleteError('加载删除影响范围失败，请重试')
        setDeletingTeacher(null)
      })
  }

  async function handleConfirmDeleteTeacher(mode: 'DELETE_ALL' | 'TRANSFER', targetTeacherId?: number) {
    if (!deletingTeacher) return
    setDeleteSubmitting(true)
    setDeleteError(null)
    try {
      await adminApi.deleteTeacher(deletingTeacher.id, mode, targetTeacherId)
      setDeletingTeacher(null)
      setDeletionImpact(null)
      loadTeachers()
      loadClasses()
      loadDashboard()
    } catch {
      setDeleteError('删除失败，请重试')
    } finally {
      setDeleteSubmitting(false)
    }
  }

  function handleOpenDeleteClassRoom(classRoom: adminApi.AdminClassRoom) {
    setDeletingClassRoom(classRoom)
    setClassDeletionImpact(null)
    setClassDeleteError(null)
    adminApi
      .fetchClassRoomDeletionImpact(classRoom.id)
      .then(setClassDeletionImpact)
      .catch(() => {
        setClassDeleteError('加载删除影响范围失败，请重试')
        setDeletingClassRoom(null)
      })
  }

  async function handleConfirmDeleteClassRoom() {
    if (!deletingClassRoom || classDeletionImpact === null) return
    setClassDeleteSubmitting(true)
    setClassDeleteError(null)
    try {
      await adminApi.deleteClassRoom(deletingClassRoom.id)
      setDeletingClassRoom(null)
      setClassDeletionImpact(null)
      loadClasses()
      loadDashboard()
    } catch {
      setClassDeleteError('删除失败，请重试')
    } finally {
      setClassDeleteSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">机构管理</h1>
          <div className="flex gap-2">
            <button type="button" onClick={onBack} className="logout-btn">
              返回看板
            </button>
            <button type="button" onClick={logout} className="logout-btn">
              退出登录
            </button>
          </div>
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
              placeholder="教师姓名"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
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
              disabled={creatingTeacher || !newPhone.trim() || !newName.trim() || !newPassword.trim()}
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
          {deleteError && <p className="mt-1 text-sm text-red-600">{deleteError}</p>}
          {teacherEditError && <p className="mt-1 text-sm text-red-600">{teacherEditError}</p>}
          {loadingTeachers && <p className="mt-1 text-sm text-gray-400">加载中...</p>}
          {!loadingTeachers && (
            <ul className="mt-2 space-y-2">
              {teachers.map((teacher) =>
                editingTeacherId === teacher.id ? (
                  <li
                    key={teacher.id}
                    className="flex items-center gap-2 rounded border border-gray-100 p-2 text-sm"
                  >
                    <input
                      aria-label={`教师姓名${teacher.phone}`}
                      value={editingTeacherName}
                      onChange={(e) => setEditingTeacherName(e.target.value)}
                      className="w-32 rounded border px-2 py-1 text-sm"
                    />
                    <button
                      type="button"
                      onClick={handleSaveTeacherName}
                      disabled={teacherEditSubmitting || !editingTeacherName.trim()}
                      className="shrink-0 rounded bg-blue-600 px-1.5 py-0.5 text-xs text-white disabled:opacity-50"
                    >
                      保存
                    </button>
                    <button
                      type="button"
                      onClick={handleCancelEditTeacher}
                      disabled={teacherEditSubmitting}
                      className="shrink-0 rounded border border-gray-300 px-1.5 py-0.5 text-xs text-gray-600"
                    >
                      取消
                    </button>
                  </li>
                ) : (
                  <li
                    key={teacher.id}
                    className="flex items-center justify-between gap-2 rounded border border-gray-100 p-2 text-sm"
                  >
                    <span>
                      {teacher.name ? `${teacher.name} · ` : ''}
                      {teacher.phone} · {teacher.role === 'ADMIN' ? '管理员' : '教师'} ·{' '}
                      {teacher.mustChangePassword ? '待修改初始密码' : '已启用'}
                    </span>
                    <span className="flex shrink-0 gap-1.5">
                      <button
                        type="button"
                        aria-label={`编辑教师${teacher.name || teacher.phone}`}
                        onClick={() => handleStartEditTeacher(teacher)}
                        className="rounded border border-gray-300 px-1.5 py-0.5 text-xs text-gray-600"
                      >
                        编辑
                      </button>
                      {teacher.role === 'TEACHER' && (
                        <button
                          type="button"
                          aria-label={`删除教师${teacher.name || teacher.phone}`}
                          onClick={() => handleOpenDeleteTeacher(teacher)}
                          className="rounded border border-red-300 px-1.5 py-0.5 text-xs text-red-600"
                        >
                          删除
                        </button>
                      )}
                    </span>
                  </li>
                ),
              )}
              {teachers.length === 0 && <li className="text-xs text-gray-400">暂无教师</li>}
            </ul>
          )}
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <h2 className="text-sm font-medium text-gray-700">托管班级列表（{classes.length}）</h2>
          {classLoadError && <p className="mt-1 text-sm text-red-600">{classLoadError}</p>}
          {classDeleteError && <p className="mt-1 text-sm text-red-600">{classDeleteError}</p>}
          {classEditError && <p className="mt-1 text-sm text-red-600">{classEditError}</p>}
          {loadingClasses && <p className="mt-1 text-sm text-gray-400">加载中...</p>}
          {!loadingClasses && (
            <ul className="mt-2 space-y-2">
              {classes.map((classRoom) =>
                editingClassRoomId === classRoom.id ? (
                  <li
                    key={classRoom.id}
                    className="flex flex-wrap items-center gap-2 rounded border border-gray-100 p-2 text-sm"
                  >
                    <input
                      aria-label={`班级名称${classRoom.id}`}
                      value={editingClassName}
                      onChange={(e) => setEditingClassName(e.target.value)}
                      className="w-28 rounded border px-2 py-1 text-sm"
                    />
                    <select
                      aria-label={`班级教师${classRoom.id}`}
                      value={editingClassTeacherId ?? ''}
                      onChange={(e) => setEditingClassTeacherId(Number(e.target.value))}
                      className="rounded border px-2 py-1 text-sm"
                    >
                      {teachers
                        .filter((t) => t.role === 'TEACHER')
                        .map((t) => (
                          <option key={t.id} value={t.id}>
                            {t.name || t.phone}
                          </option>
                        ))}
                    </select>
                    <button
                      type="button"
                      onClick={handleSaveClassRoom}
                      disabled={classEditSubmitting || !editingClassName.trim()}
                      className="shrink-0 rounded bg-blue-600 px-1.5 py-0.5 text-xs text-white disabled:opacity-50"
                    >
                      保存
                    </button>
                    <button
                      type="button"
                      onClick={handleCancelEditClassRoom}
                      disabled={classEditSubmitting}
                      className="shrink-0 rounded border border-gray-300 px-1.5 py-0.5 text-xs text-gray-600"
                    >
                      取消
                    </button>
                    {editingClassTeacherId !== null && editingClassTeacherId !== classRoom.teacherId && (
                      <p className="w-full text-xs text-amber-600">
                        该操作将把班级「{classRoom.name}」（含班内学生、任务库等全部内容）整体转移给新教师，确认后无法撤回
                      </p>
                    )}
                  </li>
                ) : (
                  <li
                    key={classRoom.id}
                    className="flex items-center justify-between gap-2 rounded border border-gray-100 p-2 text-sm"
                  >
                    <span>
                      {classRoom.name} · {classRoom.teacherName} {classRoom.teacherPhone}
                    </span>
                    <span className="flex shrink-0 gap-1.5">
                      <button
                        type="button"
                        aria-label={`编辑班级${classRoom.name}`}
                        onClick={() => handleStartEditClassRoom(classRoom)}
                        className="rounded border border-gray-300 px-1.5 py-0.5 text-xs text-gray-600"
                      >
                        编辑
                      </button>
                      <button
                        type="button"
                        aria-label={`删除班级${classRoom.name}`}
                        onClick={() => handleOpenDeleteClassRoom(classRoom)}
                        className="rounded border border-red-300 px-1.5 py-0.5 text-xs text-red-600"
                      >
                        删除
                      </button>
                    </span>
                  </li>
                ),
              )}
              {classes.length === 0 && <li className="text-xs text-gray-400">暂无班级</li>}
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

      {deletingTeacher && (
        <DeleteTeacherModal
          teacherName={deletingTeacher.name || deletingTeacher.phone}
          impact={deletionImpact}
          otherTeachers={teachers.filter((t) => t.id !== deletingTeacher.id && t.role === 'TEACHER')}
          onConfirm={handleConfirmDeleteTeacher}
          onClose={() => {
            setDeletingTeacher(null)
            setDeletionImpact(null)
          }}
          submitting={deleteSubmitting}
        />
      )}

      {deletingClassRoom && (
        <ConfirmDialog
          title="删除班级"
          message={
            classDeletionImpact === null
              ? '加载中...'
              : classDeletionImpact.studentCount > 0
                ? `班级「${deletingClassRoom.name}」下有 ${classDeletionImpact.studentCount} 名学生，删除后班级、学生及相关记录将全部清空，且不可恢复，确认删除吗？`
                : `确认删除班级「${deletingClassRoom.name}」吗？此操作不可恢复。`
          }
          onConfirm={handleConfirmDeleteClassRoom}
          onCancel={() => {
            setDeletingClassRoom(null)
            setClassDeletionImpact(null)
          }}
          confirming={classDeleteSubmitting}
        />
      )}
    </div>
  )
}
