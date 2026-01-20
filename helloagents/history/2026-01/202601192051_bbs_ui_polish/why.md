# Change Proposal: BBS 体验增强（信息架构 / 列表扫读 / 详情与评论树 / 治理与账号 / 体验底盘）

## Requirement Background

作为 BBS 项目，用户的核心路径通常是：
“浏览帖子列表 → 进入帖子详情阅读 → 参与评论/回复 → 回到列表继续浏览”，并在此过程中穿插“搜索、私信、通知、关注/取关、设置”等动作。

在此类产品中，UI/UX 的关键不只是“好看”，更是：
1) **信息架构与导航明确**（我在哪/我能去哪/如何返回）  
2) **扫读效率高**（列表一眼读懂、可控密度、状态清晰）  
3) **详情页阅读与评论树稳定**（阅读模式、回复关系可理解、操作可恢复）  
4) **治理能力可信**（管理员/版主动作明显且安全、举报/屏蔽等入口可扩展）  
5) **体验底盘一致**（空/错/加载态一致、可访问性与移动端体验达标、性能可接受）  

当前项目的前端已具备基础能力（Design Tokens、内置 UI 组件库、AppShell 布局、Toast 等），但仍需要围绕上述 5 个方面进一步系统化收敛与打磨，以便后续迭代在统一规范下稳定演进。

## Product Analysis

### Target Users and Scenarios
- **游客**：浏览帖子、搜索、查看用户主页。
- **登录用户**：发帖、评论/回复、点赞、私信、通知、关注/取关、设置。
- **管理员/版主**：管理操作（置顶/加精/删除）、统计页、搜索索引重建等。

### Core Pain Points（按 5 点归类）
1. **信息架构/导航**：跨页面的标题/面包屑/选中态不稳定会导致迷失；移动端抽屉行为不一致会导致“卡住/不知道怎么关”。
2. **列表扫读**：帖子卡片信息层级、可点击热区与状态标签不统一会降低扫读效率；筛选/排序入口不明显会让用户“找不到想看的内容”。
3. **详情与评论树**：阅读模式不稳定、回复关系不清晰、定位/引用能力不足会导致讨论体验下降。
4. **治理与账号**：身份/权限标识不足会削弱信任；危险操作缺少一致的确认与提示会提升误操作风险。
5. **底盘能力**：空/错/加载态与 Toast 机制不统一、focus 可见性不足、性能热点（如列表 N+1 请求）会影响体验与可维护性。

### Value Proposition and Success Metrics
- **价值主张**：用“规范化信息架构 + 组件化复用 + 关键路径体验打磨”的方式，把 BBS 前端体验从“能用”提升到“好用且一致”，并降低后续迭代 UI 维护成本。
- **验收指标（建议作为落地验收清单）**：
  1. 核心页面（posts、post detail、messages、search、settings、auth、profile）的标题/副标题/导航选中态一致，且移动端抽屉开关行为一致。
  2. 帖子列表卡片信息层级固定，筛选/排序入口清晰；同一组件在不同页面交互态（hover/active/focus/disabled）一致。
  3. 帖子详情具备稳定阅读排版；评论树可理解且支持基础定位/引用（至少提供引用回复的最小闭环）。
  4. 管理操作入口统一确认弹窗与危险态样式；管理员/版主标识一致。
  5. 全站空/错/加载态与 Toast 机制统一；前端 `test` 与 `build` 通过；列表页面性能热点得到缓解（最少减少不必要的 N+1 请求或加入缓存/延迟加载策略）。

### Humanistic Care
- 深色主题与高对比度场景可读性稳定；状态表达不只依赖颜色（徽章/图标/文案结合）。
- 保证键盘用户可用（focus-visible、aria-label、可触达的按钮语义）。
- 动效轻量且可预测，避免过度动画造成干扰或眩晕。

