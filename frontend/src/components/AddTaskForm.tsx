import { useState, type FormEvent } from 'react'
import type { TaskTemplate } from '../api/taskTemplates'

interface AddTaskFormProps {
  templates: TaskTemplate[]
  onAddFromTemplate: (templateId: number) => Promise<void>
  onAddCustom: (subject: string, name: string) => Promise<void>
  onCancel: () => void
}

export function AddTaskForm({ templates, onAddFromTemplate, onAddCustom, onCancel }: AddTaskFormProps) {
  const [mode, setMode] = useState<'template' | 'custom'>('template')
  const [templateId, setTemplateId] = useState<string>(templates[0] ? String(templates[0].id) : '')
  const [subject, setSubject] = useState('')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    try {
      if (mode === 'template') {
        if (!templateId) return
        await onAddFromTemplate(Number(templateId))
      } else {
        if (!subject.trim() || !name.trim()) return
        await onAddCustom(subject.trim(), name.trim())
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-2 space-y-2 rounded border border-dashed border-gray-300 p-2">
      <div className="flex gap-2 text-sm">
        <button
          type="button"
          onClick={() => setMode('template')}
          className={`rounded px-2 py-1 ${mode === 'template' ? 'bg-blue-600 text-white' : 'bg-gray-100'}`}
        >
          从任务库选
        </button>
        <button
          type="button"
          onClick={() => setMode('custom')}
          className={`rounded px-2 py-1 ${mode === 'custom' ? 'bg-blue-600 text-white' : 'bg-gray-100'}`}
        >
          定制任务
        </button>
      </div>

      {mode === 'template' ? (
        <select
          value={templateId}
          onChange={(e) => setTemplateId(e.target.value)}
          className="w-full rounded border px-2 py-1 text-sm"
        >
          {templates.length === 0 && <option value="">任务库为空</option>}
          {templates.map((t) => (
            <option key={t.id} value={t.id}>
              [{t.subject}] {t.name}
            </option>
          ))}
        </select>
      ) : (
        <div className="flex gap-2">
          <input
            placeholder="科目"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            className="w-1/3 rounded border px-2 py-1 text-sm"
          />
          <input
            placeholder="任务名称"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="flex-1 rounded border px-2 py-1 text-sm"
          />
        </div>
      )}

      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="rounded px-2 py-1 text-sm text-gray-500">
          取消
        </button>
        <button
          type="submit"
          disabled={submitting || (mode === 'template' && templates.length === 0)}
          className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
        >
          添加
        </button>
      </div>
    </form>
  )
}
