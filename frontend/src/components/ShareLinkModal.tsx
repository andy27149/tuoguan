import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import QRCode from 'qrcode'
import { fetchShareLink } from '../api/students'

interface ShareLinkModalProps {
  studentId: number
  studentName: string
  onClose: () => void
  onShowToast: (message: string) => void
}

export function ShareLinkModal({ studentId, studentName, onClose, onShowToast }: ShareLinkModalProps) {
  const [url, setUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchShareLink(studentId)
      .then(({ token }) => {
        if (cancelled) return
        setUrl(`${window.location.origin}${import.meta.env.BASE_URL}share/${token}`)
      })
      .catch(() => {
        if (!cancelled) setError('加载失败，请重试')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [studentId])

  useEffect(() => {
    if (!url || !canvasRef.current) return
    QRCode.toCanvas(canvasRef.current, url, { width: 180 }).catch(() => {
      onShowToast('二维码生成失败')
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url])

  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
  }, [onClose])

  const handleCopyLink = () => {
    if (!url) return
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(
        () => onShowToast('链接已复制，发给家长吧'),
        () => onShowToast('复制失败，请手动选择文字复制'),
      )
    } else {
      onShowToast('复制失败，请手动选择文字复制')
    }
  }

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label="家长链接">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={onClose}>
          ×
        </button>

        <div className="stats-modal__header">
          <span className="stats-modal__title">{studentName}的家长链接</span>
        </div>

        {loading && <p className="stats-status">加载中...</p>}
        {error && <p className="stats-status stats-status--error">{error}</p>}

        {!loading && !error && url && (
          <>
            <p className="share-hint">家长扫码或打开链接即可查看孩子当天及历史学习情况</p>

            <canvas ref={canvasRef} className="share-link-qr" />

            <input className="share-link-input" type="text" readOnly value={url} onFocus={(e) => e.target.select()} />

            <div className="share-modal__actions">
              <button type="button" className="btn-secondary" onClick={handleCopyLink}>
                复制链接
              </button>
              <button type="button" className="btn-small" onClick={onClose}>
                完成
              </button>
            </div>
          </>
        )}
      </div>
    </div>,
    document.body,
  )
}
