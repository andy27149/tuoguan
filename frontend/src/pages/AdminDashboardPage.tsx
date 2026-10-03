import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { AdminSidebar, isModuleHidden, type AdminModule } from '../components/AdminSidebar'
import { AdminTeachersModule } from './AdminTeachersModule'
import { AdminTeachingUnitsModule } from './AdminTeachingUnitsModule'
import { AdminStudentsModule } from './AdminStudentsModule'
import { AdminTaskStatsModule } from './AdminTaskStatsModule'
import { AdminPricingModule } from './AdminPricingModule'
import { AdminBillingModule } from './AdminBillingModule'
import { AdminSettingsModule } from './AdminSettingsModule'
import * as institutionApi from '../api/institution'

interface AdminDashboardPageProps {
  onBack: () => void
  onOpenClassKanban?: (classId: number) => void
}

export function AdminDashboardPage({ onBack, onOpenClassKanban }: AdminDashboardPageProps) {
  const { logout } = useAuth()
  const [activeModule, setActiveModule] = useState<AdminModule>('teachers')
  const [institution, setInstitution] = useState<institutionApi.InstitutionSettings | null>(null)
  const custodyEnabled = institution?.custodyEnabled ?? true
  const offCampusEnabled = institution?.offCampusEnabled ?? true

  useEffect(() => {
    institutionApi.fetchInstitutionSettings().then(setInstitution).catch(() => {})
  }, [])

  useEffect(() => {
    if (!institution) return
    if (isModuleHidden(activeModule, custodyEnabled, offCampusEnabled)) {
      setActiveModule('teachers')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [institution])

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <header className="app-header">
        <div className="app-header__top">
          <div className="flex items-center gap-2">
            {institution?.logoUrl && (
              <img src={institution.logoUrl} alt="" className="h-8 w-8 rounded-lg object-cover" />
            )}
            <h1 className="app-header__title">{institution?.name || '机构管理'}</h1>
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={onBack} className="logout-btn">
              返回看板
            </button>
            <button type="button" onClick={logout} className="logout-btn">
              退出登录
            </button>
          </div>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col px-4 pb-4 pt-4">
        <div className="flex min-h-0 flex-1 overflow-hidden rounded-2xl border border-[#ece7de] bg-[#faf9f6] shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
          <AdminSidebar
            active={activeModule}
            onSelect={setActiveModule}
            custodyEnabled={custodyEnabled}
            offCampusEnabled={offCampusEnabled}
          />
          <main className="m-0 min-h-0 w-full min-w-0 max-w-none flex-1 overflow-y-auto p-0">
            {activeModule === 'teachers' && <AdminTeachersModule onOpenClassKanban={onOpenClassKanban} />}
            {activeModule === 'units' && (
              <AdminTeachingUnitsModule custodyEnabled={custodyEnabled} offCampusEnabled={offCampusEnabled} />
            )}
            {activeModule === 'students' && <AdminStudentsModule />}
            {activeModule === 'taskStats' && <AdminTaskStatsModule />}
            {activeModule === 'pricing' && <AdminPricingModule custodyEnabled={custodyEnabled} />}
            {activeModule === 'billing' && <AdminBillingModule />}
            {activeModule === 'settings' && <AdminSettingsModule onInstitutionUpdated={setInstitution} />}
          </main>
        </div>
      </div>
    </div>
  )
}
