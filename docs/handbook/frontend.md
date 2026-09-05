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

- 社区 / 市场 / 个人三个一级域导航分组，外加有权限用户的管理组和匿名访客的登录入口。
- 侧边栏与移动端底栏的 route 级可见性（壳搜索在公开页常显，不做 route 级开关）。
- 角色、登录态、用户 id 的前端可见性判断。
- 通知 / 私信入口的未读角标绑定（`badge` key 对应 `stores/inboxUnread.js` 的计数）。

`/settings` 通过 `?section=profile|appearance|addresses` 提供可深链的 section 合同（纯函数事实在
`frontend/src/views/settingsSection.js`）：缺省或无效 section 一律回落到 `profile`，并由视图用
`router.replace` 规范化 URL，保证地址栏与实际展示的 section 一致；Settings 仍是
`requiresAuth` 路由，匿名访问按现有守卫跳转登录并在 `redirect` query 中保留完整 section 深链。
收货地址管理收进 Settings 的 addresses section；侧边栏不再保留独立入口，旧 `/market/addresses`
路由重定向到 `/settings?section=addresses`，不保留双入口。section 导航已接入 `UiTabs`：视图把
`?section=` 映射为受控 `v-model`，tab 切换（点击或方向键 / Home / End 自动激活）经 `router.push`
写回 query；三个 section 面板按 tab 懒挂载，地址簿只在 addresses section 激活时加载。
profile section 承载公开资料摘要与 OSS 头像上传会话流程，appearance section 读写主题三态与密度，
addresses section 承载地址簿的新增 / 编辑 / 删除（错态带重试，首载走 UiSkeleton）。

`/posts` 的帖子流 query 合同统一为 `categoryId`、`tag`、`order=latest|hot`（纯函数事实在
`frontend/src/views/postsViewState.js`）：`order` 缺省为 `latest` 且无效值回落，`order=latest`
不写回地址栏；已退役的 `boardId` 旧链接在读取和写回时都归一为 `categoryId`，并由视图用
`router.replace` 规范化。数据源映射：无 `tag` 的视图走游标 feed（无分类时 `GET /api/feed/global`，
带 `categoryId` 时 `GET /api/boards/{categoryId}/feed`）；带 `tag` 的视图走搜索栈
（`GET /api/search/posts?tag=&categoryId=`，页码式加载更多，命中后由 `POST /api/posts/batch-summary`
回补完整摘要），与搜索页同为最终一致。后端当前只暴露单一热度 rank feed，`order=latest|hot`
两个 tab 解析到同一端点，排序切换只重写 query 契约。未读定位（新增计数、「上次看到这里」分隔线
和跳转提示）只属于默认最新视图（`order=latest` 且无分类/标签过滤）。工具栏由
`frontend/src/components/posts/FeedToolbar.vue`（分类 UiDropdown、可清除 tag chip、清空/刷新）和
PostsView 内的 UiTabs（最新/最热）组成，帖子卡的分类 chip 与 `#标签` 纯文本可点击并回到同一过滤模型。

`/posts/:postId` 帖子详情按两级页面处理层级：页首是「返回帖子列表」父级链接，不再叠加
面包屑或历史回退按钮。主帖卡承担页面 H1（帖子标题）、分类 chip / `#标签` 链接（回到同一
过滤模型）、作者 / 时间与正文；点赞（`点赞` / `取消点赞`）与回复数在底部统计行，收藏和
分享是可见按钮，分享复制当前帖子的规范链接并以 toast 反馈；关注作者、举报帖子、屏蔽作者和
治理动作（置顶 / 加精 / 删除）收敛进「更多」`UiDropdown`。评论与回复使用「加载更多」追加
分页（游标续接、失败保留已加载内容并以同一游标重试），首载用 `UiSkeleton`，空 / 错态用
`UiState`（错态带重试），不使用 UiPagination 或裸加载文本。发布评论 / 回复后静默把最新一页
按 id 归并到列表头部并滚动定位到新内容；评论编辑保存后原位更新内容，不弹成功 toast。深链
`?commentId=` / `?replyId=` 与高亮保持：目标不在已加载页时按追加分页自动续载（有界），再
滚动定位。评论 / 回复的举报复用 `ReportModal`（`targetType=comment`），帖子 / 评论编辑复用
`EditContentModal`；两个弹窗外壳均已收敛到 `UiModal`（焦点圈定、Escape / backdrop 策略、
busy 禁关）。

