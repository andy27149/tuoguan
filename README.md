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

### 后端集成测试依赖 Docker（colima 用户需额外配置）

后端集成测试（继承 `IntegrationTestBase` 的测试类）通过 Testcontainers 启动真实 MySQL 容器。若本地用 colima 而非 Docker Desktop 运行 Docker，较新版本的 Docker Engine（本项目环境为 29.5.2，API 1.54）已不再兼容 Testcontainers 默认使用的旧版 Docker API，需要在运行测试前设置以下环境变量（建议写入 shell 配置文件，一次性生效）：

```bash
export DOCKER_HOST=unix:///Users/<你的用户名>/.colima/default/docker.sock
export DOCKER_API_VERSION=1.44
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export JAVA_TOOL_OPTIONS="-Dapi.version=1.44"
```

之后直接 `mvn test` 即可。四个变量缺一不可：`DOCKER_API_VERSION` 环境变量本身不够，docker-java 实际读取的是 `api.version` JVM 系统属性，因此需要通过 `JAVA_TOOL_OPTIONS` 传给 Maven fork 出的测试子进程；`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 用于修正 Testcontainers 清理容器（Ryuk）挂载 socket 路径的问题（colima 的 socket 在宿主机和虚拟机内部路径不同）。Docker Desktop 用户通常不需要这些配置。

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

## 阶段2：任务库与学生名册

新增机构内共享的任务模板库、托管班级只读接口，以及老师对自己班级学生的名册维护。

### 创建班级：种子命令扩展

班级（`class_room`）的创建仍需管理员完成，阶段 7 前通过种子命令的 `--seed-class` 参数手工创建，指定班级归属的老师手机号：

```bash
java -jar app.jar --seed-class --seed-class.teacherPhone="13800000000" --seed-class.name="三年级托管班"
```

老师必须已存在（手机号需先通过 `--seed`/管理员创建的账号登录过），班级的 `institutionId` 自动取自该老师所属机构。`--seed` 与 `--seed-class` 可在同一次启动中同时使用。

### 阶段2新增接口

以下接口均需携带 `Authorization: Bearer <token>`；`institutionId`/`teacherId` 一律从 JWT 读取，不接受客户端传参。

- `POST /api/task-templates`：创建任务模板，机构内共享。请求体 `{ "subject": "数学", "name": "口算练习" }`，返回 `201`。
- `GET /api/task-templates`：列出当前机构下的所有任务模板。
- `DELETE /api/task-templates/{id}`：删除任务模板；模板不属于当前机构时返回 `404`。
- `GET /api/classes`：列出当前老师名下的托管班级（`{ "id": ..., "name": ... }`）。
- `POST /api/classes/{classId}/students`：在自己名下的班级中新增学生，请求体 `{ "name": "小明", "schoolClassName": "三年级2班" }`，默认在读状态为 `true`；班级不属于当前老师时返回 `404`。
- `GET /api/classes/{classId}/students`：列出该班级学生名册；班级不属于当前老师时返回 `404`。
- `PUT /api/students/{id}`：更新学生信息，请求体 `{ "name": ..., "schoolClassName": ..., "enrolled": ... }`；学生所在班级不属于当前老师时返回 `404`。
- `DELETE /api/students/{id}`：删除学生；学生所在班级不属于当前老师时返回 `404`。

## 阶段3：看板核心：任务分配与完成

每日任务（`daily_task`）保存的是任务名称/科目的**快照**，不引用任务模板的实时状态——模板后续被编辑或删除，不影响已下发的历史记录；`class_room_id` 同样是分配当时的快照，学生转班不会改变历史记录归属（对应 PRD 第九节两条建议默认值）。每日任务从空白开始，不带入前一天。

### 阶段3新增接口

以下接口均需携带 `Authorization: Bearer <token>`；`institutionId`/`teacherId` 一律从 JWT 读取，不接受客户端传参。

- `POST /api/classes/{classId}/daily-tasks/batch`：从任务库批量分配任务给班级内**全部在读学生**。请求体 `{ "taskTemplateIds": [1, 2], "date": "2026-08-06" }`，返回 `201` 及创建的每日任务列表；`taskTemplateId` 不属于当前机构或班级不属于当前老师时返回 `404`。
- `POST /api/students/{studentId}/daily-tasks`：给单个学生新增一项任务，可来自任务库（`{ "taskTemplateId": 1, "date": "..." }`）或直接定制（`{ "subject": "语文", "name": "阅读打卡", "date": "..." }`，返回的 `custom` 为 `true`）。**按学校班级批量同步**：系统会自动将同一项任务追加给同一托管班级内、`schoolClassName` 相同的其他在读学生，无需老师确认；接口只返回目标学生本人创建的那一条记录，同步出去的记录需通过班级任务列表接口查看。学生所在班级不属于当前老师时返回 `404`。
- `GET /api/classes/{classId}/daily-tasks?date=YYYY-MM-DD`：列出该班级当天的全部每日任务（每条记录含 `studentId`）；班级不属于当前老师时返回 `404`。
- `PATCH /api/daily-tasks/{id}`：打勾/取消打勾，请求体 `{ "completed": true }`；任务所在班级不属于当前老师时返回 `404`。
- `DELETE /api/daily-tasks/{id}`：删除单条每日任务（用于老师撤销某个学生身上被自动同步的任务，不影响其他学生）；任务所在班级不属于当前老师时返回 `404`。
- `POST /api/classes/{classId}/dismissal`：将班级标记为当天已放学，请求体 `{ "date": "2026-08-06" }`，返回 `204`；仅影响当前托管班级。
- `DELETE /api/classes/{classId}/dismissal?date=YYYY-MM-DD`：撤销放学状态，返回 `204`。
- `GET /api/classes/{classId}/dismissal?date=YYYY-MM-DD`：查询放学状态，返回 `{ "dismissed": true }`。

卡片颜色规则（优先级从高到低，由前端依据上述接口返回的数据计算，后端不单独下发颜色字段）：绿色+完成印章（当天全部任务已完成）> 红色（班级已放学且该生仍有未完成任务）> 默认色（其余情况）。