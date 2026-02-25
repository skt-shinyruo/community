# Task List: 微服务边界与韧性治理（Docker Compose 部署）

Directory: `.helloagents/archive/2026-01/202601311900_compose_boundaries_resilience/`

---

## 0. 验收口径与 SSOT（先固化）
- [√] 0.1 固化边界三分与默认策略（external/internal/ops、兼容窗口、fail-closed），files: `docs/SYSTEM_DESIGN.md`, verify why.md#requirement-r1-边界三分externalinternalops
- [√] 0.2 同步更新项目约定（env/compose/配置覆盖策略/运维入口），files: `.helloagents/project.md`, verify why.md#requirement-r5-契约与可观测性体系化compose-友好

## 1. R1 边界三分（对外 internal 命名收敛）
- [√] 1.1 gateway：新增 `/api/ops/**` 权限矩阵与强保护（角色 + ops-token），files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`, verify why.md#scenario-s2-ops-入口强保护双钥--可审计
- [√] 1.2 gateway：显式 deny `/internal/**`（即便误配置路由也不放行），files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`, verify why.md#scenario-s1-internal-默认不可对外暴露
- [√] 1.3 search-service：新增对外 ops 入口的转发/适配，并标记旧 `/api/search/internal/reindex` 进入弃用窗口（保留兼容期），files: `search-service/src/main/java/com/nowcoder/community/search/api/InternalSearchController.java`, verify why.md#requirement-r1-边界三分externalinternalops
- [√] 1.4 frontend：迁移 reindex 调用到 `/api/ops/search/reindex`（必要时做兼容回退），files: `frontend/src/api/services/searchService.js`, verify why.md#requirement-r1-边界三分externalinternalops

## 2. R1 Compose 网络隔离（默认不暴露 internal）
- [√] 2.1 compose：仅暴露 gateway/frontend（以及必要观测端口），其余服务不映射 ports，files: `deploy/docker-compose.yml`, verify why.md#scenario-s1-internal-默认不可对外暴露
- [√] 2.2 compose：梳理端口覆盖文件（`docker-compose.ports.yml` 等）并确保不会误暴露 internal 服务，files: `deploy/docker-compose.ports.yml`, verify why.md#scenario-s1-internal-默认不可对外暴露

## 3. R2 DB 最小权限（同实例，多账号）
- [√] 3.1 mysql-init：补齐 per-service DB user/grant（content/message/search/social/user 等），files: `deploy/mysql-init/001_create_databases.sh`, verify why.md#scenario-s1-跨域直读被-db-权限阻断
- [√] 3.2 nacos-config：各服务 datasource 改为使用 `{SERVICE}_DB_USERNAME/{SERVICE}_DB_PASSWORD`（不再复用 `MYSQL_USER`），files: `deploy/nacos-config/content-service.yaml`, verify why.md#requirement-r2-数据所有权与最小权限db-access-policy

## 4. R3 同步调用韧性基线（无 mesh）
- [√] 4.1 common：统一 internal client 指标与 outcome 口径，并补齐使用约定，files: `common/src/main/java/com/nowcoder/community/common/web/internalclient/InternalClientSupport.java`, verify why.md#requirement-r3-同步调用韧性基线无-mesh
- [√] 4.2 message-service：聚合读接口明确降级语义（可降级/不可降级）并记录 degraded 指标，files: `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`, verify why.md#scenario-s2-可降级接口返回默认值且可观测
- [-] 4.3 可选增强：引入 Resilience4j（仅关键链路启用 bulkhead/circuit breaker/retry），files: `common/pom.xml`, verify why.md#scenario-s1-下游不可用时确定性-fast-fail
  > Note: 本次按“无 Kubernetes/service mesh + 最小变更”先落地“指标口径 + fail-open 语义 + 超时识别”。如需熔断/隔离，可在下一次迭代按链路逐步启用 Resilience4j。

## 5. R4 异步一致性闭环（Outbox + DLQ 可运营）
- [√] 5.1 配置：确保 outbox 默认启用且指标阈值明确，files: `deploy/nacos-config/content-service.yaml`, verify why.md#scenario-s1-提交成功但发送失败不丢事件
- [√] 5.2 消费侧：统一 DefaultErrorHandler 重试与 DLQ 发布指标口径（若存在差异则对齐），files: `content-service/src/main/java/com/nowcoder/community/content/kafka/KafkaErrorHandlerConfig.java`, verify why.md#scenario-s2-消费失败进入-dlq-且可回放
- [√] 5.3 Runbook：固化 DLQ 回放流程与风控（重复副作用/回放顺序/审计），files: `.helloagents/modules/runbooks/kafka-dlq-replay.md`, verify why.md#scenario-s2-消费失败进入-dlq-且可回放

## 6. R5 契约与可观测性体系化（Compose 友好）
- [√] 6.1 契约测试：补齐 DTO 字段白名单回归（覆盖搜索/评论/通知等关键公共读接口），files: `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`, verify why.md#scenario-s2-dto-字段白名单回归
- [-] 6.2 可选 tracing：引入 Micrometer Tracing + OTLP，并增加 compose tracing 后端（Tempo/Jaeger/Zipkin 任选其一），files: `deploy/observability/docker-compose.tracing.yml`, verify why.md#scenario-s1-http--kafka-端到端可追踪可选-tracing
  > Note: 本次优先保证“无额外基础设施依赖”下的可用性与边界收敛；如需 tracing，可选用 Zipkin/Tempo 并以 docker compose 形式增量接入。

## 7. Security Check
- [√] 7.1 安全检查：internal/ops 默认 fail-closed、token 不落日志明文、DB 最小权限生效、compose 不误暴露端口

## 8. Testing
- [√] 8.1 回归测试：gateway 对 `/internal/**` 显式拒绝、ops 入口强保护，file: `gateway/src/test/java/com/nowcoder/community/gateway/GatewaySecurityConfigTest.java`
- [√] 8.2 回归测试：reindex 新旧入口兼容窗口可控，file: `search-service/src/test/java/com/nowcoder/community/search/api/SearchControllerTest.java`
