import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminSettingsModule } from './AdminSettingsModule'
import * as institutionApi from '../api/institution'

vi.mock('../api/institution')

const INSTITUTION: institutionApi.InstitutionSettings = {
  id: 1,
  name: '阳光托管班',
  logoUrl: null,
  custodyEnabled: true,
  offCampusEnabled: true,
}

describe('AdminSettingsModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(institutionApi.fetchInstitutionSettings).mockResolvedValue(INSTITUTION)
  })

  it('loads and displays the institution name', async () => {
    render(<AdminSettingsModule />)

    expect(await screen.findByDisplayValue('阳光托管班')).toBeInTheDocument()
  })

  it('saves the institution name', async () => {
    const updated = { ...INSTITUTION, name: '快乐托管班' }
    vi.mocked(institutionApi.updateInstitutionName).mockResolvedValue(updated)
    render(<AdminSettingsModule />)
    await screen.findByDisplayValue('阳光托管班')

    fireEvent.change(screen.getByDisplayValue('阳光托管班'), { target: { value: '快乐托管班' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(institutionApi.updateInstitutionName).toHaveBeenCalledWith('快乐托管班'))
  })

  it('uploads a logo', async () => {
    const updated = { ...INSTITUTION, logoUrl: 'https://example.com/logo.png' }
    vi.mocked(institutionApi.uploadInstitutionLogo).mockResolvedValue(updated)
    render(<AdminSettingsModule />)
    await screen.findByDisplayValue('阳光托管班')

    const file = new File(['x'], 'logo.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('上传托管班 Logo'), { target: { files: [file] } })

    await waitFor(() => expect(institutionApi.uploadInstitutionLogo).toHaveBeenCalledWith(file))
    expect(await screen.findByAltText('托管班 Logo')).toHaveAttribute('src', 'https://example.com/logo.png')
  })

  it('toggles off the custody feature flag', async () => {
    const updated = { ...INSTITUTION, custodyEnabled: false }
    vi.mocked(institutionApi.updateFeatureFlags).mockResolvedValue(updated)
    const onInstitutionUpdated = vi.fn()
    render(<AdminSettingsModule onInstitutionUpdated={onInstitutionUpdated} />)
    await screen.findByDisplayValue('阳光托管班')

    fireEvent.click(screen.getByRole('checkbox', { name: '启用托管功能' }))

    await waitFor(() => expect(institutionApi.updateFeatureFlags).toHaveBeenCalledWith(false, true))
    await waitFor(() => expect(onInstitutionUpdated).toHaveBeenCalledWith(updated))
  })

  it('shows an error when disabling both feature flags is rejected', async () => {
    vi.mocked(institutionApi.updateFeatureFlags).mockRejectedValue(new Error('bad request'))
    render(<AdminSettingsModule />)
    await screen.findByDisplayValue('阳光托管班')

    fireEvent.click(screen.getByRole('checkbox', { name: '启用托管功能' }))

    expect(await screen.findByText('至少需要保留一项业务功能')).toBeInTheDocument()
  })
})
