import { useCallback, useEffect, useRef, useState } from 'react'
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
import * as courseApi from '../api/course'
import type { DailyTask } from '../api/dailyTasks'
import { todayDateString } from '../kanban/date'
import { groupBySchoolClass } from '../kanban/schoolClass'
import { StudentCard } from '../components/StudentCard'
import { StudentOverviewGrid } from '../components/StudentOverviewGrid'
import { StudentQuickActionPopup } from '../components/StudentQuickActionPopup'
import { AssignTaskBar } from '../components/AssignTaskBar'
import { CompleteAllTasksButton } from '../components/CompleteAllTasksButton'
import { DismissButton } from '../components/DismissButton'
import { TaskTemplateManager } from '../components/TaskTemplateManager'
import { Toast } from '../components/Toast'
import { EmptyState } from '../components/EmptyState'
import { useToast } from '../hooks/useToast'
import { UpdatesButton } from '../components/UpdatesButton'
import { HelpModal } from '../components/HelpModal'
import { useAuth } from '../auth/AuthContext'
import { BrandMark } from '../brand/BrandMark'
import { LOAD_FAILURE_MESSAGE } from '../constants'

const date = todayDateString()

interface StudentNote {
  rating: number
  comment: string
}

const EMPTY_NOTE: StudentNote = { rating: 0, comment: '' }

const EMPTY_ARRIVAL = ''
const EMPTY_LEAVE = ''

// 超过这个人数，切班级时默认打开总览视图而不是逐张卡片的列表视图——大班级
// 翻半天才能找到一个学生，总览网格按头像+姓名紧凑排布，点进去再操作。
const OVERVIEW_DEFAULT_THRESHOLD = 10

// 管理员拿到的班级列表（adminApi.fetchAdminClasses）带 teacherId，老师自己的班级列表
// （classesApi.fetchClasses）不带——用联合类型而不是单独维护一份映射，这样"这个班是不是
// 我自己带的"这个判断可以直接查 classes 状态里已有的数据，不需要额外请求。
type KanbanClassRoom = classesApi.ClassRoom | adminApi.AdminClassRoom

interface KanbanPageProps {
  onOpenConsumption: () => void
  onOpenAdmin?: () => void
  initialClassId?: number
  hasClasses?: boolean
  hasCourses?: boolean
}

