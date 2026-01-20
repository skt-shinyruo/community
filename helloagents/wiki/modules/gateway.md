# gateway 模块

## 1. 职责（迭代 0）
- 统一入口：对外暴露 `/api/**`，内部转发到各微服务（当前主要是 `auth-service`）。
- 统一能力：CORS、JWT 验签、401/403 统一错误返回、traceId 透传。
- 安全增强：基于 Redis 的多规则限流（登录/注册/验证码/敏感操作/管理操作）；管理/运维接口按角色收敛（例如搜索重建、统计接口）。
- 可观测性增强：可选启用 UV/DAU 采集（由 gateway 采集并写入 analytics-service）。
- 服务治理：接入 Nacos Discovery（可选 Nacos Config）。

## 2. 关键文件
- 启动类：`gateway/src/main/java/com/nowcoder/community/gateway/GatewayApplication.java`
- 安全配置（JWT 验签 + 权限矩阵）：`gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`
- OriginGuard 配置：`gateway/src/main/java/com/nowcoder/community/gateway/config/OriginGuardProperties.java`（`gateway.origin-guard.*`）
- traceId：`gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdGlobalFilter.java`
- 限流：`gateway/src/main/java/com/nowcoder/community/gateway/filter/GatewayRateLimitGlobalFilter.java`
- 审计：`gateway/src/main/java/com/nowcoder/community/gateway/filter/AuditLogGlobalFilter.java`
- UV/DAU 采集：`gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`
- OriginGuard（敏感接口 Origin 白名单）：`gateway/src/main/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilter.java`
- 配置：`gateway/src/main/resources/application.yml`

## 3. 路由（当前）
- `/api/auth/**` -> `lb://auth-service`
- `/api/search/**` -> `lb://search-service`
- `/api/notices/**`、`/api/messages/**` -> `lb://message-service`
- `/api/analytics/**` -> `lb://analytics-service`
- `/api/likes/**`、`/api/follows/**` -> `lb://social-service`
- `/api/users/**` -> `lb://user-service`
- `/api/posts/**` -> `lb://content-service`
- `/api/categories/**`、`/api/tags/**` -> `lb://content-service`

## 4. 本地运行（示例）
- 需要设置 `GATEWAY_JWT_HMAC_SECRET`（>=32 字节）并确保与 `AUTH_JWT_HMAC_SECRET` 一致。
- 若启用统计采集（`analytics.collect.enabled=true`），需要配置 `ANALYTICS_INTERNAL_TOKEN`，用于 gateway 调用 analytics-service 的 `/internal/**` 写入口。
- 若启用限流（默认开启），需要 Redis 可用（`spring.data.redis.host/port`）。
- 若采用“前端直连 gateway”模式（前端 `12881` + gateway `12882`），需要在 gateway allowlist 中允许对应 Origin（默认包含 `http://localhost:12881` / `http://localhost:12888`）。
- 若本地前端端口调整（例如 `12888` -> 其他端口），只需在 gateway 中更新 allowlist（CORS + OriginGuard），auth-service 不再单独维护 Origin 白名单。
- 若启用 Nacos Config：
  - Data ID：`gateway.yaml`（YAML）
  - 配置入口：`spring.cloud.gateway.globalcors.corsConfigurations.[/**].allowedOrigins` 与 `gateway.origin-guard.allowed-origins`
  - 示例模板：`deploy/nacos-config/gateway.yaml`
  - 访问 UI：docker compose 默认将 Nacos 控制台端口绑定到宿主机 `127.0.0.1:${NACOS_UI_PORT:-8848}`，打开 `http://localhost:8848/nacos`（如端口冲突可设置 `NACOS_UI_PORT` 覆盖）

## 5. 关键行为说明
- 限流触发时返回 HTTP 429，并附带 `X-RateLimit-*` 响应头（Limit/Remaining/Reset/Rule）。
- 审计日志：gateway 记录非 GET 的 `/api/**` 操作（跳过 `/api/auth/login`），包含 `status/costMs/userId/traceId`，用于 Loki/日志系统检索。

## 6. 常见问题排查
- **503 Service Unavailable（Unable to find instance）**：若 gateway 日志提示 `Unable to find instance for {service}`，通常表示 `lb://{service}` 未解析到任何实例：
  - 先确认 Nacos 中对应服务已注册且实例健康（healthy=true）。
  - 再确认 gateway 已启用 Spring Cloud LoadBalancer（`spring-cloud-starter-loadbalancer`），否则 `lb://` 可能无法正确工作。
- **502 Bad Gateway（外部反代）**：若你在本地/生产用 Nginx/Ingress/Traefik 反代 gateway，偶发 502 但容器内可直连：
  - 典型原因是“上游地址解析/缓存”导致：反代服务仅在启动时解析 DNS，上游容器/Pod 重建后 IP 变化就会指向旧地址。
  - 解决思路：确保反代层使用“可动态解析的 upstream”（Docker 环境通常需要显式配置 `resolver` 并避免静态 `proxy_pass`；K8s 环境优先通过 Service DNS 名称转发）。
