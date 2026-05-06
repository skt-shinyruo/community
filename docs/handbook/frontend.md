# 前端核心逻辑

本文档是 Vue3 SPA 前端运行模型、路由鉴权、会话恢复、API 访问、IM 实时客户端、页面状态和前端一致性语义的 SSOT。后端 owner、业务规则和资金 / 投影失败语义仍以 [business-flows.md](business-flows.md)、[integration-contracts.md](integration-contracts.md) 和 [reliability.md](reliability.md) 为准。

## 读源码顺序

| 目标 | 入口 |
| --- | --- |
| 应用启动 | `frontend/src/main.js`、`frontend/src/App.vue` |
| 路由表和页面权限 | `frontend/src/router/index.js`、`frontend/src/router/authGuard.js`、`frontend/src/router/navigation.js` |
| 会话恢复 | `frontend/src/auth/session.js`、`frontend/src/auth/sessionHint.js`、`frontend/src/stores/auth.js` |
| API base URL | `frontend/src/config/runtimeConfig.js`、`frontend/src/config/endpointResolution.js` |
| HTTP 客户端 | `frontend/src/api/http.js`、`frontend/src/api/imCoreHttp.js` |
| API service | `frontend/src/api/services/*.js` |
| IM 长连 | `frontend/src/im/imRealtimeClient.js`、`frontend/src/views/conversationDetailState.js` |
| 页面纯状态 | `frontend/src/views/*State.js` |
| 全局读侧缓存 | `frontend/src/stores/*.js` |

前端尽量把可测试的状态转换放到纯函数文件，例如 `marketState.js`、`walletState.js`、`postsViewState.js`、`postDetailState.js`、`conversationDetailState.js`。Vue 单文件组件负责加载数据、绑定 UI 和调用 service，不应把复杂规则散落在模板里。

## 路由和页面鉴权

路由表位于 `frontend/src/router/index.js`，使用 hash history。页面级权限通过 route `meta` 表达：

| `meta` 字段 | 语义 |
| --- | --- |
| `requiresAuth` | 进入页面前必须恢复到登录态，否则跳转登录页。 |
| `roles` | 需要任一角色，例如 `ROLE_ADMIN` 或 `ROLE_MODERATOR`。 |
| `navGroup` | 导航分组，用于侧边栏 / 移动端入口。 |
| `title` / `subtitle` | 页面标题和说明。 |

`frontend/src/router/authGuard.js` 是体验层守卫：

```text
protected route
  -> ensureSessionReady(...)
  -> anonymous: redirect login with redirect query
  -> error + role page: abort navigation
  -> role mismatch: forbidden
```

安全边界仍在后端。前端守卫只减少误点和无效请求，不能作为授权依据。

`frontend/src/router/navigation.js` 是导航 SSOT，包含：

- Community / Trading / Personal / Admin / Account 工作区导航分组。
- 侧边栏、移动端底栏和 shell search 的 route 级可见性。
- 角色、登录态、用户 id 的前端可见性判断。
- posts 列表的 `order`、`type`、`categoryId`、`tag`、`subscribed` query 规范化和构造。

新增页面时必须同步三处：

1. `router/index.js` 注册 route 和权限 `meta`。
2. `router/navigation.js` 决定是否进入导航。
3. 对应 `*.test.js` 覆盖 route / nav / auth guard 行为。

## 产品壳层和移动导航

`frontend/src/components/layout/AppShell.vue` 负责桌面 workspace shell，`SidebarNav.vue` 渲染工作区分组，`Topbar.vue` 渲染页面标题、账户控制和 route-aware shell search，`MobileNav.vue` 只承载高频移动入口。移动端 sidebar drawer 状态与桌面 collapsed 偏好分离，避免 sidebar 和 bottom nav 同时作为持久导航出现。

## 会话恢复

前端 access token 只保存在 Pinia 内存 store：`frontend/src/stores/auth.js`。refresh token 由后端 HttpOnly cookie 承载，浏览器脚本不可读。

刷新页面后，`frontend/src/auth/session.js` 通过以下流程恢复会话：

```text
shouldBootstrapSession(...)
  -> accessToken exists OR localStorage session hint exists
  -> ensureSessionReady(...)
      -> no accessToken: POST /api/auth/refresh
      -> got accessToken: GET /api/auth/me
      -> ready / anonymous / error
```

`pendingSessionPromise` 保证同一时间只有一个会话恢复请求，避免多个受保护页面并发进入时重复 refresh。

`sessionHint` 只表示“这个浏览器曾经有过登录态”，不是凭据。真正登录态必须由 `/api/auth/refresh` 和 `/api/auth/me` 确认。

## API Endpoint 解析

`frontend/src/config/endpointResolution.js` 统一解析浏览器访问后端的 base URL。

优先级：

