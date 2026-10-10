# CreateStudentModal 组件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `AdminStudentsModule.tsx` 顶部越来越臃肿的"新建学生"内联表单抽成一个独立的 `CreateStudentModal` 弹窗组件，并把课外课多选从原生 checkbox 改成可点击的胶囊标签（chip）。

**Architecture:** 纯展示+受控表单组件，自己持有表单字段 state（姓名/学籍班/托管班/已选课外课）和提交中/错误提示 state；不直接调用 `courseApi`，而是通过 `onCreate` 回调把"创建"这个动作委托给调用方（调用方负责实际的 API 调用 + 创建成功后的列表刷新），组件自己只负责捕获 `onCreate` 抛出的错误并选择提示文案、以及成功后调用 `onClose`。这样组件不依赖任何具体的 API 模块，方便独立测试。

**Tech Stack:** React 18 + TypeScript，Vitest + @testing-library/react，Tailwind 工具类（跟 `AdminStudentsModule.tsx`/`AdminCourseStatementModal` 现有风格一致：`#6d5bd0` 紫色、`#ece7de` 边框、`#5d5480`/`#7c7391` 灰色文字、`Sora` 字体标题、白色圆角卡片 + 半透明黑色遮罩）。

**Spec:** `docs/superpowers/specs/2026-10-09-学生总览页筛选分页重设计-design.md`（"设计：新建学生弹窗"一节），静态预览 `docs/mockups/admin-create-student-modal-redesign.html`

## Global Constraints

- 组件内部**不**import `courseApi`——所有实际的创建逻辑都通过 `onCreate` prop 由调用方提供，组件只负责表单 UI、校验（姓名必填）、loading/error 状态展示。
- 课外课多选用可点击的胶囊标签（`<button type="button" aria-pressed={...}>`），不用原生 `<input type="checkbox">`——这是本次重设计相对原表单的唯一交互性变化，其余字段（姓名/学籍班/托管班下拉）原样保留。
- 字段校验、接口参数顺序必须跟 `AdminStudentsModule.tsx` 当前的 `handleCreateStudent` 完全一致：`onCreate(trimmedName, schoolClassName.trim() || null, teachingUnitId ? Number(teachingUnitId) : null, courseIds)`——后续 Task（`AdminStudentsModule` 集成）会直接把现有的 `courseApi.createAdminStudent` 调用包一层传进来，参数顺序错了会在集成阶段才暴露。

## Review Focus

- 姓名为空（或只有空白字符）时创建按钮必须 disabled，且即使强行触发 submit 事件也不应该调用 `onCreate`——原表单用 `disabled` 属性 + `e.preventDefault()` 双重保险，这里容易漏测"按钮 disabled"和"逻辑上真的不触发"这两层中的一层。
- `onCreate` 失败时弹窗不能关闭、要展示错误文案且允许用户重试——容易漏测的是"失败后 `onClose` 一定不能被调用"这个反向断言，只测"显示了错误文案"不够。
- `onCreate` 的 404/400 状态码要展示"所选托管班不可用，请刷新后重试"，其他任何错误（包括非 `ApiError` 的普通 `Error`）要展示通用的"创建失败，请重试"——这是从原表单原样搬过来的分支逻辑，容易在重构时被简化成只有一种错误文案。
- 课外课列表为空（`offCampusCourses.length === 0`）时，整个课外课选择区域不渲染（不是渲染一个空的容器）——原表单用 `offCampusCourses.length > 0 && (...)` 这个短路判断，重构时容易被误写成"渲染容器但列表为空"。
- 提交中（`creating === true`）时创建按钮必须 disabled，防止用户重复点击导致重复提交——原表单已经有这个行为，重写成弹窗时容易被遗漏。

---

### Task 1: 创建 CreateStudentModal 组件

**Files:**
- Create: `frontend/src/components/CreateStudentModal.tsx`
- Test: `frontend/src/components/CreateStudentModal.test.tsx`