`/bookmarks` 的收藏流与帖子流共享 8px 扁平列表语言（`--radius-md`、1px 边框、hover 只改描边/表面色），
但保留自己的内容结构：整卡即「打开帖子」链接（`role="link"` + Enter 打开，嵌套的分类 chip 与 `#标签`
链接继续跳回帖子流对应过滤视图），卡片不再裹一层容器卡片。首载使用 UiSkeleton（card variant），
首屏失败由 UiState error 提供重试，空态由 UiState 给出「浏览帖子」下一步；追加失败保留已加载列表并内联
报错，「加载更多」按钮即重试入口。页码分页、会话 scope 竞态丢弃和拉黑过滤由
`frontend/src/views/useBookmarksFeed.js` 承载，事实语义未随迁移改变。

`/search` 承接壳搜索往返：Topbar 壳搜索提交后跳转 `/search?q=…`（已在搜索页时 replace query），搜索页把
`q` / `categoryId` / `tag` 路由 query 解析为搜索条件并响应地址栏变化（解析与序列化事实在
`frontend/src/views/search/useSearchPageState.js`）。筛选区由关键词 UiInput、分类 UiSelect（APG
combobox/listbox 键盘语义、可清除）和标签 UiAutosuggestInput 组成，「清空筛选」复位分类与标签；结果卡片
的分类 chip 与 `#标签` 按钮可点击并回写同一过滤模型，卡片自身以 `role="link"` + Enter 打开帖子详情
（Enter 只响应卡片自身焦点）。结果使用「加载更多」追加式分页：`GET /api/search/posts` 的页码 + size 调用
语义不变，命中后经 `POST /api/posts/batch-summary` 与作者 / 点赞计数补水合并；追加失败保留已加载列表并
内联报错，「加载更多」按钮即重试入口；首载骨架使用 UiSkeleton（card variant），空态与首载错态使用
UiState（错态带重试）。搜索结果来自 ES 投影、最终一致，页面以固定文案说明发帖 / 编辑后结果可能延迟数秒
到数十秒；标题与摘要高亮只放行后端返回的 `<em>` 标记（其余标签全部转义），匹配度分数按原值展示，不由
视觉层推断或伪造。

`/users/:userId` 成员主页随波次 5 完成迁移：身份头部（头像、成员名 H1、角色徽章、用户等级 / 签到 /
钱包 chip）、三项统计、公开资料与社区动向分区都是 8px 扁平表面，不再包裹外层容器卡片。访客（匿名）、
本人与他人三种查看状态共用同一模板：匿名只读公开身份与统计；本人显示「编辑资料」入口；他人在已登录时
显示关注 / 取消关注（`followStatusState` 决定可用性）、发私信、屏蔽与举报动作，权限与反馈语义不变。
首载使用 UiSkeleton（detail variant），首载失败使用 UiState error 并带重试；社区动向时间线卡是原生链接，
带可见 focus ring。

`/users/:userId/followees` 与 `/users/:userId/followers` 关系列表与帖子 / 收藏 / 搜索共享 8px 扁平列表
语言，并统一为「加载更多」追加式分页：`listFollowees` / `listFollowers` 的游标 + size 调用语义不变，
追加失败保留已加载列表并内联报错，「加载更多」按钮即重试入口；首载骨架使用 UiSkeleton（list variant），
空态（回到讨论区 / 返回主页两个下一步）与首载错态使用 UiState（错态带重试）。关系卡整卡可打开对应成员
主页（`role="link"` + Enter，Enter 只响应卡片自身焦点，嵌套的成员名链接与关注 / 取关按钮独立工作），
逐项关注 / 取关 mutation 与 hydration 语义不变。

