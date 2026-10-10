# 学生总览页筛选/分页/弹窗集成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **前置依赖**：本计划依赖另外两个计划已经完成并合并——`docs/superpowers/plans/2026-10-09-pagination-component.md`（产出 `Pagination` 组件）、`docs/superpowers/plans/2026-10-09-create-student-modal.md`（产出 `CreateStudentModal` 组件）。开始本计划前先确认 `frontend/src/components/Pagination.tsx` 和 `frontend/src/components/CreateStudentModal.tsx` 已存在。

**Goal:** 把 `AdminStudentsModule.tsx` 改造成：新建学生走独立弹窗、顶部有身份/托管教师/姓名三个筛选条件、表格分页展示（每页 20 条），且筛选和分页都是对已拉取全量数据的纯前端派生计算——编辑/新建/停用学生后无需额外代码，筛选和分页会自动对最新数据重新生效。

**Architecture:** 三个独立的改动分层叠加在同一个文件上：(1) 把内联的新建学生表单换成"+ 新建学生"按钮 + 条件渲染的 `CreateStudentModal`；(2) 新增三个筛选 state + 一个按"且"逻辑过滤的 `filteredStudents` 派生值，表格数据源从 `students` 切到 `filteredStudents`；(3) 新增分页 state + 在 `filteredStudents` 之上再切片出 `pageItems`，表格数据源最终切到 `pageItems`，并在表格下方渲染 `Pagination` 组件。三层都不改动已有的编辑/停用/启用逻辑和后端接口调用。

**Tech Stack:** React 18 + TypeScript，Vitest + @testing-library/react，Tailwind 工具类（沿用本文件已有风格）。

**Spec:** `docs/superpowers/specs/2026-10-09-学生总览页筛选分页重设计-design.md`

## Global Constraints

- 每页固定 20 条（`PAGE_SIZE = 20`），不加页大小选择器。
- 筛选和分页都是纯前端——不改动 `GET /api/admin/students`、不加任何查询参数、不碰 `AdminStudentController`/`AdminStudentService`/DAO 层。
- 筛选条件变化时（身份/托管教师/姓名任意一个），立即把当前页重置为第 1 页。
- 分页的"收缩"行为用 `Math.min(currentPage, totalPages)` 实现——收缩目标是"新的最后一页"，不是无条件回到第 1 页（两者只有在 `totalPages` 收缩到恰好 1 时才会重合）。
- 托管教师下拉的选项来自**全量** `students` 列表（不是 `filteredStudents`），避免切换身份筛选导致教师选项列表跟着抖动。

## Review Focus

- **筛选后页码收缩到新的最后一页**：在筛选结果有 3 页时翻到第 3 页，之后一次数据刷新（比如编辑保存后重新拉取）导致筛选结果只剩 2 页——断言自动收缩到第 2 页，不是停留在第 3 页显示空白，也不是无条件跳回第 1 页。
- **编辑导致学生从当前筛选结果消失**：身份筛选="纯课外"时，如果某个学生被编辑成托管班学生，保存后他应该从当前筛选列表里消失，且不报错、不崩溃。
- **矛盾筛选组合产生空结果而不是报错**：身份="纯课外" + 托管教师=某个具体老师（纯课外学生的 `teacherName` 恒为 null，这个组合必然是空结果）——断言列表为空、页面正常渲染、不抛异常。
- **修改筛选条件时页码重置为第 1 页**：翻到非第 1 页之后修改任意一个筛选条件，断言分页回到第 1 页——这是三个筛选条件共享的统一行为，不能只测其中一个筛选项触发了重置就认为其余两个也会触发。
- **教师下拉选项不随筛选抖动**：把身份筛选切到"纯课外"（此时表格里看不到任何托管教师），断言托管教师下拉里仍然能看到全量数据里存在的教师姓名选项——选项来源必须是全量 `students`，不是当前筛选结果。

---

### Task 1: 新建学生表单抽成弹窗

**Files:**
- Modify: `frontend/src/pages/AdminStudentsModule.tsx`
- Modify: `frontend/src/pages/AdminStudentsModule.test.tsx`

