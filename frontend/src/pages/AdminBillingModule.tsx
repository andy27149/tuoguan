import { useEffect, useMemo, useState } from 'react'
import * as adminApi from '../api/admin'
import * as billingApi from '../api/billing'
import { PAYMENT_METHOD_LABEL, type PaymentMethod } from '../api/billing'
import { currentMonthString } from '../kanban/date'
import { FeeManagementModal } from '../components/FeeManagementModal'
import { BillDetailModal } from '../components/BillDetailModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { RollCallResultModal } from '../components/RollCallResultModal'
import { Toast } from '../components/Toast'
import { useToast } from '../hooks/useToast'

const OFF_CAMPUS_FILTER_VALUE = 'off-campus'

type SortOption = 'default' | 'overdue' | 'amount'

const SORT_LABEL: Record<SortOption, string> = {
  default: '默认排序',
  overdue: '逾期天数优先',
  amount: '金额优先',
}

const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'WECHAT', 'ALIPAY', 'BANK_TRANSFER']

// 弹窗默认的确认按钮是玻璃拟态渐变色（给家长分享/看板那套页面用的），管理后台
// 统一用实心紫色圆角按钮，这里覆盖成跟"一键生成本月账单"等按钮一致的样式。
const PANEL_CONFIRM_BUTTON_CLASSNAME =
  'rounded-full bg-panel-primary px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50'

