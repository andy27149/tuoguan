# 账单欠费提醒卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-contained `UnpaidBillsCard` React component for the admin overview page that shows every unpaid bill across all months, and its test.

**Architecture:** One component, zero shared state with sibling cards. It owns its own fetch/loading/error lifecycle via `useEffect` + local `useState`, and exposes exactly one prop (`onOpenBilling`) so a parent can wire it to app navigation without the card knowing how navigation works.

**Tech Stack:** React 19, TypeScript, Vitest + Testing Library (existing project conventions).

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md` (section "2. 账单欠费提醒" and its "决策 4" correction note near the top — this card covers ALL months, not just the current one, by deliberate design).

## Global Constraints

- No month filter/parameter anywhere in this component — it always renders everything the backend returns.
- List `key` for unpaid-bill rows must be a composite of `studentId` + `yearMonth`, never `studentId` alone — the same student can legitimately appear more than once (once per unpaid month).
- Money values are always formatted with `.toFixed(2)` and a `¥` prefix.
- This card does not perform navigation itself — it calls the `onOpenBilling` prop.

## Review Focus

- A student with two different unpaid months must render as two distinct rows, not silently collide on React key or get deduplicated.
- `totalAmount` formatting must always show two decimal places (`¥1450.00`, not `¥1450`).
- The empty state (`rows.length === 0`) must still show the "查看账单管理 →" link — a clean institution should still let the admin navigate to billing.
- A rejected fetch must show an error message, not throw or render blank.
- Loading state must not look identical to the empty state (a careless implementation could conflate "no data yet" with "confirmed zero unpaid bills").

---

### Task 1: `UnpaidBillsCard` component and test

**Files:**
- Create: `frontend/src/components/UnpaidBillsCard.tsx`
- Create: `frontend/src/components/UnpaidBillsCard.test.tsx`

**Interfaces:**
- Consumes: `fetchUnpaidBills(): Promise<UnpaidBillSummary>` and `interface UnpaidBillRow { studentId: number; studentName: string; className: string; yearMonth: string; totalAmount: number }` / `interface UnpaidBillSummary { count: number; totalAmount: number; rows: UnpaidBillRow[] }`, both from `frontend/src/api/adminOverview.ts` (written by a sibling plan — assume this file and these exact exports already exist).
- Produces: `export function UnpaidBillsCard({ onOpenBilling }: { onOpenBilling: () => void })` — a parent component (built in a separate "assembly" plan) will render `<UnpaidBillsCard onOpenBilling={...} />` inside the overview grid. No other component depends on this one's internals.

- [ ] **Step 1: Write the failing tests**

```tsx
// frontend/src/components/UnpaidBillsCard.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { UnpaidBillsCard } from './UnpaidBillsCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

const SUMMARY: adminOverviewApi.UnpaidBillSummary = {
  count: 3,
  totalAmount: 2250,
  rows: [
    { studentId: 1, studentName: '李强', className: '托管A班', yearMonth: '2026-09', totalAmount: 800 },
    { studentId: 2, studentName: '王芳', className: '托管B班', yearMonth: '2026-09', totalAmount: 650 },
    { studentId: 2, studentName: '王芳', className: '托管B班', yearMonth: '2026-08', totalAmount: 800 },
  ],
}

