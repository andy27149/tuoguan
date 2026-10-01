import { apiFetch, apiUpload } from './client'
import { compressImage } from '../utils/imageCompression'

export interface InstitutionSettings {
  id: number
  name: string
  logoUrl: string | null
  custodyEnabled: boolean
  offCampusEnabled: boolean
}

export function fetchInstitutionSettings(): Promise<InstitutionSettings> {
  return apiFetch<InstitutionSettings>('/admin/institution')
}

export function updateInstitutionName(name: string): Promise<InstitutionSettings> {
  return apiFetch<InstitutionSettings>('/admin/institution/name', {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export function updateFeatureFlags(custodyEnabled: boolean, offCampusEnabled: boolean): Promise<InstitutionSettings> {
  return apiFetch<InstitutionSettings>('/admin/institution/feature-flags', {
    method: 'PUT',
    body: JSON.stringify({ custodyEnabled, offCampusEnabled }),
  })
}

export async function uploadInstitutionLogo(file: File): Promise<InstitutionSettings> {
  const compressed = await compressImage(file)
  const formData = new FormData()
  formData.append('file', compressed)
  return apiUpload<InstitutionSettings>('/admin/institution/logo', formData)
}
