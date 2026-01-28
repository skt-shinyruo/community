# Task List: 后端架构治理（5项问题）

Directory: `helloagents/plan/202601281336_backend_arch_governance_5issues/`

---

## 0. 基线核对（防重复劳动）
- [√] 0.1 对照 why.md 与代码现状：确认 5 项问题对应的“真实差距点”（默认值/开关/断裂调用/内存风险/配置兜底），补充到 `helloagents/plan/202601281336_backend_arch_governance_5issues/how.md` 的 Current State Snapshot（如需微调），verify why.md#r1-social-persistence-ssot + why.md#r2-social-outbox-reliable-produce + why.md#r3-internal-client-governance + why.md#r4-gateway-analytics-pipeline + why.md#r5-internal-token-security

## 1. social-service：SSOT 默认值治理（避免 Redis-only 误启用）
- [√] 1.1 移除 RedisLikeRepository 的缺省兜底：将 `@ConditionalOnProperty(... matchIfMissing = true)` 改为显式启用（matchIfMissing=false），file: `social-service/src/main/java/com/nowcoder/community/social/like/RedisLikeRepository.java`，verify why.md#r1-social-persistence-ssot
- [√] 1.2 移除 RedisFollowRepository 的缺省兜底：file: `social-service/src/main/java/com/nowcoder/community/social/follow/RedisFollowRepository.java`，verify why.md#r1-social-persistence-ssot, depends on 1.1（策略一致）
- [√] 1.3 移除 RedisBlockRepository 的缺省兜底：file: `social-service/src/main/java/com/nowcoder/community/social/block/RedisBlockRepository.java`，verify why.md#r1-social-persistence-ssot, depends on 1.1

- [√] 1.4 固化 DB 为缺省实现：DbLikeRepository 增加 `matchIfMissing=true`，file: `social-service/src/main/java/com/nowcoder/community/social/like/DbLikeRepository.java`，verify why.md#r1-social-persistence-ssot
- [√] 1.5 固化 DB 为缺省实现：DbFollowRepository 增加 `matchIfMissing=true`，file: `social-service/src/main/java/com/nowcoder/community/social/follow/DbFollowRepository.java`，verify why.md#r1-social-persistence-ssot, depends on 1.4
- [√] 1.6 固化 DB 为缺省实现：DbBlockRepository 增加 `matchIfMissing=true`，file: `social-service/src/main/java/com/nowcoder/community/social/block/DbBlockRepository.java`，verify why.md#r1-social-persistence-ssot, depends on 1.4

- [√] 1.7 配置显式化与边界说明：补齐 `social.storage=db|redis|memory` 的用途说明（强调 prod 推荐 db；redis/memory 仅用于非 prod/压测/演示），files: `social-service/src/main/resources/application.yml` + `deploy/nacos-config/social-service.yaml`，verify why.md#r1-social-persistence-ssot
- [-] 1.8（可选 P1）启动期防误配：当 `social.storage=redis` 且 profile=prod 时 fail-fast（或至少 warn），file: `social-service/src/main/java/com/nowcoder/community/social/config/SocialStorageGuard.java`（新增），verify why.md#r1-social-persistence-ssot
  > Note: 本次优先通过“默认值固化（DB matchIfMissing=true + Redis 非缺省）+ 部署配置显式 social.storage=db”降低误配风险，启动期强校验作为后续增强点。

## 2. Outbox：可靠投递默认开启（content/social）
- [√] 2.1 social-service：在部署侧默认开启 outbox（保留灰度/回滚开关），file: `deploy/nacos-config/social-service.yaml`，verify why.md#r2-social-outbox-reliable-produce
- [√] 2.2 content-service：在部署侧默认开启 outbox（保留灰度/回滚开关），file: `deploy/nacos-config/content-service.yaml`，verify why.md#r2-social-outbox-reliable-produce, depends on 2.1（统一策略）

