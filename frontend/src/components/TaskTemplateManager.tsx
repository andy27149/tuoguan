import { useState, type FormEvent } from 'react'
import type { TaskTemplate } from '../api/taskTemplates'

interface TaskTemplateManagerProps {
  templates: TaskTemplate[]
  onCreate: (subject: string, name: string) => Promise<void>
  onDelete: (id: number) => Promise<void>
}

export function TaskTemplateManager({ templates, onCreate, onDelete }: TaskTemplateManagerProps) {
  const [open, setOpen] = useState(false)
  const [subject, setSubject] = useState('')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!subject.trim() || !name.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      await onCreate(subject.trim(), name.trim())
      setSubject('')
      setName('')
    } catch {
      setError('添加失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between text-sm font-medium text-gray-700"
      >
        <span>任务库管理（{templates.length}）</span>
        <span className="text-xs text-gray-400">{open ? '收起' : '展开'}</span>
      </button>

      {open && (
        <div className="mt-2 space-y-2">
          <ul className="space-y-1">
            {templates.map((t) => (
              <li key={t.id} className="flex items-center justify-between text-sm">
                <span>
                  [{t.subject}] {t.name}
                </span>
                <button
                  type="button"
                  onClick={() => onDelete(t.id)}
                  aria-label={`删除模板${t.name}`}
                  className="text-gray-400 hover:text-red-500"
                >
                  ×
                </button>
              </li>
            ))}
            {templates.length === 0 && <li className="text-xs text-gray-400">任务库为空</li>}
          </ul>

          <form onSubmit={handleSubmit} className="flex gap-2">
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
            <button
              type="submit"
              disabled={submitting || !subject.trim() || !name.trim()}
              className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
            >
              添加
            </button>
          </form>
          {error && <p className="text-xs text-red-600">{error}</p>}
        </div>
      )}
    </div>
  )
}
