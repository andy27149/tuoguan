import { defineConfig, devices } from '@playwright/test'

// 产品诊断 #46：最小可用的本地冒烟测试脚手架。不负责拉起 MySQL/MinIO/后端——
// 那套标准 dev 环境假定已经常驻运行（见 e2e/README.md 和技术架构文档"本地开发
// 环境"一节），这里只管前端 dev server 本身，本地已经在跑就直接复用不重复起。
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 30_000,
  },
})
