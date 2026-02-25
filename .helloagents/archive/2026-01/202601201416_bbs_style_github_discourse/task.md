# 任务清单：bbs_style_github_discourse（Lightweight Iteration）

目标：将 BBS 视觉风格收敛为「GitHub Discussions 的专业克制」+「Discourse 的侧栏信息架构」，并默认偏紧凑（PC 为主、可扫读、信息密度高）。

## Tasks

- [√] 默认密度调整为 compact（新用户首屏更紧凑；老用户不受影响）
- [√] 交互收敛：去“漂浮/抖动”效果（card/button/chips hover 不做位移）
- [√] 帖子列表改为「紧凑行 + 分隔线/浅底」的 topic list（替换 post-card-b/vote-column）
- [√] 搜索结果列表与帖子列表统一（同一 topic list 视觉与信息层级）
- [√] 文案与空态：列表空态提供 CTA（刷新/登录/发帖）
- [√] 质量验证：`npm -C frontend test` + `npm -C frontend run build`
