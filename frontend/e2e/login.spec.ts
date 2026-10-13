import { test, expect } from '@playwright/test'

const ADMIN_PHONE = process.env.E2E_ADMIN_PHONE ?? '13900000001'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Test1234!'

// 产品诊断 #46：仓库此前零端到端测试，这是最基础的登录冒烟——覆盖真实浏览器里
// 填表单、提交、拿到真实 JWT、看到后台首屏这条最常用也最容易被意外改坏的链路。
test('admin can log in and reach the institution overview', async ({ page }) => {
  await page.goto('/')

  await page.locator('#phone').fill(ADMIN_PHONE)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page.getByRole('button', { name: '账单管理' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '课时不足预警' })).toBeVisible()
})
