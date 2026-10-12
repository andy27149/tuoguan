# CourseConsumptionPage

**文件**：`frontend/src/pages/CourseConsumptionPage.tsx`
**角色**：`TEACHER`（名下有课外课时）

## 一句话

课外课点名消课页——选课程、选日期、花名册点名，确认后批量生成消课记录。

## 功能点

- **课程 Tab 切换**：顶部 Tab 列出教师名下全部课外课（按 `billingMode = LESSON_COUNT` 的教学单元）
- **消课日期**：默认今天，`<input type="date">`，不允许选未来日期（提交时校验，超前日期会弹出警告弹窗而不是直接报错）
- **花名册**：默认全勾（全部在读学生视为出勤），教师手动取消未到场学生的勾选；**没有"缺席"概念**——未勾选的学生不生成任何记录
  - 花名册不再按"托管/纯课外"身份分组，统一按姓名排序（教师不需要关心学生的计费身份，那是后端的记账口径）
  - 有余额信息的学生（报了课外课）会显示当前余额，余额 ≤0 或 &lt;3 节标红/标黄提示，但**不阻断消课**
  - 每行有 🔗 按钮可以查看/复制该学生的家长专属分享链接（`ShareLinkModal.tsx`）
- **确认消课**：点击「确认消课（N人）」先弹出二次确认框（`ConfirmDialog`，文案"请确认针对N个孩子进行消课处理吗，此操作不可更改？"），确认后才真正提交批量消课请求
- **结果提示**：`RollCallResultModal.tsx` 显示成功（新增几条记录，已消过课的学生会被静默跳过不重复计入）或失败（如课程未配置单价）

## 依赖

- API：`api/course.ts`（`fetchMyCourses`、`fetchCourseRoster`、`recordBatchConsumption`）
- 组件：`ConfirmDialog.tsx`、`RollCallResultModal.tsx`、`ShareLinkModal.tsx`、`Toast.tsx`
- `api/client.ts`：`ApiError`（识别未配置单价等 400 错误）

## 关联文档

- [业务流程 · 流程四：课外课点名消课](../business/业务流程.md#流程四课外课点名消课)
- [业务架构 · 五、课外课计费与结算](../business/业务架构.md#五课外课计费与结算充值覆盖--月度账单兜底)（消课之后钱怎么算，这个页面本身不处理计费，只负责点名生成记录）
