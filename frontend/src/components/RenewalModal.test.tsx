import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RenewalModal } from './RenewalModal'
import * as platformApi from '../api/platform'

vi.mock('../api/platform')

const HISTORY: platformApi.RenewalRecord[] = [
  {
    id: 1,
    plan: 'TRIAL',
    amount: null,
    validUntil: '2026-11-01',
    note: null,
    recordedByTeacherName: '平台',
    createdAt: '2026-10-01T00:00:00Z',
  },
]

describe('RenewalModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(platformApi.fetchRenewalHistory).mockResolvedValue(HISTORY)
    vi.mocked(platformApi.createRenewal).mockResolvedValue({} as platformApi.PlatformInstitution)
  })

  it('loads and shows the renewal history', async () => {
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )

    expect(await screen.findByText('试用')).toBeInTheDocument()
    expect(screen.getByText('2026-11-01')).toBeInTheDocument()
    expect(screen.getByText('平台')).toBeInTheDocument()
  })

  it('defaults to MONTHLY plan with a 299 amount and a suggested valid-until one month after the current one', async () => {
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )
    await screen.findByText('平台')

    expect(screen.getByLabelText('金额')).toHaveValue(299)
    expect(screen.getByLabelText('新的到期日')).toHaveValue('2026-12-01')
  })

  it('switching to TRIAL clears and disables the amount field', async () => {
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )
    await screen.findByText('平台')

    fireEvent.change(screen.getByLabelText('套餐'), { target: { value: 'TRIAL' } })

    expect(screen.getByLabelText('金额')).toBeDisabled()
    expect(screen.getByLabelText('新的到期日')).toHaveValue('2026-12-01')
  })

  it('switching plan updates the suggested amount and valid-until', async () => {
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )
    await screen.findByText('平台')

    fireEvent.change(screen.getByLabelText('套餐'), { target: { value: 'YEARLY' } })

    expect(screen.getByLabelText('金额')).toHaveValue(2899)
    expect(screen.getByLabelText('新的到期日')).toHaveValue('2027-11-01')
  })

  it('submits the renewal with the chosen plan, amount, date and note, then saves and closes', async () => {
    const onClose = vi.fn()
    const onSaved = vi.fn()
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={onClose}
        onSaved={onSaved}
      />,
    )
    await screen.findByText('平台')

    fireEvent.change(screen.getByLabelText('备注（选填）'), { target: { value: '老客户优惠' } })
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    await waitFor(() =>
      expect(platformApi.createRenewal).toHaveBeenCalledWith(1, 'MONTHLY', 299, '2026-12-01', '老客户优惠'),
    )
    expect(onSaved).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('submits a null amount when the amount field is cleared', async () => {
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )
    await screen.findByText('平台')

    fireEvent.change(screen.getByLabelText('金额'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    await waitFor(() =>
      expect(platformApi.createRenewal).toHaveBeenCalledWith(1, 'MONTHLY', null, '2026-12-01', ''),
    )
  })

  it('shows an error and does not close when the submission fails', async () => {
    vi.mocked(platformApi.createRenewal).mockRejectedValue(new Error('boom'))
    const onClose = vi.fn()
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    )
    await screen.findByText('平台')

    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(await screen.findByText('续费记录失败，请重试')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('calls onClose when clicking 取消', async () => {
    const onClose = vi.fn()
    render(
      <RenewalModal
        institutionId={1}
        institutionName="机构A"
        currentValidUntil="2026-11-01"
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    )
    await screen.findByText('平台')

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onClose).toHaveBeenCalled()
  })
})
