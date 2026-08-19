# 前端核心逻辑

本文档是 Vue3 SPA 前端运行模型、路由鉴权、会话恢复、API 访问、IM 实时客户端、页面状态和前端一致性语义的 SSOT。后端 owner、业务规则和资金 / 投影失败语义仍以 [business-flows.md](business-flows.md)、[integration-contracts.md](integration-contracts.md) 和 [reliability.md](reliability.md) 为准。

## 读源码顺序

| 目标 | 入口 |
| --- | --- |
| 应用启动 | `frontend/src/main.js`、`frontend/src/App.vue` |
| 路由表和页面权限 | `frontend/src/router/index.js`、`frontend/src/router/routeCatalog.js`、`frontend/src/router/authGuard.js`、`frontend/src/router/navigation.js` |
| 会话恢复 | `frontend/src/auth/session.js`、`frontend/src/auth/sessionHint.js`、`frontend/src/stores/auth.js` |
| API base URL | `frontend/src/config/runtimeConfig.js`、`frontend/src/config/endpointResolution.js` |
| HTTP 客户端 | `frontend/src/api/authenticatedHttp.js`、`frontend/src/api/http.js`、`frontend/src/api/imCoreHttp.js` |
| 高风险写尝试 | `frontend/src/api/writeAttempt.js` |
| 上传链路 | `frontend/src/api/uploadSession.js`、`frontend/src/api/uploadTransport.js` |
| API service | `frontend/src/api/services/*.js` |
| IM 长连与会话详情流程 | `frontend/src/im/imRealtimeClient.js`、`frontend/src/views/useConversationDetailWorkflow.js`、`frontend/src/views/conversationDetailState.js` |
| 页面纯状态 | `frontend/src/views/*State.js` |
| 全局读侧缓存 | `frontend/src/stores/*.js` |

前端尽量把可测试的状态转换放到纯函数文件，例如 `marketState.js`、`walletState.js`、`postsViewState.js`、`postDetailState.js`、`conversationDetailState.js`。复杂、有状态的 transport 流程集中在 `use*Workflow.js`，Vue 单文件组件只绑定其公开页面模型和动作，不应把请求生命周期或复杂规则散落在模板里。

## 路由和页面鉴权

路由表位于 `frontend/src/router/index.js`，使用 hash history。`routeCatalog.js` 统一拥有 route 的稳定 workspace、权限和 active family 事实，router 与 navigation 通过窄查询函数投影这些字段。页面标题、subtitle、path、component 和 props 仍由 router 拥有；动态个人主页目标、移动端五入口和 breadcrumb fallback 仍由 navigation 实现。页面级权限通过 route `meta` 表达：

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

`frontend/src/router/navigation.js` 是导航产品策略入口，包含：

- Community / Trading / Personal / Admin / Account 工作区导航分组。
- 侧边栏、移动端底栏和 shell search 的 route 级可见性。
- 角色、登录态、用户 id 的前端可见性判断。
- posts 列表的 `boardId` query 规范化和构造。

新增页面时必须同步以下四处：

1. `routeCatalog.js` 登记 workspace、权限和 active family 等稳定事实。
2. `router/index.js` 注册 path、component、props 和页面文案。
3. `router/navigation.js` 决定是否进入导航及动态目标、移动端策略。
4. 对应 `*.test.js` 覆盖 catalog / route / nav / auth guard 行为。

## 产品壳层和移动导航

`frontend/src/components/layout/AppShell.vue` 负责桌面 workspace shell，`SidebarNav.vue` 渲染工作区分组，`Topbar.vue` 渲染 route-aware scope、页面标题、账户控制和 shell search，`MobileNav.vue` 只承载高频移动入口：讨论、搜索、通知、私信和个人入口。移动端 sidebar drawer 状态与桌面 collapsed 偏好分离，避免 sidebar 和 bottom nav 同时作为持久导航出现。

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

