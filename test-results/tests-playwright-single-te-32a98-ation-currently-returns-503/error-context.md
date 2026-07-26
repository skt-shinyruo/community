# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: tests/playwright-single/tests/99-known-issues.spec.ts >> known current single issues >> public drive share verification currently returns 503
- Location: tests/playwright-single/tests/99-known-issues.spec.ts:42:3

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 503
Received: 200
```

# Page snapshot

```yaml
- generic [ref=e2]:
  - generic [ref=e3]:
    - complementary [ref=e4]:
      - generic [ref=e5]:
        - generic [ref=e6]:
          - link "返回帖子列表" [ref=e7] [cursor=pointer]:
            - /url: "#/posts"
            - generic [ref=e8]: C
            - generic [ref=e9]:
              - generic [ref=e10]: Community
              - generic [ref=e11]: 社区工作台
          - button "折叠侧边栏" [ref=e12] [cursor=pointer]:
            - img [ref=e13]
        - generic [ref=e15]:
          - generic [ref=e16]:
            - generic [ref=e17]: 社区
            - link "帖子" [ref=e18] [cursor=pointer]:
              - /url: "#/posts"
              - img [ref=e20]
              - generic [ref=e23]: 帖子
            - link "搜索" [ref=e24] [cursor=pointer]:
              - /url: "#/search"
              - img [ref=e26]
              - generic [ref=e29]: 搜索
            - link "收藏" [ref=e30] [cursor=pointer]:
              - /url: "#/bookmarks"
              - img [ref=e32]
              - generic [ref=e34]: 收藏
            - link "我的主页" [ref=e35] [cursor=pointer]:
              - /url: "#/users/00000000-0000-7000-8000-000000000002"
              - img [ref=e37]
              - generic [ref=e40]: 我的主页
          - generic [ref=e41]:
            - generic [ref=e42]: 交易
            - link "市场" [ref=e43] [cursor=pointer]:
              - /url: "#/market"
              - img [ref=e45]
              - generic [ref=e47]: 市场
            - link "发布商品" [ref=e48] [cursor=pointer]:
              - /url: "#/market/publish"
              - img [ref=e50]
              - generic [ref=e53]: 发布商品
            - link "我的出售" [ref=e54] [cursor=pointer]:
              - /url: "#/market/my-listings"
              - img [ref=e56]
              - generic [ref=e59]: 我的出售
            - link "我的购买" [ref=e60] [cursor=pointer]:
              - /url: "#/market/orders/buying"
              - img [ref=e62]
              - generic [ref=e64]: 我的购买
            - link "出售订单" [ref=e65] [cursor=pointer]:
              - /url: "#/market/orders/selling"
              - img [ref=e67]
              - generic [ref=e70]: 出售订单
            - link "收货地址" [ref=e71] [cursor=pointer]:
              - /url: "#/market/addresses"
              - img [ref=e73]
              - generic [ref=e75]: 收货地址
          - generic [ref=e76]:
            - generic [ref=e77]: 个人
            - link "积分钱包" [ref=e78] [cursor=pointer]:
              - /url: "#/wallet"
              - img [ref=e80]
              - generic [ref=e82]: 积分钱包
            - link "网盘" [ref=e83] [cursor=pointer]:
              - /url: "#/drive"
              - img [ref=e85]
              - generic [ref=e87]: 网盘
            - link "通知" [ref=e88] [cursor=pointer]:
              - /url: "#/notices"
              - img [ref=e90]
              - generic [ref=e93]: 通知
            - link "私信" [ref=e94] [cursor=pointer]:
              - /url: "#/messages"
              - img [ref=e96]
              - generic [ref=e98]: 私信
            - link "设置" [ref=e99] [cursor=pointer]:
              - /url: "#/settings"
              - img [ref=e101]
              - generic [ref=e104]: 设置
        - link "B bbb 继续你的讨论与阅读" [ref=e106] [cursor=pointer]:
          - /url: "#/users/00000000-0000-7000-8000-000000000002"
          - generic [ref=e108]: B
          - generic [ref=e109]:
            - generic [ref=e111]: bbb
            - generic [ref=e112]: 继续你的讨论与阅读
    - generic [ref=e113]:
      - generic [ref=e114]:
        - generic [ref=e115]:
          - button "折叠或展开侧边栏" [ref=e116] [cursor=pointer]:
            - img [ref=e117]
          - generic [ref=e118]:
            - generic [ref=e119]: Files
            - generic [ref=e121]: 网盘
            - generic [ref=e122]: 管理私有文件、分享链接和回收站。
        - generic [ref=e124]:
          - button "打开页面偏好设置" [ref=e126] [cursor=pointer]:
            - img [ref=e127]
          - link "B bbb" [ref=e131] [cursor=pointer]:
            - /url: "#/users/00000000-0000-7000-8000-000000000002"
            - generic [ref=e133]: B
            - generic [ref=e135]: bbb
          - button "登出" [ref=e136] [cursor=pointer]:
            - img [ref=e137]
      - generic [ref=e141]:
        - link "首页" [ref=e143] [cursor=pointer]:
          - /url: "#/"
        - generic [ref=e144]:
          - generic [ref=e145]:
            - generic [ref=e146]: 网盘
            - generic [ref=e147]: 0 B / 10 GB·0% 已用·私有文件、分享链接和社区附件
          - generic [ref=e148]:
            - button "刷新" [ref=e149] [cursor=pointer]
            - button "新建文件夹" [ref=e150] [cursor=pointer]
            - generic [ref=e151] [cursor=pointer]:
              - text: 上传
              - button "上传" [ref=e152]
        - generic [ref=e153]:
          - generic [ref=e154]:
            - generic [ref=e155]: 已用空间
            - strong [ref=e156]: 0 B
          - generic [ref=e157]:
            - generic [ref=e158]: 剩余空间
            - strong [ref=e159]: 10 GB
          - generic [ref=e160]:
            - generic [ref=e161]: 当前目录
            - strong [ref=e162]: 我的文件
          - generic [ref=e163]:
            - generic [ref=e164]: 当前条目
            - strong [ref=e165]: "5"
        - generic [ref=e166]: 分享链接已生成
        - generic [ref=e167]:
          - generic [ref=e168]:
            - generic [ref=e169]:
              - tablist "网盘模式" [ref=e170]:
                - button "我的文件" [ref=e171] [cursor=pointer]
                - button "分享管理" [ref=e172] [cursor=pointer]
                - button "回收站" [ref=e173] [cursor=pointer]
              - generic [ref=e174]:
                - searchbox "搜索文件" [ref=e175]
                - button "搜索" [ref=e176] [cursor=pointer]
            - generic "文件夹路径" [ref=e177]:
              - button "我的文件" [ref=e178] [cursor=pointer]
            - generic [ref=e179]:
              - button "Playwright 分享保留 20260726051602 文件夹 可用 可分享" [ref=e180] [cursor=pointer]:
                - generic [ref=e181]:
                  - strong [ref=e182]: Playwright 分享保留 20260726051602
                  - generic [ref=e183]: 文件夹
                - generic [ref=e184]:
                  - generic [ref=e185]: 可用
                  - generic [ref=e186]: 可分享
              - button "Playwright 已知问题分享 20260726045021 文件夹 可用 可分享" [ref=e187] [cursor=pointer]:
                - generic [ref=e188]:
                  - strong [ref=e189]: Playwright 已知问题分享 20260726045021
                  - generic [ref=e190]: 文件夹
                - generic [ref=e191]:
                  - generic [ref=e192]: 可用
                  - generic [ref=e193]: 可分享
              - button "Playwright 已知问题分享 20260726053845 文件夹 可用 可分享" [ref=e194] [cursor=pointer]:
                - generic [ref=e195]:
                  - strong [ref=e196]: Playwright 已知问题分享 20260726053845
                  - generic [ref=e197]: 文件夹
                - generic [ref=e198]:
                  - generic [ref=e199]: 可用
                  - generic [ref=e200]: 可分享
              - button "Playwright 文件夹 20260726051242 文件夹 可用 可分享" [ref=e201] [cursor=pointer]:
                - generic [ref=e202]:
                  - strong [ref=e203]: Playwright 文件夹 20260726051242
                  - generic [ref=e204]: 文件夹
                - generic [ref=e205]:
                  - generic [ref=e206]: 可用
                  - generic [ref=e207]: 可分享
              - button "Playwright 文件夹 20260726051602 文件夹 可用 可分享" [ref=e208] [cursor=pointer]:
                - generic [ref=e209]:
                  - strong [ref=e210]: Playwright 文件夹 20260726051602
                  - generic [ref=e211]: 文件夹
                - generic [ref=e212]:
                  - generic [ref=e213]: 可用
                  - generic [ref=e214]: 可分享
          - generic [ref=e215]:
            - generic [ref=e217]:
              - generic [ref=e218]: Playwright 已知问题分享 20260726053845
              - generic [ref=e219]: 文件夹·可用
            - generic [ref=e220]:
              - generic [ref=e221]:
                - term [ref=e222]: 类型
                - definition [ref=e223]: 文件夹
              - generic [ref=e224]:
                - term [ref=e225]: 位置
                - definition [ref=e226]: 我的文件
              - generic [ref=e227]:
                - term [ref=e228]: 状态
                - definition [ref=e229]: 可用
              - generic [ref=e230]:
                - term [ref=e231]: 可见性
                - definition [ref=e232]: 可分享
            - generic [ref=e233]:
              - generic [ref=e234]:
                - generic [ref=e235]: 重命名
                - textbox "重命名" [ref=e236]:
                  - /placeholder: 输入新名称
                  - text: Playwright 已知问题分享 20260726053845
              - generic [ref=e237]:
                - button "重命名" [ref=e238] [cursor=pointer]
                - button "移动到当前目录" [ref=e239] [cursor=pointer]
              - generic [ref=e240]:
                - button "分享" [ref=e241] [cursor=pointer]
                - button "删除" [ref=e242] [cursor=pointer]
            - generic [ref=e243]:
              - generic [ref=e245]:
                - generic [ref=e246]: 分享管理
                - generic [ref=e247]: 默认私有；生成链接后可用于帖子附件、成员分享或虚拟商品交付。
              - generic [ref=e248]: Playwright 已知问题分享 20260726053845
              - generic [ref=e249]:
                - generic [ref=e250]:
                  - generic [ref=e251]: 提取码
                  - textbox "提取码" [ref=e252]:
                    - /placeholder: ""
                - generic [ref=e253]:
                  - generic [ref=e254]: 有效期
                  - textbox "有效期" [ref=e255]: 2026-07-27T13:38
                - button "生成分享链接" [ref=e256] [cursor=pointer]
              - article [ref=e258]:
                - generic [ref=e259]:
                  - strong [ref=e260]: Playwright 已知问题分享 20260726053845
                  - generic [ref=e261]: 2026-07-27T05:38:00Z
                  - code [ref=e262]: http://localhost:12881/#/drive/s/5Q9Yk2WwD7bnaANKbtBCklnH
                - generic [ref=e263]:
                  - button "复制链接" [ref=e264] [cursor=pointer]
                  - button "撤销" [ref=e265] [cursor=pointer]
  - button [ref=e266] [cursor=pointer]:
    - img [ref=e267]
