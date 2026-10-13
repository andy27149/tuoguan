# E2E 冒烟测试（产品诊断 #46）

仓库此前完全没有真实浏览器端到端测试，这是最小可用的冒烟骨架，覆盖登录、生成账单、
消课三条核心链路，用 Playwright 驱动真实浏览器。

## 前置条件

1. 本地标准 dev 环境已经在跑（见 `docs/architecture/技术架构.md`"本地开发环境"一节）：
   - `tuoguan-local-test-mysql`（23310）+ `tuoguan-local-test-minio`（19000）常驻容器
   - 后端 `mvn spring-boot:run`（配好 `DB_PORT=23310` 等环境变量）跑在 8080
   - 前端本身不需要手动起——Playwright 的 `webServer` 配置会复用已经在跑的
     `pnpm dev`（5173），没在跑时会帮你起一个

2. 需要一个已知的管理员账号可以登录。用仓库自带的 `SeedCommandLineRunner`
   一次性初始化（只需做一次，账号会一直留在本地 test 库里）：

   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.arguments="--seed --seed.institution=E2E测试机构 --seed.phone=13900000001 --seed.password=Test1234!"
   ```

   如果手机号已经存在会报错退出，属预期——说明之前已经初始化过，忽略即可。

3. 通过环境变量把账号传给测试（不写死在代码里）：

   ```bash
   export E2E_ADMIN_PHONE=13900000001
   export E2E_ADMIN_PASSWORD='Test1234!'
   ```

   不设置时默认用上面这组值，方便本地按文档操作后直接跑。

## 运行

```bash
cd frontend
pnpm test:e2e
```

## 范围说明

- 只覆盖 2 条 spec：`login.spec.ts`（登录）、`core-flow.spec.ts`（新建教学单元 →
  新建学生报名 → 消课 → 生成账单 → 标记已缴费），自建数据而不依赖手工预埋的学生/
  课程，避免脆弱。
- 不负责 CI 接入——那是诊断 #44（仓库完全没有 CI）的范围，这批只搭本地可跑的骨架。
