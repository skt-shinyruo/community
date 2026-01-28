# Task List: 后端架构升级（五类系统性问题：一致性 / traceId / IP 信任 / 内部调用治理 / 事件治理）

Directory: `helloagents/plan/202601281122_backend_arch_upgrade_5issues/`

---

## 0. 交付里程碑与开关（先低风险、再高收益、最后高影响）
- [√] 0.1 定义阶段与开关：P0（计数原子化/traceId/可信 IP）→ P1（幂等 insert-first + 清理、ES alias reindex）→ P2（Outbox），并为 P1/P2 增加可回滚开关与默认关闭策略，verify why.md#r5-event-governance-outbox-idempotency-reindex
- [√] 0.2 产出一份“灰度/回滚清单”（开关、配置项、验证点、回滚步骤），并落实到知识库/部署配置中，verify why.md#r5-event-governance-outbox-idempotency-reindex

## 1. content-service（并发一致性：评论数原子化）
- [√] 1.1 新增 Mapper 原子增量接口：为 `discuss_post.comment_count` 增加 `+1` 更新语句（避免 count→set），verify why.md#r1-comment-count-atomicity + why.md#r1s1-add-comment-concurrent, files: `content-service/src/main/java/com/nowcoder/community/content/dao/DiscussPostMapper.java` + `content-service/src/main/resources/mapper/discusspost-mapper.xml`
- [√] 1.2 改造评论新增写路径：`CommentService.addComment` 改为调用 comment_count 原子增量（并删除 count 查询/回写），verify why.md#r1s1-add-comment-concurrent, file: `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- [√] 1.3 数据修复方案：提供一次性 backfill（SQL 或脚本）把历史 `comment_count` 校准为真实值（可按分批/按时间窗口执行），verify why.md#r1-comment-count-atomicity, files: `scripts/*`（新增）或 `deploy/mysql-init/020_schema_content.sql`（若采用存储过程/修复脚本）
- [√] 1.4 回归测试：新增并发场景测试（同帖并发 addComment），验证 comment_count 无丢更新，verify why.md#r1s1-add-comment-concurrent, file: `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`（或新增 `*ConcurrencyTest`）

## 2. gateway（traceId 一致性：错误响应体补齐）
- [√] 2.1 抽取可复用的 TraceId 工具：把 `normalize/extract/buildTraceparent` 逻辑抽成公共工具，供 WebFilter/ExceptionHandler/GlobalFilter 复用，verify why.md#r2-gateway-traceid-in-error-body, files: `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdGlobalFilter.java` +（新增 `gateway/src/main/java/.../TraceIdSupport.java`）
- [√] 2.2 在 gateway 增加 TraceId WebFilter（优先于 Spring Security）：确保即便请求被 401/403 拦截，也一定写出 `X-Trace-Id`/`traceparent`，verify why.md#r2-gateway-traceid-in-error-body + why.md#r2s1-401-403-body-has-traceid, file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdWebFilter.java`（新增）
- [√] 2.3 修复 401/403 响应体 traceId：`ReactiveSecurityExceptionHandler` 写响应时把 traceId（从 exchange/request 取；缺失则生成）回填到 `Result.traceId`，verify why.md#r2s1-401-403-body-has-traceid, file: `gateway/src/main/java/com/nowcoder/community/gateway/config/ReactiveSecurityExceptionHandler.java`
- [√] 2.4 修复 429/503 响应体 traceId：`GatewayRateLimitGlobalFilter.write(...)` 统一回填 `Result.traceId`，verify why.md#r2s2-rate-limit-429-body-has-traceid, file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/GatewayRateLimitGlobalFilter.java`
- [√] 2.5 回归测试：新增/补齐 WebFlux 安全链路测试（无 token 触发 401、有 token 无权限触发 403、触发 429），断言 body.traceId 与 header `X-Trace-Id` 一致，verify why.md#r2-gateway-traceid-in-error-body, files: `gateway/src/test/java/com/nowcoder/community/gateway/filter/TraceIdGlobalFilterTest.java`（或新增 `*SecurityTraceIdTest`）

## 3. gateway（安全边界：可信代理模型，防 XFF 伪造）
- [√] 3.1 新增 trusted-proxy 配置模型（CIDR allowlist + 开关 + 行为说明），默认 **不信任** XFF，verify why.md#r3-trusted-proxy-forwarded-headers, file: `gateway/src/main/java/com/nowcoder/community/gateway/config/TrustedProxyProperties.java`（新增）
- [√] 3.2 实现统一 ClientIpResolver：仅当 remoteAddress 属于 trusted CIDR 才解析 `X-Forwarded-For`，并做 IP 格式校验与降级，verify why.md#r3s1-prevent-xff-spoof-bypass-rate-limit, file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/ClientIpResolver.java`（新增）
- [√] 3.3 接入限流：`GatewayRateLimitGlobalFilter` 使用 ClientIpResolver 替代直接读取 XFF，verify why.md#r3s1-prevent-xff-spoof-bypass-rate-limit, file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/GatewayRateLimitGlobalFilter.java`
- [√] 3.4 接入 UV/DAU：`AnalyticsCollectGlobalFilter` 使用同一套 ClientIpResolver，verify why.md#r3s2-analytics-uv-uses-trusted-ip, file: `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`
- [√] 3.5 配置落地：补齐 `gateway/src/main/resources/application.yml` 的默认值与 `application-prod.yml` 的生产建议（明确 trusted CIDR 来源），verify why.md#r3-trusted-proxy-forwarded-headers, files: `gateway/src/main/resources/application.yml` + `gateway/src/main/resources/application-prod.yml`
- [√] 3.6 回归测试：覆盖“未启用 trusted-proxy 时 XFF 伪造无效 / 启用后来自可信代理才解析”，verify why.md#r3-trusted-proxy-forwarded-headers, file: `gateway/src/test/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilterTest.java`（或新增 `*ClientIpResolverTest`）

