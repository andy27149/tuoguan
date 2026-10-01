import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import * as institutionApi from '../api/institution'

interface AdminSettingsModuleProps {
  onInstitutionUpdated?: (institution: institutionApi.InstitutionSettings) => void
}

export function AdminSettingsModule({ onInstitutionUpdated }: AdminSettingsModuleProps) {
  const [institution, setInstitution] = useState<institutionApi.InstitutionSettings | null>(null)
  const [loadingInstitution, setLoadingInstitution] = useState(true)
  const [nameInput, setNameInput] = useState('')
  const [nameSubmitting, setNameSubmitting] = useState(false)
  const [nameError, setNameError] = useState<string | null>(null)

  const [logoUploading, setLogoUploading] = useState(false)
  const [logoError, setLogoError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [flagsSubmitting, setFlagsSubmitting] = useState(false)
  const [flagsError, setFlagsError] = useState<string | null>(null)

  function loadInstitution() {
    setLoadingInstitution(true)
    institutionApi
      .fetchInstitutionSettings()
      .then((s) => {
        setInstitution(s)
        setNameInput(s.name)
      })
      .finally(() => setLoadingInstitution(false))
  }

  useEffect(() => {
    loadInstitution()
  }, [])

  async function handleSaveName(e: FormEvent) {
    e.preventDefault()
    const name = nameInput.trim()
    if (!name) return
    setNameSubmitting(true)
    setNameError(null)
    try {
      const updated = await institutionApi.updateInstitutionName(name)
      setInstitution(updated)
      onInstitutionUpdated?.(updated)
    } catch {
      setNameError('保存失败，请重试')
    } finally {
      setNameSubmitting(false)
    }
  }

  async function handleUploadLogo(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setLogoUploading(true)
    setLogoError(null)
    try {
      const updated = await institutionApi.uploadInstitutionLogo(file)
      setInstitution(updated)
      onInstitutionUpdated?.(updated)
    } catch {
      setLogoError('上传失败，请重试')
    } finally {
      setLogoUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  async function handleToggleFlag(field: 'custodyEnabled' | 'offCampusEnabled') {
    if (!institution) return
    const next = {
      custodyEnabled: institution.custodyEnabled,
      offCampusEnabled: institution.offCampusEnabled,
      [field]: !institution[field],
    }
    setFlagsSubmitting(true)
    setFlagsError(null)
    try {
      const updated = await institutionApi.updateFeatureFlags(next.custodyEnabled, next.offCampusEnabled)
      setInstitution(updated)
      onInstitutionUpdated?.(updated)
    } catch {
      setFlagsError('至少需要保留一项业务功能')
    } finally {
      setFlagsSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">机构信息</h2>
        {loadingInstitution ? (
          <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>
        ) : (
          <div className="mt-3 flex flex-wrap items-start gap-6">
            <div className="flex flex-col items-center gap-2">
              {institution?.logoUrl ? (
                <img
                  src={institution.logoUrl}
                  alt="托管班 Logo"
                  className="h-20 w-20 rounded-2xl border border-[#ece7de] object-cover"
                />
              ) : (
                <div className="flex h-20 w-20 items-center justify-center rounded-2xl border border-dashed border-[#ece7de] text-xs text-[#7c7391]">
                  暂无 Logo
                </div>
              )}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={logoUploading}
                className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
              >
                {logoUploading ? '上传中...' : '上传 Logo'}
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleUploadLogo}
                className="hidden"
                aria-label="上传托管班 Logo"
              />
              {logoError && <p className="text-xs text-[#b7591f]">{logoError}</p>}
            </div>
            <form onSubmit={handleSaveName} className="flex flex-wrap items-center gap-2">
              <label className="text-sm text-[#5d5480]">
                托管班名称
                <input
                  value={nameInput}
                  onChange={(e) => setNameInput(e.target.value)}
                  className="ml-2 w-48 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                />
              </label>
              <button
                type="submit"
                disabled={nameSubmitting || !nameInput.trim()}
                className="rounded-full bg-[#6d5bd0] px-4 py-1 text-sm font-medium text-white disabled:opacity-50"
              >
                保存
              </button>
              {nameError && <p className="w-full text-xs text-[#b7591f]">{nameError}</p>}
            </form>
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">业务功能</h2>
        {loadingInstitution ? (
          <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>
        ) : (
          institution && (
            <div className="mt-3 flex flex-col gap-3">
              <label className="flex items-center gap-2 text-sm text-[#241f3d]">
                <input
                  type="checkbox"
                  checked={institution.custodyEnabled}
                  onChange={() => handleToggleFlag('custodyEnabled')}
                  disabled={flagsSubmitting}
                />
                启用托管功能
              </label>
              <label className="flex items-center gap-2 text-sm text-[#241f3d]">
                <input
                  type="checkbox"
                  checked={institution.offCampusEnabled}
                  onChange={() => handleToggleFlag('offCampusEnabled')}
                  disabled={flagsSubmitting}
                />
                启用课外课功能
              </label>
              {flagsError && <p className="text-xs text-[#b7591f]">{flagsError}</p>}
            </div>
          )
        )}
      </div>
    </div>
  )
}