新增页面时必须同步以下四处：

1. `routeCatalog.js` 登记 workspace、权限和 active family 等稳定事实。
2. `router/index.js` 注册 path、component、props 和页面文案。
3. `router/navigation.js` 决定是否进入导航及动态目标、移动端策略。
4. 对应 `*.test.js` 覆盖 catalog / route / nav / auth guard 行为。

## 产品壳层和移动导航

`frontend/src/components/layout/AppShell.vue` 负责桌面 workspace shell。`SidebarNav.vue` 收敛为社区（帖子、搜索、收藏、我的主页）、市场、个人（积分钱包、网盘、通知、私信、设置）三个一级域，管理组对有权限用户维持现状，匿名访客只看到社区 / 市场和登录入口；市场二级目的地（发布商品、我的出售、我的购买、出售订单）不再是侧边栏一级入口，改由市场页主操作进入，市场域内全部路由共用同一个侧边栏入口与选中态。账户区只保留在侧边栏底部：头像、姓名、角色徽章链接到我的主页，另有设置与登出；折叠态下两者以带 `aria-label` 的图标按钮呈现。`Topbar.vue` 只由折叠按钮、中文工作区 eyebrow（routeCatalog 的 workspace 标签：社区 / 市场 / 个人 / 运营 / 系统）、壳搜索和主题快捷按钮组成，不再渲染账户块、溢出菜单、页面标题或通知铃铛。壳搜索在公开页桌面宽度常显，`Cmd/Ctrl+K` 聚焦输入框，提交后跳转 `/search`（已在搜索页时 replace query）；市场页内搜索随市场波次交付。`MobileNav.vue` 保持讨论、搜索、通知、私信、我五个高频入口。移动端 sidebar drawer 状态与桌面 collapsed 偏好分离，避免 sidebar 和 bottom nav 同时作为持久导航出现。

导航选中态使用 `--accent-weak` 背景、`--accent-text` 文字和 3px accent 左轨；壳层图标统一为 `lucide-vue-next` 按需命名导入，`components/layout/navIcons.js` 只做导航 icon key 到 lucide 组件的映射，不新增本地 path 表或包装层。

侧边栏通知 / 私信入口和移动端对应入口显示未读角标（超过 99 显示 `99+`），计数由 `frontend/src/stores/inboxUnread.js` 聚合通知（`GET /api/notices/summary`）与私信（`GET /api/im/unread/summary`，群聊未读不计入私信角标）。角标在登录恢复 / 登出（身份变化）、窗口重新聚焦、通知已读操作（`NoticeDetailView`）、私信已读操作（会话详情 workflow）和 IM `privateMessage` 实时事件后刷新，不引入轮询；后台刷新通过 `skipGlobalErrorToast` 静默失败，身份切换时在途结果被丢弃。

## 主题、密度与设计令牌

`frontend/src/styles/variables.css` 是唯一令牌来源：Radix Indigo accent（含 `--accent-text` / `--accent-contrast`）、独立链接令牌 `--link-color`、暗色冷相表面 `#0D0E12`–`#23262E`、七阶语义 z-index（`--z-raised` 到 `--z-toast`）、70/110/150/240/400ms 五档动效时长与 ease 曲线；页面和组件不得重复定义这些令牌。

主题偏好是 `light` / `dark` / `system` 三态，由 `frontend/src/stores/ui.js` 持久化到 localStorage（`community.ui`）。`system` 表示跟随系统：store 通过 `matchMedia('(prefers-color-scheme: dark)')` 监听系统偏好并实时切换 `data-theme`；显式偏好不受系统变化影响。`public/theme-bootstrap.js` 在应用挂载前按同一合同解析生效主题与密度，避免首屏闪烁。Topbar 与 AuthShell 的快捷按钮继续在浅色 / 深色间切换：偏好为 `system` 时按当前生效主题切到相反主题，并保存为显式偏好。完整的三态主题与密度设置位于 Settings 外观区（`/settings?section=appearance`），读写同一份 store 偏好，刷新后保持。

