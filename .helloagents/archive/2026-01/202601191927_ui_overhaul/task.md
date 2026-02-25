# Task List: UI 系统化优化（视觉一致性 / 导航信息架构 / 交互易用性）

Directory: `.helloagents/archive/2026-01/202601191927_ui_overhaul/`

---

## 1. 样式体系与 Design Tokens（frontend）
- [√] 1.1 统一与补齐 design tokens：调整 `frontend/src/styles/variables.css`，并同步基础样式约定到 `frontend/src/styles/base.css`，verify why.md#requirement-ui-001-visual-consistency, why.md#scenario-ui-001-1-consistent-controls-across-pages
- [√] 1.2 收敛组件级样式与工具类：整理 `frontend/src/styles/components.css` 与 `frontend/src/styles/utils.css` 的职责边界，补齐常用 primitives，verify why.md#requirement-ui-001-visual-consistency, why.md#scenario-ui-001-2-consistent-loadingemptyerror-states, depends on task 1.1
- [√] 1.3 （可选但推荐）新增页面级通用结构样式：新增 `frontend/src/styles/pages.css` 并在 `frontend/src/styles/index.css` 引入，统一 page header / toolbar / section 等结构，verify why.md#requirement-ui-001-visual-consistency, why.md#scenario-ui-003-1-posts-list-browsing--actions, depends on task 1.2

## 2. 导航信息架构与布局一致性（frontend）
- [√] 2.1 侧边栏信息架构收敛：重构 `frontend/src/components/layout/SidebarNav.vue`，减少内联样式、统一文案与分组、明确选中态与可点击热区，verify why.md#requirement-ui-002-information-architecture--navigation, why.md#scenario-ui-002-1-cross-page-navigation-clarity
- [√] 2.2 顶栏信息与搜索体验统一：重构 `frontend/src/components/layout/Topbar.vue`，以路由 meta 驱动标题/副标题（避免硬编码），优化搜索框交互与可访问性，verify why.md#requirement-ui-002-information-architecture--navigation, why.md#scenario-ui-002-1-cross-page-navigation-clarity, depends on task 2.3
- [√] 2.3 路由 meta 作为 SSOT：补齐 `frontend/src/router/index.js` 的 `meta.title/subtitle/navGroup` 等字段（保留 requiresAuth/roles 现有约定），verify why.md#requirement-ui-002-information-architecture--navigation, why.md#scenario-ui-002-1-cross-page-navigation-clarity
- [√] 2.4 移动端侧边栏行为收敛：修正 `frontend/src/styles/layout.css` 与侧边栏抽屉/遮罩行为（打开/关闭/点击导航项后的收敛策略），verify why.md#requirement-ui-002-information-architecture--navigation, why.md#scenario-ui-002-2-mobile-navigation-behavior, depends on task 2.1

## 3. 全局反馈机制与基础组件一致性（frontend）
- [√] 3.1 收敛 Toast 机制为单一入口：修正 `frontend/src/App.vue` 的 Toast 接入方式，并统一 `frontend/src/components/ui/UiToast.vue` 与 `frontend/src/stores/app.js` 的职责（避免重复提示与多形态并存），verify why.md#requirement-ui-001-visual-consistency, why.md#scenario-ui-001-2-consistent-loadingemptyerror-states
- [√] 3.2 统一 Confirm/Empty/Pagination 等基础组件交互：整理 `frontend/src/components/ui/UiModalConfirm.vue`、`frontend/src/components/ui/UiEmpty.vue` 并在 `frontend/src/styles/components.css` 对齐样式与状态，verify why.md#requirement-ui-001-visual-consistency, why.md#scenario-ui-001-2-consistent-loadingemptyerror-states

## 4. 认证流程体验统一（frontend）
- [√] 4.1 登录页：优化 `frontend/src/views/LoginView.vue` 的表单结构、错误提示与验证码区域一致性，尽量移除深层选择器依赖，verify why.md#requirement-ui-004-auth-flow-ux, why.md#scenario-ui-004-1-auth-form-consistency
- [√] 4.2 注册页：优化 `frontend/src/views/RegisterView.vue` 的表单结构与 CTA/错误提示一致性，verify why.md#requirement-ui-004-auth-flow-ux, why.md#scenario-ui-004-1-auth-form-consistency, depends on task 4.1
- [√] 4.3 找回/重置密码：优化 `frontend/src/views/PasswordResetView.vue` 的流程提示与表单一致性，verify why.md#requirement-ui-004-auth-flow-ux, why.md#scenario-ui-004-1-auth-form-consistency, depends on task 4.1
- [√] 4.4 激活页：优化 `frontend/src/views/ActivationView.vue` 的状态呈现与回跳引导一致性，verify why.md#requirement-ui-004-auth-flow-ux, why.md#scenario-ui-004-1-auth-form-consistency, depends on task 4.1

