# 跨领域协作说明

一个用户动作经常需要多个领域参与。本文档说明这些协作应该怎么看：哪些是同步 owner API，哪些是异步 contract event，哪些只是读模型追平。

## 基本规则

同领域内的请求从 inbound adapter 进入本领域 `*ApplicationService`。跨领域同步协作只能由 application 调 owner domain 的 `api.query` / `api.action` / `api.model`。

```text
Controller / Listener / Job
  -> SameDomainApplicationService
      -> SameDomain domain / repository
      -> ForeignOwner api.query / api.action
```

跨领域异步协作必须通过 owner 的 `contracts.event` 和可靠 outbox/Kafka 骨干表达。

```text
Owner domain event
  -> same-domain event bridge -> owner ApplicationService
  -> owner contracts.event -> eventbus.<owner>
  -> owner outbox handler -> <owner>.events
  -> consumer Kafka listener
  -> consumer ApplicationService
```

不要让 controller、listener、handler、bridge、enqueuer、job 直接调用 foreign `api.*`、foreign application、same-domain application helper/port、domain model/service/repository、mapper、dataobject 或 persistence。所有这些入口先进入同域 `*ApplicationService`，再由 application 发起必要的跨域协作。

## 同步协作什么时候用

同步协作用在“当前用例必须马上知道结果，否则不能正确提交”的场景。

| 场景 | 调用方向 | 为什么同步 |
| --- | --- | --- |
| 注册验证码通过后创建用户 | auth -> user action API | auth 不拥有用户事实，必须由 user 创建 active 用户。 |
| 登录校验账号密码 | auth -> user query API | 密码 hash 和用户状态是 user 事实，登录必须立即知道认证结果。 |
| 发帖前校验作者能否发言 | content -> user query/action API | content 不能自己判断用户处罚事实。 |
| 点赞前解析目标并写关系 | interaction -> user/content query API -> social action API | interaction 解析可信 owner / root post，social 只拥有关系写入和规则。 |
| growth 自动奖励入账 | growth -> wallet action API | 奖励是否真正入账由 wallet 账本决定。 |
| market 创建订单后的资金动作 | market -> wallet action/saga | 订单和资金事实分属不同 owner。 |

同步协作的关键判断：如果 foreign owner 返回失败，当前事务通常也应该失败或进入明确的 pending / saga 状态。

## 异步协作什么时候用

异步协作用在“主事实已经成立，下游读模型或副作用可以稍后追平”的场景。

| 场景 | 事件或投影 | 语义 |
| --- | --- | --- |
| 帖子创建、更新、删除 | `eventbus.content -> content.events -> search listener` | ES 索引最终一致，可 retry / DLQ，可 reindex。 |
| 评论、点赞、关注、治理 | owner Kafka -> notice listener | source event 去重后写通知读模型，失败走 retry / DLQ。 |
| 内容/社交事件推进任务 | owner Kafka -> growth listener | growth 做 source event 去重，避免重复推进任务。 |
| 发帖、评论和点赞标准奖励 | owner Kafka -> wallet reward listener | wallet 按 source event 派生 delta / requestId 并通过总账去重。 |
| 内容删除后的点赞清理 | `content.events` -> social deletion listener | social 写删除 fence、分页清理关系，失败由 retry / DLQ / reconciliation 追平。 |
| 用户处罚或拉黑变化 | owner Kafka -> `projection.im.policy` -> IM Kafka | realtime 本地 policy projection 最终追平。 |
| IM command 持久化完成 | im-core persisted event -> realtime push | 在线推送不等于消息事实，客户端可补拉 history。 |

异步协作的关键判断：HTTP 成功只代表 owner 主事实完成，不代表所有 projection 已完成。

## 几条代表链路

### 发帖

```text
PostController
  -> PostPublishingApplicationService
      -> content domain / repository
      -> user owner API      # 发言资格
      -> content domain event
          -> eventbus.content -> content.events
              -> search / notice / growth / wallet reward / hot-feed listener
```

content 拥有帖子事实；search 和 notice 只是下游读模型。

### 点赞

```text
LikeInteractionController
  -> LikeInteractionApplicationService
      -> user/content owner query API  # 解析可信 owner 和 postId
      -> SocialLikeActionApi
          -> LikeApplicationService
              -> social repository     # 写点赞关系
              -> social contract event
                  -> eventbus.social -> social.events
                      -> notice / growth / wallet reward / hot-feed listener
```

interaction 不信任客户端提交的目标用户；social 仍负责拉黑、删除 fence、幂等关系写入和事件。`social.controller.LikeController` 只承担 GET/read 入口。

### 拉黑影响 IM

```text
BlockController
  -> BlockApplicationService
      -> social repository
      -> social domain event
          -> eventbus.social -> social.events
              -> ImPolicyBackboneKafkaListener
                  -> projection.im.policy -> IM Kafka
                      -> im-realtime projection
```

IM realtime 使用本地 policy projection 做快速判定，但拉黑主事实仍在 social。

### 市场订单资金动作

```text
MarketController
  -> MarketOrderApplicationService
      -> market order / inventory
      -> market_wallet_action
          -> wallet owner
              -> wallet ledger
```

订单 HTTP 成功不等于资金动作全部完成。资金动作要看 saga action 状态和 wallet ledger。

## 读问题时的追踪顺序

1. 先确定 owner：这个事实到底属于哪个领域？
2. 看入口：请求、事件、job 或 WebSocket frame 从哪里进入？
3. 看同域 application：事务和用例编排在哪里？
4. 看同步 foreign API：哪些结果必须当场知道？
5. 看异步事件：哪些 projection 可能稍后追平？
6. 看失败语义：失败是回滚、幂等返回、pending、best-effort 记录日志，还是进入 DEAD/DLQ？

如果一个问题发生在读模型上，例如搜索结果、通知、IM policy 缓存，排查时不要先改 owner 事实；先确认 owner outbox、Kafka consumer/DLQ 和 projection 是否落后，再判断 reindex 或补偿入口是否需要执行。
