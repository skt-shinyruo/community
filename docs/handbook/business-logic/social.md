# Social 社交业务逻辑

社交域拥有用户之间和用户对实体的互动关系：点赞、关注和拉黑。它既服务内容展示，也影响评论/点赞/关注/IM 私信等业务规则。

## Owner / SSOT

- social owns 点赞关系、关注关系和拉黑关系。
- content owns 被点赞/关注的内容实体和帖子归属。
- user owns 用户存在性、处罚状态和资料。
- notice/growth/wallet 是社交事件下游。
- IM realtime 的拉黑 projection 来自 social 主事实，不是 SSOT。

## 入口

点赞：

- `POST /api/likes`
- `GET /api/likes/status`
- `GET /api/likes/count`
- `GET /api/likes/counts`
- `GET /api/likes/statuses`

关注：

- `POST /api/follows`
- `DELETE /api/follows`
- `GET /api/follows/status`
- `GET /api/follows/{userId}/followees`
- `GET /api/follows/{userId}/followers`
- `GET /api/follows/{userId}/followees/count`
- `GET /api/follows/{userId}/followers/count`

拉黑：

- `POST /api/blocks`
- `DELETE /api/blocks`
- `GET /api/blocks`

内部 API：

- content 调 social 清理被删实体的点赞。
- content/social/IM 查询拉黑关系。
- IM snapshot 扫描 block relations。

## 数据流

社交域的数据流由关系写入和下游事件两部分组成：

1. 点赞：`LikeApplicationService` 先解析 actor、entityType 和 entityId，再通过 content owner 回源内容实体，服务端计算目标用户、帖子归属和事件字段。写入或删除点赞关系后，同步触发 user reward / growth task，并发布 `LikeChangedDomainEvent` 转成 social contract event。
2. 关注：`FollowApplicationService` 校验目标实体和拉黑关系后写 `social_follow`。重复关注是幂等 no-op；新增关注发布 follow created event，notice 可生成关注通知。
3. 拉黑：`BlockApplicationService` 写 `social_block` 后同步清理双向 follow，再发布 `BlockRelationChangedDomainEvent`。IM policy outbox 在事务内写 `projection.im.policy`，outbox handler 转成 Kafka 事件供 realtime 更新本地 projection。
4. 查询：列表、计数和状态查询都从 social owner 的关系事实读取；content、IM 或其他域需要关系判断时通过 social owner API，不直接读表。
5. 补偿：部分 repository 声明需要显式补偿时，application 在事务回滚或事件发布失败路径注册反向动作，避免关系状态与下游副作用长时间不一致。

## 点赞

`LikeApplicationService.setLike(...)` 处理点赞状态变更：

1. 校验 actor、entityType 和 entityId。
2. 查询当前点赞关系是否存在。
3. `liked` 参数为空时执行 toggle；非空时设置到目标状态。
4. 目标状态和当前状态一致时直接返回当前结果。
5. 创建点赞前解析实体：
   - 用户实体直接以 entityId 作为目标用户。
   - 帖子/评论通过 content owner 解析目标用户和帖子 ID。
6. 如果 actor 和目标用户存在拉黑关系，拒绝点赞。
7. 写入或删除点赞关系。
8. 对需要显式补偿的 repository 注册事务回滚补偿。
9. 点赞创建时同步触发用户奖励和成长任务。
10. 点赞取消时同步触发奖励撤销/调整。
11. 发布 `LikeChangedDomainEvent`，再映射为 social contract event。
12. 返回点赞状态和计数。

关键语义：

- 服务端解析 `entityUserId` 和 `postId`，不信任客户端传入。
- 点赞支持 `USER`、`POST` 和 `COMMENT` 实体类型；其他 `entityType` 由 `LikeApplicationService` / `LikeDomainService` 拒绝。
- 批量点赞查询严格处理 `entityIds`：非法 UUID、空 token、超过 200 个 ID 都返回参数错误；重复 ID 在应用层按首次出现顺序去重。
- 自己给自己点赞不会带来奖励收益。
- 被删内容的点赞清理由 content 提交后调用 social action。
- `cleanupEntityLikes(...)` 是 owner action，不是普通用户接口。

## 关注

`FollowApplicationService.follow(...)`：

1. 校验 actor、entityType 和 entityId。
2. 重复关注视为幂等 no-op。
3. 新关注前检查双方拉黑关系。
4. 写关注关系。
5. 对需要显式补偿的 repository 注册回滚补偿。
6. 发布 `FollowCreatedDomainEvent`。

`unfollow(...)`：

- 校验参数。
- 删除关注关系。
- 当前不发布 `FollowRemoved` contract event。

关键语义：

- 关注只支持 `USER` 关系；写入、状态、列表和计数查询对非 `USER` `entityType` 都返回参数错误。
- Controller 只做 HTTP 绑定、认证提取和 DTO 转换，是否支持某个 `entityType` 由 `FollowApplicationService` / `FollowDomainService` 决定。

查询能力：

- 某用户关注列表。
- 某实体粉丝列表。
- 关注数。
- 粉丝数。
- 当前用户是否已关注。

## 拉黑

`BlockApplicationService.block(...)`：

1. 校验 actor 和 target，禁止自拉黑。
2. 重复拉黑幂等处理。
3. 写 block relation。
4. 新增成功后清理双向 follow：
   - blocker -> blocked
   - blocked -> blocker
5. 发布 `BlockRelationChangedDomainEvent(blocked=true)`。
6. IM policy outbox enqueuer 在事务内写出变更。

`unblock(...)`：

1. 校验 actor 和 target。
2. 删除 block relation。
3. 删除成功后发布 `BlockRelationChangedDomainEvent(blocked=false)`。
4. IM policy 投影收到解除拉黑事件后更新本地状态。

拉黑影响：

- 禁止关注。
- 禁止点赞。
- 禁止评论回复目标用户。
- IM realtime 发送私信前用本地 policy projection 判断是否禁止。

## 社交事件

domain events：

- `LikeChangedDomainEvent`
- `FollowCreatedDomainEvent`
- `BlockRelationChangedDomainEvent`

contract events：

- `SocialContractEvent`
- `LikePayload`
- `FollowPayload`
- `BlockPayload`

下游：

- notice 生成点赞/关注通知。
- growth 对被点赞用户推进任务。
- user/wallet 对点赞奖励做发放或撤销。
- IM policy outbox 同步拉黑变化。

## 一致性和补偿

- DB repository 本身是事务性的。
- 某些 storage adapter 可能声明需要显式补偿，application 在事务回滚时反向修复。
- 事件发布失败时，如果 repository 需要显式补偿，application 会执行补偿。
- notice 是 after-commit best-effort。
- IM policy 通过 outbox/Kafka 增量投影，失败由 outbox 重试。

## 关键代码

- `social.controller.LikeController`
- `social.controller.FollowController`
- `social.controller.BlockController`
- `social.application.LikeApplicationService`
- `social.application.FollowApplicationService`
- `social.application.BlockApplicationService`
- `social.application.ContentEntityResolver`
- `social.domain.service.LikeDomainService`
- `social.domain.service.FollowDomainService`
- `social.domain.service.BlockDomainService`
- `social.infrastructure.api.*`
- `social.contracts.event.*`
- `im.projection.ImPolicyOutboxEnqueuer`
