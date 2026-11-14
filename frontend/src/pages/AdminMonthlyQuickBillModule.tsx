import { useEffect, useMemo, useState } from 'react'
import * as adminApi from '../api/admin'
import * as billingApi from '../api/billing'
import * as quickBillApi from '../api/monthlyQuickBill'
import { currentMonthString, shiftMonthString } from '../kanban/date'
import { Toast } from '../components/Toast'
import { useToast } from '../hooks/useToast'

// 每行是学生要填的三类聚合数字：本月用餐天数、每门已报课外课的节数、请假天数。
// 费率（托管费/餐费/课时费）不在这里填——都是机构已经配置好的，这个向导只负责
// 把"吃了几天、上了几节、请了几天假"这几个数字换算成能驱动现有账单引擎的记录。
interface RowInput {
  mealDays: string
  leaveDays: string
  courseConsumptions: Record<number, string>
}

function emptyRow(courses: quickBillApi.QuickBillCourse[]): RowInput {
  const courseConsumptions: Record<number, string> = {}
  for (const c of courses) courseConsumptions[c.courseId] = ''
  return { mealDays: '', leaveDays: '', courseConsumptions }
}

export function AdminMonthlyQuickBillModule() {
  const [classes, setClasses] = useState<adminApi.AdminClassRoom[]>([])
  const [classId, setClassId] = useState<number | null>(null)
  const [month, setMonth] = useState(currentMonthString())
  const [students, setStudents] = useState<quickBillApi.QuickBillStudent[]>([])
  const [rows, setRows] = useState<Record<number, RowInput>>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [bills, setBills] = useState<billingApi.MonthlyBill[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { toastMessage, showToast } = useToast()

  useEffect(() => {
    adminApi.fetchAdminClasses().then((list) => {
      setClasses(list)
      if (list.length > 0) setClassId(list[0].id)
    })
  }, [])

  useEffect(() => {
    if (classId === null) return
    setLoading(true)
    setError(null)
    setBills(null)
    quickBillApi
      .fetchQuickBillStudents(classId)
      .then((list) => {
        setStudents(list)
        const next: Record<number, RowInput> = {}
        for (const s of list) next[s.studentId] = emptyRow(s.courses)
        setRows(next)
      })
      .catch(() => setError('加载班级学生失败，请刷新重试'))
      .finally(() => setLoading(false))
  }, [classId])

  // 一个班里不同学生报的课外课可能不一样，表格列取这个班所有学生报过的课外课的并集，
  // 没报某门课的学生在那一列直接不显示输入框。
  const allCourses = useMemo(() => {
    const seen = new Map<number, string>()
    for (const s of students) {
      for (const c of s.courses) seen.set(c.courseId, c.name)
    }
    return Array.from(seen, ([courseId, name]) => ({ courseId, name }))
  }, [students])

  function updateRow(studentId: number, patch: Partial<RowInput>) {
    setRows((prev) => ({ ...prev, [studentId]: { ...prev[studentId], ...patch } }))
  }

  function updateCourseCount(studentId: number, courseId: number, value: string) {
    setRows((prev) => ({
      ...prev,
      [studentId]: {
        ...prev[studentId],
        courseConsumptions: { ...prev[studentId].courseConsumptions, [courseId]: value },
      },
    }))
  }

  // 录入数据和生成账单是两个独立动作，刻意不绑在一起：我们这边只负责把数字换算成
  // 真实记录，什么时候出账单是机构自己的运营决定——他们可能想等数据都录完、或者跟
  // 家长对完价格之后才点生成，不应该一填完数字就被迫连带生成一份账单出来。
  async function handleSaveData() {
    if (classId === null) return
    setSaving(true)
    setError(null)
    try {
      // 后端 submit 对同一个学生同一个月是幂等的（重新提交会先清掉上一次本工具自己写的
      // 记录，再按新数字重新分配，不会叠加），所以这里可以放心并发提交、也可以放心在
      // 失败后整体重试，不用担心把之前成功的学生重复计费。
      const submissions = students
        .map((student) => {
          const row = rows[student.studentId]
          const request: quickBillApi.QuickBillSubmitRequest = {
            month,
            mealDays: Number(row.mealDays) || 0,
            leaveDays: Number(row.leaveDays) || 0,
            courseConsumptions: student.courses
              .map((c) => ({ courseId: c.courseId, count: Number(row.courseConsumptions[c.courseId]) || 0 }))
              .filter((c) => c.count > 0),
          }
          return { student, request }
        })
        .filter(
          ({ request }) =>
            request.mealDays > 0 || request.leaveDays > 0 || request.courseConsumptions.length > 0,
        )

      await Promise.all(submissions.map(({ student, request }) => quickBillApi.submitQuickBill(student.studentId, request)))
      showToast('本月数据已保存')
    } catch {
      setError('保存数据失败，请检查填写的数字是否超过当月可用天数')
    } finally {
      setSaving(false)
    }
  }

  async function handleGenerateBill() {
    if (classId === null) return
    setGenerating(true)
    setError(null)
    setBills(null)
    try {
      const generated = await billingApi.generateClassBills(classId, month)
      setBills(generated)
      showToast('本月账单已生成')
    } catch {
      setError('生成账单失败')
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div className="p-6">
      <h2 className="mb-1 text-lg font-semibold text-panel-ink">月度快速账单</h2>
      <p className="mb-4 text-sm text-panel-muted">
        不需要逐日打卡——按学生填本月用餐天数、课外课节数、请假天数，先保存数据；账单什么时候生成由机构自己决定，
        点"生成本月账单"即可用保存好的数据生成，费率自动用机构已配置的标准。
      </p>

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <select
          className="rounded-lg border border-panel-border px-3 py-1.5 text-sm"
          value={classId ?? ''}
          onChange={(e) => setClassId(Number(e.target.value))}
        >
          {classes.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <div className="flex items-center gap-1">
          <button
            type="button"
            className="rounded-full border border-panel-border px-2 py-1 text-sm"
            onClick={() => setMonth(shiftMonthString(month, -1))}
          >
            ‹
          </button>
          <span className="px-2 text-sm font-medium">{month}</span>
          <button
            type="button"
            className="rounded-full border border-panel-border px-2 py-1 text-sm"
            onClick={() => setMonth(shiftMonthString(month, 1))}
          >
            ›
          </button>
        </div>
      </div>

      {loading && <p className="text-sm text-panel-muted">加载中...</p>}
      {error && <p className="text-sm text-panel-danger">{error}</p>}

      {!loading && students.length === 0 && <p className="text-sm text-panel-muted">这个班级暂无在读学生</p>}

      {!loading && students.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-panel-border">
            <table className="w-full text-left text-sm">
              <thead className="bg-[#f3f0ff] text-panel-muted">
                <tr>
                  <th className="px-3 py-2">学生</th>
                  <th className="px-3 py-2">本月用餐天数</th>
                  <th className="px-3 py-2">请假天数</th>
                  {allCourses.map((c) => (
                    <th key={c.courseId} className="px-3 py-2">
                      {c.name}（节数）
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {students.map((s) => {
                  const row = rows[s.studentId]
                  if (!row) return null
                  const enrolledCourseIds = new Set(s.courses.map((c) => c.courseId))
                  return (
                    <tr key={s.studentId} className="border-t border-panel-border">
                      <td className="px-3 py-2 font-medium">{s.name}</td>
                      <td className="px-3 py-2">
                        <input
                          type="number"
                          min={0}
                          className="w-20 rounded-lg border border-panel-border px-2 py-1"
                          value={row.mealDays}
                          onChange={(e) => updateRow(s.studentId, { mealDays: e.target.value })}
                        />
                      </td>
                      <td className="px-3 py-2">
                        <input
                          type="number"
                          min={0}
                          className="w-20 rounded-lg border border-panel-border px-2 py-1"
                          value={row.leaveDays}
                          onChange={(e) => updateRow(s.studentId, { leaveDays: e.target.value })}
                        />
                      </td>
                      {allCourses.map((c) =>
                        enrolledCourseIds.has(c.courseId) ? (
                          <td key={c.courseId} className="px-3 py-2">
                            <input
                              type="number"
                              min={0}
                              className="w-20 rounded-lg border border-panel-border px-2 py-1"
                              value={row.courseConsumptions[c.courseId] ?? ''}
                              onChange={(e) => updateCourseCount(s.studentId, c.courseId, e.target.value)}
                            />
                          </td>
                        ) : (
                          <td key={c.courseId} className="px-3 py-2 text-panel-muted">
                            —
                          </td>
                        ),
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex gap-2">
            <button
              type="button"
              onClick={handleSaveData}
              disabled={saving}
              className="rounded-full bg-panel-primary px-5 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {saving ? '保存中...' : `保存${month}本月数据`}
            </button>
            <button
              type="button"
              onClick={handleGenerateBill}
              disabled={generating}
              className="rounded-full border border-panel-primary px-5 py-2 text-sm font-medium text-panel-primary disabled:opacity-50"
            >
              {generating ? '生成中...' : `生成${month}本月账单`}
            </button>
          </div>
        </>
      )}

      {bills && (
        <div className="mt-6 rounded-xl border border-panel-border p-4">
          <h3 className="mb-2 text-sm font-semibold text-panel-ink">{month} 账单结果</h3>
          <table className="w-full text-left text-sm">
            <thead className="text-panel-muted">
              <tr>
                <th className="px-3 py-1">学生</th>
                <th className="px-3 py-1">学费</th>
                <th className="px-3 py-1">餐费</th>
                <th className="px-3 py-1">合计</th>
              </tr>
            </thead>
            <tbody>
              {bills.map((b) => {
                const student = students.find((s) => s.studentId === b.studentId)
                return (
                  <tr key={b.id} className="border-t border-panel-border">
                    <td className="px-3 py-1">{student?.name ?? b.studentId}</td>
                    <td className="px-3 py-1">¥{b.tuitionAmount.toFixed(2)}</td>
                    <td className="px-3 py-1">¥{b.mealAmount.toFixed(2)}</td>
                    <td className="px-3 py-1 font-semibold">¥{b.totalAmount.toFixed(2)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <Toast message={toastMessage} />
    </div>
  )
}
