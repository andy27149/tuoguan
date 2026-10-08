import { useEffect, useState, type FormEvent } from 'react'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'
import { ApiError } from '../api/client'
import { AdminCourseStatementModal } from '../components/AdminCourseStatementModal'
import { FeeManagementModal } from '../components/FeeManagementModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { currentMonthString } from '../kanban/date'

export function AdminStudentsModule() {
  const [students, setStudents] = useState<courseApi.AdminStudent[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [classRooms, setClassRooms] = useState<unitApi.TeachingUnit[]>([])

  const [newName, setNewName] = useState('')
  const [newSchoolClassName, setNewSchoolClassName] = useState('')
  const [newTeachingUnitId, setNewTeachingUnitId] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [statementStudent, setStatementStudent] = useState<{ id: number; name: string } | null>(null)
  const [managingStudent, setManagingStudent] = useState<{
    id: number
    name: string
    classRoomId: number | null
    className: string
  } | null>(null)

  const [editingStudent, setEditingStudent] = useState<courseApi.AdminStudent | null>(null)
  const [editingName, setEditingName] = useState('')
  const [editingSchoolClassName, setEditingSchoolClassName] = useState('')
  const [editingTeachingUnitId, setEditingTeachingUnitId] = useState('')
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const [confirmingDeactivate, setConfirmingDeactivate] = useState<courseApi.AdminStudent | null>(null)
  const [statusSubmitting, setStatusSubmitting] = useState(false)
  const [statusError, setStatusError] = useState<string | null>(null)

  function loadStudents() {
    setLoading(true)
    setLoadError(null)
    courseApi
      .fetchAdminStudents()
      .then(setStudents)
      .catch(() => setLoadError('加载学生列表失败，请刷新重试'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadStudents()
    unitApi
      .fetchTeachingUnits('MONTHLY')
      .then(setClassRooms)
      .catch(() => setClassRooms([]))
  }, [])

  async function handleCreateStudent(e: FormEvent) {
    e.preventDefault()
    const name = newName.trim()
    if (!name) return
    setCreating(true)
    setCreateError(null)
    try {
      await courseApi.createAdminStudent(
        name,
        newSchoolClassName.trim() || null,
        newTeachingUnitId ? Number(newTeachingUnitId) : null,
      )
      setNewName('')
      setNewSchoolClassName('')
      setNewTeachingUnitId('')
      loadStudents()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 404 || err.status === 400)) {
        setCreateError('所选托管班不可用，请刷新后重试')
      } else {
        setCreateError('创建失败，请重试')
      }
    } finally {
      setCreating(false)
    }
  }

  function handleStartEdit(student: courseApi.AdminStudent) {
    setEditingStudent(student)
    setEditingName(student.name)
    setEditingSchoolClassName(student.schoolClassName ?? '')
    setEditingTeachingUnitId(student.classRoomId ? String(student.classRoomId) : '')
    setEditError(null)
  }

  function handleCancelEdit() {
    setEditingStudent(null)
    setEditError(null)
  }

  async function handleSaveEdit() {
    if (!editingStudent) return
    const name = editingName.trim()
    if (!name) return
    setEditSubmitting(true)
    setEditError(null)
    try {
      await courseApi.updateAdminStudent(
        editingStudent.id,
        name,
        editingSchoolClassName.trim() || null,
        editingTeachingUnitId ? Number(editingTeachingUnitId) : null,
        editingStudent.enrolled,
      )
      setEditingStudent(null)
      loadStudents()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 404 || err.status === 400)) {
        setEditError('所选托管班不可用，请刷新后重试')
      } else {
        setEditError('保存失败，请重试')
      }
    } finally {
      setEditSubmitting(false)
    }
  }

  async function handleReactivate(student: courseApi.AdminStudent) {
    setStatusSubmitting(true)
    setStatusError(null)
    try {
      await courseApi.updateAdminStudent(student.id, student.name, student.schoolClassName, student.classRoomId, true)
      loadStudents()
    } catch {
      setStatusError('启用失败，请重试')
    } finally {
      setStatusSubmitting(false)
    }
  }

  async function handleConfirmDeactivate() {
    if (!confirmingDeactivate) return
    setStatusSubmitting(true)
    setStatusError(null)
    try {
      await courseApi.updateAdminStudent(
        confirmingDeactivate.id,
        confirmingDeactivate.name,
        confirmingDeactivate.schoolClassName,
        confirmingDeactivate.classRoomId,
        false,
      )
      setConfirmingDeactivate(null)
      loadStudents()
    } catch {
      setStatusError('停用失败，请重试')
    } finally {
      setStatusSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">新建学生</h2>
        <form onSubmit={handleCreateStudent} className="mt-3 flex flex-wrap items-end gap-2">
          <label className="text-sm text-[#5d5480]">
            姓名
            <input
              placeholder="姓名"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="ml-2 w-28 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm text-[#5d5480]">
            学籍班（选填）
            <input
              placeholder="学籍班"
              value={newSchoolClassName}
              onChange={(e) => setNewSchoolClassName(e.target.value)}
              className="ml-2 w-28 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm text-[#5d5480]">
            托管班（选填）
            <select
              aria-label="托管班"
              value={newTeachingUnitId}
              onChange={(e) => setNewTeachingUnitId(e.target.value)}
              className="ml-2 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            >
              <option value="">不挂靠（纯课外课学生）</option>
              {classRooms.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <button
            type="submit"
            disabled={creating || !newName.trim()}
            className="rounded-full bg-[#6d5bd0] px-4 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            创建
          </button>
        </form>
        {createError && (
          <p role="alert" className="mt-2 text-sm text-[#b7591f]">
            {createError}
          </p>
        )}
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">学生总览（{students.length}）</h2>
        {loadError && <p className="mt-2 text-sm text-[#b7591f]">{loadError}</p>}
        {editError && <p className="mt-2 text-sm text-[#b7591f]">{editError}</p>}
        {statusError && <p className="mt-2 text-sm text-[#b7591f]">{statusError}</p>}
        {loading && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loading && (
          <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs whitespace-nowrap text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">姓名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">学籍班</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管班</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管教师</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">身份</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">状态</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">课外课报名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {students.map((student) => {
                const isEditing = editingStudent?.id === student.id
                return (
                  <tr key={student.id} className="border-b border-[#ece7de] align-middle hover:bg-[#faf7ff]">
                    <td className="px-4 py-3 whitespace-nowrap">
                      {isEditing ? (
                        <input
                          aria-label={`学生姓名${student.id}`}
                          value={editingName}
                          onChange={(e) => setEditingName(e.target.value)}
                          className="w-24 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="font-medium text-[#241f3d]">{student.name}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {isEditing ? (
                        <input
                          aria-label={`学籍班${student.id}`}
                          value={editingSchoolClassName}
                          onChange={(e) => setEditingSchoolClassName(e.target.value)}
                          className="w-24 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="text-[#7c7391]">{student.schoolClassName || '—'}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {isEditing ? (
                        <select
                          aria-label={`托管班${student.id}`}
                          value={editingTeachingUnitId}
                          onChange={(e) => setEditingTeachingUnitId(e.target.value)}
                          className="rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        >
                          <option value="">不挂靠（纯课外课学生）</option>
                          {classRooms.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-[#7c7391]">{student.classRoomName || '—'}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="text-[#7c7391]">{student.teacherName || '—'}</span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={
                          student.offCampusOnly
                            ? 'rounded-full bg-[#fdf1e6] px-2 py-0.5 text-xs text-[#b7591f]'
                            : 'rounded-full bg-[#e9f7f0] px-2 py-0.5 text-xs text-[#2f9e6e]'
                        }
                      >
                        {student.offCampusOnly ? '纯课外' : '托管'}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={
                          student.enrolled
                            ? 'rounded-full bg-[#e9f7f0] px-2 py-0.5 text-xs text-[#2f9e6e]'
                            : 'rounded-full bg-[#f1f0f4] px-2 py-0.5 text-xs text-[#7c7391]'
                        }
                      >
                        {student.enrolled ? '已启用' : '已停用'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {student.enrolledCourseNames.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {student.enrolledCourseNames.map((name) => (
                            <span
                              key={name}
                              className="rounded-full bg-[#eef0fd] px-2 py-0.5 text-xs text-[#4b4a9c]"
                            >
                              {name}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-[#7c7391]">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <span className="flex flex-wrap gap-1.5">
                          <button
                            type="button"
                            onClick={handleSaveEdit}
                            disabled={editSubmitting || !editingName.trim()}
                            className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelEdit}
                            disabled={editSubmitting}
                            className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex flex-wrap gap-1.5">
                          <button
                            type="button"
                            aria-label={`编辑${student.name}`}
                            onClick={() => handleStartEdit(student)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            编辑
                          </button>
                          {student.enrolled ? (
                            <button
                              type="button"
                              aria-label={`停用${student.name}`}
                              onClick={() => setConfirmingDeactivate(student)}
                              disabled={statusSubmitting}
                              className="rounded-full border border-[#f3d0c0] px-3 py-1 text-xs text-[#b7591f] hover:bg-[#fdf1e6] disabled:opacity-50"
                            >
                              停用
                            </button>
                          ) : (
                            <button
                              type="button"
                              aria-label={`启用${student.name}`}
                              onClick={() => handleReactivate(student)}
                              disabled={statusSubmitting}
                              className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
                            >
                              启用
                            </button>
                          )}
                          {(student.offCampusOnly || student.enrolledCourseNames.length > 0) && (
                            <>
                              <button
                                type="button"
                                onClick={() => setStatementStudent({ id: student.id, name: student.name })}
                                className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                              >
                                对账单/充值
                              </button>
                              <button
                                type="button"
                                onClick={() =>
                                  setManagingStudent({
                                    id: student.id,
                                    name: student.name,
                                    classRoomId: student.classRoomId,
                                    className: student.classRoomName ?? '—',
                                  })
                                }
                                className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                              >
                                费用管理
                              </button>
                            </>
                          )}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {students.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无学生
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          </div>
        )}
      </div>

      {statementStudent && (
        <AdminCourseStatementModal
          studentId={statementStudent.id}
          studentName={statementStudent.name}
          onClose={() => setStatementStudent(null)}
        />
      )}

      {managingStudent && (
        <FeeManagementModal
          studentId={managingStudent.id}
          studentName={managingStudent.name}
          classRoomId={managingStudent.classRoomId}
          className={managingStudent.className}
          month={currentMonthString()}
          onClose={() => setManagingStudent(null)}
          onSaved={() => setManagingStudent(null)}
        />
      )}

      {confirmingDeactivate && (
        <ConfirmDialog
          title="停用学生"
          message={`停用${confirmingDeactivate.name}后，该学生将从看板上隐藏，已产生的记录不会被删除，可随时重新启用。确认停用吗？`}
          confirmLabel="确认停用"
          confirming={statusSubmitting}
          onConfirm={handleConfirmDeactivate}
          onCancel={() => setConfirmingDeactivate(null)}
        />
      )}
    </div>
  )
}
