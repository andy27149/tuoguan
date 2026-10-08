# 机构管理员总览页 — 前端组装与导航接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已经各自独立实现好的五张总览卡片组件组装进一个新的 `AdminOverviewModule` 页面，接入管理员后台的导航，并把它设为登录后的默认首页。

**Architecture:** `AdminOverviewModule` 是一个纯布局组件，不做任何数据请求——五张卡片各自管理自己的 fetch/loading/error 状态（由另外四个独立计划实现），这里只负责按两栏网格把它们排好、把 `onOpenBilling` 回调从 `AdminDashboardPage` 一路传到 `UnpaidBillsCard`，并把 `'overview'` 接入 `AdminSidebar`/`AdminDashboardPage` 的导航状态机。

**Tech Stack:** React + TypeScript + Tailwind（沿用项目现有约定，无新依赖）。

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md`（前端改动范围一节 + 文件顶部 2026-10-08 补充说明）

## Global Constraints

- `'overview'` 导航项不设 `hiddenWhen`——总览页同时展示托管班和纯课外课两种身份的数据，不该因为任何一个业务开关关闭就整页隐藏。
- `AdminOverviewModule` 自身零数据请求逻辑——所有 fetch/loading/error 都在五张卡片组件内部，这里只是 import 和排版；如果发现自己在这个文件里写了 `useEffect`/`useState` 去拉数据，说明做多了，删掉。
- 五张卡片组件都是具名导出（`export function XxxCard`），不是 default export——已读 `frontend/src/components/Toast.tsx`/`AdminSidebar.tsx` 确认这是本仓库唯一的组件导出约定，不用 default export。
- `onOpenBilling` 必须原样透传三层：`AdminDashboardPage` → `AdminOverviewModule` → `UnpaidBillsCard`，中间任何一层都不能吞掉或重新定义这个回调的含义。

## Review Focus

- `AdminDashboardPage.test.tsx` 里原有的"默认落在 teachers 模块"的断言，如果实现者只是新增一条断言而不是**替换**旧断言，测试套件会同时断言"默认是 teachers"和"默认是 overview"两个矛盾的期望——Task 3 的测试步骤要求显式替换，不是新增。
- `isModuleHidden` 驱动的回退 effect（`AdminDashboardPage.tsx` 里那个"模块被隐藏时回退"的 `useEffect`）目前硬编码回退到 `'teachers'`，总览页上线后这个回退目标也该改成 `'overview'`，否则会出现"默认落地 overview，但业务开关一关又跳回 teachers"的不一致——Task 2 包含这处改动。
- 五张卡片如果在其它计划里最终用了 default export 而不是本计划假设的具名导出，`tsc -b` 会在编译期直接报错——Task 1 的步骤里显式要求跑 `tsc -b` 并确认通过，不能只跑测试就算完工。
- `UnpaidBillsCard` 的 stub 如果没有实际触发 `onOpenBilling` 的交互元素，Task 1 的"透传"测试就只是摆设、测不出真正的断链——测试步骤里 stub 必须带一个可点击元素。
- `AdminSidebar.test.tsx` 现有测试用 `screen.getByText` 按文案断言，如果总览和其它分组恰好有重复文案（目前没有，但新增分组标签"总览"要确认不会跟任何现有 group label 或 item label 冲突）——已核对现有 `NAV_GROUPS` 五个分组标签（基础设置/结构信息/运营产出/财务）和所有 item label，均无重复，Task 2 的测试按现有断言风格直接加，不需要额外去重处理。

---

### Task 1: 新增 `AdminOverviewModule` 组装组件

**Files:**
- Create: `frontend/src/pages/AdminOverviewModule.tsx`
- Create: `frontend/src/pages/AdminOverviewModule.test.tsx`

**Interfaces:**
- Consumes（五张卡片组件，均为另外四个独立计划产出的具名导出，签名如下——如果实际执行时发现某个组件的文件还不存在或导出方式不同，以当时文件的真实内容为准，不要凭本计划假设强行拼接）：
  - `LowBalanceCard` from `../components/LowBalanceCard`，`() => JSX.Element`，无 props。
  - `UnpaidBillsCard` from `../components/UnpaidBillsCard`，props `{ onOpenBilling: () => void }`。
  - `EnrollmentCard` from `../components/EnrollmentCard`，无 props。
  - `TodaySnapshotCard` from `../components/TodaySnapshotCard`，无 props。
  - `RevenueCard` from `../components/RevenueCard`，无 props。
- Produces: `AdminOverviewModule`，具名导出，props `{ onOpenBilling: () => void }`，供 Task 2 的 `AdminDashboardPage` 改动使用。

- [ ] **Step 1: 写组件代码**

```tsx
import { LowBalanceCard } from '../components/LowBalanceCard'
import { UnpaidBillsCard } from '../components/UnpaidBillsCard'
import { EnrollmentCard } from '../components/EnrollmentCard'
import { TodaySnapshotCard } from '../components/TodaySnapshotCard'
import { RevenueCard } from '../components/RevenueCard'

