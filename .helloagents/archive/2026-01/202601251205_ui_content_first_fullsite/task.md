# Task List: 前端 UI 内容优先全站一致性重构

Directory: `.helloagents/archive/2026-01/202601251205_ui_content_first_fullsite/`

---

## 1. Design Tokens & Global Styles（frontend）
- [√] 1.1 调整可读性相关 tokens（文本层级/标题层级/密度）：`frontend/src/styles/variables.css`，verify why.md#requirement-layout-consistency
- [√] 1.2 统一页面标题与动作区视觉层级：`frontend/src/styles/components.css`，verify why.md#requirement-layout-consistency
- [√] 1.3 优化论坛列表通用模式（topic-list/meta 对比度与移动端）：`frontend/src/styles/pages.css`，verify why.md#requirement-responsive-consistency

## 2. Markdown 阅读体验（frontend）
- [√] 2.1 增强 Markdown 渲染语义（段落/列表/引用/代码块占位恢复）：`frontend/src/components/ui/UiMarkdown.vue`，verify why.md#requirement-reading-experience
- [√] 2.2 调整 Markdown 排版样式（default vs compact、代码块/引用/链接）：`frontend/src/components/ui/UiMarkdown.vue`，verify why.md#requirement-reading-experience

## 3. Views 全站一致性重构（frontend）
- [√] 3.1 统一 Feed/列表页标题区与工具栏布局：`frontend/src/views/PostsView.vue`，verify why.md#requirement-layout-consistency
- [√] 3.2 统一详情页信息层级与阅读区间距：`frontend/src/views/PostDetailView.vue`，verify why.md#requirement-reading-experience
- [√] 3.3 统一 Home 与 Search 的 PageHeader/卡片/空态：`frontend/src/views/HomeView.vue`、`frontend/src/views/SearchView.vue`，verify why.md#requirement-unified-states
- [√] 3.4 统一用户域页面（Profile + Followers）：`frontend/src/views/UserProfileView.vue`、`frontend/src/views/FollowersView.vue`，verify why.md#requirement-layout-consistency
- [√] 3.5 统一用户域页面（Followees + Settings）：`frontend/src/views/FolloweesView.vue`、`frontend/src/views/SettingsView.vue`，verify why.md#requirement-layout-consistency
- [√] 3.6 统一消息域页面（会话列表 + 会话详情）：`frontend/src/views/ConversationsView.vue`、`frontend/src/views/ConversationDetailView.vue`，verify why.md#requirement-responsive-consistency
- [√] 3.7 统一通知域页面（通知列表 + 通知详情）：`frontend/src/views/NoticesView.vue`、`frontend/src/views/NoticeDetailView.vue`，verify why.md#requirement-unified-states
- [√] 3.8 统一辅助页面（收藏 + 榜单）：`frontend/src/views/BookmarksView.vue`、`frontend/src/views/LeaderboardView.vue`，verify why.md#requirement-layout-consistency
- [√] 3.9 统一管理页面（Moderation + Analytics）：`frontend/src/views/ModerationView.vue`、`frontend/src/views/AnalyticsView.vue`，verify why.md#requirement-layout-consistency
- [√] 3.10 统一认证页面（Login + Register）：`frontend/src/views/LoginView.vue`、`frontend/src/views/RegisterView.vue`，verify why.md#requirement-layout-consistency
- [√] 3.11 统一认证页面（PasswordReset + Activation）：`frontend/src/views/PasswordResetView.vue`、`frontend/src/views/ActivationView.vue`，verify why.md#requirement-unified-states
- [√] 3.12 统一异常页面（Forbidden + NotFound）：`frontend/src/views/ForbiddenView.vue`、`frontend/src/views/NotFoundView.vue`，verify why.md#requirement-unified-states

## 4. Security Check
- [√] 4.1 执行前端安全检查（重点：Markdown v-html 白名单与链接协议、敏感信息持久化）：verify how.md#security-and-performance

## 5. Documentation Update（Knowledge Base）
- [√] 5.1 同步前端 UI/样式规范到知识库：`.helloagents/modules/frontend.md`

## 6. Testing
- [√] 6.1 运行前端测试与构建：`npm -C frontend test`、`npm -C frontend run build`
