# 页面功能索引

> 粒度比 [业务架构.md](../business/业务架构.md) 的"模块级"更细，到"页面里有哪些按钮/弹窗/依赖哪些接口"。要改一个页面前，先看这页索引定位到具体页面文件，再只读那一篇，不用整个目录都读。
>
> 全部 15 个前端页面已覆盖（2026-10-12）。

## 教师端（4 个页面）

| 页面 | 文件 | 一句话 | 详情 |
|---|---|---|---|
| 登录 | `LoginPage.tsx` | 手机号+密码登录，按角色跳转 | [LoginPage.md](./LoginPage.md) |
| 改密 | `ChangePasswordPage.tsx` | 首次登录强制改密通道 | [ChangePasswordPage.md](./ChangePasswordPage.md) |
| 托管看板 | `KanbanPage.tsx` | 打卡/任务/评分/用餐请假/放学，教师的日常工作台 | [KanbanPage.md](./KanbanPage.md) |
| 课外课消课 | `CourseConsumptionPage.tsx` | 点名消课 | [CourseConsumptionPage.md](./CourseConsumptionPage.md) |

## 机构管理后台（9 个页面，容器 + 8 模块）

| 页面 | 文件 | 一句话 | 详情 |
|---|---|---|---|
| 管理后台容器 | `AdminDashboardPage.tsx` | 侧边栏 + 模块切换壳子，不含业务逻辑 | [AdminDashboardPage.md](./AdminDashboardPage.md) |
| 机构总览 | `AdminOverviewModule.tsx` | 5 张运营卡片快照 | [AdminOverviewModule.md](./AdminOverviewModule.md) |
| 基础配置 | `AdminSettingsModule.tsx` | 机构名称/Logo | [AdminSettingsModule.md](./AdminSettingsModule.md) |
| 教师列表 | `AdminTeachersModule.tsx` | 建号/改名/删除（含影响评估） | [AdminTeachersModule.md](./AdminTeachersModule.md) |
| 教学单元 | `AdminTeachingUnitsModule.tsx` | 托管班/课外课统一管理 | [AdminTeachingUnitsModule.md](./AdminTeachingUnitsModule.md) |
| 学生总览 | `AdminStudentsModule.tsx` | 全机构学生列表+建学生+对账单入口 | [AdminStudentsModule.md](./AdminStudentsModule.md) |
| 任务完成情况 | `AdminTaskStatsModule.tsx` | 按日期/班级的任务完成统计 | [AdminTaskStatsModule.md](./AdminTaskStatsModule.md) |
| 定价中心 | `AdminPricingModule.tsx` | 托管定价+课外课单价 | [AdminPricingModule.md](./AdminPricingModule.md) |
| 账单管理 | `AdminBillingModule.tsx` | 生成/核对/标记账单 | [AdminBillingModule.md](./AdminBillingModule.md) |

## 平台管理 + 家长（2 个页面）

| 页面 | 文件 | 一句话 | 详情 |
|---|---|---|---|
| 平台管理 | `PlatformAdminPage.tsx` | 开通机构/续费/到期跟踪/扫码统计 | [PlatformAdminPage.md](./PlatformAdminPage.md) |
| 家长分享页 | `ParentSharePage.tsx` | 免登录查看孩子情况 | [ParentSharePage.md](./ParentSharePage.md) |

## 页面详情条目的写法

每篇 `docs/pages/<页面名>.md` 固定包含：
- **角色**：谁能看到这个页面
- **一句话**：这个页面是干什么的
- **功能点**：页面上每个可交互元素/弹窗，一行一个
- **依赖**：`api/*.ts` 文件 + 关键子组件（`components/*.tsx`），方便顺藤摸瓜找到要改的具体文件
- **关联文档**：对应的业务流程章节
