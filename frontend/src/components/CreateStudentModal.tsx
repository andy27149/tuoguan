import { useState, type FormEvent } from 'react'
import type { TeachingUnit } from '../api/unit'
import { ApiError } from '../api/client'

interface CreateStudentModalProps {
  classRooms: TeachingUnit[]
  offCampusCourses: TeachingUnit[]
  onCreate: (
    name: string,
    schoolClassName: string | null,
    teachingUnitId: number | null,
    courseIds: number[],
  ) => Promise<void>
  onClose: () => void
}

export function CreateStudentModal({ classRooms, offCampusCourses, onCreate, onClose }: CreateStudentModalProps) {
  const [name, setName] = useState('')
  const [schoolClassName, setSchoolClassName] = useState('')
  const [teachingUnitId, setTeachingUnitId] = useState('')
  const [courseIds, setCourseIds] = useState<number[]>([])
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  function toggleCourseId(courseId: number) {
    setCourseIds((prev) => (prev.includes(courseId) ? prev.filter((id) => id !== courseId) : [...prev, courseId]))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmedName = name.trim()
    if (!trimmedName) return
    setCreating(true)
    setCreateError(null)
    try {
      await onCreate(trimmedName, schoolClassName.trim() || null, teachingUnitId ? Number(teachingUnitId) : null, courseIds)
      onClose()
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="新建学生"
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-2xl border border-[#ece7de] bg-white shadow-[0_20px_50px_rgba(36,31,61,0.25)]"
      >
        <div className="flex items-center justify-between border-b border-[#ece7de] px-5 py-4">
          <h2 className="font-['Sora'] text-base font-bold text-[#241f3d]">新建学生</h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="rounded-lg px-1.5 py-0.5 text-xl leading-none text-[#7c7391] hover:bg-[#faf7ff] hover:text-[#241f3d]"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5 px-5 py-4">
          <div className="flex gap-3">
            <label className="flex-1 text-xs font-semibold text-[#5d5480]">
              姓名
              <input
                placeholder="姓名"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[#ece7de] px-2.5 py-2 text-sm text-[#241f3d]"
              />
            </label>
            <label className="flex-1 text-xs font-semibold text-[#5d5480]">
              学籍班（选填）
              <input
                placeholder="学籍班"
                value={schoolClassName}
                onChange={(e) => setSchoolClassName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[#ece7de] px-2.5 py-2 text-sm text-[#241f3d]"
              />
            </label>
          </div>

          <label className="text-xs font-semibold text-[#5d5480]">
            托管班（选填）
            <select
              value={teachingUnitId}
              onChange={(e) => setTeachingUnitId(e.target.value)}
              className="mt-1 w-full rounded-lg border border-[#ece7de] px-2.5 py-2 text-sm text-[#241f3d]"
            >
              <option value="">纯课外课学生</option>
              {classRooms.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>

          {offCampusCourses.length > 0 && (
            <div>
              <div className="mb-1.5 flex items-baseline justify-between">
                <span className="text-xs font-semibold text-[#5d5480]">课外课（可多选）</span>
                <span className="text-[11px] text-[#7c7391]">已选 {courseIds.length} 门</span>
              </div>
              <div className="max-h-[150px] overflow-y-auto rounded-xl border border-[#ece7de] bg-[#faf9f6] p-2.5">
                <div className="flex flex-wrap gap-1.5">
                  {offCampusCourses.map((c) => {
                    const selected = courseIds.includes(c.id)
                    return (
                      <button
                        key={c.id}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => toggleCourseId(c.id)}
                        className={
                          selected
                            ? 'rounded-full border border-[#6d5bd0] bg-[#6d5bd0] px-3 py-1.5 text-xs font-semibold text-white'
                            : 'rounded-full border border-[#ece7de] bg-white px-3 py-1.5 text-xs font-semibold text-[#5d5480] hover:bg-[#faf7ff]'
                        }
                      >
                        {c.name}
                      </button>
                    )
                  })}
                </div>
              </div>
            </div>
          )}

          {createError && (
            <p role="alert" className="text-sm text-[#b7591f]">
              {createError}
            </p>
          )}

          <div className="flex justify-end gap-2 border-t border-[#ece7de] pt-3.5">
            <button
              type="button"
              onClick={onClose}
              disabled={creating}
              className="rounded-full border border-[#ece7de] px-4 py-1.5 text-sm font-medium text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={creating || !name.trim()}
              className="rounded-full bg-[#6d5bd0] px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              创建
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
