import { useEffect, useState } from 'react'
import type { TaskTemplate } from '../api/taskTemplates'
import type { SchoolClassGroup } from '../kanban/schoolClass'

interface AssignTaskBarProps {
  studentsBySchoolClass: SchoolClassGroup[]
  templates: TaskTemplate[]
  onAssign: (schoolClassName: string | null, templateIds: number[]) => Promise<void>
  onAssignAll: (templateIds: number[]) => Promise<void>
}

const UNASSIGNED_LABEL = '未分班'
const ALL_LABEL = '全部学生'
// 虚拟分组下标：真实分组下标从 0 开始，-1 专门留给“全部学生”这个跨分组选项。
const ALL_TARGET_INDEX = -1

function groupLabel(group: SchoolClassGroup): string {
  return group.schoolClassName ?? UNASSIGNED_LABEL
}

function defaultTargetIndex(groups: SchoolClassGroup[]): number | null {
  if (groups.length > 1) return ALL_TARGET_INDEX
  if (groups.length === 1) return 0
  return null
}

function isValidTargetIndex(index: number | null, groups: SchoolClassGroup[]): boolean {
  if (index === null) return false
  if (index === ALL_TARGET_INDEX) return groups.length > 1
  return index >= 0 && index < groups.length
}

export function AssignTaskBar({ studentsBySchoolClass, templates, onAssign, onAssignAll }: AssignTaskBarProps) {
  // 用下标而非班级名表示当前选中的分组：班级名可能为 null（学籍班选填且未填写），
  // 不能用 "名字是否为假值" 来判断"是否选中了一个分组"。
  const [targetIndex, setTargetIndex] = useState<number | null>(defaultTargetIndex(studentsBySchoolClass))
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isValidTargetIndex(targetIndex, studentsBySchoolClass)) {
      setTargetIndex(defaultTargetIndex(studentsBySchoolClass))
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
    setSubmitting(true)
    try {
      if (targetIndex === ALL_TARGET_INDEX) {
        await onAssignAll([...selected])
      } else {
        const target = studentsBySchoolClass[targetIndex]
        await onAssign(target.schoolClassName, [...selected])
      }
      setSelected(new Set())
    } finally {
      setSubmitting(false)
    }
  }

  const isAllTarget = targetIndex === ALL_TARGET_INDEX
  const targetGroup = targetIndex !== null && targetIndex >= 0 ? studentsBySchoolClass[targetIndex] : undefined
  const allStudentsCount = studentsBySchoolClass.reduce((sum, g) => sum + g.students.length, 0)
  const targetCount = isAllTarget ? allStudentsCount : (targetGroup?.students.length ?? 0)
  const targetLabel = isAllTarget ? ALL_LABEL : targetGroup ? groupLabel(targetGroup) : ''
  const multiGroup = studentsBySchoolClass.length > 1

  return (
    <div className="panel">
      <p style={{ margin: '0 0 8px', fontSize: '14px', fontWeight: 600 }}>从任务库批量分配任务</p>

      {multiGroup ? (
        <div className="assign-target-tabs" role="tablist" aria-label="分配对象">
          <button
            type="button"
            role="tab"
            aria-selected={targetIndex === ALL_TARGET_INDEX}
            className="assign-target-tab"
            onClick={() => selectTarget(ALL_TARGET_INDEX)}
          >
            {ALL_LABEL}（{allStudentsCount}人）
          </button>
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