## Change Content（对应 5 点）
1. **信息架构与导航**：引入“导航配置 SSOT”（Sidebar/Topbar/移动端入口一致），并补齐面包屑/筛选 chips 的一致呈现。
2. **列表扫读体验**：统一帖子卡片结构、状态徽章、操作入口；将筛选（最新/最热/置顶/精华/我的关注等）做成可视化控件。
3. **详情与评论树**：阅读模式排版统一；评论树支持折叠/定位/引用回复（最小可用闭环）。
4. **治理与账号**：用户信息卡片与身份标识统一；危险操作弹窗统一文案与样式；预留举报/屏蔽入口的 UI 扩展位。
5. **体验底盘**：空/错/加载态与 Toast 完整收敛；移动端触控热区与抽屉行为达标；针对列表 N+1 请求做 UI 侧缓解或接口侧优化建议。

## Impact Scope
- **Modules:** `frontend`（主）| `helloagents/wiki`（同步规范与约定）
- **Files（示例）：**
  - `frontend/src/router/*`（meta + nav 配置）
  - `frontend/src/components/layout/*`（导航/移动端入口）
  - `frontend/src/components/ui/*`（chips/toolbar/empty/loading/confirm）
  - `frontend/src/views/*`（posts/detail/messages/search/settings/auth/profile）
  - `helloagents/wiki/modules/frontend.md`
- **APIs:** 以“前端侧可落地”为主；如要引入“板块/分区”真实能力，需要额外后端支持（本方案仅作为可选增强）。
- **Data:** 默认不改数据模型（除非选择启用“板块”能力扩展）。

## Core Scenarios

### Requirement: BBS-IA-001 Information Architecture & Navigation
**Module:** frontend

#### Scenario: BBS-IA-001-1 Cross-page navigation clarity
用户从 posts 列表进入帖子详情、再进入用户主页并返回：
- 页面标题、面包屑、侧边栏选中态一致且可预测。
- 筛选（最新/最热/置顶/精华等）在列表页可见且可一键清空。

#### Scenario: BBS-IA-001-2 Mobile drawer behavior
在移动端打开侧边栏抽屉：
- 遮罩出现、可点击关闭；点击导航项后按约定自动收起。
- 抽屉/页面滚动不互相干扰。

### Requirement: BBS-FEED-002 Feed Scannability
**Module:** frontend

#### Scenario: BBS-FEED-002-1 Consistent post card hierarchy
帖子卡片统一包含：标题、摘要、作者/时间、评论数/点赞数、状态徽章（置顶/精华）。
- 主点击热区清晰；快捷操作不抢主热区。

### Requirement: BBS-DETAIL-003 Post Detail & Comment Tree
**Module:** frontend

#### Scenario: BBS-DETAIL-003-1 Reading mode typography
帖子正文/Markdown/代码块排版一致，可读性稳定（行宽、行高、段间距一致）。

#### Scenario: BBS-DETAIL-003-2 Threading + quote-reply MVP
评论/回复树支持“定位/折叠/引用回复（最小闭环）”，用户可理解回复关系。

### Requirement: BBS-MOD-004 Identity & Moderation
**Module:** frontend

#### Scenario: BBS-MOD-004-1 Safe destructive actions
管理员/版主操作具备一致的确认弹窗与危险态样式；误触风险可控。

### Requirement: BBS-FOUNDATION-005 UX Foundation
**Module:** frontend

#### Scenario: BBS-FOUNDATION-005-1 Unified states & feedback
空态/错误态/加载态/Toast 表现一致，错误可恢复（重试/返回）。

## Risk Assessment
- **Risk:** UI/导航回归、移动端遗漏、评论树交互复杂带来的边界 bug、性能热点未治理导致体验下降。
- **Mitigation:** 分阶段落地（先规范/组件 → 再导航/列表 → 再详情/评论树 → 再治理/底盘）；每阶段跑 `npm -C frontend test` 与 `npm -C frontend run build` 作为最低门槛。
