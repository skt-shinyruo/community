# Technical Design: frontend UI 细节增强（右侧上下文面板 / 全局搜索联动 / 密度精细化）

## Technical Solution

### Core Technologies
- Vue 3 + Vue Router + Pinia（沿用现状）
- CSS Variables（Design Tokens）扩展：新增用于密度精细化的 padding tokens

### Implementation Key Points

1. **右侧上下文面板（RightPanel）**
   - 新增 `frontend/src/components/layout/RightPanel.vue`，内容以“提示/快捷键/当前页面”为主，随路由变化自动更新。
   - AppShell 通过 slot 提供右侧内容，且仅当 `ui.rightPanelOpen=true` 时渲染第三列：
     - `frontend/src/components/layout/AppShell.vue`：`hasRight = slots.right && ui.rightPanelOpen`
   - 移动端通过 media query 隐藏 `.app-right` 并覆盖网格列定义，避免出现空白第三列。

2. **UI 偏好持久化**
   - `frontend/src/stores/ui.js` 新增 `rightPanelOpen`，写入 `localStorage: community.ui`，默认在宽屏（>=1200px）开启。
   - `frontend/src/components/layout/Topbar.vue` 增加一键开关按钮，允许用户随时隐藏/显示右侧面板。

3. **Topbar ↔ SearchView 联动细节**
   - 关键词 SSOT 仍为 URL query（`q`）。
   - Topbar 增加对 `route.query.q` 的 watch：路由切换后自动回填输入框，保证状态一致。
   - SearchView 保持“点击搜索即写入 query”，并在 mount/route query 变化时触发查询（已存在）。

4. **密度精细化（compact/comfortable）**
   - 在 `frontend/src/styles.css` 新增 tokens：
     - `--content-padding-x/y`：控制内容区 padding
     - `--card-padding`：控制卡片内边距
   - 在 `html[data-density='compact']` 下覆盖：
     - `--text-md/--text-lg/--text-xl`（字号层级）
     - `--content-padding-x/y`、`--card-padding`
     - 已有 `--control-height` 与 spacing scale
   - 通过 tokens 驱动 `.app-content` 与 `.card`，实现“密度变化全站一致生效”。

## Security and Performance
- **Security：**
  - 右侧面板仅展示提示信息与路由上下文，不展示敏感数据。
  - UI 偏好仍仅存储 theme/density/sidebar/rightPanelOpen，不存储 token/用户隐私信息。
- **Performance：**
  - 右侧面板为纯前端渲染，不新增额外 API 请求。
  - 密度切换仅影响 CSS 变量，切换开销极低。

## Testing and Deployment
- `npm -C frontend test`
- `npm -C frontend run build`
- 浏览器冒烟建议：posts/postDetail/search/messages/notices/settings（检查右侧面板开关、密度切换、Topbar 搜索回填）。
