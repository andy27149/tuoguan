# LoginPage

**文件**：`frontend/src/pages/LoginPage.tsx`
**角色**：所有人（登录前，无角色区分——ADMIN/TEACHER/PLATFORM_ADMIN 共用同一个登录表单）

## 一句话

手机号 + 密码登录入口，登录成功后由 `AuthContext` 根据返回的角色和 `mustChangePassword` 标记决定跳转到哪个界面。

## 功能点

- 手机号输入框、密码输入框、登录按钮
- 提交后调用登录接口；401（账号不存在或密码错误）统一显示一条模糊的失败提示，不区分"账号不存在"和"密码错误"（避免暴露账号是否存在）
- 登录成功后不在本页做跳转逻辑——由上层 `App.tsx`/`AuthContext` 读取登录响应里的 `mustChangePassword` 和角色来决定渲染哪个页面（见 [ChangePasswordPage.md](./ChangePasswordPage.md)）

## 依赖

- `auth/AuthContext.tsx`：`useAuth()` 提供的登录方法，登录态是一个 `loading/anonymous/mustChangePassword/authenticated` 的联合类型状态机
- `api/client.ts`：`ApiError`，用于识别 401
- `brand/BrandMark.tsx`：品牌标识组件

## 关联文档

- [业务流程 · 流程八：账号与权限](../business/业务流程.md#流程八账号与权限)
