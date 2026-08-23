import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { fetchMonthlyStats, type MonthlyStats } from '../api/monthlyStats'
import { currentMonthString, shiftMonthString } from '../kanban/date'
import { StarRating } from './StarRating'

interface MonthlyStatsModalProps {
  studentId: number
  studentName: string
  onClose: () => void
}

function Sparkline({ dailyRates }: { dailyRates: MonthlyStats['dailyRates'] }) {
  if (dailyRates.length === 0) {
    return <p className="stats-chart__empty">本月暂无数据</p>
  }

  const width = 280
  const height = 80
  const padding = 6
  const usableWidth = width - padding * 2
  const usableHeight = height - padding * 2

  const points = dailyRates.map((d, i) => {
    const x =
      dailyRates.length === 1
        ? padding + usableWidth / 2
        : padding + (usableWidth * i) / (dailyRates.length - 1)
    const y = padding + usableHeight * (1 - d.rate)
    return { x, y }
  })

  const polylinePoints = points.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')

  return (
    <svg
      className="stats-chart"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label="本月完成率趋势"
    >
      <polyline
        points={polylinePoints}
        fill="none"
        stroke="var(--accent-2, #9b6bff)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {points.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r="2.5" fill="var(--accent-2, #9b6bff)" />
      ))}
    </svg>
  )
}

export function MonthlyStatsModal({ studentId, studentName, onClose }: MonthlyStatsModalProps) {
  const [month, setMonth] = useState(currentMonthString())
  const [stats, setStats] = useState<MonthlyStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchMonthlyStats(studentId, month)
      .then((result) => {
        if (!cancelled) setStats(result)
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
  }, [studentId, month])

  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
  }, [onClose])

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="share-modal stats-modal" role="dialog" aria-modal="true" aria-label="月度统计">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={onClose}>
          ×
        </button>

        <div className="stats-modal__header">
          <span className="stats-modal__title">{studentName}的月度统计</span>
        </div>

        <div className="stats-month-nav">
          <button
            type="button"
            className="stats-month-nav__btn"
            onClick={() => setMonth((m) => shiftMonthString(m, -1))}
          >
            ‹ 上月
          </button>
          <span className="stats-month-nav__label">{month}</span>
          <button
            type="button"
            className="stats-month-nav__btn"
            onClick={() => setMonth((m) => shiftMonthString(m, 1))}
          >
            下月 ›
          </button>
        </div>

        {loading && <p className="stats-status">加载中...</p>}
        {error && <p className="stats-status stats-status--error">{error}</p>}

        {!loading && !error && stats && (
          <>
            <p className="stats-summary">
              完成天数 {stats.completedDays} / 未完成天数 {stats.incompleteDays}
            </p>

            <div className="stats-rating">
              <span className="stats-rating__label">本月平均评星</span>
              <StarRating value={Math.round(stats.averageRating)} readOnly />
              <span className="stats-rating__value">{stats.averageRating.toFixed(1)}</span>
            </div>

            <Sparkline dailyRates={stats.dailyRates} />
          </>
        )}
      </div>
    </div>,
    document.body,
  )
}
