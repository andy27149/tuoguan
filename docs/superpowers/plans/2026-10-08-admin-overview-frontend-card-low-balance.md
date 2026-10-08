# 课时不足预警卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the self-contained "课时不足预警" (low course-hour balance warning) card component for the admin overview page — it fetches its own data, renders a list of students whose course balance is low, and lets the admin jump straight to that student's recharge modal.

**Architecture:** One React component (`LowBalanceCard`) that owns its own fetch/loading/error state via `useEffect` + `useState`, calling the already-built `fetchLowBalance()` API wrapper. It has zero props and zero knowledge of the other four overview cards — a later, separate plan assembles all five cards into the overview page. Clicking a row opens the existing `AdminCourseStatementModal`, reusing the exact open/close pattern already used in `AdminStudentsModule.tsx`.

**Tech Stack:** React, TypeScript, Vitest + Testing Library, Tailwind utility classes (no new CSS).

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md` (section "1. 课时不足预警"). Visual reference: `docs/mockups/2026-10-08-admin-overview-prototype.html` (card 1 markup).

## Global Constraints

- `frontend/src/api/adminOverview.ts` already exists (built by a sibling plan) exporting `fetchLowBalance(): Promise<LowBalanceRow[]>` and `interface LowBalanceRow { studentId: number; studentName: string; courseId: number; courseName: string; balance: number }` — do not redefine these, import them.
- Balance coloring is a strict two-way branch: `balance <= 0` → red, anything else → amber. The backend only ever sends rows with `balance <= 3`, but the component must not hardcode an upper bound of 3 — just the `<= 0` vs not branch, matching the two-tier convention already used elsewhere in this codebase (e.g. the existing 消课 page's balance badges).
- This component has no props. It is not responsible for grid layout/positioning — a later assembly plan places it inside the overview page's layout.
- Do not mock `AdminCourseStatementModal` in the test — the codebase's established convention (`AdminStudentsModule.test.tsx`) is to let the real modal render and mock the APIs it calls internally (`../api/course`'s `fetchStudentCourseStatement`, `../api/unit`'s `fetchTeachingUnits`), then assert on the modal's real rendered heading text `"课外账户对账单 - {studentName}"`.

## Review Focus

- A `balance` of exactly `0` must render the red badge, not fall through to amber via an off-by-one `< 0` check.
- The empty-array response (`[]`) must render a distinct "no students" message, not look identical to the loading state or silently render nothing.
- Clicking row 2's "去充值" button must open the modal for row 2's student, not row 1's (closure/index bugs are an easy mistake when row handlers are built inline in a `.map()`).
- A rejected fetch must show a visible error message, not an unhandled promise rejection or a blank card.
- Closing the modal must fully unmount it (no leftover "课外账户对账单" text in the DOM) and must not refetch the card's own list (no reason to; the list is unaffected by viewing another modal).

---

### Task 1: `LowBalanceCard` component

**Files:**
- Create: `frontend/src/components/LowBalanceCard.tsx`
- Create: `frontend/src/components/LowBalanceCard.test.tsx`

**Interfaces:**
- Consumes: `fetchLowBalance(): Promise<LowBalanceRow[]>` and `LowBalanceRow` from `frontend/src/api/adminOverview.ts`; `AdminCourseStatementModal` from `frontend/src/components/AdminCourseStatementModal.tsx` with props `{ studentId: number; studentName: string; onClose: () => void }`.
- Produces: `export function LowBalanceCard()` — a zero-prop component, imported by a later assembly plan as `<LowBalanceCard />`.

- [ ] **Step 1: Write the failing tests**

```tsx
// frontend/src/components/LowBalanceCard.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { LowBalanceCard } from './LowBalanceCard'
import * as overviewApi from '../api/adminOverview'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'

vi.mock('../api/adminOverview')
vi.mock('../api/course')
vi.mock('../api/unit')

const ROWS: overviewApi.LowBalanceRow[] = [
  { studentId: 1, studentName: '小红', courseId: 10, courseName: '数学课', balance: 0 },
  { studentId: 2, studentName: '张伟', courseId: 11, courseName: '英语课', balance: 2 },
]

