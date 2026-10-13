import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  // 部署在跟别的系统共用的域名下的子路径（比如 /tuoguan/）时用 VITE_BASE_PATH
  // 覆盖，见 frontend/Dockerfile 和 docker-compose.yml 里的同名构建参数、
  // DEPLOY.md 第七节。默认 '/' 不影响独占域名部署和本地开发。
  base: process.env.VITE_BASE_PATH || '/',
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // e2e/ 下是 Playwright 规格文件（产品诊断 #46），用的是 @playwright/test 的
    // test()，不是 vitest 的，两边文件名都是 *.spec.ts 容易被 vitest 的默认匹配
    // 规则一起扫进去，必须显式排除。
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/cypress/**',
      '**/.{idea,git,cache,output,temp}/**',
      '**/{karma,rollup,webpack,vite,vitest,jest,ava,babel,nyc,cypress,tsup,build}.config.*',
      '**/e2e/**',
    ],
    environment: 'jsdom',
    environmentOptions: {
      jsdom: {
        url: 'http://localhost/',
      },
    },
    globals: true,
    setupFiles: './src/setupTests.ts',
    // Node 22+ ships an experimental global `localStorage` that vitest's jsdom
    // environment does not override, shadowing jsdom's real implementation.
    // Disabling it in the worker process lets jsdom's localStorage win.
    execArgv: ['--no-experimental-webstorage'],
  },
})
