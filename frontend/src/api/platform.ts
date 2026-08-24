import { apiFetch } from './client'

export interface PlatformInstitution {
  id: number
  name: string
  createdAt: string
  teacherCount: number
}

export interface CreatedPlatformInstitution {
  institutionId: number
  institutionName: string
  adminPhone: string
}

export function fetchInstitutions(): Promise<PlatformInstitution[]> {
  return apiFetch<PlatformInstitution[]>('/platform/institutions')
}

export function createInstitution(
  institutionName: string,
  adminPhone: string,
  adminInitialPassword: string,
): Promise<CreatedPlatformInstitution> {
  return apiFetch<CreatedPlatformInstitution>('/platform/institutions', {
    method: 'POST',
    body: JSON.stringify({ institutionName, adminPhone, adminInitialPassword }),
  })
}
