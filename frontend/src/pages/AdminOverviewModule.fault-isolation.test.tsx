import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { AdminOverviewModule } from './AdminOverviewModule'
import * as adminOverviewApi from '../api/adminOverview'

// 跟 AdminOverviewModule.test.tsx 不同：这里不 mock 五张卡片组件本身，而是让它们
// 真实渲染，只 mock 它们各自依赖的 adminOverview.ts，验证 spec 里"互不阻塞"这条
// 设计保证——某一张卡片的接口请求失败，不能影响其它四张卡片正常显示数据。
vi.mock('../api/adminOverview')

describe('AdminOverviewModule fault isolation', () => {
  it('keeps the other four cards rendering normally when one card fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchLowBalance).mockRejectedValue(new Error('network'))
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue({ count: 0, totalAmount: 0, rows: [] })
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 0,
      custodyCount: 0,
      offCampusOnlyCount: 0,
      byTeacher: [],
    })
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 0,
      mealCount: 0,
      leaveCount: 0,
      totalCustodyStudentCount: 0,
    })
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue({
      tuitionMonth: '2026-09',
      tuitionBilled: 0,
      tuitionCollected: 0,
      consumptionMonth: '2026-10',
      offCampusConsumptionCount: 0,
    })

    render(<AdminOverviewModule onOpenBilling={vi.fn()} />)

    // 只有课时不足预警卡片进入错误态——且只有这一张。
    const errorMessages = await screen.findAllByText('加载失败，请刷新重试')
    expect(errorMessages).toHaveLength(1)

    // 其余四张卡片各自正常渲染出自己的数据，没有被这一张卡片的失败拖垮。
    expect(screen.getByText('暂无欠费账单')).toBeInTheDocument()
    expect(screen.getByText('名在读学生')).toBeInTheDocument()
    expect(screen.getAllByText('0 / 0')).toHaveLength(3)
    expect(screen.getByRole('heading', { name: '收入快照' })).toBeInTheDocument()
  })
})
