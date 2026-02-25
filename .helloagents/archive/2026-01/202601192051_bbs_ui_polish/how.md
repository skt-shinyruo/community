# Technical Design: BBS 体验增强（信息架构 / 列表扫读 / 详情与评论树 / 治理与账号 / 体验底盘）

## Technical Solution

### Core Technologies
- Vue3 + Vue Router + Pinia
- CSS Variables（Design Tokens）+ 分层样式（`frontend/src/styles/*`）
- 内置 UI 组件库（`frontend/src/components/ui/*`）
- 测试与门禁：Vitest + Vite build

### Implementation Key Points（按 5 点拆解）

#### 1) 信息架构与导航：建立“导航配置 SSOT”
目标：避免 Sidebar/Topbar/页面内部出现多套标题/入口/选中态逻辑。

实现建议：
- 在 `frontend/src/router/` 下新增 `nav.js`（或 `navigation.js`），维护：
  - sidebar 导航项、分组、权限门槛
  - posts 页筛选项（最新/最热/置顶/精华/我的关注等）与 query 映射
- `SidebarNav.vue` 与（可选）移动端底部导航都从同一配置渲染（避免重复硬编码）。
- 路由 `meta.title/subtitle` 继续作为 Topbar 标题/副标题 SSOT；面包屑可以从 route meta + 配置推导。

#### 2) 列表扫读体验：统一 Feed Toolbar + Post Card 结构
目标：列表信息层级固定、可视化筛选明确、交互热区稳定。

实现建议：
- 新增 `UiChips` / `UiChip` 或 `UiSegmented` 组件，统一“筛选 chips”风格。
- posts 页引入 `FeedToolbar`（可作为页面组件或通用组件）：
  - 排序（最新/最热）
  - 筛选（置顶/精华等）以 chips 展示，并提供“一键清空”
- Post Card 结构统一沉淀到 `frontend/src/styles/pages.css`（现有已具备基础），页面只负责数据与事件绑定。
- 性能热点（UI 侧）：
  - posts 列表的作者信息与点赞数尽量减少 N+1 请求（例如：缓存、批量加载策略、或降低首屏请求量）。
  - 若后端可配合：建议 posts 列表接口直接返回 author 摘要与 likeCount，减少前端补水请求。

#### 3) 详情与评论树：可理解 + 可定位 + 可引用（最小闭环）
目标：讨论体验“像 BBS”，而不是“像评论区”。

实现建议：
- 阅读模式：
  - 统一正文排版与代码块样式（`UiMarkdown` 与页面 reading 宽度对齐）。
- 评论树：
  - 折叠/展开：为每条评论/回复提供折叠能力（默认展开一层，深层可折叠）。
  - 定位：支持通过 hash 或 query 定位某条评论（滚动到元素并高亮）。
  - 引用回复（最小闭环）：点击“回复”时将被回复内容的摘要以引用样式插入编辑器或展示为引用块，并在提交时带上 targetId（若后端已有字段支持）。
- 编辑体验：
  - 草稿：对“发表评论/回复”提供本地草稿（localStorage key 按 postId 分开），降低误操作丢内容成本。

#### 4) 治理与账号：一致的身份标识与危险操作
实现建议：
- 统一用户身份徽章（ADMIN/MOD 等）与展示位置（UserCard、PostHeader、Profile）。
- 危险操作统一 `UiModalConfirm` 的 confirmVariant（danger）与文案模板（删除/置顶/加精）。
- 预留举报/屏蔽入口：
  - 若后端未提供 API，可先放 UI 入口与占位（disabled + “即将上线”），避免后续改动破坏布局。

#### 5) 体验底盘：一致的状态组件 + 可访问性 + 移动端
实现建议：
- 统一状态呈现：
  - `UiEmpty` 类型化（data/search/error）
  - 列表 loading skeleton / error block 统一结构与样式
- 可访问性最低标准：
  - Icon buttons 必须有 `aria-label`
  - focus-visible 必须可见且不刺眼（已在 base/components 层处理，可继续补齐遗漏点）
- 移动端：
  - Sidebar 抽屉行为一致（遮罩、点击关闭、点击导航自动收起）。
  - 可选：新增移动端底部导航（“帖子/搜索/消息/我的”），进一步提升可达性（若不做，可在 Sidebar 的移动端状态强化）。

## Architecture Decision ADR

### ADR-001: 继续强化内部 UI 系统（Recommended）
**Context:** 项目已有 Design Tokens 与基础 UI 组件库，且 UI 优化需要跨页面一致性与可演进性。  
**Decision:** 优先补齐 tokens + 组件库 + 导航配置 SSOT，不引入完整 UI 组件库做基础替换。  
**Rationale:** 降低迁移成本与样式冲突风险，保证可控的设计系统演进。  
**Alternatives:** 引入完整 UI 库（迁移面大、主题系统冲突、包体与依赖风险更高）。  

### ADR-002: “板块/分区”能力 UI 先行，后端能力作为可选增强
**Context:** BBS 的板块是强需求，但现有 posts API 默认不包含真实 board 维度。  
**Decision:** 本方案优先落地 UI 层的“筛选/组织”能力（置顶/精华/排序/搜索），板块能力以“后端新增字段/接口”作为后续可选增强。  
**Rationale:** 先提升体验一致性与可用性，不因数据模型扩展阻塞 UI 价值交付。  

## Security and Performance
- **Security:**
  - 重点关注 `v-html` 渲染点（搜索高亮、Markdown 渲染）与外链跳转策略，避免 XSS。
  - 不新增敏感信息持久化；草稿仅保存用户输入内容，按 postId 命名空间隔离。
- **Performance:**
  - 治理列表 N+1 请求：优先 cache/延迟补水；如可配合后端则推动接口返回聚合字段。
  - 控制动画与阴影数量，避免影响滚动与输入响应。

## Testing and Deployment
- **Testing:**
  - 保持现有 Vitest 门禁；新增与 UI 无关的纯函数/路由配置测试（例如 nav 配置与筛选映射）。
  - UI 组件渲染类测试如需引入 Vue Test Utils，建议作为独立依赖变更方案单独评估。
- **Deployment:**
  - 保持现有 Vite build + preview 与 compose 联调方式不变；更新部署文档中的 UI/样式与静态资源说明（如有新增约定）。