密度只有 `compact` / `comfortable` 两档，`compact` 是默认值；两档共享组件 API，仅通过 `html[data-density='compact']` 令牌覆盖区分。`styles/base.css` 提供 `prefers-reduced-motion` 全局守卫，关闭非必要过渡与动画。

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
- 会话详情流程集中在 `frontend/src/views/useConversationDetailWorkflow.js`，只向组件公开 `model/actions/lifecycle`；HTTP/WS transport、请求竞态、订阅清理和滚动锚定不由组件直接管理。一个 `historyFlow` 统一记录 scope generation、基线阶段与轮次、连续 `seq` waterline、重连请求/完成轮次和实际补拉轮次；scope 切换会推进 generation，使旧异步执行失效。该流程先等待首次 `limit=50` history 建立基线，再在 `authed: false -> true` 后从最近一次由 HTTP history 确认的连续水位调用 after-seq backfill，并按每页 100 条推进；实时帧和 `committed` 回执不能跨越缺口推进该水位，HTTP 页内出现缺口时停在缺口前并在下次重连继续补拉。
- backfill 按会话 scope 单飞串行执行；每次重连上升沿推进请求轮次，当前执行按开始时覆盖的最新轮次完成，期间任意多次重连合并为下一轮，从最新水位继续补；空页同样完成其覆盖轮次，不能吞掉后续恢复请求。
- pending、committed、实时推送和 HTTP history 的消息观察通过 `seq`、服务端 `messageId`、`fromId + clientMsgId` 或发送 `requestId` 合并；`clientMsgId` 的唯一性是发送者作用域，peer 使用相同值不能替换或提交本地 pending。初始 history 慢响应也不能覆盖期间产生的 pending / failed 消息。
- 每条内部消息通过可枚举的 `messageIdentity` 记录显式保留 `serverMessageIds`、发送者作用域的 `clientMessageIds`、`requestIds` 和 `sequences` 别名。消息合并和排序逻辑在 `frontend/src/views/conversationDetailState.js`，任一别名命中都更新同一条消息，排序仍优先使用 `seq`，再回退到时间 / id；身份元数据不进入组件渲染模型。

## 页面状态模块

复杂页面的核心状态转换集中在 `frontend/src/views/*State.js`，并配套同名测试。

| 文件 | 责任 |
| --- | --- |
| `posts/usePostsFeed.js` | 帖子页的会话、范围（`categoryId` / `tag` / `order` 统一查询）、列表、未读和发帖五组页面语义；游标 feed 与搜索栈双数据源、隐藏游标、页码追加、补水、请求竞态和发帖幂等尝试。 |
| `useBookmarksFeed.js` | 收藏流的会话 scope、页码追加分页、请求竞态丢弃、拉黑过滤和打开帖子动作；组件只保留卡片渲染与键盘 Enter 守卫。 |
| `postsViewState.js` | 帖子流路由 query 解析/序列化（含 `boardId` 退役归一）与 feed/搜索栈数据源选择；发帖标签规范化、标签限制、帖子列表 hydration id 收集。 |
| `postDetailState.js` | 评论 / 回复 hydration id 收集、引用预览、回复内容组合，以及 `replyEditor`、`replyList`、`like` 三组评论 UI 状态初始化。 |
| `conversationDetailState.js` | 私信 conversation id 解析、Java UUID 排序、消息映射、去重和排序。 |
| `useConversationDetailWorkflow.js` | 私信详情的 HTTP/WS transport、历史分页、pending send、重连补拉、水位线、订阅和滚动生命周期。 |
| `marketState.js` | 商品、订单、争议、地址的展示投影；订单标签、资金、履约、下一步和允许动作来自同一份完整状态事实。 |
| `walletState.js` | 钱包状态文案、交易类型标签、金额展示和 feed key 生成。 |
| `driveState.js` | 网盘 quota 展示、breadcrumb、entry capability、分享表单校验和选择收敛。 |
| `registerFlowState.js` | 注册后邮箱验证码步骤的持久化、恢复和错误处理。 |
| `useUserProfilePage.js` | 用户主页的 route/session scope、并发加载、部分成功、关注/拉黑动作和生命周期隔离。 |
| `userProfileSurface.js` / `userProfileTimeline.js` | 用户主页摘要和时间线的纯展示投影。 |
| `search/useSearchPageState.js` | 搜索条件、路由解析与序列化、追加式分页（加载更多与失败重试）、请求竞态和结果 hydration 生命周期。 |
| `searchResultSurface.js` | 搜索结果展示状态。 |
| `settingsSection.js` | Settings 的 section query 深链合同（`profile` / `appearance` / `addresses`）与缺省、无效值回落。 |

