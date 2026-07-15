# Interaction 互动写入编排逻辑

`interaction` 是跨 owner 的点赞写入用例边界。它负责在进入 social 写模型前解析可信目标；点赞关系、拉黑规则、删除 fencing 和社交事件仍由 `social` owner 决定。

## Owner / SSOT

- `social` 拥有点赞关系和点赞计数。
- `user` 拥有用户目标的存在性。
- `content` 拥有帖子、评论、内容作者和根帖子归属。
- `interaction` 只拥有点赞写入的跨域编排，不持久化关系，也不发布自己的业务事件。

## 入口

- `POST /api/likes` -> `LikeInteractionController` -> `LikeInteractionApplicationService`

点赞读取仍由 social 直接提供：

- `GET /api/likes/status`
- `GET /api/likes/count`
- `GET /api/likes/counts`
- `GET /api/likes/statuses`

这些 GET 入口位于 `social.controller.LikeController`，不经过 interaction。

## 写入流程

`LikeInteractionApplicationService.setLike(...)`：

1. 校验 actor、entityId，并只接受 `USER`、`POST`、`COMMENT`。
2. `USER` 目标通过 `UserLookupQueryApi.getSummaryById(...)` 验证存在，服务端把目标用户 ID 作为可信 `entityUserId`。
3. `POST` / `COMMENT` 目标通过 `ContentEntityQueryApi.resolve(...)` 读取内容 owner 的 `entityUserId` 和根 `postId`。
4. content 返回的解析结果缺少 owner 或根帖子时按依赖不可用失败，不把不完整数据交给 social。
5. 调用 `SocialLikeActionApi.setLike(...)`，传入 actor、目标状态和服务端解析出的 `ResolvedLikeTargetView`。
6. social owner 校验目标形状、拉黑关系和删除状态，写入或删除点赞关系，并发布 social contract event。
7. interaction 只把 social 返回的 `liked` 与 `likeCount` 转为 `LikeInteractionResult`。

## 失败与一致性

- 用户目标不存在返回 not found；非法实体类型或缺失 ID 返回参数错误。
- content 目标解析不完整时 fail-closed；不信任客户端提供 `entityUserId` 或 `postId`。
- 重复设置相同点赞状态由 social owner 作为幂等 no-op 处理。
- interaction 的同步成功表示 social 主事实已经提交，不表示通知、成长、钱包奖励或 hot-feed 投影已经追平。

## 关键代码

- `interaction.controller.LikeInteractionController`
- `interaction.application.LikeInteractionApplicationService`
- `interaction.application.command.SetLikeInteractionCommand`
- `social.api.action.SocialLikeActionApi`
- `social.application.LikeApplicationService`
- `content.api.query.ContentEntityQueryApi`
- `user.api.query.UserLookupQueryApi`
