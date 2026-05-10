# Frontend 业务面映射

本文不是前端架构文档；前端架构、路由鉴权、session 恢复和 HTTP interceptor 见 [../frontend.md](../frontend.md)。本文只把 Vue 页面和 API service 映射到后端业务逻辑，方便从用户界面反查业务域。

## 入口和会话

前端默认通过 gateway 访问：

- `/api/**`
- `/files/**`
- IM session bootstrap `/api/im/sessions`
- IM WebSocket `wsUrl`

会话模型：

- access token 保存在前端内存。
- refresh token 是 HttpOnly cookie，浏览器自动携带。
- 普通业务请求遇到 401 时，HTTP interceptor 调 `/api/auth/refresh`，成功后重试原请求。
- 登录、注册、密码重置页面对应 auth 域。

关键文件：

- `frontend/src/api/http.js`
- `frontend/src/auth/session.js`
- `frontend/src/auth/sessionHint.js`
- `frontend/src/router/authGuard.js`

## 路由到业务域

| 路由/页面 | 业务域 |
| --- | --- |
| `LoginView.vue` | auth 登录、captcha、refresh session。 |
| `RegisterView.vue` | auth 注册验证码、user verified registration。 |
| `PasswordResetView.vue` | auth 密码重置、user 密码更新。 |
| `PostsView.vue` | content 帖子列表、发帖、分类、标签、点赞。 |
| `PostDetailView.vue` | content 帖子详情、评论、回复、收藏、举报、审核动作。 |
| `SearchView.vue` | search 帖子搜索和高亮。 |
| `BookmarksView.vue` | content 收藏列表。 |
| `NoticesView.vue`, `NoticeDetailView.vue` | notice 通知摘要、列表、已读。 |
| `UserProfileView.vue` | user 资料聚合、content 最近内容、social 关注/拉黑。 |
| `FolloweesView.vue`, `FollowersView.vue` | social 关注/粉丝。 |
| `SettingsView.vue` | user 头像上传/更新。 |
| `ModerationView.vue` | content 举报和审核、user 处罚联动。 |
| `AnalyticsView.vue` | analytics UV/DAU 查询。 |
| `WalletView.vue` | wallet 余额、充值、提现、转账。 |
| `DriveView.vue` | drive 网盘空间、目录、文件、上传、下载、回收站、分享管理。 |
| `DriveShareView.vue` | drive 公开分享访问、提取码校验、短时下载入口。 |
| `WalletAdminView.vue` | wallet 管理员冻结和冲正。 |
| `MarketListView.vue`, `MarketDetailView.vue` | market 商品列表和详情。 |
| `MarketPublishView.vue`, `MarketMyListingsView.vue` | market 卖家商品管理。 |
| `MarketInventoryView.vue` | market 预加载库存。 |
| `MarketAddressesView.vue` | market 收货地址。 |
| `MarketBuyingOrdersView.vue`, `MarketSellingOrdersView.vue`, `MarketOrderDetailView.vue` | market 订单、交付、发货、确认、取消、纠纷。 |
| `AdminMarketDisputesView.vue` | market 管理员纠纷裁决。 |
| `ConversationsView.vue`, `ConversationDetailView.vue` | IM 会话、历史、WebSocket 发送、已读。 |
| `OpsConsoleView.vue` | ops 搜索重建。 |
| `UserManagementView.vue` | user 管理员搜索和角色调整。 |

## API service 到业务域

| API service | 后端域 |
| --- | --- |
| `authService.js` | auth |
| `userService.js`, `adminUserService.js` | user |
| `postService.js`, `bookmarkService.js`, `taxonomyService.js`, `subscriptionService.js`, `reportService.js`, `moderationService.js` | content |
| `socialService.js`, `blockService.js` | social |
| `walletService.js` | wallet |
| `marketService.js` | market |
| `noticeService.js` | notice |
| `searchService.js` | search / ops |
| `analyticsService.js` | analytics |
| `imCoreChatService.js`, `imRealtimeClient.js` | IM gateway / realtime / core |

## 前端幂等和重试口径

- 高风险写接口使用 `Idempotency-Key`。
- `idempotencyKeyCache.js` 用于复用同一次业务尝试的 key。
- 发帖、评论、钱包、市场下单等写请求要避免刷新或重试时产生重复业务事实。
- IM 发送使用 `clientMsgId`，它和 HTTP `Idempotency-Key` 不是同一语义。

## 业务状态展示注意事项

市场：

- `ESCROW_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING` 等状态表示市场已接收请求，但资金动作仍在后台处理。
- 客户端不能把下单 HTTP 200 当成扣款已完成。

IM：

- `sendAccepted` 只表示 realtime 接单。
- 最终状态以 committed/rejected frame 或 HTTP history 为准。

搜索/通知：

- 发帖后搜索和通知可能稍后可见。
- 搜索依赖 outbox 追平。
- 通知是 after-commit best-effort。

认证：

- 角色变化通常要等待 access token 重新签发后在前端权限中体现。
- refresh 失败后应清理本地 session 并跳登录。

## 关键文件

- `frontend/src/router/index.js`
- `frontend/src/router/navigation.js`
- `frontend/src/api/services/*.js`
- `frontend/src/api/http.js`
- `frontend/src/im/imRealtimeClient.js`
- `frontend/src/views/*.vue`
- `frontend/src/views/*State.js`
- `frontend/src/stores/*.js`
