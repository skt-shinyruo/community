# Community 业务层原生控件清理设计稿

**Date:** 2026-03-25
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

在 `frontend/` 中继续推进“业务层不直接写原生交互控件”的收敛，把剩余散落在页面、布局、模态和预览页中的原生 `button`、`input`、`textarea`、`checkbox`、`file input`、`datalist` 组合迁移到共享原语或明确的场景组件上。

本次设计的目标不是“禁止项目里出现任何原生 DOM 标签”，而是完成以下收敛：

- 让业务层、布局层和预览页不再绕开共享控件体系直接写原生交互控件
- 把高重复模式收敛到少量共享原语，而不是继续容忍页面内 ad hoc 封装
- 对复杂交互保持克制，只在值得复用的层级做抽象
- 为后续开发建立明确约束，避免新的业务层原生控件再次进入代码库

这是一次组件边界与交互契约清理，不是视觉重做项目。

---

## 2. Relationship To The 2026-03-24 UiSelect Spec

本稿是 [2026-03-24-community-select-unification-design.md](/home/feng/code/project/community/docs/superpowers/specs/2026-03-24-community-select-unification-design.md) 的后续扩展，不是替代品。

两者关系如下：

- 2026-03-24 稿解决的是业务层原生 `select` 清零
- 本稿解决的是剩余业务层原生交互控件的继续收口
- `UiSelect` 的能力边界、定位策略与键盘契约仍由 2026-03-24 稿定义
- 若本稿与 2026-03-24 稿在 `UiSelect` 基础行为上冲突，以 2026-03-24 稿为准
- 本稿不重新定义 `UiSelect`，只定义 `UiSelect` 之外的剩余收敛范围

---

## 3. Confirmed Decisions

在本次 brainstorming 中已经确认的决策如下：

- 本轮目标是清理 **业务页 / 布局组件 / 模态 / 预览页** 中直接写的原生交互控件
- `frontend/src/components/ui/**` 内部的原生 DOM 实现 **不在本轮清理范围内**
- 预览/演示页也纳入本轮，例如 `EditorialPreviewView.vue` 与 `HomeView.vue`
- 推荐方案采用“分层收敛”，不是把所有剩余原生控件一口气硬抽成全局原语
- 共享原语优先收敛：`UiCheckbox`、`UiIconButton`
- 共享复合输入优先收敛：`UiAutosuggestInput`、`UiFileInput`
- 明显带业务语义的复杂输入应保留为场景组件，而不是硬塞回基础原语
- 推荐场景组件包括：`TopbarSearchBox`、`ConversationComposer`

明确不做的事情：

- 不尝试消灭 `components/ui/**` 内部的原生 DOM
- 不把文件上传逻辑、搜索路由逻辑或 IM 发送逻辑硬塞进通用输入原语
- 不为了“零原生标签”去发明过度抽象的万能组件

---

## 4. Current State And Remaining Native-Control Patterns

### 4.1 Repeated Primitive Candidates

以下模式跨页面重复出现，适合收敛为共享原语：

#### Checkbox

当前典型位置：

- `frontend/src/components/posts/FeedToolbar.vue`
- `frontend/src/views/GrowthAdminView.vue`

特点：

- 语义稳定
- 状态模型简单
- 视觉差异低
- 适合用一套 `UiCheckbox` 统一

#### Icon / Utility Buttons

当前典型位置：

- `frontend/src/components/layout/Topbar.vue`
- `frontend/src/components/layout/SidebarNav.vue`
- `frontend/src/components/layout/AuthShell.vue`
- `frontend/src/views/PostsView.vue`
- `frontend/src/views/PostDetailView.vue`
- `frontend/src/components/modals/ReportModal.vue`
- `frontend/src/components/modals/EditContentModal.vue`
- `frontend/src/views/ConversationDetailView.vue`
- `frontend/src/views/EditorialPreviewView.vue`
- `frontend/src/views/HomeView.vue`

