import { readFile } from 'node:fs/promises'
import { test, expect } from '@playwright/test'

const DEMO_PHONE = '13800000000'

async function login(page) {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo')
  await page.locator('input[autocomplete="current-password"]').fill('demo123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL('**/dashboard')
}

/** 示例简历的 id 由种子数据决定，不写死：从工作台的卡片点进去再读地址栏。 */
async function openDemoResume(page) {
  await login(page)
  await page.locator('.resume-card:not(.resume-card--new)').first().click()
  await page.waitForURL(/\/resumes\/\d+$/)
  return Number(page.url().match(/\/resumes\/(\d+)$/)[1])
}

test('编辑基本信息后，预览与分享页都能看到新内容', async ({ page, browser }) => {
  const resumeId = await openDemoResume(page)

  const section = page.locator('#section-basic_info')
  await section.getByRole('button', { name: '编辑' }).click()
  const nameInput = section.getByPlaceholder('例如：郝简历')
  await nameInput.waitFor()
  const newName = `郝简历 E2E ${Date.now().toString().slice(-6)}`
  await nameInput.fill(newName)
  await section.getByRole('button', { name: '保存模块' }).click()
  await expect(section).toContainText(newName)

  await page.goto(`/resumes/${resumeId}/preview`)
  await expect(page.locator('.paper-name')).toHaveText(newName)
  // 手机号在本人预览里是可见的，后面分享页隐藏它才有意义。
  await expect(page.locator('.resume-paper')).toContainText(DEMO_PHONE)

  await page.getByRole('button', { name: '分享' }).click()
  const dialog = page.locator('.share-dialog')
  await dialog.getByRole('checkbox', { name: '手机号' }).check()
  const [response] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/api/resumes/${resumeId}/shares`)
      && res.request().method() === 'POST'),
    dialog.getByRole('button', { name: '创建分享链接' }).click()
  ])
  expect(response.ok()).toBeTruthy()
  const { data: share } = await response.json()
  await expect(dialog.locator('.share-link-row code')).toContainText(`/share/${share.token}`)

  const guest = await browser.newContext()
  try {
    const guestPage = await guest.newPage()
    await guestPage.goto(`/share/${share.token}`)
    await expect(guestPage.locator('.paper-name')).toHaveText(newName)
    const html = await guestPage.content()
    expect(html).toContain(newName)
    expect(html).not.toContain(DEMO_PHONE)
  } finally {
    await guest.close()
  }
})

test('下载 PDF 得到有效的 PDF 文件', async ({ page }) => {
  test.setTimeout(120_000)
  const resumeId = await openDemoResume(page)
  const status = await page.request.get('/api/pdf/status')
  const body = await status.json()
  test.skip(!body?.data?.available, `服务端 PDF 引擎不可用：${body?.data?.message || 'unknown'}`)

  await page.goto(`/resumes/${resumeId}/preview`)
  await expect(page.locator('.paper-name')).toBeVisible()

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: '下载 PDF' }).click()
  ])
  expect(download.suggestedFilename()).toMatch(/\.pdf$/)
  const file = await download.path()
  const bytes = await readFile(file)
  expect(bytes.subarray(0, 5).toString()).toBe('%PDF-')
  expect(bytes.byteLength).toBeGreaterThan(1000)
})