interface AdminOverviewModuleProps {
  onOpenBilling: () => void
}

export function AdminOverviewModule({ onOpenBilling }: AdminOverviewModuleProps) {
  return (
    <div className="p-5">
      <div className="mx-auto grid max-w-6xl grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <LowBalanceCard />
          <UnpaidBillsCard onOpenBilling={onOpenBilling} />
        </div>
        <div className="space-y-4">
          <EnrollmentCard />
          <TodaySnapshotCard />
          <RevenueCard />
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 写测试（五张卡片用 stub mock，验证排版装配和 onOpenBilling 透传，不测卡片内部行为——那是各自计划自己的职责）**

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { AdminOverviewModule } from './AdminOverviewModule'

vi.mock('../components/LowBalanceCard', () => ({
  LowBalanceCard: () => <div>LowBalanceCardStub</div>,
}))
vi.mock('../components/UnpaidBillsCard', () => ({
  UnpaidBillsCard: ({ onOpenBilling }: { onOpenBilling: () => void }) => (
    <div>
      UnpaidBillsCardStub
      <button type="button" onClick={onOpenBilling}>
        trigger
      </button>
    </div>
  ),
}))
vi.mock('../components/EnrollmentCard', () => ({
  EnrollmentCard: () => <div>EnrollmentCardStub</div>,
}))
vi.mock('../components/TodaySnapshotCard', () => ({
  TodaySnapshotCard: () => <div>TodaySnapshotCardStub</div>,
}))
vi.mock('../components/RevenueCard', () => ({
  RevenueCard: () => <div>RevenueCardStub</div>,
}))

describe('AdminOverviewModule', () => {
  it('renders all five cards', () => {
    render(<AdminOverviewModule onOpenBilling={vi.fn()} />)

    expect(screen.getByText('LowBalanceCardStub')).toBeInTheDocument()
    expect(screen.getByText('UnpaidBillsCardStub')).toBeInTheDocument()
    expect(screen.getByText('EnrollmentCardStub')).toBeInTheDocument()
    expect(screen.getByText('TodaySnapshotCardStub')).toBeInTheDocument()
    expect(screen.getByText('RevenueCardStub')).toBeInTheDocument()
  })

  it('passes onOpenBilling through to UnpaidBillsCard', () => {
    const onOpenBilling = vi.fn()
    render(<AdminOverviewModule onOpenBilling={onOpenBilling} />)

    fireEvent.click(screen.getByRole('button', { name: 'trigger' }))

    expect(onOpenBilling).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 3: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/pages/AdminOverviewModule.test.tsx`
Expected: 2 个测试全部 PASS。

- [ ] **Step 4: 跑类型检查确认编译通过（五张卡片组件届时的真实导出方式会在这一步暴露出来，如果报错按真实导出方式调整 import）**

Run: `cd frontend && npx tsc -b`
Expected: 无报错。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/AdminOverviewModule.tsx frontend/src/pages/AdminOverviewModule.test.tsx
git commit -m "feat: 新增机构总览页组装组件 AdminOverviewModule"
```

---

### Task 2: 接入导航——`AdminSidebar` 新增总览入口

**Files:**
- Modify: `frontend/src/components/AdminSidebar.tsx`
- Modify: `frontend/src/components/AdminSidebar.test.tsx`

**Interfaces:**
- Consumes: 无。
- Produces: `AdminModule` 类型新增 `'overview'` 成员；`NAV_GROUPS` 新增一个 `'总览'` 分组，供 Task 3 的 `AdminDashboardPage` 改动使用。

现有文件完整内容（供对照，改动点见下）：

```tsx
export type AdminModule =
  | 'teachers'
  | 'units'
  | 'students'
  | 'taskStats'
  | 'pricing'
  | 'billing'
  | 'settings'
...
const NAV_GROUPS: NavGroup[] = [
  { label: '基础设置', items: [{ key: 'settings', label: '基础配置' }] },
  ...
]
```

- [ ] **Step 1: 给 `AdminModule` 类型加 `'overview'`，作为第一个成员**

```tsx
export type AdminModule =
  | 'overview'
  | 'teachers'
  | 'units'
  | 'students'
  | 'taskStats'
  | 'pricing'
  | 'billing'
  | 'settings'
```

- [ ] **Step 2: 给 `NAV_GROUPS` 数组最前面插入"总览"分组**

```tsx
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
```

注意"总览"分组的 item 没有设置 `hiddenWhen`（Global Constraints 已说明原因），其余分组原样保留不动。

- [ ] **Step 3: 补测试——"总览"导航项存在，点击后 `onSelect` 收到 `'overview'`**

在 `AdminSidebar.test.tsx` 现有 `describe('AdminSidebar', ...)` 块里追加（跟现有测试同一个 `describe`，风格对齐现有断言）：

```tsx
  it('renders the overview entry as its own top group and selects it on click', () => {
    const onSelect = vi.fn()
    render(<AdminSidebar active="overview" onSelect={onSelect} custodyEnabled offCampusEnabled />)

    expect(screen.getByText('总览')).toBeInTheDocument()
    const overviewButton = screen.getByText('机构总览')
    expect(overviewButton).toBeInTheDocument()

    fireEvent.click(overviewButton)

    expect(onSelect).toHaveBeenCalledWith('overview')
  })
```

这条测试需要 `fireEvent` 和 `vi`——检查文件顶部现有 import（`import { render, screen } from '@testing-library/react'` 和 `import { describe, it, expect, vi } from 'vitest'`），把 `fireEvent` 加进 `@testing-library/react` 的那一行 import 里。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/components/AdminSidebar.test.tsx`
Expected: 全部 PASS，新增的这条也 PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/AdminSidebar.tsx frontend/src/components/AdminSidebar.test.tsx
git commit -m "feat: 导航加入机构总览入口"
```

---

### Task 3: 接入默认首页——`AdminDashboardPage` 改默认模块

**Files:**
- Modify: `frontend/src/pages/AdminDashboardPage.tsx`
- Modify: `frontend/src/pages/AdminDashboardPage.test.tsx`

**Interfaces:**
- Consumes: `AdminOverviewModule` from `./AdminOverviewModule`（Task 1 产出），props `{ onOpenBilling: () => void }`。
- Produces: 无（叶子任务）。

现有文件相关片段（完整文件不长，执行时请先完整读一遍再改，这里只摘出要改的三处）：

```tsx
import { AdminSettingsModule } from './AdminSettingsModule'
import { UpdatesButton } from '../components/UpdatesButton'
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
    ...
          <main className="m-0 min-h-0 w-full min-w-0 max-w-none flex-1 overflow-y-auto p-0">
            {activeModule === 'teachers' && <AdminTeachersModule onOpenClassKanban={onOpenClassKanban} />}
            ...
            {activeModule === 'settings' && <AdminSettingsModule onInstitutionUpdated={setInstitution} />}
          </main>
    ...
```

- [ ] **Step 1: 加 import**

在 `import { AdminSettingsModule } from './AdminSettingsModule'` 这一行下面加一行：

```tsx
import { AdminOverviewModule } from './AdminOverviewModule'
```

- [ ] **Step 2: 默认模块改成 `'overview'`**

```tsx
  const [activeModule, setActiveModule] = useState<AdminModule>('overview')
```

- [ ] **Step 3: 模块被隐藏时的回退目标也改成 `'overview'`（Review Focus 里提到的那处——`'overview'` 没有 `hiddenWhen`，永远不会被这个 effect 判定为隐藏，但如果管理员当前正停留在一个因业务开关关闭而被隐藏的模块上，回退后应该落到新的默认首页，不是旧的 teachers）**

```tsx
  useEffect(() => {
    if (!institution) return
    if (isModuleHidden(activeModule, custodyEnabled, offCampusEnabled)) {
      setActiveModule('overview')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [institution])
```

- [ ] **Step 4: 在 `main` 块里加一个新分支，放在最前面（因为现在是默认模块）**

```tsx
          <main className="m-0 min-h-0 w-full min-w-0 max-w-none flex-1 overflow-y-auto p-0">
            {activeModule === 'overview' && <AdminOverviewModule onOpenBilling={() => setActiveModule('billing')} />}
            {activeModule === 'teachers' && <AdminTeachersModule onOpenClassKanban={onOpenClassKanban} />}
```

（后面 `units`/`students`/`taskStats`/`pricing`/`billing`/`settings` 几个分支原样保留不动，只在最前面插入这一行。）

- [ ] **Step 5: 更新测试——mock `AdminOverviewModule`，并把"默认落在 teachers"的断言改成"默认落在 overview"（这是替换，不是新增，旧断言删掉）**

在 `AdminDashboardPage.test.tsx` 现有的一串 `vi.mock('./AdminXxxModule', ...)` 旁边加一个：

```tsx
vi.mock('./AdminOverviewModule', () => ({
  AdminOverviewModule: () => <div>OverviewModuleStub</div>,
}))
```

把现有这个测试：

```tsx
  it('renders the header and defaults to the teachers module', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText('阳光托管班')).toBeInTheDocument()
    expect(screen.getByText('TeachersModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('UnitsModuleStub')).not.toBeInTheDocument()
  })
```

整段替换成：

```tsx
  it('renders the header and defaults to the overview module', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText('阳光托管班')).toBeInTheDocument()
    expect(screen.getByText('OverviewModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('TeachersModuleStub')).not.toBeInTheDocument()
    expect(screen.queryByText('UnitsModuleStub')).not.toBeInTheDocument()
  })
```

（断言默认不再渲染 `TeachersModuleStub`，而不只是不渲染 `UnitsModuleStub`——这是本任务新增的那条覆盖，确认默认模块真的换掉了，不是两个同时渲染。）

- [ ] **Step 6: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/pages/AdminDashboardPage.test.tsx`
Expected: 全部 PASS。

- [ ] **Step 7: 跑一次全量前端测试，确认没有破坏其它文件（比如依赖"默认 teachers"这个假设的其它测试，如果存在的话）**

Run: `cd frontend && npx vitest run`
Expected: 全部 PASS。

- [ ] **Step 8: 跑类型检查**

Run: `cd frontend && npx tsc -b`
Expected: 无报错。

- [ ] **Step 9: Commit**

```bash
git add frontend/src/pages/AdminDashboardPage.tsx frontend/src/pages/AdminDashboardPage.test.tsx
git commit -m "feat: 机构总览页设为管理员登录后默认首页"
```

---

## Self-Review

**1. Spec coverage：** 前端改动范围一节四条——`adminOverview.ts`（另一个独立计划产出，不在本计划范围）、`AdminOverviewModule.tsx`（Task 1）、`AdminSidebar.tsx` 总览入口（Task 2）、`AdminDashboardPage.tsx` 默认模块（Task 3）——全部有对应任务。测试策略一节里属于本计划范围的两条（`AdminSidebar.test.tsx` 总览断言、`AdminDashboardPage.test.tsx` 默认模块断言的"替换不是新增"）都已落实到 Task 2/Task 3 的测试步骤。`AdminOverviewModule.test.tsx` 本身"互不阻塞"那条断言不属于本计划职责（那是各卡片内部行为，由其它计划测），本计划只测排版装配和 props 透传，已在 Task 1 说明里显式划清。

**2. Placeholder scan：** 全文搜索未发现 TBD/TODO/"类似上面"等占位表达，三个 Task 的每个代码步骤都给了可直接使用的完整代码。

**3. Type consistency：** `AdminOverviewModuleProps` 在 Task 1 定义为 `{ onOpenBilling: () => void }`，Task 3 的调用点 `<AdminOverviewModule onOpenBilling={() => setActiveModule('billing')} />` 类型一致；`AdminModule` 新成员 `'overview'`（Task 2 定义）与 Task 3 里 `useState<AdminModule>('overview')`、`activeModule === 'overview'` 用法一致。

**4. Review Focus �covered：** 五条逐一有对应任务步骤兜底——"替换而非新增默认断言"（Task 3 Step 5 显式给出替换前后对照）、"隐藏回退目标同步改成 overview"（Task 3 Step 3）、"tsc -b 兜底具名/默认导出不一致"（Task 1 Step 4、Task 3 Step 8）、"UnpaidBillsCard stub 要有可点击元素"（Task 1 Step 2 的 stub 带了 `<button>`）、"总览分组标签不与现有重复"（已核对，写入 Review Focus 说明，不需要额外任务）。

一个在计划之外、但读代码时确认过不影响本计划的点：`AdminDashboardPage.test.tsx` 没有 mock `AdminStudentsModule`（它是直接引入未 mock 的），因为现有测试从未把 `activeModule` 切到 `'students'`；本计划新增的测试同样不会触发那个分支，不受影响，不需要额外处理。