`pendingSessionPromise` 按 auth store 实例和 `tokenGeneration` 共享同一次会话恢复，避免多个受保护页面并发进入时重复 refresh，也不会把上一份会话的 Promise 交给新会话。`refreshCoordinator.js` 的 refresh single-flight 使用同样的会话快照边界。

Auth store 使用 `identityState=anonymous|unresolved|resolved` 表达身份快照。安装不同 access token 时必须同步清空旧 `me` 并推进 `tokenGeneration`；新 token 对应的 `/me` 暂时失败时保持 `unresolved`，不能组合成“新 token + 旧 me”。只有 refresh 或 `/me` 明确返回 `401/403` 才是权威认证失败并清空会话；网络错误、限流、服务端错误和缺少临时响应字段均保留当前会话并返回可重试错误。

`sessionHint` 只表示“这个浏览器曾经有过登录态”，不是凭据。真正登录态必须由 `/api/auth/refresh` 和 `/api/auth/me` 确认。

## API Endpoint 解析

`frontend/src/config/endpointResolution.js` 统一解析浏览器访问后端的 base URL。

优先级：

1. runtime config：`globalThis.__COMMUNITY_RUNTIME_CONFIG__`，由 `frontend/public/app-config.js` 或部署注入。
2. Vite env：`VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL`。
3. 未显式配置时返回空字符串，浏览器使用同源相对路径，由 Vite proxy 或部署入口转发。

浏览器默认通过 gateway 访问业务 API、IM HTTP 和 IM WebSocket bootstrap。不要在页面代码里直接硬编码 `community-app`、`im-core` 或内部容器名。

生产前端镜像在容器启动时把 `FRONTEND_RUNTIME_API_BASE_URL` 和
`FRONTEND_RUNTIME_IM_HTTP_BASE_URL` 写入 `/app-config.js`；未设置时两者回退到
`GATEWAY_PUBLIC_BASE_URL`。输出通过 JSON 编码生成，部署值中的引号、反斜杠和换行不会变成可执行脚本。
该文件明确使用 `no-store`，因此同一份静态镜像可以在不同环境注入端点而无需重新构建。
将某个 `FRONTEND_RUNTIME_*` 变量显式设为空字符串会启用该客户端的同源相对路径。runtime config 的“键不存在”和“键存在但值为空”语义不同：前者继续读取 Vite env，后者明确覆盖 Vite env。

## HTTP 客户端

`frontend/src/api/authenticatedHttp.js` 是主站与 IM 客户端共用的鉴权恢复内核，只负责请求时捕获 `tokenGeneration`、注入 Authorization、在允许时单飞恢复 401，并以恢复后的 token 重试一次。base URL、cookie、auth endpoint 排除、terminal redirect 与 toast 文案继续由具体客户端拥有。

主站 HTTP 客户端是 `frontend/src/api/http.js`：

- `baseURL` 来自 `resolveApiBaseUrl()`。
- `withCredentials=true`，用于 refresh cookie。
- 请求 interceptor 注入 `Authorization: Bearer <accessToken>`。
- 请求发出时记录 `tokenGeneration`；即使当时没有 access token，也能识别请求返回 `401` 前已经完成的并发登录 / refresh。
- 非 `/api/auth/**` 响应 `401` 时单飞行调用 `/api/auth/refresh`，成功后重试原请求；登录、注册、密码重置等 auth 自身入口不触发 refresh 重试。
- 全局错误 toast 优先展示后端 `Result.message` 和 `traceId`。同一个 Error 对象只能被 `showErrorToast` 认领一次，页面 catch 不重复弹出同一错误。
- 通用 HTTP 层不生成 `Idempotency-Key`；高风险写必须由调用方提供 `WriteAttempt`。

IM HTTP 客户端是 `frontend/src/api/imCoreHttp.js`：

- `baseURL` 来自 `resolveImHttpBaseUrl()`。
- 请求同样注入 access token。
- `401` 时复用 `refreshCoordinator` 刷新 access token，再重试 IM HTTP 请求。

