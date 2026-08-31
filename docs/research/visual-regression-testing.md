# UI 迁移期视觉回归验证手段调研

> 对应 GitHub issue #124（隶属 wayfinder 地图 #121）。
> 调研问题：UI 逐域迁移期间如何防止视觉与交互回归？
> 所有结论均标注一手来源（Playwright 官方文档 / 本仓库文件路径）。

## 0. 结论摘要

1. 本仓库已有一套面向已部署 `single` 拓扑的 Playwright E2E 套件（`tests/playwright-single/`），覆盖 8 个产品域、带全局 API/页面错误审计，且已有 GitHub Actions 工作流在**隔离的 Compose 拓扑**上跑 PR 冒烟与定时全量回归。**交互回归的主防线已经存在，缺的是视觉（像素级）回归防线。**
2. Playwright 官方提供 `toHaveScreenshot()` 截图断言：首次运行生成基线、后续运行逐像素对比（pixelmatch），`--update-snapshots` 有意更新基线。官方明确警告：渲染随宿主 OS/字体/headless 模式变化，**基线必须与生成环境同环境运行**，官方 CI 文档推荐用官方 Docker 镜像固定环境。
3. 前端现有 Vitest 组件/单测跑在 jsdom 里，**不做真实布局与绘制，天然无法捕获视觉回归**；handbook 也规定「不要用 snapshot 掩盖行为断言」。视觉回归只能由真实浏览器截图承担，二者分工不冲突。
4. 建议按迁移节奏分三步引入：地基期先为「迁移前现状」生成核心页面基线（迁移中的意外像素变化 = 非目标回归信号）；帖子流试点期把 `PostsView` 系列纳入明暗双主题基线；逐域迁移期每域收尾时用 `--update-snapshots` 有意刷新该域基线并随 PR 审查 PNG diff。
5. 暗色主题应纳入基线矩阵：`stores/ui.js` 在无本地偏好时回退 `prefers-color-scheme`，Playwright 只需加一个 `colorScheme: 'dark'` 的 project 即可自动得到暗色基线，无需手工操作 localStorage。但矩阵翻倍，建议只给核心页面配双主题。
6. 最大的实施风险是**基线环境一致性**（中文字体栈跨平台字形差异大）和**页面动态内容**（时间戳测试数据）；前者靠固定 CI/容器环境解决，后者靠选择稳定页面状态 + `mask`/`stylePath` 遮盖动态区域解决。

## 1. 现有 Playwright 套件能力（一手来源：本仓库）

### 1.1 定位与启动方式

- `tests/playwright-single/README.md`（第 1–9 行）：套件「从浏览器用户的视角访问已经部署的前端 Nginx 和统一 Gateway」，**不启动服务、不初始化账号、不清理数据**，运行前需准备好可登录的本地 single 环境。
- 前置启动命令（README 第 40–44 行、`docs/handbook/testing.md` 第 184–201 行）：
  `./deploy/deployment.sh up --stack single --no-observability -- --wait --wait-timeout 120`
- 默认目标（README 第 46–52 行）：前端 `http://localhost:12881`（Nginx 托管的 SPA），Gateway `http://localhost:12880`。可用 `SINGLE_WEB_BASE_URL` / `SINGLE_API_BASE_URL` 覆盖（`.env.example`）。
- 命令入口（`package.json` scripts）：`test:smoke`（`--grep @smoke`）、`test:regression`（`--grep @regression`）、`test:headed`、`typecheck`、`show-report`。
- 依赖：`@playwright/test ^1.44.0`（`package.json` devDependencies），本地已安装版本为 `1.60.0`（`node_modules/@playwright/test/package.json`）。`maskColor`（v1.35+）、`stylePath`（v1.41+）等截图选项在当前版本线上均可用。

### 1.2 套件配置要点（`playwright.config.ts`）