```

# Test source

```ts
  1  | import { expect, test } from '@playwright/test'
  2  | import { accounts } from '../fixtures/accounts'
  3  | import { loginViaUi } from '../fixtures/auth'
  4  | import { apiBaseUrl, gotoHash } from '../fixtures/helpers'
  5  | import { data } from '../fixtures/test-data'
  6  | 
  7  | test.describe.serial('known current single issues', () => {
  8  |   test('bookmarks endpoint currently returns 503', async ({ page }) => {
  9  |     await loginViaUi(page, accounts.aaa)
  10 |     const responsePromise = page.waitForResponse((response) => response.url().includes('/api/bookmarks?page=0&size=10'))
  11 |     await gotoHash(page, '/bookmarks')
  12 |     const response = await responsePromise
  13 |     expect(response.status()).toBe(503)
  14 |   })
  15 | 
  16 |   test('im conversations endpoint currently returns 403', async ({ page }) => {
  17 |     await loginViaUi(page, accounts.bbb)
  18 |     const responsePromise = page.waitForResponse((response) => response.url().includes('/api/im/conversations'))
  19 |     await gotoHash(page, '/messages')
  20 |     const response = await responsePromise
  21 |     expect(response.status()).toBe(403)
  22 |   })
  23 | 
  24 |   test('some admin body routes currently render only shell content', async ({ page }) => {
  25 |     await loginViaUi(page, accounts.admin)
  26 |     await gotoHash(page, '/admin/users')
  27 |     await expect(page.getByText('用户管理').first()).toBeVisible()
  28 |     await expect(page.getByText('搜索用户')).toHaveCount(0)
  29 |     await gotoHash(page, '/admin/market/disputes')
  30 |     await expect(page.getByText('争议裁定').first()).toBeVisible()
  31 |     await expect(page.getByText(/争议 #/)).toHaveCount(0)
  32 |     await gotoHash(page, '/dev')
  33 |     await expect(page.getByText('联调').first()).toBeVisible()
  34 |     await expect(page.getByText('开发检查台')).toHaveCount(0)
  35 |   })
  36 | 
  37 |   test('gateway remains available while known issues are reproduced', async ({ request }) => {
  38 |     const response = await request.get(`${apiBaseUrl}/actuator/health`)
  39 |     expect(response.status()).toBe(200)
  40 |   })
  41 | 
  42 |   test('public drive share verification currently returns 503', async ({ page }) => {
  43 |     await loginViaUi(page, accounts.bbb)
  44 |     await gotoHash(page, '/drive')
  45 |     await page.getByRole('button', { name: '新建文件夹' }).click()
  46 |     await page.getByRole('textbox', { name: '文件夹名称' }).fill(data.knownIssueShareFolder)
  47 |     await page.getByRole('button', { name: '确认' }).click()
  48 |     await expect(page.getByText(data.knownIssueShareFolder).first()).toBeVisible()
  49 |     await page.locator('.drive-entry-row').filter({ hasText: data.knownIssueShareFolder }).click()
  50 |     await page.getByRole('button', { name: '分享', exact: true }).click()
  51 |     await page.getByRole('textbox', { name: '提取码' }).fill(data.shareCode)
  52 |     await page.getByRole('button', { name: '生成分享链接' }).click()
  53 |     await expect(page.getByText('分享链接已生成')).toBeVisible()
  54 |     const shareToken = await page.evaluate(() => {
  55 |       const match = document.body.innerText.match(/#\/drive\/s\/([A-Za-z0-9_-]+)/)
  56 |       return match?.[1] || ''
  57 |     })
  58 |     expect(shareToken).not.toBe('')
  59 | 
  60 |     const response = await page.request.post(`${apiBaseUrl}/api/drive/shares/${encodeURIComponent(shareToken)}/verify`, {
  61 |       data: { password: data.shareCode }
  62 |     })
> 63 |     expect(response.status()).toBe(503)
     |                               ^ Error: expect(received).toBe(expected) // Object.is equality
  64 |   })
  65 | })
  66 | 
```