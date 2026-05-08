# 业务逻辑详解文档集

本目录是 `community` 当前业务逻辑的详细说明层。它补充 [../business-flows.md](../business-flows.md) 的总览，不替代架构、安全、可靠性、存储和接口契约文档。

阅读顺序建议：

1. 先看 [../overview.md](../overview.md) 理解 deployable 和请求主线。
2. 再看 [../business-flows.md](../business-flows.md) 建立业务域总览。
3. 需要深入某个域时，打开本目录对应文档。
4. 查代码入口时，用 [../core-logic-index.md](../core-logic-index.md) 反查核心类。

## 文档地图

当前 active 业务域以 `backend/community-app` 的 `auth`、`user`、`content`、`social`、`notice`、`search`、`analytics`、`growth`、`market`、`wallet`、`ops`、`im` 包，以及 `backend/community-im/*` 的 IM 模块为准。架构守卫中仍出现的 `message` 名称是历史/兼容口径：主站 `community.message` 表现在承载站内通知语义，IM 私信/群聊主事实已经迁到 `community-im`，因此不再单独写一篇 `message` 业务域文档。

| 业务域 | 文档 | 覆盖内容 |
| --- | --- | --- |
| 认证 | [auth.md](auth.md) | 登录、注册、验证码、密码重置、refresh token、会话清理。 |
| 用户 | [user.md](user.md) | 用户资料、头像、凭据、角色、处罚状态、积分、refresh session。 |
| OSS | [oss.md](oss.md) | 对象元数据、版本、alias、签名 URL、生命周期和 Garage / S3-compatible 后端。 |
| 内容 | [content.md](content.md) | 帖子、评论、分类、标签、收藏、订阅、举报、审核、内容事件。 |
| 社交 | [social.md](social.md) | 点赞、关注、拉黑、社交事件、互动限制、IM policy 联动。 |
| 成长 | [growth.md](growth.md) | 任务模板、任务进度、事件去重、奖励、用户等级。 |
| 钱包 | [wallet.md](wallet.md) | 账户、复式账本、充值、提现、转账、奖励、市场资金、管理员操作。 |
| 市场 | [market.md](market.md) | 商品、库存、地址、订单、纠纷、钱包动作 saga、自动确认、恢复任务。 |
| 通知/搜索/分析/运维 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) | 通知读模型、搜索投影与重建、UV/DAU、ops 入口。 |
| IM | [im.md](im.md) | IM session、WebSocket、私信、群聊、成员、未读、policy projection。 |
| 前端业务面 | [frontend-surfaces.md](frontend-surfaces.md) | Vue 路由、页面和 API service 到后端业务能力的映射。 |

## 统一阅读口径

每篇文档都按当前代码真实行为说明：

- **Owner / SSOT**：哪个域拥有主事实。
- **Entry**：HTTP、internal endpoint、事件、job、WebSocket frame 或前端入口。
- **Main path**：正常读写链路。
- **State**：重要状态、状态迁移和 pending 语义。
- **Idempotency / consistency**：幂等、重试、outbox、best-effort、补偿、回源。
- **Failure**：业务失败、权限失败、下游失败时如何表现。
- **Key code**：读源码时优先打开的类。

这些文档不重新定义 DDD 分层规则。后端代码仍以 [../architecture.md](../architecture.md) 和仓库根目录 `AGENTS.md` 的严格 DDD Tactical Layering 为准。
