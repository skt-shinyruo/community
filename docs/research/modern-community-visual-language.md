# 现代社区向视觉语言 → 可施工设计令牌调研

- 票据：GitHub issue #123（Part of #121）
- 日期：2026-08（以调研当日可访问的一手来源为准）
- 约束：纯中文界面 + 系统字体栈；明暗双主题（`html[data-theme='dark']`）；桌面端优先；零 UI 框架依赖
- 现状基线：`frontend/src/styles/variables.css`（4px 间距阶、语义色 + color-mix 弱变体、字阶、阴影）

> 方法说明：仅使用一手来源——产品官方品牌页、官方设计系统文档站、官方开源仓库源码、W3C 规范。Discord 应用内 UI 色值（如 #313338 等）与「即刻」均无官方公开设计系统文档，**不作为依据**；Discord 侧仅采用其官方品牌页。所有对比度数值按 WCAG 2.1 相对亮度公式实测计算（计算脚本为一次性 Node 片段，公式见 w3.org/TR/WCAG21/#relative-luminance）。

---

## 1. 结论速览

1. 现代社区向视觉语言 = **克制的表面分层 + 一个高辨识品牌色 + 紧凑圆角 + 快速生产型动效**。色彩不是拍脑袋取色，而是**角色化色阶**（Radix 12 阶：背景 1–2 / 组件底 3–5 / 边框 6–8 / 实色 9–10 / 文本 11–12）。
2. 暗色主题**不是反色**：主流体系均为暗色单独手工调校色阶（Radix、Primer），表面靠**明度递增**分层（Material M3 surface-container 阶、M2 白色叠加层实现），灰阶普遍**带冷色相**（Primer 暗色中性阶 HSL 色相 216°）。
3. 给本项目的可施工清单：修正 `--muted` 双主题对比度（亮 `#6B6B6B` / 暗 `#8A8A8A`，全表面 ≥4.5:1）、拆分 `--radius-md/lg`（8px / 12px）、引入 Radix indigo 开源色阶作品牌色、暗色表面阶改为带冷相的 `#0D0E12 → #23262E`、新增 5 档时长 + 3 条曲线的动效令牌。

---

## 2. 一手来源调研：现代社区产品的视觉语言

### 2.1 色彩体系：角色化色阶，而非零散取色

**Radix Colors（WorkOS 官方开源色阶系统，MIT）** 是「现代社区向」（Linear、Vercel 系产品同款语言）最完整的一手参照：

