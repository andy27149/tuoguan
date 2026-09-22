import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import * as institutionApi from '../api/institution'
import * as billingApi from '../api/billing'

interface AdminSettingsModuleProps {
  onInstitutionUpdated?: (institution: institutionApi.InstitutionSettings) => void
}

export function AdminSettingsModule({ onInstitutionUpdated }: AdminSettingsModuleProps) {
  const [institution, setInstitution] = useState<institutionApi.InstitutionSettings | null>(null)
  const [loadingInstitution, setLoadingInstitution] = useState(true)
  const [nameInput, setNameInput] = useState('')
  const [nameSubmitting, setNameSubmitting] = useState(false)
  const [nameError, setNameError] = useState<string | null>(null)

  const [logoUploading, setLogoUploading] = useState(false)
  const [logoError, setLogoError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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

  function loadInstitution() {
    setLoadingInstitution(true)
    institutionApi
      .fetchInstitutionSettings()
      .then((s) => {
        setInstitution(s)
        setNameInput(s.name)
      })
      .finally(() => setLoadingInstitution(false))
  }

  function loadRates() {
    setLoadingRates(true)
    setRatesError(null)
    billingApi
      .fetchAllClassBillingRates()
      .then(setRates)
      .catch(() => setRatesError('加载班级计费单价失败，请刷新重试'))
      .finally(() => setLoadingRates(false))
  }

  useEffect(() => {
    loadInstitution()
    loadRates()
  }, [])

  async function handleSaveName(e: FormEvent) {
    e.preventDefault()
    const name = nameInput.trim()
    if (!name) return
    setNameSubmitting(true)
    setNameError(null)
    try {
      const updated = await institutionApi.updateInstitutionName(name)
      setInstitution(updated)
      onInstitutionUpdated?.(updated)
    } catch {
      setNameError('保存失败，请重试')
    } finally {
      setNameSubmitting(false)
    }
  }

  async function handleUploadLogo(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setLogoUploading(true)
    setLogoError(null)
    try {
      const updated = await institutionApi.uploadInstitutionLogo(file)
      setInstitution(updated)
      onInstitutionUpdated?.(updated)
    } catch {
      setLogoError('上传失败，请重试')
    } finally {
      setLogoUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

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

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">机构信息</h2>
        {loadingInstitution ? (
          <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>
        ) : (
          <div className="mt-3 flex flex-wrap items-start gap-6">
            <div className="flex flex-col items-center gap-2">
              {institution?.logoUrl ? (
                <img
                  src={institution.logoUrl}
                  alt="托管班 Logo"
                  className="h-20 w-20 rounded-2xl border border-[#ece7de] object-cover"
                />
              ) : (
                <div className="flex h-20 w-20 items-center justify-center rounded-2xl border border-dashed border-[#ece7de] text-xs text-[#7c7391]">
                  暂无 Logo
                </div>
              )}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={logoUploading}
                className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
              >
                {logoUploading ? '上传中...' : '上传 Logo'}
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleUploadLogo}
                className="hidden"
                aria-label="上传托管班 Logo"
              />
              {logoError && <p className="text-xs text-[#b7591f]">{logoError}</p>}
            </div>
            <form onSubmit={handleSaveName} className="flex flex-wrap items-center gap-2">
              <label className="text-sm text-[#5d5480]">
                托管班名称
                <input
                  value={nameInput}
                  onChange={(e) => setNameInput(e.target.value)}
                  className="ml-2 w-48 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                />
              </label>
              <button
                type="submit"
                disabled={nameSubmitting || !nameInput.trim()}
                className="rounded-full bg-[#6d5bd0] px-4 py-1 text-sm font-medium text-white disabled:opacity-50"
              >
                保存
              </button>
              {nameError && <p className="w-full text-xs text-[#b7591f]">{nameError}</p>}
            </form>
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">班级计费单价</h2>
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
    </div>
  )
}
