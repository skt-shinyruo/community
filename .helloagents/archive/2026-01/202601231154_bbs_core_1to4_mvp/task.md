# Task List: BBS 核心运营能力（治理 + 内容生命周期 + 收藏订阅 + 成长体系）

Directory: `.helloagents/archive/2026-01/202601231154_bbs_core_1to4_mvp/`

---

## 1. 举报与治理闭环（content-service / message-service / gateway）
- [√] 1.1 数据模型：为举报/处置新增表结构（report/moderation_action）并补齐索引与幂等策略，verify why.md#requirement-bbs-mod-101-report--review-loop, why.md#scenario-bbs-mod-101-3-audit--traceability
  - Files: `deploy/mysql-init/020_schema_content.sql`
- [√] 1.2 content-service：新增举报实体与 Mapper（report），verify why.md#scenario-bbs-mod-101-1-report-postcommentuser
  - Files: `content-service/src/main/java/com/nowcoder/community/content/entity/Report.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/dao/ReportMapper.java`
- [√] 1.3 content-service：新增 report MyBatis XML 与基础查询（分页/按状态过滤），verify why.md#scenario-bbs-mod-101-2-moderator-review--actions
  - Files: `content-service/src/main/resources/mapper/report-mapper.xml`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/ReportService.java`
- [√] 1.4 content-service：新增处置实体与 Mapper（moderation_action），verify why.md#scenario-bbs-mod-101-3-audit--traceability
  - Files: `content-service/src/main/java/com/nowcoder/community/content/entity/ModerationAction.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/dao/ModerationActionMapper.java`
- [√] 1.5 content-service：新增 moderation_action MyBatis XML 与审计查询，verify why.md#scenario-bbs-mod-101-3-audit--traceability
  - Files: `content-service/src/main/resources/mapper/moderationaction-mapper.xml`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/ModerationAuditService.java`
- [√] 1.6 content-service：新增举报 API（提交举报），verify why.md#scenario-bbs-mod-101-1-report-postcommentuser
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/ReportController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/dto/CreateReportRequest.java`
- [√] 1.7 gateway：增加治理 API 的路由与角色限制（/api/moderation/** 仅 MOD/ADMIN），verify why.md#scenario-bbs-mod-101-2-moderator-review--actions
  - Files: `gateway/src/main/resources/application.yml`
  - Files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`
- [√] 1.8 content-service：新增治理后台 API（举报列表/处置动作），verify why.md#scenario-bbs-mod-101-2-moderator-review--actions
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/ModerationController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/ModerationService.java`
- [√] 1.9 message-service：扩展通知消费，支持 moderation 通知 topic（或扩展现有 topic），verify why.md#scenario-bbs-mod-101-2-moderator-review--actions
  - Files: `common/src/main/java/com/nowcoder/community/common/event/EventTypes.java`
  - Files: `message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventProcessor.java`
- [√] 1.10 deploy：如引入新 topic（moderation/points），补齐 Kafka init 与 DLQ，verify why.md#scenario-bbs-mod-101-2-moderator-review--actions
  - Files: `deploy/docker-compose.yml`
  - Files: `common/src/main/java/com/nowcoder/community/common/event/EventTopics.java`

## 2. 拉黑/反骚扰（social-service / content-service / message-service / frontend）
- [√] 2.1 social-service：新增拉黑 API（block/unblock/list），verify why.md#requirement-bbs-mod-102-blocklist--anti-harassment, why.md#scenario-bbs-mod-102-1-block-rules
  - Files: `social-service/src/main/java/com/nowcoder/community/social/block/BlockController.java`
  - Files: `social-service/src/main/java/com/nowcoder/community/social/block/BlockService.java`
- [√] 2.2 social-service：新增 Redis 存储实现与 key 约定，verify why.md#scenario-bbs-mod-102-1-block-rules
  - Files: `social-service/src/main/java/com/nowcoder/community/social/block/RedisBlockRepository.java`
  - Files: `social-service/src/main/java/com/nowcoder/community/social/block/BlockRepository.java`
- [√] 2.3 content-service：发帖/评论前增加“拉黑约束”（被拉黑方不能对对方互动），verify why.md#scenario-bbs-mod-102-1-block-rules
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- [√] 2.4 message-service：发送私信前增加“拉黑约束”，verify why.md#scenario-bbs-mod-102-1-block-rules
  - Files: `message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
  - Files: `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`

## 3. 内容生命周期（content-service / frontend）
- [√] 3.1 数据模型：为帖子/评论增加 update_time/edit_count（软删字段按最小需要取舍），verify why.md#requirement-bbs-cont-201-edit--soft-delete
  - Files: `deploy/mysql-init/020_schema_content.sql`
