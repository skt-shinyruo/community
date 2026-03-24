# Community 无原生 Select 收敛设计稿

**Date:** 2026-03-24
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

在 `frontend/` 内彻底停止使用原生 `<select>`，把现有所有单选下拉场景收敛到一套共享 `UiSelect` 组件上。

本次设计的目标不是“给原生 `select` 换皮”，而是完成以下收敛：

- 消除浏览器原生下拉菜单带来的视觉割裂
- 让用户侧、弹窗、后台页都使用同一套选择控件契约
- 保持现有业务语义不变，不借机重写页面状态模型
- 为后续页面开发建立明确约束，避免新的原生 `select` 再次进入代码库

这是一次共享控件与交互契约收敛，不是新功能扩张项目。

---

## 2. Confirmed Decisions

在本次 brainstorming 中已经确认的决策如下：

- Scope 选择为全量替换，不只覆盖讨论页或用户侧页面
- 当前 `frontend/src` 中已有的 11 处原生 `select` 都在本次改造范围内
- 最终方向采用项目内自建 `UiSelect`，不继续保留原生 `select`，也不引入第三方 headless 选择器依赖
- 第一版 `UiSelect` 只覆盖当前代码真实需要的能力：单选、静态选项、禁用、占位显示、表单值同步、键盘导航与基础无障碍
- 第一版明确不做搜索、多选、异步远程选项、分组选项或可输入式组合框
- 改造完成后必须增加防回退约束，阻止新的原生 `select` 出现在 `frontend/src`

推荐方案明确为：

- 新建共享 `UiSelect`
- 原位替换全部现有原生 `select`
- 增加组件测试与静态守卫测试

---

## 3. Current State And Scope

### 3.1 Current Native Select Usage

当前 `frontend/src` 中的原生 `select` 分布如下：

- `frontend/src/views/PostsView.vue`
- `frontend/src/components/posts/FeedToolbar.vue`
- `frontend/src/views/SearchView.vue`
- `frontend/src/components/modals/ReportModal.vue`
- `frontend/src/views/ModerationView.vue`，共 3 处
- `frontend/src/views/RewardOpsView.vue`，共 2 处
- `frontend/src/views/GrowthAdminView.vue`
- `frontend/src/views/UserManagementView.vue`

这 11 处覆盖：

- 公共浏览页筛选
- 发帖/举报等前台输入场景
- 审核、奖励、成长、用户管理等后台操作场景

因此本次改造不能只按“公共区 UI 优化”理解，而要把管理页密度、弹窗遮挡和表单兼容一并考虑进去。

### 3.2 Shared Foundation Observations

从现有代码可见：

- 项目中还没有现成的 `UiSelect` / `Combobox` / `Dropdown` 组件可复用
- 现有共享控件体系以 `UiInput`、`UiTextarea`、`UiButton` 这类轻量 primitives 为主
- 项目没有成熟的通用 popover / floating layer 基础设施
- 现有原生 `select` 只吃到 `.input` 外壳样式，展开菜单仍由浏览器绘制
- 前端测试使用 `vitest`，但当前没有 `@vue/test-utils` 这类组件挂载测试基座

这意味着本次设计需要同时解决：

- 共享选择器本身
- 弹层定位与关闭契约
- 组件测试基础能力

---

## 4. UiSelect Component Contract

### 4.1 Public API

`UiSelect` 的公开接口保持最小但完整，第一版应至少支持：

- `modelValue`
  - 类型：`string | number | null`
  - 通过 `v-model` 作为受控值来源
- `options`
  - 结构：`{ label, value, disabled? }[]`
  - 空值项如“全部分类”或“不选择”不做隐式逻辑，直接作为普通选项显式传入
- `placeholder`
  - 当前值为空且没有匹配选项时显示
- `disabled`
  - 控制不可交互状态
- `name`
  - 若传入，则同步渲染一个隐藏字段用于表单值提交
- `ariaLabel`
  - 补足工具栏场景或无可见 label 场景的命名

为降低迁移摩擦，组件应至少发出：

- `update:modelValue`
- `change`