describe('UnpaidBillsCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the headline and every row from the fetched summary', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue(SUMMARY)
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(await screen.findByText('3 笔未缴费，共 ¥2250.00')).toBeInTheDocument()
    expect(screen.getByText('李强')).toBeInTheDocument()
    expect(screen.getAllByText('王芳')).toHaveLength(2)
    expect(screen.getByText('托管A班 · 2026-09')).toBeInTheDocument()
    expect(screen.getByText('托管B班 · 2026-09')).toBeInTheDocument()
    expect(screen.getByText('托管B班 · 2026-08')).toBeInTheDocument()
    expect(screen.getByText('¥800.00')).toBeInTheDocument()
    expect(screen.getByText('¥650.00')).toBeInTheDocument()
  })

  it('renders the same student twice as two distinct rows when they owe for two different months', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue(SUMMARY)
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    await screen.findByText('李强')
    // Both of 王芳's rows must be present with their own distinct month label —
    // if the list key collided on studentId alone, React would only keep one.
    expect(screen.getByText('托管B班 · 2026-09')).toBeInTheDocument()
    expect(screen.getByText('托管B班 · 2026-08')).toBeInTheDocument()
  })

  it('shows a loading state that is distinct from the empty state before the fetch resolves', () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockReturnValue(new Promise(() => {})) // never resolves
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(screen.getByText('加载中...')).toBeInTheDocument()
    expect(screen.queryByText('暂无欠费账单')).not.toBeInTheDocument()
  })

  it('shows an empty state but keeps the billing link when there are no unpaid bills', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue({ count: 0, totalAmount: 0, rows: [] })
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(await screen.findByText('暂无欠费账单')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '查看账单管理 →' })).toBeInTheDocument()
  })

  it('shows an error message when the fetch fails', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockRejectedValue(new Error('network error'))
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })

  it('calls onOpenBilling exactly once when the link is clicked', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue(SUMMARY)
    const onOpenBilling = vi.fn()
    render(<UnpaidBillsCard onOpenBilling={onOpenBilling} />)

    await screen.findByText('李强')
    fireEvent.click(screen.getByRole('button', { name: '查看账单管理 →' }))

    await waitFor(() => expect(onOpenBilling).toHaveBeenCalledTimes(1))
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/components/UnpaidBillsCard.test.tsx`
Expected: FAIL — `Cannot find module './UnpaidBillsCard'` (component doesn't exist yet).

- [ ] **Step 3: Write the component**

```tsx
// frontend/src/components/UnpaidBillsCard.tsx
import { useEffect, useState } from 'react'
import { fetchUnpaidBills, type UnpaidBillSummary } from '../api/adminOverview'

interface UnpaidBillsCardProps {
  onOpenBilling: () => void
}

export function UnpaidBillsCard({ onOpenBilling }: UnpaidBillsCardProps) {
  const [summary, setSummary] = useState<UnpaidBillSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchUnpaidBills()
      .then(setSummary)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <div className="flex items-baseline justify-between">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">账单欠费提醒</h2>
        <span className="text-xs text-[#7c7391]">全部未缴费（不限月份）</span>
      </div>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      {!error && !summary && <p className="mt-3 text-sm text-[#7c7391]">加载中...</p>}

      {!error && summary && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-[#241f3d]">
            {summary.count}{' '}
            <span className="text-base font-normal text-[#7c7391]">
              笔未缴费，共 ¥{summary.totalAmount.toFixed(2)}
            </span>
          </p>

          {summary.rows.length === 0 ? (
            <p className="mt-4 text-sm text-[#7c7391]">暂无欠费账单</p>
          ) : (
            <div className="mt-4 divide-y divide-[#f3f0ea]">
              {summary.rows.map((row) => (
                <div key={`${row.studentId}-${row.yearMonth}`} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium text-[#241f3d]">{row.studentName}</p>
                    <p className="text-xs text-[#7c7391]">
                      {row.className} · {row.yearMonth}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-['Sora'] text-sm font-semibold text-[#241f3d]">
                      ¥{row.totalAmount.toFixed(2)}
                    </span>
                    <span className="rounded-full bg-[#fdf1e6] px-2.5 py-1 text-xs font-medium text-[#b7591f]">
                      ● 未缴费
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            onClick={onOpenBilling}
            className="mt-3 text-xs font-medium text-[#6d5bd0] hover:underline"
          >
            查看账单管理 →
          </button>
        </>
      )}
    </section>
  )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/components/UnpaidBillsCard.test.tsx`
Expected: PASS, all 6 tests.

- [ ] **Step 5: Type-check**

Run: `cd frontend && npx tsc -b`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/UnpaidBillsCard.tsx frontend/src/components/UnpaidBillsCard.test.tsx
git commit -m "feat: add unpaid-bills overview card component"
```
