# legacy-community 功能对齐矩阵（对外功能 SSOT）

Directory: `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/`

> 目标：以旧单体（legacy-community）为“验收基准”，把所有对外入口（页面路由/JSON 接口/管理操作）映射到新体系（Vue3 + gateway + 微服务）的实现位置，并标注缺口与迁移路径。

---

## 1. 旧单体入口 → 新体系映射（概览）

### 1.1 认证与账号（legacy: LoginController）

| legacy 路径/能力 | 旧实现 | 新目标入口 | 新归属服务 | 状态 |
|---|---|---|---|---|
| `GET /register` 注册页 | legacy Thymeleaf | Vue3 注册页 | frontend | 待补齐 |
| `POST /register` 注册 | legacy | `POST /api/auth/register` | auth-service | 待补齐 |
| `GET /activation/{userId}/{code}` 激活 | legacy | `GET /api/auth/activation/{userId}/{code}` | auth-service | 待补齐 |
| `GET /kaptcha` 验证码 | legacy | `GET /api/auth/kaptcha` | auth-service | 待补齐 |
| `GET /login` 登录页 | legacy Thymeleaf | Vue3 登录页 | frontend | 已有（LoginView） |
| `POST /login` 登录（含验证码） | legacy | `POST /api/auth/login`（可选验证码） | auth-service | 部分（缺验证码） |
| `GET /logout` 退出 | legacy ticket | `POST /api/auth/logout` | auth-service | 已有（JWT + refresh） |

### 1.2 首页/帖子浏览（legacy: HomeController / DiscussPostController / CommentController）

| legacy 路径/能力 | 旧实现 | 新目标入口 | 新归属服务 | 状态 |
|---|---|---|---|---|
| `GET /index` 帖子列表（最新/热帖） | legacy | `GET /api/posts?order=latest|hot` + Vue3 列表页 | content-service + frontend | 已有（API + 页面） |
| `GET /discuss/detail/{id}` 帖子详情（含评论/回复） | legacy | `GET /api/posts/{id}` + `GET /api/posts/{id}/comments` + Vue3 详情页 | content-service + frontend | 部分（回复/用户聚合待补齐） |
| `POST /discuss/add` 发帖 | legacy | `POST /api/posts` | content-service | 已有 |
| `POST /comment/add/{postId}` 评论/回复 | legacy | `POST /api/posts/{postId}/comments` | content-service | 部分（回复/通知字段待对齐） |
| `POST /discuss/top` 置顶 | legacy | `POST /api/posts/{postId}/top` | content-service | 待补齐 |
| `POST /discuss/wonderful` 加精 | legacy | `POST /api/posts/{postId}/wonderful` | content-service | 待补齐 |
| `POST /discuss/delete` 删除 | legacy | `POST /api/posts/{postId}/delete` | content-service | 待补齐 |

### 1.3 点赞与关注（legacy: LikeController / FollowController）

| legacy 路径/能力 | 旧实现 | 新目标入口 | 新归属服务 | 状态 |
|---|---|---|---|---|
| `POST /like` 点赞 toggle（返回 likeCount/likeStatus） | legacy | `POST /api/likes/toggle`（兼容）或 `POST /api/likes`（显式 liked） | social-service | 待补齐 |
| `POST /follow` 关注 | legacy | `POST /api/follows` | social-service | 已有（API 语义不同） |
| `POST /unfollow` 取消关注 | legacy | `DELETE /api/follows?entityType&entityId` | social-service | 已有（API 语义不同） |
| `GET /followees/{userId}` 关注列表页 | legacy | `GET /api/follows/{userId}/followees` + Vue3 页面 | social-service + frontend | 待补齐 |
| `GET /followers/{userId}` 粉丝列表页 | legacy | `GET /api/follows/{userId}/followers` + Vue3 页面 | social-service + frontend | 待补齐 |

### 1.4 私信与通知（legacy: MessageController）

| legacy 路径/能力 | 旧实现 | 新目标入口 | 新归属服务 | 状态 |
|---|---|---|---|---|
| `GET /letter/list` 会话列表页（含未读/会话消息数/对端用户） | legacy | `GET /api/messages/conversations`（聚合）+ Vue3 页面 | message-service + frontend | 部分（聚合字段待补齐） |
| `GET /letter/detail/{conversationId}` 私信详情页 | legacy | `GET /api/messages/conversations/{conversationId}` + Vue3 页面 | message-service + frontend | 部分（对端用户/已读逻辑待对齐） |
| `POST /letter/send` 按用户名发私信 | legacy | `POST /api/messages/by-username`（兼容） | message-service | 待补齐 |
| `GET /notice/list` 通知聚合页 | legacy | `GET /api/notices?topic=comment|like|follow` + Vue3 页面 | message-service + frontend | 部分（聚合结构待对齐） |
| `GET /notice/detail/{topic}` 通知详情页 | legacy | `GET /api/notices?topic=...` + Vue3 页面 | message-service + frontend | 部分 |

### 1.5 统计（legacy: DataController）

| legacy 路径/能力 | 旧实现 | 新目标入口 | 新归属服务 | 状态 |
|---|---|---|---|---|
| `GET /data` 统计页 | legacy | Vue3 统计页 | frontend | 待补齐 |
| `POST /data/uv` UV | legacy | `GET /api/analytics/uv?start&end` | analytics-service | 已有（需采集链路对齐） |
| `POST /data/dau` DAU | legacy | `GET /api/analytics/dau?start&end` | analytics-service | 已有（需采集链路对齐） |

---

## 2. 关键缺口（必须补齐后才可下线 legacy）

1. 账号体系：注册/激活/验证码/邮件链路（auth-service）
2. 帖子管理：置顶/加精/删除与权限审计（content-service + gateway）
3. 点赞/关注：兼容旧 toggle 行为输出（social-service）
4. 私信/通知：会话/通知聚合结构对齐（message-service + frontend）
5. 搜索：必须切换到 Elasticsearch 存储并对齐高亮/分页体验（search-service）
6. 观测/告警/回滚/备份/压测：必须可 compose 复现并演练（deploy/）
