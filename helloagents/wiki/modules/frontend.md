# frontend（Vue3 SPA）

## 1. 技术栈
- Vite + Vue3 + Vue Router + Pinia + Axios

## 2. 信息架构（路由分层）
> 目标：对齐 历史单体 的“用户可点可用”体验，并为后续逐页打磨提供稳定路由骨架。

### 2.1 认证域（/auth/*）
- `/auth/login`（alias：`/login`）
- `/auth/register`
- `/auth/password/reset`（找回/重置密码）
- `/auth/activation/:userId/:code`

### 2.2 内容域（/posts/*）
- `/` → 重定向 `/posts`
- `/posts`（帖子列表 + 发帖入口）
- `/posts/:postId`（帖子详情 + 评论/回复树 + 点赞 + 管理操作入口）
- `/bookmarks`（我的收藏）

### 2.3 社交与消息域
- `/users/:userId`（用户主页）
- `/users/:userId/followees`（关注列表）
- `/users/:userId/followers`（粉丝列表）
- `/messages`（会话列表）
- `/messages/:conversationId`（私信详情）
- `/notices`（通知汇总）
- `/notices/:topic`（通知详情）

### 2.4 搜索与统计域
- `/search`（搜索 + 高亮；管理员可重建索引，带确认弹窗）
- `/analytics`（管理员/版主可见）
- `/leaderboard`（积分榜单）

### 2.5 系统页
- `/settings`（设置：头像回写等）
- `/moderation`（治理后台：仅管理员/版主可见）
- `/403`（Forbidden）
- `/*`（Not Found）

### 2.6 权限约定（体验层）
- `meta.requiresAuth=true`：未登录跳转到登录页，并携带 `redirect=<to.fullPath>`。
- `meta.roles=[ROLE_ADMIN, ROLE_MODERATOR]`：前端通过 `/api/auth/me` 懒加载并判断；最终仍以后端鉴权为准。

## 3. 关键文件
- 入口：`frontend/src/main.js`
- 首屏主题/密度预加载：`frontend/index.html`（读取 `localStorage: community.ui` 写入 `html[data-theme|data-density]`，避免闪烁）
- 路由：`frontend/src/router/index.js`
- 路由守卫：`frontend/src/router/authGuard.js`
- 导航配置 SSOT：`frontend/src/router/navigation.js`（Sidebar/MobileNav 分组与权限 + posts 排序/筛选映射 + breadcrumb 推导）
- store：`frontend/src/stores/auth.js`
- UI 偏好（主题/密度/侧栏折叠）：`frontend/src/stores/ui.js`
- 应用级状态（traceId）：`frontend/src/stores/app.js`
- http client：`frontend/src/api/http.js`
- Result 解析（业务码抛错）：`frontend/src/api/result.js`
- API services（按域封装）：`frontend/src/api/services/*`
- 内部组件库（最小集合）：`frontend/src/components/ui/*`
- Markdown 渲染（受控 `v-html` 点位）：`frontend/src/components/ui/UiMarkdown.vue`（先整体 escape，再生成白名单标签）
- 搜索高亮（受控 `v-html` 点位）：`frontend/src/utils/highlight.js`（只放行 `<em>`，用于 SearchView）
- Feed 工具栏：`frontend/src/components/posts/FeedToolbar.vue`（排序 chips + 筛选 chips + 清空 + 刷新）
- 通用 chips 控件：`frontend/src/components/ui/UiChips.vue`
- 统一身份徽章：`frontend/src/components/ui/UiRoleBadge.vue`
- 布局骨架（工作区三栏）：`frontend/src/components/layout/*`（AppShell/SidebarNav/Topbar/AuthShell）
- 移动端底部导航（可选增强）：`frontend/src/components/layout/MobileNav.vue`
- 右侧上下文面板：`frontend/src/components/layout/RightPanel.vue`（可开关，桌面端默认开启）
- 全局样式入口（SSOT）：`frontend/src/styles/index.css`（唯一入口；分层导入 variables/base/utils/layout/components/pages）
- 页面级通用结构样式：`frontend/src/styles/pages.css`（非业务逻辑，例如帖子卡片结构）
- Vite proxy：`frontend/vite.config.js`（默认转发 `/api` 到 `http://localhost:12882`，可用 `VITE_DEV_PROXY_TARGET` 覆盖；dev/preview 端口默认 `12881`，可用 `VITE_DEV_PORT` / `VITE_PREVIEW_PORT` / `VITE_PORT` 覆盖）
- 评论/回复锚点定位工具：`frontend/src/utils/scrollToAnchor.js`（hash/query 定位 + 高亮）

