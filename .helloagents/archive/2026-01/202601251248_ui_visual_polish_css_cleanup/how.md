# Technical Design: 前端视觉精修与遗留样式清理（未用 CSS 清理）

## Technical Solution

### Core Technologies
- Vue 3 + Vite（沿用现状）
- CSS Variables（Design Tokens）：`frontend/src/styles/variables.css`
- 全局样式分层入口（SSOT）：`frontend/src/styles/index.css`
- 自研 UI 组件库：`frontend/src/components/ui/*`

### Implementation Key Points
- **视觉精修优先级：**
  - 可读性（对比度、行高、标题层级）优先于装饰性；
  - hover/focus 反馈“轻、稳、不位移”，符合内容优先论坛气质；
  - 移动端首先确保 padding 与点击可达性，再做细节美化。
- **未用 CSS 清理策略（保守）：**
  - 优先清理“确认无引用”的 CSS 文件与选择器；
  - 对可能通过动态拼接/条件 class 使用的选择器，默认保留；
  - scoped 样式仅保留组件特有部分，通用模式沉淀到 `styles/pages.css` 或 `styles/components.css`。
- **单一入口约束：**
  - 明确并坚持 `frontend/src/styles/index.css` 是唯一全局样式入口；
  - 清理可能造成误导的旧入口/旧文件，避免未来重复引入。

## Security and Performance
- **Security:**
  - 不新增不受控的 `v-html` 入口；富文本仍统一通过 `UiMarkdown`；
  - 不新增敏感信息持久化（沿用既有 auth/ui store 策略）。
- **Performance:**
  - 清理未用 CSS 减少样式体积与维护复杂度；
  - 避免引入第三方 UI 大依赖，保持构建体积稳定。

## Testing and Deployment
- **Testing:** `npm -C frontend test`
- **Deployment:** `npm -C frontend run build`（确保生产构建无报错）
- **Manual Check:** posts 列表/详情、登录/注册、通知、私信详情、设置页在 375px/768px/1024px 下目视回归

