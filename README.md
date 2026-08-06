# 托管班学生学习管理系统

个人/小团队副业项目。技术栈：React + Vite + TS + Tailwind（前端），Spring Boot + MySQL + MinIO（后端），Docker Compose 编排。

## 本地开发

1. 复制环境变量文件：`cp .env.example .env`
2. 启动后端基础设施（MySQL + MinIO + backend 容器）：
   ```bash
   docker compose up --build -d
   ```
3. 启动前端 dev server（会自动代理 `/api` 到 `http://localhost:8080`）：
   ```bash
   cd frontend
   pnpm install
   pnpm dev
   ```
4. 浏览器打开 `http://localhost:5173`，页面应显示"✅ 后端已连接"

## 常用命令

- 后端测试：`cd backend && mvn test`
- 前端测试：`cd frontend && pnpm vitest run`
- 停止所有容器：`docker compose down`

## 阶段1：账号体系与数据隔离

后端新增基于 JWT 的登录鉴权，以及机构（Institution）/老师（Teacher）多租户数据隔离。

### JWT_SECRET 配置

JWT 签名密钥从环境变量 `JWT_SECRET` 读取，长度需 ≥32 字节（HS256 要求密钥 ≥256 位）。`.env.example` 中已给出示例值，复制为 `.env` 后按需替换为随机值；真实 `.env` 不提交到版本库。

### 创建首个账号：种子命令

数据库为空时没有任何账号可以登录，需要通过种子命令创建第一个机构和管理员账号。仅当启动参数包含 `--seed` 时才会执行，正常 `docker compose up` 启动不会触发：

```bash
java -jar app.jar --seed --seed.institution="示例机构" --seed.phone="13800000000" --seed.password="Passw0rd!"
```

创建的账号角色为 `ADMIN`，且 `must_change_password=true`，首次登录后必须调用改密接口才能访问其他资源。若手机号已被占用（`phone` 全局唯一），命令报错退出，不会覆盖已有数据。

### 阶段1新增接口

- `POST /api/auth/login`：手机号+密码登录，成功返回 `{ "token": "<jwt>", "mustChangePassword": true }`；账号不存在或密码错误统一返回 `401`。
- `POST /api/auth/change-password`：需携带 `Authorization: Bearer <token>`，请求体 `{ "oldPassword": "...", "newPassword": "..." }`，成功返回 `204`，旧密码错误返回 `401`。
- `GET /api/teachers/me`：需携带 `Authorization: Bearer <token>`，返回当前登录老师自身信息 `{ "id": ..., "phone": "...", "institutionId": ..., "role": "..." }`；`institutionId`/`teacherId` 一律从 JWT 读取，不接受客户端传参。