## 4. 关键能力（UI 等价所需）
- 登录态：`accessToken` 存放在 Pinia store（内存）；refresh token 存放在 HttpOnly Cookie。
- Axios 拦截器：
  - 自动注入 `Authorization: Bearer <accessToken>`
  - 401 自动触发 `/api/auth/refresh` 并重试（单飞一次刷新 Promise 去重）
  - refresh 失败时清理登录态，并硬跳转 `/#/auth/login`（避免 `router -> guard -> service -> http -> router` 循环依赖）
- 统一错误处理：后端 `Result<T>` 的 `code!=0` 在前端转换为 `BusinessError`（含 `code/traceId/data`）。
- 统一 Toast：
  - 应用只保留一个 `UiToast` 实例（`frontend/src/App.vue`），页面通过 `inject('showToast')` 使用。
  - Axios 拦截器保留兼容入口：`window.$toast`（由 `App.vue` 绑定到同一实现），用于网络/5xx 全局错误提示。
  - Toast 支持可选 action（`actionText`/`onAction`）：用于成功提示里提供“立即查看/去搜索”等快捷入口（避免用户误以为最终一致延迟是功能故障）。
- 组件库与交互一致性：基础按钮/输入/分页/确认弹窗等，降低逐页补齐时的交互碎片化风险。
- Notion 风格工作区能力：
  - Design Tokens：在 `frontend/src/styles/variables.css` 基于 CSS Variables 统一颜色/字体/间距/圆角/阴影；入口为 `frontend/src/styles/index.css`。
  - 主题切换：通过 `html[data-theme="light|dark"]` 切换变量集，偏好持久化到 `community.ui`。
  - 密度切换：通过 `html[data-density="compact|comfortable"]` 调整控件高度与间距，偏好持久化到 `community.ui`。
  - 全局布局：AppShell 三栏（左导航/顶栏/内容）对齐桌面优先体验；认证页统一使用 AuthShell 简化骨架（含登录/注册/找回/激活）。
  - 右侧面板：RightPanel 提供快捷键/提示/页面上下文信息，可通过 Topbar 开关并持久化 `rightPanelOpen`。

## 7. BBS UI 维护约定（新增）

### 7.1 导航配置 SSOT（Sidebar + MobileNav + Breadcrumb）
- 新增/调整导航入口：优先修改 `frontend/src/router/navigation.js`，避免在 `SidebarNav.vue`/`MobileNav.vue` 里硬编码。
- 权限约定：`requiresAuth` / `roles` 在导航层仅影响“是否显示入口”；真正的访问控制仍依赖路由守卫与后端鉴权。
- Breadcrumb：`UiBreadcrumb` 默认在 `items` 为空时自动推导（来源：`navigation.js#getBreadcrumbItems`）；如需自定义可继续传 `:items="..."`（兼容旧用法）。

### 7.2 Posts URL Query 约定（FeedToolbar）
- posts 列表页使用 URL query 作为 SSOT：
  - `order`: `hot`（默认 `latest` 可省略）
  - `type`: `top|wonderful`（默认空可省略）
  - `categoryId`: 分类 ID（默认空可省略）
  - `tag`: 单个标签名（默认空可省略；用于列表过滤）
- `FeedToolbar` 通过 `update:order/update:filter` 事件由 `PostsView.vue` 统一写回 query，避免多个组件各写一套逻辑。

### 7.3 评论/回复定位与引用回复（PostDetail）
- 锚点定位：
  - hash 模式：`#c-<commentId>` / `#r-<replyId>`
  - query 模式：`?commentId=<id>&replyId=<id>`（用于需要先展开回复再定位的场景）
- 引用回复 MVP：回复时在提交内容中插入 Markdown blockquote（`> ...`），并用 `UiMarkdown` 渲染（评论/回复使用 `variant="compact"`）。
- `UiMarkdown` 渲染策略：先 escape，再白名单生成可控标签（段落/列表/引用/代码块/链接），确保可读性与安全性兼顾；正文使用 default（更松行距），评论/卡片使用 compact（更紧凑）。
- 草稿：评论/回复草稿使用 `localStorage`，按 `postId` 隔离（键空间统一为 `community.draft.posts.<postId>.*`）。

### 7.4 可访问性（A11y）
- Icon button 必须补齐 `aria-label`；可见文案存在时可不补。
- 交互控件必须可 keyboard focus；`focus-visible` 统一使用 `var(--focus-ring)`。

