import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import * as unitApi from '../api/unit'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'
import { useEscapeToClose } from '../hooks/useEscapeToClose'
import { RollCallResultModal } from './RollCallResultModal'

interface AnnualPromotionWizardProps {
  monthlyUnits: unitApi.TeachingUnit[]
  onClose: () => void
}

interface StepRow {
  key: string
  sourceId: number | null
  outcome: unitApi.AnnualPromotionOutcome | null
  targetId: number | null
}

const OUTCOME_LABEL: Record<unitApi.AnnualPromotionOutcome, string> = {
  MOVE_TO_UNIT: '转入下一班级',
  OFF_CAMPUS: '转纯课外课',
  DEACTIVATE: '停用',
}

// 班级名称里第一段连续数字视为年级（如"3年级托管班"→3），解析不出来的班级不参与自动预填。
function parseGrade(name: string): number | null {
  const match = name.match(/\d+/)
  return match ? Number(match[0]) : null
}

function buildDefaultSteps(monthlyUnits: unitApi.TeachingUnit[]): StepRow[] {
  const graded = monthlyUnits
    .map((u) => ({ unit: u, grade: parseGrade(u.name) }))
    .filter((x): x is { unit: unitApi.TeachingUnit; grade: number } => x.grade !== null)
    .sort((a, b) => a.grade - b.grade)
  if (graded.length === 0) return []

  const steps: StepRow[] = graded.slice(0, -1).map((cur, i) => ({
    key: `default-${cur.unit.id}`,
    sourceId: cur.unit.id,
    outcome: 'MOVE_TO_UNIT',
    targetId: graded[i + 1].unit.id,
  }))
  const highest = graded[graded.length - 1].unit
  // 最高年级没有更高一级可以晋升，必须由管理员显式选择归宿，不给隐式默认，
  // 避免这批学生的去向被悄悄漏掉。
  steps.push({ key: `default-${highest.id}`, sourceId: highest.id, outcome: null, targetId: null })
  return steps
}

export function AnnualPromotionWizard({ monthlyUnits, onClose }: AnnualPromotionWizardProps) {
  const [screen, setScreen] = useState<'build' | 'confirm' | 'result'>('build')
  const [steps, setSteps] = useState<StepRow[]>(() => buildDefaultSteps(monthlyUnits))
  const [students, setStudents] = useState<courseApi.AdminStudent[]>([])
  const nextKey = useRef(0)

  useEscapeToClose(onClose)

  useEffect(() => {
    courseApi.fetchAdminStudents().then(setStudents).catch(() => setStudents([]))
  }, [])

  const unitById = useMemo(() => new Map(monthlyUnits.map((u) => [u.id, u])), [monthlyUnits])

  function enrolledCount(unitId: number | null): number {
    if (unitId === null) return 0
    return students.filter((s) => s.classRoomId === unitId && s.enrolled).length
  }

  function addStep() {
    nextKey.current += 1
    setSteps((prev) => [...prev, { key: `new-${nextKey.current}`, sourceId: null, outcome: null, targetId: null }])
  }

  function removeStep(key: string) {
    setSteps((prev) => prev.filter((s) => s.key !== key))
  }

  function updateStep(key: string, updates: Partial<StepRow>) {
    setSteps((prev) => prev.map((s) => (s.key === key ? { ...s, ...updates } : s)))
  }

  const usedSourceIds = new Set(steps.map((s) => s.sourceId).filter((id): id is number => id !== null))

  const canProceedToConfirm =
    steps.length > 0 &&
    steps.every((s) => {
      if (s.sourceId === null || s.outcome === null) return false
      if (s.outcome === 'MOVE_TO_UNIT') return s.targetId !== null && s.targetId !== s.sourceId
      return true
    }) &&
    new Set(steps.map((s) => s.sourceId)).size === steps.length

  return (
    <>
      {screen !== 'result' &&
        createPortal(
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
            onClick={(e) => {
              if (e.target === e.currentTarget) onClose()
            }}
          >
            <div
              role="dialog"
              aria-modal="true"
              aria-label="学年批量晋级"
              className="relative flex max-h-[85vh] w-full max-w-2xl flex-col rounded-2xl border border-panel-border bg-white p-5 shadow-[0_20px_50px_rgba(36,31,61,0.25)]"
            >
              <button
                type="button"
                aria-label="关闭"
                onClick={onClose}
                className="absolute right-3 top-3 rounded-lg px-1.5 py-0.5 text-xl leading-none text-panel-muted hover:bg-[#faf7ff] hover:text-panel-ink"
              >
                ×
              </button>
              <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">学年批量晋级</h2>

              {screen === 'build' && (
                <BuildScreen
                  monthlyUnits={monthlyUnits}
                  steps={steps}
                  usedSourceIds={usedSourceIds}
                  enrolledCount={enrolledCount}
                  onAddStep={addStep}
                  onRemoveStep={removeStep}
                  onUpdateStep={updateStep}
                  onNext={() => setScreen('confirm')}
                  canProceed={canProceedToConfirm}
                />
              )}

              {screen === 'confirm' && (
                <ConfirmScreen
                  steps={steps}
                  unitById={unitById}
                  enrolledCount={enrolledCount}
                  onBack={() => setScreen('build')}
                  onSuccess={() => setScreen('result')}
                />
              )}
            </div>
          </div>,
          document.body,
        )}

      {screen === 'result' && (
        <ResultModalHost steps={steps} unitById={unitById} enrolledCount={enrolledCount} onClose={onClose} />
      )}
    </>
  )
}