export function KanbanPage({
  onOpenConsumption,
  onOpenAdmin,
  initialClassId,
  hasClasses = true,
  hasCourses = true,
}: KanbanPageProps) {
  const { logout, state } = useAuth()
  const isAdmin = state.status === 'authenticated' && state.teacher.role === 'ADMIN'
  const [classes, setClasses] = useState<KanbanClassRoom[]>([])
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
  const [showHelp, setShowHelp] = useState(false)
  const { toastMessage, showToast } = useToast()

  const [viewMode, setViewMode] = useState<'overview' | 'list'>('list')
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null)
  // 每个班级只在第一次加载完成时按人数套一次默认视图，之后老师手动切换的选择
  // 在同一个班级内保留，不会被这个 effect 覆盖掉；切到另一个班级才重新判断。
  const appliedDefaultForClassId = useRef<number | null>(null)
  useEffect(() => {
    if (activeClassId === null || loading) return
    if (appliedDefaultForClassId.current === activeClassId) return
    setViewMode(students.length > OVERVIEW_DEFAULT_THRESHOLD ? 'overview' : 'list')
    appliedDefaultForClassId.current = activeClassId
  }, [activeClassId, loading, students])

  // 机构管理员本人同时是某个托管班带班老师的场景（诊断：管理员的教师身份本来就是隐式的，
  // 后端从不检查 teaching_unit.teacher_id 对应账号的 role，写接口也只按 teacherId 判断
  // 归属，不看角色）——这里让管理员在查看"自己带的班"时，前端也跟着解除只读限制，按老师
  // 一样展示完整操作入口。查看其它老师的班级时仍然维持原来的只读看板，不受影响。
  function ownsClass(classId: number | null): boolean {
    if (!isAdmin || classId === null || state.status !== 'authenticated') return false
    const cls = classes.find((c) => c.id === classId)
    return cls !== undefined && 'teacherId' in cls && cls.teacherId === state.teacher.id
  }

  const isReadOnlyView = isAdmin && !ownsClass(activeClassId)

  // 同样的双重身份场景也能发生在课外课上：管理员本人被挂为某个课外课的负责教师时，
  // 后端 /courses 接口（CourseController，见产品诊断同一条）只按 principal.teacherId()
  // 过滤，对管理员账号同样完全可用——缺的只是入口。这里单独查一次"我自己名下有没有
  // 课外课"，跟 ownsClass 不一样的是它和当前查看哪个班无关，是账号级别的、决定header
  // 里"消课"入口要不要出现。普通老师完全不受影响，继续用上层传下来的 hasCourses。
  const [hasOwnCourses, setHasOwnCourses] = useState(false)
  useEffect(() => {
    if (!isAdmin) return
    courseApi
      .fetchMyCourses()
      .then((list) => setHasOwnCourses(list.length > 0))
      .catch(() => setHasOwnCourses(false))
  }, [isAdmin])
  const showConsumptionEntry = isAdmin ? hasOwnCourses : hasCourses

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
        setError(LOAD_FAILURE_MESSAGE)
        setLoading(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin])

  const loadClassData = useCallback(
    async (classId: number) => {
      setLoading(true)
      setError(null)
      try {
        // 管理员查看自己不带的班时只调用 adminKanbanApi 的只读接口——这是前端层面的约定，
        // 不是后端强制的权限边界：管理员账号同时满足教师接口的鉴权条件，理论上能绕开这里
        // 调用写接口。新增功能时不要假设「管理员等于只读」，详见 AdminKanbanController
        // 的类注释（产品诊断 #09）。管理员如果恰好是这个班自己的带班老师（ownsClass 为
        // true），则按老师一样走写接口，拿到真实可操作的数据。
        // 管理员查看别人班级时不需要实时看到谁请假——请假状态只在月度账单详情里展示，
        // 看板读接口不返回这个字段，这里用一个空占位 Promise 保持两边解构形状一致。
        const [studentList, taskList, dismissalStatus, noteList, arrivalList, mealList, leaveList] = isAdmin &&
        !ownsClass(classId)
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
    // ownsClass 闭包用到了 classes/state，和 isAdmin 一起列进依赖，classId 由调用方传入不需要列。
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isAdmin, classes],
  )

  useEffect(() => {
    if (activeClassId !== null) {
      loadClassData(activeClassId)
      setSelectedStudentId(null)
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
    const groupStudents = students.filter((s) => s.schoolClassName === schoolClassName)
    if (groupStudents.length === 0) return
    await Promise.all(
      groupStudents.flatMap((student) =>
        templateIds.map((templateId) => dailyTasksApi.addFromTemplateForStudent(student.id, templateId, date)),
      ),
    )
    await refreshTasks()
    showToast(`已分配给${schoolClassName ?? '未分班'}`)
  }

  async function handleAssignAll(templateIds: number[]) {
    if (activeClassId === null) return
    await dailyTasksApi.batchAssign(activeClassId, templateIds, date)
    await refreshTasks()
    showToast('已分配给全部学生')
  }

  async function handleCompleteAll() {
    const incompleteTasks = tasks.filter((t) => !t.completed)
    if (incompleteTasks.length === 0) return
    await Promise.all(incompleteTasks.map((t) => dailyTasksApi.setCompleted(t.id, true)))
    await refreshTasks()
    showToast('已将今日任务全部标记为完成')
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
      showToast('已打卡到校')
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
      showToast('已取消打卡')
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
      showToast('已记录用餐')
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
      showToast('已取消用餐记录')
    } catch {
      setMealByStudent((prev) => new Set(prev).add(studentId))
    }
  }

  async function handleSetLeave(studentId: number, reason: string) {
    const previous = leaveByStudent.get(studentId) ?? EMPTY_LEAVE
    setLeaveByStudent((prev) => new Map(prev).set(studentId, reason))
    try {
      await leaveApi.setLeave(studentId, date, reason)
      showToast('已登记请假')
    } catch {
      setLeaveByStudent((prev) => new Map(prev).set(studentId, previous))
    }
  }

  async function handleClearLeave(studentId: number) {
    const previous = leaveByStudent.get(studentId) ?? EMPTY_LEAVE
    setLeaveByStudent((prev) => new Map(prev).set(studentId, EMPTY_LEAVE))
    try {
      await leaveApi.clearLeave(studentId, date)
      showToast('已取消请假')
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
    if (isReadOnlyView) {
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
            {isReadOnlyView && <span className="flag-chip">只读</span>}
          </h1>
          <div className="flex gap-2">
            {showConsumptionEntry && (
              <button type="button" onClick={onOpenConsumption} className="logout-btn">
                消课
              </button>
            )}
            {isAdmin && onOpenAdmin && (
              <button type="button" onClick={onOpenAdmin} className="logout-btn">
                机构管理
              </button>
            )}
            {!isAdmin && (
              <button type="button" onClick={() => setShowHelp(true)} className="logout-btn">
                帮助
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
        {error && <p className="text-sm text-panel-danger">{error}</p>}
        {loading && <p className="text-sm text-panel-muted">加载中...</p>}

        {!loading && activeClassId !== null && (
          <>
            <div className="date-row">
              <span className="date-row__date">{date}</span>
              {!isReadOnlyView && (
                <DismissButton dismissed={dismissed} onDismiss={handleDismiss} onUndoDismiss={handleUndoDismiss} />
              )}
            </div>

            {students.length > 0 && (
              <div className="view-switch-row">
                <div className="view-switch" role="tablist" aria-label="展示方式">
                  <button
                    type="button"
                    role="tab"
                    aria-selected={viewMode === 'overview'}
                    onClick={() => setViewMode('overview')}
                  >
                    ▦ 总览
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={viewMode === 'list'}
                    onClick={() => setViewMode('list')}
                  >
                    ☰ 列表
                  </button>
                </div>
                <span className="view-switch-hint">
                  {students.length} 人 · 超过 {OVERVIEW_DEFAULT_THRESHOLD} 人默认进入总览
                </span>
              </div>
            )}

            {!isReadOnlyView && (
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
                  onAssignAll={handleAssignAll}
                />

                <CompleteAllTasksButton
                  taskCount={tasks.filter((t) => !t.completed).length}
                  studentCount={new Set(tasks.filter((t) => !t.completed).map((t) => t.studentId)).size}
                  onCompleteAll={handleCompleteAll}
                />
              </>
            )}

            {viewMode === 'overview' ? (
              <>
                <div className="overview-legend">
                  <span>
                    <i className="is-default" />
                    未到
                  </span>
                  <span>
                    <i className="is-arrived" />
                    已到
                  </span>
                  <span>
                    <i className="is-leave" />
                    请假
                  </span>
                  <span>🍚 已用餐</span>
                </div>
                <StudentOverviewGrid
                  students={students}
                  arrivalByStudent={arrivalByStudent}
                  mealByStudent={mealByStudent}
                  leaveByStudent={leaveByStudent}
                  onSelect={setSelectedStudentId}
                />
              </>
            ) : schoolClassGroups.length > 1 ? (
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
            {students.length === 0 && <p className="text-sm text-panel-muted">该班级暂无在读学生</p>}
          </>
        )}
      </main>

      {selectedStudentId !== null &&
        (() => {
          const student = students.find((s) => s.id === selectedStudentId)
          if (!student) return null
          const note = notesByStudent.get(student.id) ?? EMPTY_NOTE
          const arrivedAt = arrivalByStudent.get(student.id) ?? EMPTY_ARRIVAL
          const hasMeal = mealByStudent.has(student.id)
          return (
            <StudentQuickActionPopup
              student={student}
              tasks={tasksByStudent.get(student.id) ?? []}
              dismissed={dismissed}
              templates={templates}
              rating={note.rating}
              comment={note.comment}
              arrivedAt={arrivedAt}
              hasMeal={hasMeal}
              leaveReason={isReadOnlyView ? EMPTY_LEAVE : leaveByStudent.get(student.id) ?? EMPTY_LEAVE}
              date={date}
              readOnly={isReadOnlyView}
              onClose={() => setSelectedStudentId(null)}
              onToggleTask={isReadOnlyView ? undefined : handleToggleTask}
              onDeleteTask={isReadOnlyView ? undefined : handleDeleteTask}
              onAddFromTemplate={isReadOnlyView ? undefined : handleAddFromTemplate}
              onAddCustom={isReadOnlyView ? undefined : handleAddCustom}
              onUploadAvatar={isReadOnlyView ? undefined : handleUploadAvatar}
              onSetRating={isReadOnlyView ? undefined : handleSetRating}
              onSetComment={isReadOnlyView ? undefined : handleSetComment}
              onSetArrival={isReadOnlyView ? undefined : handleSetArrival}
              onClearArrival={isReadOnlyView ? undefined : handleClearArrival}
              onSetMeal={isReadOnlyView ? undefined : handleSetMeal}
              onClearMeal={isReadOnlyView ? undefined : handleClearMeal}
              onSetLeave={isReadOnlyView ? undefined : handleSetLeave}
              onClearLeave={isReadOnlyView ? undefined : handleClearLeave}
              onShowToast={showToast}
              statsFetchFn={isReadOnlyView ? adminKanbanApi.fetchMonthlyStats : undefined}
              shareLinkFetchFn={isReadOnlyView ? adminKanbanApi.fetchShareLink : undefined}
            />
          )
        })()}

      <Toast message={toastMessage} />
      {showHelp && <HelpModal role="teacher" onClose={() => setShowHelp(false)} />}
    </div>
  )
}
