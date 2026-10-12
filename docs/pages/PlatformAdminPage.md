# PlatformAdminPage

**文件**：`frontend/src/pages/PlatformAdminPage.tsx`
**角色**：`PLATFORM_ADMIN`（登录后整页替换，看不到任何机构内部数据，也没有导航能回到机构视角）

## 一句话

跨机构的平台运营入口——开通新机构、看机构规模、追踪到期/续费、维护联系方式、看扫码活跃度。

## 功能点

- **开通新机构**：表单填机构名称、管理员手机号/初始密码、联系人姓名/电话、地址、试用到期日（默认建议今天+30天），提交后一次性创建机构 + 该机构首个 `ADMIN` 账号，初始密码明文展示一次
- **机构列表**：机构名称/创建时间、学生数/班级数/教师数、**今日扫码查看人数**、联系人（姓名+电话）、到期状态徽标、操作列
  - 到期状态徽标四态：灰色「未设置」、绿色「有效至 YYYY-MM-DD」、橙色「N天后到期」/「今天到期」（距到期 ≤7天）、红色「已过期N天」
- **续费登记**（`components/RenewalModal.tsx`）：选套餐（试用/月付/半年付/年付），系统按套餐建议新到期日（可手动改），保存后更新机构 `validUntil` 并写入一条续费记录；可查看该机构的历史续费记录
- **编辑联系信息**（`components/EditInstitutionContactModal.tsx`）：联系人姓名/电话/地址，电话为必填

## 依赖

- `api/platform.ts`（`fetchInstitutions`/`createInstitution`/`createRenewal`/`fetchRenewalHistory`/`updateInstitutionContact`）
- `api/client.ts`：`ApiError`（识别手机号冲突 409）
- `components/RenewalModal.tsx`、`components/EditInstitutionContactModal.tsx`
- `kanban/date.ts`：`formatDate`

## 关联文档

- [业务流程 · 流程一：机构开通与生命周期](../business/业务流程.md#流程一机构开通与生命周期)
- [业务流程 · 流程七：平台运营监控](../business/业务流程.md#流程七平台运营监控)
