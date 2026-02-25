# Task List: social-service Redis 原子性与一致性修复

Directory: `.helloagents/archive/2026-02/202602091424_social_redis_atomicity/`

---

## 1. social-service Follow（Redis）
- [√] 1.1 将 `RedisFollowRepository.follow/unfollow` 改为 Lua 原子脚本执行（双 ZSet 同步 + 幂等自愈），文件：`social-service/src/main/java/com/nowcoder/community/social/follow/RedisFollowRepository.java`，验证 why.md#core-scenarios 下 Follow 场景
- [√] 1.2 补齐 `InMemoryFollowRepository.follow` 并发幂等语义（避免 check-then-act），文件：`social-service/src/main/java/com/nowcoder/community/social/follow/InMemoryFollowRepository.java`

## 2. social-service Like（Redis）
- [√] 2.1 扩展 `LikeRepository`：新增“目标状态 set”式 API（默认实现兼容 DB/memory），文件：`social-service/src/main/java/com/nowcoder/community/social/like/LikeRepository.java`
- [√] 2.2 实现 `RedisLikeRepository` 的原子 setLike（关系 Set + 计数 key 同脚本），文件：`social-service/src/main/java/com/nowcoder/community/social/like/RedisLikeRepository.java`
- [√] 2.3 改造 `LikeService.setLike` 写路径：使用仓储 setLike 返回值决定计数/事件副作用，不再独立调用 `incrementUserLikeCount`，文件：`social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`

## 3. 事件一致性补偿（Redis 模式）
- [√] 3.1 在 Redis 存储模式下：若事件发布（Outbox enqueue）失败，对已写入的 Redis 状态执行 best-effort 回滚，文件：`social-service/src/main/java/com/nowcoder/community/social/follow/FollowService.java`
- [√] 3.2 在 Redis 存储模式下：若事件发布失败，对已写入的 Redis 状态执行 best-effort 回滚，文件：`social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`

## 4. Security Check
- [√] 4.1 执行安全检查（G9）：输入校验、敏感信息、权限控制、避免 EHRB 风险；重点检查 Lua 脚本参数与 key 构造路径

## 5. Documentation Update
- [√] 5.1 更新 social 模块文档，补充 Redis 模式的一致性保障与边界说明，文件：`.helloagents/modules/social.md`
- [√] 5.2 更新 Redis key 设计文档，补充“跨 key 原子写策略（Lua）”与限制，文件：`.helloagents/data.md`

## 6. Testing
- [√] 6.1 运行现有测试：`mvn test -pl social-service -am`（至少覆盖 LikeServiceTest/FollowServiceTest 不回归）
- [√] 6.2 增加 Redis 集成测试（Testcontainers），覆盖 Redis 模式下 follow/like 原子语义与事件失败回滚：`social-service/src/test/java/com/nowcoder/community/social/storage/RedisStorageAtomicityTest.java`