新增复杂页面逻辑时，优先抽出纯函数并新增同名测试。跨请求或跨会话的页面流程使用页面专用 module，并向组件公开按页面意图命名的 model/actions/lifecycle 或语义分组；组件只保留 UI 绑定与纯格式化。

跨页面重复的有状态流程使用 focused module：`FollowRelationListView.vue` 通过 route props 的 `relationKind` 统一关注 / 粉丝列表的「加载更多」游标追加分页、hydration、账号 / 路由隔离和逐项 mutation；`MarketOrderListView.vue` 通过 route props 的 `side` 统一买单 / 卖单呈现，并由 `useMarketOrderList.js` 统一会话隔离、分页和过期请求丢弃；`useDrivePageState.js` 只协调 `page/workspace/entries/upload/shares` 五个页面模型，目录、条目、上传和分享各自由对应 workflow 管理 transport 与请求生命周期；`usePostDetailLoader.js` 只组合 `page/postActions/discussion` 三个模型，主帖动作和评论树分别由 `usePostDetailActions.js`、`usePostDetailDiscussion.js` 负责；`useTagSuggestions.js` 统一去抖、热门标签回退和 latest-request 竞态处理。聚合页面通过 `settledRequests.js` 独立提交成功分区；某个统计、钱包、首页计数或 Drive 分区失败时保留其他成功数据和上一份可用数据，不能用一个 rejected Promise 抹掉整个页面。

`utils/latestRequest.js` 的无参数 tracker 保持 token-only interface；传入 `getScope` 后，request handle 同时捕获 route / session scope，只有最新 token 且 scope 未变化时才能提交。当前先在关系列表试点，pagination append、mutation-by-id、partial success 和 IM backfill 继续保留各自状态语义，不做通用 async 状态机。

## 全局 Store

| Store | 文件 | 语义 |
| --- | --- | --- |
| Auth | `frontend/src/stores/auth.js` | access token、`me`、authorities、session hint 写入 / 清理。 |
| UI | `frontend/src/stores/ui.js` | theme（light/dark/system 三态偏好与系统主题监听）、density、桌面 sidebar collapsed 偏好、移动 sidebar drawer 临时状态。 |
| Taxonomy | `frontend/src/stores/taxonomy.js` | 分类和热门标签轻缓存。 |
| Post Meta Cache | `frontend/src/stores/postMetaCache.js` | 用户摘要、点赞数、点赞状态 TTL 缓存。 |
| Social Prefs | `frontend/src/stores/socialPrefs.js` | 拉黑读侧状态。 |
| Inbox Unread | `frontend/src/stores/inboxUnread.js` | 壳层通知 / 私信未读角标计数；按身份 scope 隔离，触发式刷新（身份变化、窗口聚焦、已读操作、IM 实时事件），不轮询。 |

`postMetaCache` 的 TTL 约定：

- 用户摘要缓存 60 秒。
- 点赞计数 / 状态缓存 30 秒。
- 点赞状态与登录态相关，auth 变化后应清理。

