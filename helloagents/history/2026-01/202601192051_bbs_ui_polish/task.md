# Task List: BBS 体验增强（信息架构 / 列表扫读 / 详情与评论树 / 治理与账号 / 体验底盘）

Directory: `helloagents/plan/202601192051_bbs_ui_polish/`

---

## 1. 信息架构与导航（frontend）
- [√] 1.1 新增导航配置 SSOT：实现 `frontend/src/router/navigation.js`（sidebar 分组/权限/路由映射 + posts 筛选配置），verify why.md#requirement-bbs-ia-001-information-architecture--navigation, why.md#scenario-bbs-ia-001-1-cross-page-navigation-clarity
- [√] 1.2 SidebarNav 配置化渲染：重构 `frontend/src/components/layout/SidebarNav.vue` 使用 `navigation.js` 渲染，统一 active 判定与文案一致性，verify why.md#requirement-bbs-ia-001-information-architecture--navigation, why.md#scenario-bbs-ia-001-1-cross-page-navigation-clarity, depends on task 1.1
- [√] 1.3 面包屑 SSOT：扩展 `frontend/src/components/ui/UiBreadcrumb.vue` 支持从 route meta/navigation 配置推导（保留手动 items 兼容），并在 posts/detail/profile 等页面落地，verify why.md#requirement-bbs-ia-001-information-architecture--navigation, depends on task 1.1
- [√] 1.4 移动端入口增强（可选）：新增移动端底部导航组件 `frontend/src/components/layout/MobileNav.vue`（帖子/搜索/消息/我的），并与 sidebar 共用配置，verify why.md#scenario-bbs-ia-001-2-mobile-drawer-behavior, depends on task 1.1

## 2. 帖子列表扫读体验（frontend）
- [√] 2.1 通用筛选 chips 组件：新增 `frontend/src/components/ui/UiChips.vue`（或 UiSegmented），并在 `frontend/src/styles/components.css` 补齐样式（选中态/hover/focus），verify why.md#requirement-bbs-feed-002-feed-scannability
- [√] 2.2 FeedToolbar：新增 `frontend/src/components/posts/FeedToolbar.vue`（排序 + 筛选 chips + 清空），并确保与 URL query 同步，verify why.md#scenario-bbs-feed-002-1-consistent-post-card-hierarchy, depends on task 2.1
- [√] 2.3 PostsView 重构：改造 `frontend/src/views/PostsView.vue` 使用 FeedToolbar，并统一空/错/加载态结构（skeleton/empty/error），verify why.md#scenario-bbs-feed-002-1-consistent-post-card-hierarchy, depends on task 2.2
- [√] 2.4 Posts 性能热点治理（前端侧）：为 posts 列表补水请求（作者/点赞）增加缓存/并发控制/延迟加载策略（避免首屏 N+1 放大），verify why.md#requirement-bbs-foundation-005-ux-foundation, depends on task 2.3

## 3. 帖子详情与评论树（frontend）
- [√] 3.1 阅读模式排版统一：收敛 `frontend/src/components/ui/UiMarkdown.vue` 排版规则（标题/引用/代码块），并与 reading 宽度配合（必要时补齐 `frontend/src/styles/pages.css`），verify why.md#scenario-bbs-detail-003-1-reading-mode-typography
- [√] 3.2 评论定位：为 PostDetail 评论/回复增加“可定位锚点”（hash 或 query），并实现滚动定位与高亮（新增 `frontend/src/utils/scrollToAnchor.js`），verify why.md#requirement-bbs-detail-003-post-detail--comment-tree
- [√] 3.3 折叠/展开：为评论树增加折叠能力（默认展开一层，深层可折叠），并确保交互热区与状态提示一致，verify why.md#requirement-bbs-detail-003-post-detail--comment-tree
- [√] 3.4 引用回复 MVP：回复时展示引用块并最小闭环提交（必要时扩展 `frontend/src/api/services/postService.js` 的 addComment 参数映射），verify why.md#scenario-bbs-detail-003-2-threading--quote-reply-mvp
- [√] 3.5 草稿能力：为发评论/回复增加按 postId 隔离的草稿保存（localStorage），verify why.md#scenario-bbs-detail-003-2-threading--quote-reply-mvp

## 4. 账号与治理（frontend）
- [√] 4.1 统一身份徽章：统一 ADMIN/MOD 等标识的展示规则（建议沉淀到 `frontend/src/components/ui/UiRoleBadge.vue`），并在 PostDetail/UserProfile/Sidebar/Topbar 使用，verify why.md#requirement-bbs-mod-004-identity--moderation
- [√] 4.2 危险操作规范：统一删除/置顶/加精等确认弹窗的文案与 danger 样式（补齐 `UiModalConfirm` 使用点），verify why.md#scenario-bbs-mod-004-1-safe-destructive-actions
- [√] 4.3 举报/屏蔽入口占位（可选）：在帖子详情/用户卡片处预留 UI 入口（若无后端 API 则禁用并提示“即将上线”），verify why.md#requirement-bbs-mod-004-identity--moderation

## 5. 体验底盘（frontend）
- [√] 5.1 统一状态组件：补齐 `UiEmpty` 类型化用法与 error block 规范，并为核心页面对齐（posts/search/messages/settings/auth/profile），verify why.md#scenario-bbs-foundation-005-1-unified-states--feedback
- [√] 5.2 可访问性补齐：全站 icon button 补齐 `aria-label`，并验证 focus-visible 在关键页面可用（Topbar/Sidebar/Chat/Posts actions），verify why.md#requirement-bbs-foundation-005-ux-foundation
- [√] 5.3 移动端触控热区与布局：对核心页面（posts/detail/messages）在 ≤768px 下做热区与间距校验，必要时补齐 `frontend/src/styles/layout.css` / `frontend/src/styles/pages.css` 的响应式规则，verify why.md#scenario-bbs-ia-001-2-mobile-drawer-behavior

## 6. Security Check
- [√] 6.1 执行安全检查（per G9）：重点检查 `v-html` 渲染点（搜索高亮/Markdown）、外链跳转与 localStorage 草稿键空间隔离，不引入敏感信息持久化

## 7. Documentation Update（Knowledge Base）
- [√] 7.1 更新 `helloagents/wiki/modules/frontend.md`：补齐“导航配置 SSOT / FeedToolbar / 评论定位与引用回复 / 可访问性规范”的维护约定
- [√] 7.2 更新 `helloagents/CHANGELOG.md`：记录本次 BBS UI 体验增强的变更点

## 8. Testing
- [√] 8.1 为导航配置与筛选映射补齐纯函数单测：新增 `frontend/src/router/navigation.test.js`（不引入 UI 渲染测试依赖）
- [√] 8.2 质量门禁：运行 `npm -C frontend test` 与 `npm -C frontend run build`，确保单测与构建通过
