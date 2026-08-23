import { apiFetch } from './client'

export interface ClassRoom {
  id: number
  name: string
}

export function fetchClasses(): Promise<ClassRoom[]> {
  return apiFetch<ClassRoom[]>('/classes')
}

export function createClass(name: string): Promise<ClassRoom> {
  return apiFetch<ClassRoom>('/classes', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}