`userService` 的完整资料缓存使用 5 分钟 TTL 和 100 项 LRU 上限，并公开单用户/全量失效入口。缓存只接收明确列出的查看者无关资料字段，不保存 `hasFollowed` 等私有关系状态，因此可以跨登录代际复用；关注关系继续由 social API 按 auth generation 隔离。头像等资料写入成功后必须先失效对应用户；失效时仍在途的旧请求不得重新写回缓存。

## 产品 UI 基础件

`frontend/src/styles/variables.css`、`components.css` 和 `layout.css` 提供克制的产品默认样式。通用 `.card` 默认不带装饰性 hover lift 或大阴影。

`frontend/src/components/ui/UiState.vue` 只承担 empty / error / development 三种结果状态：empty 给出主要下一步，error 提供重试，development 标记未上线功能；不承担 loading。首载加载使用 `frontend/src/components/ui/UiSkeleton.vue`（list / card / detail 三档结构占位，`role="status"` 加 sr-only 标签向辅助技术播报），分页加载使用尾部指示，操作中状态使用按钮 loading；裸「加载中」文本由 `frontend/src/components/ui/loading-states.test.js` 按文件登记冻结，随页面迁移波次递减清零、不得新增。

浮层原语：`frontend/src/components/ui/UiModal.vue` 是统一的原生 `<dialog>` 外壳，提供 title、sm/md/lg 尺寸与 header/body/footer slots；Escape、backdrop 点击与关闭按钮只发出 close 请求，由使用方决定卸载时机，busy 期间禁止关闭。`frontend/src/components/ui/UiModalConfirm.vue` 已收敛到该外壳并保持既有确认语义（取消/确认文案、danger 变体），新增可选 busy 在异步确认期间禁用按钮与关闭路径。`frontend/src/components/ui/UiTooltip.vue` 提供 hover/focus 文字提示，自动做视口翻转与边界夹取，仅通过 `aria-describedby` 补充说明，trigger 保留自己的可访问名称，任何操作不依赖 tooltip 才能完成。`frontend/src/components/ui/UiDropdown.vue` 承载低频动作与入口菜单（关注 / 举报 / 屏蔽等治理动作，PostsView 工具栏的分类入口）：menu / menuitem 语义，trigger 携带 `aria-haspopup="menu"`、`aria-expanded` 与打开时的 `aria-controls`；click 与 Enter / Space / ↓ 打开并聚焦首个可用项（↑ 聚焦末项），菜单内 ↑/↓ 循环跳过禁用项、Home/End 跳转、Enter/Space 激活并经 `select` 事件交出被选项；Escape、选中、trigger 再点击与外部 pointerdown 关闭，Escape 与选中关闭后焦点返回 trigger。浮层 teleport 到 body，默认从 trigger 下缘对齐展开，视口空间不足时翻到上方并整体夹取在视口内（`--z-popover`、`--radius-lg`），危险动作以 `danger` 项标记；菜单只承载动作，不提供搜索或多选。

`frontend/src/components/ui/UiTabs.vue` 是深链可接入的选项卡原语：tablist / tab / tabpanel 语义与 `aria-controls` / `aria-labelledby` 双向关联，左右方向键自动激活并循环、Home/End 跳转，禁用 tab 被跳过且不可选；漫游 tabindex 只把选中 tab 留在 Tab 序列，面板一次性渲染并随选中切换可见性，重内容可用 panel slot 的 `active` 标志懒挂载。它只提供受控 `v-model`（`modelValue` + `update:modelValue`）：调用方把选中值映射到路由 query 即获得深链形态（PostsView 的最新/最热排序是首个生产接入，Settings 的 `?section=` 深链导航随波次 5 接入）；modelValue 缺失或指向禁用 / 不存在的 tab 时回退展示第一个可用 tab，但不代调用方发事件。tablist 横向溢出时滚动收缩（`overflow-x: auto`），桌面与移动视口均不撑破布局。

