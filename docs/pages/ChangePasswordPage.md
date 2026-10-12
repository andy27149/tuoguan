# ChangePasswordPage

**文件**：`frontend/src/pages/ChangePasswordPage.tsx`
**角色**：任意 `mustChangePassword=true` 的账号（新建的教师/管理员账号首次登录）

## 一句话

强制改密通道——`mustChangePassword` 为真时，`AuthContext` 会让整个应用只渲染这一个页面，改密成功前无法访问系统任何其他功能。

## 功能点

- 旧密码 + 新密码两个输入框，提交按钮
- 旧密码错误返回 401，页面显示错误提示
- 改密成功后（204），`AuthContext` 状态切到 `authenticated`，应用自动渲染正常界面（教师落地看板，管理员落地机构总览，平台管理员落地平台管理页）

## 依赖

- `auth/AuthContext.tsx`：改密方法和状态机
- `api/client.ts`：`ApiError`
- `brand/BrandMark.tsx`

## 关联文档

- [业务流程 · 流程八：账号与权限](../business/业务流程.md#流程八账号与权限)