**Interfaces:**
- Consumes: `CreateStudentModal`（`import { CreateStudentModal } from '../components/CreateStudentModal'`），props 为 `{ classRooms: TeachingUnit[]; offCampusCourses: TeachingUnit[]; onCreate: (name, schoolClassName, teachingUnitId, courseIds) => Promise<void>; onClose: () => void }`（见 `2026-10-09-create-student-modal.md` 的 Interfaces 定义）。
- Produces: 本任务完成后，`AdminStudentsModule.tsx` 顶部的"新建学生"卡片被替换成一个只有标题+按钮的卡片；Task 2 会在这张卡片和下面的表格卡片之间插入筛选工具条，Task 3 会在表格卡片内部加入分页状态——两者都不依赖本任务引入的任何新函数签名，只依赖"新建表单已经不在页面主体里内联渲染"这个事实。

- [ ] **Step 1: 修改测试文件——把原本直接操作内联表单的用例改成"先点开弹窗再操作"**

把 `frontend/src/pages/AdminStudentsModule.test.tsx` 里以下 5 个已有测试（函数名保持不变，用 `it('...')` 原有的描述字符串定位）整体替换为：

```tsx
  it('opens the CreateStudentModal when clicking + 新建学生, and closing it removes the modal from the page', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    expect(screen.queryByRole('dialog', { name: '新建学生' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '+ 新建学生' }))
    expect(screen.getByRole('dialog', { name: '新建学生' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))
    expect(screen.queryByRole('dialog', { name: '新建学生' })).not.toBeInTheDocument()
  })

  it('creates a pure off-campus student when no teaching unit is picked', async () => {
    vi.mocked(courseApi.createAdminStudent).mockResolvedValue({
      id: 3,
      name: '小刚',
      schoolClassName: null,
      classRoomId: null,
      classRoomName: null,
      offCampusOnly: true,
      enrolled: true,
      teacherName: null,
      enrolledCourseNames: [],
      enrolledCourseIds: [],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '+ 新建学生' }))
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小刚' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小刚', null, null, []))
    await waitFor(() => expect(courseApi.fetchAdminStudents).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('dialog', { name: '新建学生' })).not.toBeInTheDocument()
  })

  it('creates a student attached to a selected teaching unit', async () => {
    vi.mocked(courseApi.createAdminStudent).mockResolvedValue({
      id: 4,
      name: '小芳',
      schoolClassName: '三年级一班',
      classRoomId: 20,
      classRoomName: '托管一班',
      offCampusOnly: false,
      enrolled: true,
      teacherName: '王老师',
      enrolledCourseNames: [],
      enrolledCourseIds: [],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '+ 新建学生' }))
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小芳' } })
    fireEvent.change(screen.getByLabelText('学籍班（选填）'), { target: { value: '三年级一班' } })
    fireEvent.change(screen.getByLabelText('托管班（选填）'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小芳', '三年级一班', 20, []),
    )
  })

  it('creates a student enrolled in the selected off-campus courses', async () => {
    vi.mocked(courseApi.createAdminStudent).mockResolvedValue({
      id: 5,
      name: '小华',
      schoolClassName: null,
      classRoomId: null,
      classRoomName: null,
      offCampusOnly: true,
      enrolled: true,
      teacherName: null,
      enrolledCourseNames: ['书法课'],
      enrolledCourseIds: [30],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '+ 新建学生' }))
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小华' } })
    fireEvent.click(screen.getByRole('button', { name: '书法课' }))
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小华', null, null, [30]),
    )
  })

  it('shows an error inside the modal when student creation fails, and keeps the modal open', async () => {
    vi.mocked(courseApi.createAdminStudent).mockRejectedValue(new Error('boom'))
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '+ 新建学生' }))
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小刚' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('创建失败，请重试')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: '新建学生' })).toBeInTheDocument()
  })
```

- [ ] **Step 2: 运行测试，确认这批测试按预期失败**

Run: `cd frontend && npx vitest run src/pages/AdminStudentsModule.test.tsx`
Expected: FAIL——找不到 `+ 新建学生` 按钮（内联表单还在，还没有改造）。

- [ ] **Step 3: 实现——移除内联表单，接入 CreateStudentModal**

在 `frontend/src/pages/AdminStudentsModule.tsx` 做以下修改：

