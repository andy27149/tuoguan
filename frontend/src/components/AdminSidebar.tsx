export type AdminModule = 'teachers' | 'classes' | 'taskStats' | 'billing' | 'settings'

interface AdminSidebarProps {
  active: AdminModule
  onSelect: (module: AdminModule) => void
}

const NAV_ITEMS: { key: AdminModule; label: string }[] = [
  { key: 'teachers', label: '教师列表' },
  { key: 'classes', label: '托管班级' },
  { key: 'taskStats', label: '任务完成情况' },
  { key: 'billing', label: '账单管理' },
  { key: 'settings', label: '基础配置' },
]

export function AdminSidebar({ active, onSelect }: AdminSidebarProps) {
  return (
    <nav className="w-48 shrink-0 overflow-y-auto space-y-1 border-r border-[#ece7de] bg-[#f3f0ff] p-3">
      {NAV_ITEMS.map((item) => (
        <button
          key={item.key}
          type="button"
          onClick={() => onSelect(item.key)}
          aria-current={active === item.key ? 'page' : undefined}
          className={
            active === item.key
              ? 'block w-full rounded-xl bg-white px-4 py-2.5 text-left text-sm font-semibold text-[#6d5bd0] shadow-[0_1px_2px_rgba(109,91,208,0.18)]'
              : 'block w-full rounded-xl px-4 py-2.5 text-left text-sm text-[#5d5480] hover:bg-white/60'
          }
        >
          {item.label}
        </button>
      ))}
    </nav>
  )
}
