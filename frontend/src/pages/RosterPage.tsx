import { useEffect, useState, type FormEvent } from 'react'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import { ApiError } from '../api/client'

interface RosterPageProps {
  onBack: () => void
}

export function RosterPage({ onBack }: RosterPageProps) {
  const [classes, setClasses] = useState<classesApi.ClassRoom[]>([])
  const [activeClassId, setActiveClassId] = useState<number | null>(null)
  const [students, setStudents] = useState<studentsApi.Student[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [newClassName, setNewClassName] = useState('')
  const [creatingClass, setCreatingClass] = useState(false)
  const [classError, setClassError] = useState<string | null>(null)

  const [newStudentName, setNewStudentName] = useState('')
  const [newStudentSchoolClass, setNewStudentSchoolClass] = useState('')
  const [creatingStudent, setCreatingStudent] = useState(false)
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

  async function handleCreateClass(e: FormEvent) {
    e.preventDefault()
    const name = newClassName.trim()
    if (!name) return
    setCreatingClass(true)
    setClassError(null)
    try {
      const created = await classesApi.createClass(name)
      setClasses((prev) => [...prev, created])
      setActiveClassId(created.id)
      setNewClassName('')
    } catch (err) {
      setClassError(err instanceof ApiError && err.status === 409 ? '该托管班名称已存在' : '创建失败，请重试')
    } finally {
      setCreatingClass(false)
    }
  }

  async function handleCreateStudent(e: FormEvent) {
    e.preventDefault()
    if (activeClassId === null) return
    const name = newStudentName.trim()
    const schoolClassName = newStudentSchoolClass.trim()
    if (!name || !schoolClassName) return
    setCreatingStudent(true)
    setStudentError(null)
    try {
      await studentsApi.createStudent(activeClassId, name, schoolClassName)
      await refreshStudents()
      setNewStudentName('')
      setNewStudentSchoolClass('')
    } catch {
      setStudentError('添加失败，请重试')
    } finally {
      setCreatingStudent(false)
    }
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
          <h1 className="app-header__title">学生/花名册管理</h1>
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
        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <h2 className="text-sm font-medium text-gray-700">新建托管班</h2>
          <form onSubmit={handleCreateClass} className="mt-2 flex gap-2">
            <input
              placeholder="托管班名称"
              value={newClassName}
              onChange={(e) => setNewClassName(e.target.value)}
              className="flex-1 rounded border px-2 py-1 text-sm"
            />
            <button
              type="submit"
              disabled={creatingClass || !newClassName.trim()}
              className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
            >
              创建
            </button>
          </form>
          {classError && (
            <p role="alert" className="mt-1 text-xs text-red-600">
              {classError}
            </p>
          )}
        </div>

        {loadError && <p className="text-sm text-red-600">{loadError}</p>}
        {loading && <p className="text-sm text-gray-400">加载中...</p>}

        {!loading && classes.length === 0 && (
          <p className="text-sm text-gray-400">暂无托管班，先在上方创建一个吧</p>
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

            <form onSubmit={handleCreateStudent} className="mt-3 flex flex-wrap gap-2">
              <input
                placeholder="姓名"
                value={newStudentName}
                onChange={(e) => setNewStudentName(e.target.value)}
                className="w-24 rounded border px-2 py-1 text-sm"
              />
              <input
                placeholder="学籍班"
                value={newStudentSchoolClass}
                onChange={(e) => setNewStudentSchoolClass(e.target.value)}
                className="w-28 rounded border px-2 py-1 text-sm"
              />
              <button
                type="submit"
                disabled={creatingStudent || !newStudentName.trim() || !newStudentSchoolClass.trim()}
                className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
              >
                新增学生
              </button>
            </form>
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
