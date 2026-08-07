import { useState } from 'react'
import type { TaskTemplate } from '../api/taskTemplates'

interface AssignTaskBarProps {
  templates: TaskTemplate[]
  onAssign: (templateIds: number[]) => Promise<void>
}

export function AssignTaskBar({ templates, onAssign }: AssignTaskBarProps) {
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)

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
    if (selected.size === 0) return
    setSubmitting(true)
    try {
      await onAssign([...selected])
      setSelected(new Set())
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3">
      <p className="mb-2 text-sm font-medium text-gray-700">从任务库批量分配给全班</p>
      {templates.length === 0 ? (
        <p className="text-xs text-gray-400">任务库为空，请先在任务库中添加模板</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {templates.map((t) => (
            <label
              key={t.id}
              className={`cursor-pointer rounded border px-2 py-1 text-sm ${
                selected.has(t.id) ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-200'
              }`}
            >
              <input
                type="checkbox"
                className="mr-1"
                checked={selected.has(t.id)}
                onChange={() => toggle(t.id)}
              />
              [{t.subject}] {t.name}
            </label>
          ))}
        </div>
      )}
      <button
        type="button"
        onClick={handleAssign}
        disabled={submitting || selected.size === 0}
        className="mt-2 rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        批量分配给全班（{selected.size}）
      </button>
    </div>
  )
}
