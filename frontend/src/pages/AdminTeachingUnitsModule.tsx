import { useEffect, useState, type FormEvent } from 'react'
import * as unitApi from '../api/unit'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Toast } from '../components/Toast'
import { useToast } from '../hooks/useToast'

interface AdminTeachingUnitsModuleProps {
  custodyEnabled: boolean
  offCampusEnabled: boolean
  onOpenPricing?: () => void
}

const TAB_LABEL: Record<unitApi.BillingMode, string> = {
  MONTHLY: '托管班',
  LESSON_COUNT: '课外课',
}

export function AdminTeachingUnitsModule({ custodyEnabled, offCampusEnabled, onOpenPricing }: AdminTeachingUnitsModuleProps) {
  const availableTabs: unitApi.BillingMode[] = [
    ...(custodyEnabled ? (['MONTHLY'] as const) : []),
    ...(offCampusEnabled ? (['LESSON_COUNT'] as const) : []),
  ]
  const [activeTab, setActiveTab] = useState<unitApi.BillingMode>(availableTabs[0] ?? 'MONTHLY')

  const [units, setUnits] = useState<unitApi.TeachingUnit[]>([])
  const [loadingUnits, setLoadingUnits] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [teachers, setTeachers] = useState<adminApi.Teacher[]>([])
  const [loadingTeachers, setLoadingTeachers] = useState(true)

  const [newName, setNewName] = useState('')
  const [newTeacherId, setNewTeacherId] = useState<number | null>(null)
  const [newDuration, setNewDuration] = useState('')
  const [newPrice, setNewPrice] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')
  const [editingTeacherId, setEditingTeacherId] = useState<number | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const [togglingId, setTogglingId] = useState<number | null>(null)
  const [confirmingDeactivate, setConfirmingDeactivate] = useState<unitApi.TeachingUnit | null>(null)
  const { toastMessage, showToast } = useToast()

  const [deletingUnit, setDeletingUnit] = useState<unitApi.TeachingUnit | null>(null)
  const [deletionImpact, setDeletionImpact] = useState<unitApi.TeachingUnitDeletionImpact | null>(null)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  function loadUnits(tab: unitApi.BillingMode) {
    setLoadingUnits(true)
    setLoadError(null)
    unitApi
      .fetchTeachingUnits(tab)
      .then(setUnits)
      .catch(() => setLoadError('加载列表失败，请刷新重试'))
      .finally(() => setLoadingUnits(false))
  }

  useEffect(() => {
    loadUnits(activeTab)
    setNewName('')
    setNewDuration('')
    setNewPrice('')
    setCreateError(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab])

  useEffect(() => {
    adminApi
      .fetchTeachers()
      .then((list) => {
        setTeachers(list)
        if (list[0]) setNewTeacherId(list[0].id)
      })
      .finally(() => setLoadingTeachers(false))
  }, [])

  // 机构管理员本人同时带班的场景真实存在（后端写接口从一开始就只按 teacherId
  // 归属判断、不看角色，见 KanbanPage.tsx 的 ownsClass），之前这里把 ADMIN
  // 角色的账号整个过滤掉，导致这种班级根本没法通过界面创建/转移——现在把
  // 管理员账号也纳入可选范围，下拉里额外标注角色避免和普通教师账号混淆。
  const teacherOptions = teachers
  function teacherOptionLabel(t: adminApi.Teacher): string {
    const base = t.name || t.phone
    return t.role === 'ADMIN' ? `${base}（管理员）` : base
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    const name = newName.trim()
    if (!name || newTeacherId === null) return

    let lessonDurationMinutes: number | undefined
    let pricePerLesson: number | null | undefined
    if (activeTab === 'LESSON_COUNT') {
      const duration = Number(newDuration)
      if (!Number.isFinite(duration) || duration <= 0) {
        setCreateError('请输入有效的时长')
        return
      }
      lessonDurationMinutes = duration
      pricePerLesson = newPrice.trim() === '' ? null : Number(newPrice)
      if (pricePerLesson !== null && (!Number.isFinite(pricePerLesson) || pricePerLesson < 0)) {
        setCreateError('请输入有效的单价')
        return
      }
    }

    setCreating(true)
    setCreateError(null)
    try {
      await unitApi.createTeachingUnit({
        billingMode: activeTab,
        name,
        teacherId: newTeacherId,
        lessonDurationMinutes,
        pricePerLesson,
      })
      setNewName('')
      setNewDuration('')
      setNewPrice('')
      loadUnits(activeTab)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setCreateError(activeTab === 'MONTHLY' ? '该教师下已有同名班级' : '该课程名称已存在')
      } else if (err instanceof ApiError && err.status === 400) {
        setCreateError('该功能未开启，请先在基础配置中开启')
      } else {
        setCreateError('创建失败，请重试')
      }
    } finally {
      setCreating(false)
    }
  }

  function handleStartEdit(unit: unitApi.TeachingUnit) {
    setEditingId(unit.id)
    setEditingName(unit.name)
    setEditingTeacherId(unit.teacherId)
    setEditError(null)
  }

  function handleCancelEdit() {
    setEditingId(null)
    setEditError(null)
  }

  async function handleSaveEdit() {
    if (editingId === null || editingTeacherId === null) return
    const name = editingName.trim()
    if (!name) return
    setEditSubmitting(true)
    setEditError(null)
    try {
      await unitApi.updateTeachingUnit(editingId, { name, teacherId: editingTeacherId })
      setEditingId(null)
      loadUnits(activeTab)
    } catch (err) {
      setEditError(
        err instanceof ApiError && err.status === 409
          ? activeTab === 'MONTHLY'
            ? '该教师下已有同名班级'
            : '该课程名称已存在'
          : '保存失败，请重试',
      )
    } finally {
      setEditSubmitting(false)
    }
  }

  function handleToggleActive(unit: unitApi.TeachingUnit) {
    // 停用会立刻影响教师端/家长端的可见性，需要确认；重新启用是安全的加法操作，直接生效。
    if (unit.active) {
      setConfirmingDeactivate(unit)
    } else {
      void applyToggleActive(unit)
    }
  }

  async function applyToggleActive(unit: unitApi.TeachingUnit) {
    setTogglingId(unit.id)
    try {
      await unitApi.updateTeachingUnit(unit.id, { active: !unit.active })
      loadUnits(activeTab)
      showToast(unit.active ? `已停用「${unit.name}」` : `已启用「${unit.name}」`)
    } catch {
      setLoadError('操作失败，请重试')
    } finally {
      setTogglingId(null)
      setConfirmingDeactivate(null)
    }
  }

  function handleOpenDelete(unit: unitApi.TeachingUnit) {
    setDeletingUnit(unit)
    setDeletionImpact(null)
    setDeleteError(null)
    unitApi
      .fetchTeachingUnitDeletionImpact(unit.id)
      .then(setDeletionImpact)
      .catch(() => {
        setDeleteError('加载删除影响范围失败，请重试')
        setDeletingUnit(null)
      })
  }

  async function handleConfirmDelete() {
    if (!deletingUnit || deletionImpact === null) return
    setDeleteSubmitting(true)
    setDeleteError(null)
    try {
      await unitApi.deleteTeachingUnit(deletingUnit.id)
      setDeletingUnit(null)
      setDeletionImpact(null)
      loadUnits(activeTab)
    } catch {
      setDeleteError('删除失败，请重试')
    } finally {
      setDeleteSubmitting(false)
    }
  }

  const isLessonCount = activeTab === 'LESSON_COUNT'

  return (
    <div className="space-y-4 p-4">
      {availableTabs.length > 1 && (
        <div className="flex gap-2" role="tablist">
          {availableTabs.map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={activeTab === tab}
              onClick={() => setActiveTab(tab)}
              className={
                activeTab === tab
                  ? 'rounded-full bg-panel-primary px-4 py-1.5 text-sm font-medium text-white'
                  : 'rounded-full border border-panel-border px-4 py-1.5 text-sm text-[#5d5480] hover:bg-[#faf7ff]'
              }
            >
              {TAB_LABEL[tab]}
            </button>
          ))}
        </div>
      )}

      <div className="rounded-2xl border border-panel-border bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">新建{TAB_LABEL[activeTab]}</h2>
        <form onSubmit={handleCreate} className="mt-3 flex flex-wrap items-end gap-2">
          <label className="text-sm text-[#5d5480]">
            名称
            <input
              placeholder={`${TAB_LABEL[activeTab]}名称`}
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="ml-2 w-32 rounded-lg border border-panel-border px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm text-[#5d5480]">
            负责教师
            <select
              aria-label="负责教师"
              value={newTeacherId ?? ''}
              onChange={(e) => setNewTeacherId(Number(e.target.value))}
              disabled={loadingTeachers}
              className="ml-2 rounded-lg border border-panel-border px-2 py-1 text-sm"
            >
              {teacherOptions.map((t) => (
                <option key={t.id} value={t.id}>
                  {teacherOptionLabel(t)}
                </option>
              ))}
            </select>
          </label>
          {isLessonCount && (
            <>
              <label className="text-sm text-[#5d5480]">
                时长（分钟）
                <input
                  type="number"
                  min={1}
                  placeholder="时长（分钟）"
                  value={newDuration}
                  onChange={(e) => setNewDuration(e.target.value)}
                  className="ml-2 w-24 rounded-lg border border-panel-border px-2 py-1 text-sm"
                />
              </label>
              <label className="text-sm text-[#5d5480]">
                单价（选填）
                <input
                  placeholder="单价（选填）"
                  value={newPrice}
                  onChange={(e) => setNewPrice(e.target.value)}
                  className="ml-2 w-20 rounded-lg border border-panel-border px-2 py-1 text-sm"
                />
              </label>
            </>
          )}
          <button
            type="submit"
            disabled={creating || !newName.trim() || newTeacherId === null}
            className="rounded-full bg-panel-primary px-4 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            创建
          </button>
        </form>
        {createError && (
          <p role="alert" className="mt-2 text-sm text-panel-warn">
            {createError}
          </p>
        )}
      </div>

      <div className="rounded-2xl border border-panel-border bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">
          {TAB_LABEL[activeTab]}列表（{units.length}）
        </h2>
        {loadError && <p className="mt-2 text-sm text-panel-warn">{loadError}</p>}
        {deleteError && <p className="mt-2 text-sm text-panel-warn">{deleteError}</p>}
        {editError && <p className="mt-2 text-sm text-panel-warn">{editError}</p>}
        {loadingUnits && <p className="mt-2 text-sm text-panel-muted">加载中...</p>}
        {!loadingUnits && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-panel-muted">
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">名称</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">负责教师</th>
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">登录账号</th>
                {isLessonCount && <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">时长</th>}
                {isLessonCount && <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">单价</th>}
                {isLessonCount && <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">状态</th>}
                <th className="border-b border-panel-border bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {units.map((unit) => {
                const isEditing = editingId === unit.id
                const avatarColors = ['#6d5bd0', '#d9701e', '#2f9e8f', '#c0569b', '#4f86c9']
                const avatarColor = avatarColors[unit.teacherId % avatarColors.length]
                const avatarInitial = (unit.teacherName || unit.teacherPhone).charAt(0)
                return (
                  <tr key={unit.id} className="border-b border-panel-border align-middle hover:bg-[#faf7ff]">
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <input
                          aria-label={`名称${unit.id}`}
                          value={editingName}
                          onChange={(e) => setEditingName(e.target.value)}
                          className="w-28 rounded-lg border border-panel-border px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="font-medium text-panel-ink">{unit.name}</span>
                      )}
                    </td>
                    {isEditing ? (
                      <td className="px-4 py-3" colSpan={2}>
                        <div className="space-y-1">
                          <select
                            aria-label={`负责教师${unit.id}`}
                            value={editingTeacherId ?? ''}
                            onChange={(e) => setEditingTeacherId(Number(e.target.value))}
                            className="rounded-lg border border-panel-border px-2 py-1 text-sm"
                            disabled={loadingTeachers}
                          >
                            {teacherOptions.map((t) => (
                              <option key={t.id} value={t.id}>
                                {teacherOptionLabel(t)}
                              </option>
                            ))}
                          </select>
                          {editingTeacherId !== null && editingTeacherId !== unit.teacherId && (
                            <p className="rounded-lg bg-[#fdf1e6] px-2 py-1 text-xs text-panel-warn">
                              该操作将把「{unit.name}」（含全部内容）整体转移给新教师，确认后无法撤回
                            </p>
                          )}
                        </div>
                      </td>
                    ) : (
                      <>
                        <td className="px-4 py-3">
                          <span className="flex items-center gap-2">
                            <span
                              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white"
                              style={{ backgroundColor: avatarColor }}
                            >
                              {avatarInitial}
                            </span>
                            <span className="text-panel-ink">{unit.teacherName}</span>
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span className="text-panel-muted">{unit.teacherPhone}</span>
                        </td>
                      </>
                    )}
                    {isLessonCount && (
                      <td className="px-4 py-3">
                        <span className="text-panel-muted">{unit.lessonDurationMinutes}分钟</span>
                      </td>
                    )}
                    {isLessonCount && (
                      <td className="px-4 py-3">
                        <span className="text-panel-ink">
                          {unit.pricePerLesson !== null ? `¥${unit.pricePerLesson.toFixed(2)}` : '未配置'}
                        </span>
                        {onOpenPricing ? (
                          <button
                            type="button"
                            onClick={onOpenPricing}
                            className="ml-2 text-xs text-panel-primary underline-offset-2 hover:underline"
                          >
                            前往定价中心设置
                          </button>
                        ) : (
                          <span className="ml-2 text-xs text-[#a79fc2]">（在定价中心设置）</span>
                        )}
                      </td>
                    )}
                    {isLessonCount && (
                      <td className="px-4 py-3">
                        <span
                          className={
                            unit.active
                              ? 'rounded-full bg-[#e9f7f0] px-2 py-0.5 text-xs text-[#2f9e6e]'
                              : 'rounded-full bg-[#f3f0ff] px-2 py-0.5 text-xs text-panel-muted'
                          }
                        >
                          {unit.active ? '启用中' : '已停用'}
                        </span>
                      </td>
                    )}
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <span className="flex gap-1.5">
                          <button
                            type="button"
                            onClick={handleSaveEdit}
                            disabled={editSubmitting || !editingName.trim()}
                            className="shrink-0 rounded-full bg-panel-primary px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelEdit}
                            disabled={editSubmitting}
                            className="shrink-0 rounded-full border border-panel-border px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex flex-wrap gap-1.5">
                          <button
                            type="button"
                            aria-label={`编辑${unit.name}`}
                            onClick={() => handleStartEdit(unit)}
                            className="rounded-full border border-panel-border px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            编辑
                          </button>
                          {isLessonCount && (
                            <button
                              type="button"
                              aria-label={`${unit.active ? '停用' : '启用'}${unit.name}`}
                              onClick={() => handleToggleActive(unit)}
                              disabled={togglingId === unit.id}
                              className="rounded-full border border-panel-border px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
                            >
                              {unit.active ? '停用' : '启用'}
                            </button>
                          )}
                          <button
                            type="button"
                            aria-label={`删除${unit.name}`}
                            onClick={() => handleOpenDelete(unit)}
                            className="rounded-full border border-[#f3d0c0] px-3 py-1 text-xs text-panel-warn hover:bg-[#fdf1e6]"
                          >
                            删除
                          </button>
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {units.length === 0 && (
                <tr>
                  <td colSpan={isLessonCount ? 7 : 4} className="px-4 py-3 text-xs text-panel-muted">
                    暂无{TAB_LABEL[activeTab]}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {deletingUnit && (
        <ConfirmDialog
          title={`删除${TAB_LABEL[deletingUnit.billingMode]}`}
          message={
            deletionImpact === null
              ? '加载中...'
              : deletionImpact.studentCount > 0
                ? `「${deletingUnit.name}」下有 ${deletionImpact.studentCount} 名学生，删除后相关记录将全部清空，且不可恢复，确认删除吗？`
                : `确认删除「${deletingUnit.name}」吗？此操作不可恢复。`
          }
          confirmLabel="确认删除"
          onConfirm={handleConfirmDelete}
          onCancel={() => {
            setDeletingUnit(null)
            setDeletionImpact(null)
          }}
          confirming={deleteSubmitting}
        />
      )}

      {confirmingDeactivate && (
        <ConfirmDialog
          title={`停用${TAB_LABEL[confirmingDeactivate.billingMode]}`}
          message={`停用「${confirmingDeactivate.name}」后，教师端与家长端将不再显示该${TAB_LABEL[confirmingDeactivate.billingMode]}的相关入口，已产生的记录不会被删除，可随时重新启用。确认停用吗？`}
          confirmLabel="确认停用"
          confirming={togglingId === confirmingDeactivate.id}
          onConfirm={() => applyToggleActive(confirmingDeactivate)}
          onCancel={() => setConfirmingDeactivate(null)}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  )
}
