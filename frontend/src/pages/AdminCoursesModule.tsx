import { useEffect, useState, type FormEvent } from 'react'
import * as courseApi from '../api/course'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

export function AdminCoursesModule() {
  const [courses, setCourses] = useState<courseApi.AdminCourse[]>([])
  const [loadingCourses, setLoadingCourses] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)

  const [editingCourseId, setEditingCourseId] = useState<number | null>(null)
  const [editingTeacherId, setEditingTeacherId] = useState<number | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const [togglingCourseId, setTogglingCourseId] = useState<number | null>(null)

  const [newCourseName, setNewCourseName] = useState('')
  const [newCourseDuration, setNewCourseDuration] = useState('')
  const [newCourseTeacherId, setNewCourseTeacherId] = useState<number | null>(null)
  const [newCoursePrice, setNewCoursePrice] = useState('')
  const [creatingCourse, setCreatingCourse] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [editingPriceCourseId, setEditingPriceCourseId] = useState<number | null>(null)
  const [editPriceInput, setEditPriceInput] = useState('')
  const [priceSubmitting, setPriceSubmitting] = useState(false)
  const [priceError, setPriceError] = useState<string | null>(null)

  function loadCourses() {
    setLoadingCourses(true)
    setLoadError(null)
    courseApi
      .fetchAdminCourses()
      .then(setCourses)
      .catch(() => setLoadError('加载课程列表失败，请刷新重试'))
      .finally(() => setLoadingCourses(false))
  }

  useEffect(() => {
    loadCourses()
    adminApi
      .fetchTeachers()
      .then((list) => {
        setTeachers(list)
        const firstTeacher = list.find((t) => t.role === 'TEACHER')
        if (firstTeacher) setNewCourseTeacherId(firstTeacher.id)
      })
      .finally(() => setLoadingTeachers(false))
  }, [])

  function startEdit(course: courseApi.AdminCourse) {
    setEditingCourseId(course.id)
    setEditingTeacherId(course.teacherId)
    setEditError(null)
  }

  function cancelEdit() {
    setEditingCourseId(null)
    setEditError(null)
  }

  async function handleSaveEdit() {
    if (editingCourseId === null || editingTeacherId === null) return
    setEditSubmitting(true)
    setEditError(null)
    try {
      await courseApi.updateAdminCourse(editingCourseId, {
        teacherId: editingTeacherId,
      })
      setEditingCourseId(null)
      loadCourses()
    } catch {
      setEditError('保存失败，请重试')
    } finally {
      setEditSubmitting(false)
    }
  }

  async function handleToggleActive(course: courseApi.AdminCourse) {
    setTogglingCourseId(course.id)
    try {
      await courseApi.updateAdminCourse(course.id, { active: !course.active })
      loadCourses()
    } catch {
      setLoadError('操作失败，请重试')
    } finally {
      setTogglingCourseId(null)
    }
  }

  async function handleCreateCourse(e: FormEvent) {
    e.preventDefault()
    const name = newCourseName.trim()
    const duration = Number(newCourseDuration)
    if (!name || !Number.isFinite(duration) || duration <= 0 || newCourseTeacherId === null) return
    const price = newCoursePrice.trim() === '' ? null : Number(newCoursePrice)
    if (price !== null && (!Number.isFinite(price) || price < 0)) {
      setCreateError('请输入有效的单价')
      return
    }
    setCreatingCourse(true)
    setCreateError(null)
    try {
      await courseApi.createAdminCourse(name, duration, newCourseTeacherId, price)
      setNewCourseName('')
      setNewCourseDuration('')
      setNewCoursePrice('')
      loadCourses()
    } catch (err) {
      setCreateError(err instanceof ApiError && err.status === 409 ? '该课程名称已存在' : '创建失败，请重试')
    } finally {
      setCreatingCourse(false)
    }
  }

  function handleStartEditPrice(course: courseApi.AdminCourse) {
    setEditingPriceCourseId(course.id)
    setEditPriceInput(course.pricePerLesson !== null ? String(course.pricePerLesson) : '')
    setPriceError(null)
  }

  function handleCancelEditPrice() {
    setEditingPriceCourseId(null)
    setPriceError(null)
  }

  async function handleSavePrice(courseId: number) {
    const price = Number(editPriceInput)
    if (!Number.isFinite(price) || price < 0) {
      setPriceError('请输入有效的单价')
      return
    }
    setPriceSubmitting(true)
    setPriceError(null)
    try {
      await courseApi.updateAdminCourse(courseId, { pricePerLesson: price })
      setEditingPriceCourseId(null)
      loadCourses()
    } catch {
      setPriceError('保存失败，请重试')
    } finally {
      setPriceSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">新建课程</h2>
        <form onSubmit={handleCreateCourse} className="mt-3 flex flex-wrap items-end gap-2">
          <label className="text-sm text-[#5d5480]">
            课程名称
            <input
              placeholder="课程名称"
              value={newCourseName}
              onChange={(e) => setNewCourseName(e.target.value)}
              className="ml-2 w-32 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm text-[#5d5480]">
            时长（分钟）
            <input
              type="number"
              min={1}
              placeholder="时长（分钟）"
              value={newCourseDuration}
              onChange={(e) => setNewCourseDuration(e.target.value)}
              className="ml-2 w-24 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm text-[#5d5480]">
            负责教师
            <select
              aria-label="负责教师"
              value={newCourseTeacherId ?? ''}
              onChange={(e) => setNewCourseTeacherId(Number(e.target.value))}
              disabled={loadingTeachers}
              className="ml-2 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            >
              {teachers
                .filter((t) => t.role === 'TEACHER')
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name || t.phone}
                  </option>
                ))}
            </select>
          </label>
          <label className="text-sm text-[#5d5480]">
            单价（选填）
            <input
              placeholder="单价（选填）"
              value={newCoursePrice}
              onChange={(e) => setNewCoursePrice(e.target.value)}
              className="ml-2 w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
          <button
            type="submit"
            disabled={creatingCourse || !newCourseName.trim() || newCourseTeacherId === null}
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
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">课程列表（{courses.length}）</h2>
        {loadError && <p className="mt-2 text-sm text-[#b7591f]">{loadError}</p>}
        {editError && <p className="mt-2 text-sm text-[#b7591f]">{editError}</p>}
        {priceError && <p className="mt-2 text-sm text-[#b7591f]">{priceError}</p>}
        {loadingCourses && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loadingCourses && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">课程名称</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">负责教师</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">时长</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">单价</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">状态</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {courses.map((course) => {
                const isEditing = editingCourseId === course.id
                return (
                  <tr key={course.id} className="border-b border-[#ece7de] align-middle hover:bg-[#faf7ff]">
                    <td className="px-4 py-3">
                      <span className="font-medium text-[#241f3d]">{course.name}</span>
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <select
                          aria-label={`负责教师${course.id}`}
                          value={editingTeacherId ?? ''}
                          onChange={(e) => setEditingTeacherId(Number(e.target.value))}
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
                      ) : (
                        <span className="text-[#241f3d]">{course.teacherName || course.teacherPhone}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-[#7c7391]">{course.lessonDurationMinutes}分钟</span>
                    </td>
                    <td className="px-4 py-3">
                      {editingPriceCourseId === course.id ? (
                        <span className="flex items-center gap-1.5">
                          <input
                            aria-label={`单价${course.id}`}
                            value={editPriceInput}
                            onChange={(e) => setEditPriceInput(e.target.value)}
                            className="w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                          />
                          <button
                            type="button"
                            onClick={() => handleSavePrice(course.id)}
                            disabled={priceSubmitting}
                            className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelEditPrice}
                            disabled={priceSubmitting}
                            className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex items-center gap-1.5">
                          <span className="text-[#241f3d]">
                            {course.pricePerLesson !== null ? `¥${course.pricePerLesson.toFixed(2)}` : '未配置'}
                          </span>
                          <button
                            type="button"
                            onClick={() => handleStartEditPrice(course)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            设置单价
                          </button>
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={
                          course.active
                            ? 'rounded-full bg-[#e9f7f0] px-2 py-0.5 text-xs text-[#2f9e6e]'
                            : 'rounded-full bg-[#f3f0ff] px-2 py-0.5 text-xs text-[#7c7391]'
                        }
                      >
                        {course.active ? '启用中' : '已停用'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            onClick={handleSaveEdit}
                            disabled={editSubmitting}
                            className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={cancelEdit}
                            disabled={editSubmitting}
                            className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            aria-label={`改派${course.name}`}
                            onClick={() => startEdit(course)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            改派负责教师
                          </button>
                          <button
                            type="button"
                            aria-label={`${course.active ? '停用' : '启用'}${course.name}`}
                            onClick={() => handleToggleActive(course)}
                            disabled={togglingCourseId === course.id}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
                          >
                            {course.active ? '停用' : '启用'}
                          </button>
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {courses.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无课程
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
