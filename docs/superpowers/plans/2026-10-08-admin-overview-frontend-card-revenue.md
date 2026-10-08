# 收入快照卡片（RevenueCard）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-contained `RevenueCard` React component for the 机构管理员总览页 that shows last month's custody-tuition billed/collected figures and this month's off-campus-course consumption count — two different time periods, each labeled on its own row, never merged into one card-level date.

**Architecture:** One function component owns its own `useEffect` fetch, loading state, and error state — no props, no coordination with sibling cards (each of the five overview cards on this page is independently self-contained; this plan only touches this one card). It calls a single pre-existing API function and renders two sections from the one response.

**Tech Stack:** React + TypeScript (function component, `useState`/`useEffect`), Vitest + Testing Library for tests, Tailwind utility classes matching the existing admin-page visual conventions.

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md` (section "5. 收入快照（原名"本月收入快照"...)" and the correction note near the top of the file explaining why this card has two time periods instead of one).

## Global Constraints

- The two month values (`tuitionMonth`, `consumptionMonth`) are never merged into one shared card-level date — the card's `<h2>` heading is just `"收入快照"`, nothing else. Each row carries its own inline month label next to that row's own text label.
- `offCampusConsumptionCount` is never formatted as currency anywhere in this component — it is always rendered as a plain integer followed by the unit "次".
- `tuitionMonth`/`consumptionMonth` are already-formatted strings (e.g. `"2026-09"`) coming from the backend — this component displays them as-is and never computes "last month" / "this month" itself.
- All percentage-from-ratio math (the collection-rate bar) must guard the zero-denominator case: when `tuitionBilled` is `0`, the computed percentage must be `0`, never `NaN`, and the rendered `style.width` must never contain the substring `"NaN"`.
- Money values (`tuitionBilled`, `tuitionCollected`) are always formatted with exactly two decimal places via `.toFixed(2)`.

## Review Focus

- `NaN%` leaking into the collection-rate bar's `style` attribute when `tuitionBilled` is `0` — a careless `Math.round((collected / billed) * 100)` with no guard produces `NaN` for `0/0`, and React happily renders `width: NaNpx`-shaped strings into the DOM without erroring, so this needs an explicit assertion, not just visual inspection.
- A maintainer "simplifying" the two-month display back into one shared card-level date by analogy with how simpler single-date cards elsewhere in this feature look — the two-rows-two-months test in this plan exists specifically to catch that regression if it's ever reintroduced.
- Money formatting drifting to 0 or 1 decimal places on just one of the two tuition numbers while the other keeps 2 (an easy copy-paste-and-forget-to-update-both-lines mistake) — both `tuitionBilled` and `tuitionCollected` get their own formatting assertion.
- `offCampusConsumptionCount` accidentally getting a `¥` prefix or decimal formatting applied to it by a maintainer pattern-matching the money row above it in the same component.
- The loading/error states must fully replace the body content (not render alongside stale/zeroed-out numbers) — tested by asserting the money text is absent while `error` is set.

---

## Task 1: `RevenueCard` component + test

**Files:**
- Create: `frontend/src/components/RevenueCard.tsx`
- Create: `frontend/src/components/RevenueCard.test.tsx`

**Interfaces:**
- Consumes: `fetchRevenueSnapshot(): Promise<RevenueSnapshot>` and `interface RevenueSnapshot { tuitionMonth: string; tuitionBilled: number; tuitionCollected: number; consumptionMonth: string; offCampusConsumptionCount: number }`, both already exported from `frontend/src/api/adminOverview.ts` (written by a sibling plan — this plan does not touch that file).
- Produces: `export function RevenueCard()` — a zero-prop component, imported later by the page-assembly plan that renders all five overview cards together. No other file in this plan depends on anything this task produces beyond that one export name.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/components/RevenueCard.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RevenueCard } from './RevenueCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

const SNAPSHOT: adminOverviewApi.RevenueSnapshot = {
  tuitionMonth: '2026-09',
  tuitionBilled: 9200,
  tuitionCollected: 7650,
  consumptionMonth: '2026-10',
  offCampusConsumptionCount: 14,
}

describe('RevenueCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the heading with no month in it, and each row with its own month label', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue(SNAPSHOT)
    render(<RevenueCard />)

    const heading = await screen.findByRole('heading', { name: '收入快照' })
    expect(heading).toBeInTheDocument()
    expect(heading.textContent).toBe('收入快照')

    const tuitionRow = await screen.findByTestId('revenue-tuition-label')
    expect(tuitionRow.textContent).toBe('托管费实收 / 应收（2026-09）')

    const consumptionRow = screen.getByTestId('revenue-consumption-label')
    expect(consumptionRow.textContent).toBe('课外课消课次数（2026-10 至今）')
  })

  it('formats tuition billed and collected with two decimal places', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue({
      ...SNAPSHOT,
      tuitionBilled: 800,
      tuitionCollected: 800,
    })
    render(<RevenueCard />)

    expect(await screen.findByText('¥800.00 / ¥800.00')).toBeInTheDocument()
  })

  it('computes the collection-rate bar width from collected/billed', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue(SNAPSHOT)
    render(<RevenueCard />)

    const bar = await screen.findByTestId('revenue-collection-bar')
    // 7650 / 9200 = 0.8315... -> rounds to 83%
    expect(bar.style.width).toBe('83%')
    expect(await screen.findByText('已收 83%')).toBeInTheDocument()
  })

  it('renders a 0%-width bar, not NaN%, when tuitionBilled is 0', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue({
      ...SNAPSHOT,
      tuitionBilled: 0,
      tuitionCollected: 0,
    })
    render(<RevenueCard />)

    const bar = await screen.findByTestId('revenue-collection-bar')
    expect(bar.style.width).toBe('0%')
    expect(bar.style.width).not.toContain('NaN')
    expect(await screen.findByText('已收 0%')).toBeInTheDocument()
  })

  it('renders the consumption count as a plain integer with 次, never as currency', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue(SNAPSHOT)
    render(<RevenueCard />)

    const label = await screen.findByTestId('revenue-consumption-label')
    const row = label.parentElement as HTMLElement
    expect(row.textContent).toContain('14')
    expect(row.textContent).toContain('次')
    expect(row.textContent).not.toContain('¥')
  })

  it('shows an error message when the fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockRejectedValue(new Error('network'))
    render(<RevenueCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
    expect(screen.queryByText('托管费实收 / 应收')).not.toBeInTheDocument()
  })

  it('shows a loading state before the fetch resolves', () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockReturnValue(new Promise(() => {}))
    render(<RevenueCard />)

    expect(screen.getByText('加载中...')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && npx vitest run src/components/RevenueCard.test.tsx`