describe('LowBalanceCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(overviewApi.fetchLowBalance).mockResolvedValue(ROWS)
    vi.mocked(courseApi.fetchStudentCourseStatement).mockResolvedValue({
      balances: [],
      recharges: [],
      consumptions: [],
    })
    vi.mocked(unitApi.fetchTeachingUnits).mockResolvedValue([])
  })

  it('renders the headline count and each row with its balance badge', async () => {
    render(<LowBalanceCard />)

    // "2" sits as a bare text node next to the <span> label inside the same <p>,
    // so plain getByText('2') won't match it — assert on the <p>'s full textContent instead.
    const headlineLabel = await screen.findByText('名学生需要续费')
    expect(headlineLabel.closest('p')).toHaveTextContent('2 名学生需要续费')
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getByText('数学课')).toBeInTheDocument()
    expect(screen.getByText('张伟')).toBeInTheDocument()
    expect(screen.getByText('英语课')).toBeInTheDocument()
  })

  it('colors a balance of exactly 0 red and a positive low balance amber', async () => {
    render(<LowBalanceCard />)
    await screen.findByText('小红')

    expect(screen.getByText('● 余额 0 课时')).toHaveClass('bg-[#fee2e2]', 'text-[#b91c1c]')
    expect(screen.getByText('● 余额 2 课时')).toHaveClass('bg-[#fef3c7]', 'text-[#b45309]')
  })

  it('shows an empty-state message when there are no low-balance students, not a blank card', async () => {
    vi.mocked(overviewApi.fetchLowBalance).mockResolvedValue([])
    render(<LowBalanceCard />)

    expect(await screen.findByText('没有需要续费的学生')).toBeInTheDocument()
    expect(screen.queryByText('加载中...')).not.toBeInTheDocument()
  })

  it('shows an error message when the fetch fails', async () => {
    vi.mocked(overviewApi.fetchLowBalance).mockRejectedValue(new Error('network'))
    render(<LowBalanceCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })

  it('opens the recharge modal for the specific row that was clicked, not always the first row', async () => {
    render(<LowBalanceCard />)
    await screen.findByText('张伟')

    fireEvent.click(screen.getAllByRole('button', { name: '去充值' })[1])

    expect(await screen.findByText('课外账户对账单 - 张伟')).toBeInTheDocument()
    await waitFor(() => expect(courseApi.fetchStudentCourseStatement).toHaveBeenCalledWith(2))
  })

  it('closes the recharge modal and removes it from the DOM', async () => {
    render(<LowBalanceCard />)
    await screen.findByText('小红')

    fireEvent.click(screen.getAllByRole('button', { name: '去充值' })[0])
    expect(await screen.findByText('课外账户对账单 - 小红')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))

    expect(screen.queryByText('课外账户对账单 - 小红')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/components/LowBalanceCard.test.tsx`
Expected: FAIL — `LowBalanceCard.tsx` does not exist yet.

- [ ] **Step 3: Write the component**

```tsx
// frontend/src/components/LowBalanceCard.tsx
import { useEffect, useState } from 'react'
import { fetchLowBalance, type LowBalanceRow } from '../api/adminOverview'
import { AdminCourseStatementModal } from './AdminCourseStatementModal'

export function LowBalanceCard() {
  const [rows, setRows] = useState<LowBalanceRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [statementStudent, setStatementStudent] = useState<{ id: number; name: string } | null>(null)

  useEffect(() => {
    fetchLowBalance()
      .then(setRows)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <div className="flex items-baseline justify-between">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">课时不足预警</h2>
        <span className="text-xs text-[#7c7391]">阈值 &lt; 3 课时</span>
      </div>

      {error && <p className="mt-2 text-sm text-[#b91c1c]">{error}</p>}

      {!error && rows === null && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}

      {!error && rows !== null && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-[#241f3d]">
            {rows.length} <span className="text-base font-normal text-[#7c7391]">名学生需要续费</span>
          </p>

          {rows.length === 0 ? (
            <p className="mt-4 text-sm text-[#7c7391]">没有需要续费的学生</p>
          ) : (
            <div className="mt-4 divide-y divide-[#f3f0ea]">
              {rows.map((row) => (
                <div key={`${row.studentId}-${row.courseId}`} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium text-[#241f3d]">{row.studentName}</p>
                    <p className="text-xs text-[#7c7391]">{row.courseName}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={
                        row.balance <= 0
                          ? 'rounded-full bg-[#fee2e2] px-2.5 py-1 text-xs font-medium text-[#b91c1c]'
                          : 'rounded-full bg-[#fef3c7] px-2.5 py-1 text-xs font-medium text-[#b45309]'
                      }
                    >
                      ● 余额 {row.balance} 课时
                    </span>
                    <button
                      type="button"
                      onClick={() => setStatementStudent({ id: row.studentId, name: row.studentName })}
                      className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                    >
                      去充值
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {statementStudent && (
        <AdminCourseStatementModal
          studentId={statementStudent.id}
          studentName={statementStudent.name}
          onClose={() => setStatementStudent(null)}
        />
      )}
    </section>
  )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/components/LowBalanceCard.test.tsx`
Expected: PASS (6 tests)

- [ ] **Step 5: Type-check**

Run: `cd frontend && npx tsc -b`
Expected: no errors

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/LowBalanceCard.tsx frontend/src/components/LowBalanceCard.test.tsx
git commit -m "feat: 新增机构总览页课时不足预警卡片组件"
```