**Interfaces:**
- Consumes: `TeachingUnit` 类型，`import type { TeachingUnit } from '../api/unit'`（字段：`id: number; name: string; ...`，本组件只用到 `id`/`name`）；`ApiError` 类，`import { ApiError } from '../api/client'`（构造签名 `new ApiError(status: number, message: string)`，组件内部通过 `err instanceof ApiError && (err.status === 404 || err.status === 400)` 判断分支文案）。
- Produces：
  ```ts
  interface CreateStudentModalProps {
    classRooms: TeachingUnit[]
    offCampusCourses: TeachingUnit[]
    onCreate: (
      name: string,
      schoolClassName: string | null,
      teachingUnitId: number | null,
      courseIds: number[],
    ) => Promise<void>
    onClose: () => void
  }
  export function CreateStudentModal(props: CreateStudentModalProps): JSX.Element
  ```
  后续任务（`AdminStudentsModule` 集成）会 `import { CreateStudentModal } from '../components/CreateStudentModal'`，条件渲染它，并传入一个包装了 `courseApi.createAdminStudent` + `loadStudents()` 的 `onCreate` 函数。

- [ ] **Step 1: 写第一批失败测试（渲染字段 + 胶囊标签切换 + 空列表不渲染 + 关闭按钮）**

```tsx
// frontend/src/components/CreateStudentModal.test.tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { CreateStudentModal } from './CreateStudentModal'
import type { TeachingUnit } from '../api/unit'

function unit(id: number, name: string): TeachingUnit {
  return {
    id,
    name,
    billingMode: 'LESSON_COUNT',
    teacherId: 900,
    teacherName: '王老师',
    teacherPhone: '13900000000',
    lessonDurationMinutes: 60,
    pricePerLesson: 50,
    active: true,
  }
}

const CLASS_ROOMS: TeachingUnit[] = [unit(1, '托管A班')]
const COURSES: TeachingUnit[] = [unit(10, '书法课'), unit(11, '围棋课')]

function setup(overrides: Partial<React.ComponentProps<typeof CreateStudentModal>> = {}) {
  const onCreate = vi.fn().mockResolvedValue(undefined)
  const onClose = vi.fn()
  render(
    <CreateStudentModal
      classRooms={CLASS_ROOMS}
      offCampusCourses={COURSES}
      onCreate={onCreate}
      onClose={onClose}
      {...overrides}
    />,
  )
  return { onCreate, onClose }
}

describe('CreateStudentModal', () => {
  it('renders the name, school class, class room, and course fields', () => {
    setup()

    expect(screen.getByLabelText('姓名')).toBeInTheDocument()
    expect(screen.getByLabelText('学籍班（选填）')).toBeInTheDocument()
    expect(screen.getByLabelText('托管班（选填）')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '书法课' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '围棋课' })).toBeInTheDocument()
  })

  it('does not render the course section at all when there are no off-campus courses', () => {
    setup({ offCampusCourses: [] })

    expect(screen.queryByText('课外课（可多选）')).not.toBeInTheDocument()
  })

  it('disables the create button until a name is entered', () => {
    setup()

    expect(screen.getByRole('button', { name: '创建' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    expect(screen.getByRole('button', { name: '创建' })).not.toBeDisabled()
  })

  it('toggles a course chip selected state on click and updates the selected count', () => {
    setup()

    expect(screen.getByText('已选 0 门')).toBeInTheDocument()
    const chip = screen.getByRole('button', { name: '书法课' })
    expect(chip).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(chip)

    expect(chip).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('已选 1 门')).toBeInTheDocument()

    fireEvent.click(chip)

    expect(chip).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText('已选 0 门')).toBeInTheDocument()
  })

  it('calls onClose when clicking the close button or the cancel button', () => {
    const { onClose } = setup()

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))
    expect(onClose).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '取消' }))
    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
```

- [ ] **Step 2: 运行测试，确认按预期失败**

Run: `cd frontend && npx vitest run src/components/CreateStudentModal.test.tsx`
Expected: FAIL — `Cannot find module './CreateStudentModal'`（文件还不存在）。

- [ ] **Step 3: 写最小实现让第一批测试通过**

