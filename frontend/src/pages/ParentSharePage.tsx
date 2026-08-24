import { useEffect, useState } from 'react'
import { fetchPublicShare, type PublicShare } from '../api/publicShare'
import { currentMonthString, todayDateString } from '../kanban/date'
import { MonthlyStatsView } from '../components/MonthlyStatsView'

interface ParentSharePageProps {
  token: string
}

export function ParentSharePage({ token }: ParentSharePageProps) {
  const [month, setMonth] = useState(currentMonthString())
  const [share, setShare] = useState<PublicShare | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchPublicShare(token, month)
      .then((result) => {
        if (cancelled) return
        setShare(result)
        const today = todayDateString()
        if (today.startsWith(month)) {
          setSelectedDate(today)
        } else if (result.stats.days.length > 0) {
          setSelectedDate(result.stats.days[result.stats.days.length - 1].date)
        } else {
          setSelectedDate(null)
        }
      })
      .catch(() => {
        if (!cancelled) setError('链接无效或已失效，请联系老师获取新的链接')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [token, month])

  return (
    <div className="parent-share-page">
      <div className="parent-share-page__card">
        {loading && <p className="stats-status">加载中...</p>}
        {error && <p className="stats-status stats-status--error">{error}</p>}

        {!loading && !error && share && (
          <>
            <div className="parent-share-page__header">
              <div className="parent-share-page__avatar">
                {share.avatarUrl ? (
                  <img src={share.avatarUrl} alt={share.studentName} />
                ) : (
                  share.studentName.slice(0, 1)
                )}
              </div>
              <div>
                <p className="parent-share-page__name">{share.studentName}</p>
                <p className="parent-share-page__class">{share.schoolClassName}</p>
              </div>
            </div>

            <MonthlyStatsView
              stats={share.stats}
              month={month}
              onMonthChange={setMonth}
              selectedDate={selectedDate}
              onSelectDate={setSelectedDate}
            />
          </>
        )}
      </div>
    </div>
  )
}
