import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { LowBalanceCard } from './LowBalanceCard'
import * as overviewApi from '../api/adminOverview'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'

vi.mock('../api/adminOverview')
vi.mock('../api/course')
vi.mock('../api/unit')

const ROWS: overviewApi.LowBalanceRow[] = [
  { studentId: 1, studentName: '小红', courseId: 10, courseName: '数学课', balance: 0 },
  { studentId: 2, studentName: '张伟', courseId: 11, courseName: '英语课', balance: 2 },
]

describe('LowBalanceCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(overviewApi.fetchLowBalance).mockResolvedValue(ROWS)
    vi.mocked(courseApi.fetchStudentCourseStatement).mockResolvedValue({
      balances: [],
      recharges: [],
      consumptions: [],
    })
    vi.mocked(unitApi.fetchTeachingUnits).mockResolvedValue([])
  })

  it('renders the headline count and each row with its balance badge', async () => {
    render(<LowBalanceCard />)

    const headlineLabel = await screen.findByText('名学生需要续费')
    expect(headlineLabel.closest('p')).toHaveTextContent('2 名学生需要续费')
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getByText('数学课')).toBeInTheDocument()
    expect(screen.getByText('张伟')).toBeInTheDocument()
    expect(screen.getByText('英语课')).toBeInTheDocument()
  })

  it('colors a balance of exactly 0 red and a positive low balance amber', async () => {
    render(<LowBalanceCard />)
    await screen.findByText('小红')

    expect(screen.getByText('● 余额 0 课时')).toHaveClass('bg-[#fee2e2]', 'text-[#b91c1c]')
    expect(screen.getByText('● 余额 2 课时')).toHaveClass('bg-[#fef3c7]', 'text-[#b45309]')
  })

  it('shows an empty-state message when there are no low-balance students, not a blank card', async () => {
    vi.mocked(overviewApi.fetchLowBalance).mockResolvedValue([])
    render(<LowBalanceCard />)

    expect(await screen.findByText('没有需要续费的学生')).toBeInTheDocument()
    expect(screen.queryByText('加载中...')).not.toBeInTheDocument()
  })

  it('shows an error message when the fetch fails', async () => {
    vi.mocked(overviewApi.fetchLowBalance).mockRejectedValue(new Error('network'))
    render(<LowBalanceCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })

  it('opens the recharge modal for the specific row that was clicked, not always the first row', async () => {
    render(<LowBalanceCard />)
    await screen.findByText('张伟')

    fireEvent.click(screen.getAllByRole('button', { name: '去充值' })[1])

    expect(await screen.findByText('课外账户对账单 - 张伟')).toBeInTheDocument()
    await waitFor(() => expect(courseApi.fetchStudentCourseStatement).toHaveBeenCalledWith(2))
  })

  it('closes the recharge modal and removes it from the DOM', async () => {
    render(<LowBalanceCard />)
    await screen.findByText('小红')

    fireEvent.click(screen.getAllByRole('button', { name: '去充值' })[0])
    expect(await screen.findByText('课外账户对账单 - 小红')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))

    expect(screen.queryByText('课外账户对账单 - 小红')).not.toBeInTheDocument()
  })
})
