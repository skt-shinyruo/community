# Technical Design: UI 系统化优化（视觉一致性 / 导航信息架构 / 交互易用性）

## Technical Solution

### Core Technologies
- Frontend: Vite + Vue3 + Vue Router + Pinia
- Styles: CSS Variables（design tokens）+ 分层 CSS（`frontend/src/styles/*`）
- Components: 内部 UI 组件库（`frontend/src/components/ui/*`）与布局骨架（`frontend/src/components/layout/*`）
- Testing: Vitest（`npm -C frontend test`）

### Implementation Key Points

#### 1) Design Tokens 与样式分层（SSOT 收敛）
- 以 `frontend/src/styles/variables.css` 作为 tokens SSOT，补齐交互态（hover/active/disabled/focus-visible）与语义色（success/warning/危险/弱化背景）。
- 统一“样式层级”约定并逐步迁移：
  1. `variables.css`：tokens（只放变量）
  2. `base.css`：reset + 全局基础（字体、背景、a/button/input 默认行为、滚动条等）
  3. `utils.css`：通用工具类（间距/布局/文本）
  4. `components.css`：可复用组件样式（卡片/按钮/输入/分页/弹窗/空态等）
  5. `layout.css`：布局骨架（AppShell/Sidebar/Topbar/RightPanel/AuthShell）与响应式规则
  6. `pages.css`（如有需要）：页面通用结构（page header、toolbar、filters、list item 等，不写具体业务）
- 逐步减少页面内联样式与 scoped 样式，优先迁移到可复用 class（降低“每个页面一套样式”的碎片化风险）。

#### 2) 导航信息架构：route meta 作为标题/面包屑/导航归属 SSOT
- 在 `frontend/src/router/index.js` 为路由补齐 meta：
  - `meta.title`：页面标题（Topbar 使用）
  - `meta.subtitle`：副标题（可选，Topbar 或 PageHeader 使用）
  - `meta.navGroup`：侧边栏分组归属（用于高亮或未来信息架构演进）
  - `meta.requiresAuth / meta.roles` 继续保留现有约定
- `Topbar.vue` 与 `SidebarNav.vue` 尽量不再硬编码页面名称，改为“以路由 meta/配置驱动”，减少未来新增页面的维护成本。

#### 3) 组件库补齐：统一交互与反馈
- 统一按钮/输入/表单错误/Toast/ModalConfirm 的交互细节：
  - hover/active/focus-visible/disabled/loading 的一致性（视觉与行为）
  - 统一错误提示文案区域与样式（避免页面各写各的红字块）
- 统一“空态/加载态/错误态”组件化落地：
  - 列表页：skeleton → data → empty → error 的展示顺序与视觉风格统一
  - 详情页：reading 宽度 + 章节层级 + 操作区一致

#### 4) 移动端与可访问性（最低标准）
- 移动端侧边栏抽屉与遮罩行为统一：
  - 打开抽屉时遮罩出现，可点击关闭
  - 点击导航项后按约定关闭（登录态/页面类型可做少量例外，但需一致）
- 最低可访问性改造：
  - 关键 icon button 添加 `aria-label`
  - 统一 focus-visible（键盘可见、鼠标不刺眼）

#### 5) 部署与静态资源策略
- 样式入口保持单一：继续以 `frontend/src/styles/index.css` 为入口，必要时新增 `pages.css` 等按职责拆分后在入口集中 import。
- 部署侧保持 Vite build 的 hash 资源特性；在 `deploy/README.md` 中补齐“缓存/排查/Origin 注意事项”的说明（必要时评估是否将 preview 切换为更标准的静态服务器，但默认不作为本次必做项）。

## Architecture Decision ADR

### ADR-001: 保持内部设计系统，不引入完整 UI 组件库（Recommended）
**Context:** 本次目标是“系统化 UI/UX 优化”，范围覆盖多页面与导航；项目已具备 tokens 与内部组件雏形，但页面仍存在大量内联/碎片化样式。  
**Decision:** 以现有 design tokens + 内部组件库为核心，逐步迁移与补齐；不引入完整 UI 组件库作为基础设施替换。  
**Rationale:**  
- 最小化依赖与迁移成本，降低一次性重构风险与 bundle 体积不可控风险。  
- 现有 tokens/组件已能覆盖大部分基础控件需求，缺口可通过补齐内部组件解决。  
- 更利于形成项目自己的“可演进 UI 规范”，避免被外部库的主题系统锁死。  
**Alternatives:**  
- 引入完整 UI 库（Element Plus/Naive UI）→ 迁移面大、主题定制与现有样式冲突风险高。  
- 引入 Utility CSS（Tailwind/UnoCSS）→ 一致性提升但需要全局迁移策略与大量 class 改写，阶段成本更高。  
**Impact:**  
- 需要投入一定组件化与样式迁移工作量，但可在每个阶段完成后形成稳定收益。  
- 后续若要引入图标库，可作为独立 ADR 追加，不与本次主目标强绑定。

## Security and Performance
- **Security:**
  - 避免引入未经审计的第三方 UI 大依赖；如引入图标库，优先选择轻量、tree-shakable 方案。
  - 对 `UiMarkdown` 等可能渲染富文本的组件保持警惕，避免 XSS（必要时增加 sanitize 或限制渲染能力）。
  - 不在前端新增敏感信息持久化（保持既有 token 策略不变）。
- **Performance:**
  - 减少重复 CSS 与页面级样式分叉，降低样式计算与维护成本。
  - 优先使用内联 SVG（现有模式）或 tree-shaking 图标，避免一次性打包整套图标资源。
  - 动画保持短、少，避免影响滚动与输入响应。

## Testing and Deployment
- **Testing:**
  - 扩展/补齐 Vitest：覆盖路由 meta 标题映射、UI store（theme/density/sidebar）、关键 UI 组件行为（button/input/modal/toast）。
  - 为核心页面补齐最小交互回归点（例如：PostsView 的排序切换与 reload、Topbar 搜索提交行为）。
- **Deployment:**
  - 本地验证：`npm -C frontend run build` 与 `npm -C frontend run preview`。
  - Compose 联调仍按 `deploy/README.md` 推荐方式（前端直连 gateway：12881/12882）。