- 单一 project：`chromium` + `devices['Desktop Chrome']`（第 25–30 行）——与「桌面端优先」的迁移前提一致。
- `fullyParallel: false`、`workers` 默认 1（第 11–12 行）：共享本地账号/数据，串行执行。
- `expect.timeout: 15_000`、`retries: 0`（第 8–13 行）。
- 失败产物：`trace: 'retain-on-failure'`、`screenshot: 'only-on-failure'`、`video: 'retain-on-failure'`（第 21–23 行）——**注意：这里的 screenshot 只是失败证据，不是基线对比**。
- `baseURL` 来自 `SINGLE_WEB_BASE_URL`（第 3、20 行）。

### 1.3 覆盖范围与错误审计

- `tests/00-smoke.spec.ts` 至 `07-im.spec.ts` 共 8 个 spec，覆盖可达性、认证、社区（发帖/点赞/收藏/评论/个人页）、钱包、市场（serial 串行）、网盘、后台权限、IM 会话（README 第 112–127 行）。
- `fixtures/audit.ts`：统一 fixture 审计 single API 的 4xx/5xx、浏览器 `pageerror` 和应用 `console.error`，除明确豁免的匿名探针/授权断言外任何错误都使测试失败（README 第 133–137 行、`docs/handbook/testing.md` 第 203–205 行）。这套 fixture 对迁移期同样有价值：UI 重构引入的 API 误用、JS 运行时报错会被直接拦截。
- 状态型用例写入带时间戳的测试数据（`fixtures/test-data.ts`、README 第 129–131 行），不自动清理——**这对截图基线意味着列表页内容每次运行都不同，截图页选择必须避开或遮盖动态数据**。

### 1.4 与 deploy 拓扑的关系及 CI 现状

- 套件只认 `single` 拓扑，通过 `deployment.sh` 启动（AGENTS.md：`single` 是常规开发拓扑）。
- `.github/workflows/playwright-single.yml`：PR 触发跑 `smoke`，`schedule`（每日）与 `workflow_dispatch` 跑 `regression`（第 14–17 行）。关键实践：按 `GITHUB_RUN_ID` 偏移端口、子网与卷命名空间，起一个**完全隔离的 single 拓扑**（第 30–64 行），跑完后收集 Compose 日志并销毁（第 84–123 行）。这意味着视觉基线测试可以直接复用该工作流的隔离拓扑机制，不需要为截图新增部署能力。

## 2. Playwright `toHaveScreenshot()` 官方事实（一手来源：playwright.dev）

### 2.1 工作机制与基线流程

来源：https://playwright.dev/docs/test-snapshots（Visual comparisons）

- `await expect(page).toHaveScreenshot()` 首次运行时生成参考截图（"On first execution, Playwright test will generate reference screenshots. Subsequent runs will compare against the reference."）。
- 断言内部会先**连续截取多张截图直到相邻两张一致**再与基线比对，天然等待页面稳定（同页及 API 文档："This function will wait until two consecutive page screenshots yield the same result, and then compare the last screenshot with the expectation."）。
- 基线文件存放于 `<测试文件名>-snapshots/` 目录，命名形如 `example-test-1-chromium-darwin.png`：**浏览器/project 名 + 平台名是文件名的一部分**，不同浏览器/平台需要各自基线。路径可用 `testConfig.snapshotPathTemplate` 配置。
- 官方明确要求：**把 snapshots 目录提交进版本库，并审查其变更**（"You should commit this directory to your version control (e.g. git), and review any changes to it."）。
- 有意更新基线：`npx playwright test --update-snapshots`。
- 格式默认 PNG（无损），命名 `.webp` 后缀可用 WebP（同样无损）。
- 元素级截图断言 `expect(locator).toHaveScreenshot()` 同样存在，适合组件/卡片级基线。

### 2.2 跨平台/字体渲染差异与阈值

来源：https://playwright.dev/docs/test-snapshots、https://playwright.dev/docs/api/class-pageassertions#page-assertions-to-have-screenshot-1

