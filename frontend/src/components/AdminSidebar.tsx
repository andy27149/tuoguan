export type AdminModule =
  | 'overview'
  | 'teachers'
  | 'units'
  | 'students'
  | 'taskStats'
  | 'pricing'
  | 'billing'
  | 'settings'

interface AdminSidebarProps {
  active: AdminModule
  onSelect: (module: AdminModule) => void
  custodyEnabled: boolean
  offCampusEnabled: boolean
}

interface NavItem {
  key: AdminModule
  label: string
  hiddenWhen?: 'custodyDisabled' | 'offCampusDisabled' | 'unitsDisabled'
}

interface NavGroup {
  label: string
  items: NavItem[]
}

const NAV_GROUPS: NavGroup[] = [
  { label: '总览', items: [{ key: 'overview', label: '机构总览' }] },
  { label: '基础设置', items: [{ key: 'settings', label: '基础配置' }] },
  {
    label: '结构信息',
    items: [
      { key: 'teachers', label: '教师列表' },
      { key: 'units', label: '教学单元', hiddenWhen: 'unitsDisabled' },
      { key: 'students', label: '学生总览' },
    ],
  },
  {
    label: '运营产出',
    items: [{ key: 'taskStats', label: '任务完成情况', hiddenWhen: 'custodyDisabled' }],
  },
  {
    label: '财务',
    items: [
      { key: 'pricing', label: '定价中心' },
      { key: 'billing', label: '账单管理', hiddenWhen: 'custodyDisabled' },
    ],
  },
]

// 注意：custodyEnabled/offCampusEnabled 目前只是 UI 层面的软开关——关闭后只隐藏这里的
// 导航入口，后端对应接口（如 AdminTeachingUnitController）并没有机构级拦截，仍可被直接
// 调用成功。当前风险低（无对外 API），接入对外 API 前需要在后端补充硬校验。见产品诊断 #07。
export function isModuleHidden(
  module: AdminModule,
  custodyEnabled: boolean,
  offCampusEnabled: boolean,
): boolean {
  for (const group of NAV_GROUPS) {
    const item = group.items.find((i) => i.key === module)
    if (!item) continue
    if (item.hiddenWhen === 'custodyDisabled') return !custodyEnabled
    if (item.hiddenWhen === 'offCampusDisabled') return !offCampusEnabled
    if (item.hiddenWhen === 'unitsDisabled') return !custodyEnabled && !offCampusEnabled
    return false
  }
  return false
}

export function AdminSidebar({ active, onSelect, custodyEnabled, offCampusEnabled }: AdminSidebarProps) {
  return (
    <nav className="w-48 shrink-0 overflow-y-auto space-y-4 border-r border-[#ece7de] bg-[#f3f0ff] p-3">
      {NAV_GROUPS.map((group) => {
        const visibleItems = group.items.filter(
          (item) => !isModuleHidden(item.key, custodyEnabled, offCampusEnabled),
        )
        if (visibleItems.length === 0) return null
        return (
          <div key={group.label} className="space-y-1">
            <p className="px-4 text-xs font-semibold uppercase tracking-wide text-[#a79fc2]">{group.label}</p>
            {visibleItems.map((item) => (
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
          </div>
        )
      })}
    </nav>
  )
}
