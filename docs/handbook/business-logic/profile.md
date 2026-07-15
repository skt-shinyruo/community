# Profile 用户主页聚合逻辑

`profile` 是用户主页读取的跨域编排边界。它不拥有用户、内容、社交关系或等级事实，而是在一个同域 ApplicationService 中调用各 owner 的同步查询 API，组装稳定的页面结果。

## Owner / SSOT

- `user` 拥有账号、用户名、头像、角色、状态和创建时间。
- `social` 拥有获赞数、关注数、粉丝数和当前 viewer 的关注关系。
- `content` 拥有最近帖子和最近评论。
- `growth` 拥有等级及等级统计。
- `profile` 只拥有“如何组装用户主页”这一应用用例，不持久化上述事实，也不把聚合结果反向写回 owner。

## 入口

- `GET /api/users/{userId}`
- `GET /api/users/{userId}/recent-posts`
- `GET /api/users/{userId}/recent-comments`

`UserProfileController` 只解析 path/query 参数、提取可选 viewer，并把 application result 转为 HTTP response。

## 聚合流程

`UserProfileQueryApplicationService.get(viewerId, userId)`：

1. 通过 `UserProfileQueryApi.getProfile(...)` 读取 user 主事实；用户不存在时由 user owner 返回失败。
2. 通过 `UserLevelQueryApi.evaluateLevel(...)` 读取 growth 等级。返回 `null` 或 `enabled=false` 时，主页明确返回等级功能未启用，不伪造等级值。
3. 通过 `SocialLikeQueryApi.userLikeCount(...)` 读取获赞数。
4. 通过 `SocialFollowQueryApi` 读取关注数和粉丝数。
5. viewer 未登录、目标为空或查看自己时，`hasFollowed=false`；其他情况回源 social 判断关注关系。
6. 组装 `UserProfilePageResult`，controller 再完成 transport DTO 转换。

最近内容：

- `listRecentPosts(...)` 和 `listRecentComments(...)` 都先回源 user 确认目标存在，再调用 `PostReadQueryApi`。
- 最近帖子、评论的分页和内容字段由 content owner 决定；profile 不跨表 JOIN，也不复制 content repository。

## 一致性与失败

- 主页是请求时同步聚合，不是持久化 projection；各字段反映各 owner 在本次请求时返回的状态。
- ApplicationService 没有把 owner API 异常转换为空数据；必要 owner 不可用时请求失败，避免把依赖故障误报成“用户不存在”或零计数。
- profile 不发布跨域事件，也不承担 owner 数据的修复和补偿。

## 关键代码

- `profile.controller.UserProfileController`
- `profile.application.UserProfileQueryApplicationService`
- `profile.application.result.UserProfilePageResult`
- `user.api.query.UserProfileQueryApi`
- `social.api.query.SocialLikeQueryApi`
- `social.api.query.SocialFollowQueryApi`
- `content.api.query.PostReadQueryApi`
- `growth.api.query.UserLevelQueryApi`
