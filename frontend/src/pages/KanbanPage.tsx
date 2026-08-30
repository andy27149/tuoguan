import { useCallback, useEffect, useRef, useState } from 'react'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import * as templatesApi from '../api/taskTemplates'
import * as dailyTasksApi from '../api/dailyTasks'
import * as dismissalApi from '../api/dismissal'
import * as studentNotesApi from '../api/studentNotes'
import * as arrivalApi from '../api/arrival'
import * as adminApi from '../api/admin'
import * as adminKanbanApi from '../api/adminKanban'
import type { DailyTask } from '../api/dailyTasks'
import { todayDateString } from '../kanban/date'
import { groupBySchoolClass } from '../kanban/schoolClass'
import { StudentCard } from '../components/StudentCard'
import { AssignTaskBar } from '../components/AssignTaskBar'
import { DismissButton } from '../components/DismissButton'
import { TaskTemplateManager } from '../components/TaskTemplateManager'
import { Toast } from '../components/Toast'
import { useAuth } from '../auth/AuthContext'

const date = todayDateString()

interface StudentNote {
  rating: number
  comment: string
}

const EMPTY_NOTE: StudentNote = { rating: 0, comment: '' }

const EMPTY_ARRIVAL = ''

interface KanbanPageProps {
  onOpenRoster: () => void
  onOpenAdmin?: () => void
}

