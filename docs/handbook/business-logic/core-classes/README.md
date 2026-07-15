# 核心类细分

本文档是单域业务文档的类级补充，不替代 [../README.md](../README.md) 里的域级说明，也不替代 [../../core-logic-index.md](../../core-logic-index.md) 的索引。域级文档讲 owner、主路径和跨域协作；这里讲读源码时应该先看的核心类、它们各自承担什么边界、以及最该先检查哪一层。

## 阅读顺序

1. 先读对应的域级文档。
2. 再读这里的类级文档，按 ApplicationService -> DomainService -> infrastructure 的顺序看。
3. 如果某个类涉及跨域协作，再回到 [cross-domain-collaboration.md](../cross-domain-collaboration.md) 和对应 workflow。

## 文档地图

| 域 | 文档 | 备注 |
| --- | --- | --- |
| Auth | [auth.md](auth.md) | 登录、注册、验证码、密码重置、refresh token。 |
| User | [user.md](user.md) | 用户账号、头像、凭据、角色、处罚和用户策略事件。 |
| Profile | [../profile.md](../profile.md) | 用户主页的跨 owner 同步聚合。 |
| Interaction | [../interaction.md](../interaction.md) | 点赞写入前的目标解析和 social action 编排。 |
| OSS | [oss.md](oss.md) | 对象、版本、授权、引用和生命周期。 |
| Drive | [drive.md](drive.md) | 空间、条目树、回收站、分享、OSS 代理。 |
| Content | [content.md](content.md) | 帖子、评论、媒体、治理、事件和投影。 |
| Social | [social.md](social.md) | 点赞、关注、拉黑和 IM policy 联动。 |
| Growth | [growth.md](growth.md) | 任务、奖励、等级和事件去重。 |
| Wallet | [wallet.md](wallet.md) | 账户、总账、充值、提现、转账、奖励、管理员操作。 |
| Market | [market.md](market.md) | listing、库存、订单、纠纷和 wallet saga。 |
| Notice / Search / Analytics / Ops | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) | 这组支撑域共享读模型、投影和采集底座。 |
| IM | [im.md](im.md) | gateway、realtime、core、policy projection。 |
| Shared infra | [shared-infrastructure.md](shared-infrastructure.md) | 共享基础设施、outbox、idempotency、trace、UUID 适配。 |

## 取舍

- 这里故意不重复域级文档里的完整业务流程。
- 每篇类级文档只强调“哪个类是入口、哪个类承载规则、哪个类只是适配器”。
- 支撑域里 `notice / search / analytics / ops` 保持在一个文件里，因为它们围绕读模型、投影、采集和运行治理协作。
