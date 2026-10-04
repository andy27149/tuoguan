import { useEffect, useState } from 'react'
import type { TaskTemplate } from '../api/taskTemplates'
import type { SchoolClassGroup } from '../kanban/schoolClass'

interface AssignTaskBarProps {
  studentsBySchoolClass: SchoolClassGroup[]
  templates: TaskTemplate[]
  onAssign: (schoolClassName: string | null, templateIds: number[]) => Promise<void>
}

const UNASSIGNED_LABEL = '未分班'

function groupLabel(group: SchoolClassGroup): string {
  return group.schoolClassName ?? UNASSIGNED_LABEL
}

export function AssignTaskBar({ studentsBySchoolClass, templates, onAssign }: AssignTaskBarProps) {
  // 用下标而非班级名表示当前选中的分组：班级名可能为 null（学籍班选填且未填写），
  // 不能用 "名字是否为假值" 来判断"是否选中了一个分组"。
  const [targetIndex, setTargetIndex] = useState<number | null>(studentsBySchoolClass.length > 0 ? 0 : null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (targetIndex === null || targetIndex >= studentsBySchoolClass.length) {
      setTargetIndex(studentsBySchoolClass.length > 0 ? 0 : null)
      setSelected(new Set())
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studentsBySchoolClass])

  function selectTarget(index: number) {
    if (index === targetIndex) return
    setTargetIndex(index)
    setSelected(new Set())
  }

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  async function handleAssign() {
    if (selected.size === 0 || targetIndex === null) return
    const target = studentsBySchoolClass[targetIndex]
    setSubmitting(true)
    try {
      await onAssign(target.schoolClassName, [...selected])
      setSelected(new Set())
    } finally {
      setSubmitting(false)
    }
  }

  const targetGroup = targetIndex !== null ? studentsBySchoolClass[targetIndex] : undefined
  const targetCount = targetGroup?.students.length ?? 0
  const targetLabel = targetGroup ? groupLabel(targetGroup) : ''
  const multiGroup = studentsBySchoolClass.length > 1

  return (
    <div className="panel">
      <p style={{ margin: '0 0 8px', fontSize: '14px', fontWeight: 600 }}>从任务库批量分配任务</p>

      {multiGroup ? (
        <div className="assign-target-tabs" role="tablist" aria-label="分配对象">
          {studentsBySchoolClass.map((g, i) => (
            <button
              key={g.schoolClassName ?? `__unassigned_${i}`}
              type="button"
              role="tab"
              aria-selected={i === targetIndex}
              className="assign-target-tab"
              onClick={() => selectTarget(i)}
            >
              {groupLabel(g)}（{g.students.length}人）
            </button>
          ))}
        </div>
      ) : (
        targetGroup && (
          <p className="assign-target-hint">
            分配对象：{targetLabel}（{targetCount}人）
          </p>
        )
      )}

      {templates.length === 0 ? (
        <p className="templates-empty">任务库为空，请先在任务库中添加模板</p>
      ) : (
        <div className="assign-templates">
          {templates.map((t) => (
            <label key={t.id} className="assign-check">
              <input type="checkbox" checked={selected.has(t.id)} onChange={() => toggle(t.id)} />[
              {t.subject}] {t.name}
            </label>
          ))}
        </div>
      )}

      <button
        type="button"
        onClick={handleAssign}
        disabled={submitting || selected.size === 0 || targetIndex === null}
        className="btn-primary"
      >
        批量分配给{targetLabel}（{selected.size}）
      </button>
    </div>
  )
}