- 官方警告原文："Browser rendering can vary based on the host OS, version, settings, hardware, power source (battery vs. power adapter), headless mode, and other factors. **For consistent screenshots, run tests in the same environment where the baseline screenshots were generated.**"
- 像素比较使用 pixelmatch 库，可调参数：
  - `threshold`：单像素 YIQ 色彩空间感知色差，0（严格）到 1（宽松），**默认 0.2**；
  - `maxDiffPixels`：允许不同的像素总数（默认未设，可在 `expect.toHaveScreenshot` 全局配置）；
  - `maxDiffPixelRatio`：允许不同的像素占比（0–1）。
- 官方 CI 文档（https://playwright.dev/docs/ci）明确推荐在 GitHub Actions 中用 `jobs.<job_id>.container` 跑官方镜像 `mcr.microsoft.com/playwright:v<版本>-noble`，理由原文："to have a consistent environment for e.g. screenshots/visual regression testing across different operating systems."
- 同页建议 CI 中 `workers: 1` 以稳定优先——与本仓库现有 `PW_WORKERS=1` 默认一致。

### 2.3 提高截图确定性的官方手段

来源：https://playwright.dev/docs/api/class-pageassertions#page-assertions-to-have-screenshot-1

| 选项 | 默认值 | 迁移期用途 |
| --- | --- | --- |
| `animations: 'disabled'` | disabled | 冻结 CSS 动画/过渡（有限动画快进完成、无限动画回到初始帧），防止动效引入噪声。 |
| `caret: 'hide'` | hide | 隐藏文本光标，表单聚焦截图稳定。 |
| `scale: 'css'` | css | 一 CSS 像素对一图像像素，避免高 DPI 设备差异。 |
| `mask: Locator[]` / `maskColor` | — | 用纯色框遮盖动态区域（时间戳标题、头像、计数器），遮盖不可见元素也生效。 |
| `stylePath` | — | 截图时注入自定义样式表（可穿透 Shadow DOM），隐藏/固定易变元素；可在 `expect.toHaveScreenshot` 全局配置。 |
| `fullPage` | false | 整页滚动截图；长列表页慎用（内容高度随数据变化）。 |
| `clip` / `omitBackground` | — | 固定区域截图 / 透明背景。 |
| `timeout` | `expect.timeout` | 断言重试窗口；本仓库现有 `expect.timeout: 15_000` 可直接复用。 |

### 2.4 非像素快照：aria snapshot

来源：https://playwright.dev/docs/aria-snapshots

- `expect(locator).toMatchAriaSnapshot()` 对页面的**可访问性树**（角色/名称/层级，支持正则占位）做文本快照断言。
- 迁移期价值：结构/交互语义回归（按钮角色丢失、标题层级错乱、表单 label 断链）用 aria snapshot 断言比像素图更抗样式噪声；像素基线管「长得一样」，aria snapshot 管「结构语义一样」，二者互补。

## 3. Vitest 组件测试现状与分工（一手来源：本仓库）

### 3.1 现状

- 测试运行环境：`frontend/vite.config.js` 第 40–44 行 `test.environment: 'jsdom'`——**jsdom 不执行真实布局与绘制**（无渲染引擎），因此 Vitest 层在原理上无法发现 CSS/视觉回归。
- 覆盖面（`docs/handbook/testing.md` 第 173–182 行）：
  - `views/*State.test.js`：复杂页面纯状态转换（如 `postsViewState.test.js`、`marketState.test.js`、`walletState.test.js` 等 10 个）；
  - `views/*View.test.js`：22 个页面级组件交互测试（含 `PostsView.test.js`、`PostDetailView.test.js` 等）；
  - `components/**/*.test.js`：13 个组件测试（`UiState`、`UiModalConfirm`、`UiAutosuggestInput`、`FeedToolbar` 等），用 `@vue/test-utils` mount 断言类名、文本、事件（例：`UiState.test.js` 断言 `ui-state--empty` 类与文案）。
- handbook 原则（`docs/handbook/testing.md` 第 182 行、第 268 行）：「复杂页面逻辑优先写纯函数测试。只有必须验证渲染、事件绑定或组件生命周期时，才写组件测试」「不要用 snapshot 掩盖行为断言；只有稳定、低噪声 UI 结构才使用 snapshot」。

