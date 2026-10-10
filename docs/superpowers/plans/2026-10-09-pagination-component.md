# Pagination 组件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个通用、纯展示的分页条组件 `Pagination`，供学生总览页使用，并为后续账单管理页复用打好基础。

**Architecture:** 纯展示组件，不感知任何业务数据（不知道"学生"、"账单"这些概念），只接收页码/总页数/总条数并渲染摘要文字 + 上一页/下一页按钮，通过回调把页码变化上报给调用方。调用方负责自己的筛选/切片逻辑。

**Tech Stack:** React 18 + TypeScript，Vitest + @testing-library/react，Tailwind 工具类（跟 `AdminStudentsModule.tsx` 现有风格一致：`#6d5bd0` 紫色、`#ece7de` 边框、`#5d5480`/`#7c7391` 灰色文字、`rounded-full` 胶囊按钮）。

**Spec:** `docs/superpowers/specs/2026-10-09-学生总览页筛选分页重设计-design.md`（"设计：分页"一节）

## Global Constraints

- 每页固定 20 条，但这个常量由调用方持有、不属于本组件——`Pagination` 组件本身不知道页大小，只认 `page`/`totalPages`/`totalItems` 三个数字。
- 调用方保证传入的 `totalPages` 恒 >= 1（调用方按 `Math.max(1, Math.ceil(...))` 计算），本组件不需要再做防御性的 `totalPages < 1` 处理。

## Review Focus

- 只有 1 页数据时（`totalPages === 1`），上一页和下一页必须都是 disabled——这是最容易漏测的边界，因为大多数人会先测"中间页"和"首尾页"，漏掉"只有一页"这个两头都是边界的特例。
- `page` 等于 `totalPages` 时下一页 disabled，但 `page` 小于 `totalPages` 时下一页必须可点——不能把"接近最后一页"误判成"已经是最后一页"。
- 点击按钮时回调传给调用方的页码是"目标页码"（`page - 1` / `page + 1`），不是一个增减量/事件对象——调用方会直接拿这个值去 `setState`，签名错了会在集成阶段才暴露，不如在这里的单测里钉死。
- 总条数为 0 时（比如筛选结果为空），摘要文字要显示"共 0 条 · 第 1 页 / 共 1 页"而不是除零出错或者显示负数/NaN。
- disabled 按钮被点击不应该触发 `onPageChange`——原生 `disabled` 属性本身会阻止点击事件，但测试要显式验证这一点，不能假设"UI 看起来是灰的"就等于"逻辑上真的不会触发"。

---

### Task 1: 创建 Pagination 组件

**Files:**
- Create: `frontend/src/components/Pagination.tsx`
- Test: `frontend/src/components/Pagination.test.tsx`

**Interfaces:**
- Consumes: 无（不依赖其他任务产出）
- Produces：
  ```ts
  interface PaginationProps {
    page: number
    totalPages: number
    totalItems: number
    onPageChange: (page: number) => void
  }
  export function Pagination(props: PaginationProps): JSX.Element
  ```
  后续任务（`AdminStudentsModule` 集成）会 `import { Pagination } from '../components/Pagination'` 并传入这四个 prop。

- [ ] **Step 1: 写第一批失败测试（摘要文字 + 边界 disabled 状态）**

```tsx
// frontend/src/components/Pagination.test.tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('renders the summary text with total items, current page, and total pages', () => {
    render(<Pagination page={2} totalPages={5} totalItems={93} onPageChange={vi.fn()} />)

    expect(screen.getByText('共 93 条 · 第 2 页 / 共 5 页')).toBeInTheDocument()
  })

  it('renders zero items with a valid single-page summary instead of NaN or negative numbers', () => {
    render(<Pagination page={1} totalPages={1} totalItems={0} onPageChange={vi.fn()} />)

    expect(screen.getByText('共 0 条 · 第 1 页 / 共 1 页')).toBeInTheDocument()
  })

  it('disables both buttons when there is only one page', () => {
    render(<Pagination page={1} totalPages={1} totalItems={5} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
  })

  it('disables the previous button on the first page but keeps next enabled', () => {
    render(<Pagination page={1} totalPages={3} totalItems={50} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).not.toBeDisabled()
  })

  it('disables the next button on the last page but keeps previous enabled', () => {
    render(<Pagination page={3} totalPages={3} totalItems={50} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '上一页' })).not.toBeDisabled()
  })

  it('enables both buttons on a middle page', () => {
    render(<Pagination page={2} totalPages={3} totalItems={50} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '上一页' })).not.toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).not.toBeDisabled()
  })
})
```

- [ ] **Step 2: 运行测试，确认按预期失败**

Run: `cd frontend && npx vitest run src/components/Pagination.test.tsx`
Expected: FAIL — `Cannot find module './Pagination'`（文件还不存在）。

- [ ] **Step 3: 写最小实现让第一批测试通过**

```tsx
// frontend/src/components/Pagination.tsx
interface PaginationProps {
  page: number
  totalPages: number
  totalItems: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalItems, onPageChange }: PaginationProps) {
  return (
    <div className="flex items-center justify-between gap-2 pt-3 text-sm text-[#7c7391]">
      <span>
        共 {totalItems} 条 · 第 {page} 页 / 共 {totalPages} 页
      </span>
      <span className="flex gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 1}
          className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
        >
          上一页
        </button>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages}
          className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
        >
          下一页
        </button>
      </span>
    </div>
  )
}
```

- [ ] **Step 4: 运行测试，确认第一批全部通过**

Run: `cd frontend && npx vitest run src/components/Pagination.test.tsx`
Expected: PASS（6/6）

- [ ] **Step 5: 提交**

```bash
cd frontend && git add src/components/Pagination.tsx src/components/Pagination.test.tsx
git commit -m "feat: 新增通用分页条组件 Pagination"
```

- [ ] **Step 6: 写第二批失败测试（点击回调）**

```tsx
// 追加到 frontend/src/components/Pagination.test.tsx 的 describe 块内
  it('calls onPageChange with the previous page number when clicking 上一页', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={2} totalPages={3} totalItems={50} onPageChange={onPageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '上一页' }))

    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('calls onPageChange with the next page number when clicking 下一页', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={2} totalPages={3} totalItems={50} onPageChange={onPageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(onPageChange).toHaveBeenCalledWith(3)
  })

  it('does not call onPageChange when clicking a disabled button', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={1} totalItems={5} onPageChange={onPageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '上一页' }))
    fireEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(onPageChange).not.toHaveBeenCalled()
  })
```

- [ ] **Step 7: 运行测试，确认这 3 个新测试本身逻辑正确可通过（无需改实现）**

Run: `cd frontend && npx vitest run src/components/Pagination.test.tsx`
Expected: PASS（9/9）—— Step 3 的实现已经包含了正确的回调签名和原生 `disabled` 属性，这一步是确认这批新用例把"回调签名"和"disabled 阻止点击"这两点真正钉死了，不是重新实现。

- [ ] **Step 8: 提交**

```bash
cd frontend && git add src/components/Pagination.test.tsx
git commit -m "test: 补充 Pagination 点击回调与 disabled 阻止点击的用例"
```

## Completion Contract

- [ ] `frontend/src/components/Pagination.tsx` 存在，导出 `Pagination` 组件，签名与上文 Interfaces 一致。
- [ ] `frontend/src/components/Pagination.test.tsx` 的 9 个用例全部通过。
- [ ] `cd frontend && npx tsc --noEmit` 无报错。
- [ ] `cd frontend && npx oxlint src/components/Pagination.tsx` 无报错。