1. 顶部 import 增加 `CreateStudentModal`，并且把 `useState` 的 import 行里的 `type FormEvent` 去掉——本任务删除后，文件里不再有任何函数使用 `FormEvent` 类型（原来唯一的使用者就是即将被删除的 `handleCreateStudent(e: FormEvent)`），留着这个 import 会在 `tsc --noEmit`/`oxlint` 里报"未使用的导入"：
```tsx
import { useEffect, useState } from 'react'
import { CreateStudentModal } from '../components/CreateStudentModal'
```

2. 删除这些 state（原第 16-21 行）：`newName`/`newSchoolClassName`/`newTeachingUnitId`/`newCourseIds`/`creating`/`createError`。新增：
```tsx
const [showCreateModal, setShowCreateModal] = useState(false)
```

3. 删除原 `handleCreateStudent`（原第 63-90 行，签名为 `(e: FormEvent) => ...`），替换为：
```tsx
async function handleCreateStudent(
  name: string,
  schoolClassName: string | null,
  teachingUnitId: number | null,
  courseIds: number[],
) {
  await courseApi.createAdminStudent(name, schoolClassName, teachingUnitId, courseIds)
  loadStudents()
}
```
注意：这里**不**包 try/catch——错误分支的判断和展示全部交给 `CreateStudentModal` 内部处理，这里只负责"调用接口 + 成功后刷新列表"，失败时让异常原样往上抛给调用方（也就是 `CreateStudentModal`）捕获。

4. 把原来整张"新建学生"卡片（原第 178-246 行，含 `<form onSubmit={handleCreateStudent}>...</form>`）替换为：
```tsx
<div className="flex items-center justify-between rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
  <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">学生管理</h2>
  <button
    type="button"
    onClick={() => setShowCreateModal(true)}
    className="rounded-full bg-[#6d5bd0] px-4 py-1.5 text-sm font-medium text-white"
  >
    + 新建学生
  </button>
</div>
```

5. 在文件末尾（跟 `statementStudent`/`confirmingDeactivate` 的条件渲染并列，放在它们前面即可）增加：
```tsx
{showCreateModal && (
  <CreateStudentModal
    classRooms={classRooms}
    offCampusCourses={offCampusCourses}
    onCreate={handleCreateStudent}
    onClose={() => setShowCreateModal(false)}
  />
)}
```

`toggleCourseId` 辅助函数、`newCourseIds`/`editingCourseIds` 里的 `editingCourseIds` 部分（编辑行用的课外课 checkbox）**不要动**——那是编辑行自己的 state，跟新建表单无关，本任务只处理新建表单。

- [ ] **Step 4: 运行测试，确认全部通过**

Run: `cd frontend && npx vitest run src/pages/AdminStudentsModule.test.tsx`
Expected: PASS（全部用例，包括本任务改写的 6 个 + 之前未受影响的其余用例）

- [ ] **Step 5: 提交**

```bash
cd frontend && git add src/pages/AdminStudentsModule.tsx src/pages/AdminStudentsModule.test.tsx
git commit -m "refactor: 学生总览页新建学生表单改为独立弹窗 CreateStudentModal"
```

---

### Task 2: 新增筛选工具条（身份/托管教师/姓名）

**Files:**
- Modify: `frontend/src/pages/AdminStudentsModule.tsx`
- Modify: `frontend/src/pages/AdminStudentsModule.test.tsx`

**Interfaces:**
- Consumes: Task 1 产出的文件结构（"学生管理"卡片和表格卡片之间有空间插入新的筛选工具条卡片）。
- Produces: `filteredStudents`（`useMemo` 派生值，类型 `courseApi.AdminStudent[]`）——Task 3 会在这个数组基础上再做分页切片，不会重新实现筛选逻辑。

- [ ] **Step 1: 写失败测试（筛选行为 + 教师下拉不抖动）**

在 `frontend/src/pages/AdminStudentsModule.test.tsx` 的 `describe('AdminStudentsModule', () => { ... })` 块内追加：

