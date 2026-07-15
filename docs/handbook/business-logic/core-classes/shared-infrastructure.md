# Shared Infrastructure 核心类细分

本文是全仓库共享基础设施的类级补充。这里放的是跨域通用的技术底座，不是某个业务域自己的主事实。

## 先读顺序

1. `BinaryUuidCodec`
2. `IdempotencyGuard`
3. `OutboxWorkerScheduler` / `OutboxHandler`
4. `TraceIdCodec`
5. `common-web` / `common-webflux` 的 trace 和 error handler
6. `community-gateway` 的 edge filter 和 IM bridge

## 共享编码和持久化

| 类 | 核心职责 |
| --- | --- |
| `common-core.id.BinaryUuidCodec` | UUID 与 16-byte binary 的互转。 |
| `community-app.infra.persistence.mybatis.UuidBinaryTypeHandler` | community schema 的 UUID binary 适配。 |
| `community-oss.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` | community_oss schema 的 UUID binary 适配。 |
| `im.core.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` | im_core schema 的 UUID binary 适配。 |

## 幂等、outbox 和事件

| 类 | 核心职责 |
| --- | --- |
| `common-idempotency.IdempotencyGuard` | HTTP 写接口幂等守卫。 |
| `common-outbox.OutboxWorkerScheduler` | outbox 本地 worker scheduler。 |
| `common-outbox.OutboxHandler` | outbox topic handler contract。 |
| `common-core.event.BestEffortLocalEventListener` | best-effort 本地事件 listener 标记。 |
| `infra.scheduler.SingleFlightTaskGuard` | 单飞调度守卫。 |

## Trace 和 Web 统一处理

| 类 | 核心职责 |
| --- | --- |
| `common-core.trace.TraceIdCodec` | trace id 归一化。 |
| `common-web.TraceIdFilter` | Servlet trace filter。 |
| `common-web.AuditLogFilter` | Servlet audit log filter。 |
| `common-web.GlobalExceptionHandler` | Servlet 错误映射。 |
| `common-web.SecurityExceptionHandler` | Servlet 安全错误映射。 |
| `common-webflux.TraceIdWebFilter` | WebFlux trace filter。 |
| `common-webflux.GlobalExceptionHandler` | WebFlux 错误映射。 |
| `common-webflux.SecurityExceptionHandler` | WebFlux 安全错误映射。 |

## Gateway 和 OSS client

| 类 | 核心职责 |
| --- | --- |
| `community-gateway.edge.RateLimitWebFilter` | gateway edge 限流。 |
| `community-gateway.edge.AccessLogWebFilter` | gateway HTTP access log。 |
| `community-oss-client.CommunityOssClient` | typed OSS client contract。 |
| `community-oss-client.HttpCommunityOssClient` | typed OSS client HTTP 实现。 |

## 关键语义

- 这些类都不拥有业务主事实，只做横切能力或基础设施适配。
- UUID binary 适配、outbox 和 idempotency 都是跨域复用的底座。
- gateway edge 只负责路由、限流和桥接，不承担业务规则。