```tsx
// frontend/src/components/CreateStudentModal.tsx
import { useState, type FormEvent } from 'react'
import type { TeachingUnit } from '../api/unit'
import { ApiError } from '../api/client'

interface CreateStudentModalProps {
  classRooms: TeachingUnit[]
  offCampusCourses: TeachingUnit[]
  onCreate: (
    name: string,
    schoolClassName: string | null,
    teachingUnitId: number | null,
    courseIds: number[],
  ) => Promise<void>
  onClose: () => void
}

export function CreateStudentModal({ classRooms, offCampusCourses, onCreate, onClose }: CreateStudentModalProps) {
  const [name, setName] = useState('')
  const [schoolClassName, setSchoolClassName] = useState('')
  const [teachingUnitId, setTeachingUnitId] = useState('')
  const [courseIds, setCourseIds] = useState<number[]>([])
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  function toggleCourseId(courseId: number) {
    setCourseIds((prev) => (prev.includes(courseId) ? prev.filter((id) => id !== courseId) : [...prev, courseId]))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmedName = name.trim()
    if (!trimmedName) return
    setCreating(true)
    setCreateError(null)
    try {
      await onCreate(trimmedName, schoolClassName.trim() || null, teachingUnitId ? Number(teachingUnitId) : null, courseIds)
      onClose()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 404 || err.status === 400)) {
        setCreateError('所选托管班不可用，请刷新后重试')
      } else {
        setCreateError('创建失败，请重试')
      }
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="新建学生"
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-2xl border border-[#ece7de] bg-white shadow-[0_20px_50px_rgba(36,31,61,0.25)]"
      >
        <div className="flex items-center justify-between border-b border-[#ece7de] px-5 py-4">
          <h2 className="font-['Sora'] text-base font-bold text-[#241f3d]">新建学生</h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="rounded-lg px-1.5 py-0.5 text-xl leading-none text-[#7c7391] hover:bg-[#faf7ff] hover:text-[#241f3d]"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5 px-5 py-4">
          <div className="flex gap-3">
            <label className="flex-1 text-xs font-semibold text-[#5d5480]">
              姓名
              <input
                placeholder="姓名"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[#ece7de] px-2.5 py-2 text-sm text-[#241f3d]"
              />
            </label>
            <label className="flex-1 text-xs font-semibold text-[#5d5480]">
              学籍班（选填）
              <input
                placeholder="学籍班"
                value={schoolClassName}
                onChange={(e) => setSchoolClassName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[#ece7de] px-2.5 py-2 text-sm text-[#241f3d]"
              />
            </label>
          </div>

          <label className="text-xs font-semibold text-[#5d5480]">
            托管班（选填）
            <select
              value={teachingUnitId}
              onChange={(e) => setTeachingUnitId(e.target.value)}
              className="mt-1 w-full rounded-lg border border-[#ece7de] px-2.5 py-2 text-sm text-[#241f3d]"
            >
              <option value="">纯课外课学生</option>
              {classRooms.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>

          {offCampusCourses.length > 0 && (
            <div>
              <div className="mb-1.5 flex items-baseline justify-between">
                <span className="text-xs font-semibold text-[#5d5480]">课外课（可多选）</span>
                <span className="text-[11px] text-[#7c7391]">已选 {courseIds.length} 门</span>
              </div>
              <div className="max-h-[150px] overflow-y-auto rounded-xl border border-[#ece7de] bg-[#faf9f6] p-2.5">
                <div className="flex flex-wrap gap-1.5">
                  {offCampusCourses.map((c) => {
                    const selected = courseIds.includes(c.id)
                    return (
                      <button
                        key={c.id}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => toggleCourseId(c.id)}
                        className={
                          selected
                            ? 'rounded-full border border-[#6d5bd0] bg-[#6d5bd0] px-3 py-1.5 text-xs font-semibold text-white'
                            : 'rounded-full border border-[#ece7de] bg-white px-3 py-1.5 text-xs font-semibold text-[#5d5480] hover:bg-[#faf7ff]'
                        }
                      >
                        {c.name}
                      </button>
                    )
                  })}
                </div>
              </div>
            </div>
          )}

          {createError && (
            <p role="alert" className="text-sm text-[#b7591f]">
              {createError}
            </p>
          )}

          <div className="flex justify-end gap-2 border-t border-[#ece7de] pt-3.5">
            <button
              type="button"
              onClick={onClose}
              disabled={creating}
              className="rounded-full border border-[#ece7de] px-4 py-1.5 text-sm font-medium text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={creating || !name.trim()}
              className="rounded-full bg-[#6d5bd0] px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              创建
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: 运行测试，确认第一批全部通过**

Run: `cd frontend && npx vitest run src/components/CreateStudentModal.test.tsx`
Expected: PASS（5/5）

- [ ] **Step 5: 提交**

```bash
cd frontend && git add src/components/CreateStudentModal.tsx src/components/CreateStudentModal.test.tsx
git commit -m "feat: 新增新建学生弹窗组件 CreateStudentModal，课外课改为胶囊标签多选"
```

- [ ] **Step 6: 写第二批失败测试（提交参数、成功关闭、错误分支、提交中禁用）**

```tsx
// 追加到 frontend/src/components/CreateStudentModal.test.tsx 顶部 import 区
import { ApiError } from '../api/client'

