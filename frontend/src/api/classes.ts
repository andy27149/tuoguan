import { apiFetch } from './client'

export interface ClassRoom {
  id: number
  name: string
}

// 教师端只读花名册入口；建班已收归管理员，见 api/unit.ts 的 createTeachingUnit。
export function fetchClasses(): Promise<ClassRoom[]> {
  return apiFetch<ClassRoom[]>('/classes')
}
