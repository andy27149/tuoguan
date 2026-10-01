import { useEffect, useState, type FormEvent } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { DeleteTeacherModal } from '../components/DeleteTeacherModal'

interface AdminTeachersModuleProps {
  onOpenClassKanban?: (classId: number) => void
}

export function AdminTeachersModule({ onOpenClassKanban }: AdminTeachersModuleProps) {
  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)
  const [teacherLoadError, setTeacherLoadError] = useState<string | null>(null)

  const [classes, setClasses] = useState<adminApi.AdminClassRoom[]>([])

  const [newPhone, setNewPhone] = useState('')
  const [newName, setNewName] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newRole, setNewRole] = useState<'TEACHER' | 'ADMIN'>('TEACHER')
  const [creatingTeacher, setCreatingTeacher] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createdNotice, setCreatedNotice] = useState<{ phone: string; password: string } | null>(null)

  const [deletingTeacher, setDeletingTeacher] = useState<adminApi.Teacher | null>(null)
  const [deletionImpact, setDeletionImpact] = useState<adminApi.TeacherDeletionImpact | null>(null)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const [editingTeacherId, setEditingTeacherId] = useState<number | null>(null)
  const [editingTeacherName, setEditingTeacherName] = useState('')
  const [teacherEditSubmitting, setTeacherEditSubmitting] = useState(false)
  const [teacherEditError, setTeacherEditError] = useState<string | null>(null)

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
    adminApi.fetchAdminClasses().then(setClasses).catch(() => {})
  }, [])

  async function handleCreateTeacher(e: FormEvent) {
    e.preventDefault()
    const phone = newPhone.trim()
    const name = newName.trim()
    const password = newPassword.trim()
    if (!phone || !name || !password) return
    setCreatingTeacher(true)
    setCreateError(null)
    try {
      await adminApi.createTeacher(phone, name, password, newRole)
      setCreatedNotice({ phone, password })
      setNewPhone('')
      setNewName('')
      setNewPassword('')
      setNewRole('TEACHER')
      loadTeachers()
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
    } catch {
      setTeacherEditError('保存失败，请重试')
    } finally {
      setTeacherEditSubmitting(false)
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
    } catch {
      setDeleteError('删除失败，请重试')
    } finally {
      setDeleteSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">新增教师</h2>
        <form onSubmit={handleCreateTeacher} className="mt-3 flex flex-wrap gap-2">
          <input
            placeholder="手机号"
            value={newPhone}
            onChange={(e) => setNewPhone(e.target.value)}
            className="w-32 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
          />
          <input
            placeholder="教师姓名"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="w-32 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
          />
          <input
            placeholder="初始密码"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className="w-32 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
          />
          <label className="flex items-center gap-1 text-sm text-[#5d5480]">
            <input
              type="radio"
              name="newTeacherRole"
              value="TEACHER"
              checked={newRole === 'TEACHER'}
              onChange={() => setNewRole('TEACHER')}
            />
            教师
          </label>
          <label className="flex items-center gap-1 text-sm text-[#5d5480]">
            <input
              type="radio"
              name="newTeacherRole"
              value="ADMIN"
              checked={newRole === 'ADMIN'}
              onChange={() => setNewRole('ADMIN')}
            />
            管理员
          </label>
          <button
            type="submit"
            disabled={creatingTeacher || !newPhone.trim() || !newName.trim() || !newPassword.trim()}
            className="rounded-full bg-[#6d5bd0] px-4 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            创建
          </button>
        </form>
        {createError && (
          <p role="alert" className="mt-1 text-xs text-[#b7591f]">
            {createError}
          </p>
        )}
        {createdNotice && (
          <div className="mt-2 flex items-start justify-between gap-2 rounded-xl border border-[#bfe8cd] bg-[#e9f9ef] p-2 text-xs text-[#1f9d55]">
            <span>
              教师账号创建成功：手机号 {createdNotice.phone}，初始密码 {createdNotice.password}
              （请尽快告知该教师，此密码仅显示一次）
            </span>
            <button
              type="button"
              onClick={() => setCreatedNotice(null)}
              className="shrink-0 rounded-full border border-[#bfe8cd] px-1.5 py-0.5 text-[#1f9d55]"
            >
              知道了
            </button>
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">教师列表（{teachers.length}）</h2>
        {teacherLoadError && <p className="mt-2 text-sm text-[#b7591f]">{teacherLoadError}</p>}
        {deleteError && <p className="mt-2 text-sm text-[#b7591f]">{deleteError}</p>}
        {teacherEditError && <p className="mt-2 text-sm text-[#b7591f]">{teacherEditError}</p>}
        {loadingTeachers && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loadingTeachers && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">教师姓名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">电话号码</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">角色</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">账号状态</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">管理班级</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {teachers.map((teacher) => {
                const managedClasses = classes.filter((c) => c.teacherId === teacher.id)
                const isEditing = editingTeacherId === teacher.id
                return (
                  <tr key={teacher.id} className="border-b border-[#ece7de] align-middle hover:bg-[#faf7ff]">
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <input
                          aria-label={`教师姓名${teacher.phone}`}
                          value={editingTeacherName}
                          onChange={(e) => setEditingTeacherName(e.target.value)}
                          className="w-28 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="font-medium text-[#241f3d]">{teacher.name || '-'}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-[#241f3d]">{teacher.phone}</td>
                    <td className="px-4 py-3 text-[#241f3d]">{teacher.role === 'ADMIN' ? '管理员' : '教师'}</td>
                    <td className="px-4 py-3 text-[#241f3d]">{teacher.mustChangePassword ? '待修改初始密码' : '已启用'}</td>
                    <td className="px-4 py-3">
                      {managedClasses.length === 0 ? (
                        <span className="text-[#7c7391]">-</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {managedClasses.map((c) =>
                            onOpenClassKanban ? (
                              <button
                                key={c.id}
                                type="button"
                                onClick={() => onOpenClassKanban(c.id)}
                                className="rounded-full border border-[#d9cff5] bg-[#f3f0ff] px-2 py-0.5 text-xs text-[#6d5bd0] hover:bg-[#e9e2ff]"
                              >
                                {c.name}
                              </button>
                            ) : (
                              <span key={c.id} className="text-xs text-[#5d5480]">
                                {c.name}
                              </span>
                            ),
                          )}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            onClick={handleSaveTeacherName}
                            disabled={teacherEditSubmitting || !editingTeacherName.trim()}
                            className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelEditTeacher}
                            disabled={teacherEditSubmitting}
                            className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            aria-label={`编辑教师${teacher.name || teacher.phone}`}
                            onClick={() => handleStartEditTeacher(teacher)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            编辑
                          </button>
                          {teacher.role === 'TEACHER' && (
                            <button
                              type="button"
                              aria-label={`删除教师${teacher.name || teacher.phone}`}
                              onClick={() => handleOpenDeleteTeacher(teacher)}
                              className="rounded-full border border-[#f3d0c0] px-3 py-1 text-xs text-[#b7591f] hover:bg-[#fdf1e6]"
                            >
                              删除
                            </button>
                          )}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {teachers.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无教师
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

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
    </div>
  )
}
