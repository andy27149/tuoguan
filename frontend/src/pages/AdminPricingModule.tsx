import { useEffect, useState, type FormEvent } from 'react'
import * as billingApi from '../api/billing'
import * as courseApi from '../api/course'

interface AdminPricingModuleProps {
  custodyEnabled: boolean
  offCampusEnabled: boolean
}

export function AdminPricingModule({ custodyEnabled, offCampusEnabled }: AdminPricingModuleProps) {
  const [rates, setRates] = useState<billingApi.ClassBillingRateRow[]>([])
  const [loadingRates, setLoadingRates] = useState(true)
  const [ratesError, setRatesError] = useState<string | null>(null)

  const [bulkTuitionInput, setBulkTuitionInput] = useState('')
  const [bulkMealInput, setBulkMealInput] = useState('')
  const [bulkSubmitting, setBulkSubmitting] = useState(false)
  const [bulkError, setBulkError] = useState<string | null>(null)

  const [editingClassId, setEditingClassId] = useState<number | null>(null)
  const [editTuitionInput, setEditTuitionInput] = useState('')
  const [editMealInput, setEditMealInput] = useState('')
  const [rowSubmitting, setRowSubmitting] = useState(false)
  const [rowError, setRowError] = useState<string | null>(null)

  const [courses, setCourses] = useState<courseApi.AdminCourse[]>([])
  const [loadingCourses, setLoadingCourses] = useState(true)
  const [coursesError, setCoursesError] = useState<string | null>(null)

  const [editingCourseId, setEditingCourseId] = useState<number | null>(null)
  const [editCoursePriceInput, setEditCoursePriceInput] = useState('')
  const [courseRowSubmitting, setCourseRowSubmitting] = useState(false)
  const [courseRowError, setCourseRowError] = useState<string | null>(null)

  function loadRates() {
    setLoadingRates(true)
    setRatesError(null)
    billingApi
      .fetchAllClassBillingRates()
      .then(setRates)
      .catch(() => setRatesError('加载班级计费单价失败，请刷新重试'))
      .finally(() => setLoadingRates(false))
  }

  function loadCourses() {
    setLoadingCourses(true)
    setCoursesError(null)
    courseApi
      .fetchAdminCourses()
      .then(setCourses)
      .catch(() => setCoursesError('加载课外课程单价失败，请刷新重试'))
      .finally(() => setLoadingCourses(false))
  }

  useEffect(() => {
    if (custodyEnabled) loadRates()
  }, [custodyEnabled])

  useEffect(() => {
    if (offCampusEnabled) loadCourses()
  }, [offCampusEnabled])

  async function handleBulkSet(e: FormEvent) {
    e.preventDefault()
    const tuition = Number(bulkTuitionInput)
    const meal = Number(bulkMealInput)
    if (!Number.isFinite(tuition) || !Number.isFinite(meal) || tuition < 0 || meal < 0) {
      setBulkError('请输入有效的单价')
      return
    }
    setBulkSubmitting(true)
    setBulkError(null)
    try {
      const updated = await billingApi.bulkSetClassBillingRate(tuition, meal)
      setRates(updated)
    } catch {
      setBulkError('批量设置失败，请重试')
    } finally {
      setBulkSubmitting(false)
    }
  }

  function handleStartEditRate(row: billingApi.ClassBillingRateRow) {
    setEditingClassId(row.classRoomId)
    setEditTuitionInput(row.tuitionRatePerMonth === null ? '' : String(row.tuitionRatePerMonth))
    setEditMealInput(row.mealRatePerDay === null ? '' : String(row.mealRatePerDay))
    setRowError(null)
  }

  function handleCancelEditRate() {
    setEditingClassId(null)
    setRowError(null)
  }

  async function handleSaveRate(classRoomId: number) {
    const tuition = Number(editTuitionInput)
    const meal = Number(editMealInput)
    if (!Number.isFinite(tuition) || !Number.isFinite(meal) || tuition < 0 || meal < 0) {
      setRowError('请输入有效的单价')
      return
    }
    setRowSubmitting(true)
    setRowError(null)
    try {
      const saved = await billingApi.upsertClassBillingRate(classRoomId, tuition, meal)
      setRates((prev) =>
        prev.map((r) =>
          r.classRoomId === classRoomId
            ? { ...r, tuitionRatePerMonth: saved.tuitionRatePerMonth, mealRatePerDay: saved.mealRatePerDay }
            : r,
        ),
      )
      setEditingClassId(null)
    } catch {
      setRowError('保存失败，请重试')
    } finally {
      setRowSubmitting(false)
    }
  }

  function handleStartEditCoursePrice(course: courseApi.AdminCourse) {
    setEditingCourseId(course.id)
    setEditCoursePriceInput(course.pricePerLesson !== null ? String(course.pricePerLesson) : '')
    setCourseRowError(null)
  }

  function handleCancelEditCoursePrice() {
    setEditingCourseId(null)
    setCourseRowError(null)
  }

  async function handleSaveCoursePrice(courseId: number) {
    const price = Number(editCoursePriceInput)
    if (!Number.isFinite(price) || price < 0) {
      setCourseRowError('请输入有效的单价')
      return
    }
    setCourseRowSubmitting(true)
    setCourseRowError(null)
    try {
      await courseApi.updateAdminCourse(courseId, { pricePerLesson: price })
      setEditingCourseId(null)
      loadCourses()
    } catch {
      setCourseRowError('保存失败，请重试')
    } finally {
      setCourseRowSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      {custodyEnabled && (
        <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
          <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">托管班级定价</h2>
          <form onSubmit={handleBulkSet} className="mt-3 flex flex-wrap items-center gap-2">
            <label className="text-sm text-[#5d5480]">
              托管费（元/月）
              <input
                value={bulkTuitionInput}
                onChange={(e) => setBulkTuitionInput(e.target.value)}
                className="ml-2 w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
              />
            </label>
            <label className="text-sm text-[#5d5480]">
              餐费（元/天）
              <input
                value={bulkMealInput}
                onChange={(e) => setBulkMealInput(e.target.value)}
                className="ml-2 w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
              />
            </label>
            <button
              type="submit"
              disabled={bulkSubmitting || !bulkTuitionInput.trim() || !bulkMealInput.trim()}
              className="rounded-full bg-[#6d5bd0] px-4 py-1 text-sm font-medium text-white disabled:opacity-50"
            >
              一键设置所有班级
            </button>
          </form>
          {bulkError && <p className="mt-1 text-sm text-[#b7591f]">{bulkError}</p>}

          {ratesError && <p className="mt-3 text-sm text-[#b7591f]">{ratesError}</p>}
          {rowError && <p className="mt-3 text-sm text-[#b7591f]">{rowError}</p>}
          {loadingRates && <p className="mt-3 text-sm text-[#7c7391]">加载中...</p>}
          {!loadingRates && (
            <table className="mt-3 w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-[#7c7391]">
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">班级</th>
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管费（元/月）</th>
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">餐费（元/天）</th>
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {rates.map((row) => {
                  const isEditing = editingClassId === row.classRoomId
                  return (
                    <tr key={row.classRoomId} className="border-b border-[#ece7de] hover:bg-[#faf7ff]">
                      <td className="px-4 py-3 font-medium text-[#241f3d]">{row.className}</td>
                      <td className="px-4 py-3 text-[#241f3d]">
                        {isEditing ? (
                          <input
                            aria-label={`${row.className}托管费`}
                            value={editTuitionInput}
                            onChange={(e) => setEditTuitionInput(e.target.value)}
                            className="w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                          />
                        ) : row.tuitionRatePerMonth === null ? (
                          <span className="text-xs text-[#b7591f]">未配置</span>
                        ) : (
                          `¥${row.tuitionRatePerMonth.toFixed(2)}`
                        )}
                      </td>
                      <td className="px-4 py-3 text-[#241f3d]">
                        {isEditing ? (
                          <input
                            aria-label={`${row.className}餐费`}
                            value={editMealInput}
                            onChange={(e) => setEditMealInput(e.target.value)}
                            className="w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                          />
                        ) : row.mealRatePerDay === null ? (
                          <span className="text-xs text-[#b7591f]">未配置</span>
                        ) : (
                          `¥${row.mealRatePerDay.toFixed(2)}`
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {isEditing ? (
                          <span className="flex gap-1.5">
                            <button
                              type="button"
                              onClick={() => handleSaveRate(row.classRoomId)}
                              disabled={rowSubmitting}
                              className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                            >
                              保存
                            </button>
                            <button
                              type="button"
                              onClick={handleCancelEditRate}
                              disabled={rowSubmitting}
                              className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                            >
                              取消
                            </button>
                          </span>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleStartEditRate(row)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            单独设置
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
                {rates.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-4 py-3 text-xs text-[#7c7391]">
                      暂无班级
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      )}

      {offCampusEnabled && (
        <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
          <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">课外课程单价</h2>
          {coursesError && <p className="mt-2 text-sm text-[#b7591f]">{coursesError}</p>}
          {courseRowError && <p className="mt-2 text-sm text-[#b7591f]">{courseRowError}</p>}
          {loadingCourses && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
          {!loadingCourses && (
            <table className="mt-3 w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-[#7c7391]">
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">课程名称</th>
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">单价</th>
                  <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {courses.map((course) => {
                  const isEditing = editingCourseId === course.id
                  return (
                    <tr key={course.id} className="border-b border-[#ece7de] hover:bg-[#faf7ff]">
                      <td className="px-4 py-3 font-medium text-[#241f3d]">{course.name}</td>
                      <td className="px-4 py-3 text-[#241f3d]">
                        {isEditing ? (
                          <input
                            aria-label={`课外课单价${course.id}`}
                            value={editCoursePriceInput}
                            onChange={(e) => setEditCoursePriceInput(e.target.value)}
                            className="w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                          />
                        ) : course.pricePerLesson === null ? (
                          <span className="text-xs text-[#b7591f]">未配置</span>
                        ) : (
                          `¥${course.pricePerLesson.toFixed(2)}`
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {isEditing ? (
                          <span className="flex gap-1.5">
                            <button
                              type="button"
                              onClick={() => handleSaveCoursePrice(course.id)}
                              disabled={courseRowSubmitting}
                              className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                            >
                              保存
                            </button>
                            <button
                              type="button"
                              onClick={handleCancelEditCoursePrice}
                              disabled={courseRowSubmitting}
                              className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                            >
                              取消
                            </button>
                          </span>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleStartEditCoursePrice(course)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            设置单价
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
                {courses.length === 0 && (
                  <tr>
                    <td colSpan={3} className="px-4 py-3 text-xs text-[#7c7391]">
                      暂无课程
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