### 3.2 分工结论

| 层 | 工具 | 能防什么 | 防不了什么 |
| --- | --- | --- | --- |
| 状态纯函数 | Vitest `*State.test.js` | 数据/状态机回归（标签去重、分页、权限分支） | 一切渲染问题 |
| 组件交互 | Vitest `*View.test.js` / `components/**` | 事件绑定、条件渲染、用户可见文案状态 | 布局、配色、令牌泄漏、暗色对比度 |
| E2E 交互 | Playwright 现有 `@regression` + audit fixture | 跨页流程、API 错误、JS 运行时错误 | 像素/样式回归（除非恰好导致功能断言失败） |
| E2E 视觉 | **Playwright `toHaveScreenshot()`（待引入）** | 布局位移、令牌/暗色破坏、组件外壳走样 | 业务逻辑错误 |

Vitest 层在迁移期保持现有职责不变，**不要**为了视觉验证往 jsdom 层堆 snapshot（handbook 明确禁止用 snapshot 掩盖行为断言）；视觉回归全部交给真实浏览器截图层。

## 4. 迁移期验证策略建议

前提节奏（issue #121）：先地基（令牌 + Ui* 组件）→ 帖子流（PostsView）试点 → 逐域迁移；桌面端优先；暗色只做打磨；管理后台出范围。

### 4.1 引入方式：独立 spec + 独立标签，不动现有回归

- 在 `tests/playwright-single/tests/` 新增 `08-visual.spec.ts`（按现有编号约定），用例打 `@visual` 标签；`package.json` 增加 `test:visual`（`playwright test --grep @visual`）。
- 复用现有 fixtures：`auth.ts` 的 `loginViaUi`/`ensureStorageState`（登录态页面）、`helpers.ts` 的 `gotoHash`（hash 路由跳转）、`audit.ts` 错误审计（截图用例同样要求零 API/JS 错误）。
- `playwright.config.ts` 增加第二个 project（详见 4.4），并在 `expect.toHaveScreenshot` 设全局阈值（先保持 `threshold` 默认 0.2，只对个别抗锯齿噪声大的页面加小额度 `maxDiffPixels`——避免上调阈值掩盖真实回归）。
- 基线目录 `08-visual.spec.ts-snapshots/` **提交进 git**（官方要求），更新基线的 PNG diff 必须随迁移 PR 一并 review。

### 4.2 哪些页面/状态需要截图基线

选页原则：**结构稳定、内容可固定、迁移收益大**。避开内容随运行变化的状态型列表（或只截其空状态/骨架），动态区域用 `mask` 遮盖。

建议基线清单（与迁移节奏对齐；管理后台按 #121 排除）：

| 阶段 | 页面/状态 | 登录态 | 说明 |
| --- | --- | --- | --- |
| 地基期 | 登录页 `/auth/login`、注册页 | 匿名 | 纯静态表单页，最能暴露令牌/控件原语变化；现有 smoke 已断言其可达（`00-smoke.spec.ts` 第 28–32 行）。 |
| 地基期 | 帖子流 `/posts`（匿名首屏） | 匿名 | 迁移试点页的现状基线；列表数据动态 → 用 `mask` 遮盖帖子卡片区或 `stylePath` 固定，只对比页面框架/顶栏/侧栏/工具条。 |
| 地基期 | Ui* 组件集中页（可选）：临时写一个展示 16 个 Ui* 组件的本地路由或直接在试点页上截取组件区 | — | 令牌 + 组件是地基交付物，组件级 `expect(locator).toHaveScreenshot()` 成本最低、信号最纯。 |
| 试点期 | 帖子流 `/posts`、帖子详情 `/posts/:postId`（固定 seed 帖子）、发帖作曲器展开态 | 登录（aaa） | 试点域完整交互链；详情页内容需用固定测试数据（可复用 `SINGLE_TEST_RUN_ID` 固定值机制，README 第 110 行）。 |
| 逐域期 | 钱包 `/wallet`、市场列表 `/market`、网盘 `/drive`、消息 `/messages`、通知 `/notices`、搜索 `/search`、设置 `/settings`、个人主页 `/users/:userId`、收藏 `/bookmarks` | 登录 | 每域迁移收尾时纳入；优先选空状态/固定数据状态。 |
| 全程 | 403 / 404 页 | 匿名 | 全站通用外壳，便宜且稳定。 |