```tsx
  it('filters students by identity (全部/托管/纯课外)', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('身份筛选'), { target: { value: 'OFF_CAMPUS_ONLY' } })

    expect(screen.queryByText('小明')).not.toBeInTheDocument()
    expect(screen.queryByText('小刚')).not.toBeInTheDocument()
    expect(screen.getByText('小红')).toBeInTheDocument()
  })

  it('filters students by custody teacher', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('托管教师筛选'), { target: { value: '王老师' } })

    expect(screen.getByText('小明')).toBeInTheDocument()
    expect(screen.getByText('小刚')).toBeInTheDocument()
    expect(screen.queryByText('小红')).not.toBeInTheDocument()
  })

  it('filters students by fuzzy, case-insensitive name search', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('姓名搜索'), { target: { value: '明' } })

    expect(screen.getByText('小明')).toBeInTheDocument()
    expect(screen.queryByText('小红')).not.toBeInTheDocument()
    expect(screen.queryByText('小刚')).not.toBeInTheDocument()
  })

  it('combines all three filters with AND semantics', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('身份筛选'), { target: { value: 'CUSTODY' } })
    fireEvent.change(screen.getByLabelText('托管教师筛选'), { target: { value: '王老师' } })
    fireEvent.change(screen.getByLabelText('姓名搜索'), { target: { value: '刚' } })

    expect(screen.queryByText('小明')).not.toBeInTheDocument()
    expect(screen.getByText('小刚')).toBeInTheDocument()
  })

  it('shows an empty, non-crashing result for a contradictory filter combination', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    // 小红是纯课外学生，teacherName 恒为 null，这个组合必然匹配不到任何人。
    fireEvent.change(screen.getByLabelText('身份筛选'), { target: { value: 'OFF_CAMPUS_ONLY' } })
    fireEvent.change(screen.getByLabelText('托管教师筛选'), { target: { value: '王老师' } })

    expect(screen.queryByText('小明')).not.toBeInTheDocument()
    expect(screen.queryByText('小红')).not.toBeInTheDocument()
    expect(screen.queryByText('小刚')).not.toBeInTheDocument()
    expect(screen.getByText('暂无学生')).toBeInTheDocument()
  })

  it('derives the custody teacher dropdown options from the full student list, not the filtered subset', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('身份筛选'), { target: { value: 'OFF_CAMPUS_ONLY' } })

    const teacherSelect = screen.getByLabelText('托管教师筛选') as HTMLSelectElement
    const optionLabels = Array.from(teacherSelect.options).map((o) => o.textContent)
    expect(optionLabels).toContain('王老师')
  })

  it('removes a student from the current filtered view after an edit changes them out of the active identity filter', async () => {
    // 小红原本是纯课外学生；编辑后把她分到托管一班，身份变成托管——在"身份=纯课外"
    // 筛选下应该从列表里消失，而不是继续显示一个已经不符合筛选条件的学生。
    const updatedStudents = STUDENTS.map((s) =>
      s.id === 2
        ? { ...s, classRoomId: 20, classRoomName: '托管一班', offCampusOnly: false, teacherName: '王老师' }
        : s,
    )
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValueOnce(STUDENTS).mockResolvedValueOnce(updatedStudents)
    vi.mocked(courseApi.updateAdminStudent).mockResolvedValue(updatedStudents[1])
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('身份筛选'), { target: { value: 'OFF_CAMPUS_ONLY' } })
    expect(screen.getByText('小红')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '编辑小红' }))
    fireEvent.change(screen.getByLabelText('托管班2'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(courseApi.fetchAdminStudents).toHaveBeenCalledTimes(2))
    expect(screen.queryByText('小红')).not.toBeInTheDocument()
    expect(screen.getByText('暂无学生')).toBeInTheDocument()
  })
```

- [ ] **Step 2: 运行测试，确认按预期失败**

Run: `cd frontend && npx vitest run src/pages/AdminStudentsModule.test.tsx`
Expected: FAIL——找不到 `身份筛选`/`托管教师筛选`/`姓名搜索` 这几个 label（筛选工具条还不存在）。

- [ ] **Step 3: 实现——新增筛选 state、派生值与工具条 UI**

在 `frontend/src/pages/AdminStudentsModule.tsx`：

