# 任务清单：bbs_discourse_filters_panel（Lightweight Iteration）

目标：继续向 Discourse 靠拢，补齐“未读筛选/导航入口”和右侧面板的信息架构（快速筛选 + 标签/关键词）。

## Tasks

- [√] posts filter：新增 `未读`（URL query `type=unread`，仅登录可用）
- [√] sidebar：增加「未读」入口（筛选分组）
- [√] right panel：增加“快速筛选”与“热门标签/关键词”卡片（链接到 posts/search）
- [√] 测试：`npm -C frontend test` + `npm -C frontend run build`
