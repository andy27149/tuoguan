import { useCallback, useEffect, useState } from 'react'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import * as templatesApi from '../api/taskTemplates'
import * as dailyTasksApi from '../api/dailyTasks'
import * as dismissalApi from '../api/dismissal'
import * as studentNotesApi from '../api/studentNotes'
import * as arrivalApi from '../api/arrival'
import * as mealApi from '../api/meal'
import * as leaveApi from '../api/leave'
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
import { EmptyState } from '../components/EmptyState'
import { useToast } from '../hooks/useToast'
import { UpdatesButton } from '../components/UpdatesButton'
import { useAuth } from '../auth/AuthContext'
import { BrandMark } from '../brand/BrandMark'

const date = todayDateString()

interface StudentNote {
  rating: number
  comment: string
}

const EMPTY_NOTE: StudentNote = { rating: 0, comment: '' }

const EMPTY_ARRIVAL = ''
const EMPTY_LEAVE = ''

interface KanbanPageProps {
  onOpenRoster: () => void
  onOpenConsumption: () => void
  onOpenAdmin?: () => void
  initialClassId?: number
  hasClasses?: boolean
  hasCourses?: boolean
}

export function KanbanPage({
  onOpenRoster,
  onOpenConsumption,
  onOpenAdmin,
  initialClassId,
  hasClasses = true,
  hasCourses = true,
}: KanbanPageProps) {
  const { logout, state } = useAuth()
  const isAdmin = state.status === 'authenticated' && state.teacher.role === 'ADMIN'
  const [classes, setClasses] = useState<classesApi.ClassRoom[]>([])
  const [activeClassId, setActiveClassId] = useState<number | null>(null)
  const [students, setStudents] = useState<studentsApi.Student[]>([])
  const [tasks, setTasks] = useState<DailyTask[]>([])
  const [templates, setTemplates] = useState<templatesApi.TaskTemplate[]>([])
  const [notesByStudent, setNotesByStudent] = useState<Map<number, StudentNote>>(new Map())
  const [arrivalByStudent, setArrivalByStudent] = useState<Map<number, string>>(new Map())
  const [mealByStudent, setMealByStudent] = useState<Set<number>>(new Set())
  const [leaveByStudent, setLeaveByStudent] = useState<Map<number, string>>(new Map())
  const [dismissed, setDismissed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { toastMessage, showToast } = useToast()

  useEffect(() => {
    const fetchClassList = isAdmin ? adminApi.fetchAdminClasses() : classesApi.fetchClasses()
    Promise.all([fetchClassList, templatesApi.fetchTaskTemplates()])
      .then(([classList, templateList]) => {
        setClasses(classList)
        setTemplates(templateList)
        if (classList.length > 0) {
          const preferred =
            initialClassId !== undefined && classList.some((c) => c.id === initialClassId)
              ? initialClassId
              : classList[0].id
          setActiveClassId(preferred)
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
        // 管理员分支只调用 adminKanbanApi 的只读接口——这是前端层面的约定，不是后端强制
        // 的权限边界：管理员账号同时满足教师接口的鉴权条件，理论上能绕开这里调用写接口。
        // 新增功能时不要假设「管理员等于只读」，详见 AdminKanbanController 的类注释（产品诊断 #09）。
        // 管理员不需要实时看到谁请假——请假状态只在月度账单详情里展示，看板读
        // 接口不返回这个字段，这里用一个空占位 Promise 保持两边解构形状一致。
        const [studentList, taskList, dismissalStatus, noteList, arrivalList, mealList, leaveList] = isAdmin
          ? await Promise.all([
              adminKanbanApi.fetchStudents(classId),
              adminKanbanApi.listDailyTasksForClass(classId, date),
              adminKanbanApi.fetchDismissalStatus(classId, date),
              adminKanbanApi.fetchStudentNotes(classId, date),
              adminKanbanApi.fetchArrivals(classId, date),
              adminKanbanApi.fetchMeals(classId, date),
              Promise.resolve<leaveApi.StudentLeaveRecord[]>([]),
            ])
          : await Promise.all([
              studentsApi.fetchStudents(classId),
              dailyTasksApi.listForClass(classId, date),
              dismissalApi.fetchDismissalStatus(classId, date),
              studentNotesApi.fetchStudentNotes(classId, date),
              arrivalApi.fetchArrivals(classId, date),
              mealApi.fetchMeals(classId, date),
              leaveApi.fetchLeaves(classId, date),
            ])
        setStudents(studentList.filter((s) => s.enrolled))
        setTasks(taskList)
        setDismissed(dismissalStatus.dismissed)
        setNotesByStudent(new Map(noteList.map((n) => [n.studentId, { rating: n.rating, comment: n.comment }])))
        setArrivalByStudent(new Map(arrivalList.map((a) => [a.studentId, a.arrivedAt])))
        setMealByStudent(new Set(mealList.map((m) => m.studentId)))
        setLeaveByStudent(new Map(leaveList.map((l) => [l.studentId, l.reason ?? EMPTY_LEAVE])))
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

  async function handleBatchAssign(schoolClassName: string | null, templateIds: number[]) {
    const representative = students.find((s) => s.schoolClassName === schoolClassName)
    if (!representative) return
    await Promise.all(
      templateIds.map((templateId) =>
        dailyTasksApi.addFromTemplateForStudent(representative.id, templateId, date),
      ),
    )
    await refreshTasks()
    showToast(`已分配给${schoolClassName ?? '未分班'}`)
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
    const previousArrival = arrivalByStudent.get(studentId) ?? EMPTY_ARRIVAL
    const previousLeave = leaveByStudent.get(studentId) ?? EMPTY_LEAVE
    setArrivalByStudent((prev) => new Map(prev).set(studentId, arrivedAt))
    // 后端打卡到了会联动清除当天的请假记录，这里同步清本地状态，避免画面与服务端状态不一致。
    setLeaveByStudent((prev) => new Map(prev).set(studentId, EMPTY_LEAVE))
    try {
      await arrivalApi.setArrival(studentId, date, arrivedAt)
    } catch {
      setArrivalByStudent((prev) => new Map(prev).set(studentId, previousArrival))
      setLeaveByStudent((prev) => new Map(prev).set(studentId, previousLeave))
    }
  }

  async function handleClearArrival(studentId: number) {
    const previousArrival = arrivalByStudent.get(studentId) ?? EMPTY_ARRIVAL
    const previousHadMeal = mealByStudent.has(studentId)
    setArrivalByStudent((prev) => new Map(prev).set(studentId, EMPTY_ARRIVAL))
    // 后端清除签到会联动清除当天的用餐记录，这里同步清本地状态。
    setMealByStudent((prev) => {
      const next = new Set(prev)
      next.delete(studentId)
      return next
    })
    try {
      await arrivalApi.clearArrival(studentId, date)
    } catch {
      setArrivalByStudent((prev) => new Map(prev).set(studentId, previousArrival))
      setMealByStudent((prev) => {
        const next = new Set(prev)
        if (previousHadMeal) next.add(studentId)
        else next.delete(studentId)
        return next
      })
    }
  }

  async function handleSetMeal(studentId: number) {
    setMealByStudent((prev) => new Set(prev).add(studentId))
    try {
      await mealApi.setMeal(studentId, date)
    } catch {
      setMealByStudent((prev) => {
        const next = new Set(prev)
        next.delete(studentId)
        return next
      })
    }
  }

  async function handleClearMeal(studentId: number) {
    setMealByStudent((prev) => {
      const next = new Set(prev)
      next.delete(studentId)
      return next
    })
    try {
      await mealApi.clearMeal(studentId, date)
    } catch {
      setMealByStudent((prev) => new Set(prev).add(studentId))
    }
  }

  async function handleSetLeave(studentId: number, reason: string) {
    const previous = leaveByStudent.get(studentId) ?? EMPTY_LEAVE
    setLeaveByStudent((prev) => new Map(prev).set(studentId, reason))
    try {
      await leaveApi.setLeave(studentId, date, reason)
    } catch {
      setLeaveByStudent((prev) => new Map(prev).set(studentId, previous))
    }
  }

  async function handleClearLeave(studentId: number) {
    const previous = leaveByStudent.get(studentId) ?? EMPTY_LEAVE
    setLeaveByStudent((prev) => new Map(prev).set(studentId, EMPTY_LEAVE))
    try {
      await leaveApi.clearLeave(studentId, date)
    } catch {
      setLeaveByStudent((prev) => new Map(prev).set(studentId, previous))
    }
  }

  async function handleDismiss() {
    if (activeClassId === null) return
    await dismissalApi.dismissClass(activeClassId, date)
    setDismissed(true)
    showToast('已放学')
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
        <EmptyState icon="🏫" message="本机构暂无托管班级，前往机构管理可以创建班级、添加教师。">
          {onOpenAdmin && (
            <button type="button" onClick={onOpenAdmin} className="logout-btn">
              前往机构管理查看教师
            </button>
          )}
          <button type="button" onClick={logout} className="logout-btn">
            退出登录
          </button>
        </EmptyState>
      )
    }
    if (!hasClasses && hasCourses) {
      return (
        <EmptyState icon="📋" message="暂无托管班级，请联系管理员创建。你已经有课外课可以消课。">
          <button type="button" onClick={onOpenConsumption} className="logout-btn">
            前往消课
          </button>
          <button type="button" onClick={logout} className="logout-btn">
            退出登录
          </button>
        </EmptyState>
      )
    }
    return (
      <EmptyState icon="📋" message="暂无托管班级，请联系管理员创建。">
        <button type="button" onClick={onOpenRoster} className="logout-btn">
          前往学生管理
        </button>
        <button type="button" onClick={logout} className="logout-btn">
          退出登录
        </button>
      </EmptyState>
    )
  }

  function renderStudentCard(student: studentsApi.Student) {
    const note = notesByStudent.get(student.id) ?? EMPTY_NOTE
    const arrivedAt = arrivalByStudent.get(student.id) ?? EMPTY_ARRIVAL
    const hasMeal = mealByStudent.has(student.id)
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
          hasMeal={hasMeal}
          leaveReason={EMPTY_LEAVE}
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
        hasMeal={hasMeal}
        leaveReason={leaveByStudent.get(student.id) ?? EMPTY_LEAVE}
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
        onSetMeal={handleSetMeal}
        onClearMeal={handleClearMeal}
        onSetLeave={handleSetLeave}
        onClearLeave={handleClearLeave}
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
            <BrandMark size={22} />
            托管班看板
            {isAdmin && <span className="flag-chip">只读</span>}
          </h1>
          <div className="flex gap-2">
            {!isAdmin && hasClasses && (
              <button type="button" onClick={onOpenRoster} className="logout-btn">
                学生管理
              </button>
            )}
            {!isAdmin && hasCourses && (
              <button type="button" onClick={onOpenConsumption} className="logout-btn">
                消课
              </button>
            )}
            {isAdmin && onOpenAdmin && (
              <button type="button" onClick={onOpenAdmin} className="logout-btn">
                机构管理
              </button>
            )}
            <UpdatesButton />
            <button type="button" onClick={logout} className="logout-btn logout-btn--subtle">
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
                {schoolClassGroups.map((group, i) => (
                  <div key={group.schoolClassName ?? `__unassigned_${i}`} className="school-class-column">
                    <h3 className="school-class-column__name">
                      {group.schoolClassName ?? '未分班'}
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
