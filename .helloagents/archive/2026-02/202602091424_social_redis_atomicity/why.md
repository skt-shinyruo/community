# Change Proposal: social-service Redis 存储并发与一致性修复（防重复事件、计数漂移）

## Requirement Background
当 `social.storage=redis` 时，当前 Redis 存储实现存在典型的并发与一致性缺口，导致：
1. **重复事件：** follow 的写路径存在非原子 check-then-act，并发下可能多次返回“创建成功”，从而重复发布 FollowCreated。
2. **计数漂移：** like 的写路径将“关系写入”和“获赞计数更新”拆成两次独立 Redis 操作，任意中途异常/超时/重试都会出现“关系已变更但计数/事件未对齐”。

虽然 `social.storage=db`（DB 为 SSOT）是默认推荐且用于生产建议路径，但 Redis 路径一旦在本地/压测启用，上述问题会真实发生，并且单测难以稳定覆盖 Redis 的原子语义与并发窗口。

## Change Content
1. **Follow（Redis）原子化：** 使用 Lua 脚本将“写入 followee ZSet + 写入 follower ZSet”收敛为单次原子操作，并在幂等重入时尽可能自愈双写不一致。
2. **Like（Redis）原子化：** 使用 Lua 脚本将“写入实体点赞关系（Set）+ 更新被赞用户计数（String counter）”收敛为单次原子操作，消除中途失败导致的计数漂移。
3. **Service 层副作用一致性：** follow/like 的事件发布严格基于仓储返回的“是否发生状态变更”，避免重复发布；在 Redis 存储模式下，对事件入队失败做 best-effort 回滚，尽量保持“状态与事件同生共死”的语义。
4. **可验证性补强：** 增加（可选）Redis 集成测试或最小可行的仓储测试，以覆盖并发与原子语义的关键断言。
5. **知识库同步：** 更新 social 模块与 Redis key 设计说明，明确 Redis 模式的一致性保障与边界（尤其是跨 Redis/DB 的原子不可达场景与补偿策略）。

## Impact Scope
- **Modules：**
  - `social-service`（关注/点赞 Redis 存储与写路径）
  - `.helloagents/modules/social.md`（行为边界与说明）
  - `.helloagents/data.md`（Redis key 与一致性说明补充）
- **Files（预期）：**
  - `social-service/src/main/java/com/nowcoder/community/social/follow/RedisFollowRepository.java`
  - `social-service/src/main/java/com/nowcoder/community/social/follow/InMemoryFollowRepository.java`（补齐幂等语义的一致实现）
  - `social-service/src/main/java/com/nowcoder/community/social/follow/FollowService.java`
  - `social-service/src/main/java/com/nowcoder/community/social/like/LikeRepository.java`
  - `social-service/src/main/java/com/nowcoder/community/social/like/RedisLikeRepository.java`
  - `social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`
  - `.helloagents/modules/social.md`
  - `.helloagents/data.md`

## Core Scenarios

### Requirement: 关注（Redis）
**Module:** social-service
修复 Redis Follow 写路径在并发下的重复创建与双写不一致。

#### Scenario: 并发重复 follow 只触发一次副作用
条件：两个并发请求对同一 `(actorUserId, entityType=USER, entityId)` 发起 follow
- follow 仓储最多一次返回 `created=true`
- `followee:<userId>:<entityType>` 与 `follower:<entityType>:<entityId>` 两侧数据保持一致
- FollowCreated 事件最多发布一次

#### Scenario: 幂等重复 follow 不产生新副作用
条件：同一用户重复 follow 同一目标
- 返回 `created=false`
- 不重复发布 FollowCreated

### Requirement: 点赞（Redis）
**Module:** social-service
修复 Redis Like 写路径“关系 + 计数”分离导致的计数漂移、事件重复/缺失对齐问题（尽可能）。

#### Scenario: 点赞创建与获赞计数更新原子一致
条件：对 POST/COMMENT 点赞（liked=true）
- 关系写入与 `like:user:<entityUserId>` 计数更新在 Redis 侧同一原子单元完成
- 幂等重复 setLike(liked=true) 不重复增长计数、不重复发布 LikeCreated

#### Scenario: 取消点赞与获赞计数回滚原子一致
条件：取消点赞（liked=false）
- 关系删除与计数递减在 Redis 侧同一原子单元完成
- 幂等重复 setLike(liked=false) 不重复递减计数、不重复发布 LikeRemoved

## Risk Assessment
- **Risk：跨存储原子性不可达（Redis 状态 vs DB Outbox）。**
  - **Mitigation：** Redis 存储模式下，对事件入队失败执行 best-effort 回滚 Redis 状态；并在文档中明确该模式仅用于本地/压测/演示，生产应以 DB 为 SSOT。
- **Risk：Lua 脚本错误引入逻辑回归。**
  - **Mitigation：** 脚本逻辑保持最小化、覆盖 follow/unfollow/like/unlike 的关键断言；尽可能补齐集成测试或在压测环境进行回归验证。

