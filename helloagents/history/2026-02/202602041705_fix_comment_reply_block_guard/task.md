# Task List: fix_comment_reply_block_guard

Directory: `helloagents/plan/202602041705_fix_comment_reply_block_guard/`

---

## 1. content-service（评论/回复写路径校验）
- [√] 1.1 在 `CommentService.addComment` 增加回复评论归属校验（同帖 + 仅一级评论可回复），files:
  - `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`
  verify: why.md#requirement-评论回复归属一致性与层级约束-回复评论必须属于同一帖子
- [√] 1.2 补齐单测覆盖跨帖回复/回复回复的拒绝行为，files:
  - `content-service/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
  verify: why.md#requirement-评论回复归属一致性与层级约束-跨帖回复应被拒绝

## 2. social-service（点赞/关注拉黑约束）
- [√] 2.1 LikeService：创建点赞前做拉黑校验（仅阻断创建副作用），files:
  - `social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`
  verify: why.md#requirement-点赞关注写路径拉黑约束一致性-拉黑后点赞应被拒绝
- [√] 2.2 FollowService：创建关注前做拉黑校验（仅阻断创建副作用），files:
  - `social-service/src/main/java/com/nowcoder/community/social/follow/FollowService.java`
  verify: why.md#requirement-点赞关注写路径拉黑约束一致性-拉黑后关注应被拒绝
- [√] 2.3 补齐 Like/Follow 单测覆盖“拉黑后创建关系拒绝”，files:
  - `social-service/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
  - `social-service/src/test/java/com/nowcoder/community/social/service/FollowServiceTest.java`
  verify: why.md#requirement-点赞关注写路径拉黑约束一致性-拉黑后点赞应被拒绝

## 3. Security Check
- [√] 3.1 安全检查：避免跨帖写入、反骚扰语义一致、无敏感信息新增日志输出。

## 4. Documentation Update
- [√] 4.1 更新知识库：补齐“仅一级回复”契约与“拉黑禁止点赞/关注创建”语义，files:
  - `helloagents/wiki/modules/content.md`
  - `helloagents/wiki/modules/social.md`
  - `helloagents/CHANGELOG.md`

## 5. Testing
- [√] 5.1 执行单测回归：
  - `mvn -pl content-service -am test`
  - `mvn -pl social-service -am test`