export function KanbanPage({ onOpenRoster, onOpenAdmin }: KanbanPageProps) {
  const { logout, state } = useAuth()
  const isAdmin = state.status === 'authenticated' && state.teacher.role === 'ADMIN'
  const [classes, setClasses] = useState<classesApi.ClassRoom[]>([])
  const [activeClassId, setActiveClassId] = useState<number | null>(null)
  const [students, setStudents] = useState<studentsApi.Student[]>([])
  const [tasks, setTasks] = useState<DailyTask[]>([])
  const [templates, setTemplates] = useState<templatesApi.TaskTemplate[]>([])
  const [notesByStudent, setNotesByStudent] = useState<Map<number, StudentNote>>(new Map())
  const [arrivalByStudent, setArrivalByStudent] = useState<Map<number, string>>(new Map())
  const [dismissed, setDismissed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const toastTimerRef = useRef<number | null>(null)

  const showToast = useCallback((message: string) => {
    setToastMessage(message)
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current)
    toastTimerRef.current = window.setTimeout(() => setToastMessage(null), 2200)
  }, [])

  useEffect(() => {
    const fetchClassList = isAdmin ? adminApi.fetchAdminClasses() : classesApi.fetchClasses()
    Promise.all([fetchClassList, templatesApi.fetchTaskTemplates()])
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin])

  const loadClassData = useCallback(
    async (classId: number) => {
      setLoading(true)
      setError(null)
      try {
        const [studentList, taskList, dismissalStatus, noteList, arrivalList] = isAdmin
          ? await Promise.all([
              adminKanbanApi.fetchStudents(classId),
              adminKanbanApi.listDailyTasksForClass(classId, date),
              adminKanbanApi.fetchDismissalStatus(classId, date),
              adminKanbanApi.fetchStudentNotes(classId, date),
              adminKanbanApi.fetchArrivals(classId, date),
            ])
          : await Promise.all([
              studentsApi.fetchStudents(classId),
              dailyTasksApi.listForClass(classId, date),
              dismissalApi.fetchDismissalStatus(classId, date),
              studentNotesApi.fetchStudentNotes(classId, date),
              arrivalApi.fetchArrivals(classId, date),
            ])
        setStudents(studentList.filter((s) => s.enrolled))
        setTasks(taskList)
        setDismissed(dismissalStatus.dismissed)
        setNotesByStudent(new Map(noteList.map((n) => [n.studentId, { rating: n.rating, comment: n.comment }])))
        setArrivalByStudent(new Map(arrivalList.map((a) => [a.studentId, a.arrivedAt])))
      } catch {
        setError('加载班级数据失败，请刷新重试')
      } finally {
        setLoading(false)
      }
    },
    [isAdmin],
  )

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

  async function handleCreateTemplate(subject: string, name: string) {
    const created = await templatesApi.createTaskTemplate(subject, name)
    setTemplates((prev) => [...prev, created])
  }

  async function handleDeleteTemplate(id: number) {
    await templatesApi.deleteTaskTemplate(id)
    setTemplates((prev) => prev.filter((t) => t.id !== id))
  }

  async function handleBatchAssign(schoolClassName: string, templateIds: number[]) {
    const representative = students.find((s) => s.schoolClassName === schoolClassName)
    if (!representative) return
    await Promise.all(
      templateIds.map((templateId) =>
        dailyTasksApi.addFromTemplateForStudent(representative.id, templateId, date),
      ),
    )
    await refreshTasks()
    showToast(`已分配给${schoolClassName}`)
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

  async function handleUploadAvatar(studentId: number, file: File) {
    const updated = await studentsApi.uploadAvatar(studentId, file)
    setStudents((prev) => prev.map((s) => (s.id === studentId ? updated : s)))
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

  async function handleSetRating(studentId: number, rating: number) {
    const previous = notesByStudent.get(studentId) ?? EMPTY_NOTE
    setNotesByStudent((prev) => new Map(prev).set(studentId, { ...previous, rating }))
    try {
      await studentNotesApi.setRating(studentId, date, rating)
    } catch {
      setNotesByStudent((prev) => new Map(prev).set(studentId, previous))
    }
  }

  async function handleSetComment(studentId: number, comment: string) {
    const previous = notesByStudent.get(studentId) ?? EMPTY_NOTE
    setNotesByStudent((prev) => new Map(prev).set(studentId, { ...previous, comment }))
    try {
      await studentNotesApi.setComment(studentId, date, comment)
    } catch {
      setNotesByStudent((prev) => new Map(prev).set(studentId, previous))
    }
  }

  async function handleSetArrival(studentId: number, arrivedAt: string) {
    const previous = arrivalByStudent.get(studentId) ?? EMPTY_ARRIVAL
    setArrivalByStudent((prev) => new Map(prev).set(studentId, arrivedAt))
    try {
      await arrivalApi.setArrival(studentId, date, arrivedAt)
    } catch {
      setArrivalByStudent((prev) => new Map(prev).set(studentId, previous))
    }
  }

  async function handleClearArrival(studentId: number) {
    const previous = arrivalByStudent.get(studentId) ?? EMPTY_ARRIVAL
    setArrivalByStudent((prev) => new Map(prev).set(studentId, EMPTY_ARRIVAL))
    try {
      await arrivalApi.clearArrival(studentId, date)
    } catch {
      setArrivalByStudent((prev) => new Map(prev).set(studentId, previous))
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

  const schoolClassGroups = groupBySchoolClass(students)

  if (!loading && classes.length === 0) {
    if (isAdmin) {
      return (
        <div className="no-class-screen">
          <p>本机构暂无托管班级</p>
          <div className="flex gap-2">
            {onOpenAdmin && (
              <button type="button" onClick={onOpenAdmin} className="logout-btn">
                前往机构管理查看教师
              </button>
            )}
            <button type="button" onClick={logout} className="logout-btn">
              退出登录
            </button>
          </div>
        </div>
      )
    }
    return (
      <div className="no-class-screen">
        <p>暂无托管班级</p>
        <button type="button" onClick={onOpenRoster} className="logout-btn">
          前往学生管理创建托管班
        </button>
      </div>
    )
  }

  function renderStudentCard(student: studentsApi.Student) {
    const note = notesByStudent.get(student.id) ?? EMPTY_NOTE
    const arrivedAt = arrivalByStudent.get(student.id) ?? EMPTY_ARRIVAL
    if (isAdmin) {
      return (
        <StudentCard
          key={student.id}
          student={student}
          tasks={tasksByStudent.get(student.id) ?? []}
          dismissed={dismissed}
          templates={templates}
          rating={note.rating}
          comment={note.comment}
          arrivedAt={arrivedAt}
          date={date}
          readOnly
          onShowToast={showToast}
          statsFetchFn={adminKanbanApi.fetchMonthlyStats}
          shareLinkFetchFn={adminKanbanApi.fetchShareLink}
        />
      )
    }
    return (
      <StudentCard
        key={student.id}
        student={student}
        tasks={tasksByStudent.get(student.id) ?? []}
        dismissed={dismissed}
        templates={templates}
        rating={note.rating}
        comment={note.comment}
        arrivedAt={arrivedAt}
        date={date}
        onToggleTask={handleToggleTask}
        onDeleteTask={handleDeleteTask}
        onAddFromTemplate={handleAddFromTemplate}
        onAddCustom={handleAddCustom}
        onUploadAvatar={handleUploadAvatar}
        onSetRating={handleSetRating}
        onSetComment={handleSetComment}
        onSetArrival={handleSetArrival}
        onClearArrival={handleClearArrival}
        onShowToast={showToast}
      />
    )
  }

  return (
    <div className="min-h-screen pb-8">
      <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true">
        <defs>
          <linearGradient id="ringAccent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#4F86F7" />
            <stop offset="100%" stopColor="#9B6BFF" />
          </linearGradient>
          <linearGradient id="ringGold" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FFD65A" />
            <stop offset="100%" stopColor="#FF9142" />
          </linearGradient>
        </defs>
      </svg>

      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">
            托管班看板
            {isAdmin && <span className="flag-chip">只读</span>}
          </h1>
          <div className="flex gap-2">
            {!isAdmin && (
              <button type="button" onClick={onOpenRoster} className="logout-btn">
                学生管理
              </button>
            )}
            {isAdmin && onOpenAdmin && (
              <button type="button" onClick={onOpenAdmin} className="logout-btn">
                机构管理
              </button>
            )}
            <button type="button" onClick={logout} className="logout-btn">
              退出登录
            </button>
          </div>
        </div>
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
      </header>

      <main>
        {error && <p className="text-sm text-red-600">{error}</p>}
        {loading && <p className="text-sm text-gray-400">加载中...</p>}

        {!loading && activeClassId !== null && (
          <>
            <div className="date-row">
              <span className="date-row__date">{date}</span>
              {!isAdmin && (
                <DismissButton dismissed={dismissed} onDismiss={handleDismiss} onUndoDismiss={handleUndoDismiss} />
              )}
            </div>

            {!isAdmin && (
              <>
                <TaskTemplateManager
                  templates={templates}
                  onCreate={handleCreateTemplate}
                  onDelete={handleDeleteTemplate}
                />

                <AssignTaskBar
                  studentsBySchoolClass={schoolClassGroups}
                  templates={templates}
                  onAssign={handleBatchAssign}
                />
              </>
            )}

            {schoolClassGroups.length > 1 ? (
              <div className="school-class-columns">
                {schoolClassGroups.map((group) => (
                  <div key={group.schoolClassName} className="school-class-column">
                    <h3 className="school-class-column__name">
                      {group.schoolClassName}
                      <span className="school-class-column__count">{group.students.length}人</span>
                    </h3>
                    <div className="student-grid">{group.students.map(renderStudentCard)}</div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="student-grid">{students.map(renderStudentCard)}</div>
            )}
            {students.length === 0 && <p className="text-sm text-gray-400">该班级暂无在读学生</p>}
          </>
        )}
      </main>

      <Toast message={toastMessage} />
    </div>
  )
}
