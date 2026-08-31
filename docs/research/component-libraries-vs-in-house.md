# 组件库候选调研：引入 vs 自研补齐

> 关联 issue：#122（Part of #121）
> 调研日期：2026-08-31
> 方法声明：全部结论基于一手来源——官方文档（含官方仓库内文档 markdown）、npm registry 元数据、GitHub API、以及本仓库代码；包体积为本次在干净环境中实测（方法见 §3）。未引用任何二手测评/博客。

## 1. 问题与约束

评估两条技术路线的事实基础：

- **路线 A：引入成熟 Vue 组件库**（候选：Naive UI、Element Plus、Ant Design Vue、PrimeVue）
- **路线 B：维持零 UI 依赖自研，补齐缺失原语**

项目硬约束（来自 #121）：Vue 3.5 + Vite 8（`frontend/package.json`：`vue ^3.5.13`、`vite ^8.2.1`）；纯中文界面；暗色模式**已存在**且由 `html[data-theme='dark']` CSS 令牌驱动（`frontend/src/styles/variables.css:135-205`，另有 compact 密度模式 207-218 行，`stores/ui.js` 驱动）；桌面端优先；视觉目标为"现代社区向（类 Discord/即刻）"。

现状地基（来自 #120 审计结论）：

- 令牌体系完整（218 行，间距/圆角/阴影/字阶/语义色板/焦点环，明暗双套）。
- 已有 14 个 Ui\* 原语（`frontend/src/components/ui/`）：UiButton、UiIconButton、UiCard、UiBadge、UiRoleBadge、UiAvatar、UiState、UiPageHeader、UiModalConfirm、UiPagination、UiScrollTop、UiToast、UiBreadcrumb、UiUserCard、UiMarkdown、UiAutosuggestInput。
- **缺口集中在表单控件与交互浮层**：无 UiInput/UiSelect/UiTextarea/UiField/UiTabs/UiDropdown/UiTable/UiTooltip；视图依赖全局 `.input` 类（`frontend/src/styles/components.css:32-68`，实测 19 个文件使用）和 20+ 处裸 `<select>/<textarea>`；`modal-mask/modal-card` 样板复制 4 份（UiModalConfirm、ReportModal、EditContentModal、ModerationView，实测 `modal-mask` 出现于 4 个文件）。

## 2. 候选库对比总表

版本均为 npm `latest`（npm registry，2026-08-31 抓取）。体积为**实测**（见 §3），均为 min+gzip 后字节数、Vue 计为 external。