// 追加到 describe('CreateStudentModal', () => { ... }) 块内
  it('calls onCreate with trimmed name, nullable fields, and selected course ids, then closes on success', async () => {
    const { onCreate, onClose } = setup()

    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '  小明  ' } })
    fireEvent.change(screen.getByLabelText('学籍班（选填）'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('托管班（选填）'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: '书法课' }))

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(onCreate).toHaveBeenCalledWith('小明', null, 1, [10]))
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
  })

  it('shows a friendly message and does not close when onCreate rejects with a 404/400 ApiError', async () => {
    const onCreate = vi.fn().mockRejectedValue(new ApiError(404, 'not found'))
    const { onClose } = setup({ onCreate })
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('所选托管班不可用，请刷新后重试')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a generic failure message and does not close for any other error', async () => {
    const onCreate = vi.fn().mockRejectedValue(new Error('network down'))
    const { onClose } = setup({ onCreate })
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('创建失败，请重试')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('disables the create button while the submission is in flight', async () => {
    let resolveCreate: () => void = () => {}
    const onCreate = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveCreate = resolve
        }),
    )
    setup({ onCreate })
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(screen.getByRole('button', { name: '创建' })).toBeDisabled()
    resolveCreate()
    await waitFor(() => expect(onCreate).toHaveBeenCalled())
  })
```

Also update the `render`/`waitFor` import line at the top of the test file to include `waitFor`:

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
```

- [ ] **Step 7: 运行测试，确认第二批全部通过**

Run: `cd frontend && npx vitest run src/components/CreateStudentModal.test.tsx`
Expected: PASS（9/9）—— Step 3 的实现已经包含了正确的参数顺序、`try/catch`分支、`finally` 里的 `setCreating(false)`，这一步是确认这批新用例把"提交参数"、"成功关闭"、"两种错误分支"、"提交中禁用"这几点真正钉死了，不是重新实现。如果有任何一个断言失败，说明 Step 3 的实现跟 Global Constraints 里约定的参数顺序或分支逻辑有偏差，需要修正实现（不是修改测试去迁就错误实现）。

- [ ] **Step 8: 提交**

```bash
cd frontend && git add src/components/CreateStudentModal.test.tsx
git commit -m "test: 补充 CreateStudentModal 提交参数、成功关闭与错误分支的用例"
```

## Completion Contract

- [ ] `frontend/src/components/CreateStudentModal.tsx` 存在，导出 `CreateStudentModal` 组件，签名与上文 Interfaces 一致，且不 import `courseApi`。
- [ ] `frontend/src/components/CreateStudentModal.test.tsx` 的 9 个用例全部通过。
- [ ] `cd frontend && npx tsc --noEmit` 无报错。
- [ ] `cd frontend && npx oxlint src/components/CreateStudentModal.tsx` 无报错。
