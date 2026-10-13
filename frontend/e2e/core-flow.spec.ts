import { test, expect, type Page } from '@playwright/test'

const ADMIN_PHONE = process.env.E2E_ADMIN_PHONE ?? '13900000001'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Test1234!'

// 199 前缀 + 时间戳尾号：和后端 IntegrationTestBase.uniquePhone() 同样的思路，
// 避免每次重复跑测试时手机号/名称冲突（诊断 #45 的教训——写死的号段迟早撞车）。
const RUN_ID = Date.now().toString().slice(-8)
const TEACHER_PHONE = `199${RUN_ID}`
const TEACHER_PASSWORD = 'Teacher123!'
const TEACHER_NAME = `E2E教师${RUN_ID}`
const COURSE_NAME = `E2E课外课${RUN_ID}`
const STUDENT_NAME = `E2E学生${RUN_ID}`

async function loginAsAdmin(page: Page) {
  await page.goto('/')
  await page.locator('#phone').fill(ADMIN_PHONE)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('button', { name: '账单管理' })).toBeVisible()
}

// 这条测试自建全部数据（新教师、新课外课、新学生），不依赖手工预埋的记录，同时
// 覆盖登录、消课、生成账单三个核心链路（产品诊断 #46）。
test('admin creates a course and student, teacher consumes it, admin bills and marks it paid', async ({ page }) => {
  await loginAsAdmin(page)

  // 1. 新建一个课外课老师。
  await page.getByRole('button', { name: '教师列表' }).click()
  await page.getByPlaceholder('手机号').fill(TEACHER_PHONE)
  await page.getByPlaceholder('教师姓名').fill(TEACHER_NAME)
  await page.getByPlaceholder('初始密码').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(`教师账号创建成功：手机号 ${TEACHER_PHONE}`)).toBeVisible()

  // 2. 新建一个课外课教学单元，负责教师指向刚才创建的老师，顺带配好单价方便后面出账单。
  await page.getByRole('button', { name: '教学单元' }).click()
  const offCampusTab = page.getByRole('tab', { name: '课外课' })
  if (await offCampusTab.isVisible()) {
    await offCampusTab.click()
  }
  await page.getByPlaceholder('课外课名称').fill(COURSE_NAME)
  await page.getByLabel('负责教师').selectOption({ label: TEACHER_NAME })
  await page.getByPlaceholder('时长（分钟）').fill('60')
  await page.getByPlaceholder('单价（选填）').fill('50')
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(COURSE_NAME).first()).toBeVisible()

  // 3. 新建学生并报名这门课外课。
  await page.getByRole('button', { name: '学生总览' }).click()
  await page.getByRole('button', { name: '+ 新建学生' }).click()
  await page.getByPlaceholder('姓名', { exact: true }).fill(STUDENT_NAME)
  await page.getByRole('button', { name: COURSE_NAME }).click()
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(STUDENT_NAME)).toBeVisible()

  // 4. 切到刚才新建的老师账号，给这个学生点名消课。
  await page.getByRole('button', { name: '退出登录' }).click()
  await page.locator('#phone').fill(TEACHER_PHONE)
  await page.locator('#password').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()

  // 新教师账号首次登录强制改密码。
  await expect(page.getByRole('heading', { name: '首次登录需修改密码' })).toBeVisible()
  await page.locator('#oldPassword').fill(TEACHER_PASSWORD)
  await page.locator('#newPassword').fill(TEACHER_PASSWORD)
  await page.locator('#confirmPassword').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '确认修改' }).click()

  await page.getByRole('button', { name: '消课' }).click()
  await expect(page.getByText(STUDENT_NAME)).toBeVisible()
  await page.getByRole('button', { name: /确认消课（\d+人）/ }).click()
  await page.getByRole('button', { name: '确认消课', exact: true }).click()
  await expect(page.getByText(/已确认消课|该日期已全部确认过/)).toBeVisible()
  await page.getByRole('button', { name: '知道了' }).click()

  // 5. 切回管理员，生成本月账单，确认这笔课外课费用体现在账单里，并标记已缴费。
  await page.getByRole('button', { name: '返回看板' }).click()
  await page.getByRole('button', { name: '退出登录' }).click()
  await loginAsAdmin(page)

  await page.getByRole('button', { name: '账单管理' }).click()
  await page.getByPlaceholder('搜索学生姓名').fill(STUDENT_NAME)
  await expect(page.getByRole('row', { name: new RegExp(STUDENT_NAME) })).toBeVisible()
  await page.getByRole('button', { name: '一键生成本月账单' }).click()
  await page.getByRole('button', { name: '确认生成' }).click()
  await expect(page.getByRole('dialog', { name: '账单生成完成' })).toBeVisible()
  await page.getByRole('button', { name: '知道了' }).click()

  const studentRow = page.getByRole('row', { name: new RegExp(STUDENT_NAME) })
  await expect(studentRow.getByRole('button', { name: /^¥50\.00$/ })).toBeVisible()

  await studentRow.getByRole('button', { name: '未缴费' }).click()
  await page.getByLabel('收款方式').selectOption('CASH')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(studentRow.getByRole('button', { name: /已缴费（现金）/ })).toBeVisible()
})
