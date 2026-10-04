import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import App from './App'
import * as authApi from './api/auth'
import * as classesApi from './api/classes'
import * as courseApi from './api/course'
import * as taskTemplatesApi from './api/taskTemplates'
import * as studentsApi from './api/students'
import * as dailyTasksApi from './api/dailyTasks'
import * as dismissalApi from './api/dismissal'
import * as studentNotesApi from './api/studentNotes'
import * as arrivalApi from './api/arrival'
import * as adminApi from './api/admin'
import * as institutionApi from './api/institution'
import { setToken } from './api/client'

vi.mock('./api/auth', async () => {
  const actual = await vi.importActual<typeof import('./api/auth')>('./api/auth')
  return { ...actual, fetchMe: vi.fn() }
})
vi.mock('./api/classes')
vi.mock('./api/course')
vi.mock('./api/taskTemplates')
vi.mock('./api/students')
vi.mock('./api/dailyTasks')
vi.mock('./api/dismissal')
vi.mock('./api/studentNotes')
vi.mock('./api/arrival')
vi.mock('./api/admin')
vi.mock('./api/institution')

const TEACHER = { id: 2, phone: '13700000002', institutionId: 1, role: 'TEACHER' as const }
const ADMIN = { id: 3, phone: '13700000003', institutionId: 1, role: 'ADMIN' as const }

function loginAsTeacher() {
  setToken('teacher-token')
  vi.mocked(authApi.fetchMe).mockResolvedValue(TEACHER)
}

function loginAsAdmin() {
  setToken('admin-token')
  vi.mocked(authApi.fetchMe).mockResolvedValue(ADMIN)
}

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
    vi.mocked(taskTemplatesApi.fetchTaskTemplates).mockResolvedValue([])
    vi.mocked(studentsApi.fetchStudents).mockResolvedValue([])
    vi.mocked(dailyTasksApi.listForClass).mockResolvedValue([])
    vi.mocked(dismissalApi.fetchDismissalStatus).mockResolvedValue({ dismissed: false })
    vi.mocked(studentNotesApi.fetchStudentNotes).mockResolvedValue([])
    vi.mocked(arrivalApi.fetchArrivals).mockResolvedValue([])
  })

  it('shows the login form when no session token is stored', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: '托管班看板登录' })).toBeInTheDocument()
  })

  it('routes a platform admin straight to the platform management page', async () => {
    setToken('platform-token')
    vi.mocked(authApi.fetchMe).mockResolvedValue({
      id: 1,
      phone: '13700000001',
      institutionId: null,
      role: 'PLATFORM_ADMIN',
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: '平台管理' })).toBeInTheDocument()
  })

  it('lands an institution admin on the admin dashboard by default, not the read-only kanban', async () => {
    loginAsAdmin()
    vi.mocked(institutionApi.fetchInstitutionSettings).mockResolvedValue({
      id: 1,
      name: '阳光托管班',
      logoUrl: null,
      custodyEnabled: true,
      offCampusEnabled: true,
    })
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue([])
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue([])

    render(<App />)

    expect(await screen.findByText('教师列表')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '学生管理' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '查看托管看板' })).toBeInTheDocument()
  })

  it('shows 学生管理 but hides 消课 for a teacher with a class but no courses', async () => {
    loginAsTeacher()
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([{ id: 1, name: '一班' }])
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([])

    render(<App />)

    expect(await screen.findByRole('button', { name: '学生管理' })).toBeInTheDocument()
    // hasClasses/hasCourses both default to true before the resource-check effect resolves,
    // so 消课 can be present on the very first paint — wait for the effect to settle rather
    // than asserting synchronously right after an unrelated findByRole.
    await waitFor(() => expect(screen.queryByRole('button', { name: '消课' })).not.toBeInTheDocument())
  })

  it('hides 学生管理 and shows a 前往消课 prompt for a teacher with courses but no class', async () => {
    loginAsTeacher()
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([])
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([
      { id: 1, name: '书法课', lessonDurationMinutes: 60, pricePerLesson: 50, active: true },
    ])

    render(<App />)

    expect(await screen.findByText('暂无托管班级，请联系管理员创建')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '前往消课' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '学生管理' })).not.toBeInTheDocument()
  })

  it('shows the empty-teacher state for a teacher with neither a class nor courses', async () => {
    loginAsTeacher()
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([])
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([])

    render(<App />)

    expect(await screen.findByText('您还未被分配托管班或课外课，请联系管理员')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('暂无托管班级，请联系管理员创建')).not.toBeInTheDocument())
  })
})
