import { useEffect, useState, type FormEvent } from 'react'
import * as platformApi from '../api/platform'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { BrandMark } from '../brand/BrandMark'
import { formatDate } from '../kanban/date'
import { RenewalModal } from '../components/RenewalModal'
import { EditInstitutionContactModal } from '../components/EditInstitutionContactModal'

function defaultTrialValidUntil(): string {
  const d = new Date()
  d.setDate(d.getDate() + 30)
  return d.toISOString().slice(0, 10)
}

function statusBadge(institution: platformApi.PlatformInstitution): { label: string; className: string } {
  if (institution.status === 'NONE' || !institution.validUntil) {
    return { label: '未设置', className: 'bg-gray-100 text-gray-500' }
  }
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const until = new Date(institution.validUntil)
  until.setHours(0, 0, 0, 0)
  const diffDays = Math.round((until.getTime() - today.getTime()) / 86400000)
  if (institution.status === 'EXPIRED') {
    return { label: `已过期 ${-diffDays} 天`, className: 'bg-red-100 text-red-600' }
  }
  if (institution.status === 'EXPIRING_SOON') {
    return {
      label: diffDays === 0 ? '今天到期' : `${diffDays} 天后到期`,
      className: 'bg-orange-100 text-orange-600',
    }
  }
  return { label: `有效至 ${institution.validUntil}`, className: 'bg-green-100 text-green-600' }
}