- 每种颜色 **12 阶**，每阶有明确用途（来源：[radix-ui.com/colors/docs/palette-composition/understanding-the-scale](https://www.radix-ui.com/colors/docs/palette-composition/understanding-the-scale)）：
  - 1–2 阶：应用背景 / 微妙组件背景
  - 3–5 阶：UI 组件背景（3=常态、4=hover、5=按下/选中）
  - 6–8 阶：边框（6=非交互组件、7=交互组件、8=强调边框与焦点环）
  - 9–10 阶：实色背景（9=彩度最高、10=9 的 hover 态）
  - 11–12 阶：文本（11=低对比文本、12=高对比文本）
- 文本阶的对比度目标是按 **APCA** 算法设计的（来源：[radix-ui.com/colors](https://www.radix-ui.com/colors) 首页「APCA text contrast」）。
- 每阶同时提供 **alpha 透明变体**，用于叠加在彩色背景上（同上，「Transparent variants」）。

**Discord 官方品牌页**（[discord.com/branding](https://discord.com/branding)）只定义品牌资产色：主色 **Blurple `#5865F2`**、Light Blurple `#E0E3FF`、Black `#000000`、White `#FFFFFF`（页面内另出现 `#F0F0F0`、`#23272A`、`#161CBB`）。注意：Discord **未公开**应用内 UI 令牌体系，其客户端深灰背景色无官方文档，本调研不引用。

**含义**：品牌色负责辨识度（一个主色 + 深浅变体），界面色负责层级（角色化中性阶），两者分离。本项目可沿用此二分。

### 2.2 圆角与卡片语言：紧凑、分用途

GitHub Primer 官方令牌（[primer/primitives → src/tokens/functional/size/radius.json5](https://github.com/primer/primitives/blob/main/src/tokens/functional/size/radius.json5)）：

| 令牌 | 值 | 官方用途标注 |
|---|---|---|
| small | 3px | 徽章、标签、小输入框（16px 以下元素） |
| medium | 6px | **大多数组件默认**：按钮、输入框、卡片、容器 |
| large | 12px | 对话框、模态等更大表面 |
| full | 9999px | 头像、药丸形元素 |

**含义**：社区产品的「卡片感」不来自大圆角，而来自**中小圆角（6–12px）+ 表面明度差 + 细边框**。头像/药丸用全圆角形成形状对比。

### 2.3 阴影与层级：亮主题靠阴影，暗主题靠明度

Material 3 官方令牌（[material-components/material-web → tokens/versions/latest/sass/_md-sys-color__dark.scss](https://github.com/material-components/material-web/blob/main/tokens/versions/latest/sass/_md-sys-color__dark.scss) + [_md-ref-palette.scss](https://github.com/material-components/material-web/blob/main/tokens/versions/latest/sass/_md-ref-palette.scss)）中，暗色 surface 角色是一个**明度递增的容器阶**：

| 角色 | 令牌引用 | 实测色值 |
|---|---|---|
| surface | neutral6 | `#141218` |
| surface-container | neutral12 | `#211F26` |
| surface-container-high | neutral17 | `#2B2930` |
| surface-container-highest | neutral22 | `#36343B` |
| surface-bright | neutral24 | `#3B383E` |
| on-surface（正文） | neutral90 | `#E6E0E9` |
| on-surface-variant（次级） | neutral-variant80 | `#CAC4D0` |
| outline-variant（分隔线/不需 3:1 的边框） | neutral-variant30 | `#49454F` |

Material 2 官方 Web 组件实现（m2.material.io 站点 bundle 内的 MDC CSS）中，暗色海拔通过 **`.mdc-elevation-overlay` 白色叠加层**（`background-color:#fff` + 透明度变量）表达，而非加深阴影。

**含义**：层级表达分主题——亮主题用阴影 + 明度差；暗主题用**表面明度递增 + 边框**，阴影只作辅助且需加深不透明度。

### 2.4 间距节奏：4px 基已成事实标准

Primer 官方间距令牌（[primer/primitives → src/tokens/functional/spacing/space.json5](https://github.com/primer/primitives/blob/main/src/tokens/functional/spacing/space.json5)）：xxs=2px（表单内极紧凑分隔）、xs=4px（徽章/列表紧凑间距）、sm=8px（**大多数 UI 默认间距**）……即 2px 微调 + 4px 基阶。本项目现有 `--space-1..9`（4–48px，4px 基）与该体系一致，可保留，仅需考虑补 2px 微调档。

### 2.5 动效曲线与时长：生产型优先，时长有档位

**Carbon 官方动效文档**（[carbondesignsystem.com/elements/motion/overview/](https://carbondesignsystem.com/elements/motion/overview/)）是时长档位最清晰的一手来源：

- 双风格：**productive（生产型）** 用于绝大多数微交互（按钮态、下拉、信息展开），**expressive（表现型）** 只留给重要时刻（系统通知出现等）。
- 时长令牌：`duration-fast-01` 70ms（按钮/开关微交互）、`fast-02` 110ms（淡入淡出）、`moderate-01` 150ms（小展开/短距移动）、`moderate-02` 240ms（展开/toast/系统沟通）、`slow-01` 400ms（大展开/重要通知）、`slow-02` 700ms（背景变暗）。
- 曲线：standard productive `cubic-bezier(0.2, 0, 0.38, 0.9)`、entrance productive `cubic-bezier(0, 0, 0.38, 0.9)`、exit productive `cubic-bezier(0.2, 0, 1, 0.9)`。

**Material**（官方 MDC Web 实现，m2.material.io bundle）：标准曲线 `cubic-bezier(0.4, 0, 0.2, 1)`，组件过渡典型值 280ms。

**Primer**（[primer/primitives → base/motion/easing.json5](https://github.com/primer/primitives/blob/main/src/tokens/base/motion/easing.json5)、[timing.json5](https://github.com/primer/primitives/blob/main/src/tokens/base/motion/timing.json5)）：easeOut `[0.3, 0.8, 0.6, 1]`（元素进入/出现，官方标注 RECOMMENDED 默认）、easeInOut `[0.6, 0, 0.2, 1]`（视口内移动/形变）、ease `[0.25, 0.1, 0.25, 1]`（hover 微交互）；时长刻度 0/50/100/200/300/400…1000ms。

**含义**：社区产品动效应「快而少」——hover/fade 70–110ms，展开/菜单 150–240ms，模态/大展开 ≤400ms；曲线以减速型（ease-out 系）为主。

### 2.6 暗色主题配色策略：为何不是简单反色

三份一手来源共同印证「暗色 = 独立设计的色阶」：

1. **Radix**：暗色是**独立手工调校的 scale**（官网宣称「Dark mode Just Works」；[github.com/radix-ui/colors → src/dark.ts](https://github.com/radix-ui/colors/blob/main/src/dark.ts) 中 `grayDark` = `#111111/#191919/#222222/#2A2A2A/#313131/#3A3A3A/#484848/#606060/#6E6E6E/#7B7B7B/#B4B4B4/#EEEEEE`，与亮色阶非镜像关系），并配套白色 alpha 叠加变体。
2. **Primer**：暗色中性阶**带蓝色相**（[dark.json5](https://github.com/primer/primitives/blob/main/src/tokens/base/color/dark/dark.json5)：`#0D1117` HSL(216, 27.8%, 7.1%)、`#151B23`、`#212830`、`#262C36`、`#2A313C`、`#2F3742`、`#3D444D`），不是纯灰。
3. **Material**：暗色表面用明度递增分层（见 2.3），且 M3 的 neutral 阶本身带轻微紫相（`#1D1B20` 等）；文本用色阶浅端（neutral80/90），不是白色直出。

**反色为何失败**：① 简单反色会把亮主题「越深越重」的层级翻成暗主题里违反直觉的「越亮越重」——必须改为**明度递增 = 层级递增**；② 纯黑底 + 纯白字对比过激（21:1），长文阅读刺眼，故文本用 `#E6E0E9` / `#EEEEEE` 级别而非 `#FFF`；③ 高彩度语义色在暗底上发荧，需提亮去饱和（本项目暗色已把 danger hover 换 `#F87171`、success 换 `#34D399`，方向正确）；④ 纯中性灰显「脏」，主流体系都给暗色灰阶加冷色相（Primer 蓝相、M3 紫相），与品牌色呼应。

---

## 3. 本项目现状对照（`frontend/src/styles/variables.css`）

| 维度 | 现状 | 评价 |
|---|---|---|
| 间距 | `--space-1..9` = 4–48px，4px 基 | ✅ 与 Primer/Radix 同基，保留；可选补 2px 微调档 |
| 圆角 | sm 6 / **md 12 / lg 12（重复）** / xl 16 / full 9999 | ⚠️ md 与 lg 同值，需拆分（见 4.2） |
| 阴影 | 亮主题 4 档（slate 色相、低透明度）；暗主题 3 档加深 | ✅ 结构合理；暗色不透明度可再加深 |
| 字阶 | 12/14/15/18/22/28 + 行高 1.2/1.5/1.8 | ✅ 中文系统字体栈下合理，不动 |
| 语义色 | danger/success/warning + color-mix 弱变体；暗色提亮 | ✅ 方向正确，保留 color-mix 派生模式 |
| 中性阶 | 亮：`#FFF/#F3F3F3/#E5E5E5`；暗：`#000/#0B0B0B/#121212/#1D1D1D` 纯灰 | ⚠️ 暗色为纯黑底 + 纯灰，建议改为带冷相的明度递增阶（见 4.4） |
| `--muted` | 亮 `#C7C7C7`（白底 1.69:1）、暗 `#4A4A4A`（黑底 2.37:1） | ❌ 双主题均远低于 4.5:1，需修正（见 4.1）；全仓仅 `UiBreadcrumb.vue` 分隔符一处使用 |
| 品牌色 | `--accent` 亮 `#111` / 暗 `#FFF`（黑白） | ⚠️ 无辨识度，与「现代社区向」目标不符（见 4.3） |

---

## 4. 可落地令牌建议清单（明暗双主题）

> 所有对比度为 WCAG 2.1 相对亮度公式实测值，达标线：正文文本 4.5:1、大文本与非文本关键 UI 3:1（[w3.org/WAI/WCAG21/Understanding/contrast-minimum](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum)、[non-text-contrast](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast)）。

### 4.1 修正一：`--muted` 对比度不达标

当前值：亮 `#C7C7C7`（白底 1.69:1、surface-2 上 1.52:1）；暗 `#4A4A4A`（黑底 2.37:1、surface-2 上 2.11:1）。均不达标。

**建议值（全表面 ≥4.5:1）：**

| 主题 | 建议值 | on `--bg` | on `--surface-2` | on `--surface-3` |
|---|---|---|---|---|
| 亮 | `--muted: #6B6B6B` | 5.33:1（#FFF） | 4.97:1（#F6F7F9） | 4.59:1（#ECEEF2） |
| 暗 | `--muted: #8A8A8A` | 6.08:1（#000）/ 5.59:1（建议新 bg #0D0E12） | 5.43:1（#121212）/ 4.93:1（建议新 #1A1C22） | — |

配套说明：亮主题在 4.5:1 硬约束下，「muted 弱于 text-3」的余量极小，需同时微调 `--text-3` 保持层级次序，建议亮主题 `--text-3` 由 `#737373` 加深为 `#666666`（5.74:1 on 白 / 5.36:1 on #F6F7F9 / 4.94:1 on #ECEEF2），层级变为 text-1 `#0D0D0D` > text-2 `#2D2D2D` > text-3 `#666666` > muted `#6B6B6B`。暗主题 `--text-3` `#8F8F8F`（5.27–6.49:1）已达标，保持强于 muted `#8A8A8A`，次序自然成立。

### 4.2 修正二：`--radius-md` 与 `--radius-lg` 同为 12px

参照 Primer 用途分档（3/6/12/full），建议：

| 令牌 | 现值 | 建议值 | 用途约定 |
|---|---|---|---|
| `--radius-sm` | 6px | **6px**（不变） | 徽章、标签、小按钮 |
| `--radius-md` | 12px | **8px** | 按钮、输入框、卡片、列表项（默认） |
| `--radius-lg` | 12px | **12px**（不变） | 模态、菜单、大容器 |
| `--radius-xl` | 16px | **16px**（不变） | 特大面板（少用） |
| `--radius-full` | 9999px | 9999px（不变） | 头像、药丸 |

迁移影响：`--radius-md` 现有 19 处使用（7 个文件：`styles/pages.css`、`styles/components.css`、`WalletView.vue`、`WalletAdminView.vue`、`SettingsView.vue`、`UiUserCard.vue`、`UiToast.vue`），由 12px 收紧到 8px 属有意视觉变化（更现代紧凑，贴近 Primer 卡片 6px 的方向）；个别需要保留 12px 观感的组件应改用 `--radius-lg`。

### 4.3 品牌色：引入 Radix indigo 开源色阶

现状黑白 accent 无辨识度。建议直接采用 **Radix indigo 色阶**（MIT 协议，明暗双阶齐备，[src/light.ts](https://github.com/radix-ui/colors/blob/main/src/light.ts) / [src/dark.ts](https://github.com/radix-ui/colors/blob/main/src/dark.ts)），与 Discord Blurple `#5865F2` 同属蓝紫家族但色值不同，不撞品牌：

| 令牌 | 亮主题 | 暗主题 | 实测对比度 |
|---|---|---|---|
| `--accent`（实色按钮底） | `#3E63DD`（indigo9） | `#3E63DD`（indigoDark9） | 白字 5.21:1 ✅ |
| `--accent-hover` | `#3358D4`（indigo10） | `#5472E4`（indigoDark10） | 白字 6.02:1 ✅ |
| `--accent-weak`（选中底/徽章底） | `#EDF2FE`（indigo3） | `#182449`（indigoDark3） | 配 `--accent-text` 分别 11.80:1 / 11.59:1 ✅ |
| `--accent-text`（链接/弱底上的文字） | `#3A5BC7`（indigo11） | `#9EB1FF`（indigoDark11） | 6.00:1 on 白 / 9.36:1 on 暗 bg ✅ |
| `--accent-contrast` | `#FFFFFF` | `#FFFFFF` | — |

注意：暗主题实色按钮不要换用 `#5472E4` 作底色（白字仅 4.28:1），保持 `#3E63DD` 底 + `#5472E4` hover。后续可把完整 12 阶作为 `--brand-1..12` 基础层落库，再语义化引用（与 Radix 角色约定一致：3–5 组件底、9–10 实色、11–12 文本）。

### 4.4 表面与边框分层（中性阶）

**亮主题**（轻微冷调，呼应品牌色；改动保守）：

| 令牌 | 现值 | 建议值 | 说明 |
|---|---|---|---|
| `--bg` / `--surface` | #FFFFFF | #FFFFFF（不变） | 亮主题内容区保持纸白 |
| `--surface-2` | #F3F3F3 | `#F6F7F9` | 冷调浅灰，弱化「水泥感」 |
| `--surface-3` | #E5E5E5 | `#ECEEF2` | hover/按下层 |
| `--border` | #D8D8D8 | `#DFE2E8` | 装饰性分隔（无对比度义务） |
| `--border-strong` | #8D8D8D | #8D8D8D（不变，3.32:1 on 白 ✅ 过 3:1） | 输入框/关键边界 |

**暗主题**（纯黑 + 纯灰 → 带冷相明度递增阶，参照 Primer 216° 色相与 M3 容器阶间距）：

| 令牌 | 现值 | 建议值 | 层级角色 |
|---|---|---|---|
| `--bg` | #000000 | `#0D0E12` | 应用背景（避免纯黑） |
| `--surface` | #0B0B0B | `#131418` | 卡片/面板 |
| `--surface-2` | #121212 | `#1A1C22` | hover/次级容器 |
| `--surface-3` | #1D1D1D | `#23262E` | 按下/浮层 |
| `--border` | #262626 | `#2A2D36` | 装饰性分隔 |
| `--border-strong` | #3B3B3B | `#5D6373` | 关键边界（3.21:1 on bg / 3.07:1 on surface ✅ 过 3:1；现值仅约 1.8:1） |

暗色文本校验（建议新表面阶上）：text-1 `#F5F5F5` 15.6:1、text-2 `#D0D0D0` 11.0:1、text-3 `#8F8F8F` 5.3–6.1:1、muted `#8A8A8A` 4.9–5.6:1，全部达标。`--topbar-bg` 同步改为 `rgba(13, 14, 18, 0.84)`。

### 4.5 语义色

保留现有体系（Tailwind 系 danger/success/warning + color-mix 弱变体 + 暗色提亮），只补一条规则：**语义色弱变体统一用 color-mix/alpha 派生**（现有 `--success-weak` 等已是该模式，与 Radix alpha 变体思路一致），暗色 weak 继续用 rgba 形式，不再为每个语义色维护第二套硬编码。

### 4.6 阴影

亮主题 4 档保留。暗主题按「阴影只作辅助、明度承担分层」原则加深并补齐第 4 档：

```css
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.5);
--shadow-md: 0 8px 18px rgba(0, 0, 0, 0.45);
--shadow-lg: 0 16px 32px rgba(0, 0, 0, 0.5);
--shadow-xl: 0 24px 48px rgba(0, 0, 0, 0.55); /* 新增，暗色浮层/模态 */
```

### 4.7 动效令牌（新增）

综合 Carbon 时长档 + Material/Primer 曲线：

```css
/* 时长：Carbon duration tokens（70/110/150/240/400） */
--duration-instant: 70ms;   /* 按钮/开关等微交互 */
--duration-fast: 110ms;     /* 淡入淡出、hover 变色 */
--duration-base: 150ms;     /* 小展开、短距移动 */
--duration-slow: 240ms;     /* 菜单、modal、toast */
--duration-slower: 400ms;   /* 大展开、重要系统通知 */

/* 曲线 */
--ease-standard: cubic-bezier(0.4, 0, 0.2, 1);   /* Material 标准曲线，视口内移动默认 */
--ease-enter: cubic-bezier(0, 0, 0.38, 0.9);     /* Carbon entrance productive，进入/出现 */
--ease-exit: cubic-bezier(0.2, 0, 1, 0.9);       /* Carbon exit productive，离场 */
```

约定：生产型动作为默认（hover/fade ≤110ms、展开 ≤240ms），表现型（开业通知、空状态插画等）才允许 400ms 以上。

### 4.8 间距与字阶

- 间距：保留 `--space-1..9`；可选新增 `--space-0: 2px`（Primer xxs=2px，用于表单字段间极紧凑分隔）。
- 字阶：12/14/15/18/22/28 不动；中文长文行高用 `--line-loose` 1.8；字体栈维持系统栈（不引入中文 webfont，与 #121 既定前提一致）。

---

## 5. 落地顺序与风险

1. **低风险先行**：4.1（--muted + --text-3 微调）、4.7（动效令牌新增）、4.6（暗色阴影）——纯令牌值变更，无结构影响。
2. **中风险**：4.2（radius 拆分，19 处 --radius-md 观感收紧，需试点页回归）、4.4（暗色表面阶换色，注意排查硬编码色值泄漏，参照 #120 审计的令牌泄漏清单）。
3. **决策依赖**：4.3（品牌色引入）会改变产品气质，建议先在「视觉小样」票中与黑白 accent 做 A/B 小样再定。
4. 全部改动只动 `variables.css` 令牌值 + 语义化引用，组件侧零改动（受益于此前的令牌化基础）。

---

## 6. 来源清单（全部一手）

| 结论 | 来源 |
|---|---|
| 12 阶色阶角色约定（背景/组件底/边框/实色/文本） | https://www.radix-ui.com/colors/docs/palette-composition/understanding-the-scale |
| APCA 对比目标、alpha 变体、暗色独立调校 | https://www.radix-ui.com/colors |
| Radix grayDark / indigo / indigoDark 色值 | https://github.com/radix-ui/colors/blob/main/src/dark.ts 、 https://github.com/radix-ui/colors/blob/main/src/light.ts |
| Discord 品牌色 Blurple #5865F2 等 | https://discord.com/branding |
| Primer 圆角 3/6/12/9999 及用途 | https://github.com/primer/primitives/blob/main/src/tokens/functional/size/radius.json5 |
| Primer 动效曲线与时长刻度 | https://github.com/primer/primitives/blob/main/src/tokens/base/motion/easing.json5 、 https://github.com/primer/primitives/blob/main/src/tokens/base/motion/timing.json5 |
| Primer 间距 2/4/8px | https://github.com/primer/primitives/blob/main/src/tokens/functional/spacing/space.json5 |
| Primer 暗色中性阶（#0D1117 等，HSL 216°） | https://github.com/primer/primitives/blob/main/src/tokens/base/color/dark/dark.json5 |
| M3 暗色 surface-container 阶与 neutral 色值 | https://github.com/material-components/material-web/blob/main/tokens/versions/latest/sass/_md-sys-color__dark.scss 、 https://github.com/material-components/material-web/blob/main/tokens/versions/latest/sass/_md-ref-palette.scss |
| M2 暗色海拔白色叠加层实现、标准曲线 cubic-bezier(0.4,0,0.2,1) | Material 官方 MDC Web 组件 CSS（m2.material.io 站点 bundle，同源于 https://github.com/material-components/material-web） |
| Carbon 动效双风格、时长令牌 70–700ms、easing 曲线 | https://carbondesignsystem.com/elements/motion/overview/ |
| WCAG 对比度达标线 4.5:1 / 3:1 与亮度公式 | https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum 、 https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast |
| 本项目现状令牌与使用量统计 | frontend/src/styles/variables.css；--muted 唯一起用于 frontend/src/components/ui/UiBreadcrumb.vue:37；--radius-md 19 处 / --radius-lg 6 处（grep 统计） |
