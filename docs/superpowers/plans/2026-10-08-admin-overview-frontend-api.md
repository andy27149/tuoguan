# 机构总览页：前端 API 模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `frontend/src/api/adminOverview.ts`，给机构总览页五张卡片提供类型化的 fetch 函数。

**Architecture:** 纯薄封装层，跟 `frontend/src/api/billing.ts` 等现有 api 模块的写法完全一致——每个后端 `/api/admin/overview/*` 接口对应一个 TS 接口 + 一个零参数 `apiFetch` 调用，没有任何分支逻辑。

**Tech Stack:** TypeScript, 现有的 `apiFetch<T>`（`frontend/src/api/client.ts`）。

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md`

## Global Constraints

- 文件名必须是 `adminOverview.ts`，不是 spec 里最初写的 `adminDashboard.ts`——这是故意的命名调整，避免跟已有的、不相关的 `AdminDashboardPage.tsx`（管理员后台外壳组件）混淆。
- 五个接口的字段名和类型必须跟下面给出的完全一致——这是已经和四个后端计划（分别实现这五个真实接口）谈好的最终形状，不是本计划自己设计的，不要改动。

## Review Focus

本计划范围是纯类型声明 + 零逻辑的 fetch 包装函数，没有分支、没有状态、没有可能出错的运行时行为，不适用"五个最容易踩坑的输入/场景"这个检查项——跳过，不要为了凑数编risk。

---

### Task 1: 新增 adminOverview.ts

**Files:**
- Create: `frontend/src/api/adminOverview.ts`

**Interfaces:**
- Consumes: `apiFetch<T>(path: string, init?: RequestInit): Promise<T>`（已存在于 `frontend/src/api/client.ts`，直接 import）。
- Produces（五张卡片的组件后续会消费这些，确切签名必须保持一致）：
  - `fetchLowBalance(): Promise<LowBalanceRow[]>`
  - `fetchUnpaidBills(): Promise<UnpaidBillSummary>`
  - `fetchEnrollmentSummary(): Promise<EnrollmentSummary>`
  - `fetchTodaySnapshot(): Promise<TodaySnapshot>`
  - `fetchRevenueSnapshot(): Promise<RevenueSnapshot>`
  - 以及五个对应的 TS 接口：`LowBalanceRow`、`UnpaidBillRow`、`UnpaidBillSummary`、`TeacherStudentCount`、`EnrollmentSummary`、`TodaySnapshot`、`RevenueSnapshot`。

- [ ] **Step 1: 创建文件，写入以下完整内容**

```ts
import { apiFetch } from './client'

export interface LowBalanceRow {
  studentId: number
  studentName: string
  courseId: number
  courseName: string
  balance: number
}

export interface UnpaidBillRow {
  studentId: number
  studentName: string
  className: string
  yearMonth: string
  totalAmount: number
}

export interface UnpaidBillSummary {
  count: number
  totalAmount: number
  rows: UnpaidBillRow[]
}

export interface TeacherStudentCount {
  teacherName: string
  studentCount: number
}

export interface EnrollmentSummary {
  totalCount: number
  custodyCount: number
  offCampusOnlyCount: number
  byTeacher: TeacherStudentCount[]
}

export interface TodaySnapshot {
  arrivedCount: number
  mealCount: number
  leaveCount: number
  totalCustodyStudentCount: number
}

export interface RevenueSnapshot {
  tuitionMonth: string
  tuitionBilled: number
  tuitionCollected: number
  consumptionMonth: string
  offCampusConsumptionCount: number
}

export function fetchLowBalance(): Promise<LowBalanceRow[]> {
  return apiFetch<LowBalanceRow[]>('/admin/overview/low-balance')
}

export function fetchUnpaidBills(): Promise<UnpaidBillSummary> {
  return apiFetch<UnpaidBillSummary>('/admin/overview/unpaid-bills')
}

export function fetchEnrollmentSummary(): Promise<EnrollmentSummary> {
  return apiFetch<EnrollmentSummary>('/admin/overview/enrollment')
}

export function fetchTodaySnapshot(): Promise<TodaySnapshot> {
  return apiFetch<TodaySnapshot>('/admin/overview/today')
}

export function fetchRevenueSnapshot(): Promise<RevenueSnapshot> {
  return apiFetch<RevenueSnapshot>('/admin/overview/revenue')
}
```

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npx tsc -b`
Expected: 无输出、无报错（这个文件没有引入任何会编译失败的东西，纯声明+直通调用）。

没有测试文件：这个模块没有分支逻辑，五张卡片各自的组件测试（其它计划负责）会 `vi.mock('../api/adminOverview')` 来间接验证这些函数被正确调用，单独给这个文件写测试没有额外价值。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/adminOverview.ts
git commit -m "feat: 新增机构总览页五张卡片的 API 封装"
```

## Self-Review

1. **Spec coverage**：spec"前端改动范围"一节要求的"五个接口各一个 fetch 函数 + 对应 TS 接口"已全部覆盖。
2. **Placeholder scan**：无 TBD/TODO，代码是完整可运行的最终形态。
3. **Type consistency**：七个接口名、五个函数名跟本计划"Interfaces: Produces"列出的完全一致；跟 spec 里五个后端端点的路径（`/admin/overview/low-balance` 等）逐一对应。
4. **Review Focus**：已在上面说明此计划范围不适用，跳过。

自查通过。
