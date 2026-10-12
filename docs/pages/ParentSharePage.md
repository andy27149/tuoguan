# ParentSharePage

**文件**：`frontend/src/pages/ParentSharePage.tsx`
**角色**：家长（隐性角色，无账号，凭 URL 里的 `share_token` 访问，`/share/{token}`）

## 一句话

免登录公开页，内容按学生当前身份自动组装，是家长唯一能接触到系统的入口。

## 功能点

- 头像 + 姓名 + 学籍班（无头像时显示姓名首字）
- **有托管班**（`components/MonthlyStatsView.tsx`）：
  - 月份切换（上月/下月）、日历按天查看
  - 本月平均评星、已完成/未完成天数汇总
  - 点某一天：当天任务完成情况、评分+点评、到达托管班时间、是否用餐、请假原因（图标小胶囊样式，2026-10-12 优化过，之前因为缺 CSS 规则导致字号异常大）
  - 每日任务完成占比图（**已去掉"每日获星趋势"折线图**，2026-10 的改动，之前占用版面但信息密度不高）
  - 若同时报了课外课（双重身份）：额外展示「课外课情况」卡片（`components/CourseActivityView.tsx`），**按当前查看月份过滤、跟随日历选中日期变化**，展示当天实际上的课；没有消课记录的日期显示"当天没有课外课记录"（不是固定列表，这是 2026-10-12 的改动，之前是"最近5条全量记录"，切换日期不会变化）
- **无托管班（纯课外课）**（`components/CourseStatementView.tsx`）：课外课账户对账单——已充值/已消耗/余额，按课程分组
- 页面底部品牌标识，无其他导航——这是一个死胡同页面，没有返回按钮，也没有登录入口

## 依赖

- `api/publicShare.ts`（`fetchPublicShare`，唯一的数据来源，无需 token 鉴权之外的任何身份校验）
- `kanban/date.ts`：`currentMonthString`、`todayDateString`
- 组件：`MonthlyStatsView.tsx`（内部含 `CourseActivityView.tsx`）、`CourseStatementView.tsx`

## 关联文档

- [业务流程 · 流程六：家长触达与查看](../business/业务流程.md#流程六家长触达与查看)
- [业务架构 · 五、课外课计费与结算 · 5.3](../business/业务架构.md#五课外课计费与结算充值覆盖--月度账单兜底)（为什么双重身份学生这里看不到充值/余额——这是展示口径的历史遗留，和结算规则已经统一了不是一回事）