特点：

- 大量为关闭、更多、投票、发送、删除标签、主题/密度切换等 utility 按钮
- 命中区域、焦点态、禁用态与辅助说明高度重复
- 适合收敛为 `UiIconButton`

### 4.2 Repeated Composite Input Candidates

#### Autosuggest / Datalist Inputs

当前典型位置：

- `frontend/src/views/SearchView.vue`
- `frontend/src/views/PostsView.vue`
- `frontend/src/components/posts/FeedToolbar.vue`

特点：

- 当前都采用 `UiInput + datalist` 组合
- 行为相近：建议列表、回车确认、blur 提交
- 不值得继续分散在页面里手写
- 适合收敛为共享复合组件 `UiAutosuggestInput`

#### File Input

当前典型位置：

- `frontend/src/views/SettingsView.vue`

特点：

- 使用频次低，但属于明确的能力型输入
- 需要稳定地承载 `accept`、文件名显示、清空选择等行为
- 上传逻辑本身仍应保留在页面中
- 适合收敛为共享复合组件 `UiFileInput`

### 4.3 Contextual Composite Candidates

以下模式虽使用原生交互控件，但更适合抽成场景组件，而不是压回基础原语：

#### Topbar Search

位置：

- `frontend/src/components/layout/Topbar.vue`

原因：

- 它不只是搜索输入框
- 它带桌面显隐、快捷键聚焦、路由跳转和 overflow 结构
- 应抽为布局场景组件 `TopbarSearchBox`

#### Conversation Composer

位置：

- `frontend/src/views/ConversationDetailView.vue`

原因：

- 它不只是 `textarea + button`
- 它带 Enter 发送、禁用态、IM 发送语义与消息区布局
- 应抽为场景组件 `ConversationComposer`

---

## 5. Scope

### 5.1 In Scope

本轮强约束范围为：

- `frontend/src/views/**`
- `frontend/src/components/layout/**`
- `frontend/src/components/modals/**`
- `frontend/src/components/posts/**`

本轮明确纳入的预览/演示页包括：

- `frontend/src/views/EditorialPreviewView.vue`
- `frontend/src/views/HomeView.vue`

### 5.2 Out Of Scope

以下内容不在本轮“必须消灭”的范围内：

- `frontend/src/components/ui/**` 内部实现
- 纯展示标签，如 `div`、`span`、`img`、`svg`
- 原生 `form` 语义本身
- 上传 API、搜索 API、IM 发送逻辑的业务重构

### 5.3 Native Controls To Eliminate From Business / Layout / Preview Layers

在本轮范围内，以下直接原生交互控件都应被清理掉：

- 原生 `button`
- 原生 `input`
- 原生 `textarea`
- 原生 `checkbox`
- 原生 `file input`
- 原生 `datalist` / `option` 建议列表路径
- 与上述交互直接耦合的业务层 `label`

实现结束后，这些目录中的交互应只通过共享原语或场景组件暴露，而不再直接写原生控件。

### 5.4 Control Mapping Rules

为避免 implementation plan 无法判断“某个原生控件应该迁到哪里”，本稿明确以下映射规则：

| 当前直接原生控件模式 | 目标组件 |
|---|---|
| 普通文本/强调按钮 | 现有 `UiButton` |
| icon-only / utility button | 新增 `UiIconButton` |
| 简单布尔勾选 | 新增 `UiCheckbox` |
| `UiInput + datalist` 标签建议输入 | 新增 `UiAutosuggestInput` |
| 文件选择 | 新增 `UiFileInput` |
| 顶栏搜索输入组合 | 新增 `TopbarSearchBox` |
| 私信页消息发送输入组合 | 新增 `ConversationComposer` |

补充约束：

- 本稿不会替换现有 `UiButton`
- 本稿只新建 `UiIconButton` 来收口 icon-only / utility button
- 业务层里所有“非 icon-only 的普通按钮”都应统一回 `UiButton`