### 7.5 空状态与右侧面板（视觉一致性）
- 空状态统一使用 `frontend/src/components/ui/UiEmpty.vue`：默认卡片化展示，支持 `description/actions` slot，避免出现“大片留白 + 文案孤零零”的廉价观感。
- 列表页空态建议提供可操作 CTA：刷新、切换筛选、登录/发帖等，引导用户下一步。
- `RightPanel` 建议以 `card` 分区承载不同信息块；卡片 hover 避免位移（防止鼠标移动时抖动），列表项建议使用 `button` 以获得更好的可访问性与交互一致性。

### 7.6 技术社区列表风格（GitHub Discussions × Discourse）
- 默认密度：新用户默认 `compact`（`frontend/src/stores/ui.js`），更适合 PC 主场景的高信息密度浏览；用户仍可在 Topbar/Sidebar 切换到 `comfortable`。
- 列表视觉：帖子/搜索结果推荐使用「紧凑行 + 分隔线 + 轻 hover」的 topic list（样式在 `frontend/src/styles/pages.css`：`topic-list/topic-row/*`）。

### 7.7 样式入口与清理约定（SSOT + 未用 CSS 清理）
- 全局样式只允许通过 `frontend/src/styles/index.css` 引入，避免出现多个入口导致的维护歧义（历史 `frontend/src/styles.css` 已移除）。
- 样式分层：`variables.css`（tokens）→ `base.css`（重置/基础排版）→ `utils.css`（工具类）→ `layout.css`（骨架布局）→ `components.css`（组件样式）→ `pages.css`（页面通用结构）。
- 页面 `scoped` 样式仅保留“与该页面强绑定”的部分；通用模式优先沉淀到 `frontend/src/styles/*` 对应分层。
- 清理策略（保守）：仅删除“确认无引用”的文件/选择器；对动态 class/条件渲染相关样式默认保留；清理后必须通过 `npm -C frontend test` + `npm -C frontend run build` 验证。
- 尽量避免零散 `style=""`；若为临时排障或 MVP，后续应收敛到 scoped 或全局分层，保持可维护性。
- 交互收敛：`card/button/chips` hover 不做 `translate/scale` 位移动画，优先使用 `border/background/shadow` 的轻变化，保持“专业、克制、不浮夸”的 BBS 观感。

### 7.7 Discourse-like topic list（列布局 / 未读 / 最后回复）
- 列布局：`PostsView.vue` 使用 topic list 四列布局（Title / Replies / Likes / Activity），包含表头（`topic-head`），更接近 Discourse 的扫读体验。
- 最后回复：列表的 Activity 列展示“最后回复人 + 时间”；列表补水采用 batch API（用户摘要/点赞计数/点赞状态）并使用 TTL 缓存（`frontend/src/stores/postMetaCache.js`）。
- 未读提示（轻量版）：
  - 不依赖后端“已读时间线”，使用本地 `localStorage` 追踪（键：`community.read.posts.v1`，实现：`frontend/src/utils/readTracker.js`）。
  - 列表首次访问不强制全量未读；后续根据 `lastActivityTime` 与本地 `readAt/baseline` 比较决定是否显示未读点。
  - 点击帖子进入详情页会写入已读标记（`PostsView.vue#openPost` + `PostDetailView.vue` onMounted/watch）。
- 新内容提示（更贴近 Discourse 的“new / last visit”体验）：
  - 仅在 `order=latest&type=` 的首屏生效（避免在未读/置顶/精华等视图误触发 baseline 更新）。
  - 顶部展示“自上次访问后新增 X 条”提示条，并提供“上次位置”滚动入口。
  - 列表中插入“上次看到这里”分割线（基于 `lastActivityTime` 与 `readTracker` baseline 对比）。

### 7.8 信息架构增强（更贴近 Discourse）
- posts 筛选新增 `未读`（URL query：`type=unread`，仅登录可用）：
  - 顶部 `FeedToolbar` chips 同步支持 `未读`
  - Sidebar 的「筛选」分组增加 `未读` 入口
- 右侧面板（`RightPanel`）增加：
  - “快速筛选”（最新/热门/未读/置顶/精华）
  - “分类”（来自后端 `/api/categories`，点击进入 posts 列表过滤）
  - “热门标签”（来自后端 `/api/tags/hot` 聚合，点击进入 posts 列表过滤）