上传不经过带 15 秒超时的主站 `http`。`uploadTransport.js` 会把浏览器 origin 和 runtime API origin 都视为可信主站：可信上传使用 `timeout=0`，只复用鉴权内核的请求快照与一次恢复 helper；其他绝对 URL 使用无 cookie、不注入主站 `Authorization`、不触发 refresh 的独立客户端，但保留 upload session 明确提供的存储服务签名头。`uploadSession.js` 在发送字节前校验服务端 session 的 `maxBytes` / `mimeTypes`；`POST` 指令构造 multipart，预签名 `PUT` 指令发送原始文件。页面通过 `AbortSignal` 和规范化进度回调提供取消与进度状态。

## 前端幂等语义

服务端高风险写接口语义见 [reliability.md](reliability.md#http-idempotency-key)。当前前端状态：

| 功能 | 当前前端行为 |
| --- | --- |
| 发帖 | 发帖 composer 持有一个 `WriteAttempt`，失败后人工重试复用 key。 |
| 评论 / 回复 | 输入框或回复草稿持有 `WriteAttempt`；同一草稿重试复用 key。 |
| 测试积分领取 / 销毁、钱包转账 | 每个动作表单分别持有 `WriteAttempt`。 |
| 市场下单 | 商品详情的下单表单持有一个 `WriteAttempt`。 |

`frontend/src/api/writeAttempt.js` 明确建模 `idle -> active -> succeeded|cancelled|changed`。首次发送生成 key；传输失败不结束 attempt，人工重试继续使用同一个 key；成功、取消、切换账号 / 页面或修改业务意图后清除旧 key，下次发送再生成。不要按 URL、payload 指纹或时间窗口缓存 key，两个内容相同但由用户分别发起的动作仍是两个业务尝试。高风险 service 缺少 `WriteAttempt` 时直接报错，以便在开发期暴露生命周期遗漏。

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
- 发送后先插入带 `clientMsgId` 的 pending message；`committed` frame 将其转为已提交，reject / send error 将其标成失败，不能把 WebSocket send 当成落库成功。
- 会话详情流程集中在 `frontend/src/views/useConversationDetailWorkflow.js`，只向组件公开 `model/actions/lifecycle`；HTTP/WS transport、请求竞态、订阅清理和滚动锚定不由组件直接管理。该流程先等待首次 `limit=50` history 建立 scope-bound 基线，再在 `authed: false -> true` 后从最近一次由 HTTP history 确认的连续 `seq` 水位调用 after-seq backfill，并按每页 100 条推进；实时帧和 `committed` 回执不能跨越缺口推进该水位，HTTP 页内出现缺口时停在缺口前并在下次重连继续补拉。
- backfill 按会话 scope 单飞串行执行；执行期间再次出现重连上升沿时排队一轮，当前轮结束后从最新水位继续补，不能吞掉新的恢复请求。
- pending、committed、实时推送和 HTTP history 的消息观察通过 `seq`、服务端 `messageId`、`fromId + clientMsgId` 或发送 `requestId` 合并；`clientMsgId` 的唯一性是发送者作用域，peer 使用相同值不能替换或提交本地 pending。初始 history 慢响应也不能覆盖期间产生的 pending / failed 消息。
- 每条内部消息通过可枚举的 `messageIdentity` 记录显式保留 `serverMessageIds`、发送者作用域的 `clientMessageIds`、`requestIds` 和 `sequences` 别名。消息合并和排序逻辑在 `frontend/src/views/conversationDetailState.js`，任一别名命中都更新同一条消息，排序仍优先使用 `seq`，再回退到时间 / id；身份元数据不进入组件渲染模型。

## 页面状态模块

复杂页面的核心状态转换集中在 `frontend/src/views/*State.js`，并配套同名测试。

| 文件 | 责任 |
| --- | --- |
| `postsFeedState.js` | 最新流默认视图判断、上次阅读分隔线、新内容跳转提示、分页推进。 |
| `posts/usePostsFeed.js` | 帖子页的会话、范围、列表、未读和发帖五组页面语义；隐藏游标、补水、请求竞态和发帖幂等尝试。 |
| `postsViewState.js` | 发帖标签规范化、标签限制、帖子列表 hydration id 收集。 |
| `postDetailState.js` | 评论 / 回复 hydration id 收集、引用预览、回复内容组合，以及 `replyEditor`、`replyList`、`like` 三组评论 UI 状态初始化。 |
| `conversationDetailState.js` | 私信 conversation id 解析、Java UUID 排序、消息映射、去重和排序。 |
| `useConversationDetailWorkflow.js` | 私信详情的 HTTP/WS transport、历史分页、pending send、重连补拉、水位线、订阅和滚动生命周期。 |
| `marketState.js` | 商品、订单、争议、地址的状态标签和展示文本。 |
| `walletState.js` | 钱包状态文案、交易类型标签、金额展示和 feed key 生成。 |
| `driveState.js` | 网盘 quota 展示、breadcrumb、entry capability、分享表单校验和选择收敛。 |
| `registerFlowState.js` | 注册后邮箱验证码步骤的持久化、恢复和错误处理。 |
| `useUserProfilePage.js` | 用户主页的 route/session scope、并发加载、部分成功、关注/拉黑动作和生命周期隔离。 |
| `userProfileSurface.js` / `userProfileTimeline.js` | 用户主页摘要和时间线的纯展示投影。 |
| `searchResultSurface.js` | 搜索结果展示状态。 |

新增复杂页面逻辑时，优先抽出纯函数并新增同名测试。跨请求或跨会话的页面流程使用页面专用 module，并向组件公开按页面意图命名的 model/actions/lifecycle 或语义分组；组件只保留 UI 绑定与纯格式化。

跨页面重复的有状态流程使用 focused module：`FollowRelationListView.vue` 通过 route props 的 `relationKind` 统一关注 / 粉丝列表的游标、hydration、账号 / 路由隔离和逐项 mutation；`MarketOrderListView.vue` 通过 route props 的 `side` 统一买单 / 卖单呈现，并由 `useMarketOrderList.js` 统一会话隔离、分页和过期请求丢弃；`useDrivePageState.js` 只协调 `page/workspace/entries/upload/shares` 五个页面模型，目录、条目、上传和分享各自由对应 workflow 管理 transport 与请求生命周期；`usePostDetailLoader.js` 只组合 `page/postActions/discussion` 三个模型，主帖动作和评论树分别由 `usePostDetailActions.js`、`usePostDetailDiscussion.js` 负责；`useTagSuggestions.js` 统一去抖、热门标签回退和 latest-request 竞态处理。聚合页面通过 `settledRequests.js` 独立提交成功分区；某个统计、钱包、首页计数或 Drive 分区失败时保留其他成功数据和上一份可用数据，不能用一个 rejected Promise 抹掉整个页面。

`utils/latestRequest.js` 的无参数 tracker 保持 token-only interface；传入 `getScope` 后，request handle 同时捕获 route / session scope，只有最新 token 且 scope 未变化时才能提交。当前先在关系列表试点，pagination append、mutation-by-id、partial success 和 IM backfill 继续保留各自状态语义，不做通用 async 状态机。

## 全局 Store

| Store | 文件 | 语义 |
| --- | --- | --- |
| Auth | `frontend/src/stores/auth.js` | access token、`me`、authorities、session hint 写入 / 清理。 |
| UI | `frontend/src/stores/ui.js` | theme、density、桌面 sidebar collapsed 偏好、移动 sidebar drawer 临时状态。 |
| Taxonomy | `frontend/src/stores/taxonomy.js` | 分类和热门标签轻缓存。 |
| Post Meta Cache | `frontend/src/stores/postMetaCache.js` | 用户摘要、点赞数、点赞状态 TTL 缓存。 |
| Social Prefs | `frontend/src/stores/socialPrefs.js` | 拉黑读侧状态。 |

`postMetaCache` 的 TTL 约定：

- 用户摘要缓存 60 秒。
- 点赞计数 / 状态缓存 30 秒。
- 点赞状态与登录态相关，auth 变化后应清理。

`userService` 的完整资料缓存使用 5 分钟 TTL 和 100 项 LRU 上限，并公开单用户/全量失效入口。缓存只接收明确列出的查看者无关资料字段，不保存 `hasFollowed` 等私有关系状态，因此可以跨登录代际复用；关注关系继续由 social API 按 auth generation 隔离。头像等资料写入成功后必须先失效对应用户；失效时仍在途的旧请求不得重新写回缓存。

## 产品 UI 基础件

`frontend/src/styles/variables.css`、`components.css` 和 `layout.css` 提供克制的产品默认样式。通用 `.card` 默认不带装饰性 hover lift 或大阴影。

`frontend/src/components/ui/UiState.vue` 是 empty / loading / error / forbidden / unavailable / pending / development-only 的共享状态块；页面空态、错误态和开发态都直接使用它。

所有 dialog 使用 `useModalFocus.js`：打开后聚焦首个可操作控件，Tab / Shift+Tab 保持在弹窗内，关闭或卸载后恢复触发控件焦点；同时保留 `role=dialog`、`aria-modal`、可关联标题 / 描述和 Escape 关闭语义。

页面需要展示调试辅助信息时，应使用 `UiState` 的 `development` variant 显式标记，而不是把它伪装成普通业务内容。正式 router 不注册独立开发入口。

这些约定由 `frontend/src/styles/productTokens.test.js` 和 `frontend/src/views/viewComplexity.test.js` 约束，新增全局样式时不要绕过这些 guardrail。

## 用户可见一致性语义

前端必须把最终一致和 pending 状态展示成用户可理解的“处理中”，不能按完成态处理。

| 场景 | 前端展示原则 |
| --- | --- |
| Notice | 通知是 owner Kafka 驱动的最终一致投影，写操作成功后可能稍后出现；失败由 consumer retry / `.dlq` 处理。 |
| Search | 搜索结果来自 ES 投影，发帖 / 改帖后搜索可短暂落后；必要时查 `content.events` consumer/DLQ 或 reindex。 |
| IM | WS 推送是 best-effort；pending send 以 committed / reject frame 更新，断线重连后从 HTTP 已确认的连续 `seq` 水位分页补拉并合并。 |
| Market 下单 | HTTP 成功可能只是订单创建成功，资金可能处于 `ESCROW_PENDING`。 |
| Market 确认 / 取消 / 争议 | 资金放款 / 退款由 `market_wallet_action` processor / recovery 推进，`ESCROW_CANCEL_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING`、`DISPUTE_RELEASE_PENDING`、`DISPUTE_REFUND_PENDING` 都应展示为处理中。 |
| Wallet | 钱包 ledger 是资金 owner；市场页面不要自行推断余额变化。 |
| Like / Follow | 前端可乐观更新局部状态，但最终计数以 owner API 读侧返回为准。 |
| Drive | 上传先创建服务端 upload session，前置校验约束后再由独立 transport 提交 multipart，并支持进度 / 取消；外部绝对上传地址不携带主站凭据。分享下载必须先用提取码换短时 ticket。彻底删除后 OSS blob 清理失败时，后端可通过重复 delete 重试，前端不要把本地条目恢复为 active。 |

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
| 新增路由、页面权限或导航入口 | 本文档、`routeCatalog.js` / `router/index.js` / `router/navigation.js` 对应测试。 |
| 修改 session、refresh、token 存储或 401 重试 | 本文档、[security.md](security.md)、相关 auth / http 测试。 |
| 修改 endpoint 解析或部署注入方式 | 本文档、[local-development.md](local-development.md)、相关 resolution 测试。 |
| 修改前端幂等或高风险写提交方式 | 本文档、[reliability.md](reliability.md)、[integration-contracts.md](integration-contracts.md)。 |
| 修改 IM session bootstrap、WS 协议或 backfill 行为 | 本文档、[business-flows.md](business-flows.md)、[integration-contracts.md](integration-contracts.md)。 |
| 新增复杂页面状态纯函数 | 本文档、同名 `*.test.js`。 |
