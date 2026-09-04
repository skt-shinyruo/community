import { expect, test } from '../fixtures/test'
import {
  authenticateVisualPage,
  expectVisualSnapshot,
  mockResult,
  openVisualPage,
  prepareVisualPage,
  visualIds
} from '../fixtures/visual'
import { accounts } from '../fixtures/accounts'

test.describe('migration visual baseline @visual', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await prepareVisualPage(page, testInfo)
  })

  test('login @visual-dark', async ({ page }) => {
    await openVisualPage(page, '/auth/login', '回到讨论广场前')
    await expectVisualSnapshot(page, 'login')
  })

  test('posts empty state @visual-dark', async ({ page }) => {
    await mockResult(page, '/api/feed/global*', { items: [], nextCursor: '', rankVersion: 'visual' })
    await openVisualPage(page, '/posts', '当前视图暂无讨论')
    await expectVisualSnapshot(page, 'posts')
  })

  test('post detail @visual-dark', async ({ page }) => {
    await mockResult(page, `/api/posts/${visualIds.post}`, {
      id: visualIds.post,
      userId: accounts.aaa.userId,
      title: '固定视觉基线帖子',
      blocks: [{ type: 'paragraph', text: '用于审查帖子详情的稳定正文。' }],
      tags: ['visual'],
      likeCount: 7,
      commentCount: 2,
      createTime: '2026-08-31T08:00:00Z'
    })
    // 先注册评论列表通配，再注册回复专用模式；Playwright 后注册的路由优先匹配回复请求。
    await mockResult(page, `/api/posts/${visualIds.post}/comments*`, {
      items: [
        {
          id: '66666666-6666-7666-8666-666666666666',
          userId: accounts.aaa.userId,
          content: '第一条固定评论，用于审查评论线程的视觉表现。',
          createTime: '2026-08-31T08:30:00Z',
          replyCount: 1
        },
        {
          id: '77777777-7777-7777-8777-777777777777',
          userId: accounts.bbb.userId,
          content: '第二条固定评论，保持列表间距与分割线稳定。',
          createTime: '2026-08-31T09:00:00Z',
          replyCount: 0
        }
      ],
      nextCursor: ''
    })
    await mockResult(
      page,
      `/api/posts/${visualIds.post}/comments/66666666-6666-7666-8666-666666666666/replies*`,
      {
        items: [
          {
            id: '88888888-8888-7888-8888-888888888888',
            userId: accounts.bbb.userId,
            replyToUserId: accounts.aaa.userId,
            content: '固定回复内容，用于审查嵌套回复的视觉层级。',
            createTime: '2026-08-31T09:30:00Z'
          }
        ],
        nextCursor: ''
      }
    )
    await mockResult(page, '/api/users/batch-summary', [
      { id: accounts.aaa.userId, username: 'aaa', headerUrl: '' },
      { id: accounts.bbb.userId, username: 'bbb', headerUrl: '' }
    ])
    await mockResult(page, '/api/likes/counts*', {
      '66666666-6666-7666-8666-666666666666': 3,
      '77777777-7777-7777-8777-777777777777': 1,
      '88888888-8888-7888-8888-888888888888': 2
    })
    await mockResult(page, `/api/users/${accounts.aaa.userId}`, {
      id: accounts.aaa.userId,
      username: 'aaa',
      headerUrl: ''
    })
    await openVisualPage(page, `/posts/${visualIds.post}`, '固定视觉基线帖子')
    // 展开首条评论的回复线程，把嵌套回复样式纳入基线审查。
    await page.getByRole('button', { name: '展开 1 条回复' }).click()
    await expect(page.getByText('固定回复内容，用于审查嵌套回复的视觉层级。')).toBeVisible()
    await expectVisualSnapshot(page, 'post-detail')
  })

  test('settings @visual-dark', async ({ page }) => {
    await authenticateVisualPage(page)
    await openVisualPage(page, '/settings', '头像上传')
    await expectVisualSnapshot(page, 'settings')
  })

  test('403', async ({ page }) => {
    await openVisualPage(page, '/403', '403 无权限')
    await expectVisualSnapshot(page, '403')
  })

  test('404', async ({ page }) => {
    await openVisualPage(page, '/visual-baseline-not-found', '404 页面不存在')
    await expectVisualSnapshot(page, '404')
  })

  test('bookmarks empty state', async ({ page }) => {
    await authenticateVisualPage(page)
    await mockResult(page, '/api/bookmarks*', [])
    await openVisualPage(page, '/bookmarks', '暂无收藏')
    await expectVisualSnapshot(page, 'bookmarks')
  })

  test('search empty state', async ({ page }) => {
    await mockResult(page, '/api/search/posts*', [])
    await openVisualPage(page, '/search', '尚未添加限定词')
    await expectVisualSnapshot(page, 'search')
  })

  test('profile', async ({ page }) => {
    await mockResult(page, `/api/users/${accounts.aaa.userId}`, {
      id: accounts.aaa.userId,
      username: 'aaa',
      headerUrl: '',
      createTime: '2026-08-31T08:00:00Z'
    })
    await mockResult(page, `/api/users/${accounts.aaa.userId}/recent-posts*`, [])
    await mockResult(page, `/api/users/${accounts.aaa.userId}/recent-comments*`, [])
    await openVisualPage(page, `/users/${accounts.aaa.userId}`, '公开身份')
    await expectVisualSnapshot(page, 'profile')
  })

  test('notices empty state', async ({ page }) => {
    await authenticateVisualPage(page)
    await mockResult(page, '/api/notices/summary', [])
    await openVisualPage(page, '/notices', '暂无通知')
    await expectVisualSnapshot(page, 'notices')
  })

  test('conversation list empty state', async ({ page }) => {
    // 固定非零未读，让侧边栏通知/私信角标进入基线审查。
    await authenticateVisualPage(page, {
      noticeSummary: [{ topic: 'comment', unreadCount: 3, noticeCount: 5 }],
      imUnreadSummary: {
        rooms: [],
        conversations: [{ conversationId: 'visual-badge', lastSeq: 3, lastReadSeq: 1, unreadCount: 2 }]
      }
    })
    await mockResult(page, '/api/im/conversations/page*', { items: [], nextCursor: null, hasMore: false })
    await openVisualPage(page, '/messages', '暂无会话')
    await expect(page.getByRole('link', { name: '通知，3 条未读' })).toBeVisible()
    await expect(page.getByRole('link', { name: '私信，2 条未读' })).toBeVisible()
    await expectVisualSnapshot(page, 'conversations')
  })

  test('fixed conversation thread', async ({ page }) => {
    await authenticateVisualPage(page)
    const messagePath = `/api/im/conversations/${visualIds.conversation}/messages`
    await mockResult(page, `${messagePath}*`, { items: [] })
    await mockResult(page, `${messagePath}/history*`, {
      items: [
        {
          messageId: '44444444-4444-7444-8444-444444444444',
          seq: 1,
          fromUserId: accounts.bbb.userId,
          toUserId: accounts.aaa.userId,
          content: '这是一条固定的视觉基线消息。',
          clientMsgId: 'visual-message-1',
          createdAtEpochMs: 1788163200000
        },
        {
          messageId: '55555555-5555-7555-8555-555555555555',
          seq: 2,
          fromUserId: accounts.aaa.userId,
          toUserId: accounts.bbb.userId,
          content: '收到，线程布局保持稳定。',
          clientMsgId: 'visual-message-2',
          createdAtEpochMs: 1788163260000
        }
      ],
      nextBeforeSeq: null,
      hasMore: false,
      lastReadSeq: 2
    })
    await mockResult(page, `/api/im/conversations/${visualIds.conversation}/read`, null)
    await openVisualPage(page, `/messages/${visualIds.conversation}`, '这是一条固定的视觉基线消息')
    await expectVisualSnapshot(page, 'conversation-detail')
  })

  test('drive empty state', async ({ page }) => {
    await authenticateVisualPage(page)
    await mockResult(page, '/api/drive/space', { usedBytes: 0, quotaBytes: 1073741824 })
    await mockResult(page, '/api/drive/entries*', [])
    await openVisualPage(page, '/drive', '暂无文件')
    await expectVisualSnapshot(page, 'drive')
  })

  test('public drive share gate', async ({ page }) => {
    await mockResult(page, `/api/drive/shares/${visualIds.share}`, {
      shareToken: visualIds.share,
      requiresPassword: true
    })
    await openVisualPage(page, `/drive/s/${visualIds.share}`, '提取码')
    await expectVisualSnapshot(page, 'drive-share')
  })

  test('wallet empty state', async ({ page }) => {
    await authenticateVisualPage(page)
    await mockResult(page, '/api/wallet/summary', { balance: 0 })
    await mockResult(page, '/api/wallet/transactions*', [])
    await mockResult(page, '/api/wallet/capabilities', { testCredits: { enabled: false } })
    await openVisualPage(page, '/wallet', '暂无交易记录')
    await expectVisualSnapshot(page, 'wallet')
  })

  test('market list empty state', async ({ page }) => {
    await mockResult(page, '/api/market/listings*', { items: [], page: 0, size: 20, hasNext: false })
    await openVisualPage(page, '/market', '暂无在售商品')
    await expectVisualSnapshot(page, 'market-list')
  })

  test('market listing detail', async ({ page }) => {
    await mockResult(page, `/api/market/listings/${visualIds.listing}`, {
      listingId: visualIds.listing,
      sellerUserId: accounts.bbb.userId,
      sellerName: 'bbb',
      goodsType: 'VIRTUAL',
      deliveryMode: 'PRELOADED',
      title: '固定视觉基线商品',
      description: '用于审查市场详情布局的稳定商品。',
      unitPrice: 1999,
      stockAvailable: 3,
      status: 'ACTIVE'
    })
    await openVisualPage(page, `/market/listings/${visualIds.listing}`, '固定视觉基线商品')
    await expectVisualSnapshot(page, 'market-detail')
  })

  test('market order detail', async ({ page }) => {
    await authenticateVisualPage(page)
    await mockResult(page, `/api/market/orders/${visualIds.order}`, {
      orderId: visualIds.order,
      requestId: 'visual-order-request',
      goodsType: 'PHYSICAL',
      sellerUserId: accounts.bbb.userId,
      buyerUserId: accounts.aaa.userId,
      listingTitleSnapshot: '固定视觉基线订单',
      status: 'SHIPPED',
      totalAmount: 12900,
      shipment: { carrierName: '顺丰', trackingNo: 'VISUAL123456', shippingRemark: '固定配送信息' }
    })
    await openVisualPage(page, `/market/orders/${visualIds.order}`, '固定视觉基线订单')
    await expectVisualSnapshot(page, 'market-order')
  })
})