---

## 6. Target Component Model

### 6.1 Shared Primitives

#### UiCheckbox

职责：

- 承载简单布尔开关
- 统一复选框视觉、焦点态、禁用态和说明文本结构

建议公开契约：

- `modelValue`
- `disabled`
- `name`
- `label`
- 默认 slot

第一版不做：

- tri-state
- checkbox group 系统
- 内建表单验证框架

#### UiIconButton

职责：

- 承载 icon-only 或 utility button
- 替换业务层分散的 `.btn-icon` / 手写 icon button

建议公开契约：

- `ariaLabel`
- `title`
- `disabled`
- `variant`
- `size`

设计约束：

- `ariaLabel` 不能为空
- 统一 hit area
- 统一焦点态
- 页面层不再直接拼 icon-button class 组合

### 6.2 Shared Composite Inputs

#### UiAutosuggestInput

职责：

- 替代当前 `UiInput + datalist`
- 提供建议列表、键盘高亮、回车确认、blur 提交

建议公开契约：

- `modelValue`
- `suggestions`
- `placeholder`
- `disabled`
- `name`
- `commitOnBlur`
- `commitOnEnter`
- `update:modelValue`
- `select`
- `commit`

第一版不做：

- 远程拉取内建
- 多选 token 系统内建
- 分组建议
- 自定义高亮渲染系统

第一版事件语义明确如下：

- 输入文本变化时，发出 `update:modelValue`
- 选择 suggestion 时，必须先发出 `update:modelValue(selectedValue)`
- 选择 suggestion 时，同时发出 `select(selectedValue)`
- 选择 suggestion **不自动等同于 commit**
- `commit(value)` 只在以下时机触发：
  - `commitOnEnter === true` 且用户按 Enter
  - `commitOnBlur === true` 且输入失焦

这样做的原因是当前 3 处标签建议输入都以“Enter / blur 提交最终值”为主语义，而 suggestion 选择只是辅助输入，不应在第一版强行改成“选中即提交”。

#### UiFileInput

职责：

- 提供文件选择、文件名展示与清空动作
- 不接管上传业务

建议公开契约：

- `accept`
- `disabled`
- `name`
- `buttonText`
- `clearable`
- `modelValue`
- `update:modelValue`

明确不做：

- 预签名上传
- 上传进度
- 后端存储策略

第一版值语义明确如下：

- `modelValue` 类型为 `File | null`
- 选择文件时，发出 `update:modelValue(file)`
- 清空时，发出 `update:modelValue(null)`
- 组件只负责选择与反馈，不拥有上传副作用

### 6.3 Feature / Layout Components

#### TopbarSearchBox

职责：

- 取代 `Topbar.vue` 中直接写的原生 search input
- 封装桌面搜索输入、快捷键聚焦与提交导航

边界：

- 保留搜索路由逻辑
- 不变成全局万能搜索组件

放置约束：

- 不应继续放在被 native-control guard 直接扫描的业务模板目录里内联实现
- 建议作为专门场景组件放在新的实现目录 `frontend/src/components/scene/**`

#### ConversationComposer

职责：

- 取代 `ConversationDetailView.vue` 中直接写的原生 textarea + send button
- 保留 IM 消息发送的场景语义

边界：

- 保留 Enter 发送约束
- 不抽象成全站通用富文本或评论框

放置约束：

- 建议与 `TopbarSearchBox` 一样，放在 `frontend/src/components/scene/**`
- 它是业务场景组件，不属于 `components/ui/**` 原语层

---

## 7. Interaction Contracts

### 7.1 Checkbox And Icon Button Contracts

`UiCheckbox` 与 `UiIconButton` 应满足：

- 可键盘访问
- 正确 disabled
- 明确辅助命名
- 视觉与当前控件系统一致

其中 `UiIconButton` 还应满足：