- [√] 2.3 content-service：补齐 outbox 运维接口（health/replay），file: `content-service/src/main/java/com/nowcoder/community/content/outbox/InternalOutboxController.java`（新增），verify why.md#r2s2-retry-and-failure-visible
- [√] 2.4 outbox 可观测：补齐 backlog/failed 的指标（counter/gauge 任一即可，但需要能用于告警阈值），files: `content-service/src/main/java/com/nowcoder/community/content/outbox/OutboxRelayJob.java` + `social-service/src/main/java/com/nowcoder/community/social/outbox/OutboxRelayJob.java`，verify why.md#r2s2-retry-and-failure-visible

## 3. internal clients：统一错误码透传 + 降级语义 + 收敛 user-service 聚合调用
- [√] 3.1 common：增强 Result 解包逻辑，保留下游 code/message/traceId（使用 SimpleErrorCode），file: `common/src/main/java/com/nowcoder/community/common/web/internalclient/InternalClientSupport.java`，verify why.md#r3-internal-client-governance
- [√] 3.2 common：统一 outcome 语义（至少区分 success/error/timeout/degraded/forbidden），并在 record 中作为 tags 输出，file: `common/src/main/java/com/nowcoder/community/common/web/internalclient/InternalClientSupport.java`，verify why.md#r3s3-observability, depends on 3.1

- [√] 3.3 content-service：SocialBlockClient 在 fail-open 时记录 degraded（而非 error），file: `content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`，verify why.md#r3s2-explicit-fallback-policy
- [√] 3.4 message-service：SocialServiceClient 在 fail-open 时记录 degraded（而非 error），file: `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`，verify why.md#r3s2-explicit-fallback-policy
- [√] 3.5 content-service：UserModerationClient 在 fail-open 时记录 degraded（而非 error），file: `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`，verify why.md#r3s2-explicit-fallback-policy

- [√] 3.6 social-service：新增 internal read API（供 user-service 聚合读取计数/状态，避免 /api + Authorization 透传），file: `social-service/src/main/java/com/nowcoder/community/social/api/InternalSocialReadController.java`（新增），verify why.md#r3-internal-client-governance
- [√] 3.7 user-service：扩展 SocialServiceClientProperties，增加 base-url/internal-token/fail-open（保留已有 timeout），file: `user-service/src/main/java/com/nowcoder/community/user/config/SocialServiceClientProperties.java`，verify why.md#r3s1-timeout-fast-fail
- [√] 3.8 user-service：重写 SocialServiceClient，使用 internal-token 调用 internal read API，移除 Authorization 透传与硬编码 BASE_URL，file: `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`，verify why.md#r3-internal-client-governance, depends on 3.6 + 3.7
- [√] 3.9 deploy：补齐 user-service 的 social-client 配置（base-url/internal-token/fail-open），file: `deploy/nacos-config/user-service.yaml`，verify why.md#r3s1-timeout-fast-fail, depends on 3.7

## 4. gateway：统计采集链路有界化 + traceId 透传
- [√] 4.1 gateway：引入 Caffeine 并暴露可调参数（max-size/ttl），files: `gateway/pom.xml` + `gateway/src/main/resources/application.yml`，verify why.md#r4s1-bounded-memory
- [√] 4.2 gateway：用 Caffeine 有界 TTL 缓存替换静态 Set（移除 static day rotate 逻辑），file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`，verify why.md#r4s1-bounded-memory, depends on 4.1
- [√] 4.3 gateway：analytics 内部调用补齐 traceId 透传（X-Trace-Id/traceparent），file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`，verify why.md#r4s2-timeout-and-limit, depends on 4.2
- [√] 4.4 gateway：新增测试覆盖“有界缓存不会无限增长 + timeout/并发限制生效 + userId 解析失败不记录”，file: `gateway/src/test/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilterTest.java`（新增），verify why.md#r4-gateway-analytics-pipeline
- [-] 4.5（可选 P2）事件化采集：gateway 发送访问事件到 Kafka，analytics-service 消费写入（彻底从在线链路剥离），files: `gateway/src/main/java/...` + `analytics-service/src/main/java/...`（需拆分为更细任务），verify why.md#r4-gateway-analytics-pipeline
  > Note: 本次聚焦“有界化 + 可观测 + traceId 透传”的风险收敛，事件化采集留作后续 P2 演进。

