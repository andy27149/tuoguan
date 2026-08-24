import { useMemo } from 'react'
import type { DayDetail, MonthlyStats } from '../api/monthlyStats'
import { monthCalendarCells, shiftMonthString, todayDateString } from '../kanban/date'
import { subjectColor, subjectIconMarkup } from '../kanban/subjectIcons'
import { StarRating } from './StarRating'

const WEEKDAY_LABELS = ['日', '一', '二', '三', '四', '五', '六']

function dayStatus(detail: DayDetail | undefined): 'none' | 'done' | 'incomplete' {
  if (!detail || detail.tasks.length === 0) return 'none'
  return detail.tasks.every((t) => t.completed) ? 'done' : 'incomplete'
}

function RatingTrend({ dailyRatings }: { dailyRatings: MonthlyStats['dailyRatings'] }) {
  if (dailyRatings.length === 0) {
    return <p className="stats-chart__empty">本月暂无评星数据</p>
  }

  const width = 280
  const height = 90
  const padding = 10
  const usableWidth = width - padding * 2
  const usableHeight = height - padding * 2

  const points = dailyRatings.map((d, i) => {
    const x =
      dailyRatings.length === 1
        ? padding + usableWidth / 2
        : padding + (usableWidth * i) / (dailyRatings.length - 1)
    const y = padding + usableHeight * (1 - d.rating / 5)
    return { x, y }
  })

  const polylinePoints = points.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')

  return (
    <svg className="stats-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="每日获星趋势">
      {[1, 2, 3, 4, 5].map((v) => {
        const y = padding + usableHeight * (1 - v / 5)
        return <line key={v} x1={padding} x2={width - padding} y1={y} y2={y} className="stats-chart__gridline" />
      })}
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

function CompletionPie({ completedDays, incompleteDays }: { completedDays: number; incompleteDays: number }) {
  const total = completedDays + incompleteDays
  if (total === 0) {
    return <p className="stats-chart__empty">本月暂无任务数据</p>
  }
  const r = 30
  const c = 2 * Math.PI * r
  const completedRatio = completedDays / total
  const offset = c * (1 - completedRatio)

  return (
    <div className="stats-pie-wrap">
      <svg className="stats-pie" viewBox="0 0 76 76" role="img" aria-label="每日任务完成占比">
        <circle cx="38" cy="38" r={r} className="stats-pie__track" strokeWidth="12" fill="none" />
        <circle
          cx="38"
          cy="38"
          r={r}
          className="stats-pie__fill"
          strokeWidth="12"
          fill="none"
          strokeDasharray={c.toFixed(2)}
          strokeDashoffset={offset.toFixed(2)}
          transform="rotate(-90 38 38)"
        />
        <text x="38" y="42" textAnchor="middle" className="stats-pie__label">
          {Math.round(completedRatio * 100)}%
        </text>
      </svg>
      <ul className="stats-pie__legend">
        <li className="stats-pie__legend-item">
          <span className="stats-pie__swatch stats-pie__swatch--done" />
          完成 {completedDays} 天
        </li>
        <li className="stats-pie__legend-item">
          <span className="stats-pie__swatch stats-pie__swatch--incomplete" />
          未完成 {incompleteDays} 天
        </li>
      </ul>
    </div>
  )
}

function DayDetailPanel({ date, detail }: { date: string; detail: DayDetail | undefined }) {
  const tasks = detail?.tasks ?? []
  const rating = detail?.rating ?? 0
  const comment = detail?.comment ?? ''

  return (
    <div className="stats-day-detail">
      <div className="stats-day-detail__header">
        <span className="stats-day-detail__date">{date}</span>
        <StarRating value={rating} readOnly />
      </div>
      <ul className="stats-day-detail__tasks">
        {tasks.length === 0 ? (
          <li className="stats-day-detail__empty">当天没有任务记录</li>
        ) : (
          tasks.map((t) => (
            <li key={t.id} className={`stats-day-detail__task${t.completed ? ' is-done' : ''}`}>
              <span
                className="task-subject"
                style={{ color: subjectColor(t.subject) }}
                dangerouslySetInnerHTML={{ __html: subjectIconMarkup(t.subject, t.id) }}
              />
              <span>
                [{t.subject}] {t.name}
              </span>
              <span className="stats-day-detail__mark">{t.completed ? '✓' : '—'}</span>
            </li>
          ))
        )}
      </ul>
      {comment && <p className="stats-day-detail__comment">{comment}</p>}
    </div>
  )
}

interface MonthlyStatsViewProps {
  stats: MonthlyStats
  month: string
  onMonthChange: (month: string) => void
  selectedDate: string | null
  onSelectDate: (date: string) => void
}

export function MonthlyStatsView({ stats, month, onMonthChange, selectedDate, onSelectDate }: MonthlyStatsViewProps) {
  const dayByDate = useMemo(() => {
    const map = new Map<string, DayDetail>()
    stats.days.forEach((d) => map.set(d.date, d))
    return map
  }, [stats])

  const cells = useMemo(() => monthCalendarCells(month), [month])
  const today = todayDateString()

  return (
    <>
      <div className="stats-month-nav">
        <button
          type="button"
          className="stats-month-nav__btn"
          onClick={() => onMonthChange(shiftMonthString(month, -1))}
        >
          ‹ 上月
        </button>
        <span className="stats-month-nav__label">{month}</span>
        <button
          type="button"
          className="stats-month-nav__btn"
          onClick={() => onMonthChange(shiftMonthString(month, 1))}
        >
          下月 ›
        </button>
      </div>

      <p className="stats-summary">
        完成天数 {stats.completedDays} / 未完成天数 {stats.incompleteDays}
      </p>

      <div className="stats-rating">
        <span className="stats-rating__label">本月平均评星</span>
        <StarRating value={Math.round(stats.averageRating)} readOnly />
        <span className="stats-rating__value">{stats.averageRating.toFixed(1)}</span>
      </div>

      <div className="stats-calendar">
        <div className="stats-calendar__weekdays">
          {WEEKDAY_LABELS.map((w) => (
            <span key={w}>{w}</span>
          ))}
        </div>
        <div className="stats-calendar__grid">
          {cells.map((date, i) => {
            if (!date) {
              return <span key={`empty-${i}`} className="stats-calendar__cell stats-calendar__cell--empty" />
            }
            const detail = dayByDate.get(date)
            const status = dayStatus(detail)
            const future = date > today
            return (
              <button
                key={date}
                type="button"
                className={`stats-calendar__cell${date === selectedDate ? ' is-selected' : ''}${
                  date === today ? ' is-today' : ''
                }`}
                disabled={future}
                onClick={() => onSelectDate(date)}
              >
                {Number(date.slice(-2))}
                {status !== 'none' && <span className="stats-calendar__dot" data-status={status} />}
              </button>
            )
          })}
        </div>
      </div>

      {selectedDate ? (
        <DayDetailPanel date={selectedDate} detail={dayByDate.get(selectedDate)} />
      ) : (
        <p className="stats-status">点击日期查看当天详情</p>
      )}

      <div className="stats-charts">
        <div className="stats-chart-block">
          <span className="stats-chart-block__title">每日获星趋势</span>
          <RatingTrend dailyRatings={stats.dailyRatings} />
        </div>
        <div className="stats-chart-block">
          <span className="stats-chart-block__title">每日任务完成占比</span>
          <CompletionPie completedDays={stats.completedDays} incompleteDays={stats.incompleteDays} />
        </div>
      </div>
    </>
  )
}