export function AdminBillingModule() {
  const [classes, setClasses] = useState<adminApi.AdminClassRoom[]>([])
  const [loadingClasses, setLoadingClasses] = useState(true)

  const [month, setMonth] = useState(currentMonthString())
  // 班级筛选下拉的值：''（全部班级）、OFF_CAMPUS_FILTER_VALUE（只看纯课外课学生）、
  // 或某个班级 id 的字符串形式。用字符串统一承载三种互斥状态，避免再额外加一个布尔开关。
  const [classFilter, setClassFilter] = useState<string>('')
  const [studentName, setStudentName] = useState('')
  const [sortBy, setSortBy] = useState<SortOption>('default')

  const selectedClassId = classFilter && classFilter !== OFF_CAMPUS_FILTER_VALUE ? Number(classFilter) : undefined
  const offCampusOnly = classFilter === OFF_CAMPUS_FILTER_VALUE

  const [rows, setRows] = useState<billingApi.BillOverviewRow[]>([])
  const [loadingRows, setLoadingRows] = useState(true)
  const [rowsError, setRowsError] = useState<string | null>(null)

  const sortedRows = useMemo(() => {
    if (sortBy === 'default') return rows
    const withBills = rows.filter((r) => r.billId !== null)
    const withoutBills = rows.filter((r) => r.billId === null)
    const sorted =
      sortBy === 'overdue'
        ? [...withBills].sort((a, b) => b.overdueDays - a.overdueDays)
        : [...withBills].sort((a, b) => (b.totalAmount ?? 0) - (a.totalAmount ?? 0))
    return [...sorted, ...withoutBills]
  }, [rows, sortBy])

  const [managingStudent, setManagingStudent] = useState<{
    studentId: number
    studentName: string
    classRoomId: number | null
    className: string
    month: string
  } | null>(null)

  const [viewingBill, setViewingBill] = useState<{
    bill: billingApi.MonthlyBill
    studentName: string
    className: string
  } | null>(null)
  const [billLoadError, setBillLoadError] = useState<string | null>(null)
  const [confirmingPaidToggle, setConfirmingPaidToggle] = useState<billingApi.BillOverviewRow | null>(null)
  const [singlePaymentMethod, setSinglePaymentMethod] = useState<PaymentMethod | ''>('')
  const [confirmingBulkGenerate, setConfirmingBulkGenerate] = useState(false)
  const [bulkGenerating, setBulkGenerating] = useState(false)
  const [bulkGenerateResult, setBulkGenerateResult] = useState<billingApi.BulkBillGenerationResult | null>(null)

  const [selectedBillIds, setSelectedBillIds] = useState<Set<number>>(new Set())
  const [confirmingBatchPaid, setConfirmingBatchPaid] = useState(false)
  const [batchPaymentMethod, setBatchPaymentMethod] = useState<PaymentMethod | ''>('')
  const [batchSubmitting, setBatchSubmitting] = useState(false)
  const [batchResult, setBatchResult] = useState<billingApi.BatchSetPaidResult | null>(null)

  const { toastMessage, showToast } = useToast()

  useEffect(() => {
    adminApi
      .fetchAdminClasses()
      .then(setClasses)
      .finally(() => setLoadingClasses(false))
  }, [])

  function loadRows() {
    setLoadingRows(true)
    setRowsError(null)
    setSelectedBillIds(new Set())
    billingApi
      .fetchBillOverview(month || undefined, selectedClassId, studentName.trim() || undefined, offCampusOnly)
      .then(setRows)
      .catch(() => setRowsError('加载账单列表失败，请刷新重试'))
      .finally(() => setLoadingRows(false))
  }

  useEffect(() => {
    loadRows()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [month, classFilter, studentName])

  async function applyBulkGenerate() {
    if (!month) return
    setBulkGenerating(true)
    try {
      // 生成范围和当前列表展示的筛选结果保持一致（产品诊断 #55）：筛了班级/搜了姓名，
      // 这里就只生成筛选出来的这些学生，不会背着管理员把其它学生的账单也重新算一遍。
      const result = await billingApi.generateInstitutionBills(
        month,
        selectedClassId,
        studentName.trim() || undefined,
        offCampusOnly,
      )
      setBulkGenerateResult(result)
      loadRows()
    } finally {
      setBulkGenerating(false)
      setConfirmingBulkGenerate(false)
    }
  }

  function handleTogglePaid(row: billingApi.BillOverviewRow) {
    if (row.billId === null) return
    setSinglePaymentMethod('')
    setConfirmingPaidToggle(row)
  }

  async function applyTogglePaid() {
    const row = confirmingPaidToggle
    if (!row || row.billId === null) return
    const nextPaid = !row.isPaid
    const paymentMethod = nextPaid && singlePaymentMethod ? singlePaymentMethod : undefined
    setRows((prev) =>
      prev.map((r) => (r.billId === row.billId ? { ...r, isPaid: nextPaid, paymentMethod: paymentMethod ?? null } : r)),
    )
    setConfirmingPaidToggle(null)
    try {
      await billingApi.setBillPaid(row.billId, nextPaid, paymentMethod)
      showToast(nextPaid ? `已标记${row.studentName}的账单为已缴费` : `已标记${row.studentName}的账单为未缴费`)
    } catch {
      setRows((prev) =>
        prev.map((r) => (r.billId === row.billId ? { ...r, isPaid: row.isPaid, paymentMethod: row.paymentMethod } : r)),
      )
    }
  }

  function toggleSelected(billId: number) {
    setSelectedBillIds((prev) => {
      const next = new Set(prev)
      if (next.has(billId)) {
        next.delete(billId)
      } else {
        next.add(billId)
      }
      return next
    })
  }

  const selectableBillIds = sortedRows.filter((r) => r.billId !== null).map((r) => r.billId as number)
  const allSelected = selectableBillIds.length > 0 && selectableBillIds.every((id) => selectedBillIds.has(id))

  function toggleSelectAll() {
    setSelectedBillIds(allSelected ? new Set() : new Set(selectableBillIds))
  }

  async function applyBatchPaid() {
    if (selectedBillIds.size === 0 || !batchPaymentMethod) return
    setBatchSubmitting(true)
    try {
      const result = await billingApi.batchSetBillPaid([...selectedBillIds], true, batchPaymentMethod)
      setBatchResult(result)
      setSelectedBillIds(new Set())
      loadRows()
    } finally {
      setBatchSubmitting(false)
      setConfirmingBatchPaid(false)
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
      <div className="rounded-2xl border border-panel-border bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-sm text-[#5d5480]">
            月份
            <input
              type="month"
              value={month}
              max={currentMonthString()}
              onChange={(e) => setMonth(e.target.value)}
              className="ml-2 rounded-lg border border-panel-border px-2 py-1 text-sm"
            />
          </label>
          <span className="text-xs text-panel-muted">留空查看全部月份</span>
          <label className="text-sm text-[#5d5480]">
            班级
            <select
              value={classFilter}
              onChange={(e) => setClassFilter(e.target.value)}
              disabled={loadingClasses}
              className="ml-2 rounded-lg border border-panel-border px-2 py-1 text-sm"
            >
              <option value="">全部班级</option>
              <option value={OFF_CAMPUS_FILTER_VALUE}>纯课外课学生</option>
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
              className="ml-2 rounded-lg border border-panel-border px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm text-[#5d5480]">
            排序
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortOption)}
              className="ml-2 rounded-lg border border-panel-border px-2 py-1 text-sm"
            >
              {(Object.keys(SORT_LABEL) as SortOption[]).map((option) => (
                <option key={option} value={option}>
                  {SORT_LABEL[option]}
                </option>
              ))}
            </select>
          </label>
          {month && (
            <button
              type="button"
              onClick={() => {
                if (rows.length === 0) {
                  showToast('当前无学生可以生成账单！')
                  return
                }
                setConfirmingBulkGenerate(true)
              }}
              className="rounded-full bg-panel-primary px-4 py-1 text-sm font-medium text-white"
            >
              一键生成本月账单
            </button>
          )}
        </div>
      </div>

      {selectedBillIds.size > 0 && (
        <div className="flex items-center gap-3 rounded-2xl border border-panel-border bg-white p-3 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
          <span className="text-sm text-[#5d5480]">已选 {selectedBillIds.size} 条账单</span>
          <button
            type="button"
            onClick={() => {
              setBatchPaymentMethod('')
              setConfirmingBatchPaid(true)
            }}
            className="rounded-full bg-panel-primary px-4 py-1 text-sm font-medium text-white"
          >
            批量标记已缴费（{selectedBillIds.size}）
          </button>
        </div>
      )}

      <div className="rounded-2xl border border-panel-border bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">学生账单（{rows.length}）</h2>
        {rowsError && <p className="mt-2 text-sm text-panel-warn">{rowsError}</p>}
        {billLoadError && <p className="mt-2 text-sm text-panel-warn">{billLoadError}</p>}
        {loadingRows && <p className="mt-2 text-sm text-panel-muted">加载中...</p>}
        {!loadingRows && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-panel-muted">
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">
                  <input
                    type="checkbox"
                    aria-label="全选"
                    checked={allSelected}
                    disabled={selectableBillIds.length === 0}
                    onChange={toggleSelectAll}
                  />
                </th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">学生姓名</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">托管班级</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">托管教师</th>
                {!month && <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">月份</th>}
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">是否已缴费</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">逾期天数</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">账单金额</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">费用管理</th>
              </tr>
            </thead>
            <tbody>
              {sortedRows.map((row) => (
                <tr key={`${row.studentId}-${row.yearMonth}`} className="border-b border-panel-border hover:bg-[#faf7ff]">
                  <td className="px-4 py-3">
                    {row.billId !== null && (
                      <input
                        type="checkbox"
                        aria-label={`选择${row.studentName}的账单`}
                        checked={selectedBillIds.has(row.billId)}
                        onChange={() => toggleSelected(row.billId as number)}
                      />
                    )}
                  </td>
                  <td className="px-4 py-3 font-medium text-panel-ink">{row.studentName}</td>
                  <td className="px-4 py-3 text-panel-ink">{row.className}</td>
                  <td className="px-4 py-3 text-panel-ink">{row.teacherName}</td>
                  {!month && <td className="px-4 py-3 text-panel-ink">{row.yearMonth}</td>}
                  <td className="px-4 py-3">
                    {row.billId === null ? (
                      <span className="text-xs text-panel-muted">未生成</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleTogglePaid(row)}
                        className={
                          row.isPaid
                            ? 'rounded-full bg-[#e9f9ef] px-2 py-0.5 text-xs text-[#1f9d55]'
                            : 'rounded-full bg-[#fdf1e6] px-2 py-0.5 text-xs text-panel-warn'
                        }
                      >
                        {row.isPaid ? `已缴费${row.paymentMethod ? `（${PAYMENT_METHOD_LABEL[row.paymentMethod]}）` : ''}` : '未缴费'}
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3 text-panel-ink">
                    {row.billId !== null && !row.isPaid && row.overdueDays > 0 ? (
                      <span className="text-panel-warn">{row.overdueDays} 天</span>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-4 py-3 font-['Sora'] font-semibold text-panel-ink">
                    {row.totalAmount === null ? (
                      '-'
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleViewBill(row)}
                        className="underline decoration-dotted underline-offset-2 hover:text-panel-primary"
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
                      className="rounded-full border border-panel-border px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                    >
                      费用管理
                    </button>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={month ? 8 : 9} className="px-4 py-3 text-xs text-panel-muted">
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

      {confirmingPaidToggle && (
        <ConfirmDialog
          title={confirmingPaidToggle.isPaid ? '标记为未缴费' : '标记为已缴费'}
          message={
            confirmingPaidToggle.isPaid
              ? `确认将${confirmingPaidToggle.studentName}（${confirmingPaidToggle.yearMonth}）的账单标记为未缴费吗？`
              : `确认将${confirmingPaidToggle.studentName}（${confirmingPaidToggle.yearMonth}）的账单标记为已缴费吗？`
          }
          confirmLabel="确认"
          danger={false}
          confirmClassName={PANEL_CONFIRM_BUTTON_CLASSNAME}
          confirmDisabled={!confirmingPaidToggle.isPaid && !singlePaymentMethod}
          onConfirm={applyTogglePaid}
          onCancel={() => setConfirmingPaidToggle(null)}
        >
          {!confirmingPaidToggle.isPaid && (
            <div className="mt-3 text-left">
              <label className="block text-xs font-semibold text-[#5d5480]">
                收款方式
                <select
                  value={singlePaymentMethod}
                  onChange={(e) => setSinglePaymentMethod(e.target.value as PaymentMethod)}
                  className="mt-1 w-full rounded-lg border border-panel-border px-2.5 py-2 text-sm text-panel-ink"
                >
                  <option value="">请选择</option>
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>
                      {PAYMENT_METHOD_LABEL[m]}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          )}
        </ConfirmDialog>
      )}

      {confirmingBatchPaid && (
        <ConfirmDialog
          title="批量标记已缴费"
          message={`确认将选中的 ${selectedBillIds.size} 条账单标记为已缴费吗？`}
          confirmLabel="确认"
          danger={false}
          confirmClassName={PANEL_CONFIRM_BUTTON_CLASSNAME}
          confirming={batchSubmitting}
          confirmDisabled={!batchPaymentMethod}
          onConfirm={applyBatchPaid}
          onCancel={() => setConfirmingBatchPaid(false)}
        >
          <div className="mt-3 text-left">
            <label className="block text-xs font-semibold text-[#5d5480]">
              收款方式
              <select
                value={batchPaymentMethod}
                onChange={(e) => setBatchPaymentMethod(e.target.value as PaymentMethod)}
                className="mt-1 w-full rounded-lg border border-panel-border px-2.5 py-2 text-sm text-panel-ink"
              >
                <option value="">请选择</option>
                {PAYMENT_METHODS.map((m) => (
                  <option key={m} value={m}>
                    {PAYMENT_METHOD_LABEL[m]}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </ConfirmDialog>
      )}

      {confirmingBulkGenerate && (
        <ConfirmDialog
          title="一键生成本月账单"
          message={`确定要为当前筛选出的 ${rows.length} 名学生生成 ${month} 账单吗？已生成过的账单会被重新计算覆盖。`}
          confirmLabel="确认生成"
          danger={false}
          confirmClassName={PANEL_CONFIRM_BUTTON_CLASSNAME}
          confirming={bulkGenerating}
          onConfirm={applyBulkGenerate}
          onCancel={() => setConfirmingBulkGenerate(false)}
        />
      )}

      {bulkGenerateResult && (
        <RollCallResultModal
          type={bulkGenerateResult.failures.length === 0 ? 'success' : 'warning'}
          title={bulkGenerateResult.failures.length === 0 ? '账单生成完成' : '账单生成提醒'}
          message={
            bulkGenerateResult.failures.length === 0
              ? `已生成 ${bulkGenerateResult.generatedCount} 份账单`
              : `已生成 ${bulkGenerateResult.generatedCount} 份账单，${bulkGenerateResult.failures.length} 名学生因班级未配置单价被跳过：` +
                bulkGenerateResult.failures.map((f) => f.studentName).join('、')
          }
          onClose={() => setBulkGenerateResult(null)}
        />
      )}

      {batchResult && (
        <RollCallResultModal
          type={batchResult.failures.length === 0 ? 'success' : 'warning'}
          title={batchResult.failures.length === 0 ? '批量标记完成' : '批量标记提醒'}
          message={
            batchResult.failures.length === 0
              ? `已标记 ${batchResult.successCount} 条账单为已缴费`
              : `已标记 ${batchResult.successCount} 条账单为已缴费，${batchResult.failures.length} 条失败`
          }
          onClose={() => setBatchResult(null)}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  )
}