交互状态基线（少量、高价值）：UiModal 外壳（迁移痛点之一）、发帖作曲器、设置页主题切换控件。**不为每个交互态建基线**——交互行为正确性仍由现有 `@regression` 断言承担。

### 4.3 基线何时生成与更新

1. **迁移开工前**：为「现状页面」生成初版基线并提交。这一步的价值是把迁移期的每次运行变成「迁移影响面探测器」——未迁移页面截图不变 = 地基改动无泄漏；变化 = 非目标回归。
2. **试点迁移 PR 中**：帖子流相关基线用 `--update-snapshots` **有意更新**，PNG diff 作为 PR 审查材料（官方建议 review snapshot 变更）。这是「视觉验收」的落点。
3. **逐域迁移 PR**：同上，每域只更新该域基线；若 PR 中出现了**本域以外**的基线 diff，说明令牌/组件改动有跨域泄漏，必须回查。
4. **纪律**：`--update-snapshots` 只允许在「迁移该域」或「地基有意变更」的 PR 中使用；CI 失败时禁止用更新基线消红。

### 4.4 明暗双主题是否纳入基线矩阵

**纳入，但只给核心页面。**依据与机制：

- 暗色由 `html[data-theme='dark']` 驱动（`frontend/src/stores/ui.js` 第 41–45 行 `applyToDocument`）；store 初始化时若 localStorage 无偏好则回退 `window.matchMedia('(prefers-color-scheme: dark)')`（同文件第 24–27 行）。
- 因此 Playwright 侧**无需操作 localStorage**：在 `playwright.config.ts` 增加一个 `use: { ...devices['Desktop Chrome'], colorScheme: 'dark' }` 的 project（如 `chromium-dark`），全新上下文首访即渲染暗色。截图文件名自动带 project 名后缀（官方命名规则，见 2.1），明暗基线天然分文件。
- 矩阵成本翻倍，建议分级：
  - **双主题**：登录页、帖子流、帖子详情、设置页（主题切换的入口页）、Ui 组件基线——这些正是「暗色打磨」的验收对象；
  - **仅亮色**：钱包、市场、网盘、消息、通知、搜索等其余域——迁移期保证暗色「不崩」即可，暗色质量验收集中在核心页。
- 密度默认 `compact`（`ui.js` 第 28–29 行注释：技术社区默认紧凑），基线按默认密度即可，不为 comfortable 建矩阵。

### 4.5 环境一致性与预期成本

**环境（最大风险项）**：

- 官方硬要求：基线与运行同环境（2.2）。本仓库前端字体栈为 `"IBM Plex Sans", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", ...`（`frontend/src/styles/variables.css` 第 3 行），跨平台回退路径完全不同（macOS 落 PingFang、Windows 落 YaHei、Linux 落 Noto），**中文字形差异足以让任何像素对比失败**。纯中文界面放大了这个风险。
- 因此：**基线必须在 CI 环境（ubuntu-latest + `npx playwright install --with-deps chromium`，即现有 `playwright-single.yml` 的做法）生成**，本地（尤其 macOS）生成的基线不可提交。操作路径：
  1. 在 CI 上以 `--update-snapshots` 跑视觉用例（可用 `workflow_dispatch` + 上传 artifact 的方式取回 PNG），下载后提交；
  2. 或本地用官方 Docker 镜像 `mcr.microsoft.com/playwright:v<lock 版本>-noble` 跑（官方 CI 文档推荐容器保证截图一致性，见 2.2）。注意镜像内是否含 CJK 字体需先验证（官方文档未明示），若无则在镜像内补装或用挂载字体的方式固定。
- Playwright 版本需随 `package-lock.json` 钉住（当前 lock 解析为 1.60.0）；升级 Playwright 时浏览器渲染可能变化，应预期一次全量基线刷新。

