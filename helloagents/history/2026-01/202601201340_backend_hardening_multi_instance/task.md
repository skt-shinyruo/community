# Task List: 后端多实例上线加固（同站不同源 / HTTP / Docker Compose）

Directory: `helloagents/plan/202601201340_backend_hardening_multi_instance/`

---

## 1. Gateway（CORS / OriginGuard / 安全基线）
- [√] 1.1 对齐 CORS：允许 credentials 且 origin 精确匹配 `http://localhost:5173`，以 `gateway/src/main/resources/application.yml` 为 SSOT，verify why.md#r1-cors-cookie-refresh + why.md#r1s1-fe-gateway-cookie-login-refresh
- [√] 1.2 对齐 OriginGuard allowlist 与 CORS allowlist 的配置来源（避免双点漂移），verify why.md#r1s2-origin-guard-allowlist
- [√] 1.3（可选）补齐“allowlist 为空时 fail-open”的可观测/告警策略，避免误配置静默放行，verify why.md#r1s2-origin-guard-allowlist

## 2. Frontend（跨源 cookie 的正确使用）
- [√] 2.1 统一 http client：对登录/刷新/登出启用 `withCredentials/credentials: include`，并验证浏览器能保存/携带 refresh cookie，verify why.md#r1s1-fe-gateway-cookie-login-refresh, files: `frontend/src/api/http.js`（或等价文件）
- [√] 2.2 增加一个最小“登录态回归”E2E/手工用例说明（避免后续改动破坏 cookie 行为），verify why.md#r1s1-fe-gateway-cookie-login-refresh, docs: `docs/SECURITY.md` 或 `helloagents/wiki/api.md`

## 3. user-service（身份域 SSOT + internal 鉴权接口）
- [√] 3.1 新增 internal-token 配置与校验基线（或接入统一 Filter），verify why.md#r3-internal-api-hardening + why.md#r3s1-internal-token-enforced
- [√] 3.2 新增内部鉴权接口 `POST /internal/users/authenticate`（校验用户名密码、状态、返回 roles），verify why.md#r2s1-login-via-user-service-internal-auth
- [√] 3.3 新增内部会话画像接口 `GET /internal/users/{id}/session-profile`（返回 status/roles），供 refresh 使用，verify why.md#r2s2-refresh-check-user-status
- [√] 3.4 密码哈希升级：实现 BCrypt/Argon2 + legacy 渐进 rehash（登录成功后写回），verify why.md#r6s1-gradual-rehash-on-login, files: `user-service/src/main/java/...` + `deploy/mysql-init/010_schema_identity.sql`（若需字段调整）

## 4. auth-service（无库编排：改为调用 user-service internal API）
- [√] 4.1 新增 `user-service` internal client（超时+指标+traceId 透传），verify why.md#r4-sync-call-resilience-standardize + why.md#r4s1-downstream-unavailable-fast-fail
- [√] 4.2 登录流程改造：移除对 `UserMapper` 的直接依赖，改为 internal authenticate，verify why.md#r2s1-login-via-user-service-internal-auth, files: `auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- [√] 4.3 refresh 流程改造：refresh 时通过 internal session-profile 获取 status/roles（禁用立即生效），verify why.md#r2s2-refresh-check-user-status
- [-] 4.4 迁移与开关：加入“数据库直连 vs internal API”双路径切换开关，支持灰度与回滚，verify why.md#r2-auth-service-stateless-no-mysql
- [√] 4.5 断库：移除 auth-service datasource/MyBatis 依赖与 `auth-service/src/main/java/com/nowcoder/community/auth/user/UserMapper.java`，并更新配置/compose，verify why.md#r2-auth-service-stateless-no-mysql

## 5. message-service（同步调用韧性对齐）
- [√] 5.1 为 `message-service` RestTemplate 增加确定性超时（connect/read），并补齐可观测指标与降级语义，verify why.md#r4s1-downstream-unavailable-fast-fail, files: `message-service/src/main/java/com/nowcoder/community/message/config/MessageRestClientConfig.java` + `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`
- [√] 5.2（可选）为跨服务调用增加 traceId 透传拦截器（`X-Trace-Id`），verify why.md#r4-sync-call-resilience-standardize

