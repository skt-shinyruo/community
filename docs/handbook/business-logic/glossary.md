# 业务术语表

本文档解释阅读业务手册时会反复出现的术语。它只做业务和协作语义说明；严格分层规则以 [../architecture.md](../architecture.md) 和仓库根目录 `AGENTS.md` 为准。

## 架构和边界

| 术语 | 含义 | 典型场景 |
| --- | --- | --- |
| Owner domain | 拥有某类业务事实的领域。只有 owner 能决定事实如何创建、修改、删除。 | `content` 拥有帖子和评论，`wallet` 拥有余额和账本。 |
| SSOT | Single Source of Truth，主事实来源。读模型、缓存和投影不能反过来当成事实。 | Elasticsearch 是搜索读模型，不是帖子事实。 |
| ApplicationService | 同领域用例入口，负责事务、编排、幂等、命令/result 组装和跨域 owner API 调用。 | `PostPublishingApplicationService` 编排发帖。 |
| Domain model | 领域内的业务对象，表达状态和规则，不依赖 controller、infrastructure 或 foreign API。 | 帖子、钱包账户、市场订单、任务进度。 |
| DomainService / Policy | 不自然属于单个实体的领域规则。 | 密码策略、任务进度规则、可发言策略。 |
| Repository interface | 领域定义的持久化契约，application/domain 只依赖接口。 | `*Repository` 由 MyBatis infrastructure 实现。 |
| Infrastructure | 技术实现层，包含 MyBatis、Redis、MQ、outbox、OSS client、Spring event adapter 等。 | `MyBatis*Repository`、outbox handler。 |
| Inbound adapter | 外部进入系统的适配器，例如 controller、listener、handler、bridge、job。 | HTTP controller、事件 listener、XXL job handler。 |
| Owner API | owner 暴露给外域同步协作的 `api.query` / `api.action` / `api.model`。 | auth 调 user 创建已验证用户。 |
| Contract event | owner 暴露给外域异步协作的 `contracts.event`。 | content/social 事件被 notice/growth/search 消费。 |

## 请求和身份

| 术语 | 含义 | 典型场景 |
| --- | --- | --- |
| Actor | 正在执行动作的用户或系统身份。 | 发帖人、下单买家、管理员。 |
| Viewer | 正在查看数据的用户身份，通常影响是否展示私有状态。 | 帖子详情是否展示已点赞、已收藏。 |
| Access token | 短期 JWT，前端保存在内存中，用于普通业务请求鉴权。 | `/api/posts`、`/api/wallet/**`。 |
| Refresh token | HttpOnly cookie 中的长期刷新凭证，服务端只保存 hash 和 session 状态。 | `/api/auth/refresh` 旋转 token。 |
| Registration draft | 注册验证码通过前的草稿状态，不是正式用户行。 | Verify-First 注册流程。 |
| Idempotency-Key | 客户端或服务端提供的幂等键，用于防止重复提交产生重复写入。 | 发帖、市场下单。 |
| requestId | 业务请求标识，常用于钱包、市场 saga、奖励发放和重复请求判定。 | wallet ledger 的重复入账保护。 |

## 一致性和投影

| 术语 | 含义 | 典型场景 |
| --- | --- | --- |
| Domain event | 领域内部发生的事实事件，先在 owner 内表达。 | 帖子创建、点赞变更、拉黑关系变更。 |
| Outbox | 事务内写出待投递事件，再由 worker 可靠投递。 | search post projection、IM policy projection。 |
| Projection | 为查询、推送或快速判定维护的派生视图。它不是 SSOT。 | notice 表、ES 索引、IM policy 本地缓存。 |
| Feed cursor | Feed 翻页的不透明游标，客户端不能依赖内部结构。 | 全局热榜、板块热榜、关注流翻页。 |
| Rank version | 一次榜单排序结果或投影批次的版本标识，用于解释 feed 页属于哪次排序视图。 | 热榜刷新后返回新的 `rankVersion`。 |
| Best-effort | 主事务提交后尽力执行的副作用，失败不回滚主事实。 | 通知投影失败只记录日志。 |
| Saga command | 跨领域长流程中的状态化命令，不等于最终业务完成。 | market wallet action 的 escrow/release/refund。 |
| Pending state | 主流程已经接单，但下游动作仍在进行的中间状态。 | 订单等待资金动作完成。 |

## 业务概念

| 术语 | 含义 | Owner |
| --- | --- | --- |
| 帖子 / 评论 | 社区内容主事实，包含正文、分类、标签、媒体引用和治理状态。 | `content` |
| 全局热榜 | 面向所有访问者的默认首页 feed，由事件驱动预计算热度并写入读模型。 | `content` |
| 关注流 | 登录用户基于关注作者产生的 feed 入口，优先通过拉取合并候选内容生成。 | `content` + `social` |
| 两级评论 | 帖子下 root comment 与 root comment 下 reply 的评论结构，不支持无限嵌套。 | `content` |
| 内容可见性状态 | 内容是否公开、仅作者可见、待审核、拒绝或移除的业务状态。 | `content` |
| 点赞 / 关注 / 拉黑 | 用户之间或用户对实体的社交关系。 | `social` |
| 通知 | 点赞、评论、关注、治理等事件形成的站内通知读模型。 | `notice` |
| 任务进度 | 用户因内容或社交事件推进的成长任务状态。 | `growth` |
| 奖励 / 余额 / 账本 | 用户资金或积分的最终事实，采用复式账本入账。 | `wallet` |
| 商品 / 库存 / 订单 / 纠纷 | 交易市场主事实和交易状态。 | `market` |
| 对象 / 版本 / grant | 文件对象的技术事实和访问授权。 | `community-oss` |
| 网盘条目 / 分享 | 用户私有文件树、回收站、分享 token 和提取码。 | `drive` |
| IM session | 客户端连接 IM 的短期 ticket 和 worker 路由结果。 | `community-im-gateway` |
| IM message | 私信、群聊消息、会话状态、未读水位。 | `im-core` |

## 容易混淆的成功

| 成功类型 | 代表什么 | 不代表什么 |
| --- | --- | --- |
| HTTP 成功 | 主请求已经按用例完成并返回。 | 搜索、通知、积分、IM policy 等下游全部追平。 |
| Command accepted | 实时服务已接收 command。 | command 已经持久化为最终事实。 |
| Projection 完成 | 某个读模型已经追上。 | owner 主事实发生变化。 |
| Saga action 成功 | 某个长流程动作达到 `SUCCEEDED`。 | 其他 saga action 也一定完成。 |
