# Change Proposal: fix_comment_reply_block_guard

## Requirement Background
当前实现存在两类 P0 级逻辑漏洞/一致性缺口：

1) **评论/回复归属校验缺失 + 多层回复语义不一致**
- `POST /api/posts/{postId}/comments` 在回复评论（`entityType=COMMENT`）时，仅校验 `entityId(commentId)` 存在，但未校验该 comment 是否属于路径里的 `postId`。
- 直接后果：可构造“跨帖回复”（对 A 帖子的接口路径，回复 B 帖子下的 comment），写入后形成**不可达/难以治理**的脏数据。
- 另外，API 层面的 replies 读取只支持对“帖子一级评论”拉取 replies，而内部 resolve 存在多跳追溯能力，导致语义不一致：写入允许更深层、读侧不支持完整读取。

2) **拉黑关系对互动写路径约束覆盖不一致**
- 评论与私信已实现“双方任意一方拉黑则禁止互动”的 fail-closed 校验。
- 但点赞/关注写路径未补齐同等约束，导致拉黑后仍可继续点赞/关注并产生通知类副作用，违反反骚扰语义一致性。

## Change Content
1. 评论/回复写路径增加“归属一致性 + 层级约束”：
   1) 回复评论必须属于同一帖子（路径 `postId` 与目标 comment 的归属一致）。
   2) 统一回复层级为“仅支持对帖子一级评论回复”（避免多层回复导致读侧/列表活动字段漏计）。
2. 点赞/关注写路径补齐拉黑约束：
   1) 当双方存在拉黑关系时，禁止创建点赞（LikeCreated）与关注（FollowCreated）。
   2) 保持幂等：已存在关系的重复“设置为已点赞/已关注”请求不额外产生副作用。

## Impact Scope
- **Modules:**
  - content-service（评论/回复写路径校验）
  - social-service（点赞/关注写路径反骚扰校验）
  - helloagents（知识库与变更记录）
- **Files:**
  - `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`
  - `content-service/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
  - `social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`
  - `social-service/src/main/java/com/nowcoder/community/social/follow/FollowService.java`
  - `social-service/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
  - `social-service/src/test/java/com/nowcoder/community/social/service/FollowServiceTest.java`
  - `.helloagents/modules/content.md`
  - `.helloagents/modules/social.md`
  - `.helloagents/CHANGELOG.md`

- **APIs（行为变更，不新增路由）:**
  - `POST /api/posts/{postId}/comments`：回复评论时增加归属校验，且仅允许回复“该帖子一级评论”
  - `POST /api/likes`：创建点赞时增加拉黑校验
  - `POST /api/follows`：创建关注时增加拉黑校验

- **Data:**
  - 不新增表结构；避免继续产生跨帖/多层回复脏数据。

## Core Scenarios

### Requirement: 评论/回复归属一致性与层级约束
**Module:** content
确保回复评论不会跨帖写入，并将回复语义与读侧能力保持一致（仅一级回复）。

#### Scenario: 回复评论必须属于同一帖子
前置条件：用户已登录，目标 comment 存在且为该帖子一级评论
- 回复写入成功
- 生成 CommentCreated 事件（供通知消费）

#### Scenario: 跨帖回复应被拒绝
前置条件：用户已登录，目标 comment 存在但不属于该帖子
- 返回 404（资源不存在），不写入 DB，不发布事件

#### Scenario: 回复“回复评论”（多层）应被拒绝
前置条件：用户已登录，目标 comment 为 reply（entityType=COMMENT）
- 返回 404（资源不存在），避免写入后不可达

### Requirement: 点赞/关注写路径拉黑约束一致性
**Module:** social
当双方存在拉黑关系时，禁止产生新的互动副作用（点赞/关注事件与通知）。

#### Scenario: 拉黑后点赞应被拒绝
前置条件：用户已登录，双方存在任一方向拉黑关系
- 返回 403（Forbidden），不写入点赞关系，不发布 LikeCreated

#### Scenario: 拉黑后关注应被拒绝
前置条件：用户已登录，双方存在任一方向拉黑关系
- 返回 403（Forbidden），不写入关注关系，不发布 FollowCreated

## Risk Assessment
- **Risk:** 可能存在历史客户端依赖“多层回复”写入（虽然读侧不支持完整读取）。
  - **Mitigation:** 选择 fail-closed（404）阻断不一致写入，并在知识库明确“仅一级回复”的契约；若后续确需多层回复，需同步扩展读 API 与活动统计 SQL。
- **Risk:** 拉黑约束补齐会改变既有边缘行为（拉黑后点赞/关注从可行变为禁止）。
  - **Mitigation:** 仅阻断“创建关系”的副作用，保持幂等与可预期错误码（403），并通过单测固化语义。