表单原语为 `frontend/src/components/ui/UiInput.vue`、`UiTextarea.vue` 和 `UiField.vue`：`UiInput` / `UiTextarea` 提供 `v-model`（含 trim / number 修饰符）、原生属性透传和禁用状态，`UiInput` 另有 size（md / sm）与 variant（outline / ghost）；`UiField` 承载 label 关联、帮助 / 错误文本（`aria-describedby` / `aria-invalid` / `role=alert`）和 `required` / `pattern` / `invalid` 原生校验语义，不引入表单校验库。字段内的 `UiInput` / `UiTextarea` 自动继承关联与校验状态；其他控件使用默认 slot 的 `controlId` / `describedBy` / `invalid` / `required` 手动接线。`.input`、`.auth-field`、`.field-label`、`.auth-form` 是原语内部实现细节，视图不得新增使用，现状由 `tokens.test.js` 的基线守卫登记、随页面簇迁移只减不增。

`frontend/src/components/ui/UiSelect.vue` 是单选下拉原语，承担搜索与其他筛选场景的单选控件：受控 `v-model`（`modelValue` + `update:modelValue`），选中已选值时不再重复发事件。它实现 APG select-only combobox/listbox 语义：触发按钮带 `role="combobox"`、`aria-haspopup="listbox"`、`aria-expanded` 与打开时的 `aria-controls`，可访问名称按「label + 当前值」经 `aria-labelledby` 组合（UiField 内由字段 label 提供并自动继承 `aria-describedby` / `aria-invalid`，`required` 映射为 `aria-required`；独立使用时由 `label` 属性提供隐藏标签）；DOM 焦点始终留在 combobox 上，打开后以 `aria-activedescendant` 指向活动选项，listbox 行带 `aria-selected` / `aria-disabled`。click 与 Enter / Space / ↓ / ↑ 打开（↓ 定位已选或首个可用项，无已选时 ↑ 定位末项），打开后 ↑/↓ 移动活动项（跳过禁用项、首尾不循环）、Home/End 跳转首尾、Enter/Space 选中并关闭；Escape 关闭且不改动选中，Tab 关闭并把焦点让给自然顺序，选中、Escape 与清除后焦点回到 combobox。typeahead 沿用原生 select 语义而不提供搜索输入框：关闭态键入字符按标签前缀直接选中匹配项（连续同字符循环、停顿后重新匹配、整串无匹配退化为最新字符），打开态键入只移动活动项，禁用项不参与匹配。`clearable` 提供带 `aria-label`（`clearLabel` 可配）的清除按钮并把值重置为 `''`；`loading` 打开时以 `role="status"` 状态行加 `aria-busy` listbox 播报并挂起导航与选中；空选项渲染禁用的「暂无可选项」行。默认 slot 自定义触发区内容，`option` slot 自定义选项行（scope 含 `option` / `active` / `selected`）。浮层 teleport 到 body，与 UiDropdown 同一定位策略（`placement` bottom / top，视口空间不足自动翻转并夹取在视口内，`--z-popover`），浮层内 mousedown 被拦截以保住 combobox 焦点。不支持多选。SearchView 的分类筛选是它的首个生产接入（随搜索波次迁移落地）。

