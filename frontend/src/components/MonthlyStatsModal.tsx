import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { fetchMonthlyStats, type MonthlyStats } from '../api/monthlyStats'
import { currentMonthString, todayDateString } from '../kanban/date'
import { MonthlyStatsView } from './MonthlyStatsView'

interface MonthlyStatsModalProps {
  studentId: number
  studentName: string
  onClose: () => void
  fetchFn?: (studentId: number, month?: string) => Promise<MonthlyStats>
}

export function MonthlyStatsModal({ studentId, studentName, onClose, fetchFn = fetchMonthlyStats }: MonthlyStatsModalProps) {
  const [month, setMonth] = useState(currentMonthString())
  const [stats, setStats] = useState<MonthlyStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchFn(studentId, month)
      .then((result) => {
        if (cancelled) return
        setStats(result)
        const today = todayDateString()
        if (today.startsWith(month)) {
          setSelectedDate(today)
        } else if (result.days.length > 0) {
          setSelectedDate(result.days[result.days.length - 1].date)
        } else {
          setSelectedDate(null)
        }
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
  }, [studentId, month, fetchFn])

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

        {loading && <p className="stats-status">加载中...</p>}
        {error && <p className="stats-status stats-status--error">{error}</p>}

        {!loading && !error && stats && (
          <MonthlyStatsView
            stats={stats}
            month={month}
            onMonthChange={setMonth}
            selectedDate={selectedDate}
            onSelectDate={setSelectedDate}
          />
        )}
      </div>
    </div>,
    document.body,
  )
}
