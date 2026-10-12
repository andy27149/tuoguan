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

const logout = vi.fn()

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    logout,
    state: { status: 'authenticated', teacher: { id: 1, phone: '13800000000', institutionId: 1, role: 'TEACHER' } },
  }),
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

describe('KanbanPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
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
