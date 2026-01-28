# Technical Design: 前端 UI 内容优先全站一致性重构

## Technical Solution

### Core Technologies
- Vue 3 + Vite + Vue Router + Pinia（沿用现状）
- CSS Variables（`frontend/src/styles/variables.css`）作为 Design Tokens SSOT
- 自研轻量 UI 组件库（`frontend/src/components/ui/*`）作为一致性入口

### Implementation Key Points
- **设计系统落地（不换栈）：** 以现有 `styles/index.css` 分层为主（variables/base/utils/layout/components/pages），避免引入重量级 UI 框架导致样式与行为“二次碎片化”。
- **可读性优先：** 优先优化文本对比度与层级（页面标题、正文、辅助信息），并确保深浅色模式一致。
- **Markdown 渲染增强：** 在保持安全策略（先 escape，再白名单生成可控标签）的前提下，补齐段落语义与列表结构，优化正文（default）与评论（compact）的排版差异。
- **全站一致性改造：** 以 “UiPageHeader + UiCard + .page 容器” 作为页面骨架；逐个视图补齐标题/动作区/空加载态的一致模式。
- **移动端与密度：** 继续支持 `html[data-density]`（compact/comfortable），并确保移动端导航与内容区 padding 不冲突。

## Security and Performance
- **Security:**
  - Markdown 渲染继续采用“先转义 → 再可控生成标签”的策略，链接协议仅放行 http/https/mailto/tel 与相对路径。
  - 避免新增 `v-html` 的不受控入口；所有富文本渲染统一走 `UiMarkdown`。
- **Performance:**
  - 不引入大型 UI 库；样式尽量集中在现有分层文件内，减少 scoped style 的重复规则。
  - 动画保持短、少、克制（150–300ms），避免影响滚动与输入响应。

## Testing and Deployment
- **Testing:**
  - 回归验证关键用户路径：Posts 列表 → PostDetail 阅读 → 评论/回复 → 登录/注册 → 搜索 → 私信。
  - 运行前端单测（如已有覆盖）：`npm -C frontend test`。
- **Deployment:**
  - 本地验证构建与预览：`npm -C frontend run build`、`npm -C frontend run preview`。