## 5. internal-token：按服务 token 收敛 + 轮转 runbook
- [√] 5.1 social-service：去掉对 `INTERNAL_TOKEN` 的默认兜底（生产推荐 fail-closed），并增加 previous token 配置位，file: `deploy/nacos-config/social-service.yaml`，verify why.md#r5s1-service-scoped-token
- [√] 5.2 content-service：去掉对 `INTERNAL_TOKEN` 的默认兜底（含 clients.user/clients.social），并增加 previous token 配置位，file: `deploy/nacos-config/content-service.yaml`，verify why.md#r5s1-service-scoped-token
- [√] 5.3 user-service：去掉对 `INTERNAL_TOKEN` 的默认兜底，并增加 previous token 配置位，file: `deploy/nacos-config/user-service.yaml`，verify why.md#r5s1-service-scoped-token
- [√] 5.4 Runbook：新增 internal-token 轮转与回滚步骤（current + previous 灰度窗口），file: `helloagents/wiki/runbooks/internal-token-rotation.md`（新增），verify why.md#r5s2-rotation-without-downtime
- [-] 5.5（可选 P1）启动期校验：关键服务缺失 `<segment>.internal-token` 时 fail-fast（或至少 warn），file: `common/src/main/java/com/nowcoder/community/common/internal/InternalTokenFilter.java`（或各服务独立校验类），verify why.md#r5-internal-token-security
  > Note: 当前优先通过“部署配置显式化 + runbook”落地；启动期强校验可能影响本地开发体验，后续可按环境（prod）增强。

## 6. Security Check
- [√] 6.1 执行安全检查（G9）：internal 运维接口最小授权、token 轮转窗口、全局兜底禁用策略、日志/指标高基数与注入风险，输出结论与整改清单，file: `helloagents/wiki/runbooks/security-review-20260128.md`（新增），verify why.md#r5-internal-token-security

## 7. Documentation Update（知识库 SSOT 同步）
- [√] 7.1 更新架构文档：补齐“默认值固化（DB/outbox）+ internal 聚合收敛 + gateway analytics 有界化”的图与说明，file: `helloagents/wiki/arch.md`，verify why.md#r1-social-persistence-ssot + why.md#r2-social-outbox-reliable-produce + why.md#r4-gateway-analytics-pipeline
- [√] 7.2 更新 social 模块文档：明确 storage 模式边界、internal read API、outbox 运维入口与告警建议，file: `helloagents/wiki/modules/social.md`，verify why.md#r1-social-persistence-ssot + why.md#r2-social-outbox-reliable-produce
- [√] 7.3 更新 common 模块文档：internal-token 语义、轮转窗口、InternalClientSupport 的错误码与 outcome 约定，file: `helloagents/wiki/modules/common.md`，verify why.md#r3-internal-client-governance + why.md#r5-internal-token-security
- [√] 7.4 更新 gateway/analytics 模块文档：采集链路的有界策略、指标口径与 traceId 透传，files: `helloagents/wiki/modules/gateway.md` + `helloagents/wiki/modules/analytics.md`，verify why.md#r4-gateway-analytics-pipeline

## 8. Testing
- [√] 8.1 social-service：跑通并补齐测试（DB 存储默认值、like/follow/block 基本一致性），file: `social-service/src/test/java/com/nowcoder/community/social/api/SocialControllerTest.java`（或新增专用测试），verify why.md#r1s2-read-write-consistency
- [-] 8.2 outbox：验证 Kafka 宕机→写入→恢复→自动补发；failed 可观测可重放（可先用手工演练 + 记录结果到 runbook），verify why.md#r2s1-kafka-down-not-lost + why.md#r2s2-retry-and-failure-visible
  > Note: 当前仅完成单测/集成测试与编译门禁；未在本地/CI 拉起 Kafka 做“宕机→恢复补发/failed 重放”演练。
- [√] 8.3 internal clients：user-service 聚合链路回归（计数/关注状态一致，降级语义明确），verify why.md#r3-internal-client-governance
- [√] 8.4 gateway：跑通新增测试与验证（缓存有界、timeout/并发限制、指标口径），verify why.md#r4-gateway-analytics-pipeline
