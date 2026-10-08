# 总览页：在读规模 + 今日运营快照卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two small, self-contained React cards — `EnrollmentCard`（在读规模）and `TodaySnapshotCard`（今日运营快照）— each fetching its own data from the already-built `/api/admin/overview/*` endpoints and rendering independently of any sibling card.

**Architecture:** Each card is a standalone component with its own `useState`/`useEffect` fetch + loading/error state, zero props, zero shared parent state. A later, separate "assembly" plan renders these components inside the overview page grid; this plan does not touch that page.

**Tech Stack:** React, TypeScript, Vitest + Testing Library, Tailwind utility classes (no new dependencies).

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md` (sections "3. 在读规模" and "4. 今日运营快照"; also read the "背景" correction note — it explains why cards 2/5 changed but confirms these two cards are unaffected).

## Global Constraints

- 请假进度条颜色固定为中性浅紫 `bg-[#b7a9f0]`，禁止使用任何警示色（橙/红）——请假人数多不代表"不好"，这是设计评审阶段用户明确纠正过的点。
- 两张卡片的所有"比例/百分比"计算必须对分母为 0 的情况做保护，渲染结果必须是 `0%`，绝不能让 `NaN` 出现在任何 `style` 属性里。
- 每张卡片自己管理自己的 `fetch`/loading/error 状态，不依赖、不感知其他卡片，不接受与兄弟卡片协调相关的 props。
- "今日"固定是服务器当前日期，不做日期参数、不做历史回看。

## Review Focus

- 机构在读学生总数为 0 时，在读规模卡片的两段比例条必须渲染 `0%` 宽度，不能是 `NaN%`。
- 机构今日托管学生总数为 0 时，今日运营快照卡片的三条进度条必须全部是 `0%`，不能是 `NaN%`。
- 请假进度条必须使用中性色 `bg-[#b7a9f0]`，测试要显式断言它**没有**用到应用里其他地方的警示色（如 `bg-[#b7591f]`），不能只检查文案对不对。
- `byTeacher` 为空数组（机构只有纯课外课学生，没有任何托管班学生）时，"按老师分布"区块要显示一句提示文案，不能渲染一个空白区域。
- 两张卡片各自的接口请求失败时，只影响这一张卡片显示错误态，不能让整个组件树崩溃（用 try/catch 风格的 `.catch`，不要让 rejection 变成未捕获异常）。

---

### Task 1: EnrollmentCard（在读规模卡片）

**Files:**
- Create: `frontend/src/components/EnrollmentCard.tsx`
- Create: `frontend/src/components/EnrollmentCard.test.tsx`

**Interfaces:**
- Consumes: `fetchEnrollmentSummary(): Promise<EnrollmentSummary>` and `interface EnrollmentSummary { totalCount: number; custodyCount: number; offCampusOnlyCount: number; byTeacher: TeacherStudentCount[] }` / `interface TeacherStudentCount { teacherName: string; studentCount: number }`，均已存在于 `frontend/src/api/adminOverview.ts`（由另一个计划实现，直接 `import` 使用，不要重新定义这些类型）。
- Produces: `export function EnrollmentCard()`——无 props，供后续"组装"计划直接 `<EnrollmentCard />` 使用。

- [ ] **Step 1: Write the failing test**

```tsx
// frontend/src/components/EnrollmentCard.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { EnrollmentCard } from './EnrollmentCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

describe('EnrollmentCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders total/custody/off-campus counts and the byTeacher list', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 32,
      custodyCount: 18,
      offCampusOnlyCount: 14,
      byTeacher: [
        { teacherName: '李琦', studentCount: 12 },
        { teacherName: '王老师', studentCount: 6 },
      ],
    })

    render(<EnrollmentCard />)

    expect(await screen.findByText('32')).toBeInTheDocument()
    expect(screen.getByText('名在读学生')).toBeInTheDocument()
    expect(screen.getByText('托管班 18')).toBeInTheDocument()
    expect(screen.getByText('纯课外课 14')).toBeInTheDocument()
    expect(screen.getByText('李琦')).toBeInTheDocument()
    expect(screen.getByText('12 人')).toBeInTheDocument()
    expect(screen.getByText('王老师')).toBeInTheDocument()
    expect(screen.getByText('6 人')).toBeInTheDocument()
  })

  it('sizes the two proportion-bar segments from the counts', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 32,
      custodyCount: 18,
      offCampusOnlyCount: 14,
      byTeacher: [],
    })

    render(<EnrollmentCard />)
    await screen.findByText('32')

    const custodySegment = screen.getByTestId('enrollment-bar-custody')
    const offCampusSegment = screen.getByTestId('enrollment-bar-off-campus')
    expect(custodySegment.style.width).toBe('56%')
    expect(offCampusSegment.style.width).toBe('44%')
  })

  it('renders 0% bars instead of NaN% when totalCount is 0', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 0,
      custodyCount: 0,
      offCampusOnlyCount: 0,
      byTeacher: [],
    })

    render(<EnrollmentCard />)
    await screen.findByText('0')

    const custodySegment = screen.getByTestId('enrollment-bar-custody')
    const offCampusSegment = screen.getByTestId('enrollment-bar-off-campus')
    expect(custodySegment.style.width).toBe('0%')
    expect(offCampusSegment.style.width).toBe('0%')
    expect(custodySegment.style.width).not.toContain('NaN')
    expect(offCampusSegment.style.width).not.toContain('NaN')
  })

  it('shows a placeholder line when byTeacher is empty', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 14,
      custodyCount: 0,
      offCampusOnlyCount: 14,
      byTeacher: [],
    })

    render(<EnrollmentCard />)
    await screen.findByText('14')

    expect(screen.getByText('暂无托管班学生')).toBeInTheDocument()
  })

  it('shows an error message when the fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockRejectedValue(new Error('network error'))

    render(<EnrollmentCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/EnrollmentCard.test.tsx`
Expected: FAIL — `Cannot find module './EnrollmentCard'` (component doesn't exist yet).

- [ ] **Step 3: Write the component**

```tsx
// frontend/src/components/EnrollmentCard.tsx
import { useEffect, useState } from 'react'
import { fetchEnrollmentSummary, type EnrollmentSummary } from '../api/adminOverview'

export function EnrollmentCard() {
  const [summary, setSummary] = useState<EnrollmentSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchEnrollmentSummary()
      .then(setSummary)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">在读规模</h2>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {!error && !summary && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}

      {summary && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-[#241f3d]">
            {summary.totalCount} <span className="text-base font-normal text-[#7c7391]">名在读学生</span>
          </p>

          <div className="mt-3 flex h-2 overflow-hidden rounded-full bg-[#f1f0f4]">
            <div
              data-testid="enrollment-bar-custody"
              className="h-full bg-[#6d5bd0]"
              style={{ width: `${pct(summary.custodyCount, summary.totalCount)}%` }}
            />
            <div
              data-testid="enrollment-bar-off-campus"
              className="h-full bg-[#b7a9f0]"
              style={{ width: `${pct(summary.offCampusOnlyCount, summary.totalCount)}%` }}
            />
          </div>
          <div className="mt-2 flex items-center justify-between text-xs">
            <span className="flex items-center gap-1.5 text-[#5d5480]">
              <span className="h-2 w-2 rounded-full bg-[#6d5bd0]" />
              托管班 {summary.custodyCount}
            </span>
            <span className="flex items-center gap-1.5 text-[#5d5480]">
              <span className="h-2 w-2 rounded-full bg-[#b7a9f0]" />
              纯课外课 {summary.offCampusOnlyCount}
            </span>
          </div>

          <div className="mt-4 space-y-2 border-t border-[#f3f0ea] pt-3">
            <p className="text-xs text-[#a79fc2]">按老师分布</p>
            {summary.byTeacher.length === 0 ? (
              <p className="text-sm text-[#7c7391]">暂无托管班学生</p>
            ) : (
              <div className="space-y-1.5 text-sm text-[#5d5480]">
                {summary.byTeacher.map((entry) => (
                  <div key={entry.teacherName} className="flex items-center justify-between">
                    <span>{entry.teacherName}</span>
                    <span className="font-medium text-[#241f3d]">{entry.studentCount} 人</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}

function pct(count: number, total: number): number {
  return total > 0 ? Math.round((count / total) * 100) : 0
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/EnrollmentCard.test.tsx`
Expected: PASS — all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/EnrollmentCard.tsx frontend/src/components/EnrollmentCard.test.tsx
git commit -m "feat: add EnrollmentCard for admin overview page"
```

---

### Task 2: TodaySnapshotCard（今日运营快照卡片）

**Files:**
- Create: `frontend/src/components/TodaySnapshotCard.tsx`
- Create: `frontend/src/components/TodaySnapshotCard.test.tsx`

**Interfaces:**
- Consumes: `fetchTodaySnapshot(): Promise<TodaySnapshot>` and `interface TodaySnapshot { arrivedCount: number; mealCount: number; leaveCount: number; totalCustodyStudentCount: number }`，已存在于 `frontend/src/api/adminOverview.ts`。
- Produces: `export function TodaySnapshotCard()`——无 props。

- [ ] **Step 1: Write the failing test**

```tsx
// frontend/src/components/TodaySnapshotCard.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TodaySnapshotCard } from './TodaySnapshotCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

describe('TodaySnapshotCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the three fraction lines', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 15,
      mealCount: 10,
      leaveCount: 1,
      totalCustodyStudentCount: 18,
    })

    render(<TodaySnapshotCard />)

    expect(await screen.findByText('15 / 18')).toBeInTheDocument()
    expect(screen.getByText('10 / 18')).toBeInTheDocument()
    expect(screen.getByText('1 / 18')).toBeInTheDocument()
  })

  it('uses the neutral lavender color for the leave bar, never an alert color', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 15,
      mealCount: 10,
      leaveCount: 1,
      totalCustodyStudentCount: 18,
    })

    render(<TodaySnapshotCard />)
    await screen.findByText('1 / 18')

    const leaveBar = screen.getByTestId('today-bar-leave')
    expect(leaveBar).toHaveClass('bg-[#b7a9f0]')
    expect(leaveBar).not.toHaveClass('bg-[#b7591f]')
    expect(leaveBar).not.toHaveClass('bg-red-500')
    expect(leaveBar).not.toHaveClass('bg-amber-500')
  })

  it('renders 0% bars instead of NaN% when totalCustodyStudentCount is 0', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 0,
      mealCount: 0,
      leaveCount: 0,
      totalCustodyStudentCount: 0,
    })

    render(<TodaySnapshotCard />)
    await screen.findByText('0 / 0')

    const arrivedBar = screen.getByTestId('today-bar-arrived')
    const mealBar = screen.getByTestId('today-bar-meal')
    const leaveBar = screen.getByTestId('today-bar-leave')
    expect(arrivedBar.style.width).toBe('0%')
    expect(mealBar.style.width).toBe('0%')
    expect(leaveBar.style.width).toBe('0%')
  })

  it('shows an error message when the fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockRejectedValue(new Error('network error'))

    render(<TodaySnapshotCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/TodaySnapshotCard.test.tsx`
Expected: FAIL — `Cannot find module './TodaySnapshotCard'`.

- [ ] **Step 3: Write the component**

```tsx
// frontend/src/components/TodaySnapshotCard.tsx
import { useEffect, useState } from 'react'
import { fetchTodaySnapshot, type TodaySnapshot } from '../api/adminOverview'

const TODAY = new Date().toISOString().slice(0, 10)

export function TodaySnapshotCard() {
  const [snapshot, setSnapshot] = useState<TodaySnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchTodaySnapshot()
      .then(setSnapshot)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <div className="flex items-baseline justify-between">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">今日运营快照</h2>
        <span className="text-xs text-[#7c7391]">{TODAY}</span>
      </div>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {!error && !snapshot && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}

      {snapshot && (
        <div className="mt-4 space-y-3">
          <Row
            testId="today-bar-arrived"
            icon="🕐"
            label="到了"
            count={snapshot.arrivedCount}
            total={snapshot.totalCustodyStudentCount}
            barColor="bg-[#2f9e6e]"
          />
          <Row
            testId="today-bar-meal"
            icon="🍚"
            label="用餐"
            count={snapshot.mealCount}
            total={snapshot.totalCustodyStudentCount}
            barColor="bg-[#2f9e6e]"
          />
          <Row
            testId="today-bar-leave"
            icon="🌴"
            label="请假"
            count={snapshot.leaveCount}
            total={snapshot.totalCustodyStudentCount}
            barColor="bg-[#b7a9f0]"
          />
        </div>
      )}
    </section>
  )
}

function Row({
  testId,
  icon,
  label,
  count,
  total,
  barColor,
}: {
  testId: string
  icon: string
  label: string
  count: number
  total: number
  barColor: string
}) {
  const width = total > 0 ? Math.round((count / total) * 100) : 0
  return (
    <div>
      <div className="flex items-center justify-between text-sm">
        <span className="text-[#5d5480]">
          {icon} {label}
        </span>
        <span className="font-['Sora'] font-semibold text-[#241f3d]">
          {count} / {total}
        </span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-[#f1f0f4]">
        <div data-testid={testId} className={`h-full rounded-full ${barColor}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/TodaySnapshotCard.test.tsx`
Expected: PASS — all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/TodaySnapshotCard.tsx frontend/src/components/TodaySnapshotCard.test.tsx
git commit -m "feat: add TodaySnapshotCard for admin overview page"
```

---

## Self-Review

**1. Spec coverage:** 在读规模 section → Task 1 (totalCount/custodyCount/offCampusOnlyCount/byTeacher, all rendered). 今日运营快照 section → Task 2 (三条 count/total + 进度条, "今日"固定为 `LocalDate.now()` 对应的客户端当前日期)。两张卡片都不依赖其余三张卡片，符合决策 2（每张卡片独立接口、独立 loading/error）。

**2. Placeholder scan:** 无 TBD/TODO；两个组件和两个测试文件都是完整代码，没有"仿照 Task X"这种省略写法。

**3. Type consistency:** 两个组件都从 `../api/adminOverview` 导入已经在 spec 里定好的 `EnrollmentSummary`/`TeacherStudentCount`/`TodaySnapshot` 类型，字段名（`totalCount`/`custodyCount`/`offCampusOnlyCount`/`byTeacher`/`teacherName`/`studentCount`/`arrivedCount`/`mealCount`/`leaveCount`/`totalCustodyStudentCount`）跟 spec 和兄弟计划（`frontend-api` 计划）里写定的完全一致，没有擅自改名。

**4. Review Focus:** 五条都已经在对应任务里落实为具体测试——(a) 在读规模 0 分母测试（Task 1 Step 1 第三个 `it`）；(b) 今日快照 0 分母测试（Task 2 Step 1 第三个 `it`）；(c) 请假进度条颜色测试，显式断言不是警示色（Task 2 Step 1 第二个 `it`）；(d) `byTeacher` 为空的提示文案测试（Task 1 Step 1 第四个 `it`）；(e) 两张卡片各自的 reject 测试，互不影响（Task 1/2 Step 1 最后一个 `it`，且两个组件在各自文件里各管各的 state，天然不会互相影响——留给"组装"计划在渲染两者都在同一页面时再做一次集成层面的互不阻塞验证）。
