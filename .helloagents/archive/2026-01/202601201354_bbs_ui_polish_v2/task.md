# 任务清单：bbs_ui_polish_v2（Lightweight Iteration）

目标：解决“页面过于空/平、右侧栏与空状态显得廉价”的观感问题，在不引入 UI 框架的前提下提升层次、可读性与一致性。

## Tasks

- [√] 调整工作区三栏视觉分层（Sidebar/RightPanel 背景、分隔与阴影）
- [√] FeedToolbar 视觉升级（从 flat 变为 toolbar card，避免 hover 抖动）
- [√] 空状态组件 `UiEmpty` 视觉升级（卡片化、支持 description/actions slot）
- [√] `PostsView` 空态文案与 CTA（刷新/登录/发帖引导）
- [√] `RightPanel` 卡片化与交互细节（标题、列表 hover、footer 对齐）
- [√] 质量验证：`npm -C frontend test` + `npm -C frontend run build`