export function PlatformAdminPage() {
  const { logout } = useAuth()

  const [institutions, setInstitutions] = useState<platformApi.PlatformInstitution[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [name, setName] = useState('')
  const [adminPhone, setAdminPhone] = useState('')
  const [adminPassword, setAdminPassword] = useState('')
  const [contactName, setContactName] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [address, setAddress] = useState('')
  const [trialValidUntil, setTrialValidUntil] = useState(defaultTrialValidUntil())
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createdNotice, setCreatedNotice] = useState<{ name: string; phone: string; password: string } | null>(null)
  const [renewingInstitution, setRenewingInstitution] = useState<platformApi.PlatformInstitution | null>(null)
  const [editingContactInstitution, setEditingContactInstitution] =
    useState<platformApi.PlatformInstitution | null>(null)

  function loadInstitutions() {
    setLoading(true)
    setLoadError(null)
    platformApi
      .fetchInstitutions()
      .then(setInstitutions)
      .catch(() => setLoadError('加载机构列表失败，请刷新重试'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadInstitutions()
  }, [])

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    const institutionName = name.trim()
    const phone = adminPhone.trim()
    const password = adminPassword.trim()
    const phoneToContact = contactPhone.trim()
    if (!institutionName || !phone || !password || !phoneToContact || !trialValidUntil) return
    setCreating(true)
    setCreateError(null)
    try {
      await platformApi.createInstitution(
        institutionName,
        phone,
        password,
        contactName.trim(),
        phoneToContact,
        address.trim(),
        trialValidUntil,
      )
      setCreatedNotice({ name: institutionName, phone, password })
      setName('')
      setAdminPhone('')
      setAdminPassword('')
      setContactName('')
      setContactPhone('')
      setAddress('')
      setTrialValidUntil(defaultTrialValidUntil())
      loadInstitutions()
    } catch (err) {
      setCreateError(err instanceof ApiError && err.status === 409 ? '该手机号已注册' : '创建失败，请重试')
    } finally {
      setCreating(false)
    }
  }

  const canSubmit =
    !!name.trim() && !!adminPhone.trim() && !!adminPassword.trim() && !!contactPhone.trim() && !!trialValidUntil

  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">
            <BrandMark size={22} />
            平台管理
          </h1>
          <button type="button" onClick={logout} className="logout-btn">
            退出登录
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-4xl space-y-4 px-4 pt-4">
        <div className="rounded-lg border border-panel-border bg-white p-3">
          <h2 className="text-sm font-medium text-panel-muted">开通新机构</h2>
          <form onSubmit={handleCreate} className="mt-2 flex flex-wrap items-end gap-2">
            <input
              placeholder="机构名称"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <input
              placeholder="管理员手机号"
              value={adminPhone}
              onChange={(e) => setAdminPhone(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <input
              placeholder="管理员初始密码"
              value={adminPassword}
              onChange={(e) => setAdminPassword(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <input
              placeholder="联系电话"
              value={contactPhone}
              onChange={(e) => setContactPhone(e.target.value)}
              className="w-28 rounded border px-2 py-1 text-sm"
            />
            <input
              placeholder="联系人姓名（选填）"
              value={contactName}
              onChange={(e) => setContactName(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <input
              placeholder="地址（选填）"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              className="w-32 rounded border px-2 py-1 text-sm"
            />
            <label className="flex flex-col text-xs text-panel-muted">
              试用到期日
              <input
                type="date"
                aria-label="试用到期日"
                value={trialValidUntil}
                onChange={(e) => setTrialValidUntil(e.target.value)}
                className="rounded border px-2 py-1 text-sm text-panel-ink"
              />
            </label>
            <button
              type="submit"
              disabled={creating || !canSubmit}
              className="rounded bg-panel-primary px-3 py-1 text-sm text-white disabled:opacity-50"
            >
              开通
            </button>
          </form>
          {createError && (
            <p role="alert" className="mt-1 text-xs text-panel-danger">
              {createError}
            </p>
          )}
          {createdNotice && (
            <div className="mt-2 flex items-start justify-between gap-2 rounded border border-green-200 bg-green-50 p-2 text-xs text-green-800">
              <span>
                机构 {createdNotice.name} 开通成功：管理员手机号 {createdNotice.phone}，初始密码{' '}
                {createdNotice.password}（请尽快告知该管理员，此密码仅显示一次）
              </span>
              <button
                type="button"
                onClick={() => setCreatedNotice(null)}
                className="shrink-0 rounded border border-green-300 px-1.5 py-0.5 text-green-700"
              >
                知道了
              </button>
            </div>
          )}
        </div>

        <div className="rounded-lg border border-panel-border bg-white p-3">
          <h2 className="text-sm font-medium text-panel-muted">机构列表（{institutions.length}）</h2>
          {loadError && <p className="mt-1 text-sm text-panel-danger">{loadError}</p>}
          {loading && <p className="mt-1 text-sm text-panel-muted">加载中...</p>}
          {!loading && (
            <table className="mt-2 w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-panel-muted">
                  <th className="border-b border-panel-border py-2">机构名称</th>
                  <th className="border-b border-panel-border py-2">学生数</th>
                  <th className="border-b border-panel-border py-2">班级数</th>
                  <th className="border-b border-panel-border py-2">教师数</th>
                  <th className="border-b border-panel-border py-2">今日查看</th>
                  <th className="border-b border-panel-border py-2">联系人</th>
                  <th className="border-b border-panel-border py-2">到期状态</th>
                  <th className="border-b border-panel-border py-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {institutions.map((institution) => {
                  const badge = statusBadge(institution)
                  return (
                    <tr key={institution.id} className="border-b border-panel-border">
                      <td className="py-2">
                        {institution.name}
                        <div className="text-xs text-panel-muted">创建于 {formatDate(institution.createdAt)}</div>
                      </td>
                      <td className="py-2">{institution.studentCount}</td>
                      <td className="py-2">{institution.classCount}</td>
                      <td className="py-2">{institution.teacherCount}</td>
                      <td className="py-2">{institution.todayViewCount}</td>
                      <td className="py-2 text-xs text-panel-muted">
                        {institution.contactName || '—'} · {institution.contactPhone || '—'}
                      </td>
                      <td className="py-2">
                        <span className={`rounded-full px-2 py-0.5 text-xs ${badge.className}`}>{badge.label}</span>
                      </td>
                      <td className="py-2">
                        <button
                          type="button"
                          onClick={() => setRenewingInstitution(institution)}
                          className="rounded-full border border-panel-border px-2 py-0.5 text-xs text-panel-muted hover:bg-[#faf7ff]"
                        >
                          续费
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingContactInstitution(institution)}
                          className="ml-1 rounded-full border border-panel-border px-2 py-0.5 text-xs text-panel-muted hover:bg-[#faf7ff]"
                        >
                          编辑联系信息
                        </button>
                      </td>
                    </tr>
                  )
                })}
                {institutions.length === 0 && (
                  <tr>
                    <td colSpan={8} className="py-2 text-xs text-panel-muted">
                      暂无机构
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </main>

      {renewingInstitution && (
        <RenewalModal
          institutionId={renewingInstitution.id}
          institutionName={renewingInstitution.name}
          currentValidUntil={renewingInstitution.validUntil}
          onClose={() => setRenewingInstitution(null)}
          onSaved={loadInstitutions}
        />
      )}

      {editingContactInstitution && (
        <EditInstitutionContactModal
          institutionId={editingContactInstitution.id}
          institutionName={editingContactInstitution.name}
          initialContactName={editingContactInstitution.contactName}
          initialContactPhone={editingContactInstitution.contactPhone}
          initialAddress={editingContactInstitution.address}
          onClose={() => setEditingContactInstitution(null)}
          onSaved={loadInstitutions}
        />
      )}
    </div>
  )
}
