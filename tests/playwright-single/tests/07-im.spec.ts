import { expect, test } from '../fixtures/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'
import { runId } from '../fixtures/test-data'

const focusedConversationId = `${accounts.aaa.userId}_${accounts.bbb.userId}`

test.describe('IM product flow @regression', () => {
  test('authenticated user can load the cursor conversation inbox @regression', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    const responsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'GET'
        && url.pathname === '/api/im/conversations/page'
    })
    await gotoHash(page, '/messages')
    const response = await responsePromise
    expect(response.status()).toBe(200)
    const body = await response.json()
    expect(Array.isArray(body.data?.items)).toBe(true)
    expect(typeof body.data?.hasMore).toBe('boolean')

    const items = body.data.items
    if (items.length === 0) {
      await expect(page.getByText('暂无会话')).toBeVisible()
    } else {
      // hash 历史模式下 href 形如 "#/messages/<cid>"，用包含匹配。
      await expect(page.locator('a[href*="/messages/"]').first()).toBeVisible()
    }
    await expect(page.locator('.conversations-empty.ui-state--error')).toHaveCount(0)
  })

  test('focused conversation thread keeps send states and history order through the realtime link @smoke @regression', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    await gotoHash(page, `/messages/${focusedConversationId}`)

    // 视图复用 /api/im/sessions + ticket 与现有 realtime client，等待实时链路认证完成。
    await expect(page.getByText('实时已就绪')).toBeVisible()
    await expect(page.getByRole('link', { name: '返回收件箱' })).toBeVisible()

    // 键盘/焦点：composer 可聚焦，反向 / 正向 Tab 经过可交互元素且 focus ring 可见。
    const composer = page.locator('#conversation-message-input')
    const sendButton = page.getByRole('button', { name: '发送消息' })
    const content = `Playwright 聚焦会话消息 ${runId} ${Date.now()}`
    await composer.click()
    await expect(composer).toBeFocused()
    // 空输入时发送按钮禁用、不可聚焦；先填入内容再沿 Tab 顺序检查前后控件。
    await composer.fill(content)
    await page.keyboard.press('Shift+Tab')
    const ringOnPreviousControl = await page.evaluate(() => {
      const el = document.activeElement
      return el ? getComputedStyle(el).boxShadow : ''
    })
    expect(ringOnPreviousControl).not.toBe('none')
    expect(ringOnPreviousControl).not.toBe('')
    await page.keyboard.press('Tab')
    await expect(composer).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(sendButton).toBeFocused()
    const ringOnSendButton = await page.evaluate(() => {
      const el = document.activeElement
      return el ? getComputedStyle(el).boxShadow : ''
    })
    expect(ringOnSendButton).not.toBe('none')
    expect(ringOnSendButton).not.toBe('')

    // Enter 发送：先出现乐观行，committed 回执到达后「发送中」消失，不出现失败态。
    await page.keyboard.press('Shift+Tab')
    await expect(composer).toBeFocused()
    await page.keyboard.press('Enter')
    const sentRow = page.locator('.message-row', { hasText: content })
    await expect(sentRow).toBeVisible()
    await expect(sentRow.getByText('发送中…')).toHaveCount(0)
    await expect(sentRow.getByText('发送失败')).toHaveCount(0)

    // 列表 ↔ 详情往返后消息仍在尾部：HTTP history 是持久化事实，顺序与定位不变。
    await gotoHash(page, '/messages')
    await expect(page.getByText(content).first()).toBeVisible()
    await page.locator(`a[href*="/messages/${focusedConversationId}"]`).first().click()
    const persistedRow = page.locator('.message-row', { hasText: content })
    await expect(persistedRow).toBeVisible()
    await expect(persistedRow.getByText('发送中…')).toHaveCount(0)
    await expect(page.locator('.message-row').last()).toContainText(content)
  })
})
