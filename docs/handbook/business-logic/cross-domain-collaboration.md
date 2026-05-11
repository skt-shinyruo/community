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

跨领域异步协作必须通过 owner 的 `contracts.event` 或可靠 outbox 表达。

```text
Owner domain event
  -> infrastructure event adapter
  -> owner contracts.event
  -> consumer listener / outbox handler
  -> consumer ApplicationService
```

不要让 controller、listener、handler、job 直接调用 foreign application、foreign domain、repository、mapper 或 dataobject。

## 同步协作什么时候用

同步协作用在“当前用例必须马上知道结果，否则不能正确提交”的场景。

| 场景 | 调用方向 | 为什么同步 |
| --- | --- | --- |
| 注册验证码通过后创建用户 | auth -> user action API | auth 不拥有用户事实，必须由 user 创建 active 用户。 |
| 登录校验账号密码 | auth -> user query API | 密码 hash 和用户状态是 user 事实，登录必须立即知道认证结果。 |
| 发帖前校验作者能否发言 | content -> user query/action API | content 不能自己判断用户处罚事实。 |
| 点赞前解析内容实体 | social -> content query API | social 不能信任客户端传入的 `entityUserId` 和 `postId`。 |
| growth 自动奖励入账 | growth -> wallet action API | 奖励是否真正入账由 wallet 账本决定。 |
| market 创建订单后的资金动作 | market -> wallet action/saga | 订单和资金事实分属不同 owner。 |

同步协作的关键判断：如果 foreign owner 返回失败，当前事务通常也应该失败或进入明确的 pending / saga 状态。

## 异步协作什么时候用

异步协作用在“主事实已经成立，下游读模型或副作用可以稍后追平”的场景。

| 场景 | 事件或投影 | 语义 |
| --- | --- | --- |
| 帖子创建、更新、删除 | content event -> search outbox | ES 索引最终一致，可重试，可 reindex。 |
| 评论、点赞、关注、治理 | content/social/moderation event -> notice projection | 通知 after-commit best-effort，失败不回滚上游。 |
| 内容/社交事件推进任务 | content/social -> growth | growth 做事件去重，避免重复推进任务。 |
| 用户处罚或拉黑变化 | user/social -> IM policy outbox -> Kafka | realtime 本地 policy projection 最终追平。 |
| IM command 持久化完成 | im-core persisted event -> realtime push | 在线推送不等于消息事实，客户端可补拉 history。 |

异步协作的关键判断：HTTP 成功只代表 owner 主事实完成，不代表所有 projection 已完成。

## 几条代表链路

### 发帖

```text
PostController
  -> PostPublishingApplicationService
      -> content domain / repository
      -> user owner API      # 发言资格
      -> growth / wallet     # 任务和奖励协作
      -> content domain event
          -> search outbox
          -> notice projection
```

content 拥有帖子事实；search 和 notice 只是下游读模型。

### 点赞

```text
LikeController
  -> LikeApplicationService
      -> content owner API   # 解析实体 owner 和 postId
      -> social repository   # 写点赞关系
      -> growth / wallet     # 任务和奖励
      -> social contract event
          -> notice projection
```

social 不信任客户端提交的目标用户，而是回源 content 解析。

### 拉黑影响 IM

```text
BlockController
  -> BlockApplicationService
      -> social repository
      -> social domain event
          -> IM policy outbox
              -> Kafka
                  -> im-realtime projection
```

IM realtime 使用本地 policy projection 做快速判定，但拉黑主事实仍在 social。

### 市场订单资金动作

```text
MarketApplicationService
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

如果一个问题发生在读模型上，例如搜索结果、通知、IM policy 缓存，排查时不要先改 owner 事实；先确认 projection 是否落后、outbox 是否卡住、reindex 或补偿入口是否需要执行。
