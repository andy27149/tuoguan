import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { AdminOverviewModule } from './AdminOverviewModule'

vi.mock('../components/LowBalanceCard', () => ({
  LowBalanceCard: () => <div>LowBalanceCardStub</div>,
}))
vi.mock('../components/UnpaidBillsCard', () => ({
  UnpaidBillsCard: ({ onOpenBilling }: { onOpenBilling: () => void }) => (
    <div>
      UnpaidBillsCardStub
      <button type="button" onClick={onOpenBilling}>
        trigger
      </button>
    </div>
  ),
}))
vi.mock('../components/EnrollmentCard', () => ({
  EnrollmentCard: () => <div>EnrollmentCardStub</div>,
}))
vi.mock('../components/TodaySnapshotCard', () => ({
  TodaySnapshotCard: () => <div>TodaySnapshotCardStub</div>,
}))
vi.mock('../components/RevenueCard', () => ({
  RevenueCard: () => <div>RevenueCardStub</div>,
}))

describe('AdminOverviewModule', () => {
  it('renders all five cards', () => {
    render(<AdminOverviewModule onOpenBilling={vi.fn()} />)

    expect(screen.getByText('LowBalanceCardStub')).toBeInTheDocument()
    expect(screen.getByText('UnpaidBillsCardStub')).toBeInTheDocument()
    expect(screen.getByText('EnrollmentCardStub')).toBeInTheDocument()
    expect(screen.getByText('TodaySnapshotCardStub')).toBeInTheDocument()
    expect(screen.getByText('RevenueCardStub')).toBeInTheDocument()
  })

  it('passes onOpenBilling through to UnpaidBillsCard', () => {
    const onOpenBilling = vi.fn()
    render(<AdminOverviewModule onOpenBilling={onOpenBilling} />)

    fireEvent.click(screen.getByRole('button', { name: 'trigger' }))

    expect(onOpenBilling).toHaveBeenCalledTimes(1)
  })
})