interface BuildScreenProps {
  monthlyUnits: unitApi.TeachingUnit[]
  steps: StepRow[]
  usedSourceIds: Set<number>
  enrolledCount: (unitId: number | null) => number
  onAddStep: () => void
  onRemoveStep: (key: string) => void
  onUpdateStep: (key: string, updates: Partial<StepRow>) => void
  onNext: () => void
  canProceed: boolean
}

function BuildScreen({
  monthlyUnits,
  steps,
  usedSourceIds,
  enrolledCount,
  onAddStep,
  onRemoveStep,
  onUpdateStep,
  onNext,
  canProceed,
}: BuildScreenProps) {
  return (
    <div className="mt-3 flex min-h-0 flex-1 flex-col">
      <p className="text-sm text-panel-muted">
        已按班级名称中的数字自动预填升序晋级链，最高年级需手动选择归宿；可任意增删改。
      </p>
      <div className="mt-3 flex-1 space-y-2 overflow-y-auto">
        {steps.map((step) => {
          const availableSources = monthlyUnits.filter(
            (u) => u.id === step.sourceId || !usedSourceIds.has(u.id),
          )
          return (
            <div
              key={step.key}
              className="flex flex-wrap items-center gap-2 rounded-xl border border-panel-border p-2.5"
            >
              <select
                aria-label="源班级"
                value={step.sourceId ?? ''}
                onChange={(e) => onUpdateStep(step.key, { sourceId: e.target.value ? Number(e.target.value) : null })}
                className="rounded-lg border border-panel-border px-2 py-1 text-sm"
              >
                <option value="">选择源班级</option>
                {availableSources.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name}
                  </option>
                ))}
              </select>
              {step.sourceId !== null && (
                <span className="rounded-full bg-[#f3f0ff] px-2 py-0.5 text-xs text-panel-primary">
                  {enrolledCount(step.sourceId)}人在读
                </span>
              )}
              <select
                aria-label="归宿"
                value={step.outcome ?? ''}
                onChange={(e) =>
                  onUpdateStep(step.key, {
                    outcome: (e.target.value || null) as unitApi.AnnualPromotionOutcome | null,
                    targetId: e.target.value === 'MOVE_TO_UNIT' ? step.targetId : null,
                  })
                }
                className="rounded-lg border border-panel-border px-2 py-1 text-sm"
              >
                <option value="">选择归宿</option>
                <option value="MOVE_TO_UNIT">{OUTCOME_LABEL.MOVE_TO_UNIT}</option>
                <option value="OFF_CAMPUS">{OUTCOME_LABEL.OFF_CAMPUS}</option>
                <option value="DEACTIVATE">{OUTCOME_LABEL.DEACTIVATE}</option>
              </select>
              {step.outcome === 'MOVE_TO_UNIT' && (
                <select
                  aria-label="目标班级"
                  value={step.targetId ?? ''}
                  onChange={(e) => onUpdateStep(step.key, { targetId: e.target.value ? Number(e.target.value) : null })}
                  className="rounded-lg border border-panel-border px-2 py-1 text-sm"
                >
                  <option value="">选择目标班级</option>
                  {monthlyUnits
                    .filter((u) => u.id !== step.sourceId)
                    .map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.name}
                      </option>
                    ))}
                </select>
              )}
              <button
                type="button"
                aria-label="删除该步骤"
                onClick={() => onRemoveStep(step.key)}
                className="ml-auto rounded-full border border-[#f3d0c0] px-3 py-1 text-xs text-panel-warn hover:bg-[#fdf1e6]"
              >
                删除
              </button>
            </div>
          )
        })}
        {steps.length === 0 && <p className="text-sm text-panel-muted">暂无步骤，点击下方按钮添加</p>}
      </div>
      <button
        type="button"
        onClick={onAddStep}
        className="mt-2 self-start rounded-full border border-panel-border px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
      >
        + 添加步骤
      </button>
      <div className="mt-4 flex justify-end gap-2">
        <button
          type="button"
          onClick={onNext}
          disabled={!canProceed}
          className="rounded-full bg-panel-primary px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          下一步
        </button>
      </div>
    </div>
  )
}

