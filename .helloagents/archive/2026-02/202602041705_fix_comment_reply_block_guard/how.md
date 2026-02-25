# Technical Design: fix_comment_reply_block_guard

## Technical Solution

### Core Technologies
- Java 17 / Spring Boot 3
- MyBatis（content-service / social-service 数据访问）
- 现有领域模型：Comment(entityType/entityId)、BlockRepository、LikeRepository、FollowRepository

### Implementation Key Points

#### 1) content-service：回复评论归属与层级校验（写路径 fail-closed）
- 修改 `CommentService.addComment(...)` 在 `entityType=COMMENT` 分支：
  1) 读取目标 comment（`commentMapper.selectCommentById(entityId)`）。
  2) 校验 comment 存在且 `status=0`。
  3) **归属校验：**要求目标 comment 必须为该帖子一级评论：`targetComment.entityType == POST` 且 `targetComment.entityId == postId`。
  4) 不满足则返回 404（`CommonErrorCode.NOT_FOUND`），避免通过错误信息形成“跨帖 comment 存在性侧信道”，同时阻断脏数据写入。
- 设计选择：统一为“仅支持对帖子一级评论回复”，与 `GET /api/posts/{postId}/comments/{commentId}/replies` 的语义保持一致，避免多层回复导致读侧不可达与列表活动字段漏计。

#### 2) social-service：点赞/关注写路径补齐拉黑约束（仅阻断创建副作用）

**点赞 LikeService**
- 新增依赖 `BlockService`（内部调用，SSOT= social-service block 关系）。
- 在 `setLike(...)` 中：
  - 先读取当前 liked 状态（用于区分“创建”与“幂等重复/取消”）。
  - 仅当“目标状态为 liked=true 且当前未点赞”时：
    1) 解析 entity owner（现有逻辑：post/comment 通过 content-service internal resolve；user 直接使用 entityId）。
    2) 若存在 entityUserId 且 `blockService.isEitherBlocked(actorUserId, entityUserId)` 为 true，则返回 403。
  - 取消点赞（liked=false）不阻断，避免拉黑后无法清理自身状态。

**关注 FollowService**
- 新增依赖 `BlockService`。
- 在 `follow(...)` 中：
  - 仅当当前尚未关注时，检查 `blockService.isEitherBlocked(actorUserId, targetUserId)`，命中则返回 403。
  - 维持 follow 幂等与事件发布语义（只在首次关注时发布 FollowCreated）。

### Error Code Strategy
- 跨帖/多层回复：`404 NOT_FOUND`（避免泄露目标 comment 存在性与归属）
- 拉黑约束：`403 FORBIDDEN`（明确权限/反骚扰策略阻断）

## Security and Performance
- **Security:**
  - 回复写路径增加归属校验，消除跨帖注入与不可达数据。
  - 点赞/关注补齐拉黑约束，避免“拉黑后仍产生通知类副作用”。
- **Performance:**
  - 点赞/关注的拉黑检查仅在“创建关系”路径触发，避免对幂等重复/取消操作额外增加依赖开销。

## Testing and Deployment
- **Testing:**
  - content-service：新增 CommentService 单测覆盖跨帖回复/回复回复的拒绝策略。
  - social-service：新增 Like/Follow 单测覆盖“拉黑后创建关系应拒绝”。
  - 回归执行：`mvn -pl content-service test`、`mvn -pl social-service test`
- **Deployment:**
  - 仅行为变更，无 schema 变更；上线后可通过审计/错误码观测确认新策略生效。

