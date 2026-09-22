import { useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'

export function AdminClassesModule() {
  const [classes, setClasses] = useState<adminApi.AdminClassRoom[]>([])
  const [loadingClasses, setLoadingClasses] = useState(true)
  const [classLoadError, setClassLoadError] = useState<string | null>(null)

  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)

  const [deletingClassRoom, setDeletingClassRoom] = useState<adminApi.AdminClassRoom | null>(null)
  const [classDeletionImpact, setClassDeletionImpact] = useState<adminApi.ClassRoomDeletionImpact | null>(null)
  const [classDeleteSubmitting, setClassDeleteSubmitting] = useState(false)
  const [classDeleteError, setClassDeleteError] = useState<string | null>(null)

  const [editingClassRoomId, setEditingClassRoomId] = useState<number | null>(null)
  const [editingClassName, setEditingClassName] = useState('')
  const [editingClassTeacherId, setEditingClassTeacherId] = useState<number | null>(null)
  const [classEditSubmitting, setClassEditSubmitting] = useState(false)
  const [classEditError, setClassEditError] = useState<string | null>(null)

  function loadClasses() {
    setLoadingClasses(true)
    setClassLoadError(null)
    adminApi
      .fetchAdminClasses()
      .then(setClasses)
      .catch(() => setClassLoadError('加载班级列表失败，请刷新重试'))
      .finally(() => setLoadingClasses(false))
  }

  function loadTeachers() {
    setLoadingTeachers(true)
    adminApi
      .fetchTeachers()
      .then(setTeachers)
      .finally(() => setLoadingTeachers(false))
  }

  useEffect(() => {
    loadClasses()
    loadTeachers()
  }, [])

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
    } catch (err) {
      setClassEditError(err instanceof ApiError && err.status === 409 ? '该教师下已有同名班级' : '保存失败，请重试')
    } finally {
      setClassEditSubmitting(false)
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
    } catch {
      setClassDeleteError('删除失败，请重试')
    } finally {
      setClassDeleteSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">托管班级列表（{classes.length}）</h2>
        {classLoadError && <p className="mt-2 text-sm text-[#b7591f]">{classLoadError}</p>}
        {classDeleteError && <p className="mt-2 text-sm text-[#b7591f]">{classDeleteError}</p>}
        {classEditError && <p className="mt-2 text-sm text-[#b7591f]">{classEditError}</p>}
        {loadingClasses && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loadingClasses && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">班级名称</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">班主任</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">登录账号</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {classes.map((classRoom) => {
                const isEditing = editingClassRoomId === classRoom.id
                const avatarColors = ['#6d5bd0', '#d9701e', '#2f9e8f', '#c0569b', '#4f86c9']
                const avatarColor = avatarColors[classRoom.teacherId % avatarColors.length]
                const avatarInitial = (classRoom.teacherName || classRoom.teacherPhone).charAt(0)
                return (
                  <tr key={classRoom.id} className="border-b border-[#ece7de] align-middle hover:bg-[#faf7ff]">
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <input
                          aria-label={`班级名称${classRoom.id}`}
                          value={editingClassName}
                          onChange={(e) => setEditingClassName(e.target.value)}
                          className="w-28 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="font-medium text-[#241f3d]">{classRoom.name}</span>
                      )}
                    </td>
                    {isEditing ? (
                      <td className="px-4 py-3" colSpan={2}>
                        <div className="space-y-1">
                          <select
                            aria-label={`班级教师${classRoom.id}`}
                            value={editingClassTeacherId ?? ''}
                            onChange={(e) => setEditingClassTeacherId(Number(e.target.value))}
                            className="rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                            disabled={loadingTeachers}
                          >
                            {teachers
                              .filter((t) => t.role === 'TEACHER')
                              .map((t) => (
                                <option key={t.id} value={t.id}>
                                  {t.name || t.phone}
                                </option>
                              ))}
                          </select>
                          {editingClassTeacherId !== null && editingClassTeacherId !== classRoom.teacherId && (
                            <p className="rounded-lg bg-[#fdf1e6] px-2 py-1 text-xs text-[#b7591f]">
                              该操作将把班级「{classRoom.name}」（含班内学生、任务库等全部内容）整体转移给新教师，确认后无法撤回
                            </p>
                          )}
                        </div>
                      </td>
                    ) : (
                      <>
                        <td className="px-4 py-3">
                          <span className="flex items-center gap-2">
                            <span
                              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white"
                              style={{ backgroundColor: avatarColor }}
                            >
                              {avatarInitial}
                            </span>
                            <span className="text-[#241f3d]">{classRoom.teacherName}</span>
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span className="text-[#7c7391]">{classRoom.teacherPhone}</span>
                        </td>
                      </>
                    )}
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            onClick={handleSaveClassRoom}
                            disabled={classEditSubmitting || !editingClassName.trim()}
                            className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelEditClassRoom}
                            disabled={classEditSubmitting}
                            className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            aria-label={`编辑班级${classRoom.name}`}
                            onClick={() => handleStartEditClassRoom(classRoom)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            编辑
                          </button>
                          <button
                            type="button"
                            aria-label={`删除班级${classRoom.name}`}
                            onClick={() => handleOpenDeleteClassRoom(classRoom)}
                            className="rounded-full border border-[#f3d0c0] px-3 py-1 text-xs text-[#b7591f] hover:bg-[#fdf1e6]"
                          >
                            删除
                          </button>
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {classes.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无班级
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

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
