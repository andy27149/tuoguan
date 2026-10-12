# AdminStudentsModule

**文件**：`frontend/src/pages/AdminStudentsModule.tsx`
**角色**：`ADMIN`

## 一句话

全机构学生的统一列表——唯一能同时看到一个学生"托管+课外课"全貌的界面，建学生、改信息、停用、以及课外课充值都在这里。

## 功能点

- **筛选**：身份筛选（全部/托管/纯课外）、按姓名搜索、按托管教师筛选；列表分页（`components/Pagination.tsx`）
- **列表**：姓名、身份徽标（托管/纯课外）、学籍班、课外课报名情况、状态（已启用/已停用）
- **新建学生**（`components/CreateStudentModal.tsx`）：填姓名、学籍班，可选挂一个托管班（留空=纯课外课学生），可多选报名若干课外课——建号时系统自动生成该生的分享 `shareToken`
- **编辑**：改姓名/学籍班/托管班归属/课外课报名
- **停用**：`ConfirmDialog` 二次确认（"该学生将从看板上隐藏"，已产生的记录不会被删除，可随时重新启用）
- **对账单 / 充值**（`components/AdminCourseStatementModal.tsx`）：**只要报了课外课的学生都有这个入口**（不分是否同时在托管班），点开能看到该生每门课的充值、消课记录和余额明细，也能直接帮他充值

## 依赖

- API：`api/course.ts`（充值/对账单）、`api/unit.ts`（教学单元下拉）
- `api/client.ts`：`ApiError`
- 组件：`AdminCourseStatementModal.tsx`、`ConfirmDialog.tsx`、`CreateStudentModal.tsx`、`Pagination.tsx`

## 关联文档

- [业务流程 · 流程二：机构内部搭建](../business/业务流程.md#流程二机构内部搭建) 第 4 步
- [业务架构 · 三、核心业务对象与关系](../business/业务架构.md#三核心业务对象与关系)（学生身份判定规则）
