# Task List: 前端视觉精修与遗留样式清理（未用 CSS 清理）

Directory: `.helloagents/archive/2026-01/202601251248_ui_visual_polish_css_cleanup/`

---

## 1. CSS 清理（frontend）
- [√] 1.1 清理未使用的旧样式入口/文件（例如历史 `styles.css`）：verify why.md#requirement-remove-unused-css
- [√] 1.2 清理全局样式中确认未引用的选择器（保守删除）：`frontend/src/styles/components.css`、`frontend/src/styles/pages.css`、`frontend/src/styles/layout.css`，verify why.md#requirement-remove-unused-css
- [√] 1.3 清理页面 scoped style 中未用规则/重复规则（保守删除）：`frontend/src/views/*`，verify why.md#requirement-remove-unused-css

## 2. 视觉精修（frontend）
- [√] 2.1 统一常见间距与对齐（PageHeader/Card/列表）：`frontend/src/styles/*`、关键 view，verify why.md#requirement-visual-polish
- [√] 2.2 校验深浅色与密度模式下的对比度与 hover/focus：`frontend/src/styles/variables.css`、相关组件，verify why.md#requirement-visual-polish

## 3. Security Check
- [√] 3.1 执行前端安全检查（重点：UiMarkdown v-html 白名单、链接协议、避免新增不受控渲染）：verify how.md#security-and-performance

## 4. Documentation Update（Knowledge Base）
- [√] 4.1 更新前端样式入口与清理约定：`.helloagents/modules/frontend.md`

## 5. Testing
- [√] 5.1 运行测试与构建：`npm -C frontend test`、`npm -C frontend run build`