interface ConfirmScreenProps {
  steps: StepRow[]
  unitById: Map<number, unitApi.TeachingUnit>
  enrolledCount: (unitId: number | null) => number
  onBack: () => void
  onSuccess: () => void
}

function ConfirmScreen({ steps, unitById, enrolledCount, onBack, onSuccess }: ConfirmScreenProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const total = steps.reduce((sum, s) => sum + enrolledCount(s.sourceId), 0)

  async function handleConfirm() {
    setSubmitting(true)
    setError(null)
    try {
      await unitApi.runAnnualPromotion(
        steps.map((s) => ({
          sourceTeachingUnitId: s.sourceId!,
          outcome: s.outcome!,
          targetTeachingUnitId: s.outcome === 'MOVE_TO_UNIT' ? s.targetId : null,
        })),
      )
      onSuccess()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '执行失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mt-3 flex min-h-0 flex-1 flex-col">
      <div className="flex-1 space-y-1.5 overflow-y-auto">
        {steps.map((s) => {
          const source = unitById.get(s.sourceId!)
          const target = s.outcome === 'MOVE_TO_UNIT' ? unitById.get(s.targetId!) : null
          return (
            <p key={s.key} className="text-sm text-panel-ink">
              「{source?.name}」（{enrolledCount(s.sourceId)}人）→{' '}
              {s.outcome === 'MOVE_TO_UNIT' ? `「${target?.name}」` : OUTCOME_LABEL[s.outcome!]}
            </p>
          )
        })}
      </div>
      <p className="mt-3 text-sm font-medium text-panel-ink">共迁移 {total} 名学生</p>
      <p className="mt-1 text-xs text-panel-warn">该操作会立即生效，不可撤销；建议在本月账单生成前执行。</p>
      {error && (
        <p role="alert" className="mt-2 text-sm text-panel-warn">
          {error}
        </p>
      )}
      <div className="mt-4 flex justify-end gap-2">
        <button
          type="button"
          onClick={onBack}
          disabled={submitting}
          className="rounded-full border border-panel-border px-4 py-1.5 text-sm text-[#5d5480] hover:bg-[#faf7ff]"
        >
          返回修改
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={submitting}
          className="rounded-full bg-panel-primary px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          确认执行
        </button>
      </div>
    </div>
  )
}

interface ResultModalHostProps {
  steps: StepRow[]
  unitById: Map<number, unitApi.TeachingUnit>
  enrolledCount: (unitId: number | null) => number
  onClose: () => void
}

function ResultModalHost({ steps, unitById, enrolledCount, onClose }: ResultModalHostProps) {
  const total = steps.reduce((sum, s) => sum + enrolledCount(s.sourceId), 0)
  const summaries = steps.map((s) => {
    const source = unitById.get(s.sourceId!)
    const target = s.outcome === 'MOVE_TO_UNIT' ? unitById.get(s.targetId!) : null
    const destination = s.outcome === 'MOVE_TO_UNIT' ? `「${target?.name}」` : OUTCOME_LABEL[s.outcome!]
    return `「${source?.name}」→${destination}（${enrolledCount(s.sourceId)}人）`
  })
  const message = `已完成 ${steps.length} 个步骤，共迁移 ${total} 名学生：${summaries.join('、')}`

  return (
    <RollCallResultModal type="success" title="学年晋级完成" message={message} onClose={onClose} />
  )
}