## 6. common（internal API 与 Kafka traceId 复用能力）
- [√] 6.1 抽取一个可复用的 internal-token 校验组件（Servlet Filter/Interceptor），并在各服务接入，verify why.md#r3s1-internal-token-enforced, files: `common/src/main/java/...`
- [√] 6.2 抽取 Kafka 消费端 traceId 注入工具（从 envelope 读取 `traceId` 写入 MDC/TraceId，finally 清理），并在各 consumer 复用，verify why.md#r5s3-traceid-in-kafka-log, files: `common/src/main/java/...`

## 7. content-service（Kafka 消费一致性：幂等 + DLQ + traceId）
- [√] 7.1 移除 `seenEventIds` JVM 去重（`content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`），改为持久化幂等或天然幂等（依赖 `RedisPostScoreQueue` 的 set 语义），verify why.md#r5s1-duplicate-delivery-idempotent
- [√] 7.2 为 content-service 补齐 Kafka DefaultErrorHandler + DLQ（对齐 message/search），verify why.md#r5s2-consume-failure-to-dlq
- [√] 7.3 content-service Kafka consumer 注入 traceId（复用 common 工具），verify why.md#r5s3-traceid-in-kafka-log

## 8. search-service / message-service（Kafka traceId 对齐）
- [√] 8.1 search-service `PostEventConsumer` 注入 traceId（复用 common 工具），verify why.md#r5s3-traceid-in-kafka-log, file: `search-service/src/main/java/com/nowcoder/community/search/kafka/PostEventConsumer.java`
- [√] 8.2 message-service Kafka listener/processor 注入 traceId（复用 common 工具），verify why.md#r5s3-traceid-in-kafka-log, file: `message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventProcessor.java`

## 9. deploy（compose 网络隔离 + 配置治理）
- [√] 9.1 docker compose：仅暴露 gateway，对外关闭各微服务 `ports:` 映射（保留内部网络互通），verify why.md#r3s2-compose-network-isolation, files: `deploy/docker-compose*.yml`（按实际文件）
- [√] 9.2 统一 internal-token、JWT secret 的配置落地与命名（Nacos/环境变量），增加启动期校验，verify why.md#r7s1-env-drift-protection, files: `deploy/nacos-config/*.yaml` + 各服务 `application.yml`

## 10. P1：Outbox（可靠投递）
- [-] 10.1 设计并落地 outbox_event 表（content/social 等生产者侧），verify why.md#r8-outbox-reliable-delivery
- [-] 10.2 实现 relay worker：扫描 outbox 并投递 Kafka，失败重试与指标可观测，verify why.md#r8s1-commit-then-kafka-down-no-loss
- [-] 10.3 演练：Kafka 宕机/恢复后，事件不丢且下游幂等无重复副作用，verify why.md#r8s1-commit-then-kafka-down-no-loss

## 11. Security Check
- [√] 11.1 执行安全检查（鉴权/权限、敏感信息、CSRF 风险、token 泄露面、最小权限账号、internal API 暴露面），按 G9 记录风险与结论

## 12. Documentation Update（SSOT 同步）
- [√] 12.1 更新 `helloagents/wiki/modules/*`：补齐 internal API、调用链路、Kafka/DLQ 约定与运维说明
- [√] 12.2 更新 `helloagents/CHANGELOG.md`：记录本次上线加固与边界收敛

## 13. Testing
- [?] 13.1 集成测试：同站不同源下 login/refresh/logout 的 cookie 行为（含 withCredentials + CORS + OriginGuard），verify why.md#r1s1-fe-gateway-cookie-login-refresh
- [?] 13.2 集成测试：auth→user internal 鉴权链路（成功/失败/禁用/角色变化），verify why.md#r2-auth-service-stateless-no-mysql
- [?] 13.3 集成测试：Kafka 重试→DLQ→回放（至少覆盖 message/search/content 各 1 条），verify why.md#r5s2-consume-failure-to-dlq
