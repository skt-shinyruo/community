# 端到端业务流程

本目录按用户动作解释业务链路。单域文档回答“某个领域拥有什么”；这里回答“一个完整动作怎样穿过多个领域”。

## 阅读顺序

1. [auth-registration-login.md](auth-registration-login.md)：注册、登录、refresh 和 logout。
2. [content-publish-comment.md](content-publish-comment.md)：发帖、媒体、标签、评论和下游事件。
3. [social-notice-policy.md](social-notice-policy.md)：点赞、关注、拉黑、通知和 IM policy。
4. [growth-reward-level.md](growth-reward-level.md)：内容/社交事件如何推进任务、发奖励和计算等级。
5. [market-wallet.md](market-wallet.md)：市场下单、资金托管、确认收货、退款和账本。
6. [drive-oss.md](drive-oss.md)：网盘上传、目录树、回收站、分享和 OSS 下载。
7. [im-session-messaging.md](im-session-messaging.md)：IM session、WebSocket、私信、群聊和未读。
8. [notice-search-analytics-ops.md](notice-search-analytics-ops.md)：通知、搜索、分析和运维投影。

## 统一读法

每条流程都按以下问题阅读：

- 这个动作的入口在哪里？
- 哪个领域拥有主事实？
- 哪些同步 owner API 必须在当前请求内完成？
- 哪些下游投影可以最终一致？
- 幂等、失败、pending 和补偿语义是什么？
- 如果线上表现不对，应该先查 owner 事实还是查 projection？

## 成功语义提醒

HTTP 200、WebSocket accepted、outbox 投递成功和 saga action 成功不是同一个层面的成功。读流程文档时要始终区分主事实提交、command 接单、读模型追平和长流程动作完成。
