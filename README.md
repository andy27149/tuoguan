# 托管班学生学习管理系统

个人/小团队副业项目。技术栈：React + Vite + TS + Tailwind（前端），Spring Boot + MySQL + MinIO（后端），Docker Compose 编排。首个试用机构已于 2026-09-02 上线并产生真实数据，目前处于业务验证/反馈迭代阶段。

## 文档导航

这份 README 只覆盖本地开发命令和项目最早期（阶段1-4）的开发日志——**要了解当前功能现状、已知问题、业务规则或代码架构，请从下面这些文档进去，不要依赖本文件下方的"阶段N"记录**（那部分从阶段4之后就没再按这个粒度继续写，是历史存档，不是最新状态）：

| 想知道什么 | 看哪份文档 |
|---|---|
| 整个文档体系怎么组织、该先看哪篇 | [docs/INDEX.md](./docs/INDEX.md) |
| 系统目前有哪些已知问题/设计缺口，哪些已修复 | [docs/功能全景与产品诊断.md](./docs/功能全景与产品诊断.md) |
| 业务规则、角色权限、计费逻辑 | [docs/business/业务架构.md](./docs/business/业务架构.md)、[docs/business/业务流程.md](./docs/business/业务流程.md) |
| 技术栈、目录结构、数据库迁移、测试基建 | [docs/architecture/技术架构.md](./docs/architecture/技术架构.md)、[docs/architecture/代码架构.md](./docs/architecture/代码架构.md) |
| 某个具体页面有哪些功能点/依赖哪些接口 | [docs/pages/INDEX.md](./docs/pages/INDEX.md) |
| 生产部署步骤 | [DEPLOY.md](./DEPLOY.md) |

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
4. 浏览器打开 `http://localhost:5173`，未登录时会看到登录页；用种子命令创建的账号登录后，首次登录会强制跳转到修改密码页，之后进入看板

## 常用命令

- 后端测试：`cd backend && mvn test`
- 前端测试：`cd frontend && pnpm vitest run`
- 端到端冒烟测试（需要本地标准 dev 环境已启动，见 `frontend/e2e/README.md`）：`cd frontend && pnpm test:e2e`
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

---

> **以下是项目早期（阶段1-4）的开发日志，已停更，仅作历史存档**。记录的接口/行为在当时准确，但后续有大量未在此补记的迭代（账号体系演进、托管班/课外课合并为统一教学单元、计费与账单、平台管理、家长分享等），这些都已经在上面"文档导航"列出的 `docs/` 体系里持续维护。判断当前某个功能到底是什么样，请去查 `docs/`，不要以下面的阶段记录为准。

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

## 阶段1-3 前端：登录与看板

前端（`frontend/`）已实现阶段1-3全部功能对应的界面，不使用路由库或全局状态库——认证状态是简单的 `loading/anonymous/mustChangePassword/authenticated` 联合类型，班级 Tab 切换是页面内 `useState`。

- 登录页 → 强制改密页（`mustChangePassword=true` 时）→ 看板页，JWT 存于 `localStorage`（key: `tuoguan_token`），由 `apiFetch` 统一附加 `Authorization` 头。
- 看板页：顶部班级 Tab 切换；"批量分配给全班"从任务库多选后一次性下发给全班在读学生；每个学生卡片可从任务库选择或定制任务、勾选完成、删除单条任务；"放学/撤销放学"按钮切换班级放学状态。
- 前端对"按学校班级批量同步"新增任务的处理：由于后端接口只返回目标学生本人创建的记录，同步出去的记录需要通过班级任务列表接口获取，因此新增任务后前端会整体重新拉取当天任务列表，而不是尝试合并接口返回的局部结果；勾选完成/删除单条任务是单行操作，使用乐观更新+失败回滚，不触发整体重新拉取。
- 卡片颜色计算逻辑独立为纯函数 `frontend/src/kanban/cardStatus.ts`（`computeCardStatus`），并配有独立单元测试，避免颜色优先级规则被埋没在组件渲染逻辑里而难以覆盖测试。

## 阶段4：学生头像

头像存储于 MinIO（私有桶），仅登录老师可上传/查看，不开放学生/家长自行上传入口（对应 PRD 第6.5节的隐私要求）。上传经后端中转（前端 → 后端 → MinIO），桶本身保持全私有，读取一律通过后端签发的 1 小时有效期预签名 URL。

### MinIO 环境变量

```bash
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<对应 MINIO_ROOT_USER>
MINIO_SECRET_KEY=<对应 MINIO_ROOT_PASSWORD>
MINIO_BUCKET=avatars
```

`docker compose up` 已自动完成上述变量的传递（`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` 来自 `.env`）；桶不存在时后端启动会自动创建。

### 阶段4新增接口

- `POST /api/students/{id}/avatar`：需携带 `Authorization: Bearer <token>`，`multipart/form-data` 请求，字段名 `file`；仅接受 `image/jpeg`/`image/png`/`image/webp`，其他类型返回 `400`；单文件大小上限 5MB（超限由 Spring 自动返回 `400`）。学生不属于当前老师时返回 `404`。上传成功后旧头像对象会从 MinIO 删除，返回更新后的学生信息（含新的预签名 `avatarUrl`）。
- 学生相关接口（`GET /api/classes/{classId}/students` 等）返回体新增 `avatarUrl` 字段（`string | null`），未上传过头像时为 `null`。

### 前端交互

学生卡片左侧头像为可点击按钮（未上传时显示姓名首字），点击后调起系统拍照/相册选择（`<input type="file" accept="image/jpeg,image/png,image/webp">`），选择文件后立即上传并原地替换头像，无需额外确认步骤。