## 5. 核心业务页面逐页打磨（frontend）
- [√] 5.1 posts 列表页：重构 `frontend/src/views/PostsView.vue` 的信息层级、可点击热区、排序与刷新区域、空/错/加载态一致性，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-003-1-posts-list-browsing--actions
- [√] 5.2 帖子详情页：重构 `frontend/src/views/PostDetailView.vue` 的阅读排版（reading 宽度）与操作区一致性，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-003-2-reading-experience-in-post-detail
- [√] 5.3 用户主页：重构 `frontend/src/views/UserProfileView.vue` 的信息层级与关注/取关等操作反馈一致性，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-002-1-cross-page-navigation-clarity
- [√] 5.4 私信列表：优化 `frontend/src/views/ConversationsView.vue` 的列表密度、空态与加载态，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-002-1-cross-page-navigation-clarity
- [√] 5.5 私信详情：优化 `frontend/src/views/ConversationDetailView.vue` 的消息气泡/输入区一致性与滚动体验，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-002-1-cross-page-navigation-clarity, depends on task 5.4
- [√] 5.6 搜索页：优化 `frontend/src/views/SearchView.vue` 的搜索表单、结果列表与高亮展示一致性，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-002-1-cross-page-navigation-clarity
- [√] 5.7 设置页：优化 `frontend/src/views/SettingsView.vue` 的表单布局与上传/保存反馈一致性，verify why.md#requirement-ui-003-core-page-ux-polish, why.md#scenario-ui-002-1-cross-page-navigation-clarity

## 6. 移动端与可访问性最低标准（frontend）
- [√] 6.1 统一 focus-visible：在 `frontend/src/styles/base.css` 补齐 `:focus-visible` 约定（避免只对 input 生效），并在关键控件上验证可见性，verify why.md#requirement-ui-001-visual-consistency, why.md#scenario-ui-001-1-consistent-controls-across-pages
- [√] 6.2 Icon button 可访问性：为 `frontend/src/components/layout/Topbar.vue` 与 `frontend/src/components/layout/SidebarNav.vue` 的 icon buttons 补齐 `aria-label/title` 语义，并验证键盘可用，verify why.md#requirement-ui-002-information-architecture--navigation, why.md#scenario-ui-002-2-mobile-navigation-behavior

## 7. 部署与静态资源/样式策略（deploy）
- [√] 7.1 梳理样式与静态资源策略说明：更新 `deploy/README.md`（与前端样式组织、构建产物与排查建议对齐），verify why.md#requirement-ui-005-static-assets--style-strategy, why.md#scenario-ui-005-1-stable-styling-entry-and-build-output
- [-] 7.2 校验前端镜像策略：检查 `deploy/Dockerfile.frontend` 是否需要小幅调整（默认保持现状，必要时补充说明），verify why.md#requirement-ui-005-static-assets--style-strategy, why.md#scenario-ui-005-1-stable-styling-entry-and-build-output
  > Note: 当前镜像构建/运行策略满足本次 UI 优化需求，无需调整 Dockerfile；已在 `deploy/README.md` 补充静态资源与样式策略说明。

## 8. Security Check
- [√] 8.1 执行安全检查（per G9：XSS 风险点、敏感信息处理、权限控制、EHRB 风险规避；重点关注富文本渲染与外链跳转）

## 9. Documentation Update（Knowledge Base）
- [√] 9.1 同步更新前端模块文档：更新 `.helloagents/modules/frontend.md`，补齐新的样式分层约定、route meta 规范与组件复用建议
- [√] 9.2 记录变更：在实施完成后更新 `.helloagents/CHANGELOG.md`

## 10. Testing
- [-] 10.1 补齐/更新前端单测：新增或更新 `frontend/src/components/ui/*.test.js` 与 `frontend/src/router/*.test.js`，覆盖路由 meta 标题映射、UI store、关键组件交互
  > Note: 当前前端仅配置 Vitest（未引入 Vue Test Utils），为避免本次 UI 优化额外引入测试依赖，暂不新增组件渲染类单测；已通过既有单测与 build 门禁验证。
- [√] 10.2 执行质量验证：运行 `npm -C frontend test` 与 `npm -C frontend run build`，确保构建与单测均通过
