import { useEffect, useState } from 'react'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import { BrandMark } from '../brand/BrandMark'

interface RosterPageProps {
  onBack: () => void
}

export function RosterPage({ onBack }: RosterPageProps) {
  const [classes, setClasses] = useState<classesApi.ClassRoom[]>([])
  const [activeClassId, setActiveClassId] = useState<number | null>(null)
  const [students, setStudents] = useState<studentsApi.Student[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [studentError, setStudentError] = useState<string | null>(null)

  const [editingStudentId, setEditingStudentId] = useState<number | null>(null)
  const [editName, setEditName] = useState('')
  const [editSchoolClass, setEditSchoolClass] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)

  useEffect(() => {
    loadClasses()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function loadClasses() {
    setLoading(true)
    setLoadError(null)
    classesApi
      .fetchClasses()
      .then((classList) => {
        setClasses(classList)
        setActiveClassId((prev) => prev ?? (classList.length > 0 ? classList[0].id : null))
        if (classList.length === 0) setLoading(false)
      })
      .catch(() => {
        setLoadError('加载失败，请刷新重试')
        setLoading(false)
      })
  }

  useEffect(() => {
    if (activeClassId === null) return
    setLoading(true)
    setLoadError(null)
    studentsApi
      .fetchStudents(activeClassId)
      .then(setStudents)
      .catch(() => setLoadError('加载学生列表失败，请刷新重试'))
      .finally(() => setLoading(false))
  }, [activeClassId])

  async function refreshStudents() {
    if (activeClassId === null) return
    const list = await studentsApi.fetchStudents(activeClassId)
    setStudents(list)
  }

  function startEdit(student: studentsApi.Student) {
    setEditingStudentId(student.id)
    setEditName(student.name)
    setEditSchoolClass(student.schoolClassName)
  }

  function cancelEdit() {
    setEditingStudentId(null)
  }

  async function handleSaveEdit(student: studentsApi.Student) {
    const name = editName.trim()
    const schoolClassName = editSchoolClass.trim()
    if (!name || !schoolClassName) return
    setSavingEdit(true)
    try {
      await studentsApi.updateStudent(student.id, name, schoolClassName, student.enrolled)
      await refreshStudents()
      setEditingStudentId(null)
    } catch {
      setStudentError('保存失败，请重试')
    } finally {
      setSavingEdit(false)
    }
  }

  async function handleToggleEnrolled(student: studentsApi.Student) {
    const previous = students
    setStudents((prev) =>
      prev.map((s) => (s.id === student.id ? { ...s, enrolled: !s.enrolled } : s)),
    )
    try {
      await studentsApi.updateStudent(student.id, student.name, student.schoolClassName, !student.enrolled)
    } catch {
      setStudents(previous)
      setStudentError('操作失败，请重试')
    }
  }

  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">
            <BrandMark size={22} />
            学生/花名册管理
          </h1>
          <button type="button" onClick={onBack} className="logout-btn">
            返回看板
          </button>
        </div>
        {classes.length > 0 && (
          <div className="class-tabs" role="tablist">
            {classes.map((c) => (
              <button
                key={c.id}
                type="button"
                role="tab"
                aria-selected={activeClassId === c.id}
                className="class-tab"
                onClick={() => setActiveClassId(c.id)}
              >
                {c.name}
              </button>
            ))}
          </div>
        )}
      </header>

      <main className="mx-auto max-w-2xl space-y-4 px-4 pt-4">
        {loadError && <p className="text-sm text-red-600">{loadError}</p>}
        {loading && <p className="text-sm text-gray-400">加载中...</p>}

        {!loading && classes.length === 0 && (
          <p className="text-sm text-gray-400">暂无托管班，请联系管理员创建</p>
        )}

        {!loading && activeClassId !== null && classes.length > 0 && (
          <div className="rounded-lg border border-gray-200 bg-white p-3">
            <h2 className="text-sm font-medium text-gray-700">学生列表（{students.length}）</h2>
            <ul className="mt-2 space-y-2">
              {students.map((student) => (
                <li key={student.id} className="rounded border border-gray-100 p-2 text-sm">
                  {editingStudentId === student.id ? (
                    <div className="flex flex-wrap items-center gap-2">
                      <input
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        className="w-24 rounded border px-2 py-1 text-sm"
                      />
                      <input
                        value={editSchoolClass}
                        onChange={(e) => setEditSchoolClass(e.target.value)}
                        className="w-28 rounded border px-2 py-1 text-sm"
                      />
                      <button
                        type="button"
                        onClick={() => handleSaveEdit(student)}
                        disabled={savingEdit || !editName.trim() || !editSchoolClass.trim()}
                        className="rounded bg-blue-600 px-2 py-1 text-xs text-white disabled:opacity-50"
                      >
                        保存
                      </button>
                      <button
                        type="button"
                        onClick={cancelEdit}
                        className="rounded border px-2 py-1 text-xs text-gray-600"
                      >
                        取消
                      </button>
                    </div>
                  ) : (
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span>
                        {student.name} · {student.schoolClassName}
                        {!student.enrolled && <span className="ml-2 text-xs text-gray-400">（已停用）</span>}
                        {student.enrolledCourseNames.length > 0 && (
                          <span className="ml-2 text-xs text-gray-400">
                            另报名：{student.enrolledCourseNames.join('、')}
                          </span>
                        )}
                      </span>
                      <span className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => startEdit(student)}
                          className="rounded border px-2 py-1 text-xs text-gray-600"
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          onClick={() => handleToggleEnrolled(student)}
                          className="rounded border px-2 py-1 text-xs text-gray-600"
                        >
                          {student.enrolled ? '停用' : '启用'}
                        </button>
                      </span>
                    </div>
                  )}
                </li>
              ))}
              {students.length === 0 && <li className="text-xs text-gray-400">该班暂无学生</li>}
            </ul>

            <p className="mt-3 border-t border-gray-100 pt-3 text-xs text-gray-400">新增学生请联系管理员添加</p>
            {studentError && (
              <p role="alert" className="mt-1 text-xs text-red-600">
                {studentError}
              </p>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
