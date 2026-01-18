# Change Proposal: frontend UI Notion 化重构（桌面优先）

## Requirement Background

当前 `frontend` 的 UI 以“功能可用”为主，存在以下问题：

1. **视觉一致性不足**：页面之间缺少统一的间距、字体层级、交互状态（hover/focus/disabled）与组件风格，整体观感偏“拼装”。
2. **信息密度与阅读效率不足**：面向技术用户的核心场景是“快速扫读 + 深度阅读”，现有布局（单列为主、局部内联样式）难以兼顾高密度与舒适阅读。
3. **桌面优先体验缺失**：缺少 Notion 式的“工作区”骨架（可折叠侧边栏、顶部全局操作、右侧上下文面板），导致路径长、上下文切换成本高。
4. **主题与密度不可控**：缺少暗色模式与密度切换，技术用户夜间/长时间使用体验不足。

本变更拟在**不改变现有路由与 API 行为**的前提下，对 Vue3 SPA 的视觉与交互进行全量重构，使其具备 Notion 风格的“克制、干净、排版优先”，同时保留 BBS 的“高信息密度与效率”。

## Product Analysis

### Target Users and Scenarios
- **User Groups：** 以技术用户为主（高频浏览、重度阅读、习惯快捷键、对可读性与一致性敏感）。
- **Usage Scenarios：**
  - 桌面端浏览帖子列表、筛选/搜索、快速打开详情。
  - 深度阅读帖子正文与评论/回复树，长时间停留。
  - 处理消息/通知、查看用户主页与关注关系、进入设置。
- **Core Pain Points：**
  - 视觉层级不稳定导致扫读成本高。
  - 缺少“工作区骨架”，页面之间切换时上下文丢失。
  - 缺少暗色与密度控制，无法兼顾不同偏好与使用时段。

### Value Proposition and Success Metrics
- **Value Proposition：** 用统一的设计系统与布局骨架，提供“高信息密度 + 视觉氛围”的技术社区体验；让用户在不牺牲效率的情况下获得更舒适的阅读与更少的认知噪音。
- **Success Metrics（可验证）：**
  - 视觉一致性：所有页面使用同一套 tokens（颜色/间距/字体/圆角/阴影），交互状态一致。
  - 可用性：关键路径（看帖、发帖、评论、搜索、消息/通知处理）交互清晰且不增加点击次数。
  - 可读性：列表信息对齐、正文最大阅读宽度可控、代码块/长文本不破版。
  - 可切换：明暗主题与密度切换可用且状态持久化（刷新/重开浏览器保持）。

### Humanistic Care
- **可访问性：** 保留可见的 focus ring、键盘可达（Tab 顺序合理）、对比度满足基本阅读要求。
- **隐私与安全：** 不在前端持久化敏感信息（仅存储主题/密度/侧栏展开等 UI 偏好）。

## Change Content

1. 建立 Notion 风格的 **Design Tokens**（CSS 变量）与基础排版规范（字体层级、间距、圆角、边框、阴影、颜色语义）。
2. 引入 **桌面三栏骨架（AppShell）**：左侧导航（可折叠）+ 顶部全局操作（搜索/新建/用户）+ 右侧上下文面板（可选）。
3. 补齐/统一内部组件库（按钮、输入、卡片、徽章、标签、头像、空状态、分页、弹窗等），统一 hover/focus/disabled 状态。
4. 对所有路由页面进行 UI 重构（`frontend/src/views/*.vue`），确保一致的布局、密度与视觉语言。
5. 提供 **主题切换（light/dark）** 与 **密度切换（compact/comfortable）**，并持久化到 localStorage。

## Impact Scope

- **Modules：**
  - `frontend`（主要变更）
- **Files（范围级）：**
  - 设计系统：`frontend/src/styles.css`（重构）、新增 `frontend/src/styles/tokens.css`（可选）
  - 布局骨架：`frontend/src/App.vue`、新增 `frontend/src/components/layout/*`
  - 组件库：`frontend/src/components/ui/*`（扩展/统一）
  - 页面：`frontend/src/views/*.vue`（全量改造）
  - 状态与工具：`frontend/src/stores/*`、`frontend/src/utils/*`
- **APIs：** 不改动既有后端 API（仅调整展示与交互层）
- **Data：** 无数据模型变更

## Core Scenarios

### Requirement: design-system
**Module:** frontend
建立统一 tokens 与基础交互状态，保证页面一致性。

#### Scenario: theme-toggle
用户可在任意页面切换 light/dark，刷新后仍保持。
- 期望：主题切换不闪烁，文字/背景/边框/代码块对比度可读。

#### Scenario: density-toggle
用户可切换 compact/comfortable 两档密度。
- 期望：列表行高、卡片间距、字体大小按档位统一变化，不出现局部破版。

### Requirement: app-shell-layout
**Module:** frontend
实现桌面优先三栏骨架与统一导航。

#### Scenario: workspace-navigation
左侧栏承载主要路由入口，顶部栏提供全局搜索/新建/用户入口。
- 期望：导航在所有页面一致可用，移动端可退化为抽屉/折叠。

### Requirement: posts-and-reading
**Module:** frontend
提升帖子列表与详情的扫读与深读体验。

#### Scenario: posts-list-density
帖子列表支持高信息密度展示：标题、标签/状态、作者、时间、回复/点赞等元信息对齐可扫读。
- 期望：在不显著增加纵向高度的前提下信息齐全；hover/选中态清晰。

#### Scenario: post-detail-document
帖子详情页以“文档化排版”展示正文与评论树（清晰层级、舒适行距、长文本不溢出）。
- 期望：正文最大宽度合理、评论/回复层级可识别、交互按钮不干扰阅读。

### Requirement: auth-settings-consistency
**Module:** frontend
认证与设置页面保持同一视觉语言与表单体验。

#### Scenario: login-register-activation
登录/注册/激活页面在同一骨架与表单样式下呈现。
- 期望：表单校验与错误提示一致，按钮与输入交互一致。

#### Scenario: settings-page
设置页（头像/偏好等）结构清晰、操作反馈明确。
- 期望：保存/上传进度与结果提示清晰，不阻断用户路径。

### Requirement: system-pages
**Module:** frontend
统一 403/404 等系统页与空状态提示。

#### Scenario: empty-and-error-states
无数据、无权限、未找到等场景提供一致的空状态组件。
- 期望：信息清楚、提供返回/登录等下一步入口。

## Risk Assessment

- **Risk：** 全量 UI 改造导致局部页面样式回归、交互状态不一致、移动端适配退化。
  - **Mitigation：** 按“骨架 → 组件 → 页面”顺序推进；每改完一批页面就执行 `npm -C frontend test` 与 `npm -C frontend run build`；关键路由做冒烟清单。
- **Risk：** 可访问性与对比度不足（暗色模式常见问题）。
  - **Mitigation：** 使用 tokens 统一控制对比度；保留 focus ring；在暗色下重点检查正文、代码块与按钮。
