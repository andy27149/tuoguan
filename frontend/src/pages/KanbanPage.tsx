import { useCallback, useEffect, useState } from 'react'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import * as templatesApi from '../api/taskTemplates'
import * as dailyTasksApi from '../api/dailyTasks'
import * as dismissalApi from '../api/dismissal'
import type { DailyTask } from '../api/dailyTasks'
import { todayDateString } from '../kanban/date'
import { StudentCard } from '../components/StudentCard'
import { AssignTaskBar } from '../components/AssignTaskBar'
import { DismissButton } from '../components/DismissButton'
import { useAuth } from '../auth/AuthContext'

const date = todayDateString()

export function KanbanPage() {
  const { logout } = useAuth()
  const [classes, setClasses] = useState<classesApi.ClassRoom[]>([])
  const [activeClassId, setActiveClassId] = useState<number | null>(null)
  const [students, setStudents] = useState<studentsApi.Student[]>([])
  const [tasks, setTasks] = useState<DailyTask[]>([])
  const [templates, setTemplates] = useState<templatesApi.TaskTemplate[]>([])
  const [dismissed, setDismissed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([classesApi.fetchClasses(), templatesApi.fetchTaskTemplates()])
      .then(([classList, templateList]) => {
        setClasses(classList)
        setTemplates(templateList)
        if (classList.length > 0) {
          setActiveClassId(classList[0].id)
        } else {
          setLoading(false)
        }
      })
      .catch(() => {
        setError('加载失败，请刷新重试')
        setLoading(false)
      })
  }, [])

  const loadClassData = useCallback(async (classId: number) => {
    setLoading(true)
    setError(null)
    try {
      const [studentList, taskList, dismissalStatus] = await Promise.all([
        studentsApi.fetchStudents(classId),
        dailyTasksApi.listForClass(classId, date),
        dismissalApi.fetchDismissalStatus(classId, date),
      ])
      setStudents(studentList.filter((s) => s.enrolled))
      setTasks(taskList)
      setDismissed(dismissalStatus.dismissed)
    } catch {
      setError('加载班级数据失败，请刷新重试')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeClassId !== null) {
      loadClassData(activeClassId)
    }
  }, [activeClassId, loadClassData])

  async function refreshTasks() {
    if (activeClassId === null) return
    const taskList = await dailyTasksApi.listForClass(activeClassId, date)
    setTasks(taskList)
  }

  async function handleBatchAssign(templateIds: number[]) {
    if (activeClassId === null) return
    await dailyTasksApi.batchAssign(activeClassId, templateIds, date)
    await refreshTasks()
  }

  async function handleAddFromTemplate(studentId: number, templateId: number) {
    await dailyTasksApi.addFromTemplateForStudent(studentId, templateId, date)
    await refreshTasks()
  }

  async function handleAddCustom(studentId: number, subject: string, name: string) {
    await dailyTasksApi.addCustomForStudent(studentId, subject, name, date)
    await refreshTasks()
  }

  async function handleToggleTask(taskId: number, completed: boolean) {
    setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, completed } : t)))
    try {
      await dailyTasksApi.setCompleted(taskId, completed)
    } catch {
      setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, completed: !completed } : t)))
    }
  }

  async function handleDeleteTask(taskId: number) {
    const previous = tasks
    setTasks((prev) => prev.filter((t) => t.id !== taskId))
    try {
      await dailyTasksApi.deleteDailyTask(taskId)
    } catch {
      setTasks(previous)
    }
  }

  async function handleDismiss() {
    if (activeClassId === null) return
    await dismissalApi.dismissClass(activeClassId, date)
    setDismissed(true)
  }

  async function handleUndoDismiss() {
    if (activeClassId === null) return
    await dismissalApi.undoDismissClass(activeClassId, date)
    setDismissed(false)
  }

  const tasksByStudent = new Map<number, DailyTask[]>()
  for (const task of tasks) {
    const list = tasksByStudent.get(task.studentId) ?? []
    list.push(task)
    tasksByStudent.set(task.studentId, list)
  }

  if (!loading && classes.length === 0) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 text-center text-gray-500">
        <p>暂无托管班级，请联系机构管理员创建</p>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-8">
      <header className="sticky top-0 z-10 bg-white shadow-sm">
        <div className="flex items-center justify-between px-4 py-2">
          <h1 className="text-lg font-semibold">托管班看板</h1>
          <button type="button" onClick={logout} className="text-sm text-gray-400">
            退出登录
          </button>
        </div>
        <div className="flex gap-1 overflow-x-auto px-4 pb-2">
          {classes.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setActiveClassId(c.id)}
              className={`shrink-0 rounded-full px-3 py-1 text-sm ${
                activeClassId === c.id ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'
              }`}
            >
              {c.name}
            </button>
          ))}
        </div>
      </header>

      <main className="mx-auto max-w-md space-y-3 px-4 py-3">
        {error && <p className="text-sm text-red-600">{error}</p>}
        {loading && <p className="text-sm text-gray-400">加载中...</p>}

        {!loading && activeClassId !== null && (
          <>
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-500">{date}</p>
              <DismissButton dismissed={dismissed} onDismiss={handleDismiss} onUndoDismiss={handleUndoDismiss} />
            </div>

            <AssignTaskBar templates={templates} onAssign={handleBatchAssign} />

            <div className="space-y-3">
              {students.map((student) => (
                <StudentCard
                  key={student.id}
                  student={student}
                  tasks={tasksByStudent.get(student.id) ?? []}
                  dismissed={dismissed}
                  templates={templates}
                  onToggleTask={handleToggleTask}
                  onDeleteTask={handleDeleteTask}
                  onAddFromTemplate={handleAddFromTemplate}
                  onAddCustom={handleAddCustom}
                />
              ))}
              {students.length === 0 && <p className="text-sm text-gray-400">该班级暂无在读学生</p>}
            </div>
          </>
        )}
      </main>
    </div>
  )
}
