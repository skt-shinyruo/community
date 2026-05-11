# 发帖、媒体、标签和评论流程

本文解释内容主链路。领域细节见 [../content.md](../content.md)、[../social.md](../social.md)、[../growth.md](../growth.md)、[../notice-search-analytics-ops.md](../notice-search-analytics-ops.md)。

## 参与领域

| 领域 | 职责 |
| --- | --- |
| content | 帖子、评论、正文 blocks、媒体引用、标签、分类、收藏、订阅、举报和治理状态。 |
| user | 作者存在性、发言资格、处罚状态。 |
| OSS | 帖子媒体对象、版本、公有文件 URL。 |
| social | 内容上的点赞关系和删除后的点赞清理。 |
| growth / wallet | 发帖、评论等事件推进任务和奖励入账。 |
| search | 帖子搜索索引。 |
| notice | 评论、治理等事件产生通知。 |

## 发帖主流程

1. `PostController` 接收请求，读取登录用户和 `Idempotency-Key`。
2. controller 进入 `PostPublishingApplicationService.create(...)`。
3. application 使用 `IdempotencyGuard.executeRequired(...)` 包住真实写操作。
4. content 清洗文本，校验分类、标签、正文 blocks、媒体引用和请求字段。
5. content 回源 user owner，确认作者存在且可以发言。
6. domain / repository 写帖子主事实。
7. 新 tag 通过 `ensureTagId(...)` 幂等创建，帖子和 tag 关系在 content 内绑定。
8. 如果有媒体，content 只绑定当前用户已上传且类型匹配的 asset；对象和版本事实仍在 OSS。
9. 提交前同步触发积分或成长任务相关 owner API。
10. content 发布 domain event。
11. search outbox 在事务内写入待投影事件。
12. notice projection 在事务提交后 best-effort 生成通知。
13. 帖子分数刷新等副作用按当前策略安排执行。

## 媒体上传和绑定

帖子媒体不是直接把文件写进 content。

1. content 先创建 draft asset 或上传意图。
2. 浏览器通过 OSS upload session 上传 blob。
3. 上传完成后，OSS 激活 object/version。
4. 发帖或改帖时，content 校验 asset 属于当前用户、类型匹配、仍可绑定。
5. 新引用建立后，旧引用会释放 OSS reference。

这样 content 拥有“帖子引用了哪些媒体”的业务事实，OSS 拥有“文件对象和版本”的技术事实。

## 评论和回复流程

1. `CommentApplicationService` 接收发评论命令。
2. content 校验帖子存在、状态允许评论。
3. 如果是回复，校验目标评论存在且归属正确。
4. content 回源 user owner 校验作者发言资格。
5. content 可通过 social owner 判断双方拉黑关系。
6. content 写 `comment`，同步更新帖子评论数或活动状态。
7. content 触发积分、成长任务和评论事件。
8. notice 根据评论事件生成接收人的通知。

## 删除和治理

内容删除或治理下线改变 content 主事实。提交后，下游分别追平：

- search 删除或更新 ES 文档。
- notice 可生成治理通知。
- social owner 清理失效内容实体上的点赞关系。
- growth/wallet 根据当前业务规则处理奖励或撤销。

## 排查口径

| 现象 | 先查哪里 |
| --- | --- |
| 发帖重复或返回 replay conflict | `Idempotency-Key` 和 request fingerprint。 |
| 发帖成功但搜索不到 | search outbox、ES projection、reindex，不要先改 content。 |
| 评论成功但没有通知 | notice after-commit projection 日志和投影规则。 |
| 媒体链接不可访问 | content asset 绑定、OSS object/version、alias 或 grant。 |
