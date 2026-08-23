import { useEffect, useState } from 'react'
import type { TaskTemplate } from '../api/taskTemplates'
import type { SchoolClassGroup } from '../kanban/schoolClass'

interface AssignTaskBarProps {
  studentsBySchoolClass: SchoolClassGroup[]
  templates: TaskTemplate[]
  onAssign: (schoolClassName: string, templateIds: number[]) => Promise<void>
}

export function AssignTaskBar({ studentsBySchoolClass, templates, onAssign }: AssignTaskBarProps) {
  const [target, setTarget] = useState<string | null>(studentsBySchoolClass[0]?.schoolClassName ?? null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!studentsBySchoolClass.some((g) => g.schoolClassName === target)) {
      setTarget(studentsBySchoolClass[0]?.schoolClassName ?? null)
      setSelected(new Set())
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studentsBySchoolClass])

  function selectTarget(name: string) {
    if (name === target) return
    setTarget(name)
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
    if (selected.size === 0 || !target) return
    setSubmitting(true)
    try {
      await onAssign(target, [...selected])
      setSelected(new Set())
    } finally {
      setSubmitting(false)
    }
  }

  const targetGroup = studentsBySchoolClass.find((g) => g.schoolClassName === target)
  const targetCount = targetGroup?.students.length ?? 0
  const multiGroup = studentsBySchoolClass.length > 1

  return (
    <div className="panel">
      <p style={{ margin: '0 0 8px', fontSize: '14px', fontWeight: 600 }}>从任务库批量分配任务</p>

      {multiGroup ? (
        <div className="assign-target-tabs" role="tablist" aria-label="分配对象">
          {studentsBySchoolClass.map((g) => (
            <button
              key={g.schoolClassName}
              type="button"
              role="tab"
              aria-selected={g.schoolClassName === target}
              className="assign-target-tab"
              onClick={() => selectTarget(g.schoolClassName)}
            >
              {g.schoolClassName}（{g.students.length}人）
            </button>
          ))}
        </div>
      ) : (
        target && (
          <p className="assign-target-hint">
            分配对象：{target}（{targetCount}人）
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
        disabled={submitting || selected.size === 0 || !target}
        className="btn-primary"
      >
        批量分配给{target ?? ''}（{selected.size}）
      </button>
    </div>
  )
}
