import { test, expect, type Page } from '@playwright/test'

const ADMIN_PHONE = process.env.E2E_ADMIN_PHONE ?? '13900000001'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Test1234!'

const RUN_ID = Date.now().toString().slice(-8)
const TEACHER_PHONE = `199${RUN_ID}1`
const TEACHER_PASSWORD = 'Teacher123!'
const TEACHER_NAME = `E2E点名教师${RUN_ID}`
const COURSE_NAME = `E2E点名课${RUN_ID}`
const STUDENT_A = `E2E点名甲${RUN_ID}`
const STUDENT_B = `E2E点名乙${RUN_ID}`

async function loginAsAdmin(page: Page) {
  await page.goto('/')
  await page.locator('#phone').fill(ADMIN_PHONE)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('button', { name: '账单管理', exact: true })).toBeVisible()
}

async function createStudent(page: Page, name: string) {
  await page.getByRole('button', { name: '学生总览' }).click()
  await page.getByRole('button', { name: '+ 新建学生' }).click()
  await page.getByPlaceholder('姓名', { exact: true }).fill(name)
  await page.getByRole('button', { name: COURSE_NAME }).click()
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(name)).toBeVisible()
}

// 产品诊断 #46：core-flow.spec.ts 只是顺带带过一次全勾选消课，这里专门针对点名消课这条
// 链路补两个它没覆盖的真实业务规则：1）可以只勾选部分学生提交（不是非全部不可）；
// 2）同一天重复提交时，已经记过账的学生不会被重复计费，只有新勾选的学生才会新增记录
// ——这是防止老师手滑重复点"确认消课"导致家长被多扣课时的关键保护。
test('teacher can partially roll call, and resubmitting the same date only charges newly-checked students', async ({ page }) => {
  await loginAsAdmin(page)

  await page.getByRole('button', { name: '教师列表' }).click()
  await page.getByPlaceholder('手机号').fill(TEACHER_PHONE)
  await page.getByPlaceholder('教师姓名').fill(TEACHER_NAME)
  await page.getByPlaceholder('初始密码').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(`教师账号创建成功：手机号 ${TEACHER_PHONE}`)).toBeVisible()

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

  await createStudent(page, STUDENT_A)
  await createStudent(page, STUDENT_B)

  await page.getByRole('button', { name: '退出登录' }).click()
  await page.locator('#phone').fill(TEACHER_PHONE)
  await page.locator('#password').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '首次登录需修改密码' })).toBeVisible()
  await page.locator('#oldPassword').fill(TEACHER_PASSWORD)
  await page.locator('#newPassword').fill(TEACHER_PASSWORD)
  await page.locator('#confirmPassword').fill(TEACHER_PASSWORD)
  await page.getByRole('button', { name: '确认修改' }).click()

  await page.getByRole('button', { name: '消课' }).click()
  await expect(page.getByText(STUDENT_A)).toBeVisible()
  await expect(page.getByText(STUDENT_B)).toBeVisible()

  // 花名册默认全勾选，先取消勾选乙，只给甲消课。原生 checkbox 做了视觉隐藏（无障碍
  // sr-only 写法，靠 <label> 转发点击），所以点 label 本身而不是直接操作 checkbox。
  const studentBCard = page.locator('li').filter({ hasText: STUDENT_B })
  await studentBCard.locator('label.roster-card').click()
  await expect(page.getByRole('button', { name: /确认消课（1人）/ })).toBeVisible()
  await page.getByRole('button', { name: /确认消课（1人）/ }).click()
  await page.getByRole('button', { name: '确认消课', exact: true }).click()
  await expect(page.getByText('已确认消课，新增 1 条记录')).toBeVisible()
  await page.getByRole('button', { name: '知道了' }).click()

  // 同一天再勾上乙重新提交：甲已经记过账，不应该被重复计费，只有乙是新增的一条。
  await studentBCard.locator('label.roster-card').click()
  await expect(page.getByRole('button', { name: /确认消课（2人）/ })).toBeVisible()
  await page.getByRole('button', { name: /确认消课（2人）/ }).click()
  await page.getByRole('button', { name: '确认消课', exact: true }).click()
  await expect(page.getByText('已确认消课，新增 1 条记录')).toBeVisible()
})
