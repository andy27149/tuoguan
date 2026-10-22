import { render, screen, fireEvent, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { KanbanPage } from './KanbanPage'
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

const logout = vi.fn()

// 用可变对象而不是固定值，方便管理员场景的测试在渲染前改成 role: 'ADMIN' 和不同的
// teacher.id（用来验证"这个班的带班老师是不是我自己"这条判断）。
const authState: { status: 'authenticated'; teacher: { id: number; phone: string; institutionId: number; role: 'ADMIN' | 'TEACHER' } } = {
  status: 'authenticated',
  teacher: { id: 1, phone: '13800000000', institutionId: 1, role: 'TEACHER' },
}

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ logout, state: authState }),
}))
vi.mock('../api/classes')
vi.mock('../api/students')
vi.mock('../api/taskTemplates')
vi.mock('../api/dailyTasks')
vi.mock('../api/dismissal')
vi.mock('../api/studentNotes')
vi.mock('../api/arrival')
vi.mock('../api/meal')
vi.mock('../api/leave')
vi.mock('../api/admin')
vi.mock('../api/adminKanban')
vi.mock('../api/course')

describe('KanbanPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authState.teacher = { id: 1, phone: '13800000000', institutionId: 1, role: 'TEACHER' }
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([{ id: 1, name: '一年级托管班' }])
    vi.mocked(templatesApi.fetchTaskTemplates).mockResolvedValue([])
    vi.mocked(studentsApi.fetchStudents).mockResolvedValue([])
    vi.mocked(dailyTasksApi.listForClass).mockResolvedValue([])
    vi.mocked(dismissalApi.fetchDismissalStatus).mockResolvedValue({ dismissed: false })
    vi.mocked(studentNotesApi.fetchStudentNotes).mockResolvedValue([])
    vi.mocked(arrivalApi.fetchArrivals).mockResolvedValue([])
    vi.mocked(mealApi.fetchMeals).mockResolvedValue([])
    vi.mocked(leaveApi.fetchLeaves).mockResolvedValue([])
  })

  it('opens and closes the teacher help modal', async () => {
    render(<KanbanPage onOpenConsumption={vi.fn()} />)
    await screen.findByText('托管班看板')

    fireEvent.click(screen.getByRole('button', { name: '帮助' }))

    const dialog = await screen.findByRole('dialog', { name: '帮助' })
    expect(within(dialog).getByText('教师帮助')).toBeInTheDocument()

    fireEvent.click(within(dialog).getByRole('button', { name: '关闭' }))
    expect(screen.queryByRole('dialog', { name: '帮助' })).not.toBeInTheDocument()
  })
})

// 机构管理员本人同时是某个托管班带班老师的场景：管理员查看自己带的班时应该和老师一样
// 拿到完整的操作入口，查看别的老师的班时仍然维持原有的只读看板，两者不应该互相影响。
describe('KanbanPage as institution admin', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authState.teacher = { id: 42, phone: '13900000042', institutionId: 1, role: 'ADMIN' }
    vi.mocked(templatesApi.fetchTaskTemplates).mockResolvedValue([])
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([])
    vi.mocked(adminKanbanApi.fetchStudents).mockResolvedValue([])
    vi.mocked(adminKanbanApi.listDailyTasksForClass).mockResolvedValue([])
    vi.mocked(adminKanbanApi.fetchDismissalStatus).mockResolvedValue({ dismissed: false })
    vi.mocked(adminKanbanApi.fetchStudentNotes).mockResolvedValue([])
    vi.mocked(adminKanbanApi.fetchArrivals).mockResolvedValue([])
    vi.mocked(adminKanbanApi.fetchMeals).mockResolvedValue([])
    vi.mocked(studentsApi.fetchStudents).mockResolvedValue([])
    vi.mocked(dailyTasksApi.listForClass).mockResolvedValue([])
    vi.mocked(dismissalApi.fetchDismissalStatus).mockResolvedValue({ dismissed: false })
    vi.mocked(studentNotesApi.fetchStudentNotes).mockResolvedValue([])
    vi.mocked(arrivalApi.fetchArrivals).mockResolvedValue([])
    vi.mocked(mealApi.fetchMeals).mockResolvedValue([])
    vi.mocked(leaveApi.fetchLeaves).mockResolvedValue([])
  })

  it('stays read-only when the admin is not the owning teacher of the class', async () => {
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue([
      { id: 1, name: '一年级托管班', teacherId: 99, teacherName: '王老师', teacherPhone: '13900000099' },
    ])

    render(<KanbanPage onOpenConsumption={vi.fn()} />)
    // "托管班看板"标题在数据加载完之前就已经渲染了，等一个只有数据加载完才会出现的
    // 元素，避免断言跑在 loadClassData 的异步调用完成之前导致的竞态。
    await screen.findByText('该班级暂无在读学生')

    expect(screen.getByText('只读')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '放学' })).not.toBeInTheDocument()
    expect(adminKanbanApi.fetchStudents).toHaveBeenCalledWith(1)
    expect(studentsApi.fetchStudents).not.toHaveBeenCalled()
  })

  it('unlocks full teacher controls when the admin is the owning teacher of the class', async () => {
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue([
      { id: 1, name: '五年级托管班', teacherId: 42, teacherName: '管理员兼老师', teacherPhone: '13900000042' },
    ])

    render(<KanbanPage onOpenConsumption={vi.fn()} />)
    await screen.findByText('托管班看板')

    expect(screen.queryByText('只读')).not.toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '放学' })).toBeInTheDocument()
    expect(studentsApi.fetchStudents).toHaveBeenCalledWith(1)
    expect(adminKanbanApi.fetchStudents).not.toHaveBeenCalled()
  })

  // 管理员本人同时是某个课外课负责教师的场景同样存在（CourseController 的 /courses
  // 系列接口也是纯按 teacherId 过滤，不看角色）：header 里"消课"入口之前写死了
  // !isAdmin，导致这种场景下管理员压根进不去课外消课页。这里锁定修复后的行为：
  // 入口按管理员是否真的名下有课外课来决定，不再是角色一刀切。
  it('does not show the 消课 entry when the admin owns no courses', async () => {
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue([
      { id: 1, name: '五年级托管班', teacherId: 42, teacherName: '管理员兼老师', teacherPhone: '13900000042' },
    ])

    render(<KanbanPage onOpenConsumption={vi.fn()} />)
    await screen.findByText('托管班看板')

    expect(screen.queryByRole('button', { name: '消课' })).not.toBeInTheDocument()
  })

  it('shows the 消课 entry when the admin personally owns a course', async () => {
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue([
      { id: 1, name: '五年级托管班', teacherId: 42, teacherName: '管理员兼老师', teacherPhone: '13900000042' },
    ])
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([
      { id: 5, name: '管理员兼任课外课', pricePerLesson: 60, lessonDurationMinutes: 60, active: true },
    ])
    const onOpenConsumption = vi.fn()

    render(<KanbanPage onOpenConsumption={onOpenConsumption} />)
    const button = await screen.findByRole('button', { name: '消课' })

    fireEvent.click(button)
    expect(onOpenConsumption).toHaveBeenCalled()
  })
})
