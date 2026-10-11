import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { EditInstitutionContactModal } from './EditInstitutionContactModal'
import * as platformApi from '../api/platform'

vi.mock('../api/platform')

describe('EditInstitutionContactModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(platformApi.updateInstitutionContact).mockResolvedValue({} as platformApi.PlatformInstitution)
  })

  it('pre-fills the form with the current contact info', () => {
    render(
      <EditInstitutionContactModal
        institutionId={1}
        institutionName="机构A"
        initialContactName="张校长"
        initialContactPhone="13900000001"
        initialAddress="示例路1号"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('联系人姓名')).toHaveValue('张校长')
    expect(screen.getByLabelText('联系电话')).toHaveValue('13900000001')
    expect(screen.getByLabelText('地址')).toHaveValue('示例路1号')
  })

  it('shows empty inputs when there is no existing contact info', () => {
    render(
      <EditInstitutionContactModal
        institutionId={1}
        institutionName="机构A"
        initialContactName={null}
        initialContactPhone={null}
        initialAddress={null}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('联系人姓名')).toHaveValue('')
    expect(screen.getByLabelText('联系电话')).toHaveValue('')
    expect(screen.getByLabelText('地址')).toHaveValue('')
  })

  it('submits the edited values, then saves and closes', async () => {
    const onClose = vi.fn()
    const onSaved = vi.fn()
    render(
      <EditInstitutionContactModal
        institutionId={1}
        institutionName="机构A"
        initialContactName="张校长"
        initialContactPhone="13900000001"
        initialAddress="示例路1号"
        onClose={onClose}
        onSaved={onSaved}
      />,
    )

    fireEvent.change(screen.getByLabelText('联系人姓名'), { target: { value: '李校长' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(platformApi.updateInstitutionContact).toHaveBeenCalledWith(1, '李校长', '13900000001', '示例路1号'),
    )
    expect(onSaved).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('shows an error and does not close when the submission fails', async () => {
    vi.mocked(platformApi.updateInstitutionContact).mockRejectedValue(new Error('boom'))
    const onClose = vi.fn()
    render(
      <EditInstitutionContactModal
        institutionId={1}
        institutionName="机构A"
        initialContactName="张校长"
        initialContactPhone="13900000001"
        initialAddress="示例路1号"
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('保存失败，请重试')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('disables 保存 when contact phone is cleared, so it cannot be blanked out', () => {
    render(
      <EditInstitutionContactModal
        institutionId={1}
        institutionName="机构A"
        initialContactName="张校长"
        initialContactPhone="13900000001"
        initialAddress="示例路1号"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )

    fireEvent.change(screen.getByLabelText('联系电话'), { target: { value: '' } })

    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    expect(platformApi.updateInstitutionContact).not.toHaveBeenCalled()
  })

  it('calls onClose when clicking 取消', () => {
    const onClose = vi.fn()
    render(
      <EditInstitutionContactModal
        institutionId={1}
        institutionName="机构A"
        initialContactName={null}
        initialContactPhone={null}
        initialAddress={null}
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onClose).toHaveBeenCalled()
  })
})
