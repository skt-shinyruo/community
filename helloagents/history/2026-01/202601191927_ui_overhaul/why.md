# Change Proposal: UI 系统化优化（视觉一致性 / 导航信息架构 / 交互易用性）

## Requirement Background

当前前端已具备基础的设计系统能力（CSS Variables design tokens、基础 UI 组件、三栏工作区布局），但在页面与组件层面仍存在以下典型问题，导致体验不够统一、维护成本偏高：

1. **视觉一致性不足**：大量内联样式与页面 scoped 样式并存，控件尺寸、间距、字号、阴影、边框与 hover/focus 行为在不同页面出现分叉。
2. **交互反馈碎片化**：loading/empty/error/success 的呈现方式不统一；Toast 存在多入口/多形态接入，容易造成重复与视觉不一致。
3. **信息架构与导航可达性不稳定**：侧边栏/顶栏在不同尺寸与登录态下的展示与文案存在不一致；移动端侧边栏抽屉与遮罩逻辑也需要更严格的行为收敛。
4. **关键业务页体验差异明显**：posts 列表、帖子详情、用户主页、消息、搜索、设置等页面在布局密度、信息层级、交互热区（可点击区域）上缺乏统一规范。
5. **样式与静态资源策略需要可演进**：需要形成明确的“样式组织与复用约定”（tokens → primitives → components → pages），并在部署侧保证构建产物与静态资源策略清晰可复用。

本次改造目标是在**不引入完整 UI 组件库的大改架构**前提下，强化现有 design tokens + 内部组件库，使 UI/UX 一致性可持续演进，并逐页完成核心场景体验收敛。

## Product Analysis

### Target Users and Scenarios
- **User Groups:**
  - 游客：浏览帖子、搜索内容、查看用户主页。
  - 登录用户：发帖、点赞、评论、私信、查看通知、修改设置。
  - 管理员/版主：查看统计页、执行管理操作（置顶/加精/删除、重建索引等）。
- **Usage Scenarios:**
  - 高频浏览：posts 列表浏览 → 进入帖子详情 → 进入作者主页 → 返回列表继续浏览。
  - 任务型操作：搜索 → 进入结果 → 进一步筛选/跳转。
  - 沟通：进入消息会话 → 查看/发送消息 → 返回会话列表。
  - 账户闭环：登录/注册/激活/找回密码 → 回到业务页。
- **Core Pain Points:**
  - 跨页面视觉与交互不一致导致的学习成本与信任感下降。
  - 导航与页面信息层级不稳定导致“我在哪/我能去哪”的不确定性。
  - 移动端/小屏体验与触控可用性不足（热区小、抽屉与遮罩行为不统一）。

### Value Proposition and Success Metrics
- **Value Proposition:** 通过“统一规范 + 组件化复用 + 逐页落地”的方式，把社区前端从“能用”提升到“好用且一致”，同时降低后续迭代的 UI 维护成本。
- **Success Metrics（建议作为验收清单的基础）:**
  1. 核心页面（posts、post detail、profile、messages、search、settings、auth）在按钮/输入/卡片/提示/空态/加载态表现一致。
  2. 侧边栏与顶栏的文案、分组、选中态、快捷操作一致；移动端抽屉打开/关闭行为一致且可预测。
  3. 页面布局密度与阅读宽度一致（阅读类页面使用 reading 宽度；列表页使用 page 宽度）。
  4. 全局 Toast/错误提示入口收敛为“单一机制”，避免重复叠加。
  5. `npm -C frontend test` 与 `npm -C frontend run build` 均通过（作为最低质量门槛）。

### Humanistic Care
- 确保深色主题与高对比度场景可读性；不只依赖颜色表达状态（例如同时提供图标/文案/形状变化）。
- 对键盘用户提供清晰的 focus-visible 反馈；保证关键按钮具备可访问性语义（button/aria-label）。
- 控制动画与动效强度，减少眩晕风险（优先短、轻、可预测的过渡）。

## Change Content
1. **Design Tokens 统一与补齐**：统一间距/字号/圆角/阴影/颜色语义；补齐 focus ring、hover/active/disabled 等交互态变量。
2. **组件库与样式组织收敛**：将内联样式逐步迁移为可复用 class；补齐页面级 primitives（page header、toolbar、section、list item 等）。
3. **导航信息架构与文案统一**：侧边栏/顶栏统一路由分组、选中态与文案；引入 route meta 作为标题/面包屑/导航归属的 SSOT。
4. **核心页面逐页打磨**：posts 列表、帖子详情、用户主页、消息、搜索、设置与认证流程逐页落地统一规范。
5. **部署与静态资源策略梳理**：明确样式入口与拆分策略；完善部署文档中的静态资源说明与最佳实践。

