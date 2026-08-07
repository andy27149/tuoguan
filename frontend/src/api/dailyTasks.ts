import { apiFetch } from './client'

export interface DailyTask {
  id: number
  studentId: number
  subject: string
  name: string
  custom: boolean
  completed: boolean
}

export function batchAssign(classId: number, taskTemplateIds: number[], date: string): Promise<DailyTask[]> {
  return apiFetch<DailyTask[]>(`/classes/${classId}/daily-tasks/batch`, {
    method: 'POST',
    body: JSON.stringify({ taskTemplateIds, date }),
  })
}

export function addFromTemplateForStudent(studentId: number, taskTemplateId: number, date: string): Promise<DailyTask> {
  return apiFetch<DailyTask>(`/students/${studentId}/daily-tasks`, {
    method: 'POST',
    body: JSON.stringify({ taskTemplateId, date }),
  })
}

export function addCustomForStudent(
  studentId: number,
  subject: string,
  name: string,
  date: string,
): Promise<DailyTask> {
  return apiFetch<DailyTask>(`/students/${studentId}/daily-tasks`, {
    method: 'POST',
    body: JSON.stringify({ subject, name, date }),
  })
}

export function listForClass(classId: number, date: string): Promise<DailyTask[]> {
  return apiFetch<DailyTask[]>(`/classes/${classId}/daily-tasks?date=${date}`)
}

export function setCompleted(id: number, completed: boolean): Promise<DailyTask> {
  return apiFetch<DailyTask>(`/daily-tasks/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ completed }),
  })
}

export function deleteDailyTask(id: number): Promise<void> {
  return apiFetch<void>(`/daily-tasks/${id}`, { method: 'DELETE' })
}
