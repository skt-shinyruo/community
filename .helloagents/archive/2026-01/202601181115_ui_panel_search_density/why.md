# Change Proposal: frontend UI 细节增强（右侧上下文面板 / 全局搜索联动 / 密度精细化）

## Requirement Background

前一轮已完成 `frontend` 的 Notion 风格工作区骨架（AppShell 三栏、Design Tokens、全站 views 统一）。在实际使用中仍存在三类“体验细节”缺口：

1. **缺少右侧上下文面板**：桌面优先场景下，用户需要一个“随时可见的轻量提示区”承载快捷键提示、当前页面上下文、隐私提醒等信息，减少认知负担与操作停顿。
2. **全局搜索联动不够顺滑**：Topbar 与 SearchView 之间需要更一致的状态同步（例如从 URL query 恢复、路由切换后的输入框回填），否则容易出现“搜索页已切换但顶栏关键词未同步”的割裂感。
3. **密度切换粒度不足**：现有 `compact/comfortable` 只影响控件高度与少量间距，仍难以覆盖“字体层级、页面内边距、卡片 padding”等影响信息密度与阅读舒适度的关键变量。

本变更聚焦于“细节补齐”，在不改变现有路由与 API 行为的前提下，完善桌面工作区体验。

## Product Analysis

### Target Users and Scenarios
- **User Groups：** 技术用户（高频浏览、习惯快捷键、重度阅读、注重信息密度与秩序感）。
- **Usage Scenarios：**
  - 桌面端长时间停留：浏览帖子/搜索/私信/通知/统计。
  - 快捷切换与多任务：边读边搜，跨页面频繁跳转。
  - 夜间/长时间阅读：需要更好的密度与字体层级控制。
- **Core Pain Points：**
  - 缺少“右侧提示与上下文”，导致用户需要在页面内寻找说明或重复记忆操作规则。
  - 全局搜索状态不一致时，会降低对“工作区”的信任感。
  - 密度控制不精细导致“紧凑不够紧凑、舒适不够舒适”。

### Value Proposition and Success Metrics
- **Value Proposition：** 让工作区更像 Notion：信息有序、状态一致、细节克制但完整。
- **Success Metrics（可验证）：**
  - 右侧面板可一键显示/隐藏（桌面端默认开启，移动端自动隐藏），内容随路由变化更新。
  - Topbar 搜索框能在路由切换后同步当前搜索 query（SearchView 与 Topbar 对齐）。
  - 密度切换覆盖到字体大小、页面 padding、卡片 padding、控件高度等关键 tokens，整体观感有明显差异且不破版。

### Humanistic Care
- **隐私提示：** 在私信等页面展示轻量提示，提醒用户谨慎分享敏感内容截图或链接。
- **可访问性：** 保持可见 focus ring；交互控件在密度变化下仍可点击与可读。

## Change Content

1. 新增右侧上下文面板组件 `RightPanel`，并在 AppShell 中按开关决定是否渲染第三列。
2. 增加 UI 偏好项 `rightPanelOpen`（可持久化），Topbar 增加一键开关入口。
3. 完善 Topbar ↔ SearchView 联动：路由 query 改变时，Topbar 搜索输入框自动回填；SearchView 保持 URL query 为搜索 SSOT。
4. 精细化密度 tokens：新增 `--content-padding-x/y`、`--card-padding`，并在 compact 模式下覆盖字体层级与关键 padding。

## Impact Scope

- **Modules：** `frontend`
- **Files（范围级）：**
  - UI 状态：`frontend/src/stores/ui.js`
  - 布局：`frontend/src/components/layout/AppShell.vue`、新增 `frontend/src/components/layout/RightPanel.vue`
  - 顶栏：`frontend/src/components/layout/Topbar.vue`
  - 入口：`frontend/src/App.vue`
  - 样式：`frontend/src/styles.css`
- **APIs：** 无变更
- **Data：** 无变更

## Core Scenarios

### Requirement: right-panel
**Module:** frontend
右侧上下文面板可开关，并随路由变化更新提示信息。

#### Scenario: toggle-right-panel
用户可在 Topbar 一键显示/隐藏右侧面板。
- 期望：切换不影响主内容宽度布局（网格列切换），刷新后保持偏好。

#### Scenario: mobile-hide
移动端宽度下右侧面板自动隐藏，不占布局宽度。
- 期望：移动端仅保留侧栏折叠与主内容，不出现空白第三列。

### Requirement: global-search-sync
**Module:** frontend
Topbar 与 SearchView 的关键词状态保持一致。

#### Scenario: query-backfill
从 URL query 进入/切换到搜索页时，Topbar 搜索框自动回填当前 query。
- 期望：Topbar 搜索框显示与 SearchView 当前查询一致。

### Requirement: density-fine-tune
**Module:** frontend
密度切换影响关键 tokens（字体/页面 padding/卡片 padding/控件高度）。

#### Scenario: compact-vs-comfortable
切换 compact 时，整体更紧凑但不破版；切回 comfortable 时恢复舒适阅读。
- 期望：列表/详情/表单页一致生效，视觉差异可感知。

## Risk Assessment

- **Risk：** 右侧面板引入后可能导致小屏幕布局异常或第三列空白。
  - **Mitigation：** 仅在桌面宽度开启，并在 CSS media query 下强制隐藏右侧列与面板。
- **Risk：** 密度变量覆盖过多导致局部排版回归。
  - **Mitigation：** 仅调整关键 tokens（字号、card/content padding），并通过 `vite build` 与页面冒烟检查验证。
