# Technical Design: social-service Redis 存储原子性与一致性修复

## Technical Solution

### Core Technologies
- Java / Spring Boot
- Spring Data Redis `StringRedisTemplate`
- Redis Lua Script（`EVAL`）+ `DefaultRedisScript<Long>`
- MySQL Outbox（现状保持；Redis 模式下做 best-effort 一致性补偿）

### Implementation Key Points
1. **Follow：Lua 原子双写 + 自愈**
   - 使用 Lua 脚本将“关注列表（followee ZSet）+ 粉丝列表（follower ZSet）”的写入合并为单次原子执行。
   - 幂等重复 follow：返回 `0`，并尽可能补齐缺失的一侧（自愈双写不一致的历史遗留/异常窗口）。
   - unfollow 同理：单次脚本删除两侧并返回是否发生状态变更。

2. **Like：Lua 原子关系 + 计数**
   - 新增仓储层 API：以“目标状态 set”为语义（liked=true/false）。
   - Redis 实现通过 Lua 脚本将 `SADD/SREM` 与 `INCRBY` 合并为单次原子执行，并返回是否发生状态变更（1/0）。
   - 计数保持非负（可选：在脚本中对负数进行 clamp 到 0，避免历史漂移放大）。

3. **Service：副作用收敛与补偿**
   - 事件发布严格依赖仓储返回的“是否发生状态变更”（created/removed），杜绝并发下重复事件。
   - Redis 存储模式下，如事件入队（Outbox enqueue / JSON 序列化等）抛错：
     - 对本次已成功写入的 Redis 状态执行 best-effort 回滚（follow -> unfollow；like -> setLike(false)；unlike -> setLike(true)）。
     - 回滚失败时记录日志并继续抛出原始异常（避免吞错）。

4. **兼容性与回归**
   - DB 存储模式：保持现有事务语义（DB SSOT + Outbox 同事务），不引入额外回滚逻辑。
   - memory 存储模式：补齐 InMemoryFollowRepository 的并发幂等语义（例如 `putIfAbsent`），与 Redis/DB 行为一致。

## Architecture Decision ADR
### ADR-1: Redis 写路径使用 Lua 脚本实现原子性
**Context：**
- Redis 存储模式当前存在 check-then-act 与跨 key 多次写入，导致重复事件与计数漂移。
- MULTI/WATCH 方案复杂且需要重试，吞吐与实现复杂度不利。

**Decision：**
- 使用 Lua 脚本将“跨多个 key 的复合写”收敛为单次原子操作，并以脚本返回值作为“是否发生状态变更”的唯一判据。

**Rationale：**
- 语义清晰、实现集中、执行原子、避免客户端重试窗口导致的副作用漂移。

**Alternatives：**
- 方案：WATCH + MULTI/EXEC + 自旋重试 → 拒绝原因：实现复杂、重试成本高、对高并发不友好。
- 方案：调整 `social.storage=redis` 语义为“仅缓存/投影” → 拒绝原因：行为变更大，可能影响现有本地/压测用法（可作为后续演进方案）。

**Impact：**
- Redis 模式写路径一致性显著提升（关系/计数/事件“是否发布”至少在 Redis 内部对齐）。
- 跨 Redis 与 DB Outbox 仍无法强原子，只能 best-effort 补偿并明确边界。

## Security and Performance
- **Security：**
  - Lua 脚本仅接收服务端拼装的 key 与参数，避免客户端注入 key。
  - 保持现有“创建关系前的拉黑校验”逻辑不变。
- **Performance：**
  - 通过单次脚本减少 RTT（原本 2-3 次 Redis round-trip → 1 次）。
  - 脚本逻辑保持常数复杂度（O(1)）。

## Testing and Deployment
- **Testing：**
  - 单元：补齐 InMemory 幂等语义；增加对 service 层“幂等不重复发布事件”的断言。
  - 集成：新增 `RedisStorageAtomicityTest`（Testcontainers Redis），覆盖并发场景下 follow/like 的原子语义与“事件发布失败回滚”关键断言（无 Docker 环境自动跳过）。
- **Deployment：**
  - 无需变更配置项；仅在 `social.storage=redis` 路径生效。
  - 生产建议仍使用 `social.storage=db`（DB 为 SSOT）。