**工作量/成本估算**（按上述清单）：

- 用例数：约 12–14 个截图用例 × （1–2 主题）≈ **20 张左右基线 PNG**；
- 首次接入：1 个 spec 文件 + config project + CI 基线生成流程，约 1–2 个工作日；
- 运行成本：视觉用例复用现有隔离 single 拓扑，截图断言本身为秒级；增量主要在 CI 每次运行多一个 suite（可挂在现有 `playwright-single.yml` 的 regression 路径，或 PR 阶段只跑 `@visual` 子集）；
- 维护成本：每次有意的视觉变更伴随 PNG diff 审查；动态区域遮盖清单（`mask`/`stylePath`）需随页面演进维护，这是主要的长期成本。

### 4.6 交互回归侧补充（非截图）

- 现有 `@regression` 套件在迁移期保持全绿即可作为交互回归底线；迁移某域时若改动交互路径，应按 handbook「每个 bugfix 优先补能复现原问题的测试」原则在该域 spec 补用例。
- 对结构语义敏感的重构（组件外壳统一、表单控件原语替换），可在关键页面对 `toMatchAriaSnapshot()` 建少量文本快照（2.4），比像素图抗样式噪声，适合在视觉基线尚未稳定的迁移早期充当结构防波堤。

## 5. 风险与开放问题

1. **CI 环境 CJK 字体**：ubuntu-latest / 官方 Playwright 镜像中 Noto Sans SC 等字体的可用性需在首次生成基线时验证；字体不一致是基线 flaky 的第一嫌疑。
2. **动态数据页面**：时间戳测试数据（`fixtures/test-data.ts`）使列表内容不可复现；基线页选择需坚持「空状态/固定数据 + mask 遮盖」，必要时为截图用例准备专用固定数据（mock-data-studio 或 seed）。
3. **基线生成流程未自动化**：当前建议的「CI 生成 → artifact 下载 → 提交」是人工环节；若迁移期基线更新频繁，可考虑专用 `workflow_dispatch` job 自动回传 PNG。
4. **阈值治理**：`threshold` 默认 0.2 是感知色差容忍，不是像素数容忍；迁移期不建议全局上调，个别页面噪声用 `maxDiffPixels` 小额度豁免并注释原因。
5. **IM 实时页**：`/messages/:conversationId` 含 WebSocket 实时状态，截图确定性差，建议排除或只截空会话态。

## 6. 来源清单

官方文档（一手）：

- Playwright Visual comparisons：https://playwright.dev/docs/test-snapshots
- Playwright PageAssertions.toHaveScreenshot API：https://playwright.dev/docs/api/class-pageassertions#page-assertions-to-have-screenshot-1
- Playwright CI（Docker 容器保证截图环境一致）：https://playwright.dev/docs/ci
- Playwright Aria snapshots：https://playwright.dev/docs/aria-snapshots

仓库文件（一手）：

- `tests/playwright-single/README.md`、`package.json`、`playwright.config.ts`、`.env.example`
- `tests/playwright-single/fixtures/audit.ts`、`fixtures/auth.ts`、`fixtures/test-data.ts`
- `tests/playwright-single/tests/00-smoke.spec.ts`
- `docs/handbook/testing.md`（测试层级、前端测试分工、single E2E 说明）
- `.github/workflows/playwright-single.yml`（隔离拓扑 CI）、`.github/workflows/quality.yml`（前端门禁）
- `frontend/vite.config.js`（jsdom 测试环境与覆盖率阈值）
- `frontend/src/stores/ui.js`（主题/密度机制）
- `frontend/src/styles/variables.css`（字体栈与设计令牌）
- `frontend/src/views/*State.test.js`、`frontend/src/views/*View.test.js`、`frontend/src/components/**/*.test.js`（Vitest 现状）
- `frontend/src/router/index.js`（路由清单，基线选页依据）
- GitHub issue #121（地图：迁移节奏、暗色打磨、桌面优先、后台出范围）、#124（本票）
