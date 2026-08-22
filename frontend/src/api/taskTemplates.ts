import { apiFetch } from './client'

export interface TaskTemplate {
  id: number
  subject: string
  name: string
}

export function fetchTaskTemplates(): Promise<TaskTemplate[]> {
  return apiFetch<TaskTemplate[]>('/task-templates')
}

export function createTaskTemplate(subject: string, name: string): Promise<TaskTemplate> {
  return apiFetch<TaskTemplate>('/task-templates', {
    method: 'POST',
    body: JSON.stringify({ subject, name }),
  })
}

export function deleteTaskTemplate(id: number): Promise<void> {
  return apiFetch<void>(`/task-templates/${id}`, { method: 'DELETE' })
}