## 4. common（内部调用治理：统一规范与基础能力）
- [√] 4.1 定义内部调用规范（契约）：统一超时（connect/read）、traceId 透传、internal-token 注入、Result 解包与错误映射、指标命名与 tags，verify why.md#r4-internal-http-client-governance, docs: `helloagents/wiki/modules/common.md`（或新增 `helloagents/wiki/internal-clients.md`）
- [√] 4.2 抽取 common 基础组件（可复用）：headers 构建（JSON + X-Internal-Token + X-Trace-Id）、Result 解包（保留 code/message）、异常映射与指标 helper，verify why.md#r4s1-standard-timeout-metrics-error-mapping, files: `common/src/main/java/com/nowcoder/community/common/web/TraceIdClientHttpRequestInterceptor.java` +（新增 `common/src/main/java/.../internalclient/*`）
- [√] 4.3 content-service 迁移（user）：将 `UserModerationClient` 收敛到统一规范（properties + 校验 + 指标 + 错误映射 + 降级策略开关），verify why.md#r4s2-explicit-fallback-policy-per-call, files: `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java` +（新增/更新 `content-service/src/main/java/.../*Properties.java`）
- [√] 4.4 content-service 迁移（social）：将 `SocialBlockClient` 收敛到统一规范（同上），verify why.md#r4s2-explicit-fallback-policy-per-call, files: `content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java` +（新增/更新 `content-service/src/main/java/.../*Properties.java`）
- [√] 4.5 message-service 迁移（social internal）：将 `message-service` 的 `SocialServiceClient` 收敛到统一规范（properties + 校验 + 指标 + 降级策略），verify why.md#r4s1-standard-timeout-metrics-error-mapping, files: `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java` + `message-service/src/main/java/com/nowcoder/community/message/config/MessageRestClientConfig.java`（若需） 
- [√] 4.6 search-service 迁移（content internal）：将 `contentServiceClient` 收敛到统一规范（properties + 校验 + 指标 + 错误映射），verify why.md#r4s1-standard-timeout-metrics-error-mapping, files: `search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`（或等价）+ `search-service/src/main/java/com/nowcoder/community/search/config/*Properties.java`

## 5. search-service（幂等治理统一：insert-first + 清理）
- [√] 5.1 幂等存储改造：将 `SearchConsumedEventStore` 改为 insert-first（返回 boolean 表示是否首次消费），并避免每条消息 2 次 DB IO，verify why.md#r5s2-insert-first-idempotency-with-ttl, files: `search-service/src/main/java/com/nowcoder/community/search/kafka/SearchConsumedEventStore.java` + `search-service/src/main/java/com/nowcoder/community/search/kafka/PostEventConsumer.java`
- [√] 5.2 DB 索引与清理：为 `search_consumed_event(consumed_at)` 增加索引，并实现定时清理（按 retention-days 配置），verify why.md#r5s2-insert-first-idempotency-with-ttl, files: `deploy/mysql-init/040_schema_search.sql` +（新增 `search-service/src/main/java/.../SearchConsumedEventCleanupJob.java`）
- [√] 5.3 回归测试：重复 eventId 不应重复索引/不应重复写幂等副作用，verify why.md#r5s2-insert-first-idempotency-with-ttl, file: `search-service/src/test/java/com/nowcoder/community/search/api/SearchControllerTest.java`（或新增 `*ConsumerTest`）

## 6. message-service（幂等表清理策略对齐）
- [√] 6.1 为 `consumed_event(consumed_at)` 增加索引，并落地定时清理（按 retention-days 配置），verify why.md#r5s2-insert-first-idempotency-with-ttl, files: `deploy/mysql-init/030_schema_message.sql` +（新增 `message-service/src/main/java/.../ConsumedEventCleanupJob.java`）

