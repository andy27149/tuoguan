import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ShareLinkModal } from './ShareLinkModal'
import * as studentsApi from '../api/students'
import QRCode from 'qrcode'

vi.mock('../api/students')
vi.mock('qrcode', () => ({
  default: { toCanvas: vi.fn() },
}))

describe('ShareLinkModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(studentsApi.fetchShareLink).mockResolvedValue({ token: 'abc123' })
    vi.mocked(QRCode.toCanvas).mockResolvedValue(undefined as never)
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  it('fetches the share token and shows the full shareable link', async () => {
    render(<ShareLinkModal studentId={10} studentName="小明" onClose={vi.fn()} onShowToast={vi.fn()} />)

    expect(await screen.findByDisplayValue(`${window.location.origin}/share/abc123`)).toBeInTheDocument()
    expect(studentsApi.fetchShareLink).toHaveBeenCalledWith(10)
  })

  it('copies the link and shows a toast on click', async () => {
    const onShowToast = vi.fn()
    render(<ShareLinkModal studentId={10} studentName="小明" onClose={vi.fn()} onShowToast={onShowToast} />)

    await screen.findByDisplayValue(`${window.location.origin}/share/abc123`)
    fireEvent.click(screen.getByRole('button', { name: '复制链接' }))

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(`${window.location.origin}/share/abc123`)
  })

  it('closes when the close button is clicked', async () => {
    const onClose = vi.fn()
    render(<ShareLinkModal studentId={10} studentName="小明" onClose={onClose} onShowToast={vi.fn()} />)

    await screen.findByDisplayValue(`${window.location.origin}/share/abc123`)
    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onClose).toHaveBeenCalled()
  })

  it('shows an error message when the share link fails to load', async () => {
    vi.mocked(studentsApi.fetchShareLink).mockRejectedValue(new Error('boom'))
    render(<ShareLinkModal studentId={10} studentName="小明" onClose={vi.fn()} onShowToast={vi.fn()} />)

    expect(await screen.findByText('加载失败，请重试')).toBeInTheDocument()
  })
})
