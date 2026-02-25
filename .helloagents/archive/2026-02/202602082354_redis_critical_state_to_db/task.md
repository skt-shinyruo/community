# Task List: Redis 关键状态迁移到 MySQL（幂等/refresh）+ 故障降级隔离

Directory: `.helloagents/archive/2026-02/202602082354_redis_critical_state_to_db/`

---

## 1. common（幂等抽象 + MySQL Store）
- [√] 1.1 改造 `IdempotencyGuard`：通过依赖注入获取 `IdempotencyStore`，移除构造函数内硬编码 Redis store（`common/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`），verify why.md#req-idempotency-db
- [√] 1.2 新增 `JdbcIdempotencyStore`（含状态机与 TTL 字段语义）并提供条件装配/配置项（`common/src/main/java/com/nowcoder/community/common/idempotency/JdbcIdempotencyStore.java` + auto-config），verify why.md#req-idempotency-db
- [√] 1.3 增加幂等 store 的指标口径（success/duplicate/concurrent_conflict/store_error/degraded），并确保跨服务一致（`common/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`），depends on task 1.1

## 2. content-service（幂等落库 + schema）
- [√] 2.1 新增 `http_idempotency` 表到内容域 schema（`deploy/mysql-init/020_schema_content.sql`），verify why.md#req-idempotency-db
- [√] 2.2 同步测试 schema（`content-service/src/test/resources/schema.sql`），depends on task 2.1
- [√] 2.3 配置 content-service 使用 DB 幂等 store（`content-service/src/main/resources/application.yml` 或 nacos 模板 `deploy/nacos-config/content-service.yaml`），depends on task 1.2

## 3. message-service（幂等落库 + schema）
- [√] 3.1 新增 `http_idempotency` 表到消息域 schema（`deploy/mysql-init/030_schema_message.sql`），verify why.md#req-idempotency-db
- [√] 3.2 同步测试 schema（`message-service/src/test/resources/schema.sql`），depends on task 3.1
- [√] 3.3 配置 message-service 使用 DB 幂等 store（`message-service/src/main/resources/application.yml` 或 nacos 模板 `deploy/nacos-config/message-service.yaml`），depends on task 1.2

## 4. auth-service（refresh token SSOT=MySQL + 安全能力降级）
- [√] 4.1 设计并实现 refresh token DB store 模式（建议：`db` + 迁移期 `dual`），并将 refresh token 落库仅存 hash（`auth-service/src/main/java/com/nowcoder/community/auth/service/*RefreshTokenStore*.java`），verify why.md#req-refresh-db
- [√] 4.2 调整 refresh/captcha/password-reset 配置默认值与降级开关（`auth-service/src/main/resources/application.yml`），verify why.md#req-auth-degrade
- [√] 4.3 登录限流与验证码在存储异常时的降级语义落地（避免 503 阻断登录主链路，需打点可观测）（`auth-service/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java` + `CaptchaService`），depends on task 4.2

## 5. user-service（若采用内部托管 refresh token store）
- [√] 5.1 新增 refresh token 表（identity schema）与索引（`deploy/mysql-init/010_schema_identity.sql`），verify why.md#req-refresh-db
- [√] 5.2 同步测试 schema（`user-service/src/test/resources/schema.sql`），depends on task 5.1
- [√] 5.3 新增 internal session API（store/find/revoke/revokeFamily）与 service/repository（`user-service/src/main/java/com/nowcoder/community/user/api/*` + `user-service/src/main/java/com/nowcoder/community/user/service/*`），depends on task 5.1

## 6. gateway（限流 Redis 故障降级）
- [√] 6.1 将网关限流的 Redis 异常处理升级为“降级模式”（按规则 fail-open 或本地 fallback），并补齐 outcome 指标（`gateway/src/main/java/com/nowcoder/community/gateway/filter/GatewayRateLimitGlobalFilter.java`），verify why.md#req-gateway-ratelimit-degrade
- [√] 6.2 配置项补齐：支持 per-rule 降级策略/或至少明确全局开关默认值（`gateway/src/main/resources/application.yml`），depends on task 6.1

## 7. Security Check
- [√] 7.1 执行安全检查（凭据不落明文、internal API 暴露面、降级 fail-open 的风险接受与告警、输入校验），verify how.md#security-and-performance

## 8. Documentation Update
- [√] 8.1 同步更新知识库：`.helloagents/modules/common.md`、`.helloagents/modules/auth-service.md`、`.helloagents/modules/gateway.md`（对齐默认 store 与降级策略），并在 `.helloagents/CHANGELOG.md` 记录变更

## 9. Testing
- [-] 9.1 幂等：新增并发/重复请求用例（建议优先单测 + 必要时集成测试），覆盖发帖/评论/私信的 required 幂等在 Redis 故障下仍可用
- [-] 9.2 auth refresh：覆盖 rotate/revokeFamily/dual 模式兼容（包含旧 token 仍可刷新）
- [-] 9.3 gateway：模拟 Redis 不可用时的降级路径与指标打点（不再返回 503 阻断写入口）

---

### Notes
- 4.1 中提到的 `dual` 迁移模式本轮未实现；当前默认使用 `db`（并由 user-service 托管 refresh token 状态）。
- 9.* 未补充专项测试用例；已通过全仓 `mvn test` 完成回归验证。