1. 顶部 import 补上 `useMemo`（Task 1 已经把这一行改成不含 `FormEvent` 的版本，这里只是在那基础上加 `useMemo`）：
```tsx
import { useEffect, useMemo, useState } from 'react'
```

2. 在现有 state 声明区域（`showCreateModal` 附近）新增：
```tsx
type IdentityFilter = 'ALL' | 'CUSTODY' | 'OFF_CAMPUS_ONLY'

const [identityFilter, setIdentityFilter] = useState<IdentityFilter>('ALL')
const [teacherFilter, setTeacherFilter] = useState('')
const [nameQuery, setNameQuery] = useState('')
```

3. 在 `loadStudents`/`useEffect` 之后、`return` 之前新增两个派生值：
```tsx
const teacherOptions = useMemo(() => {
  const names = new Set<string>()
  students.forEach((s) => {
    if (s.teacherName) names.add(s.teacherName)
  })
  return Array.from(names).sort((a, b) => a.localeCompare(b))
}, [students])

const filteredStudents = useMemo(() => {
  const query = nameQuery.trim().toLowerCase()
  return students.filter((s) => {
    if (identityFilter === 'CUSTODY' && s.offCampusOnly) return false
    if (identityFilter === 'OFF_CAMPUS_ONLY' && !s.offCampusOnly) return false
    if (teacherFilter && s.teacherName !== teacherFilter) return false
    if (query && !s.name.toLowerCase().includes(query)) return false
    return true
  })
}, [students, identityFilter, teacherFilter, nameQuery])
```

4. 在"学生管理"卡片（Task 1 产出）和表格卡片之间插入筛选工具条卡片：
```tsx
<div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
  <div className="flex flex-wrap items-end gap-3">
    <label className="text-xs font-semibold text-[#5d5480]">
      身份
      <select
        aria-label="身份筛选"
        value={identityFilter}
        onChange={(e) => setIdentityFilter(e.target.value as IdentityFilter)}
        className="mt-1 block rounded-lg border border-[#ece7de] px-2.5 py-1.5 text-sm text-[#241f3d]"
      >
        <option value="ALL">全部</option>
        <option value="CUSTODY">托管</option>
        <option value="OFF_CAMPUS_ONLY">纯课外</option>
      </select>
    </label>
    <label className="text-xs font-semibold text-[#5d5480]">
      托管教师
      <select
        aria-label="托管教师筛选"
        value={teacherFilter}
        onChange={(e) => setTeacherFilter(e.target.value)}
        className="mt-1 block rounded-lg border border-[#ece7de] px-2.5 py-1.5 text-sm text-[#241f3d]"
      >
        <option value="">全部</option>
        {teacherOptions.map((t) => (
          <option key={t} value={t}>
            {t}
          </option>
        ))}
      </select>
    </label>
    <label className="text-xs font-semibold text-[#5d5480]">
      姓名
      <input
        aria-label="姓名搜索"
        placeholder="搜索姓名"
        value={nameQuery}
        onChange={(e) => setNameQuery(e.target.value)}
        className="mt-1 block rounded-lg border border-[#ece7de] px-2.5 py-1.5 text-sm text-[#241f3d]"
      />
    </label>
  </div>
</div>
```

5. 表格卡片的标题和数据源从 `students` 切到 `filteredStudents`：把 `学生总览（{students.length}）` 改成 `学生总览（{filteredStudents.length}）`；把 `{students.map((student) => {` 改成 `{filteredStudents.map((student) => {`；空状态判断 `{students.length === 0 && (` 改成 `{filteredStudents.length === 0 && (`。

- [ ] **Step 4: 运行测试，确认全部通过**

Run: `cd frontend && npx vitest run src/pages/AdminStudentsModule.test.tsx`
Expected: PASS（全部用例）

- [ ] **Step 5: 提交**

```bash
cd frontend && git add src/pages/AdminStudentsModule.tsx src/pages/AdminStudentsModule.test.tsx
git commit -m "feat: 学生总览页新增身份/托管教师/姓名筛选工具条"
```

---

### Task 3: 接入分页（派生切片 + Pagination 组件 + 筛选变化重置页码）

**Files:**
- Modify: `frontend/src/pages/AdminStudentsModule.tsx`
- Modify: `frontend/src/pages/AdminStudentsModule.test.tsx`

