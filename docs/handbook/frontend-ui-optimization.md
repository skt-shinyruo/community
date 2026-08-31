# Community 前台 UI 优化规范

| 项目 | 值 |
| --- | --- |
| 状态 | 已批准，规划中（尚未实施） |
| 日期 | 2026-08-31 |
| 范围 | `frontend/` 的全部非管理前台页面 |
| 视觉方向 | 现代社区 · Indigo |
| 技术路线 | 保留自研令牌与 Ui 原语，增量补齐缺口 |
| 决策地图 | [Wayfinder 地图：优化本项目的 UI 界面](https://github.com/skt-shinyruo/community/issues/121) |
| 成稿票 | [分阶段实施计划与 spec 成稿](https://github.com/skt-shinyruo/community/issues/129) |

## 1. 文档定位

本文是本轮前台 UI 迁移的唯一施工规范，统一视觉语言、技术路线、交互与信息架构、迁移波次和验收门槛。迁移中的实现取舍不得绕过本文另起一套规则。

本文描述目标状态，不替代当前行为文档。每个迁移波次落地后，必须同步更新 `docs/handbook/frontend.md`；新增或改变测试入口时同步更新 `docs/handbook/testing.md`。波次尚未落地前，handbook 仍是当前行为的来源。

研究材料只提供决策依据，不是并列规范：

- [现代社区向设计参考调研](../research/modern-community-visual-language.md)
- [组件库候选调研：引入 vs 自研补齐](../research/component-libraries-vs-in-house.md)
- [UI 迁移期视觉回归验证手段调研](../research/visual-regression-testing.md)
- [帖子流视觉小样](../../frontend/prototype/posts-visual-directions.html)

## 2. 目标与非目标

### 2.1 目标

- 全部非管理前台页面使用同一套现代社区视觉语言，明暗主题与紧凑密度表现一致。
- 共享控件收敛到 Ui 原语，页面只负责页面语义、渲染与交互绑定。
- 桌面导航、反馈、加载、层级、分页和键盘操作遵守统一规则。
- 先完成视觉基线预检与基础层，再以 `PostsView` 单页试点，随后按页面簇迁移。
- 每个迁移波次都有自动化检查、视觉基线和人工验收，不以“能构建”代替行为验证。

### 2.2 非目标

- 不重设计管理后台：治理、统计、用户管理、钱包管理和市场争议页面保持现状。只允许为退役共享 CSS、收敛 Modal 外壳和清理空面包屑做行为等价的机械调整。
- 不做移动端重设计；移动端只要求既有导航、内容与操作不溢出、不遮挡、不失效。
- 不重建主题系统；现有 CSS 令牌、暗色主题和 compact 密度继续作为基础。
- 不修改后端 API、部署或数据结构。
- 不在本轮实现评论排序；后续见 [支持帖子评论排序](https://github.com/skt-shinyruo/community/issues/131)。
- 不在本轮实现评论删除；spec 只记录其依赖新的后端删除接口。
- 不在本轮实现 Topbar 通知铃铛或摘要面板；侧边栏未读角标承担发现入口。
- 不增加完整 `--brand-1..12` 色阶、2px 间距档、UiSelect 搜索/多选或复杂表格能力。

## 3. 术语与范围边界

- **一级域**只表示侧边栏的社区、市场、个人三个顶级导航区域。
- **基础层**表示全部页面共同依赖的令牌、原语、产品壳层和验证能力，不是业务页面。
- **页面簇**表示迁移实施单元；它可以包含同一用户目标下的多个路由，但不是后端 owner domain。
- **迁移波次**表示可独立验收的阶段；可以包含多个小 PR，但整波通过验收门后才能进入下一波。
- **页面合同**记录正式路由在共享规范下的差异和验收责任；它不是逐页像素稿。
- **消费流**包括帖子、市场、通知、会话、收藏和评论等浏览型列表，统一使用“加载更多”追加分页。

本轮覆盖当前 **29 个非管理端点、27 个独立 View**：认证、帖子与评论、搜索、收藏、设置、个人主页与关注关系、通知、私信、市场与订单、网盘与公开分享、钱包、403 和 404。管理路由不在覆盖清单内。

### 3.1 页面合同索引

每个正式路由都属于一个页面簇，并在对应迁移波次中履行下表的页面合同。合同记录页面差异和验收责任，不是逐页像素稿。

| 路由 | 迁移波次 | 页面合同重点 |
| --- | --- | --- |
| `/auth/login`、`/auth/register`、`/auth/password/reset` | 1. 基础层 | 表单原语、原生校验、匿名壳层和键盘流 |
| `/posts` | 2. PostsView 试点 | 过滤、消费流、发布 composer 和双主题核心基线 |
| `/posts/:postId`、`/bookmarks` | 3. 社区完善 | 详情层级、评论深链、分享/举报、收藏和加载更多 |
| `/search` | 4. 搜索 | 壳搜索往返、分类/tag 筛选、加载更多和空/错态 |
| `/settings`、`/market/addresses` | 1 / 5. 基础层与 Account | 基础层先交付外观/地址与兼容重定向；Account 波次完成资料和整体视觉 |
| `/users/:userId`、`/users/:userId/followees`、`/users/:userId/followers` | 5. Account | 访客/本人/他人状态、关系操作和加载更多 |
| `/notices`、`/notices/:topic`、`/messages`、`/messages/:conversationId` | 6. Inbox | 未读角标、已读、IM pending/committed、断线恢复和空会话 |
| `/drive`、`/drive/s/:shareToken` | 7. Drive | 列表工作区、上传/取消、提取码分享和回收站 |
| `/wallet` | 8. Wallet | 余额/流水、pending、转账幂等和资损确认 |
| `/market`、`/market/listings/:listingId` | 9. Market | 商品浏览、页内搜索、库存/价格状态 |
| `/market/publish`、`/market/my-listings`、`/market/my-listings/:listingId/inventory` | 9. Market | 表单、库存表格和高风险写操作 |
| `/market/orders/buying`、`/market/orders/selling`、`/market/orders/:orderId` | 9. Market | 域内 tabs、订单 pending、确认/争议入口 |
| `/403`、`/:pathMatch(.*)*` | 1. 基础层 | 共享 `UiState`、壳层、焦点和稳定截图 |

## 4. 视觉语言

### 4.1 色彩与主题

- `frontend/src/styles/variables.css` 是唯一令牌来源，不引入第二套主题对象。
- 品牌色采用 Radix Indigo：`--accent: #3E63DD`、亮色 `--accent-hover: #3358D4`、暗色 `--accent-hover: #5472E4`、亮色 `--accent-weak: #EDF2FE`、暗色 `--accent-weak: #182449`、亮色 `--accent-text: #3A5BC7`、暗色 `--accent-text: #9EB1FF`、`--accent-contrast: #FFFFFF`。
- 链接使用独立语义令牌：亮色 `--link-color: #2563EB`，暗色 `--link-color: #4A86FF`；不得用 `--unread` 代替链接色。
- `--text-3` 使用亮色 `#666666` / 暗色 `#8F8F8F`；`--muted` 使用亮色 `#6B6B6B` / 暗色 `#8A8A8A`，并纳入对比度测试。`--muted` 仍仅用于装饰、禁用和占位，正文和辅助文字使用 `--text-2` / `--text-3`。
- 暗色表面使用带冷相、逐层增亮的 `#0D0E12` / `#131418` / `#1A1C22` / `#23262E`，普通边框使用 `#2A2D36`、`--border-strong` 使用 `#5D6373`；不得通过亮色反相生成。阴影、focus ring、hover 和 active 都必须在暗色背景可辨识。
- 主题偏好为浅色、深色、跟随系统三态；Topbar 快捷按钮继续在浅色/深色间切换，完整三态与密度设置位于 Settings 的“外观”区。偏好为“跟随系统”时，点击 Topbar 按钮按当前有效主题切到相反主题，并保存为显式偏好。
- compact 继续作为默认密度；comfortable 与 compact 共用组件 API，只改变令牌。

### 4.2 形状、排版与层级

- 控件、输入和消费流卡片默认 `--radius-md: 8px`，小型 chip 使用 `--radius-sm: 6px`，弹窗、菜单和大型独立面板使用 `--radius-lg: 12px`。
- 帖子和消费流卡片使用 8px 圆角、扁平表面和 1px 边框；hover 只改变描边或表面色，不抬升、不放大。
- 未读状态使用 3px accent 左轨和弱色 chip，不靠整卡高饱和底色表达。
- 卡片标题使用系统无衬线字体、19px、字重 650、行高 1.35、字距 0；不引入中文 webfont。
- 页面标题和长文正文继续使用系统无衬线字体；本轮不启用现有 serif display 令牌。
- meta 文本使用 13px 正常大小写；中文界面不得使用无意义的全大写和加宽字距；迁移页面的 `letter-spacing` 固定为 `0`，不使用负字距。
- 分类使用弱表面 chip，标签使用 `#标签` 纯文本，不新增 UiTag。
- z-index 只使用七阶语义令牌：`--z-raised: 1`、`--z-sticky: 40`、`--z-nav: 60`、`--z-overlay: 100`、`--z-popover: 200`、`--z-modal: 300`、`--z-toast: 1000`。

### 4.3 动效与图标

- 动效时长固定为 70/110/150/240/400ms，对应 instant/fast/base/slow/slower。
- hover 和 fade 不超过 110ms，普通展开不超过 240ms，大范围展开不超过 400ms；使用 ease-out 系进入/退出曲线。
- transition 必须列出具体属性，不使用 `transition: all`。
- 遵守 `prefers-reduced-motion`，关闭非必要位移和过渡。
- 图标统一使用 `lucide-vue-next` 的命名导入；它是本轮允许新增的唯一视觉依赖，不因此引入组件库。
- 图标按钮必须使用 `UiIconButton`，有明确 `aria-label` 和 tooltip/title；主要命令仍使用图标加文字，不用无标签图标猜语义。
- 不新增本地图标 path 表或 UiIcon 包装层；旧手写 `<svg>` 随所在页面簇迁移，收尾时从公共壳层和非管理页面清零。

## 5. 技术路线与组件边界

### 5.1 路线

- 不引入 Vue UI 组件库；现有 CSS 令牌、Ui 原语和构建链保持所有权。
- 现有 Ui 原语原地演进，不做全量替换或双轨组件体系。
- 复杂表格出现虚拟滚动、列冻结或单元格编辑时，才重新评估按需表格组件。
- 自研 Select/Dropdown 的可访问性成本明显超出预算时，才重新评估 headless 原语库。

### 5.2 新增与收敛的原语

以下十个原语的职责一次性冻结，但实现按首次需要交付，避免基础层堆积未验证组件：

它们增量补齐现有 16 个 Ui SFC，而不是替换既有组件：`UiAutosuggestInput`、`UiAvatar`、`UiBadge`、`UiBreadcrumb`、`UiButton`、`UiCard`、`UiIconButton`、`UiMarkdown`、`UiModalConfirm`、`UiPageHeader`、`UiPagination`、`UiRoleBadge`、`UiScrollTop`、`UiState`、`UiToast`、`UiUserCard`。清单以文件为准，不沿用旧审计中的“14 个”计数。

| 原语 | 最小职责 | 首次交付 |
| --- | --- | --- |
| `UiInput` | 单行输入、`v-model`、size/variant、原生属性透传 | 1. 基础层 |
| `UiTextarea` | 多行输入、`v-model`、原生属性透传 | 1. 基础层 |
| `UiField` | label、帮助文本、错误文本、原生校验状态 | 1. 基础层 |
| `UiTooltip` | hover/focus 提示和视口边界处理 | 1. 基础层 |
| `UiModal` | 原生 `<dialog>` 外壳、尺寸/title、header/body/footer slots、close 事件 | 1. 基础层 |
| `UiSkeleton` | 列表、卡片和详情首载的结构占位 | 1. 基础层 |
| `UiTabs` | tablist/tab/tabpanel 和方向键/Home/End 切换 | 2. PostsView 试点 |
| `UiDropdown` | menu/menuitem、定位、焦点管理、Escape/外部关闭 | 2. PostsView 试点 |
| `UiSelect` | 单选、浮层定位、typeahead、禁用/清除/加载、APG combobox/listbox 键盘语义 | 4. 搜索 |
| `UiTable` | 语义 table、统一样式和排序钩子；不含虚拟滚动、冻结列、编辑 | 9. Market |

组件传值优先 `v-model`，内容优先 slots，variant/size 命名沿用 `UiButton`。`UiField` 先使用 `required`、`pattern` 和 `:invalid`，不预装表单校验库。UiSelect 不做搜索输入或多选。所有 dialog 继续遵守初始焦点、焦点圈定、Escape/backdrop 策略、标题/描述关联、异步动作禁用和关闭后焦点恢复约定。

新增原语的规划量级为 10.5–16 人日，只作为拆票与容量基线，不构成排期承诺；首次交付后按真实数据修正，不为覆盖估算而预建抽象。

### 5.3 CSS 组织

- 页面样式只写在 `<style scoped>`；超过约 300 行时可拆同目录外部 scoped CSS。
- 全局样式只保留 variables、base、layout、utils 四类。
- `PostsView.css` 改为 scoped 引入；`pages.css` 的市场样式随市场页面簇迁入各视图后删除。
- `components.css` 中的原语样式随组件迁入各 Ui SFC；完成后全局只保留 variables、base、layout、utils。
- `.btn`、`.input`、`.card` 和 `.skeleton` 是原语内部实现细节，迁移后的视图不得直接使用。
- `UiButton` 提供 `to` / `href` 形态，吸收“链接外观按钮”。
- `.auth-form`、`.auth-field`、`.field-label` 由 `UiField` 收敛；`.tag` / `.tag-btn` 只保留一个定义，不新增 UiTag。
- 间距和语义尺寸使用 `--space-*`；1px 发丝边框和图标固有尺寸可保留 px。
- 不新增断点令牌；移动端维持现有断点。

## 6. 信息架构与交互契约

### 6.1 产品壳层

- 侧边栏收敛为三个一级域：社区（帖子、搜索、收藏、我的主页）、市场、个人（积分钱包、网盘、通知、私信、设置）。
- 发布商品、我的出售、我的购买和出售订单进入市场页主操作或域内 tabs；收货地址进入 Settings。
- Settings 使用 `?section=profile|appearance|addresses` 驱动 UiTabs；旧 `/market/addresses` 重定向到 `/settings?section=addresses`，不保留双入口。
- 波次 1 先建立 Settings section/query 契约并交付 appearance/addresses；`UiTabs` 在波次 2 首次交付后，于波次 5 的 Account 迁移中替换临时 section 导航。
- 管理组对有权限用户维持现状，本轮不重设计。
- 壳搜索在公开页常显，搜索帖子、标签和用户并跳转 `/search`；市场使用自己的页内搜索。`Cmd/Ctrl+K` 聚焦壳搜索。
- 账户区只保留在侧边栏底部：头像、姓名、角色、设置和登出；Topbar 删除重复用户块和溢出菜单。
- 导航选中态使用 `--accent-weak` 背景、`--accent-text` 文字和 3px accent 左轨，不使用实色整块。
- Topbar 只由折叠按钮、中文工作区 eyebrow、壳搜索和主题按钮组成；本轮不增加通知铃铛或摘要面板。
- 侧边栏通知和私信入口显示未读角标。移动端五入口保持现状，只同步视觉令牌和角标。
- 未读数在登录恢复、窗口重新聚焦、已读操作和 IM 实时事件后刷新，不增加轮询。

### 6.2 页面层级

- 两级页面使用“返回父级”链接；三级以上或状态驱动路径使用 `UiBreadcrumb`。
- 删除只显示“首页”的空面包屑，禁止同页同时出现面包屑和重复返回按钮。
- 页面 H1 由 `UiPageHeader` 承载；Topbar 只显示工作区 eyebrow。
- `UiPageHeader` 仅保留 page/section 两档，不为单页新增标题变体。

### 6.3 反馈、加载与确认

| 场景 | 反馈渠道 |
| --- | --- |
| 结果立即在当前屏幕可见 | 静默更新 |
| 结果不可见或操作伴随跳转 | toast |
| 错误可定位到字段或区块 | 内联反馈 |
| 错误不可定位 | toast |

- 不可逆、资损风险或影响他人的操作使用 `UiModalConfirm`；可逆、单人操作和登出免确认。
- 列表、卡片流和详情首载使用 `UiSkeleton`；分页加载使用尾部指示；操作中使用按钮 loading。
- `UiState` 只承担 empty/error/development 等结果状态：empty 必有主要下一步，error 必有重试，development 只用于未上线功能。
- 全站淘汰裸“加载中...”文本，不得用 empty 状态冒充 loading。

### 6.4 消费流与帖子链路

- 消费流统一使用“加载更多”追加分页；`UiPagination` 只用于管理和表格页面。
- 帖子流查询统一为 `categoryId`、`tag`、`order=latest|hot`；`boardId` 退役。
- 帖子卡的分类和标签可点击并回到相同过滤模型；toolbar 提供最新/最热 tabs 和可清除的 tag chip。
- 评论/回复发布后静默插入并定位到新内容；评论编辑保存不弹成功 toast。
- 分享复制链接后 toast；评论举报复用 ReportModal；关注、举报、屏蔽等低频动作进入 `UiDropdown`。
- 评论和回复继续支持深链定位与高亮；分页改为加载更多后不得破坏 `commentId` / `replyId` 定位。

### 6.5 页面形态

- Settings 是 profile/appearance/addresses 三个可深链 section，不拆成新顶级路由。
- IM 保留会话列表页与聚焦线程页两个路由，不改成桌面双栏。
- Drive 使用列表优先工作区：工具栏、状态路径面包屑、文件夹/文件行和上传进度；不增加画廊模式。
- Wallet 使用余额摘要、操作和加载更多流水；不做图表仪表盘。
- Market 使用商品卡片目录、详情页和域内管理 Tabs；只有库存/订单等横向比较场景使用裁剪 UiTable。
- Posts、Bookmarks、Search、Notices 和关系列表共享 8px 扁平列表语言与状态规则，但保留各自内容结构，不制造卡片套卡片。

## 7. 迁移波次计划

所有迁移波次必须通过第 8 节通用硬门槛。一个波次未通过时，不得以更新截图基线或降低断言阈值进入下一波次。

| 迁移波次 | 页面与工作 | 波次出口 |
| --- | --- | --- |
| 0. 视觉基线预检 | 固定 Playwright/Chromium 与 Linux/Noto CJK 环境；增加独立 `08-visual.spec.ts`、`@visual` 和 `test:visual`；在令牌改动前生成现状基线 | 同一环境连续两次稳定；约 20 张 PNG 可审查；动态内容已固定或 mask；不得靠提高全局阈值消噪 |
| 1. 基础层 | 令牌、主题三态、密度、首批六原语、Lucide、scoped CSS 规则、产品壳层、导航/搜索/账户区、认证页、403/404；Settings 先交付 appearance/addresses 与旧地址重定向 | 明暗/compact 令牌、壳层匿名/登录/角色态、Modal 键盘、认证流程和地址兼容入口通过；未迁移页无意外 diff |
| 2. PostsView 试点 | 只迁 `PostsView`；交付 UiTabs/UiDropdown；过滤、最新/最热、分类/标签入口、composer、加载更多和 scoped CSS | 帖子首屏、筛选、排序、发帖、点赞、未读定位与键盘打开通过；Posts 明暗截图获批；不提前迁详情 |
| 3. 社区完善 | `PostDetailView`、评论/回复组件、`BookmarksView`；返回层级、分享、评论举报、更多动作和深链 | 详情/评论/收藏链路、追加分页、骨架/空/错态通过；PostDetail 双主题与 Bookmarks 亮色截图获批 |
| 4. 搜索 | `SearchView`；交付 UiSelect；壳搜索往返、分类/tag 筛选、追加式结果和暗色泄漏清理 | 搜索键盘选择、清除、最终一致性说明、加载更多与 Playwright 流程通过；亮色截图获批 |
| 5. Account | 完成 `SettingsView`，迁 `UserProfileView` 与关注/粉丝列表 | 三 section 深链、资料/头像、主题/密度、地址、本人/他人状态与关系列表加载更多通过；Settings/Profile 基线获批 |
| 6. Inbox | `NoticesView`、`NoticeDetailView`、`ConversationsView`、`ConversationDetailView`；导航角标、已读与 IM toast | 角标、已读、会话列表、固定线程、pending/failed/committed、重连 backfill 和无虚假点击 toast 通过；补 IM detail E2E |
| 7. Drive | `DriveView`、`DriveShareView`；列表工作区、上传、分享、回收站和确认弹窗；删除 `var(--muted, #667085)` 与链接色 hex fallback，分别使用 `--text-3` / `--muted` 和 `--link-color` | breadcrumb、上传进度/取消、分享提取、移动/删除状态通过；公开分享匿名可用；暗色无令牌泄漏 |
| 8. Wallet | `WalletView`；余额、操作和追加式流水 | 金额和 pending 语义不被视觉层推断；转账幂等保持；无仪表盘化；亮色截图获批 |
| 9. Market | 除已重定向地址外的全部市场路由；交付 UiTable；域内 tabs、页内搜索、表单与订单 | 虚拟/实物、买卖双方和订单 pending 可区分；资损动作和 E2E 通过；`pages.css` 退役；后台争议页只做等价 CSS 搬移 |
| 10. 全站收尾 | 退役 `components.css`；清理原语内部类、源代码手写 `<svg>`、空面包屑、裸 loading、消费流 UiPagination、非零字距和违规 px；补齐 handbook/守卫/截图矩阵 | 第 8–10 节一次通过；页面合同无未完成项；全局样式只剩 variables/base/layout/utils |

## 8. 通用硬验收门槛

### 8.1 视觉与布局

- 迁移页面必须在明暗主题和默认 compact 密度下验收；核心页面同时保留明暗截图基线。
- 正文与辅助文字满足 WCAG 2.1 AA 4.5:1；大字和关键 UI 边界满足 3:1。
- 无手写 `data-theme` 页面覆盖、无 `var()` hex fallback、无未定义颜色或 z-index。
- 桌面 1280px 和 1440px 无文字截断、控件跳动和遮挡；移动端在 390×844 和 768×1024 下无横向溢出或内容遮挡，导航、主要操作、表单与弹窗可用。本轮不为移动端增加截图基线或新信息架构。

### 8.2 交互与可访问性

- 所有本轮公开页面和新增原语都要做键盘通行检查；自动化覆盖原语、产品壳层和核心用户流，其他页面逐页人工审计。
- Tab 顺序与视觉顺序一致；focus ring 可见；弹窗关闭后焦点返回触发点。
- Select/Dropdown/Tabs/Dialog 的角色、名称、状态和键盘模型符合对应 ARIA APG 模式。
- loading、empty、error、disabled、pending 和 success 状态不会复用同一含混表现。
- tooltip 不承载完成操作所必需的唯一信息。

### 8.3 工程边界

- 迁移视图不直接使用原语内部类，不新增全局页面样式。
- 页面继续复用 `frontend/src/api/http.js` / `imCoreHttp.js`、runtime endpoint、session 和 `WriteAttempt` 语义。
- UI 迁移不得把请求生命周期、IM 协议、市场状态机或钱包事实搬回 Vue 模板。
- 行为变化有对应 Vitest 或 Playwright 回归；视觉变化有经审查的截图 diff。
- 每个页面簇落地时更新 handbook 和本波次覆盖的测试说明。

## 9. 视觉回归策略

- 在基础层改造前，先为迁移前页面生成基线；未迁移页面的变化视为泄漏。
- 在 `tests/playwright-single/tests/` 增加独立 `08-visual.spec.ts` 和 `@visual` 标签，复用现有登录、路由和错误审计 fixtures；所有 UI PR 都运行该套视觉检查。
- 增加 `test:visual` 命令，不改变现有 `@regression` 语义。
- 登录、Posts、PostDetail、Settings 保留明暗双主题；403、404、Bookmarks、Search、Profile、Notices、IM 列表、固定 IM 线程、Drive、DriveShare、Wallet、Market 列表/详情/订单保留亮色核心状态。其余暗色状态由人工 checklist 补足。
- 403/404、空状态和固定 seed 状态优先进入基线；动态时间、头像、计数和列表内容使用 `mask` 或 `stylePath` 固定。
- 基线只在与 CI 相同的固定 Ubuntu/Linux + Noto CJK 字体环境生成并提交；Playwright/Chromium 升级允许一次独立、可审查的全量刷新。
- `threshold` 保持默认 0.2；仅单页抗锯齿噪声可设置小额 `maxDiffPixels` 并说明原因。
- `--update-snapshots` 只用于有意视觉变化；CI 失败不得通过无审查刷新基线消除。

固定矩阵共 18 个视觉用例、22 张 PNG。截图管布局和样式，Vitest 管状态与交互，现有 Playwright regression 管跨页面工作流；三层不得互相替代。

## 10. 验证与交付

每个页面簇先运行相关定向 Vitest，再执行前端完整检查：

```bash
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
```

视觉测试落地后，从仓库根目录执行：

```bash
npm --prefix tests/playwright-single run typecheck
npm --prefix tests/playwright-single run test:visual
```

需要验证完整用户链路时，使用隔离的 `single` 拓扑运行现有 smoke/regression；不得为截图验证清空开发者本地数据。

文档改动至少执行：

```bash
git diff --check -- AGENTS.md README.md docs frontend/README.md backend/README.md deploy/README.md tools
```

本轮完成的定义是：第 7 节所有非管理路由完成迁移，第 8 节门槛全部通过，视觉基线在固定环境稳定，handbook 已更新为实际行为。此后本文转为设计历史；新的当前行为继续由 handbook 维护。