- [√] 3.2 content-service：实现帖子编辑 API（24h 窗口）与“已编辑”字段回传，verify why.md#scenario-bbs-cont-201-1-edit-post-within-24h
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- [√] 3.3 content-service：实现评论编辑 API（15min 窗口），verify why.md#scenario-bbs-cont-201-2-edit-comment-within-15m
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/CommentController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- [√] 3.4 content-service：作者软删帖子（不影响管理员 delete 接口），verify why.md#scenario-bbs-cont-201-3-soft-delete-and-origin-tracking
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - Files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`

## 4. 收藏与订阅（content-service / frontend）
- [√] 4.1 数据模型：新增 post_bookmark/user_subscription_* 表与索引，verify why.md#requirement-bbs-book-301-bookmark-posts, why.md#requirement-bbs-sub-302-subscribe-taxonomy
  - Files: `deploy/mysql-init/020_schema_content.sql`
- [√] 4.2 content-service：收藏 API（bookmark toggle + list），verify why.md#scenario-bbs-book-301-1-bookmark-toggle--status, why.md#scenario-bbs-book-301-2-my-bookmarks-list
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/BookmarkController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/BookmarkService.java`
- [√] 4.3 content-service：帖子详情扩展收藏状态字段，verify why.md#scenario-bbs-book-301-1-bookmark-toggle--status
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/BookmarkService.java`
- [√] 4.4 content-service：订阅分类 API（subscribe/unsubscribe/list），verify why.md#scenario-bbs-sub-302-1-subscribe-categories
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/SubscriptionController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/SubscriptionService.java`
- [√] 4.5 content-service：订阅筛选（posts list 增加 subscribed=true 过滤），verify why.md#scenario-bbs-sub-302-3-filter-by-subscriptions
  - Files: `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - Files: `content-service/src/main/java/com/nowcoder/community/content/service/PostService.java`

## 5. 成长体系（user-service / common / frontend）
- [√] 5.1 数据模型：user 增加 score/mute_until/ban_until，新增 user_score_log 表与索引，verify why.md#requirement-bbs-grow-401-points--level
  - Files: `deploy/mysql-init/010_schema_identity.sql`
  - Files: `deploy/mysql-init/015_schema_growth.sql`
- [√] 5.2 user-service：积分与等级的读模型（profile response 扩展或 stats API），verify why.md#scenario-bbs-grow-401-2-profile-shows-score--level
  - Files: `user-service/src/main/java/com/nowcoder/community/user/api/dto/UserProfileResponse.java`
  - Files: `user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`
- [√] 5.3 user-service：Kafka 消费 post/comment/like 事件并幂等记账（points consumer），verify why.md#scenario-bbs-grow-401-1-points-by-events-with-anti-abuse
  - Files: `user-service/src/main/java/com/nowcoder/community/user/kafka/PointsEventConsumer.java`
  - Files: `user-service/src/main/java/com/nowcoder/community/user/service/PointsService.java`
- [√] 5.4 user-service：榜单 API（top users by score），verify why.md#scenario-bbs-grow-401-3-leaderboard
  - Files: `user-service/src/main/java/com/nowcoder/community/user/api/LeaderboardController.java`
  - Files: `user-service/src/main/java/com/nowcoder/community/user/service/LeaderboardService.java`

## 6. 前端落地（frontend）
- [√] 6.1 举报入口与提交弹窗（帖子详情/用户卡片），verify why.md#scenario-bbs-mod-101-1-report-postcommentuser
  - Files: `frontend/src/views/PostDetailView.vue`
  - Files: `frontend/src/components/modals/ReportModal.vue`
- [√] 6.2 治理后台页面（仅 MOD/ADMIN 可见），verify why.md#scenario-bbs-mod-101-2-moderator-review--actions
  - Files: `frontend/src/views/ModerationView.vue`
  - Files: `frontend/src/router/index.js`
- [√] 6.3 拉黑/解除拉黑按钮与“已拉黑”状态展示（用户卡片/主页），verify why.md#scenario-bbs-mod-102-1-block-rules
  - Files: `frontend/src/components/ui/UiUserCard.vue`
  - Files: `frontend/src/api/services/blockService.js`
- [√] 6.4 帖子/评论编辑 UI（窗口内展示编辑入口与错误提示），verify why.md#scenario-bbs-cont-201-1-edit-post-within-24h, why.md#scenario-bbs-cont-201-2-edit-comment-within-15m
  - Files: `frontend/src/views/PostDetailView.vue`
  - Files: `frontend/src/components/modals/EditContentModal.vue`
- [√] 6.5 收藏按钮 + 我的收藏页，verify why.md#scenario-bbs-book-301-2-my-bookmarks-list
  - Files: `frontend/src/views/BookmarksView.vue`
  - Files: `frontend/src/router/index.js`
- [√] 6.6 订阅入口（分类/标签）+ “仅看订阅”筛选，verify why.md#scenario-bbs-sub-302-1-subscribe-categories, why.md#scenario-bbs-sub-302-3-filter-by-subscriptions
  - Files: `frontend/src/views/PostsView.vue`
  - Files: `frontend/src/api/services/subscriptionService.js`
- [√] 6.7 个人主页展示积分/等级 + 榜单页入口，verify why.md#scenario-bbs-grow-401-2-profile-shows-score--level, why.md#scenario-bbs-grow-401-3-leaderboard
  - Files: `frontend/src/views/UserProfileView.vue`
  - Files: `frontend/src/views/LeaderboardView.vue`

## 7. Security Check
- [√] 7.1 执行安全检查（per G9）：权限边界（治理/删除/编辑/处罚）、输入校验（举报与编辑内容）、事件幂等与敏感信息不落库明文、避免新增明文密钥

## 8. Documentation Update（Knowledge Base）
- [√] 8.1 更新 `.helloagents/api.md`：补齐新增 API（reports/moderation/blocks/bookmarks/subscriptions/leaderboard）
- [√] 8.2 更新 `.helloagents/modules/content.md`：补齐内容域新增表与接口约定
- [√] 8.3 更新 `.helloagents/modules/user.md`：补齐积分/处罚状态与事件消费约定
- [√] 8.4 更新 `.helloagents/modules/social.md`：补齐拉黑关系 API 与 Redis key 约定
- [√] 8.5 更新 `.helloagents/CHANGELOG.md`：记录本次“核心运营能力”变更点

## 9. Testing
- [√] 9.1 content-service：举报/处置/收藏/订阅/编辑窗口的集成测试（含权限与幂等）
- [√] 9.2 user-service：积分事件消费幂等测试 + 榜单查询测试
- [√] 9.3 frontend：关键页面最小测试（路由守卫 + API 封装 + 关键交互）
- [√] 9.4 质量门禁：`mvn test` + `npm -C frontend test` + `npm -C frontend run build` + docker compose 端到端联调自测