**Interfaces:**
- Consumes: `Pagination`（`import { Pagination } from '../components/Pagination'`），props `{ page: number; totalPages: number; totalItems: number; onPageChange: (page: number) => void }`（见 `2026-10-09-pagination-component.md`）；Task 2 产出的 `filteredStudents`。
- Produces: 无——本任务是本计划的最后一层，不再有后续任务依赖它的输出。

- [ ] **Step 1: 写失败测试（分页展示、翻页、筛选重置页码、数据刷新后页码收缩到新的最后一页）**

在 `frontend/src/pages/AdminStudentsModule.test.tsx` 顶部（`describe` 外）新增一个生成大量学生数据的辅助函数：

```tsx
function makeStudents(count: number, idOffset = 100, teacherName: string | null = null): courseApi.AdminStudent[] {
  return Array.from({ length: count }, (_, i) => {
    const n = i + 1
    return {
      id: idOffset + n,
      name: `学生${String(n).padStart(3, '0')}`,
      schoolClassName: null,
      classRoomId: null,
      classRoomName: null,
      offCampusOnly: true,
      enrolled: true,
      teacherName,
      enrolledCourseNames: [],
      enrolledCourseIds: [],
    }
  })
}
```

在 `describe('AdminStudentsModule', () => { ... })` 块内追加：

```tsx
  it('renders pagination controls reflecting the total filtered count and a page size of 20', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    expect(screen.getByText('共 3 条 · 第 1 页 / 共 1 页')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
  })

  it('shows only 20 students on the first page, advancing to the rest via the pagination control', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue(makeStudents(25))
    render(<AdminStudentsModule />)
    await screen.findByText('学生001')

    expect(screen.getByText('学生020')).toBeInTheDocument()
    expect(screen.queryByText('学生021')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(await screen.findByText('学生021')).toBeInTheDocument()
    expect(screen.getByText('学生025')).toBeInTheDocument()
    expect(screen.queryByText('学生001')).not.toBeInTheDocument()
  })

  it('resets to page 1 when a filter changes, even if the current page was not page 1', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue(makeStudents(25))
    render(<AdminStudentsModule />)
    await screen.findByText('学生001')

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))
    await screen.findByText('学生021')

    // 这个查询仍然匹配全部 25 个学生（都叫"学生xxx"），只是用来触发一次筛选条件变化。
    fireEvent.change(screen.getByLabelText('姓名搜索'), { target: { value: '学生' } })

    expect(await screen.findByText('学生001')).toBeInTheDocument()
    expect(screen.getByText('共 25 条 · 第 1 页 / 共 2 页')).toBeInTheDocument()
  })

  it('resets to page 1 when the identity or custody-teacher filter changes, not just the name search', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue(makeStudents(25, 100, '赵老师'))
    render(<AdminStudentsModule />)
    await screen.findByText('学生001')

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))
    await screen.findByText('学生021')
    fireEvent.change(screen.getByLabelText('身份筛选'), { target: { value: 'OFF_CAMPUS_ONLY' } })
    expect(await screen.findByText('学生001')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))
    await screen.findByText('学生021')
    fireEvent.change(screen.getByLabelText('托管教师筛选'), { target: { value: '赵老师' } })
    expect(await screen.findByText('学生001')).toBeInTheDocument()
  })

  it('clamps to the new last page (not page 1) when a data refresh shrinks the filtered results across fewer pages', async () => {
    vi.mocked(courseApi.fetchAdminStudents)
      .mockResolvedValueOnce(makeStudents(45))
      .mockResolvedValueOnce(makeStudents(25))
    vi.mocked(courseApi.updateAdminStudent).mockResolvedValue(makeStudents(45)[40])
    render(<AdminStudentsModule />)
    await screen.findByText('学生001')

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))
    await screen.findByText('学生021')
    fireEvent.click(screen.getByRole('button', { name: '下一页' }))
    const student041 = await screen.findByText('学生041')
    expect(student041).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '编辑学生041' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(courseApi.fetchAdminStudents).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('共 25 条 · 第 2 页 / 共 2 页')).toBeInTheDocument()
    expect(screen.getByText('学生021')).toBeInTheDocument()
    expect(screen.queryByText('学生041')).not.toBeInTheDocument()
  })
```

