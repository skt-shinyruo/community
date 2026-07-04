# 点赞、关注、拉黑、通知和 IM Policy 流程

本文解释社交互动如何影响内容展示、通知、成长任务和 IM 发送权限。领域细节见 [../social.md](../social.md)、[../content.md](../content.md)、[../notice-search-analytics-ops.md](../notice-search-analytics-ops.md)、[../im.md](../im.md)。

## 参与领域

| 领域 | 职责 |
| --- | --- |
| social | 点赞、关注、拉黑关系主事实。 |
| content | 被点赞或评论的内容实体、作者和帖子归属。 |
| user | 用户存在性、资料和处罚事实。 |
| notice | 点赞、评论、关注、治理等事件生成的通知读模型。 |
| growth / wallet | 社交事件推进任务和奖励入账。 |
| IM realtime | 使用 user/social projection 做发送前快速判定。 |

![Social notice and IM policy workflow](../../assets/workflow-social-notice-policy.svg)

## 点赞流程

1. `LikeController` 进入 `LikeApplicationService`。
2. application 解析 actor、`entityType` 和 `entityId`。
3. social 通过 `ContentEntityQueryApi` 回源 content 解析目标实体。
4. 服务端解析 `entityUserId` 和 `postId`，不信任客户端声明。
5. social 检查点赞关系当前是否存在。
6. `liked` 参数为空时执行 toggle；非空时设置到目标状态。
7. 目标状态和当前状态一致时直接返回当前结果。
8. 新增点赞前检查 actor 和目标用户之间是否存在拉黑关系。
9. repository 写入或删除点赞关系。
10. 如底层 storage adapter 需要显式补偿，application 注册事务回滚补偿。
11. social 发布 `LikeChangedDomainEvent`。
12. social 将 domain event 映射为 contract event，并写入 outbox。
13. social contract event 进入 Kafka 后，notice、growth、user reward 和 content score projection 等下游异步追平。

关键语义：

- 自己给自己点赞不会带来奖励收益。
- 被删除内容的点赞清理由 content 在提交后调用 social owner action。
- 取消点赞不一定撤销已经生成的通知，按 notice 投影规则执行。

## 关注流程

1. `FollowApplicationService.follow(...)` 校验 actor、目标实体和参数。
2. 重复关注视为幂等 no-op。
3. 新关注前检查双方拉黑关系。
4. social 写关注关系。
5. 发布 follow created event。
6. notice 可生成关注通知。

`unfollow(...)` 当前主要删除关注关系；是否发布 `FollowRemoved` contract event 以当前实现为准。

## 拉黑流程

1. `BlockApplicationService.block(...)` 校验 actor 和 target，禁止自拉黑。
2. 重复拉黑幂等返回。
3. social 写 block relation。
4. 新增成功后清理双向 follow。
5. 发布 `BlockRelationChangedDomainEvent(blocked=true)`。
6. IM policy outbox 在事务内写出 projection 变更。
7. outbox handler 投递 Kafka 事件。
8. im-realtime 消费事件，更新本地 policy projection。

解除拉黑时删除 block relation，并发布 `blocked=false` 事件让 IM projection 追平。

## 对 IM 的影响

IM realtime 发送私信前会用本地 policy projection 做快速判断：

- 发送者是否被禁言或封禁。
- 目标用户是否存在并可接收。
- 双方是否存在拉黑关系。

这个 projection 不是 SSOT。user 处罚事实在 user，拉黑事实在 social；projection 落后时，realtime 的快速判定可能短暂滞后，最终通过 snapshot 和 outbox 增量追平。

## 排查口径

| 现象 | 先查哪里 |
| --- | --- |
| 点赞目标用户不对 | content entity resolver，不要相信客户端 payload。 |
| 重复关注没有报错 | 这是幂等 no-op 语义。 |
| 拉黑后仍能短暂发送 IM | IM policy projection 是否追平，主事实仍查 social。 |
| 通知缺失 | social contract event 和 notice projection。 |
