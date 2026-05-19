import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'
import { data } from '../fixtures/test-data'

test.describe.serial('market product flow', () => {
  let listingUrl = ''
  let orderUrl = ''

  test('seller publishes a virtual preloaded listing', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/market/publish')
    await page.getByRole('textbox', { name: '标题' }).fill(data.virtualListingTitle)
    await page.getByRole('textbox', { name: '描述' }).fill(data.virtualListingDescription)
    await page.getByRole('spinbutton', { name: '价格' }).fill('3')
    await page.getByRole('spinbutton', { name: '库存数量' }).fill('1')
    await page.getByRole('textbox', { name: '预存内容' }).fill(data.virtualListingInventory)
    await page.getByRole('button', { name: '确认发布' }).click()
    await expect(page.getByText('发布成功')).toBeVisible()
    await gotoHash(page, '/market')
    await page.getByRole('link', { name: new RegExp(data.virtualListingTitle) }).click()
    await expect(page.getByText(data.virtualListingDescription).first()).toBeVisible()
    await expect(page.getByRole('button', { name: '安全下单' })).toBeVisible()
    listingUrl = page.url()
  })

  test('buyer creates order and sees it in buying orders', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    await page.goto(listingUrl)
    await page.getByRole('button', { name: '安全下单' }).click()
    await expect(page).toHaveURL(/#\/market\/orders\//)
    orderUrl = page.url()
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
    await gotoHash(page, '/market/orders/buying')
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
  })

  test('buyer can create an address', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    await gotoHash(page, '/market/addresses')
    await page.getByRole('textbox', { name: '收货人' }).fill(data.addressReceiver)
    await page.getByRole('textbox', { name: '手机号' }).fill(data.addressPhone)
    await page.getByRole('textbox', { name: '省份' }).fill('北京')
    await page.getByRole('textbox', { name: '城市' }).fill('北京')
    await page.getByRole('textbox', { name: '区县' }).fill('海淀')
    await page.getByRole('textbox', { name: '详细地址' }).fill('中关村测试路 1 号')
    await page.getByRole('textbox', { name: '邮编' }).fill('100000')
    await page.getByRole('checkbox', { name: '设为默认地址' }).check()
    await page.getByRole('button', { name: '新增地址' }).click()
    await expect(page.getByText('地址已创建')).toBeVisible()
  })

  test('seller sees order and can manage inventory', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/market/orders/selling')
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
    await page.goto(orderUrl)
    await expect(page.getByText('订单详情').first()).toBeVisible()
    await gotoHash(page, '/market/my-listings')
    await expect(page.getByRole('heading', { name: '先看商品状态，再决定进库存还是进卖单' })).toBeVisible()
    const listingRow = page.locator('.market-row').filter({ hasText: data.virtualListingTitle })
    await listingRow.getByRole('link', { name: '库存管理' }).click()
    await page.getByRole('textbox', { name: '追加库存' }).fill(data.virtualListingExtraInventory)
    await page.getByRole('button', { name: '追加库存' }).click()
    await expect(page.getByText(data.virtualListingExtraInventory)).toBeVisible()
    await page.getByRole('button', { name: '失效' }).click()
    await expect(page.getByText('库存已失效')).toBeVisible()
  })
})