`change` 事件的语义是“用户通过组件完成了一次值变更”，便于页面在不重构整段状态逻辑的前提下完成替换。

### 4.2 First-Version Boundaries

第一版 `UiSelect` 明确不支持：

- 多选
- 搜索输入
- 远程异步加载
- 分组选项
- 自定义 option 模板插槽

这样做的原因不是永远拒绝这些能力，而是控制这次“去原生化”改造的复杂度，避免把一次控件统一项目膨胀成一套完整组合框系统。

### 4.3 Form Compatibility

当页面传入 `name` 时，`UiSelect` 应渲染一个隐藏字段并同步当前值。

目的不是为了模仿原生 `select` 的 DOM 形态，而是保持以下兼容性：

- 未来如果某些表单需要依赖字段名序列化，不会因为控件替换而断掉
- 页面迁移时不必为了“是不是还可提交值”重新审视整个表单契约

---

## 5. Rendering And Overlay Strategy

### 5.1 Structural Model

`UiSelect` 不应伪装成文本输入框。它的内部结构应采用：

- 关闭态：`button`
- 展开态面板：`listbox`
- 选项项：`option`

原因很直接：

- 当前所有用例都是“从固定列表中选择一个值”，不是自由输入
- `button + listbox` 比“假输入框”更符合语义
- 键盘交互与焦点语义更容易保持一致

### 5.2 Teleport And Positioning

选项面板不应直接绝对定位在组件容器内部，而应：

- `Teleport` 到 `document.body`
- 使用锚点触发器的 `getBoundingClientRect()`
- 通过 `position: fixed` 定位

这是本设计的关键约束。原因如下：

- 项目现有 modal/card 容器存在 `overflow` 与层级裁切风险
- 如果继续把下拉面板挂在局部布局树中，举报弹窗、审核弹窗、后台卡片和窄容器场景都容易出现被裁切或被压住的问题
- `Teleport + fixed` 能一次性规避父级 `overflow`、局部 stacking context 和复杂布局约束

### 5.3 Positioning Scope

第一版定位策略只解决本项目当前真实需要：

- 面板默认向下展开
- 若底部空间不足，则向上翻转
- 宽度至少与触发器一致
- 面板最大高度受限，溢出时内部滚动

第一版不额外引入通用 floating library，也不预先抽象大型 overlay 系统。

若实现中发现定位逻辑超出组件可承受复杂度，可以抽出一个很小的内部 composable，但不应先造通用框架。

---

## 6. Interaction And Accessibility Contract

### 6.1 Open / Close Behavior

`UiSelect` 的标准交互为：

- 点击触发器打开或关闭面板
- 点击外部区域关闭
- 选择某个选项后关闭
- `Escape` 关闭并把焦点返回触发器

当窗口尺寸变化或页面滚动导致锚点位置变化时，组件应重算面板位置。

### 6.2 Keyboard Behavior

第一版必须支持以下键盘行为：

- `Enter` / `Space` 打开
- `ArrowDown` / `ArrowUp` 打开，并把高亮定位到当前值或最近的可选项
- 打开后，`ArrowDown` / `ArrowUp` 在可用选项之间移动
- disabled 选项必须被跳过
- `Enter` 选择当前高亮项
- `Escape` 关闭

打开面板时：

- 若当前值有对应项，则高亮该项
- 若当前值为空或失配，则高亮第一个可选项

### 6.3 Accessibility Semantics

第一版无障碍契约至少包括：

- 触发器具备 `aria-haspopup="listbox"`
- 正确反映 `aria-expanded`
- 通过 `aria-controls` 关联面板
- 面板使用 `role="listbox"`
- 选项使用 `role="option"`
- 无可见标签场景必须传 `ariaLabel`

目标不是追求无障碍细节的理论完备，而是保证这个共享控件不会在语义层面比原生控件退化。

### 6.4 State Safety

若 `modelValue` 在 `options` 中没有匹配项，组件不应偷偷修改业务值。

正确行为是：

- 展示 placeholder 或空态文本
- 保留真实 `modelValue`
- 若存在隐藏字段，仍同步真实值

这样可以避免：

