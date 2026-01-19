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

### 2.5 系统页
- `/settings`（设置：头像回写等）
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
- store：`frontend/src/stores/auth.js`
- UI 偏好（主题/密度/侧栏折叠）：`frontend/src/stores/ui.js`
- 应用级状态（traceId/toast）：`frontend/src/stores/app.js`
- http client：`frontend/src/api/http.js`
- Result 解析（业务码抛错）：`frontend/src/api/result.js`
- API services（按域封装）：`frontend/src/api/services/*`
- 内部组件库（最小集合）：`frontend/src/components/ui/*`
- 布局骨架（工作区三栏）：`frontend/src/components/layout/*`（AppShell/SidebarNav/Topbar/AuthShell）
- 右侧上下文面板：`frontend/src/components/layout/RightPanel.vue`（可开关，桌面端默认开启）
- Vite proxy：`frontend/vite.config.js`（默认转发 `/api` 到 `http://localhost:12882`，可用 `VITE_DEV_PROXY_TARGET` 覆盖）

## 4. 关键能力（UI 等价所需）
- 登录态：`accessToken` 存放在 Pinia store（内存）；refresh token 存放在 HttpOnly Cookie。
- Axios 拦截器：
  - 自动注入 `Authorization: Bearer <accessToken>`
  - 401 自动触发 `/api/auth/refresh` 并重试（单飞一次刷新 Promise 去重）
  - refresh 失败时清理登录态，并硬跳转 `/#/auth/login`（避免 `router -> guard -> service -> http -> router` 循环依赖）
- 统一错误处理：后端 `Result<T>` 的 `code!=0` 在前端转换为 `BusinessError`（含 `code/traceId/data`）。
- 组件库与交互一致性：基础按钮/输入/分页/确认弹窗等，降低逐页补齐时的交互碎片化风险。
- Notion 风格工作区能力：
  - Design Tokens：在 `frontend/src/styles.css` 基于 CSS Variables 统一颜色/字体/间距/圆角/阴影。
  - 主题切换：通过 `html[data-theme="light|dark"]` 切换变量集，偏好持久化到 `community.ui`。
  - 密度切换：通过 `html[data-density="compact|comfortable"]` 调整控件高度与间距，偏好持久化到 `community.ui`。
  - 全局布局：AppShell 三栏（左导航/顶栏/内容）对齐桌面优先体验；认证页使用 AuthShell 简化骨架。
  - 右侧面板：RightPanel 提供快捷键/提示/页面上下文信息，可通过 Topbar 开关并持久化 `rightPanelOpen`。

## 5. 页面与 API 依赖（摘要）
- `LoginView.vue`：risk-based 验证码（后端返回 `code=10005/10006` 时展示验证码）；`/api/auth/captcha` + `/api/auth/login`；支持 `redirect` 回跳。
- `RegisterView.vue` / `ActivationView.vue`：注册与激活闭环；注册强制验证码：`/api/auth/captcha` + `/api/auth/register`。
- `PasswordResetView.vue`：找回/重置密码（为避免用户枚举，request 统一返回“已处理”）；`/api/auth/captcha` + `/api/auth/password/reset/request|confirm`。
- `PostsView.vue`：`/api/posts`（分页）+ `POST /api/posts`；作者信息来自 `/api/users/{userId}`；点赞数来自 `/api/likes/count`。
- `PostDetailView.vue`：帖子详情 + 点赞/关注 + 评论/回复树；管理员/版主可执行 `/api/posts/{postId}/top|wonderful|delete`（二次确认）。
- `UserProfileView.vue`：用户主页（含获赞/关注/粉丝统计）+ 关注/取关 + 关注/粉丝列表入口。
- `FolloweesView.vue` / `FollowersView.vue`：关注/粉丝列表（分页 + 用户摘要 + 关注状态）。
- `ConversationsView.vue` / `ConversationDetailView.vue`：私信会话与详情（分页 + 已读）。
- `NoticesView.vue` / `NoticeDetailView.vue`：通知汇总与详情（分页 + 已读）。
- `SearchView.vue`：搜索与高亮；管理员重建索引带确认弹窗。
- `AnalyticsView.vue`：UV/DAU 查询（管理员/版主）。
- `SettingsView.vue`：头像上传 token（七牛）与回写。

## 6. 本地运行（示例）
- 安装依赖：`npm -C frontend ci`
- 启动开发服务器（端口固定 `12881`）：`npm -C frontend run dev`
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
- 如需覆盖，可通过 `VITE_API_BASE_URL` 指定（若使用非 `localhost` 的页面地址，需要同步调整后端 `Origin` 白名单与 gateway CORS 配置）。
 - 若希望使用本地 dev（HMR）但后端仍用 compose：请在启动 compose 时用 `--scale frontend=0` 禁用容器内前端（避免 `12881` 端口冲突），并统一用 `http://localhost:12881` 访问以匹配 Origin 白名单。