- 打开辅助菜单、关闭弹窗、删除标签、发送消息等操作都使用同一交互基线
- 页面层不再各写一套 title/aria-label/尺寸 class

### 7.2 Autosuggest Contract

`UiAutosuggestInput` 应满足：

- suggestion 列表显示
- 方向键选择
- Enter 提交或选择
- blur 提交
- 与当前标签输入语义兼容

它是当前 `datalist` 的能力替代品，而不是浏览器原生 `datalist` 的视觉换皮。

### 7.3 File Input Contract

`UiFileInput` 应满足：

- 明确选择按钮
- 已选文件名反馈
- 清空能力
- disabled 状态

它不负责上传，只负责文件选择和反馈。

### 7.4 Keyboard And Focus Behavior

对于本轮新增共享控件，键盘行为必须明确测试覆盖：

- `UiCheckbox` 的切换
- `UiIconButton` 的触发
- `UiAutosuggestInput` 的方向键、回车、blur
- `UiFileInput` 的选择与清空

---

## 8. Testing And Regression Strategy

### 8.1 Component Tests

以下组件必须补 focused component tests：

- `UiCheckbox`
- `UiIconButton`
- `UiAutosuggestInput`
- `UiFileInput`

这些组件会跨页面复用，不能只靠人工点测。

### 8.2 Focused Page-Level Regression Tests

不要求给每个页面都写重型挂载测试，但必须补高风险薄测试：

- `FeedToolbar` 的 `update:subscribed`
- Search / Posts / FeedToolbar 的标签建议输入提交语义
- Settings avatar 文件选择能正确取到 `File`
- Conversation composer 保留 Enter 发送与 disabled 约束

### 8.3 Native-Control Guard

本轮完成后，应新增或扩展一个针对业务层 / 布局层 / 预览层的 native-control guard，覆盖本轮明确要禁止的直接原生交互控件。

该 guard 必须：

- 聚焦真实交互控件使用，而不是误伤注释或普通字符串
- 只针对本轮范围目录
- 明确排除 `frontend/src/components/ui/**`
- 明确排除承载场景组件实现的新目录 `frontend/src/components/scene/**`

Guard 的目标不是追求静态分析理论完备，而是作为可持续的防回退约束。

---

## 9. Recommended Execution Order

本轮建议按以下顺序实施：

1. `UiCheckbox` + `UiIconButton`
   - 先收敛最高重复、最低风险的原生控件
2. `UiAutosuggestInput`
   - 再统一三处 `datalist` 输入路径
3. `UiFileInput`
   - 再解决设置页文件选择
4. `TopbarSearchBox` + `ConversationComposer`
   - 最后处理场景型组合输入
5. Native-control guard
   - 最后补约束，防止业务层再次出现直写原生控件

这样拆分的好处是：

- 共享基础先站稳
- 复杂组合交互放到后面
- 每一批都可以独立验证
- 不把所有交互风险捆成单个巨大 diff

---

## 10. Non-Goals

本次设计明确不包含：

- 重写 `components/ui/**` 内部原语实现
- 富文本消息编辑器
- 通用上传框架
- 搜索服务、上传服务或 IM 发送服务的业务改造
- 与控件清理无关的页面视觉重做

---

## 11. Design Summary

本稿定义了一条后续收敛路径：在保留 `components/ui/**` 作为原生 DOM 承载层的前提下，继续把业务页、布局组件、模态与预览页中直接写的原生交互控件清理掉。

推荐的最终分层为：

- 共享原语：`UiCheckbox`、`UiIconButton`
- 共享复合输入：`UiAutosuggestInput`、`UiFileInput`
- 场景组件：`TopbarSearchBox`、`ConversationComposer`

这条路线既能系统性减少业务层原生控件散落，又不会为了“绝对零原生标签”把复杂场景过度抽象成脆弱的共享万能组件。后续 implementation plan 应围绕这一分层与执行顺序展开。
