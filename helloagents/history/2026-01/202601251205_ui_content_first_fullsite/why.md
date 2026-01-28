# Change Proposal: 前端 UI 内容优先全站一致性重构

## Requirement Background

当前项目前端已具备一套基础 Design Tokens（CSS Variables）、布局骨架（AppShell 三栏）、以及最小可用 UI 组件库（UiButton/UiInput/UiCard/UiPageHeader 等）。但在逐页迭代过程中，仍存在“页面结构不一致、文本可读性不足、内容排版（尤其 Markdown）不够稳定、移动端体验分裂”的问题，导致：
- 信息层级不稳定：不同页面的标题/动作区/工具栏密度不同，用户需要重新学习页面结构；
- 内容阅读成本偏高：正文、评论、代码块的排版与段落语义不够清晰；
- 空/加载/错误态不统一：相同状态在不同页面呈现方式不同；
- 全站风格碎片化：局部存在大量 inline style 与局部 scoped style，难以形成一致的视觉语言。

本次改动采用“内容优先”的论坛 UI 方向，在不引入大型 UI 框架的前提下，基于现有 tokens 与组件库完成全站一致性重构与打磨，确保桌面与移动端都具备稳定、可读、可扩展的 UI 基线。

## Product Analysis

### Target Users and Scenarios
- **User Groups:**
  - 普通用户：浏览/搜索帖子、阅读详情、发表评论与回复；
  - 深度用户：高频阅读与互动、订阅内容、收藏、私信沟通；
  - 版主/管理员：内容管理、统计与治理。
- **Usage Scenarios:**
  - 列表页快速扫读：按分类/标签/排序筛选，快速判断是否点入；
  - 详情页深度阅读：顺畅阅读正文与评论树，定位/引用回复；
  - 运营治理：在管理页快速识别状态、执行操作并获得明确反馈。
- **Core Pain Points:**
  - 视觉与结构不一致导致“认知切换成本”；
  - 文本对比度与排版不佳导致“阅读疲劳”；
  - 移动端信息密度与交互可达性不稳定。

### Value Proposition and Success Metrics
- **Value Proposition:** 提供“内容优先、结构一致、信息层级清晰”的论坛体验，使用户更专注于阅读与讨论本身。
- **Success Metrics:**
  - 全站页面统一遵循 PageHeader + Card + Page 容器结构（主观验收 + 代码审查）；
  - 375px/768px/1024px 三档下无横向滚动条，关键操作可达；
  - Markdown 正文与评论渲染具备段落语义、列表与代码块可读；
  - 空/加载/错误态在主要页面一致（统一组件与样式规范）。

### Humanistic Care
- 可访问性：保留并强化 focus-visible、键盘可达性与可读对比度；
- 隐私与安全：Markdown 渲染持续采用“先转义再放行”的白名单策略，避免 XSS；
- 低带宽与性能：不引入重量级 UI 依赖，减少 UI 资源体积与渲染开销。

## Change Content
1. 明确“内容优先”的全站视觉规范：文本层级、卡片密度、间距、边框/阴影使用规则。
2. 优化 Design Tokens：可读性相关的文本颜色、标题层级与阅读宽度策略。
3. 强化 Markdown 阅读体验：段落语义、列表结构、代码块与引用块排版优化（保持安全策略）。
4. 全站页面一致性重构：补齐未使用 UiPageHeader 的页面，并收敛重复/零散的样式钩子。
5. 统一空/加载/错误态：优先使用 UiEmpty/UiToast 等统一入口。

## Impact Scope
- **Modules:** frontend
- **Files:** `frontend/src/styles/*`、`frontend/src/components/ui/*`、`frontend/src/components/layout/*`、`frontend/src/views/*`
- **APIs:** 无新增/变更（UI 表现层优化）
- **Data:** 无数据结构变更

## Core Scenarios

### Requirement: Layout Consistency
**Module:** frontend
在全站范围内统一页面结构与信息层级（标题/副标题/操作区/内容区）。

#### Scenario: Unified PageHeader
当用户打开任意页面（列表、详情、设置、私信、搜索等）时：
- 页面顶部使用一致的标题区（UiPageHeader）呈现“标题 + 可选副标题 + 操作按钮”；
- 主要内容区使用一致的 Card 与 Page 容器，间距与密度可预测；
- 重要操作按钮具有一致的视觉权重与状态（hover/disabled/focus）。

### Requirement: Reading Experience
**Module:** frontend
保证帖子正文与评论内容的阅读体验（段落、引用、列表、代码块）清晰、可扫读、可聚焦。

#### Scenario: Markdown Readability
当用户阅读帖子正文与评论时：
- 正文渲染具备段落语义（p/ul/blockquote/pre），而不是纯 `<br>` 堆叠；
- 代码块可水平滚动、视觉分隔明确；行内 code 与链接可识别且不刺眼；
- 评论区采用紧凑渲染（compact），正文采用阅读渲染（default），保持层级与密度差异。

### Requirement: Responsive Consistency
**Module:** frontend
在移动端与中等屏幕尺寸下保持一致的结构与可用性。

#### Scenario: Mobile Usability
在 375px 宽度下：
- 主要交互（返回、发表、筛选、刷新）不被遮挡，按钮可点；
- 列表/详情页无横向滚动条（除代码块/表格等必要场景）；
- 空/加载/错误态视觉不跳变，用户明确下一步行动。

### Requirement: Unified States
**Module:** frontend
统一空状态、加载状态、错误状态的视觉与交互表达。

#### Scenario: Empty/Loading/Error Patterns
当数据为空/加载中/请求失败时：
- 使用统一的 UiEmpty 与 muted/error 文案风格；
- 在可恢复场景提供明确的 CTA（刷新、重试、返回）。

## Risk Assessment
- **Risk:** 全局 tokens/样式调整可能影响大量页面的视觉与布局。
- **Mitigation:** 以“向后兼容”为原则保留既有 class 语义；分阶段修改并对关键页面（Posts/PostDetail/Auth/Search/Messages）进行回归验证；不引入大型 UI 依赖，降低不可控风险。

