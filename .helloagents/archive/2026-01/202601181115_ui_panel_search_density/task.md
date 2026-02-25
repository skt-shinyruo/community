# Task List: frontend UI 细节增强（右侧上下文面板 / 全局搜索联动 / 密度精细化）

Directory: `.helloagents/archive/2026-01/202601181115_ui_panel_search_density/`

---

## 1. frontend - 右侧上下文面板
- [√] 1.1 增加右侧上下文面板组件并接入 AppShell：`frontend/src/components/layout/RightPanel.vue`、`frontend/src/App.vue`，verify why.md#requirement-right-panel-scenario-toggle-right-panel
- [√] 1.2 AppShell 按开关决定是否渲染第三列：`frontend/src/components/layout/AppShell.vue`，verify why.md#requirement-right-panel-scenario-toggle-right-panel，depends on task 1.1
- [√] 1.3 UI store 增加 rightPanelOpen（含默认策略与持久化）：`frontend/src/stores/ui.js`，verify why.md#requirement-right-panel-scenario-toggle-right-panel

## 2. frontend - Topbar 与搜索联动
- [√] 2.1 Topbar watch `route.query.q` 并回填搜索输入：`frontend/src/components/layout/Topbar.vue`，verify why.md#requirement-global-search-sync-scenario-query-backfill

## 3. frontend - 密度精细化
- [√] 3.1 新增并应用 `--content-padding-x/y`、`--card-padding`，并在 compact 覆盖字号与 padding：`frontend/src/styles.css`，verify why.md#requirement-density-fine-tune-scenario-compact-vs-comfortable
- [√] 3.2 移动端隐藏右侧面板，避免第三列空白：`frontend/src/styles.css`，verify why.md#requirement-right-panel-scenario-mobile-hide

## 4. Documentation Update
- [√] 4.1 更新知识库 frontend 模块：`.helloagents/modules/frontend.md`
- [√] 4.2 更新变更记录：`.helloagents/CHANGELOG.md`

## 5. Testing
- [√] 5.1 执行前端单测与构建：`npm -C frontend test`、`npm -C frontend run build`
  > Note: 已通过 vitest（9 tests）与 vite build；仍建议在浏览器中人工验证面板开关与密度切换的实际观感。
