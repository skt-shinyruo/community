# Task List: UI 全量等价（信息架构 + 组件库优先）

Directory: `helloagents/plan/202601171622_ui_rebuild_parity/`

---

## 1. Frontend IA 与组件库（先搭骨架）
- [√] 1.1 建立全局布局骨架（AppShell/导航/页面容器），并将现有页面迁移到新布局中：`frontend/src/App.vue`, verify why.md#requirement-信息架构与组件库-scenario-全局导航与路由分层
- [√] 1.2 建立内部组件库最小集合（Button/Input/Card/Modal/Pagination/Empty/Skeleton）：`frontend/src/components/ui/*`, verify why.md#requirement-信息架构与组件库-scenario-统一交互与错误处理
- [√] 1.3 统一 API Result 解析与错误处理（code!=0 视为业务错误 + traceId 透传）：`frontend/src/api/*`, verify why.md#requirement-信息架构与组件库-scenario-统一交互与错误处理
- [√] 1.4 路由分层与权限 guard（requiresAuth/roles + 403/404 页面）：`frontend/src/router/*`, verify why.md#requirement-信息架构与组件库-scenario-全局导航与路由分层

## 2. Auth 页面等价（注册/激活/验证码/登录/登出）
- [√] 2.1 新增注册页（表单校验 + 调用 `/api/auth/register` + 成功提示）：`frontend/src/views/RegisterView.vue`, verify why.md#requirement-账号体系-ui-等价注册激活验证码登录登出-scenario-注册激活登录闭环
- [√] 2.2 新增激活结果页（解析 userId/code + 调用 `/api/auth/activation/...`）：`frontend/src/views/ActivationView.vue`, verify why.md#requirement-账号体系-ui-等价注册激活验证码登录登出-scenario-注册激活登录闭环
- [√] 2.3 登录页接入验证码（展示 `/api/auth/captcha` 图片 + 校验 `/api/auth/captcha/verify` + 刷新验证码 + redirect 回跳）：`frontend/src/views/LoginView.vue`, verify why.md#requirement-账号体系-ui-等价注册激活验证码登录登出-scenario-登录验证码
- [√] 2.4 统一登出体验（导航入口 + 清理登录态 + 跳转）：`frontend/src/App.vue`, verify why.md#requirement-账号体系-ui-等价注册激活验证码登录登出-scenario-注册激活登录闭环

## 3. 帖子/评论/回复页面等价（含点赞）
- [√] 3.1 帖子列表页对齐信息密度（作者摘要/点赞数/更好的空状态与分页）：`frontend/src/views/PostsView.vue`, verify why.md#requirement-帖子评论回复-ui-等价含点赞-scenario-首页帖子列表
- [√] 3.2 帖子详情页补齐评论点赞与回复树展示（评论分页、展开回复、回复评论）：`frontend/src/views/PostDetailView.vue`, verify why.md#requirement-帖子评论回复-ui-等价含点赞-scenario-帖子详情评论--回复树
- [√] 3.3 评论/回复点赞能力补齐（entityType=2）：`frontend/src/views/PostDetailView.vue`, verify why.md#requirement-帖子评论回复-ui-等价含点赞-scenario-帖子详情评论--回复树

## 4. 关注/粉丝列表页面等价
- [√] 4.1 新增关注列表页（followees，分页 + 用户摘要 + hasFollowed）：`frontend/src/views/FolloweesView.vue`, verify why.md#requirement-关注粉丝-ui-等价-scenario-关注粉丝列表页
- [√] 4.2 新增粉丝列表页（followers，分页 + 用户摘要 + hasFollowed）：`frontend/src/views/FollowersView.vue`, verify why.md#requirement-关注粉丝-ui-等价-scenario-关注粉丝列表页

