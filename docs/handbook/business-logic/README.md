# 核心业务手册

本目录解释 `community` 的核心业务：哪些领域拥有主事实、用户动作如何穿过多个领域、异步投影为什么会最终一致。目标读者是第一次接触项目的开发、测试、运维和评审同学。

这里不替代 [../architecture.md](../architecture.md)、[../system-design.md](../system-design.md)、[../business-flows.md](../business-flows.md) 和 [../core-logic-index.md](../core-logic-index.md)。如果你要理解业务，先读本目录；如果你要改代码，再回到架构规则和核心代码索引。

## 项目业务总览

`community` 的核心业务不是一个单一 CRUD 站点，而是一套社区产品域：内容社区、社交互动、通知搜索、成长激励、钱包市场、网盘文件和 IM 会话共同组成用户体验。前端 Vue3 SPA 通过 `frontend/src/api/services/*.js` 调用后端；后端主站业务集中在 `backend/community-app`，IM 消息事实在 `backend/community-im/*`，文件对象事实由 OSS 模块拥有。

后端业务按 owner domain 治理。每个领域都要先分清“谁拥有主事实”：`content` 拥有帖子和评论，`social` 拥有点赞/关注/拉黑，`wallet` 拥有账户和账本，`market` 拥有商品和订单，`drive` 拥有网盘目录和分享，OSS 拥有对象和版本，IM 拥有消息和会话事实。通知、搜索、分析和 IM policy 多数是读模型或投影，不应该反向充当上游业务事实。

一次用户动作通常从 HTTP controller、listener、job 或 WebSocket frame 进入同领域 `*ApplicationService`。ApplicationService 负责用例编排、事务、幂等、领域规则调用、repository 持久化、同步 owner API 协作和事件发布。必须当场知道结果的协作用同步 `api.query` / `api.action`；主事实已经成立、下游可以稍后追平的协作用 `contracts.event`、outbox 或 projection。

最重要的端到端链路包括：

1. 注册登录：`auth` 编排验证码、登录风控、JWT 和 refresh token，`user` 拥有账号、密码 hash、用户状态和 refresh session 存储事实。
2. 发帖评论：`content` 写帖子/评论主事实，回源 `user` 校验发言资格，媒体通过 OSS，搜索和通知通过事件最终一致。
3. 点赞关注拉黑：`social` 写互动关系，点赞目标回源 `content` 解析，拉黑变化通过 outbox / Kafka 追平 IM policy projection。
4. 成长奖励：`growth` 根据内容和社交事件推进任务、去重和计算等级；真正入账由 `wallet` owner 决定。
5. 市场交易：`market` 写商品、库存、订单和纠纷，资金动作进入 market wallet action saga，最终由 `wallet` 的复式账本落账。
6. 网盘文件：`drive` 处理空间、目录、回收站、分享和提取码，OSS 处理对象、版本、引用、授权和签名 URL。
7. IM 会话：前端先 bootstrap session，再走 WebSocket；send accepted 只表示 command 被接收，最终状态以 IM core 持久化和 history 为准。

排查业务问题时，先确定 owner 和主事实，再看当前入口的 ApplicationService，然后区分同步 owner API、异步事件、outbox、projection 和补偿任务。HTTP 200、WebSocket accepted、outbox 投递成功、读模型追平和 saga action 成功不是同一个层面的成功。

## 推荐阅读路线

1. 先读 [glossary.md](glossary.md)，把 owner、SSOT、ApplicationService、outbox、projection 等术语对齐。
2. 再读 [domain-map.md](domain-map.md)，知道每个业务领域负责什么、不负责什么。
3. 然后读 [cross-domain-collaboration.md](cross-domain-collaboration.md)，理解同步 API 和异步事件的协作方式。
4. 接着按用户动作读 [workflows/README.md](workflows/README.md) 下的端到端流程。
5. 需要深入某个领域时，再打开下面的单域详解文档。
6. 从页面反查业务时，读 [frontend-surfaces.md](frontend-surfaces.md)。
7. 遇到疑惑时，先看 [faq.md](faq.md)。
8. 如果你已经理解域级流程，想按类读源码，再看 [core-classes/README.md](core-classes/README.md)。

## 新人入口文档

| 文档 | 适合回答的问题 |
| --- | --- |
| [glossary.md](glossary.md) | 这些业务、架构和一致性术语到底是什么意思？ |
| [domain-map.md](domain-map.md) | 系统有哪些核心业务域？每个域的边界在哪里？ |
| [cross-domain-collaboration.md](cross-domain-collaboration.md) | 一个业务需要多个域参与时，应该同步调用还是发事件？ |
| [workflows/README.md](workflows/README.md) | 注册、发帖、评论、点赞、交易、网盘和 IM 这些完整链路怎么走？ |
| [faq.md](faq.md) | 为什么这个项目这样拆域、这样做最终一致和投影？ |

## 单域详解

