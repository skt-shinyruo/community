# gateway 模块

## 1. 职责（迭代 0）
- 统一入口：对外暴露 `/api/**`，内部转发到各微服务（当前主要是 `auth-service`）。
- 统一能力：CORS、JWT 验签、401/403 统一错误返回、traceId 透传。
- 安全增强：基于 Redis 的多规则限流（登录/注册/验证码/敏感操作/管理操作）；管理/运维接口按角色收敛（例如搜索重建、统计接口）。
- 可观测性增强：可选启用 UV/DAU 采集（由 gateway 采集并写入 analytics-service）。
- 服务治理：接入 Nacos Discovery（可选 Nacos Config）。

## 2. 关键文件
- 启动类：`gateway/src/main/java/com/nowcoder/community/gateway/GatewayApplication.java`
- common 自动装配（跨服务一致能力）：`common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 安全配置（JWT 验签 + 权限矩阵）：`gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`
- OriginGuard 配置：`gateway/src/main/java/com/nowcoder/community/gateway/config/OriginGuardProperties.java`（`gateway.origin-guard.*`）
- traceId：`gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdWebFilter.java`、`gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdSupport.java`（单一注入点）
- 安全异常响应：`gateway/src/main/java/com/nowcoder/community/gateway/config/ReactiveSecurityExceptionHandler.java`
- 网关全局异常收敛：`gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayErrorWebExceptionHandler.java`（非 401/403 的统一错误协议）
- WebClient 兜底配置：`gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayWebClientConfig.java`、`gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayWebClientProperties.java`（`gateway.webclient.*`）
- 可信代理配置：`gateway/src/main/java/com/nowcoder/community/gateway/config/TrustedProxyProperties.java`（`gateway.trusted-proxy.*`）
- 客户端 IP 解析：`gateway/src/main/java/com/nowcoder/community/gateway/filter/ClientIpResolver.java`
- 反代/HTTPS offload 同源解析：`gateway/src/main/java/com/nowcoder/community/gateway/filter/ForwardedOriginResolver.java`（仅在可信代理 CIDR 命中时解析 `Forwarded/X-Forwarded-*`）
- 限流：`gateway/src/main/java/com/nowcoder/community/gateway/filter/GatewayRateLimitGlobalFilter.java`
- 审计：`gateway/src/main/java/com/nowcoder/community/gateway/filter/AuditLogGlobalFilter.java`
- UV/DAU 采集：`gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`
- UV/DAU 采集异步调度：`gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`（有界队列 + worker）
- OriginGuard（敏感接口 Origin 白名单）：`gateway/src/main/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilter.java`
- 配置：`gateway/src/main/resources/application.yml`

## 3. 路由（当前）
- `/api/auth/**` -> `lb://auth-service`
- `/api/search/**` -> `lb://search-service`
- `/api/notices/**`、`/api/messages/**` -> `lb://message-service`
- `/api/analytics/**` -> `lb://analytics-service`
- `/api/likes/**`、`/api/follows/**` -> `lb://social-service`
- `/api/blocks/**` -> `lb://social-service`
- `/api/users/**` -> `lb://user-service`
- `/files/**` -> `lb://user-service`（头像等公开文件访问，local provider）
- `/api/posts/**` -> `lb://content-service`
- `/api/reports/**`、`/api/moderation/**` -> `lb://content-service`
- `/api/bookmarks/**`、`/api/subscriptions/**` -> `lb://content-service`
- `/api/categories/**`、`/api/tags/**` -> `lb://content-service`

## 4. 本地运行（示例）
- 需要设置 `GATEWAY_JWT_HMAC_SECRET`（>=32 字节）并确保与 `AUTH_JWT_HMAC_SECRET` 一致。
- 若启用统计采集（`analytics.collect.enabled=true`），gateway 会调用 analytics-service 的 `/internal/**` 写入口（开发阶段不再要求 internal token 鉴权）。
  - 去重/降噪参数：`analytics.collect.dedup-enabled`、`analytics.collect.uv-cache-max-size`、`analytics.collect.dau-cache-max-size`、`analytics.collect.dedup-ttl-seconds`（网关单实例内生效）。
  - 隔离/背压参数：`analytics.collect.queue-capacity`（有界队列）、`analytics.collect.max-concurrency`（worker 并发）、`analytics.collect.timeout-ms`（采集超时）。
  - Runbook：`helloagents/wiki/runbooks/gateway-analytics-collect.md`
- 若启用限流（默认开启），限流依赖 Redis（`spring.data.redis.host/port`）；Redis 不可用时按配置降级（`gateway.rate-limit.fail-open=true` 时放行并打点可观测）。
- 若采用“前端直连 gateway”模式（前端 `12881` + gateway `12882`），需要在 gateway allowlist 中允许对应 Origin（默认包含 `http://localhost:12881` / `http://localhost:12888`）。
- 若本地前端端口调整（例如 `12888` -> 其他端口），需要同步更新 allowlist（CORS + OriginGuard），并确保 gateway 与 auth-service 的 OriginGuard allowlist 保持一致（建议在配置中心统一维护同一套值）。
- 旁路防护：auth-service 同样启用 OriginGuard，配置键与 gateway 对齐（`gateway.origin-guard.*`），避免绕过网关直连 auth-service 时降低安全性。
- 若部署在反向代理/Ingress 后，默认 **不信任** `X-Forwarded-For`；需显式配置 `gateway.trusted-proxy.enabled=true` + `gateway.trusted-proxy.cidrs` 才会解析 XFF。
- 若部署在反向代理/HTTPS offload 后，且希望 OriginGuard 的“同源请求放行”能正确识别 `https://<public-host>`：需要同时配置可信代理（`gateway.trusted-proxy.*`）并确保代理正确回填 `Forwarded/X-Forwarded-Proto/Host/Port`。
- 若启用 Nacos Config：
  - Data ID：`gateway.yaml`（YAML）
  - 配置入口：`spring.cloud.gateway.globalcors.corsConfigurations.[/**].allowedOrigins` 与 `gateway.origin-guard.allowed-origins`
  - 示例模板：`deploy/nacos-config/gateway.yaml`
  - 访问 UI：docker compose 默认将 Nacos 控制台端口绑定到宿主机 `127.0.0.1:${NACOS_UI_PORT:-8848}`，打开 `http://localhost:8848/nacos`（如端口冲突可设置 `NACOS_UI_PORT` 覆盖）

## 5. 关键行为说明
- 限流触发时返回 HTTP 429，并附带 `X-RateLimit-*` 响应头（Limit/Remaining/Reset/Rule）。
- 401/403/429/503 等错误响应统一回填 `traceId`（响应体 `Result.traceId` + 响应头 `X-Trace-Id/traceparent`）。
- 网关自身异常（路由未命中/请求解析/超时/上游不可用等）统一收敛为 `Result` + 4xx/5xx，避免默认 HTML/空响应导致调用方难以处理。
- traceId 注入仅由 `TraceIdWebFilter` 负责，避免 WebFilter/GlobalFilter 重复导致的维护成本与潜在覆盖困惑。
- 审计日志：gateway 记录非 GET 的 `/api/**` 操作（跳过 `/api/auth/login`），包含 `status/costMs/userId/traceId`，用于 Loki/日志系统检索。
- 权限矩阵：治理后台接口 `/api/moderation/**` 仅允许 `ROLE_ADMIN/ROLE_MODERATOR`（其余用户返回 403）。
- 帖子接口：仅开放读接口（`GET /api/posts`、`GET /api/posts/{postId}`、`GET /api/posts/{postId}/comments`、`GET /api/posts/{postId}/comments/{commentId}/replies`）；敏感管理操作（`POST /api/posts/{postId}/top|wonderful|delete`）要求 `ROLE_ADMIN/ROLE_MODERATOR`。为避免 matcher 顺序/通配导致误放行，网关侧对公开路径使用显式清单而非 `/api/posts/**`。
- 文件访问：`GET /files/**` 允许匿名访问，但仅用于公开头像资源（下游 user-service 仍会做前缀与路径校验）。
- UV/DAU 采集链路：网关侧仅做“有界降噪”（TTL + 最大容量），最终以 analytics-service Redis 去重/聚合为准；网关调用 analytics-service 时会透传 `X-Trace-Id/traceparent` 便于排障。
- UV/DAU 采集链路（隔离版）：filter 仅采集字段并投递到有界队列；异步 worker 执行 WebClient 调用；队列满允许丢弃并通过指标观测（`gateway_analytics_collect_total{metric,outcome}` + `gateway_analytics_collect_latency{metric}`）。
- WebClient 全局兜底：网关通过 `gateway.webclient.*` 提供出站调用的统一超时与连接池上限（含 pending acquire 限制），用于覆盖“新增链路忘配 timeout”并在极端网络条件下保护网关不被连接堆积拖垮。
- OriginGuard：同源判定会在“可信代理 CIDR 命中”时基于 `Forwarded/X-Forwarded-*` 计算 effective scheme/host/port（反代/HTTPS offload 兼容）；非可信来源忽略 forwarded 头，避免伪造绕过 allowlist。

## 6. 常见问题排查
- **503 Service Unavailable（Unable to find instance）**：若 gateway 日志提示 `Unable to find instance for {service}`，通常表示 `lb://{service}` 未解析到任何实例：
  - 先确认 Nacos 中对应服务已注册且实例健康（healthy=true）。
  - 再确认 gateway 已启用 Spring Cloud LoadBalancer（`spring-cloud-starter-loadbalancer`），否则 `lb://` 可能无法正确工作。
- **502 Bad Gateway（外部反代）**：若你在本地/生产用 Nginx/Ingress/Traefik 反代 gateway，偶发 502 但容器内可直连：
  - 典型原因是“上游地址解析/缓存”导致：反代服务仅在启动时解析 DNS，上游容器/Pod 重建后 IP 变化就会指向旧地址。
  - 解决思路：确保反代层使用“可动态解析的 upstream”（Docker 环境通常需要显式配置 `resolver` 并避免静态 `proxy_pass`；K8s 环境优先通过 Service DNS 名称转发）。
