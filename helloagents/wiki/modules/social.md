# social

## Purpose
提供点赞、关注/粉丝、拉黑等社交关系能力。

## Module Overview
- **Responsibility：** 点赞/取消点赞；统计实体点赞数；关注/取关；关注列表/粉丝列表；拉黑/解除拉黑
- **Status：** ✅Stable
- **Last Updated：** 2026-02-04

## Specifications

### Requirement: 点赞
**Module:** social
用户可对帖子/评论点赞，并可取消。

#### Scenario: 点赞帖子
前置条件：用户已登录
- MySQL 作为关系 SSOT（默认）；Redis 可选作为缓存/计数加速层（非 SSOT）
- 反骚扰一致性：若双方存在任一方向拉黑关系，禁止创建点赞（返回 403，不发布 LikeCreated）
- 更新被赞用户的获赞计数（DB 侧具备唯一约束兜底幂等）
- 写路径契约可信：服务端通过 content-service internal resolve 解析 entity 的 owner/postId 并校验存在性（禁止信任客户端注入字段）
- 触发社交事件：`LikeCreated/LikeRemoved`，默认使用 Outbox 可靠投递（可通过开关回滚到 After-Commit 直发）

### Requirement: 关注/粉丝
**Module:** social
用户可关注用户并查看关注列表/粉丝列表。

#### Scenario: 关注用户
前置条件：用户已登录
- MySQL 作为关系 SSOT（默认）；Redis 可选作为缓存/加速层
- follow 写路径收敛：当前仅支持关注 USER（避免跨域 entity 造成信任边界与脏关系问题）
- 反骚扰一致性：若双方存在任一方向拉黑关系，禁止创建关注（返回 403，不发布 FollowCreated）
- 触发关注事件：优先使用 Outbox 可靠投递（可通过开关回滚到 After-Commit 直发）

### Requirement: 拉黑/反骚扰
**Module:** social
用户可拉黑/解除拉黑对方，用于反骚扰与私信/回复等写路径约束。

#### Scenario: 拉黑用户
- MySQL 作为关系 SSOT（默认）；Redis 可选作为缓存/加速层
- 提供内部接口查询 A/B 拉黑关系，供 message-service/content-service 写路径校验
- 社交写路径自校验：在点赞/关注“创建关系”场景同样执行拉黑校验，避免拉黑后仍产生通知类互动副作用

## API Interfaces（现状）
- `POST /api/likes`（显式 liked=true/false，幂等）
- `GET /api/likes/status`、`GET /api/likes/count`、`GET /api/likes/users/{userId}/count`
- batch（用于 Feed 聚合读避免 N+1）：`GET /api/likes/counts`、`GET /api/likes/statuses`
- `POST /api/follows`、`DELETE /api/follows`
- `GET /api/follows/status`
- `GET /api/follows/{userId}/followees`、`GET /api/follows/{userId}/followers`
- `GET /api/follows/{userId}/followees/count`、`GET /api/follows/{userId}/followers/count`
- `POST /api/blocks`（拉黑）
- `DELETE /api/blocks?userId=`（解除拉黑）
- `GET /api/blocks`（我的拉黑列表）
- `GET /api/blocks/status?userId=`（查询是否已拉黑）
- internal（仅服务间调用；开发阶段默认放行）：
  - `GET /internal/social/blocks/relation?userIdA=&userIdB=`
  - `GET /internal/social/likes/scan?entityType=&afterEntityId=&afterUserId=&limit=`（供 content-service 回填 Redis 点赞投影）
  - internal read（供聚合展示，避免跨服务透传 Authorization）：
    - `GET /internal/social/read/likes/users/{userId}/count`
    - `GET /internal/social/read/follows/{userId}/followees/count?entityType=3`
    - `GET /internal/social/read/follows/{userId}/followers/count?entityType=3`
    - `GET /internal/social/read/follows/status?userId=&entityType=3&entityId=`
    - `GET /internal/social/read/users/{userId}/profile-stats?viewerId=`（用户主页聚合，一次返回获赞/关注/粉丝/关注状态）
  - outbox 运维：
    - `GET /internal/social/outbox/health`
    - `POST /internal/social/outbox/replay?limit=200`

## Data Models
### Storage Modes（重要）
- `social.storage=db`：默认推荐，MySQL 为 SSOT（生产建议）
- `social.storage=redis`：仅用于本地/压测/演示（非 SSOT，不推荐在 prod 使用）
- `social.storage=memory`：仅用于单测/演示

### Redis Keys
（详见 `helloagents/wiki/data.md` 的 “Redis Key 设计” 小节；注意：Redis 不再作为 SSOT）

## Dependencies
- user（用户资料用于列表展示）
- content-service internal（resolve entity 元信息：owner/postId/存在性；用于点赞写路径与事件 payload 可信化）
- message（点赞/关注通知）
- infra（Redis/Kafka）

## Change History
- 2026-01-18：Kafka 事件发布统一 After-Commit 策略（在事务活跃时 commit 后发送），并补齐发布失败指标用于观测。
- 2026-01-23：新增拉黑/反骚扰能力（Redis Set 存储 + 对外 API + internal 关系查询）。
- 2026-01-28：固化 DB 为默认 SSOT（避免 Redis-only 误启用）；补齐 internal read API；Outbox 默认开启（部署侧）。
- 2026-02-01：新增 `LikeRemoved` 事件并发布；点赞写路径改为服务端 resolve entity 元信息（禁止客户端注入、校验存在性）；新增 internal likes scan 供下游回填投影；follow 写路径收敛仅支持 USER；补齐 MyBatis `mapper-locations`/`map-underscore-to-camel-case` 以确保 outbox XML mapper 生效。
- 2026-02-02：新增 internal 用户主页聚合 read API（profile-stats），供 user-service 单次调用获取获赞/关注/粉丝/关注状态，降低 fan-out。
- 2026-02-03：Outbox 认领升级支持 `FOR UPDATE SKIP LOCKED`（可配置回退），降低多实例 relay 并发时的锁等待与头阻塞风险；outbox 运维入口保留（开发阶段默认放行）。
- 2026-02-04：补齐反骚扰语义一致性：点赞/关注在“创建关系”场景增加拉黑校验（403），避免拉黑后仍产生互动与通知副作用。