Expected: FAIL — `Cannot find module './RevenueCard'` (the component doesn't exist yet).

- [ ] **Step 3: Write the component**

Create `frontend/src/components/RevenueCard.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { fetchRevenueSnapshot, type RevenueSnapshot } from '../api/adminOverview'

export function RevenueCard() {
  const [snapshot, setSnapshot] = useState<RevenueSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchRevenueSnapshot()
      .then(setSnapshot)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">收入快照</h2>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      {!error && !snapshot && <p className="mt-3 text-sm text-[#7c7391]">加载中...</p>}

      {!error && snapshot && (
        <>
          <div className="mt-3">
            <div className="flex items-baseline justify-between">
              <span data-testid="revenue-tuition-label" className="text-sm text-[#5d5480]">
                托管费实收 / 应收
                <span className="ml-1 text-xs text-[#a79fc2]">（{snapshot.tuitionMonth}）</span>
              </span>
              <span className="font-['Sora'] text-sm font-semibold text-[#241f3d]">
                ¥{snapshot.tuitionCollected.toFixed(2)} / ¥{snapshot.tuitionBilled.toFixed(2)}
              </span>
            </div>
            <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-[#f1f0f4]">
              <div
                data-testid="revenue-collection-bar"
                className="h-full rounded-full bg-[#2f9e6e]"
                style={{
                  width: `${
                    snapshot.tuitionBilled > 0
                      ? Math.round((snapshot.tuitionCollected / snapshot.tuitionBilled) * 100)
                      : 0
                  }%`,
                }}
              />
            </div>
            <p className="mt-1 text-xs text-[#a79fc2]">
              已收{' '}
              {snapshot.tuitionBilled > 0
                ? Math.round((snapshot.tuitionCollected / snapshot.tuitionBilled) * 100)
                : 0}
              %
            </p>
          </div>

          <div className="mt-4 flex items-center justify-between border-t border-[#f3f0ea] pt-3">
            <span data-testid="revenue-consumption-label" className="text-sm text-[#5d5480]">
              课外课消课次数
              <span className="ml-1 text-xs text-[#a79fc2]">（{snapshot.consumptionMonth} 至今）</span>
            </span>
            <span className="font-['Sora'] text-lg font-semibold text-[#241f3d]">
              {snapshot.offCampusConsumptionCount} <span className="text-xs font-normal text-[#7c7391]">次</span>
            </span>
          </div>
        </>
      )}
    </section>
  )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run src/components/RevenueCard.test.tsx`
Expected: PASS, all 7 tests.

- [ ] **Step 5: Type-check**

Run: `cd frontend && npx tsc -b`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/RevenueCard.tsx frontend/src/components/RevenueCard.test.tsx
git commit -m "feat: add RevenueCard component for admin overview page"
```