- 异步选项尚未准备好时状态被误清空
- URL query / 历史状态里的旧值被组件擅自改写

---

## 7. Migration Strategy

### 7.1 Replace In Place

本次迁移遵循“控件替换，不改业务语义”的原则：

- 原页面状态变量不重命名
- 原页面的禁用条件不变化
- 原页面空值含义不变化
- 原页面基于值变化触发的查询、过滤、提交逻辑不变化

换句话说，改造重点是把“值如何被选中”从原生 `select` 改为 `UiSelect`，而不是借机重构业务状态流。

### 7.2 Styling Strategy

现有页面里一部分 `select` 依赖 `.input` 通用外壳和局部 class 控制密度。

迁移后应遵循：

- `UiSelect` 自身拥有稳定的默认外观
- 继续允许页面通过 class 控制宽度与布局占位
- 若确有必要，可在组件层提供有限尺寸变体来覆盖工具栏紧凑场景

但不应让页面重新回到“半共享、半内联逃逸”的状态。

### 7.3 Modal And Admin Coverage

迁移不能只验证首页与搜索页。至少要覆盖：

- 帖子发帖分类
- 帖子流工具栏分类
- 搜索页分类
- 举报弹窗原因选择
- 审核页筛选与处理表单
- 奖励与成长后台表单
- 用户管理角色切换

原因是这些场景共同构成了本次设计的正确性边界：

- 弹窗中是否被裁切
- 管理页紧凑密度是否可用
- 普通页面与表单页的键盘行为是否一致

---

## 8. Testing And Regression Guards

### 8.1 Test Baseline

要让 `UiSelect` 成为可信的共享基础控件，前端测试基线必须补齐：

- 增加 `@vue/test-utils`
- 增加 `jsdom`
- 在 `vitest` 环境中启用 DOM 组件测试能力

这不是“顺手优化”，而是本次设计成立的必要条件。否则共享控件只能依赖手工点测，无法承担全局替换风险。

### 8.2 Component Test Scope

`UiSelect` 至少需要覆盖以下测试：

- 当前值与 placeholder 的渲染
- 打开与关闭行为
- 键盘导航
- disabled 选项跳过
- 选择后触发 `update:modelValue` 与 `change`
- 点击外部关闭
- `Teleport` 后面板挂载到 `body`
- 传入 `name` 时隐藏字段同步值

### 8.3 Static Guard

除组件测试外，必须增加一个静态守卫测试：

- 扫描 `frontend/src`
- 若发现新的原生 `<select`，则测试失败

这个守卫是本次设计的重要组成部分，因为用户要求不是“把几个下拉修漂亮”，而是“本项目不再使用任何原生 select”。

为避免把文档、脚本或测试 fixture 一并误伤，第一版守卫范围先限定在 `frontend/src`。

---

## 9. Risks And Non-Goals

### 9.1 Risks

这次改造的主要风险不在视觉，而在共享交互细节：

- 键盘行为不完整会直接让共享控件变得难用
- 弹层定位若处理粗糙，会在弹窗和窄容器中出现裁切或错位
- 若没有防回退守卫，代码库很快会重新混入原生 `select`

因此实现时应优先保障交互与测试，再考虑额外装饰。

### 9.2 Non-Goals

本次设计明确不包含：

- 组合框式搜索下拉
- 多选标签选择器
- 远程搜索选项
- 通用 overlay 平台化重构
- 对业务页面做与控件替换无关的样式重写

这些能力可以在未来扩展，但不应阻塞这次“停止使用原生 select”的主目标。

---

## 10. Design Summary

本次设计将 `frontend/src` 内现有 11 处原生 `select` 全量替换为共享 `UiSelect`，通过 `button + listbox + option` 的单选模型、`Teleport + fixed` 的弹层策略、明确的键盘与无障碍契约，以及组件测试加静态守卫的组合，建立一条可持续的无原生 `select` 前端约束。

这个方案刻意保持第一版边界收敛：

- 只解决当前真实存在的单选问题
- 不重写业务状态流
- 不引入不必要的第三方依赖
- 不把一次基础控件统一演变为完整组合框系统

后续 implementation plan 应围绕这一约束展开。
