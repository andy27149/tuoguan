# AdminSettingsModule

**文件**：`frontend/src/pages/AdminSettingsModule.tsx`
**角色**：`ADMIN`

## 一句话

机构基础信息配置——机构名称、Logo；"业务功能开关"这部分 UI 目前隐藏。

## 功能点

- **机构信息**：机构名称编辑（保存）、Logo 上传
- **业务功能开关**（`custodyEnabled`/`offCampusEnabled`，"启用托管功能"/"启用课外课功能"）：
  - **整块 UI 目前被 `SHOW_BUSINESS_FEATURE_TOGGLES = false` 隐藏**，管理员看不到这个区块——因为现阶段接入的机构基本都要同时用托管和课外课，两个字段数据库默认值都是 `TRUE`，暂时不需要管理员手动管理
  - 代码逻辑还在（没有删除，只是不渲染）：关闭某一项会先弹 `ConfirmDialog` 二次确认（"关闭后，XX 相关入口将从教师端与家长端隐藏，已产生的数据不会被删除，可随时重新开启"），且不能两项都关闭
  - 如果未来需要重新放开这个入口，只需要把那个常量改回 `true`

## 依赖

- `api/institution.ts`（机构信息读写、`updateFeatureFlags`）
- `components/ConfirmDialog.tsx`、`components/Toast.tsx`

## 关联文档

- [业务架构 · 二、角色体系](../business/业务架构.md#二角色体系)（最后一条关于功能开关现状的说明）
- [业务流程 · 流程二：机构内部搭建](../business/业务流程.md#流程二机构内部搭建) 第 1 步
