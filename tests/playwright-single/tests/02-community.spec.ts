import { expect, test } from '../fixtures/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'
import { data } from '../fixtures/test-data'

test.describe.serial('community product flow @regression', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
  })

  test('post creation, detail, like, bookmark, and comment work @regression', async ({ page }) => {
    await gotoHash(page, '/posts')
    await page.locator('.posts-feed-compose-strip').click()
    const composer = page.locator('.posts-composer')
    await composer.getByRole('textbox', { name: '标题' }).fill(data.postTitle)
    await composer.getByPlaceholder('正文内容...').fill(data.postBody)
    const tagDraft = composer.locator('input[name="post-tag-draft"]')
    await tagDraft.fill(data.postTag)
    await tagDraft.press('Enter')
    await composer.getByRole('button', { name: '发布' }).click()
    await page.getByRole('button', { name: '立即查看帖子' }).click()
    await expect(page).toHaveURL(/#\/posts\/[^/]+$/)
    await expect(page.getByRole('heading', { name: data.postTitle })).toBeVisible()
    await expect(page.getByText(data.postBody).first()).toBeVisible()
    await page.getByRole('button', { name: '点赞' }).first().click()
    await page.getByRole('button', { name: '收藏' }).click()
    await page.getByPlaceholder('写下你的观点…（支持 Markdown）').fill(data.postComment)
    await page.getByRole('button', { name: '提交' }).click()
    await expect(page.getByText(data.postComment)).toBeVisible()
    const bookmarksResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'GET'
        && url.pathname === '/api/bookmarks'
        && url.searchParams.get('page') === '0'
        && url.searchParams.get('size') === '10'
    })
    await gotoHash(page, '/bookmarks')
    const bookmarksResponse = await bookmarksResponsePromise
    expect(bookmarksResponse.status()).toBe(200)
    const bookmarksBody = await bookmarksResponse.json()
    expect(Array.isArray(bookmarksBody.data)).toBe(true)
    expect(bookmarksBody.data.some((post) => post.title === data.postTitle)).toBe(true)
    await expect(page.getByText(data.postTitle).first()).toBeVisible()
  })

  test('profile, social lists, notices, and settings pages load @regression', async ({ page }) => {
    await gotoHash(page, `/users/${accounts.aaa.userId}`)
    await expect(page.getByText('公开身份').first()).toBeVisible()
    await gotoHash(page, `/users/${accounts.aaa.userId}/followees`)
    await expect(page.getByText('关注').first()).toBeVisible()
    await gotoHash(page, `/users/${accounts.aaa.userId}/followers`)
    await expect(page.getByText('粉丝').first()).toBeVisible()
    await gotoHash(page, '/notices')
    await expect(page.getByText('通知').first()).toBeVisible()
    await gotoHash(page, '/notices/comment')
    await expect(page.getByText('通知').first()).toBeVisible()
    await gotoHash(page, '/settings')
    await expect(page.getByText('头像上传').first()).toBeVisible()
  })
})
