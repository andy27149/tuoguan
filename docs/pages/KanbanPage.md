# KanbanPage

**文件**：`frontend/src/pages/KanbanPage.tsx`
**角色**：`TEACHER`（名下有托管班时）；`ADMIN` 也能看，但走只读分支（见下）

## 一句话

托管班的日常工作台——打卡、布置任务、打勾完成、评分点评、用餐/请假登记、放学确认，教师每天打开最多的页面。

## 功能点

- **班级 Tab 切换**：顶部 Tab 列出教师名下全部托管班（数据按"学校班级"二次分组展示，见 `kanban/schoolClass.ts` 的 `groupBySchoolClass`）
- **任务库管理**：机构内共享的任务模板增删，删除仍被引用的模板会自动归档而非硬删（`TaskTemplateManager.tsx`）
- **批量分配任务**：从任务库多选模板，一次性下发给"全部学生"或当前学校班（`AssignTaskBar.tsx`）
- **一键完成今日任务**：把当前班级所有未完成任务标记为已完成（`CompleteAllTasksButton.tsx`，有二次确认）
- **学生卡片**（`components/StudentCard.tsx`）承载大部分单学生操作：
  - 头像上传
  - 今日任务打勾/取消、+ 添加任务（自动同步给同学籍班其他学生）
  - 1-5 星评价 + 一句话点评
  - 到校打卡（🕐）、用餐登记（🍚）、请假登记+原因（🌴）
  - 🖼 海报（`SharePosterModal.tsx`）/ 📊 月度统计（`MonthlyStatsModal.tsx`）/ 🔗 分享链接（`ShareLinkModal.tsx`）
- **放学确认**：`DismissButton.tsx`，标记班级当天已放学，影响任务卡片颜色规则（全部完成=绿色印章 &gt; 已放学仍有未完成=红色 &gt; 默认色）
- **空状态**：名下没有托管班时整页替换为 `EmptyState.tsx`，提示联系管理员建班（**教师没有自建班级入口**，班级创建已收归管理员，见[业务流程 · 流程二](../business/业务流程.md#流程二机构内部搭建)）
- **管理员只读模式**：`ADMIN` 查看某个班级看板时，走 `api/adminKanban.ts` 这条只读接口分支（对应后端 `AdminKanbanController`，仅 GET），UI 上所有修改类按钮隐藏，带"只读"徽标——这是前端路径设计，不是后端强制隔离，细节见[业务架构 · 二、角色体系](../business/业务架构.md#二角色体系)的权限细节说明

## 依赖

- API：`api/classes.ts`（班级列表，只读）、`api/students.ts`、`api/taskTemplates.ts`、`api/dailyTasks.ts`、`api/dismissal.ts`、`api/studentNotes.ts`、`api/arrival.ts`、`api/meal.ts`、`api/leave.ts`、`api/admin.ts`、`api/adminKanban.ts`（管理员只读分支）
- 组件：`StudentCard.tsx`（内部再用到 `SharePosterModal.tsx`、`MonthlyStatsModal.tsx`、`ShareLinkModal.tsx`）、`AssignTaskBar.tsx`、`CompleteAllTasksButton.tsx`、`DismissButton.tsx`、`TaskTemplateManager.tsx`、`Toast.tsx`、`EmptyState.tsx`、`UpdatesButton.tsx`
- 工具：`kanban/date.ts`（`todayDateString`）、`kanban/schoolClass.ts`（`groupBySchoolClass`）、`kanban/cardStatus.ts`（卡片颜色计算，纯函数，独立单测）
- `auth/AuthContext.tsx`：判断当前是否 `isAdmin`，决定走只读分支

## 关联文档

- [业务流程 · 流程三：托管班日常运营](../business/业务流程.md#流程三托管班日常运营)
