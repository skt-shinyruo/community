# 核心业务手册

本目录解释 `community` 的核心业务：哪些领域拥有主事实、用户动作如何穿过多个领域、异步投影为什么会最终一致。目标读者是第一次接触项目的开发、测试、运维和评审同学。

这里不替代 [../architecture.md](../architecture.md)、[../system-design.md](../system-design.md)、[../business-flows.md](../business-flows.md) 和 [../core-logic-index.md](../core-logic-index.md)。如果你要理解业务，先读本目录；如果你要改代码，再回到架构规则和核心代码索引。

## 推荐阅读路线

1. 先读 [glossary.md](glossary.md)，把 owner、SSOT、ApplicationService、outbox、projection 等术语对齐。
2. 再读 [domain-map.md](domain-map.md)，知道每个业务领域负责什么、不负责什么。
3. 然后读 [cross-domain-collaboration.md](cross-domain-collaboration.md)，理解同步 API 和异步事件的协作方式。
4. 接着按用户动作读 [workflows/README.md](workflows/README.md) 下的端到端流程。
5. 需要深入某个领域时，再打开下面的单域详解文档。
6. 从页面反查业务时，读 [frontend-surfaces.md](frontend-surfaces.md)。
7. 遇到疑惑时，先看 [faq.md](faq.md)。

## 新人入口文档

| 文档 | 适合回答的问题 |
| --- | --- |
| [glossary.md](glossary.md) | 这些业务、架构和一致性术语到底是什么意思？ |
| [domain-map.md](domain-map.md) | 系统有哪些核心业务域？每个域的边界在哪里？ |
| [cross-domain-collaboration.md](cross-domain-collaboration.md) | 一个业务需要多个域参与时，应该同步调用还是发事件？ |
| [workflows/README.md](workflows/README.md) | 注册、发帖、评论、点赞、交易、网盘和 IM 这些完整链路怎么走？ |
| [faq.md](faq.md) | 为什么这个项目这样拆域、这样做最终一致和投影？ |

## 单域详解

当前 active 业务域以 `backend/community-app` 的 `auth`、`user`、`content`、`social`、`notice`、`search`、`analytics`、`growth`、`market`、`wallet`、`drive`、`ops`、`im` 包，以及 `backend/community-im/*` 的 IM 模块为准。架构守卫中仍出现的 `message` 名称是历史/兼容口径：主站 `community.message` 表现在承载站内通知语义，IM 私信/群聊主事实已经迁到 `community-im`，因此不再单独写一篇 `message` 业务域文档。

| 业务域 | 文档 | 覆盖内容 |
| --- | --- | --- |
| 认证 | [auth.md](auth.md) | 登录、注册、验证码、密码重置、refresh token、会话清理。 |
| 用户 | [user.md](user.md) | 用户资料、头像、凭据、角色、处罚状态、积分、refresh session。 |
| OSS | [oss.md](oss.md) | 对象元数据、版本、alias、签名 URL、生命周期和 Garage / S3-compatible 后端。 |
| 网盘 | [drive.md](drive.md) | 10GiB 私有网盘、目录树、回收站、分享链接、提取码和 OSS 代理下载。 |
| 内容 | [content.md](content.md) | 帖子、评论、分类、标签、收藏、订阅、举报、审核、内容事件。 |
| 社交 | [social.md](social.md) | 点赞、关注、拉黑、社交事件、互动限制、IM policy 联动。 |
| 成长 | [growth.md](growth.md) | 任务模板、任务进度、事件去重、奖励、用户等级。 |
| 钱包 | [wallet.md](wallet.md) | 账户、复式账本、充值、提现、转账、奖励、市场资金、管理员操作。 |
| 市场 | [market.md](market.md) | 商品、库存、地址、订单、纠纷、钱包动作 saga、自动确认、恢复任务。 |
| 通知/搜索/分析/运维 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) | 通知读模型、搜索投影与重建、UV/DAU、ops 入口。 |
| IM | [im.md](im.md) | IM session、WebSocket、私信、群聊、成员、未读、policy projection。 |
| 前端业务面 | [frontend-surfaces.md](frontend-surfaces.md) | Vue 路由、页面和 API service 到后端业务能力的映射。 |

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