| 维度 | Naive UI 2.45.3 | Element Plus 2.14.5 | Ant Design Vue 4.2.6 | PrimeVue 5.0.1 |
|---|---|---|---|---|
| License | MIT（[LICENSE](https://raw.githubusercontent.com/tusen-ai/naive-ui/main/LICENSE)，TuSimple） | MIT（[LICENSE](https://raw.githubusercontent.com/element-plus/element-plus/dev/LICENSE)） | MIT（[LICENSE](https://raw.githubusercontent.com/vueComponent/ant-design-vue/main/LICENSE)） | MIT（[LICENSE.md](https://raw.githubusercontent.com/primefaces/primevue/master/LICENSE.md)，PrimeTek） |
| Vue 3.5 兼容 | peerDep `vue ^3.0.0` ✅ | peerDep `vue ^3.3.7` ✅ | peerDep `vue >=3.2.0` ✅ | 无 peerDep 声明，Vue 3 专用库 ✅ |
| Vite 8 兼容 | 纯 ESM + 运行时注入样式，无 bundler 插件依赖 ✅（README："no webpack loaders are required"） | 官方维护 [element-plus-vite-starter](https://github.com/element-plus/element-plus-vite-starter)（暗色文档中引用）；按需样式需 unplugin 插件或手动 import CSS ✅ | 官方入门文档以 Vite 为首选脚手架（[getting-started.zh-CN](https://raw.githubusercontent.com/vueComponent/ant-design-vue/main/site/src/vueDocs/getting-started.zh-CN.md)）✅ | 纯 ESM（`primevue/*` 子路径导出），无需 bundler 插件 ✅ |
| 样式架构 | **CSS-in-JS**（运行时 css-render 注入；README 明示 "no less/sass/**css variables**"） | **CSS 变量驱动**（--el-* 变量；[dark-mode 文档](https://raw.githubusercontent.com/element-plus/element-plus/dev/docs/en-US/guide/dark-mode.md)："extracted and unified all necessary variables... based on CSS Vars"） | **CSS-in-JS**（v4 Design Token + ConfigProvider，[customize-theme.zh-CN](https://raw.githubusercontent.com/vueComponent/ant-design-vue/main/site/src/vueDocs/customize-theme.zh-CN.md)：v4 弃用 less/CSS 变量改用 CSS-in-JS） | **设计令牌 → CSS 变量**（styled 模式；[ArchitectureDoc](https://raw.githubusercontent.com/primefaces/primevue/master/apps/showcase/doc/theming/styled/ArchitectureDoc.vue)："base is the style rules with CSS variables as placeholders whereas the preset is a set of design tokens... mapping the tokens to CSS variables"） |
| 暗色模式实现 | JS 主题对象：`<n-config-provider :theme="darkTheme">`（[customize-theme 文档](https://raw.githubusercontent.com/tusen-ai/naive-ui/main/demo/pages/docs/customize-theme/enUS/index.md)） | `html.dark` class + 导入 `element-plus/theme-chalk/dark/css-vars.css`；可 CSS 覆盖变量 | ConfigProvider `theme.algorithm = theme.darkAlgorithm`（JS 运行时切换） | 预设含 colorScheme 双套令牌，`darkModeSelector` 可配置为任意选择器（官方示例 `.my-app-dark`；[DarkModeDoc](https://raw.githubusercontent.com/primefaces/primevue/master/apps/showcase/doc/theming/styled/DarkModeDoc.vue)） |
| 与现有 `variables.css` / `html[data-theme='dark']` 共存 | 差：令牌在 JS 主题对象中，需建第二套令牌体系并桥接 stores/ui.js 到 ConfigProvider | 中：同为 CSS 变量可覆盖 --el-* 映射项目令牌；但暗色挂在 `html.dark` 选择器，需在 stores/ui.js 同步加 class 或重写其作用域 | 差：同 Naive，JS token 体系，双令牌源 | **最好**：CSS 变量 + 可配置暗色选择器（理论上可直接用 `[data-theme='dark']`，需实测验证）；可自定义 preset 映射项目令牌 |
| 表单控件覆盖 | 全有：NInput/NSelect/NForm(校验)/NTabs/NDropdown/NDataTable/NTooltip/NModal（实测 import 全部解析成功） | 全有：ElInput/ElSelect/ElForm(校验)/ElTabs/ElDropdown/ElTable/ElTooltip/ElDialog（实测 import 全部解析成功） | 全有：Input/Select/Form(校验)/Tabs/Dropdown/Table/Tooltip/Modal/Textarea（实测 import 全部解析成功） | 全有：InputText/Select/Textarea/Tabs/DataTable/Tooltip/Dialog；Form 为 v4+ 新增组件（实测 import 全部解析成功；注意 v5 已移除旧 `primevue/dropdown`，由 Select 取代） |
| 中文文档 | 官方站默认中文，文档齐全（[naiveui.com](https://www.naiveui.com)） | 官方中文文档齐全（element-plus.org zh-CN） | 中文一手文档齐全（仓库内 zh-CN markdown 与 en-US 一一对应） | **官方文档仅英文**；组件文案有中文 locale（[primelocale/zh-CN.json](https://raw.githubusercontent.com/primefaces/primelocale/main/zh-CN.json)），但文档/社区中文材料最少 |
| 生态（GitHub stars，2026-08-31，gh api） | 18,522 | 27,723 | 21,635 | 14,457 |
| 维护活跃度 | 最新 release 2026-08-27 ✅ | 最新 release 2026-08-21 ✅ | **最新 release 2024-11-11（4.2.6），已 ~21 个月无发布** ⚠️（仓库 push 仍在 2026-08，说明主分支活跃但发版停滞） | 最新 release 2026-08-13（5.0.1，大版本刚发布，存在新 major 的 churn 风险） |
| **实测按需体积（gzip）** | **JS 168.7 KB**（含运行时样式，无独立 CSS） | JS 326.8 KB + 按需 CSS 14.8 KB ≈ **341.6 KB**（全量 CSS 为 48.1 KB） | **JS 236.2 KB** + reset.css 1.2 KB | **JS 151.4 KB**（含 Aura 预设主题令牌） |
| 包整体大小（npm unpackedSize） | 51.9 MB / 5432 文件 | 43.6 MB / 6863 文件 | 78.0 MB / 5355 文件 | 9.3 MB / 1634 文件 |

## 3. 体积实测方法与明细

方法：干净目录 `npm install vue@^3.5.13 esbuild 各库@latest`，编写入口文件仅 import 本项目实际需要的组件集（Button/Input/Select/Form/Tabs/Dropdown/Table/Tooltip/Modal/Textarea 等价物 + 主题设施），`esbuild --bundle --minify --format=esm --external:vue`，再 `gzip -c` 统计。按需 tree-shaking 生效依据：esbuild 对 ESM 的 dead-code elimination + 各库官方声明（Naive README "they are all treeshakable"；EP quickstart "Element Plus provides out of box Tree Shaking functionalities based on ES Module"；AntDV getting-started "默认支持基于 ES modules 的 tree shaking"）。

| 产物 | min 字节 | gzip 字节 |
|---|---|---|
| naive.out.js（11 组件 + NConfigProvider + darkTheme） | 594,256 | **168,683** |
| ep.out.js（13 组件，仅 JS） | 1,031,928 | **326,755** |
| ep 9 组件按需 CSS | 112,239 | 14,829 |
| ep 全量 CSS（dist/index.css） | 360,847 | 48,107 |
| antdv.out.js（13 组件，CSS-in-JS 含运行时代码） | 769,614 | **236,199** |
| antdv reset.css | 2,966 | 1,214 |
| prime.out.js（14 组件 + PrimeVue config + Aura 预设） | 709,998 | **151,406** |

解读：四者按需引入后 gzip 增量均在 **150–340 KB** 区间。最轻的是 PrimeVue（151 KB）与 Naive UI（169 KB）；Element Plus 最重（约 342 KB，JS 中含 dayjs、async-validator、floating 等运行时依赖）。对比参照：项目当前不含任何 UI 库（`frontend/package.json` 仅 vue/vue-router/pinia/axios 四个运行时依赖）。

## 4. 自研补齐路线成本估算

缺口清单（来自 #120 审计与本次复核）与复杂度评估：

| 组件 | 复杂度 | 估算（含 Vitest 用例） | 风险点 |
|---|---|---|---|
| UiInput | 低 | 0.5 人日 | 已有 `.input` 全局类做视觉基座（components.css:32-68），主要是组件化封装 + v-model 语义统一 |
| UiTextarea | 低 | 0.5 人日 | 同上，`.input.multiline` 已存在（components.css:46-52） |
| UiField（label/错误/帮助文本/校验态） | 低-中 | 1 人日 | 需定义校验结果传递约定（项目无表单校验库）；可配合原生 `required/pattern` + `:invalid`，或后续接 vee-validate 类库 |
| 统一 UiModal 外壳 | 低 | 0.5-1 人日 | 4 份 `modal-mask/modal-card` 复制（UiModalConfirm/ReportModal/EditContentModal/ModerationView），且全部基于原生 `<dialog>`（focus trap、Esc、backdrop 已由浏览器提供）——收敛为外壳组件 + slot，纯重构 |
| UiTabs | 低 | 1 人日 | 无定位/浮层问题；注意 aria（role=tablist/tab/tabpanel 键盘左右键） |
| UiTooltip | 中 | 1-2 人日 | 需要轻量定位逻辑（视口边界翻转）；无 floating-ui 依赖下自写定位是主要工作量 |
| UiDropdown | 中-高 | 2-3 人日 | 定位 + 焦点管理 + 键盘导航 + 点击外部关闭；a11y（menu/menuitem 语义）是主要风险 |
| UiSelect | 高 | 3-5 人日 | 全家最贵：弹出层定位、键盘导航（↑↓/Enter/Esc）、typeahead、aria（combobox/listbox 模式，需对齐 ARIA APG）、清除/禁用/加载态。建议范围裁剪：项目 20+ 处裸 `<select>` 多为简单单选，可先做单选无搜索版（1-2 人日），复杂场景保留原生 |
| UiTable | 中（裁剪后） | 1-2 人日 | 桌面端数据表只做"样式化的语义 table + 排序钩子"即可；虚拟滚动/列冻结/可编辑表格是深坑，明确不做（需要时再单独立项） |
| **合计** | | **约 10-17 人日**（含范围裁剪） | |

迁移面（替换裸元素为 Ui*）：`.input` 类使用 19 个文件、裸 select/textarea 20+ 处、modal 4 处——属机械替换，可随"先地基后逐域迁移"节奏摊到各域实施阶段，不构成一次性成本。

自研路线的既有优势（#120 审计已验证）：暗色/密度主题零额外工作（令牌已在 `html[data-theme='dark']` 下就位）；原生 `<dialog>` 弹窗模式成熟可复用；视觉语言完全自由，与"现代社区向"目标零冲突；无第三方依赖审计/升级负担。

## 5. 两条路线的关键事实对照

| 决策因子 | 引入组件库 | 自研补齐 |
|---|---|---|
| 一次性成本 | 接入 + 14 个现有 Ui\* 组件的取舍（迁移或长期双轨并存）+ 全站表单/弹窗替换 | 10-17 人日建 9 个原语 + 机械替换 |
| 主题化 | 需把已就位的 `html[data-theme='dark']` 令牌体系桥接进库的主题机制：Naive/AntDV 为 JS 主题对象（双令牌源），EP 为 --el-* 变量 + html.dark 选择器（半兼容），仅 PrimeVue 的 CSS 变量 + 可配置 darkModeSelector 天然贴合 | 零成本，已在令牌层解决 |
| 视觉语言 | 四者均有强默认风格：EP/AntDV 企业后台风、Naive 中性但可辨识、PrimeVue 取决于预设（Aura/Material/Lara/Nora）；要做"类 Discord/即刻"的现代社区视觉需大量 token/样式覆盖，覆盖深度越大，引入库的价值越被稀释 | 完全自由 |
| 包体积 | +151-342 KB gzip | +0（仅项目自有代码） |
| 维护风险 | AntDV 21 个月未发版 ⚠️；PrimeVue 5.0.1 为新 major ⚠️；EP/Naive 正常 | 全部自有，无上游风险；代价是组件缺陷自修 |
| 中文生态 | Naive/EP/AntDV 文档全中文；PrimeVue 官方文档仅英文 | — |
| a11y | 库的组件键盘/aria 经过广泛实战检验（显著优势，尤其 Select/Dropdown） | 自研 Select/Dropdown 的 a11y 是最大风险点，需对照 ARIA APG 自验 |

## 6. 倾向性建议

**建议路线 B：维持零 UI 依赖自研，补齐 9 个原语。** 理由（均可回溯上文事实）：

1. **主题化地基已就位且为 CSS 变量路线**——四候选中只有 PrimeVue 与该架构天然兼容，其余三个（尤其 CSS-in-JS 的 Naive/AntDV）都意味着在项目里引入第二套令牌源并长期桥接，与 #120 审计结论"主题化已解决，不要重建"直接冲突。
2. **缺口高度集中且成本可控**——9 个原语、10-17 人日，其中最贵的 UiSelect 可范围裁剪；相比之下引入库并不能只买缺口，还要处理 14 个现有组件的双轨问题与全站默认风格的覆盖战争。
3. **视觉目标与库的默认风格相悖**——"现代社区向（类 Discord/即刻）"不是任何候选库的开箱风格；覆盖越狠，库的价值越薄，而体积成本（150-342 KB gzip）与升级耦合却照付。
4. **维护面事实不利**——AntDV 发版停滞 ~21 个月，PrimeVue 5 刚发 major，中文界面下 PrimeVue 文档生态最弱；EP/Naive 虽健康，但被第 1、3 条否决。

**保留条款（何时应重新打开本决策）**：

- 若未来出现真正复杂的数据表需求（虚拟滚动、列冻结、单元格编辑），单独评估按需引入 Naive UI `NDataTable` 或 PrimeVue `DataTable` 的可行性（两者均支持 ESM 按需，实测单库地板价约 151-169 KB gzip），而不是为此提前全量引入。
- 若自研 Select/Dropdown 的 a11y 打磨超出预期，可评估引入 headless 原语库（只出行为不出样式，如 Reka UI）作为中间路线——这不会改变令牌与视觉体系的所有权。

## 7. 来源清单（均为一手）

本仓库：
- `frontend/package.json`（vue ^3.5.13 / vite ^8.2.1 / 零 UI 框架依赖）
- `frontend/src/styles/variables.css`（218 行令牌；135-205 暗色；207-218 compact）
- `frontend/src/styles/components.css`（`.input` 32-68、`.btn` 70-146、modal 238+）
- `frontend/src/components/ui/`（14 个 Ui\* 组件清单）
- issue #120 审计评论（组件缺口、modal 复制 4 处、裸 select/textarea 20+ 处）

官方来源：
- Naive UI：<https://github.com/tusen-ai/naive-ui>（README、LICENSE）、<https://raw.githubusercontent.com/tusen-ai/naive-ui/main/demo/pages/docs/customize-theme/enUS/index.md>、<https://www.naiveui.com>
- Element Plus：<https://github.com/element-plus/element-plus>（LICENSE）、<https://raw.githubusercontent.com/element-plus/element-plus/dev/docs/en-US/guide/dark-mode.md>、<https://raw.githubusercontent.com/element-plus/element-plus/dev/docs/en-US/guide/quickstart.md>、<https://github.com/element-plus/element-plus-vite-starter>
- Ant Design Vue：<https://github.com/vueComponent/ant-design-vue>（LICENSE）、<https://raw.githubusercontent.com/vueComponent/ant-design-vue/main/site/src/vueDocs/customize-theme.zh-CN.md>、<https://raw.githubusercontent.com/vueComponent/ant-design-vue/main/site/src/vueDocs/getting-started.zh-CN.md>
- PrimeVue：<https://github.com/primefaces/primevue>（LICENSE.md）、<https://raw.githubusercontent.com/primefaces/primevue/master/apps/showcase/doc/theming/styled/ArchitectureDoc.vue>、<https://raw.githubusercontent.com/primefaces/primevue/master/apps/showcase/doc/theming/styled/DarkModeDoc.vue>、<https://raw.githubusercontent.com/primefaces/primelocale/main/zh-CN.json>
- npm registry 元数据：<https://registry.npmjs.org/naive-ui>、<https://registry.npmjs.org/element-plus>、<https://registry.npmjs.org/ant-design-vue>、<https://registry.npmjs.org/primevue>（version/license/peerDependencies/unpackedSize/time）
- GitHub API（stars、pushed_at）：`gh api repos/{owner}/{repo}`
- 体积实测：esbuild 0.25 + npm registry 最新版本，方法见 §3（2026-08-31 一次性环境实测，命令可复现）