- [ ] **Step 2: 运行测试，确认按预期失败**

Run: `cd frontend && npx vitest run src/pages/AdminStudentsModule.test.tsx`
Expected: FAIL——找不到分页摘要文字/"上一页"/"下一页"按钮（分页还没接入）。

- [ ] **Step 3: 实现——分页 state、派生切片、Pagination 渲染、筛选变化重置页码**

在 `frontend/src/pages/AdminStudentsModule.tsx`：

1. 顶部 import 增加：
```tsx
import { Pagination } from '../components/Pagination'
```

2. 在筛选 state 声明之后新增分页常量与 state：
```tsx
const PAGE_SIZE = 20

const [currentPage, setCurrentPage] = useState(1)
```

3. 把筛选 state 的三个 setter 包装成"设置值 + 重置页码"的 handler（放在 `toggleCourseId` 附近）：
```tsx
function handleIdentityFilterChange(value: IdentityFilter) {
  setIdentityFilter(value)
  setCurrentPage(1)
}

function handleTeacherFilterChange(value: string) {
  setTeacherFilter(value)
  setCurrentPage(1)
}

function handleNameQueryChange(value: string) {
  setNameQuery(value)
  setCurrentPage(1)
}
```

4. 把筛选工具条里三个表单控件的 `onChange` 换成调用这几个 handler：
```tsx
onChange={(e) => handleIdentityFilterChange(e.target.value as IdentityFilter)}
...
onChange={(e) => handleTeacherFilterChange(e.target.value)}
...
onChange={(e) => handleNameQueryChange(e.target.value)}
```

5. 在 `filteredStudents` 的 `useMemo` 之后新增分页派生值：
```tsx
const totalPages = Math.max(1, Math.ceil(filteredStudents.length / PAGE_SIZE))
const currentPageClamped = Math.min(currentPage, totalPages)
const pageItems = useMemo(
  () => filteredStudents.slice((currentPageClamped - 1) * PAGE_SIZE, currentPageClamped * PAGE_SIZE),
  [filteredStudents, currentPageClamped],
)
```

6. 表格数据源从 `filteredStudents.map(...)` 切到 `pageItems.map(...)`（空状态判断 `{filteredStudents.length === 0 && (...)}` 保持不变，继续用 `filteredStudents` 判断"筛选后到底有没有人"，不要改成 `pageItems`）。

7. 在表格 `</table>` 之后、`</div>`（包裹表格的 `overflow-x-auto` 容器）之前，追加：
```tsx
<Pagination
  page={currentPageClamped}
  totalPages={totalPages}
  totalItems={filteredStudents.length}
  onPageChange={setCurrentPage}
/>
```

- [ ] **Step 4: 运行测试，确认全部通过**

Run: `cd frontend && npx vitest run src/pages/AdminStudentsModule.test.tsx`
Expected: PASS（全部用例）

- [ ] **Step 5: 提交**

```bash
cd frontend && git add src/pages/AdminStudentsModule.tsx src/pages/AdminStudentsModule.test.tsx
git commit -m "feat: 学生总览页接入分页，筛选变化自动重置页码"
```

## Completion Contract

- [ ] `frontend/src/pages/AdminStudentsModule.tsx`：新建学生走 `CreateStudentModal` 弹窗；顶部有身份/托管教师/姓名三个筛选控件；表格按 `pageItems`（`filteredStudents` 的分页切片）渲染，下方渲染 `Pagination`。
- [ ] 不存在任何对 `GET /api/admin/students` 的查询参数改动，后端文件（`AdminStudentController`/`AdminStudentService`/DAO）未被触碰。
- [ ] `frontend/src/pages/AdminStudentsModule.test.tsx` 全部用例通过，覆盖本计划 Review Focus 列出的 5 个点。
- [ ] `cd frontend && npx vitest run` 全量前端测试套件通过（确认本次改动没有破坏其他页面的测试）。
- [ ] `cd frontend && npx tsc --noEmit` 无报错。
- [ ] `cd frontend && npx oxlint src/pages/AdminStudentsModule.tsx` 无报错。