当前 active 业务域以 `backend/community-app` 的 `auth`、`user`、`content`、`social`、`notice`、`search`、`analytics`、`growth`、`market`、`wallet`、`drive`、`ops`、`im` 包，以及 `backend/community-im/*` 的 IM 模块为准。站内通知读模型在 notice 的 `notice_record`，IM 私信和群聊主事实在 `community-im`，因此不再单独写一篇 `message` 业务域文档。

| 业务域 | 文档 | 覆盖内容 |
| --- | --- | --- |
| 认证 | [auth.md](auth.md) | 登录、注册、验证码、密码重置、refresh token、会话清理。 |
| 用户 | [user.md](user.md) | 用户资料、头像、凭据、角色、处罚状态、积分、refresh session。 |
| OSS | [oss.md](oss.md) | 对象元数据、版本、签名 URL、生命周期和 Garage / S3-compatible 后端。 |
| 网盘 | [drive.md](drive.md) | 10GiB 私有网盘、目录树、回收站、分享链接、提取码和 OSS 代理下载。 |
| 内容 | [content.md](content.md) | 帖子、评论、分类、标签、收藏、订阅、举报、审核、内容事件。 |
| 社交 | [social.md](social.md) | 点赞、关注、拉黑、社交事件、互动限制、IM policy 联动。 |
| 成长 | [growth.md](growth.md) | 任务模板、任务进度、事件去重、奖励、用户等级。 |
| 钱包 | [wallet.md](wallet.md) | 账户、复式账本、充值、提现、转账、奖励、市场资金、管理员操作。 |
| 市场 | [market.md](market.md) | 商品、库存、地址、订单、纠纷、钱包动作 saga、自动确认、恢复任务。 |
| 通知/搜索/分析/运维 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) | 通知读模型、搜索投影与重建、UV/DAU、ops 入口。 |
| IM | [im.md](im.md) | IM session、WebSocket、私信、群聊、成员、未读、policy projection。 |
| 前端业务面 | [frontend-surfaces.md](frontend-surfaces.md) | Vue 路由、页面和 API service 到后端业务能力的映射。 |

## 核心类细分

| 文档 | 适合回答的问题 |
| --- | --- |
| [core-classes/README.md](core-classes/README.md) | 某个域的核心 ApplicationService / DomainService / adapter 应该先看哪些类？ |
| [core-classes/auth.md](core-classes/auth.md) | 认证域的登录、注册、验证码、密码重置、refresh token 具体先读哪些类？ |
| [core-classes/user.md](core-classes/user.md) | 用户域的资料、头像、处罚、积分、refresh session 具体先读哪些类？ |
| [core-classes/oss.md](core-classes/oss.md) | OSS 对象、版本、授权、引用和生命周期的类级职责是什么？ |
| [core-classes/drive.md](core-classes/drive.md) | 网盘空间、条目树、回收站、分享和 OSS 代理下载分别由哪些类承载？ |
| [core-classes/content.md](core-classes/content.md) | 帖子、评论、媒体、治理、事件和投影类分别怎么分工？ |
| [core-classes/social.md](core-classes/social.md) | 点赞、关注、拉黑以及 IM policy 联动的关键类是什么？ |
| [core-classes/growth.md](core-classes/growth.md) | 任务、奖励、等级和事件去重的关键类是什么？ |
| [core-classes/wallet.md](core-classes/wallet.md) | 账户、总账、订单、奖励和管理员操作的关键类是什么？ |
| [core-classes/market.md](core-classes/market.md) | 商品、库存、订单、纠纷和 wallet saga 的关键类是什么？ |
| [core-classes/notice-search-analytics-ops.md](core-classes/notice-search-analytics-ops.md) | 通知、搜索、分析这组支撑域的关键类是什么？ |
| [core-classes/im.md](core-classes/im.md) | IM gateway、realtime、core、policy projection 的关键类是什么？ |
| [core-classes/shared-infrastructure.md](core-classes/shared-infrastructure.md) | 共享基础设施、UUID 适配、outbox、idempotency 和 trace 的关键类是什么？ |

## 统一阅读口径

单域文档按当前代码真实行为说明：

- **Owner / SSOT**：哪个域拥有主事实。
- **Entry**：HTTP、internal endpoint、事件、job、WebSocket frame 或前端入口。
- **Main path**：正常读写链路。
- **Data flow**：请求、状态、持久化、事件、投影和补偿如何串起来。
- **State**：重要状态、状态迁移和 pending 语义。
- **Idempotency / consistency**：幂等、重试、outbox、best-effort、补偿、回源。
- **Failure**：业务失败、权限失败、下游失败时如何表现。
- **Key code**：读源码时优先打开的类。

这些文档不重新定义 DDD 分层规则。后端代码仍以 [../architecture.md](../architecture.md) 和仓库根目录 `AGENTS.md` 的严格 DDD Tactical Layering 为准。
