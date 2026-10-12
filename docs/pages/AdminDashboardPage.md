# AdminDashboardPage

**文件**：`frontend/src/pages/AdminDashboardPage.tsx`
**角色**：`ADMIN`

## 一句话

机构管理后台的容器壳子——侧边栏 + 模块切换，本身不含业务逻辑，业务逻辑都在被它渲染的 8 个子模块里。

## 功能点

- 侧边栏（`components/AdminSidebar.tsx`）：5 个分组、8 个模块入口，`isModuleHidden` 根据机构的 `custodyEnabled`/`offCampusEnabled` 决定对应模块是否显示（当前这两个开关本身的切换 UI 已隐藏，默认都是开，见 [AdminSettingsModule.md](./AdminSettingsModule.md)）
- `activeModule` 状态决定右侧渲染哪个子模块，默认落地「机构总览」
- 顶部「退出登录」
- 「查看托管看板」：跳回教师视角的只读看板（走 `onOpenClassKanban`，对应 [KanbanPage.md](./KanbanPage.md) 的管理员只读分支）
- 「帮助」：打开 `components/HelpModal.tsx`（`role="admin"`），弹窗展示机构管理员的上手步骤+常见问题（文字版，不带截图）——**这份内容是从 `docs/training/操作培训手册.html` 的机构管理员部分摘录+改写的，两处独立维护，改任何一处涉及的功能流程都要检查并同步另一处**，见 [docs/INDEX.md](../INDEX.md) 的维护提示

## 子模块一览

| 模块 | 详情 |
|---|---|
| 机构总览 | [AdminOverviewModule.md](./AdminOverviewModule.md) |
| 基础配置 | [AdminSettingsModule.md](./AdminSettingsModule.md) |
| 教师列表 | [AdminTeachersModule.md](./AdminTeachersModule.md) |
| 教学单元 | [AdminTeachingUnitsModule.md](./AdminTeachingUnitsModule.md) |
| 学生总览 | [AdminStudentsModule.md](./AdminStudentsModule.md) |
| 任务完成情况 | [AdminTaskStatsModule.md](./AdminTaskStatsModule.md) |
| 定价中心 | [AdminPricingModule.md](./AdminPricingModule.md) |
| 账单管理 | [AdminBillingModule.md](./AdminBillingModule.md) |

## 依赖

- `components/AdminSidebar.tsx`、`components/UpdatesButton.tsx`、`components/HelpModal.tsx`
- `api/institution.ts`（读取机构基础信息，决定侧边栏显隐）
- `auth/AuthContext.tsx`

## 关联文档

- [业务架构 · 4.1 机构管理员 — 管理后台 8 大模块](../business/业务架构.md#41-机构管理员admin--管理后台-8-大模块)
