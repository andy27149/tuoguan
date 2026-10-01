import { useEffect, useState } from 'react'
import * as courseApi from '../api/course'
import * as adminApi from '../api/admin'

export function AdminCoursesModule() {
  const [courses, setCourses] = useState<courseApi.AdminCourse[]>([])
  const [loadingCourses, setLoadingCourses] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)

  const [editingCourseId, setEditingCourseId] = useState<number | null>(null)
  const [editingPrice, setEditingPrice] = useState('')
  const [editingTeacherId, setEditingTeacherId] = useState<number | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const [togglingCourseId, setTogglingCourseId] = useState<number | null>(null)

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
      .then(setTeachers)
      .finally(() => setLoadingTeachers(false))
  }, [])

  function startEdit(course: courseApi.AdminCourse) {
    setEditingCourseId(course.id)
    setEditingPrice(course.pricePerLesson !== null ? String(course.pricePerLesson) : '')
    setEditingTeacherId(course.teacherId)
    setEditError(null)
  }

  function cancelEdit() {
    setEditingCourseId(null)
    setEditError(null)
  }

  async function handleSaveEdit() {
    if (editingCourseId === null || editingTeacherId === null) return
    const price = Number(editingPrice)
    if (!Number.isFinite(price) || price < 0) {
      setEditError('请输入有效的单价')
      return
    }
    setEditSubmitting(true)
    setEditError(null)
    try {
      await courseApi.updateAdminCourse(editingCourseId, {
        pricePerLesson: price,
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

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">课外课程列表（{courses.length}）</h2>
        {loadError && <p className="mt-2 text-sm text-[#b7591f]">{loadError}</p>}
        {editError && <p className="mt-2 text-sm text-[#b7591f]">{editError}</p>}
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
                      {isEditing ? (
                        <input
                          aria-label={`课程单价${course.id}`}
                          value={editingPrice}
                          onChange={(e) => setEditingPrice(e.target.value)}
                          className="w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="text-[#241f3d]">
                          {course.pricePerLesson !== null ? `¥${course.pricePerLesson.toFixed(2)}` : '未配置'}
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
                            aria-label={`设置单价${course.name}`}
                            onClick={() => startEdit(course)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            设置单价/改派
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
