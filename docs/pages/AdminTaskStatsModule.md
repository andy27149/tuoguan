# AdminTaskStatsModule

**文件**：`frontend/src/pages/AdminTaskStatsModule.tsx`
**角色**：`ADMIN`（托管功能关闭时整个模块入口隐藏）

## 一句话

按日期查看各托管班的任务完成情况统计——管理员用来了解教师日常执行力的只读报表页。

## 功能点

- 选日期（默认今天）
- 按班级列出：学生数量、签到数量、任务完成数量
- 纯只读展示，没有编辑/操作入口

## 依赖

- `api/admin.ts`
- `kanban/date.ts`（`todayDateString`）

## 关联文档

- 暂无专门的业务流程章节——这是管理员对 [业务流程 · 流程三：托管班日常运营](../business/业务流程.md#流程三托管班日常运营) 产出数据的汇总查看
