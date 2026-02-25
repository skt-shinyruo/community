# Change Proposal: 前端视觉精修与遗留样式清理（未用 CSS 清理）

## Requirement Background

在“内容优先 + 全站一致性”重构后，前端的基础视觉与交互已经收敛到 Design Tokens + 组件库 + 统一页面骨架（`UiCard + UiPageHeader + .page`）。但仍存在两类维护成本与观感问题：

1. **视觉精修空间**：部分页面/组件的间距、对齐、标题层级与细节（hover/focus、移动端 padding）仍有可优化点，影响“内容优先”的阅读舒适度与专业感。
2. **遗留样式与未用 CSS**：历史迭代中遗留的 CSS 文件/选择器、scoped 样式片段与零散 inline style，造成：
   - 维护成本增加（不确定哪些还在用，改动更谨慎、更慢）
   - 样式体积与复杂度上升（全局/局部样式重复）
   - 新人理解困难（存在多个“入口/风格”的错觉）

本次变更目标是“在不引入新 UI 框架、不改变路由与业务逻辑”的前提下，完成视觉细节打磨并清理未用 CSS，进一步降低维护成本与 UI 漂移风险。

## Change Content
1. 视觉精修：统一常见间距与对齐，提升页面信息层级与细节一致性（内容优先、克制、可读）。
2. 遗留样式清理：删除未被使用的 CSS 文件与显著未用选择器；收敛重复样式。
3. 约束与规范补齐：在知识库中明确 CSS 入口与清理约定，避免未来再次“多入口/重复样式”。

## Impact Scope
- **Modules:** frontend
- **Files:** `frontend/src/styles/*`、部分 `frontend/src/views/*`、少量 `frontend/src/components/*`
- **APIs:** 无
- **Data:** 无

## Core Scenarios

### Requirement: Visual Polish（视觉精修）
**Module:** frontend
在桌面与移动端下保持更稳定的阅读体验与一致的视觉细节。

#### Scenario: Consistent spacing and hierarchy
当用户浏览帖子、阅读详情、查看通知、进行登录/注册等动作时：
- 页面标题/副标题/操作区的层级统一，可快速扫读；
- 卡片内部 padding、列表间距与分隔线更一致；
- focus-visible、hover 的反馈克制且一致，移动端无“跳动/抖动”体验。

### Requirement: Remove Unused CSS（清理未用 CSS）
**Module:** frontend
在不破坏功能与布局的前提下清理未使用样式。

#### Scenario: Single CSS entry and low ambiguity
当开发者维护样式时：
- 全局样式入口唯一、清晰（`frontend/src/styles/index.css` 为 SSOT）；
- 遗留未用 CSS 被清理，减少“改了没效果/删了会坏”的不确定性；
- scoped 样式只保留与组件强绑定的必要部分，通用样式沉淀到 `styles/*` 分层。

## Risk Assessment
- **Risk:** 误删“动态 class/条件渲染”相关样式导致局部 UI 回归。
- **Mitigation:** 使用代码搜索与保守策略（只删除确认未引用的文件与选择器）；执行 `npm -C frontend test` 与 `npm -C frontend run build`；对关键页面进行目视回归（posts、postDetail、login/register、messages、notices）。

