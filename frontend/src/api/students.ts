import { apiFetch } from './client'

export interface Student {
  id: number
  name: string
  schoolClassName: string
  enrolled: boolean
}

export function fetchStudents(classId: number): Promise<Student[]> {
  return apiFetch<Student[]>(`/classes/${classId}/students`)
}

export function createStudent(classId: number, name: string, schoolClassName: string): Promise<void> {
  return apiFetch<void>(`/classes/${classId}/students`, {
    method: 'POST',
    body: JSON.stringify({ name, schoolClassName }),
  })
}

export function updateStudent(
  studentId: number,
  name: string,
  schoolClassName: string,
  enrolled: boolean,
): Promise<void> {
  return apiFetch<void>(`/students/${studentId}`, {
    method: 'PUT',
    body: JSON.stringify({ name, schoolClassName, enrolled }),
  })
}

export function deleteStudent(studentId: number): Promise<void> {
  return apiFetch<void>(`/students/${studentId}`, { method: 'DELETE' })
}
