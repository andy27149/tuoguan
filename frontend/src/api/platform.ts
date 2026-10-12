import { apiFetch } from './client'

export type RenewalPlan = 'TRIAL' | 'MONTHLY' | 'HALF_YEAR' | 'YEARLY'
export type InstitutionStatus = 'NONE' | 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED'

export interface PlatformInstitution {
  id: number
  name: string
  createdAt: string
  teacherCount: number
  studentCount: number
  classCount: number
  contactName: string | null
  contactPhone: string | null
  address: string | null
  validUntil: string | null
  currentPlan: RenewalPlan | null
  status: InstitutionStatus
  todayViewCount: number
}

export interface CreatedPlatformInstitution {
  institutionId: number
  institutionName: string
  adminPhone: string
}

export interface RenewalRecord {
  id: number
  plan: RenewalPlan
  amount: number | null
  validUntil: string
  note: string | null
  recordedByTeacherName: string
  createdAt: string
}

export function fetchInstitutions(): Promise<PlatformInstitution[]> {
  return apiFetch<PlatformInstitution[]>('/platform/institutions')
}

export function createInstitution(
  institutionName: string,
  adminPhone: string,
  adminInitialPassword: string,
  contactName: string,
  contactPhone: string,
  address: string,
  trialValidUntil: string,
): Promise<CreatedPlatformInstitution> {
  return apiFetch<CreatedPlatformInstitution>('/platform/institutions', {
    method: 'POST',
    body: JSON.stringify({
      institutionName,
      adminPhone,
      adminInitialPassword,
      contactName: contactName || null,
      contactPhone,
      address: address || null,
      trialValidUntil,
    }),
  })
}

export function updateInstitutionContact(
  institutionId: number,
  contactName: string,
  contactPhone: string,
  address: string,
): Promise<PlatformInstitution> {
  return apiFetch<PlatformInstitution>(`/platform/institutions/${institutionId}/contact`, {
    method: 'PATCH',
    body: JSON.stringify({
      contactName: contactName || null,
      contactPhone: contactPhone || null,
      address: address || null,
    }),
  })
}

export function createRenewal(
  institutionId: number,
  plan: RenewalPlan,
  amount: number | null,
  validUntil: string,
  note: string,
): Promise<PlatformInstitution> {
  return apiFetch<PlatformInstitution>(`/platform/institutions/${institutionId}/renewals`, {
    method: 'POST',
    body: JSON.stringify({ plan, amount, validUntil, note: note || null }),
  })
}

export function fetchRenewalHistory(institutionId: number): Promise<RenewalRecord[]> {
  return apiFetch<RenewalRecord[]>(`/platform/institutions/${institutionId}/renewals`)
}