1. runtime config：`globalThis.__COMMUNITY_RUNTIME_CONFIG__`，由 `frontend/public/app-config.js` 或部署注入。
2. Vite env：`VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL`。
3. 本地推断：页面来自 `localhost:5173`、`12881`、`12890` 或 `12888` 时，推断 gateway 为 `http://localhost:12880`。

浏览器默认通过 gateway 访问业务 API、IM HTTP 和 IM WebSocket bootstrap。不要在页面代码里直接硬编码 `community-app`、`im-core` 或内部容器名。

## HTTP 客户端

主站 HTTP 客户端是 `frontend/src/api/http.js`：

- `baseURL` 来自 `resolveApiBaseUrl()`。
- `withCredentials=true`，用于 refresh cookie。
- 请求 interceptor 注入 `Authorization: Bearer <accessToken>`。
- 响应 `401` 时单飞行调用 `/api/auth/refresh`，成功后重试原请求。
- 全局错误 toast 优先展示后端 `Result.message` 和 `traceId`。
- 对发帖和评论自动附加 `Idempotency-Key`。

IM HTTP 客户端是 `frontend/src/api/imCoreHttp.js`：

- `baseURL` 来自 `resolveImHttpBaseUrl()`。
- 请求同样注入 access token。
- `401` 时复用主站 `http.post('/api/auth/refresh')` 刷新 access token，再重试 IM HTTP 请求。

## 前端幂等语义

