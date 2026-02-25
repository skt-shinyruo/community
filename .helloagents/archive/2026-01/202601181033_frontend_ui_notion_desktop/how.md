# Technical Design: frontend UI Notion 化重构（桌面优先）

## Technical Solution

### Core Technologies
- Vue 3 + Vite + Vue Router + Pinia（沿用现状）
- CSS Variables（Design Tokens）+ 轻量组件库（自研 `frontend/src/components/ui/*` 扩展）
- localStorage：仅用于 UI 偏好（theme/density/sidebar 展开）

### Implementation Key Points

1. **Design Tokens（SSOT）**
   - 在 `frontend/src/styles.css`（或拆分 `styles/tokens.css`）建立 tokens：
     - 颜色：bg/surface/text/muted/border/accent/semantic（success/warn/danger）
     - 字体：font family、字号 scale、字重、行高
     - 间距：spacing scale（4/8/12/16/20/24…）
     - 圆角与阴影：Notion 式轻量阴影（弹层）+ 细边框（卡片/输入）
   - 主题：通过 `html[data-theme="light|dark"]` 切换变量集。
   - 密度：通过 `html[data-density="compact|comfortable"]` 控制 spacing/line-height/控件高度。

2. **AppShell（三栏工作区骨架）**
   - 新增 `frontend/src/components/layout/*`：
     - `AppShell.vue`：整体布局容器（Sidebar / Topbar / Content / RightPanel）
     - `SidebarNav.vue`：路由入口、分组、折叠
     - `Topbar.vue`：全局搜索（Ctrl/Cmd+K 入口预留）、新建、用户菜单、主题/密度开关
     - `RightPanel.vue`（可选）：热榜、筛选、上下文信息（页面可按需启用）
   - 在 `frontend/src/App.vue` 中接入 AppShell，替代当前顶部按钮堆叠式导航。

3. **组件库补齐与一致化**
   - 在 `frontend/src/components/ui/*` 统一交互状态（hover/focus/disabled）与密度适配：
     - Button/Input/Textarea/Card/Modal/Pagination/Empty（已有，统一）
     - Badge/Tag/Avatar/Divider/Select（按页面需要新增）
   - 统一页面结构：
     - 页面标题区（Title + Subtitle + Actions）
     - 内容区（列表/详情/表单）
     - 空状态/错误状态（统一组件）

4. **页面改造策略（全量，但可渐进）**
   - 顺序建议：`PostsView` → `PostDetailView` → 用户域 → 消息通知域 → 搜索统计 → 设置认证 → 系统页
   - 每个页面目标：去除内联样式、采用 AppShell 区块、使用统一组件与 tokens、保持现有 API 调用与路由参数不变。

## Architecture Design

```mermaid
flowchart TD
  App[App.vue] --> Shell[AppShell]
  Shell --> Sidebar[SidebarNav]
  Shell --> Topbar[Topbar]
  Shell --> View[RouterView: views/*]
  Shell --> Right[RightPanel (optional)]
  View --> Api[api/services/*]
  View --> Store[stores/*]
  View --> Ui[components/ui/*]
```

## Architecture Decision ADR

### ADR-001: 采用 Token-based 自研 UI（不引入大型 UI 库）
**Context:** 目标是 Notion 气质（克制、排版优先）且要高信息密度。引入成熟 UI 库会带来默认视觉语言与大量覆盖成本，且易“像 UI 库”而非 Notion。  
**Decision:** 使用 CSS 变量 tokens + 自研轻量 UI 组件，保持依赖最小与风格可控。  
**Rationale:** 统一性强、可维护、可按密度/主题全局切换、避免样式覆盖地狱。  
**Alternatives:**
- 引入 Element Plus/Naive UI → 拒绝原因：主题覆盖成本高、默认风格强、难以做到 Notion 一致性。
- Tailwind 全站铺开 → 拒绝原因：class 密度高，长期可维护性需要更强的团队约束；本次优先自研 tokens + 组件化。
**Impact:** 需要在组件库上投入一定工作量，但可换取长期风格一致与更低的后续改动成本。

## API Design

无后端 API 变更；前端保持既有 `frontend/src/api/services/*` 调用不变。

## Data Model

无数据模型变更。

## Security and Performance

- **Security：**
  - UI 偏好仅存储 theme/density/sidebar 等，不存储 token/用户敏感信息：localStorage key 为 `community.ui`（见 `frontend/src/stores/ui.js` 与 `frontend/index.html` 初始化脚本）。
  - accessToken 仍仅保存在 Pinia 内存态（`frontend/src/stores/auth.js`），不会写入 localStorage。
  - 文本内容保持纯文本渲染（不引入 Markdown/HTML 直出）以避免 XSS 风险升级；搜索结果高亮仍走 `emOnlyHtml` 白名单（仅放行 `<em>`）。
- **Performance：**
  - tokens 基于 CSS 变量，主题切换不触发大规模重渲染（仅 CSS 生效）。
  - 组件与页面尽量避免深层 watch 与重复计算；分页/列表保持现有请求策略。

## Testing and Deployment

- **Testing：**
  - `npm -C frontend test`（单测/路由守卫等）
  - 冒烟清单：访问 `/posts`、`/posts/:id`、`/search`、`/messages`、`/notices`、`/settings`、`/auth/login`
- **Build：**
  - `npm -C frontend run build`
- **Regression Strategy：**
  - 每完成一批页面（2-4 个）就执行一次 build + 冒烟路由检查，避免一次性大爆炸。