`frontend/src/components/ui/UiButton.vue` 在原生 button 之外提供 `to` / `href` 链接形态，吸收“链接外观按钮”：链接形态复用同一 variant 命名与 `.btn` 外观，`disabled` 时阻止导航并以 `aria-disabled` 标记。登录、注册和密码重置页已收敛到 `UiField` + `UiInput` + `UiButton`：字段 label 成为控件的可访问名称，表单级错误文案与提交、验证码刷新和返回社区流程保持既有语义；验证码位图由真实 button 承载，可点击也可键盘触发刷新。403 / 404 页使用 `UiState`（error variant）与共享壳层，挂载后焦点移入状态区域（`tabindex="-1"`，不显示额外轮廓），返回帖子列表的入口是可键盘操作的 `UiButton` 链接。PostsView 已完成波次 2 试点迁移：发布 composer 收敛到 `UiField` + `UiInput` + `UiAutosuggestInput`，首载骨架使用 `UiSkeleton`（card variant），分页尾部指示与按钮 loading 分离，视图样式全部位于 `<style scoped src="./posts/PostsView.css">`，不再使用 `.btn` / `.input` / `.card` / `.skeleton` 原语内部类。BookmarksView 已随波次 3 完成迁移：列表卡为 8px 扁平表面（无容器卡片嵌套），首载骨架、空/错态与尾部加载指示全部走 Ui 原语，裸「加载中」文本登记随之删除。PostDetailView 与评论 / 回复组件已完成波次 3 迁移：详情、评论区和回复编辑器全部使用 Ui 原语与 scoped 样式（`frontend/src/views/post-detail/*.css`），令牌取自 `variables.css`。SearchView 已随波次 4 完成迁移：关键词、分类与标签筛选收敛到 UiInput / UiSelect / UiAutosuggestInput，结果卡片使用 8px 扁平列表语言，首载骨架、空 / 错态与「加载更多」尾部指示全部走 Ui 原语，页面级暗色覆盖与原语内部类使用清零。SettingsView 已随波次 5 完成迁移：section 导航接入 UiTabs，地址表单收敛到 UiField + UiInput，地址簿首载骨架、可重试错态与空态全部走 Ui 原语，`.input` 内部类与 `market-*` 全局页面样式依赖清零，`tokens.test.js` 的 Settings 基线登记随之移除。NoticesView 与 NoticeDetailView 已随波次 6 完成迁移：两个视图改用 8px 扁平列表语言（不再裹容器卡片），未读条目以 3px accent 左轨加弱色 chip 表达，首载骨架、可重试错态、带主要下一步的空态全部走 Ui 原语；主题详情从 `UiPagination` 翻页改为「加载更多」追加（`useNoticeTopicFeedState`），「标记已读」成功后本地翻转已读并刷新壳层未读角标（结果立即可见、静默更新，不弹 toast），返回层级由页顶「返回通知汇总」链接承担；手写 `<svg>` 图标换成 lucide 命名导入，`.btn` 链接外观按钮收敛到 `UiButton` 的 `to` 形态，`loading-states.test.js` 的 `NoticeDetailView.vue` 裸「加载中」登记随之移除。

所有 dialog 的焦点由 `frontend/src/composables/useModalFocus.js` 管理：打开后聚焦 `[data-autofocus]` 或首个可操作控件，Tab / Shift+Tab 保持在弹窗内，关闭或卸载后恢复触发控件焦点；同时保留 `role=dialog`、`aria-modal`、可关联标题 / 描述和 Escape 关闭语义。UiModal / UiModalConfirm / ReportModal / EditContentModal 已接入；ModerationView 的旧 dialog 随治理页面收尾迁移。

页面需要展示调试辅助信息时，应使用 `UiState` 的 `development` variant 显式标记，而不是把它伪装成普通业务内容。正式 router 不注册独立开发入口。

全局样式与令牌约定由 `frontend/src/styles/tokens.test.js` 约束：它锁定规范批准的令牌值、对比度阈值、`var()` 引用与 hex fallback、页面级 `data-theme` 覆盖、z-index 语义令牌和 reduced-motion 守卫；新增全局样式时不要绕过这些 guardrail。

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

迁移前视觉基线位于 `tests/playwright-single/tests/08-visual.spec.ts`。它复用 single
套件的认证、路由和页面/API 错误审计 fixture，以 18 个页面状态、22 张 PNG 固定
当前 compact 桌面 UI；登录、Posts、PostDetail 和 Settings 同时覆盖明暗主题。
运行入口为 `npm --prefix tests/playwright-single run test:visual`。

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
| 修改主题三态、密度或全局令牌 | 本文档、`frontend/src/stores/ui.test.js`、`frontend/src/styles/tokens.test.js`。 |