## 5. 页面与 API 依赖（摘要）
- `LoginView.vue`：risk-based 验证码（后端返回 `code=10005/10006` 时展示验证码）；`/api/auth/captcha` + `/api/auth/login`；支持 `redirect` 回跳。
- `RegisterView.vue` / `ActivationView.vue`：注册与激活闭环；注册强制验证码：`/api/auth/captcha` + `/api/auth/register`。
- `PasswordResetView.vue`：找回/重置密码（为避免用户枚举，request 统一返回“已处理”）；`/api/auth/captcha` + `/api/auth/password/reset/request|confirm`。
- `PostsView.vue`：
  - `GET /api/posts`（分页；支持 `categoryId/tag` 过滤）+ `POST /api/posts`（支持 `categoryId/tags[]`）
  - taxonomy：`GET /api/categories`（分类下拉/列表映射）、`GET /api/tags/hot`（热门标签）、`GET /api/tags/suggest`（标签自动补全）
  - 列表补水预算（建议 3~4 个请求/次）：
    - `POST /api/users/batch-summary`
    - `GET /api/likes/counts`
    - `GET /api/likes/statuses`（仅登录态）
    - 前端缓存：`frontend/src/stores/postMetaCache.js`（用户摘要 60s；点赞计数/状态 30s，用于降低“写后刷新读旧投影”的感知不一致）
- `PostDetailView.vue`：帖子详情 + taxonomy（分类/标签跳转过滤） + 点赞/关注 + 评论/回复树；管理员/版主可执行 `/api/posts/{postId}/top|wonderful|delete`（二次确认）。
- `PostDetailView.vue`（一致性体验补充）：点赞/取消点赞成功后写入短 TTL 覆盖；刷新/重载时优先合并覆盖（read-your-writes），降低事件投影尚未收敛时的可见不一致。
- `PostsView.vue` / `PostDetailView.vue`（最终一致 UX）：发帖/编辑成功后提示“搜索/通知可能延迟”，并提供“立即查看帖子/去搜索”等快捷入口。
- `UserProfileView.vue`：用户主页（含获赞/关注/粉丝统计）+ 关注/取关 + 关注/粉丝列表入口；当后端标记 `socialDegraded=true` 时展示占位并提示可刷新。
- `FolloweesView.vue` / `FollowersView.vue`：关注/粉丝列表（分页 + 用户摘要 + 关注状态）。
- `ConversationsView.vue` / `ConversationDetailView.vue`：私信会话与详情（分页 + 已读）。
- `NoticesView.vue` / `NoticeDetailView.vue`：通知汇总与详情（分页 + 已读）。
- `SearchView.vue`：搜索与高亮；支持分类/标签过滤（`/api/search/posts?keyword&categoryId&tag`）；提示“搜索索引最终一致，可能延迟”；管理员可触发重建索引（`POST /api/ops/search/reindex`）。
- `AnalyticsView.vue`：UV/DAU 查询（管理员/版主）。
- `SettingsView.vue`：头像上传（local/qiniu）与回写。
- `OpsConsoleView.vue`：运维控制台（仅管理员）：集中高风险动作并提供配置引导。
- `UserManagementView.vue`：用户管理（仅管理员）：搜索用户并修改角色（USER/MODERATOR/ADMIN）。

## 6. 本地运行（示例）
- 安装依赖：`npm -C frontend ci`
- 启动开发服务器（默认 `12881`，可通过 env 覆盖）：`npm -C frontend run dev`
- 单测：`npm -C frontend test`
- 构建：`npm -C frontend run build`
- 注意：CI 会执行 `npm -C frontend run build`；即使单测通过，Vue 模板/样式语法错误也可能在 build 阶段直接失败（例如重复 attribute、缺失 `}`）。

### 6.1 Docker Compose：前端直连 gateway（推荐用于本地联调）
目标：不依赖 Nginx，前端与网关分别暴露两个端口；浏览器访问前端，前端跨端口直连 gateway。

- 前端（Vite preview）：`http://localhost:12881`
- gateway（API）：`http://localhost:12882`

启动方式（在仓库根目录执行）：
- `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`

说明：
- 前端在本地 `localhost` 且页面端口为 `12881` 时，会自动推导 API 基址为 `http://localhost:12882`（详见 `frontend/src/api/http.js`）。
- 如需覆盖，可通过 `VITE_API_BASE_URL` 指定（若使用非 `localhost` 的页面地址，需要同步调整 gateway allowlist：CORS + OriginGuard）。
- 若希望使用本地 dev（HMR）但后端仍用 compose：请在启动 compose 时用 `--scale frontend=0` 禁用容器内前端（避免端口冲突），并统一用 `http://localhost:<前端端口>` 访问；若端口不是 `12881`，需确保 gateway allowlist（CORS + OriginGuard）包含对应 origin。
