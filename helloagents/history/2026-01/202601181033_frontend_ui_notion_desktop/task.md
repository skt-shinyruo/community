# Task List: frontend UI Notion 化重构（桌面优先）

Directory: `helloagents/history/2026-01/202601181033_frontend_ui_notion_desktop/`

---

## 1. frontend - 设计系统与全局骨架
- [√] 1.1 定义 Design Tokens（theme/density）并重构全局样式：`frontend/src/styles.css`，verify why.md#requirement-design-system-scenario-theme-toggle
- [√] 1.2 新增 UI 偏好 store（theme/density/sidebar）并持久化到 localStorage：`frontend/src/stores/ui.js`，verify why.md#requirement-design-system-scenario-density-toggle，depends on task 1.1
- [√] 1.3 实现 AppShell 三栏布局骨架并接入应用入口：`frontend/src/components/layout/AppShell.vue`、`frontend/src/App.vue`，verify why.md#requirement-app-shell-layout-scenario-workspace-navigation，depends on task 1.2
- [√] 1.4 实现 SidebarNav 与 Topbar（含主题/密度切换入口）：`frontend/src/components/layout/SidebarNav.vue`、`frontend/src/components/layout/Topbar.vue`，verify why.md#requirement-app-shell-layout-scenario-workspace-navigation，depends on task 1.3

## 2. frontend - 基础组件库一致化（Notion 风格）
- [√] 2.1 统一 Button 交互状态（hover/focus/disabled）与密度适配：`frontend/src/components/ui/UiButton.vue`，depends on task 1.1
- [√] 2.2 统一 Input/Textarea 的样式与校验态：`frontend/src/components/ui/UiInput.vue`、`frontend/src/components/ui/UiTextarea.vue`，depends on task 1.1
- [√] 2.3 统一 Card/Empty/Pagination 视觉与空状态文案结构：`frontend/src/components/ui/UiCard.vue`、`frontend/src/components/ui/UiEmpty.vue`、`frontend/src/components/ui/UiPagination.vue`，depends on task 1.1
- [√] 2.4 新增 Avatar/Badge/Tag/Divider 等缺失组件：`frontend/src/components/ui/UiAvatar.vue`、`frontend/src/components/ui/UiBadge.vue`、`frontend/src/components/ui/UiTag.vue`，depends on task 1.1

## 3. frontend - 页面改造（views 全量）
- [√] 3.1 首页（Home）改造并对齐 AppShell：`frontend/src/views/HomeView.vue`，depends on task 1.3
- [√] 3.2 帖子列表页高密度改造：`frontend/src/views/PostsView.vue`，verify why.md#requirement-posts-and-reading-scenario-posts-list-density，depends on task 2.1
- [√] 3.3 帖子详情页文档化改造（正文/评论区视觉一致）：`frontend/src/views/PostDetailView.vue`，verify why.md#requirement-posts-and-reading-scenario-post-detail-document，depends on task 2.1
- [√] 3.4 用户主页改造：`frontend/src/views/UserProfileView.vue`，depends on task 2.4
- [√] 3.5 关注列表页改造：`frontend/src/views/FolloweesView.vue`、`frontend/src/views/FollowersView.vue`，depends on task 2.4
- [√] 3.6 会话列表页改造：`frontend/src/views/ConversationsView.vue`，depends on task 2.3
- [√] 3.7 私信详情页改造：`frontend/src/views/ConversationDetailView.vue`，depends on task 2.3
- [√] 3.8 通知汇总页改造：`frontend/src/views/NoticesView.vue`，depends on task 2.3
- [√] 3.9 通知详情页改造：`frontend/src/views/NoticeDetailView.vue`，depends on task 2.3
- [√] 3.10 搜索页改造（输入、结果、关键字高亮视觉）：`frontend/src/views/SearchView.vue`，depends on task 2.2
- [√] 3.11 统计页改造（管理员视图排版一致）：`frontend/src/views/AnalyticsView.vue`，depends on task 2.3
- [√] 3.12 设置页改造：`frontend/src/views/SettingsView.vue`，verify why.md#requirement-auth-settings-consistency-scenario-settings-page，depends on task 2.2
- [√] 3.13 登录页改造：`frontend/src/views/LoginView.vue`，verify why.md#requirement-auth-settings-consistency-scenario-login-register-activation，depends on task 2.2
- [√] 3.14 注册页改造：`frontend/src/views/RegisterView.vue`，verify why.md#requirement-auth-settings-consistency-scenario-login-register-activation，depends on task 2.2
- [√] 3.15 激活页改造：`frontend/src/views/ActivationView.vue`，verify why.md#requirement-auth-settings-consistency-scenario-login-register-activation，depends on task 2.2
- [√] 3.16 403/404 系统页改造：`frontend/src/views/ForbiddenView.vue`、`frontend/src/views/NotFoundView.vue`，verify why.md#requirement-system-pages-scenario-empty-and-error-states，depends on task 2.3

## 4. Security Check
- [√] 4.1 执行前端安全检查（输入校验、错误提示、token 不落盘、XSS 风险不升级），记录检查点：`helloagents/history/2026-01/202601181033_frontend_ui_notion_desktop/how.md`

## 5. Documentation Update
- [√] 5.1 同步更新知识库（frontend 模块文档 + 设计系统说明）：`helloagents/wiki/modules/frontend.md`
- [√] 5.2 更新变更记录：`helloagents/CHANGELOG.md`

## 6. Testing
- [√] 6.1 执行前端单测与构建：`npm -C frontend test`、`npm -C frontend run build`
- [?] 6.2 冒烟验证关键路由（posts/detail/search/messages/notices/settings/auth），并记录结果到 `helloagents/history/<YYYY-MM>/.../task.md`
  > Note: 已通过 `vitest` 与 `vite build`；仍建议在浏览器中按路由清单做一次人工冒烟（含登录态与权限路由）。
