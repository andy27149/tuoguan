import { test, expect, type Page } from '@playwright/test'

const ADMIN_PHONE = process.env.E2E_ADMIN_PHONE ?? '13900000001'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Test1234!'

const RUN_ID = Date.now().toString().slice(-8)
const TEACHER_PHONE = `199${RUN_ID}2`
const TEACHER_PASSWORD = 'Teacher123!'
const TEACHER_NAME = `E2E分享教师${RUN_ID}`
const CLASS_NAME = `E2E分享班${RUN_ID}`
const STUDENT_NAME = `E2E分享生${RUN_ID}`

async function loginAsAdmin(page: Page) {
  await page.goto('/')
  await page.locator('#phone').fill(ADMIN_PHONE)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('button', { name: '账单管理', exact: true })).toBeVisible()
}

// 产品诊断 #46：家长分享页是唯一一个家长真实会打开、且完全不需要登录的公开页面，
// 此前完全没有自动化覆盖——前端改动（字段改名/组件重构）或后端 PublicShareService
// 改坏，只能等家长投诉才会发现。这条测试覆盖"老师打卡→家长打开专属链接→看到刚才
// 那次打卡"的最小完整链路，用全新、未登录的浏览器上下文打开分享链接，贴近家长真实
// 使用场景（家长设备上从来没有任何登录态）。
test('parent share link opens without login and reflects a teacher action', async ({ page, context }) => {
  await loginAsAdmin(page)

  await page.getByRole('button', { name: '教师列表' }).click()
  await page.getByPlaceholder('手机号').fill(TEACHER_PHONE)
  await page.getByPlaceholder('教师姓名').fill(TEACHER_NAME)
  await page.getByPlaceholder('初始密码').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(`教师账号创建成功：手机号 ${TEACHER_PHONE}`)).toBeVisible()

  // 新建一个托管班（默认 tab 就是托管班，不用切 tab），负责教师指向刚建的老师。
  await page.getByRole('button', { name: '教学单元' }).click()
  await page.getByPlaceholder('托管班名称').fill(CLASS_NAME)
  await page.getByLabel('负责教师').selectOption({ label: TEACHER_NAME })
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(CLASS_NAME).first()).toBeVisible()

  await page.getByRole('button', { name: '学生总览' }).click()
  await page.getByRole('button', { name: '+ 新建学生' }).click()
  await page.getByPlaceholder('姓名', { exact: true }).fill(STUDENT_NAME)
  await page.getByLabel('托管班（选填）').selectOption({ label: CLASS_NAME })
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(STUDENT_NAME)).toBeVisible()

  await page.getByRole('button', { name: '退出登录' }).click()
  await page.locator('#phone').fill(TEACHER_PHONE)
  await page.locator('#password').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '首次登录需修改密码' })).toBeVisible()
  await page.locator('#oldPassword').fill(TEACHER_PASSWORD)
  await page.locator('#newPassword').fill(TEACHER_PASSWORD)
  await page.locator('#confirmPassword').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '确认修改' }).click()

  // 老师给这个学生打卡到校，这是一个会立即体现在分享页当天详情里的真实动作。
  const studentCard = page.locator('[data-testid="student-card"]').filter({ hasText: STUDENT_NAME })
  await expect(studentCard).toBeVisible()
  await studentCard.getByRole('button', { name: '🕐 到了' }).click()
  await expect(studentCard.getByRole('button', { name: /🕐 \d{2}:\d{2}/ })).toBeVisible()

  // 拿到家长专属链接。
  await studentCard.getByRole('button', { name: '🔗 链接' }).click()
  const shareUrl = await page.locator('.share-link-input').inputValue()
  expect(shareUrl).toContain('/share/')

  // 用全新、未登录的浏览器上下文打开——贴近家长设备从来没有任何登录态这个事实。
  const parentPage = await context.newPage()
  await parentPage.goto(shareUrl)

  await expect(parentPage.getByText('你好呀！')).toBeVisible()
  await expect(parentPage.getByText(STUDENT_NAME)).toBeVisible()
  // 用 exact 匹配，避免命中当天详情面板里"2026-10-15"这种以当前月份开头的日期文本。
  const currentMonth = new Date().toISOString().slice(0, 7)
  await expect(parentPage.getByText(currentMonth, { exact: true })).toBeVisible()
})
