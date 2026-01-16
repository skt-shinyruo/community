# gateway 模块

## 1. 职责（迭代 0）
- 统一入口：对外暴露 `/api/**`，内部转发到各微服务（当前主要是 `auth-service`）。
- 统一能力：CORS、JWT 验签、401/403 统一错误返回、traceId 透传。
- 服务治理：接入 Nacos Discovery（可选 Nacos Config）。

## 2. 关键文件
- 启动类：`gateway/src/main/java/com/nowcoder/community/gateway/GatewayApplication.java`
- 安全配置（JWT 验签 + 权限矩阵）：`gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`
- traceId：`gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdGlobalFilter.java`
- 配置：`gateway/src/main/resources/application.yml`

## 3. 路由（当前）
- `/api/auth/**` -> `lb://auth-service`

## 4. 本地运行（示例）
- 需要设置 `GATEWAY_JWT_HMAC_SECRET`（>=32 字节）并确保与 `AUTH_JWT_HMAC_SECRET` 一致。