## 5. 私信页面等价
- [√] 5.1 新增会话列表页（聚合字段 + 对端用户信息 + 未读数）：`frontend/src/views/ConversationsView.vue`, verify why.md#requirement-私信-ui-等价-scenario-会话列表与私信详情
- [√] 5.2 新增私信详情页（分页加载 + 发送私信 + 已读标记）：`frontend/src/views/ConversationDetailView.vue`, verify why.md#requirement-私信-ui-等价-scenario-会话列表与私信详情

## 6. 通知页面等价
- [√] 6.1 新增通知汇总页（topic summary + 未读）：`frontend/src/views/NoticesView.vue`, verify why.md#requirement-通知-ui-等价-scenario-通知汇总与详情
- [√] 6.2 新增通知详情页（topic=comment/like/follow + 分页 + 标记已读）：`frontend/src/views/NoticeDetailView.vue`, verify why.md#requirement-通知-ui-等价-scenario-通知汇总与详情

## 7. 搜索页面等价
- [√] 7.1 新增搜索页（keyword + 分页 + 高亮展示 + 跳转帖子详情）：`frontend/src/views/SearchView.vue`, verify why.md#requirement-搜索-ui-等价-scenario-搜索与高亮
- [√] 7.2 （可选）ADMIN 可见 reindex 按钮并提供确认弹窗：`frontend/src/views/SearchView.vue`, verify why.md#requirement-搜索-ui-等价-scenario-搜索与高亮

## 8. 统计页面等价（权限）
- [√] 8.1 新增统计页（日期区间查询 UV/DAU + 权限提示）：`frontend/src/views/AnalyticsView.vue`, verify why.md#requirement-统计-ui-等价权限-scenario-uvdau-查询

## 9. 审核管理页面/入口等价（权限）
- [√] 9.1 帖子详情页或管理页补齐置顶/加精/删除入口（二次确认 + 刷新状态）：`frontend/src/views/PostDetailView.vue`, verify why.md#requirement-审核管理-ui-等价权限-scenario-帖子置顶加精删除

## 10. （推荐）为 UI 等价补齐聚合 API（避免 N+1）
- [-] 10.1 帖子列表返回 author/likeCount（新增 DTO 或扩展返回）：`content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`, verify how.md#api-designui-等价所需的接口清单与潜在缺口
  - Note: 现阶段由前端按需聚合（带轻量缓存），避免跨服务耦合；如需性能优化再补齐聚合 DTO。
- [-] 10.2 评论/回复聚合接口（返回 CommentVO：用户摘要 + 点赞信息 + 回复树）：`content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`, verify how.md#api-designui-等价所需的接口清单与潜在缺口
  - Note: 同上（前端聚合 + 缓存）；后续可按性能瓶颈再内聚。
- [-] 10.3 followees/followers 列表项补齐用户摘要与 hasFollowed（新增 DTO 或新接口）：`social-service/src/main/java/com/nowcoder/community/social/follow/FollowController.java`, verify how.md#api-designui-等价所需的接口清单与潜在缺口
  - Note: 列表项目前由前端拉取 userProfile + followStatus 补齐（可用但存在 N+1），后续可优化为批量/聚合接口。

## 11. Security Check
- [√] 11.1 执行安全检查：权限边界/输入校验/敏感信息处理（参考 `scripts/security-check.sh`）

## 12. Documentation Update（知识库同步）
- [√] 12.1 更新 `helloagents/wiki/api.md`：补充 UI 依赖 API 与权限说明（ADMIN/MODERATOR）
- [√] 12.2 新增/更新 `helloagents/wiki/modules/frontend.md`：记录前端 IA、组件库与路由规范

## 13. Testing（门禁）
- [√] 13.1 增补前端 vitest：Result 解析/权限判断/路由守卫（`frontend/src/api/*` 或 `frontend/src/router/*`）
- [-] 13.2 增加 UI 自动化回归（Playwright browser）：覆盖核心用户路径，并接入 CI（`.github/workflows/ci.yml`）
  - Note: UI browser 自动化会受到验证码与浏览器依赖下载成本影响，建议在明确门禁策略后单独引入。
