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