## 7. search-service（ES 无停机 reindex：alias/蓝绿切换）
- [√] 7.1 引入索引命名与 alias 约定（alias 固定、实际索引带版本号），并在启动期保证 alias 存在（不存在则创建初始索引+alias），verify why.md#r5s3-search-reindex-zero-downtime-with-alias, files: `search-service/src/main/java/com/nowcoder/community/search/repo/PostIndexInitializer.java` +（新增 `search-service/src/main/java/.../PostIndexManager.java`）
- [√] 7.2 改造 ES reindex：构建新索引→回填→切换 alias（失败不影响线上查询），verify why.md#r5s3-search-reindex-zero-downtime-with-alias, files: `search-service/src/main/java/com/nowcoder/community/search/service/PostSearchService.java` + `search-service/src/main/java/com/nowcoder/community/search/repo/ElasticsearchPostSearchRepository.java`（或新增专用 indexing service）
- [√] 7.3 运维能力：提供“保留 N 个历史索引/清理旧索引”的策略与脚本（避免磁盘膨胀），verify why.md#r5s3-search-reindex-zero-downtime-with-alias, files: `scripts/*`（新增）或 `helloagents/wiki/modules/search-service.md`（若需新增模块文档）

## 8. content-service（P2：Outbox 可靠投递，DB 事务型生产者）
## 8. content-service（P2：Outbox 可靠投递，DB 事务型生产者）
- [√] 8.1 表结构落地：新增 `outbox_event`（content schema）并补齐测试 schema，verify why.md#r5s1-outbox-reliable-delivery-for-db-producers, files: `deploy/mysql-init/020_schema_content.sql` + `content-service/src/test/resources/schema.sql`
- [√] 8.2 Outbox 写入：实现 outbox 的 mapper/repo，并把 `KafkaContentEventPublisher` 改为“事务内写 outbox”（保留开关/回滚到 after-commit 直发），verify why.md#r5s1-outbox-reliable-delivery-for-db-producers, files: `content-service/src/main/java/com/nowcoder/community/content/event/KafkaContentEventPublisher.java` +（新增 `content-service/src/main/java/.../outbox/*`）
- [√] 8.3 Relay worker：实现定时扫描/批量 claim/发送 Kafka/更新状态/退避重试（支持多实例并发安全），verify why.md#r5s1-outbox-reliable-delivery-for-db-producers, files: `content-service/src/main/java/.../outbox/OutboxRelayJob.java` +（新增 `content-service/src/main/java/.../outbox/OutboxEventMapper.java`）
- [-] 8.4 演练与验证：Kafka 宕机→写入 outbox→恢复后补投递成功；并验证下游幂等无重复副作用，verify why.md#r5s1-outbox-reliable-delivery-for-db-producers
  > Note: 未执行环境演练

## 9. deploy / 运维（配置治理与灰度）
## 9. deploy / 运维（配置治理与灰度）
- [√] 9.1 Nacos 配置落地：trusted-proxy、idempotency retention-days、ES alias/reindex 参数、outbox 开关与批次大小/重试退避等，verify why.md#r3-trusted-proxy-forwarded-headers + why.md#r5-event-governance-outbox-idempotency-reindex, files: `deploy/nacos-config/gateway.yaml` + `deploy/nacos-config/search-service.yaml`（按需增加 content/message）
- [√] 9.2 运维脚本与说明：ES alias reindex 演练、outbox 观察/重放、幂等表清理演练与告警策略，verify why.md#r5-event-governance-outbox-idempotency-reindex, files: `scripts/*` + `docs/*`（按需）

## 10. Security Check
## 10. Security Check
- [-] 10.1 执行安全检查（Forwarded/XFF 信任边界、鉴权绕过、traceId 伪造/日志注入、高基数指标、internal-token 头防伪），按 G9 记录风险与结论
  > Note: 未执行脚本，仅完成代码审阅

## 11. Documentation Update（SSOT 同步）
## 11. Documentation Update（SSOT 同步）
- [√] 11.1 更新知识库（gateway）：traceId 写入链路、401/403/429 traceId 保证、trusted-proxy 模型与配置，files: `helloagents/wiki/modules/gateway.md`
- [√] 11.2 更新知识库（common）：内部调用规范、错误映射与指标约定，files: `helloagents/wiki/modules/common.md`（或新增专章）
- [√] 11.3 更新知识库（search/outbox）：幂等与清理策略、ES alias reindex 运维说明、outbox 演练与回放，files: `helloagents/wiki/modules/search-service.md`（若不存在则新增）+ `helloagents/wiki/data.md`
- [√] 11.4 更新 `helloagents/CHANGELOG.md`：记录本次架构升级的关键变更与灰度策略

## 12. Testing
## 12. Testing
- [-] 12.1 gateway：跑通并补齐测试（traceId + trusted-proxy + rate limit），并记录验证结果，verify why.md#r2-gateway-traceid-in-error-body + why.md#r3-trusted-proxy-forwarded-headers
  > Note: 未运行测试
- [-] 12.2 content-service：跑通并补齐测试（comment_count 并发正确性、写路径不丢更新），verify why.md#r1-comment-count-atomicity
  > Note: 未运行测试
- [-] 12.3 search-service：跑通并补齐测试（幂等 insert-first、清理 job；ES alias reindex 需做演练或集成验证），verify why.md#r5-event-governance-outbox-idempotency-reindex
  > Note: 未运行测试
- [-] 12.4 message-service：跑通并补齐测试（幂等表清理策略不影响消费），verify why.md#r5s2-insert-first-idempotency-with-ttl
  > Note: 未运行测试
