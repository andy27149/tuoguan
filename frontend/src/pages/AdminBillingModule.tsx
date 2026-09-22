import { useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import * as billingApi from '../api/billing'
import { currentMonthString } from '../kanban/date'
import { FeeManagementModal } from '../components/FeeManagementModal'
import { BillDetailModal } from '../components/BillDetailModal'

export function AdminBillingModule() {
  const [classes, setClasses] = useState<adminApi.AdminClassRoom[]>([])
  const [loadingClasses, setLoadingClasses] = useState(true)

  const [month, setMonth] = useState(currentMonthString())
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null)
  const [studentName, setStudentName] = useState('')

  const [rows, setRows] = useState<billingApi.BillOverviewRow[]>([])
  const [loadingRows, setLoadingRows] = useState(true)
  const [rowsError, setRowsError] = useState<string | null>(null)

  const [managingStudent, setManagingStudent] = useState<{
    studentId: number
    studentName: string
    classRoomId: number
    className: string
    month: string
  } | null>(null)

  const [viewingBill, setViewingBill] = useState<{
    bill: billingApi.MonthlyBill
    studentName: string
    className: string
  } | null>(null)
  const [billLoadError, setBillLoadError] = useState<string | null>(null)

  useEffect(() => {
    adminApi
      .fetchAdminClasses()
      .then(setClasses)
      .finally(() => setLoadingClasses(false))
  }, [])

  function loadRows() {
    setLoadingRows(true)
    setRowsError(null)
    billingApi
      .fetchBillOverview(month || undefined, selectedClassId ?? undefined, studentName.trim() || undefined)
      .then(setRows)
      .catch(() => setRowsError('加载账单列表失败，请刷新重试'))
      .finally(() => setLoadingRows(false))
  }

  useEffect(() => {
    loadRows()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [month, selectedClassId, studentName])

  async function handleTogglePaid(row: billingApi.BillOverviewRow) {
    if (row.billId === null) return
    const nextPaid = !row.isPaid
    setRows((prev) => prev.map((r) => (r.billId === row.billId ? { ...r, isPaid: nextPaid } : r)))
    try {
      await billingApi.setBillPaid(row.billId, nextPaid)
    } catch {
      setRows((prev) => prev.map((r) => (r.billId === row.billId ? { ...r, isPaid: row.isPaid } : r)))
    }
  }

  async function handleViewBill(row: billingApi.BillOverviewRow) {
    if (row.billId === null) return
    setBillLoadError(null)
    try {
      const bill = await billingApi.fetchBillDetail(row.billId)
      setViewingBill({ bill, studentName: row.studentName, className: row.className })
    } catch {
      setBillLoadError('加载账单详情失败，请重试')
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-sm text-[#5d5480]">
            月份
            <input
              type="month"
              value={month}
              max={currentMonthString()}
              onChange={(e) => setMonth(e.target.value)}
              className="ml-2 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
          <span className="text-xs text-[#7c7391]">留空查看全部月份</span>
          <label className="text-sm text-[#5d5480]">
            班级
            <select
              value={selectedClassId ?? ''}
              onChange={(e) => setSelectedClassId(e.target.value === '' ? null : Number(e.target.value))}
              disabled={loadingClasses}
              className="ml-2 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            >
              <option value="">全部班级</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm text-[#5d5480]">
            学生姓名
            <input
              placeholder="搜索学生姓名"
              value={studentName}
              onChange={(e) => setStudentName(e.target.value)}
              className="ml-2 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
            />
          </label>
        </div>
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">学生账单（{rows.length}）</h2>
        {rowsError && <p className="mt-2 text-sm text-[#b7591f]">{rowsError}</p>}
        {billLoadError && <p className="mt-2 text-sm text-[#b7591f]">{billLoadError}</p>}
        {loadingRows && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loadingRows && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">学生姓名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管班级</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管教师</th>
                {!month && <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">月份</th>}
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">是否已缴费</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">账单金额</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">费用管理</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={`${row.studentId}-${row.yearMonth}`} className="border-b border-[#ece7de] hover:bg-[#faf7ff]">
                  <td className="px-4 py-3 font-medium text-[#241f3d]">{row.studentName}</td>
                  <td className="px-4 py-3 text-[#241f3d]">{row.className}</td>
                  <td className="px-4 py-3 text-[#241f3d]">{row.teacherName}</td>
                  {!month && <td className="px-4 py-3 text-[#241f3d]">{row.yearMonth}</td>}
                  <td className="px-4 py-3">
                    {row.billId === null ? (
                      <span className="text-xs text-[#7c7391]">未生成</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleTogglePaid(row)}
                        className={
                          row.isPaid
                            ? 'rounded-full bg-[#e9f9ef] px-2 py-0.5 text-xs text-[#1f9d55]'
                            : 'rounded-full bg-[#fdf1e6] px-2 py-0.5 text-xs text-[#b7591f]'
                        }
                      >
                        {row.isPaid ? '已缴费' : '未缴费'}
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3 font-['Sora'] font-semibold text-[#241f3d]">
                    {row.totalAmount === null ? (
                      '-'
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleViewBill(row)}
                        className="underline decoration-dotted underline-offset-2 hover:text-[#6d5bd0]"
                      >
                        ¥{row.totalAmount.toFixed(2)}
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      onClick={() =>
                        setManagingStudent({
                          studentId: row.studentId,
                          studentName: row.studentName,
                          classRoomId: row.classRoomId,
                          className: row.className,
                          month: row.yearMonth,
                        })
                      }
                      className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                    >
                      费用管理
                    </button>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={month ? 6 : 7} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无学生
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {managingStudent && (
        <FeeManagementModal
          studentId={managingStudent.studentId}
          studentName={managingStudent.studentName}
          classRoomId={managingStudent.classRoomId}
          className={managingStudent.className}
          month={managingStudent.month}
          onClose={() => setManagingStudent(null)}
          onSaved={loadRows}
        />
      )}

      {viewingBill && (
        <BillDetailModal
          bill={viewingBill.bill}
          studentName={viewingBill.studentName}
          className={viewingBill.className}
          onClose={() => setViewingBill(null)}
        />
      )}
    </div>
  )
}
