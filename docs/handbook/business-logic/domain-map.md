# 核心业务领域地图

本文档用新人视角说明系统有哪些核心业务领域，以及每个领域的边界。详细实现链路见同目录下的单域文档和 [workflows/README.md](workflows/README.md)。

## 总体形态

`community` 不是一个所有包互相调用的普通单体。主站业务集中在 `community-app`，但按 owner domain 治理；文件对象由 `community-oss` 拥有；IM 消息权威状态由 `community-im` 拥有。

```text
Browser / Client
  -> community-gateway
      -> community-app      # 主站业务 owner 集合
      -> community-oss      # 文件对象和 /files/**
      -> community-im-*     # IM session、WebSocket、消息事实
```

## 领域职责一览

| 领域 | 拥有什么 | 不拥有或不应该直接决定什么 | 深入阅读 |
| --- | --- | --- | --- |
| auth | 注册、登录、验证码、JWT 签发、refresh token 策略、登录风控。 | 用户账号事实、密码 hash 和 refresh session 存储事实。 | [auth.md](auth.md) |
| user | 用户账号、资料、邮箱、密码 hash、角色、处罚状态、头像业务投影、refresh session 存储事实。 | 登录流程策略、帖子/评论主事实、OSS 对象事实、IM policy 缓存。 | [user.md](user.md) |
| content | 帖子、评论、回复、分类、标签、收藏、订阅、举报和内容治理状态。 | 用户身份事实、点赞关系、搜索索引、通知读模型。 | [content.md](content.md) |
| social | 点赞、关注、拉黑关系，以及这些关系产生的社交事件。 | 内容实体事实、用户处罚事实、IM 消息事实。 | [social.md](social.md) |
| growth | 任务模板、任务进度、事件去重、自动奖励触发、等级规则。 | 钱包入账事实、内容或社交事件事实。 | [growth.md](growth.md) |
| wallet | 账户、复式账本、充值、提现、转账、奖励、市场资金动作。 | 商品、订单、纠纷事实；任务是否完成。 | [wallet.md](wallet.md) |
| market | 商品、库存、地址快照、订单、纠纷、自动确认和资金动作 saga。 | 钱包余额事实和账本入账细节。 | [market.md](market.md) |
| drive | 用户私有网盘空间、目录树、回收站、分享链接、提取码和访问记录。 | 文件对象、版本、签名 URL 和 blob 生命周期。 | [drive.md](drive.md) |
| OSS | 对象、版本、上传会话、reference、grant、底层 blob 位置和生命周期。 | 使用方的业务授权和展示投影。 | [oss.md](oss.md) |
| notice | 站内通知读模型、未读数、通知摘要。 | 点赞、评论、关注、治理等上游主事实。 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) |
| search | Elasticsearch 索引、查询语义和 alias 管理。 | 帖子和评论主事实。 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) |
| analytics | UV/DAU 等访问采集和 Redis 统计读模型。 | 用户、内容、会话主事实。 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) |
| ops | 管理型清理和补偿入口的分发。 | 任何业务事实本身。 | [notice-search-analytics-ops.md](notice-search-analytics-ops.md) |
| IM | session bootstrap、WebSocket 连接、私信/群聊消息、成员、未读和在线推送。 | 主站用户处罚和拉黑主事实。 | [im.md](im.md) |

## 核心边界

1. `auth` 和 `user` 分开：auth 负责“如何进入系统”，user 负责“用户事实是什么”。
2. `content` 和 `social` 分开：content 负责内容实体，social 负责互动关系。
3. `notice` 和 `search` 都是读模型：它们服务查询和提醒，不拥有上游业务事实。
4. `growth` 决定任务进度和奖励触发，`wallet` 决定奖励是否真正入账。
5. `market` 决定订单和纠纷，`wallet` 决定资金事实。
6. `drive` 决定网盘业务状态，OSS 决定对象存储技术事实。
7. `community-im` 决定消息事实，`community-app` 只向它提供用户 policy 和拉黑关系投影。

## message 名称说明

当前主站 `community.message` 表承载站内通知语义；IM 私信和群聊主事实属于 `community-im`，因此业务阅读时不要再把 `message` 当成独立 IM owner。

## 新需求从哪里开始看

| 想改的能力 | 先看 owner | 再看协作方 |
| --- | --- | --- |
| 登录、注册、密码重置 | auth | user、analytics |
| 用户资料、头像、处罚 | user | OSS、IM policy |
| 发帖、评论、举报、收藏 | content | user、social、search、notice、growth |
| 点赞、关注、拉黑 | social | content、notice、growth、IM policy |
| 任务、等级、积分奖励 | growth | wallet、content、social |
| 余额、奖励、交易资金 | wallet | market、growth |
| 商品、订单、纠纷 | market | wallet、user |
| 网盘和文件分享 | drive | OSS |
| 文件上传下载底座 | OSS | user、content、drive |
| 搜索结果不一致 | search | content、outbox |
| IM 发送失败或未读异常 | IM | user、social、community-app projection |
