import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
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
