import { useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import { todayDateString } from '../kanban/date'

export function AdminTaskStatsModule() {
  const [date, setDate] = useState(todayDateString())
  const [dashboard, setDashboard] = useState<adminApi.AdminDashboard | null>(null)
  const [loadingDashboard, setLoadingDashboard] = useState(true)
  const [dashboardLoadError, setDashboardLoadError] = useState<string | null>(null)

  function loadDashboard() {
    setLoadingDashboard(true)
    setDashboardLoadError(null)
    adminApi
      .fetchAdminDashboard(date)
      .then(setDashboard)
      .catch(() => setDashboardLoadError('加载看板数据失败，请刷新重试'))
      .finally(() => setLoadingDashboard(false))
  }

  useEffect(() => {
    loadDashboard()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date])

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <div className="flex items-center justify-between gap-2">
          <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">班级任务完成情况</h2>
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
          />
        </div>
        {dashboardLoadError && <p className="mt-2 text-sm text-[#b7591f]">{dashboardLoadError}</p>}
        {loadingDashboard && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loadingDashboard && dashboard && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">班级</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">学生数量</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">签到数量</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">任务完成数</th>
              </tr>
            </thead>
            <tbody>
              {dashboard.classes.map((c) => (
                <tr key={c.classRoomId} className="border-b border-[#ece7de] hover:bg-[#faf7ff]">
                  <td className="px-4 py-3 font-medium text-[#241f3d]">{c.className}</td>
                  <td className="px-4 py-3 text-[#241f3d]">{c.studentCount}</td>
                  <td className="px-4 py-3 text-[#241f3d]">{c.checkinCount}</td>
                  <td className="px-4 py-3 text-[#241f3d]">{c.completedStudentCount}</td>
                </tr>
              ))}
              {dashboard.classes.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无班级数据
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
