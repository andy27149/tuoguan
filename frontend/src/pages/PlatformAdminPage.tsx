import { useEffect, useState, type FormEvent } from 'react'
import * as platformApi from '../api/platform'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function PlatformAdminPage() {
  const { logout } = useAuth()

  const [institutions, setInstitutions] = useState<platformApi.PlatformInstitution[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [name, setName] = useState('')
  const [adminPhone, setAdminPhone] = useState('')
  const [adminPassword, setAdminPassword] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createdNotice, setCreatedNotice] = useState<{ name: string; phone: string; password: string } | null>(null)

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
    if (!institutionName || !phone || !password) return
    setCreating(true)
    setCreateError(null)
    try {
      await platformApi.createInstitution(institutionName, phone, password)
      setCreatedNotice({ name: institutionName, phone, password })
      setName('')
      setAdminPhone('')
      setAdminPassword('')
      loadInstitutions()
    } catch (err) {
      setCreateError(err instanceof ApiError && err.status === 409 ? '该手机号已注册' : '创建失败，请重试')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">平台管理</h1>
          <button type="button" onClick={logout} className="logout-btn">
            退出登录
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-2xl space-y-4 px-4 pt-4">
        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <h2 className="text-sm font-medium text-gray-700">开通新机构</h2>
          <form onSubmit={handleCreate} className="mt-2 flex flex-wrap gap-2">
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
            <button
              type="submit"
              disabled={creating || !name.trim() || !adminPhone.trim() || !adminPassword.trim()}
              className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
            >
              开通
            </button>
          </form>
          {createError && (
            <p role="alert" className="mt-1 text-xs text-red-600">
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

        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <h2 className="text-sm font-medium text-gray-700">机构列表（{institutions.length}）</h2>
          {loadError && <p className="mt-1 text-sm text-red-600">{loadError}</p>}
          {loading && <p className="mt-1 text-sm text-gray-400">加载中...</p>}
          {!loading && (
            <ul className="mt-2 space-y-2">
              {institutions.map((institution) => (
                <li key={institution.id} className="rounded border border-gray-100 p-2 text-sm">
                  {institution.name} · {institution.teacherCount} 位教师 · 创建于{' '}
                  {new Date(institution.createdAt).toLocaleDateString()}
                </li>
              ))}
              {institutions.length === 0 && <li className="text-xs text-gray-400">暂无机构</li>}
            </ul>
          )}
        </div>
      </main>
    </div>
  )
}