服务端高风险写接口语义见 [reliability.md](reliability.md#http-idempotency-key)。当前前端状态：

| 功能 | 当前前端行为 |
| --- | --- |
| 发帖 | `http.js` 根据 `POST /api/posts` 自动生成并短期复用 `Idempotency-Key`。 |
| 评论 | `http.js` 根据 `POST /api/posts/{postId}/comments` 自动生成并短期复用 `Idempotency-Key`。 |
| 钱包充值 / 提现 / 转账 | 页面仍生成 body `requestId`，依赖服务端兼容路径。 |
| 市场下单 | 页面仍生成 body `requestId`，依赖服务端兼容路径。 |

`frontend/src/api/idempotencyKeyCache.js` 按请求指纹短期复用 key，默认窗口是 10 秒。修改重试、按钮防重复或自动保存逻辑时，必须保证同一次业务尝试复用同一个 key / requestId；新业务尝试才生成新值。

## IM 实时客户端

IM 的客户端模型是“WebSocket best-effort 推送 + HTTP backfill”。`frontend/src/im/imRealtimeClient.js` 不直接拼固定 WS 地址，而是先创建服务端 session：

```text
connect(accessToken)
  -> POST /api/im/sessions
  -> response: wsUrl + ticket
  -> new WebSocket(wsUrl)
  -> send { type: 'connect', ticket }
```

当前 `wsUrl` 由 `community-im-gateway` 统一返回，浏览器不再依赖 worker 专属路径。

连接行为：

- `connected` 消息表示 worker 接受 ticket，客户端进入 authed 状态。
- `reject` 且 `cmd=connect` 表示 ticket / 权限被拒绝，客户端不应假设消息已可发送。
- `online` 和页面 `visibilitychange` 会触发恢复连接。
- 断开后按指数退避重连，最大基础延迟 5 秒并带 jitter。
- `sendPrivateText` 和 `sendRoomText` 会生成或复用 `clientMsgId`。

正确性边界：

- WebSocket command 被发送不表示消息已经落库。
- `im-core` 是消息持久化、顺序号和已读状态 owner。
- 会话详情页必须通过 HTTP history / backfill 补齐断线期间消息。
- 消息合并和排序逻辑在 `frontend/src/views/conversationDetailState.js`，优先按 `seq` 去重和排序，再回退到时间 / id。

## 页面状态模块

复杂页面的核心状态转换集中在 `frontend/src/views/*State.js`，并配套同名测试。

| 文件 | 责任 |
| --- | --- |
| `postsFeedState.js` | 最新流默认视图判断、上次阅读分隔线、新内容跳转提示、分页推进。 |
| `postsViewState.js` | 发帖标签规范化、标签限制、帖子列表 hydration id 收集。 |
| `postDetailState.js` | 评论 / 回复 hydration id 收集、引用预览、回复内容组合、评论回复状态初始化。 |
| `conversationDetailState.js` | 私信 conversation id 解析、Java UUID 排序兼容、消息映射、去重和排序。 |
| `marketState.js` | 商品、订单、争议、地址的状态标签和展示文本。 |
| `walletState.js` | 钱包状态文案、交易类型标签、金额展示和 feed key 生成。 |
| `registerFlowState.js` | 注册后邮箱验证码步骤的持久化、恢复和错误处理。 |
| `userProfileSurface.js` / `userProfileTimeline.js` | 用户主页摘要、时间线展示状态。 |
| `searchResultSurface.js` | 搜索结果展示状态。 |

新增复杂页面逻辑时，优先抽出纯函数并新增同名测试。组件只保留加载、提交、toast 和 UI 绑定。

## 全局 Store

| Store | 文件 | 语义 |
| --- | --- | --- |
| Auth | `frontend/src/stores/auth.js` | access token、`me`、authorities、session hint 写入 / 清理。 |
| App | `frontend/src/stores/app.js` | 当前 trace id 等应用级状态。 |
| UI | `frontend/src/stores/ui.js` | theme、density、桌面 sidebar collapsed 偏好、移动 sidebar drawer 临时状态。 |
| Taxonomy | `frontend/src/stores/taxonomy.js` | 分类和热门标签轻缓存。 |
| Post Meta Cache | `frontend/src/stores/postMetaCache.js` | 用户摘要、点赞数、点赞状态 TTL 缓存。 |
| Social Prefs | `frontend/src/stores/socialPrefs.js` | 拉黑和订阅分类读侧状态。 |

`postMetaCache` 的 TTL 约定：

- 用户摘要缓存 60 秒。
- 点赞计数 / 状态缓存 30 秒。
- 点赞状态与登录态相关，auth 变化后应清理。

## 产品 UI 基础件

`frontend/src/styles/variables.css`、`components.css` 和 `layout.css` 提供克制的产品默认样式。通用 `.card` 默认不带装饰性 hover lift 或大阴影；需要对象卡片强调时显式使用 `.object-card`。

`frontend/src/components/ui/UiState.vue` 是 empty / loading / error / forbidden / unavailable / pending / development-only 的共享状态块，`UiEmpty.vue` 只是兼容包装层。`UiToolbar.vue` 是页面工具栏基础件，使用 leading / filters / actions 三个 slot 表达常见工作区操作结构。

`/dev` 只保留给本地联调和 trace 检查，不应进入正常导航；如果页面需要展示调试辅助信息，应使用 `UiState` 的 `development` variant 显式标记，而不是把它伪装成普通业务内容。

这些约定由 `frontend/src/styles/productTokens.test.js` 和 `frontend/src/views/viewComplexity.test.js` 约束，新增全局样式时不要绕过这些 guardrail。

## 用户可见一致性语义

前端必须把最终一致和 pending 状态展示成用户可理解的“处理中”，不能按完成态处理。

| 场景 | 前端展示原则 |
| --- | --- |
| Notice | 通知是 after-commit best-effort 投影，写操作成功后通知可能稍后出现。 |
| Search | 搜索结果来自 ES 投影，发帖 / 评论后搜索可短暂落后；必要时依赖 reindex / outbox 追平。 |
| IM | WS 推送是 best-effort，断线后以 HTTP history 补拉为准。 |
| Market 下单 | HTTP 成功可能只是订单创建成功，资金可能处于 `ESCROW_PENDING`。 |
| Market 确认 / 取消 / 争议 | 资金放款 / 退款由 `market_wallet_action` processor / recovery 推进，`ESCROW_CANCEL_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING`、`DISPUTE_RELEASE_PENDING`、`DISPUTE_REFUND_PENDING` 都应展示为处理中。 |
| Wallet | 钱包 ledger 是资金 owner；市场页面不要自行推断余额变化。 |
| Like / Follow | 前端可乐观更新局部状态，但最终计数以 owner API 读侧返回为准。 |

## 测试

前端测试使用 Vitest：

```bash
cd frontend
npm test
```

常见定向测试：

```bash
npm test -- src/router/authGuard.test.js
npm test -- src/auth/session.test.js
npm test -- src/api/http.test.js src/api/http.resolution.test.js
npm test -- src/im/imRealtimeClient.test.js
npm test -- src/views/marketState.test.js src/views/walletState.test.js
npm test -- src/views/conversationDetailState.test.js
```

构建验证：

```bash
cd frontend
npm run build
```

完整测试策略见 [testing.md](testing.md)。

## 维护清单

| 代码变化 | 必改文档 |
| --- | --- |
| 新增路由、页面权限或导航入口 | 本文档、`router/index.js` / `router/navigation.js` 对应测试。 |
| 修改 session、refresh、token 存储或 401 重试 | 本文档、[security.md](security.md)、相关 auth / http 测试。 |
| 修改 endpoint 解析或部署注入方式 | 本文档、[local-development.md](local-development.md)、相关 resolution 测试。 |
| 修改前端幂等或高风险写提交方式 | 本文档、[reliability.md](reliability.md)、[integration-contracts.md](integration-contracts.md)。 |
| 修改 IM session bootstrap、WS 协议或 backfill 行为 | 本文档、[business-flows.md](business-flows.md)、[integration-contracts.md](integration-contracts.md)。 |
| 新增复杂页面状态纯函数 | 本文档、同名 `*.test.js`。 |
