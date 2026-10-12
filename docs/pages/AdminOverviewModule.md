# AdminOverviewModule

**文件**：`frontend/src/pages/AdminOverviewModule.tsx`
**角色**：`ADMIN`

## 一句话

机构管理后台的默认落地页——5 张卡片拼成的运营快照，一眼看完今天/本月的情况。

## 功能点（5 张卡片，均为独立组件）

- **课时不足预警**（`components/LowBalanceCard.tsx`）：按"学生+课程"列出余额 ≤ 阈值（&lt;3）的记录，每行有「去充值」按钮直接打开 `AdminCourseStatementModal.tsx`
- **账单欠费提醒**（`components/UnpaidBillsCard.tsx`）：全部未缴费账单汇总（笔数+总金额），点「查看账单管理 →」跳转到账单管理模块（`onOpenBilling` 回调，由 `AdminDashboardPage` 处理模块切换）
- **在读规模**（`components/EnrollmentCard.tsx`）：在读学生总数、托管/纯课外课人数拆分、按教师分布
- **今日运营快照**（`components/TodaySnapshotCard.tsx`）：今天到校/用餐/请假人数 vs 托管学生总数
- **收入快照**（`components/RevenueCard.tsx`）：上月托管费应收/实收、本月课外课消课次数

## 依赖

- API：各卡片组件内部分别调用 `api/adminOverview.ts` 的对应接口（`fetchLowBalance`、`fetchUnpaidBills`、`fetchEnrollmentSummary`、`fetchTodaySnapshot`、`fetchRevenueSnapshot`）
- 组件：`LowBalanceCard.tsx`、`UnpaidBillsCard.tsx`、`EnrollmentCard.tsx`、`TodaySnapshotCard.tsx`、`RevenueCard.tsx`，`LowBalanceCard` 内部还会用到 `AdminCourseStatementModal.tsx`

## 关联文档

- [业务流程 · 流程五：账单生成与收款](../business/业务流程.md#流程五账单生成与收款)（"账单欠费提醒"卡片与这个流程对应）
- 其余几张卡片对应管理员日常"先看总览再决定去哪个模块"的工作习惯，暂无单独的业务流程章节