## Impact Scope
- **Modules:**
  - `frontend`（主要改动：styles / components / views / router）
  - `deploy`（静态资源与部署策略文档/镜像策略小幅调整）
  - `helloagents/wiki`（同步更新前端模块规范与维护约定）
- **Files:**
  - `frontend/src/styles/*`
  - `frontend/src/components/layout/*`
  - `frontend/src/components/ui/*`
  - `frontend/src/views/*`
  - `frontend/src/router/index.js`
  - `deploy/*`（按需）
  - `helloagents/wiki/modules/frontend.md`
- **APIs:** 无（不调整后端接口契约）
- **Data:** 无（不调整数据模型）

## Core Scenarios

### Requirement: UI-001 Visual Consistency
**Module:** frontend
（视觉一致性：design tokens 与基础组件在全站落地，控件与卡片在所有页面表现一致。）

#### Scenario: UI-001-1 Consistent controls across pages
在任意页面使用按钮/输入框/分页/弹窗时：
- 控件高度、间距、字体、圆角一致；hover/active/focus/disabled 行为一致。
- 键盘导航可见 focus 状态（focus-visible），且不会被过度阴影/动画干扰。

#### Scenario: UI-001-2 Consistent loading/empty/error states
在核心页面加载/空数据/请求失败时：
- loading skeleton / empty / error 的视觉与交互入口一致。
- 错误信息可读，且提供可恢复动作（如重试/返回）。

### Requirement: UI-002 Information Architecture & Navigation
**Module:** frontend
（信息架构与导航：侧边栏/顶栏/面包屑与页面标题一致且可预测，移动端抽屉交互稳定。）

#### Scenario: UI-002-1 Cross-page navigation clarity
用户从 posts 列表进入帖子详情、再进入用户主页并返回：
- 页面标题、面包屑与选中态一致，用户始终清楚“我在哪里/我来自哪里/我能去哪”。
- 侧边栏分组与文案一致（不混用中文/英文/不同命名方式）。

#### Scenario: UI-002-2 Mobile navigation behavior
在移动端打开侧边栏抽屉：
- 遮罩出现且可点击关闭；点击导航项后抽屉可按约定自动关闭。
- 页面滚动与抽屉滚动不会相互干扰（优先保证主流程可用）。

### Requirement: UI-003 Core Page UX Polish
**Module:** frontend
（核心页面打磨：posts、post detail、profile、messages、search、settings 页面在信息层级、布局密度与交互热区上统一。）

#### Scenario: UI-003-1 Posts list browsing & actions
在 posts 列表浏览、刷新、加载更多与点赞：
- 列表项卡片信息层级清晰；可点击区域明确；操作反馈一致。
- 排序切换与刷新动作清晰可见，状态切换无突兀跳动。

#### Scenario: UI-003-2 Reading experience in post detail
在帖子详情阅读与评论/回复：
- 阅读宽度与排版对齐“阅读模式”；正文/评论区层级清晰。
- 管理操作（置顶/加精/删除）入口清晰且具备二次确认。

### Requirement: UI-004 Auth Flow UX
**Module:** frontend
（认证流程：登录/注册/激活/找回密码页面在表单规范、错误提示、验证码区域与跳转引导上统一。）

#### Scenario: UI-004-1 Auth form consistency
登录/注册/找回密码页面：
- 表单布局一致；错误提示位置一致；CTA（主按钮）统一。
- 验证码区域与刷新交互一致，且不影响主要输入流程。

### Requirement: UI-005 Static Assets & Style Strategy
**Module:** frontend, deploy
（静态资源与样式策略：样式组织明确可维护；部署与构建产物策略清晰可复用。）

#### Scenario: UI-005-1 Stable styling entry and build output
在本地与部署环境：
- 样式入口清晰（单一入口导入，拆分文件职责明确）。
- 构建产物可稳定被浏览器缓存（hash 资源），文档明确如何运行与排查。

## Risk Assessment
- **Risk:** UI 回归（视觉/交互/布局）、导航行为变化导致用户迷失、移动端适配遗漏、样式策略收敛过程中引入不可控碎片。
- **Mitigation:**
  - 采用“先规范/组件 → 再布局导航 → 再逐页落地”的分阶段任务拆分，并配套回归清单。
  - 每阶段执行 `npm -C frontend test` 与 `npm -C frontend run build`，把构建失败视为阻断问题优先修复。
  - 避免引入完整 UI 组件库；优先增强现有 tokens 与内部组件库，降低一次性迁移风险。
