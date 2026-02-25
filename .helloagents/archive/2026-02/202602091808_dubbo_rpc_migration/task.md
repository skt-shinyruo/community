# 任务清单：Dubbo RPC 服务间调用迁移（保留网关 HTTP 路由）

Directory: `.helloagents/archive/2026-02/202602091808_dubbo_rpc_migration/`

---

## 1. 构建与 `*-api` 模块拆分
- [√] 1.1 在 `pom.xml` 增加新模块 `user-api`、`social-api`、`content-api`、`analytics-api`，verify why.md#req-api-modules
- [√] 1.2 创建 `user-api/pom.xml`（jar 模块，依赖 `common`），verify why.md#req-api-modules，depends on task 1.1
- [√] 1.3 创建 `social-api/pom.xml`（jar 模块，依赖 `common`），verify why.md#req-api-modules，depends on task 1.1
- [√] 1.4 创建 `content-api/pom.xml`（jar 模块，依赖 `common`），verify why.md#req-api-modules，depends on task 1.1
- [√] 1.5 创建 `analytics-api/pom.xml`（jar 模块，依赖 `common`），verify why.md#req-api-modules，depends on task 1.1

## 2. common：Dubbo 调用治理公共能力（最佳实践落点）
- [√] 2.1 新增 Dubbo trace 透传 Filter（consumer 写 attachment / provider 注入 TraceContext 并清理），建议落在 `common/src/main/java/com/nowcoder/community/common/dubbo/TraceContextDubboFilter.java`，verify why.md#scn-governance
- [√] 2.2 新增 Dubbo Micrometer 指标 Filter（计数 + 时延 + outcome 标签），建议落在 `common/src/main/java/com/nowcoder/community/common/dubbo/DubboMetricsFilter.java`，verify why.md#scn-governance，depends on task 2.1
- [√] 2.3 约定 attachment key（`X-Trace-Id`/`traceparent`）与 outcome 口径，并在 `common` 统一常量定义，verify why.md#scn-governance，depends on task 2.1

## 3. user-api：RPC 接口与 DTO
- [√] 3.1 定义 user-service 的内部认证与会话接口（替代 `/internal/users/**`），新增 `user-api/src/main/java/com/nowcoder/community/user/api/rpc/UserInternalRpcService.java`，verify why.md#scn-auth-user
- [√] 3.2 迁移/新增认证相关 DTO 到 `user-api`（authenticate/register/activate/session-profile/by-email/refresh-token），路径 `user-api/src/main/java/com/nowcoder/community/user/api/rpc/dto/*`，verify why.md#scn-auth-user，depends on task 3.1
- [√] 3.3 定义用户摘要/批量查询/用户名解析 RPC（替代 `message-service` 直连 `/api/users/**` 与 `/internal/users/batch-summary`），新增 `user-api/src/main/java/com/nowcoder/community/user/api/rpc/UserReadRpcService.java`，verify why.md#scn-message-social-user
- [√] 3.4 定义用户处罚治理 RPC（moderation-status/scan/apply），新增 `user-api/src/main/java/com/nowcoder/community/user/api/rpc/UserModerationRpcService.java`，verify why.md#scn-content-social-user

## 4. social-api：RPC 接口与 DTO
- [√] 4.1 定义用户主页统计聚合与关注/获赞等 read RPC，新增 `social-api/src/main/java/com/nowcoder/community/social/api/rpc/SocialReadRpcService.java`，verify why.md#scn-user-social-profile
- [√] 4.2 定义拉黑关系查询 RPC，新增 `social-api/src/main/java/com/nowcoder/community/social/api/rpc/SocialBlockRpcService.java`，verify why.md#scn-message-social-user
- [√] 4.3 定义点赞扫描 RPC（供 content-service 回填投影），新增 `social-api/src/main/java/com/nowcoder/community/social/api/rpc/SocialLikeScanRpcService.java`，verify why.md#scn-content-social-user
- [√] 4.4 迁移 `SocialLikeScanResponse` 等 DTO 到 `social-api/src/main/java/com/nowcoder/community/social/api/rpc/dto/*`，并在 consumer 侧删除重复定义，verify why.md#scn-content-social-user，depends on task 4.3

## 5. content-api：RPC 接口与 DTO
- [√] 5.1 定义帖子扫描 RPC（供 search-service reindex），新增 `content-api/src/main/java/com/nowcoder/community/content/api/rpc/ContentScanRpcService.java`，verify why.md#scn-search-content-reindex
- [√] 5.2 迁移 `ContentPostScanResponse` 到 `content-api/src/main/java/com/nowcoder/community/content/api/rpc/dto/*`，verify why.md#scn-search-content-reindex，depends on task 5.1
- [√] 5.3 定义实体解析 RPC（POST/COMMENT owner/postId 解析），新增 `content-api/src/main/java/com/nowcoder/community/content/api/rpc/ContentEntityRpcService.java`，verify why.md#scn-social-content-resolve

## 6. analytics-api：RPC 接口与 DTO
- [√] 6.1 定义 analytics 采集 RPC（recordUv/recordDau），新增 `analytics-api/src/main/java/com/nowcoder/community/analytics/api/rpc/InternalAnalyticsRpcService.java`，verify why.md#scn-gateway-analytics

## 7. Provider：各服务暴露 Dubbo 服务（@DubboService）
- [√] 7.1 user-service 实现 `UserInternalRpcService`（调用现有 `InternalUserService` 逻辑并返回 `Result`），新增 `user-service/src/main/java/.../rpc/UserInternalRpcServiceImpl.java`，verify why.md#scn-auth-user，depends on tasks 3.1~3.2
- [√] 7.2 user-service 实现 `UserReadRpcService` / `UserModerationRpcService`，新增 `user-service/src/main/java/.../rpc/UserReadRpcServiceImpl.java`（或拆分实现类），verify why.md#scn-message-social-user，depends on tasks 3.3~3.4
- [√] 7.3 social-service 实现 `SocialReadRpcService` / `SocialBlockRpcService` / `SocialLikeScanRpcService`，新增 `social-service/src/main/java/.../rpc/*RpcServiceImpl.java`，verify why.md#req-rpc-migration，depends on tasks 4.1~4.4
- [√] 7.4 content-service 实现 `ContentScanRpcService` / `ContentEntityRpcService`，新增 `content-service/src/main/java/.../rpc/*RpcServiceImpl.java`，verify why.md#req-rpc-migration，depends on tasks 5.1~5.3
- [√] 7.5 analytics-service 实现 `InternalAnalyticsRpcService`（调用现有 `AnalyticsService` 并返回 `Result.ok()`），新增 `analytics-service/src/main/java/.../rpc/InternalAnalyticsRpcServiceImpl.java`，verify why.md#scn-gateway-analytics，depends on task 6.1

## 8. Consumer：替换现有 HTTP internal clients 为 Dubbo 调用（一次性切换）
- [√] 8.1 auth-service：将 `auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java` 替换为 Dubbo 调用（不再注入 RestTemplate/baseUrl），verify why.md#scn-auth-user，depends on task 7.1
- [√] 8.2 user-service：将 `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java` 替换为 Dubbo 调用并保留 fail-open 降级语义，verify why.md#scn-user-social-profile，depends on task 7.3
- [√] 8.3 message-service：将 `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java` 替换为 Dubbo 调用（保留 resolveCache），verify why.md#scn-message-social-user，depends on task 7.2
- [√] 8.4 message-service：将 `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java` 替换为 Dubbo 调用，verify why.md#scn-message-social-user，depends on task 7.3
- [√] 8.5 message-service：将 `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationClient.java` 替换为 Dubbo 调用，并与 content-service 复用同一 DTO（来自 `user-api`），verify why.md#scn-content-social-user，depends on task 7.2
- [√] 8.6 content-service：将 `content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`、`content-service/src/main/java/com/nowcoder/community/content/like/SocialLikeScanClient.java` 替换为 Dubbo 调用，verify why.md#scn-content-social-user，depends on task 7.3
- [√] 8.7 content-service：将 `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java` 替换为 Dubbo 调用，verify why.md#scn-content-social-user，depends on task 7.2
- [√] 8.8 search-service：将 `search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java` 替换为 Dubbo 调用，verify why.md#scn-search-content-reindex，depends on task 7.4
- [√] 8.9 social-service：将 `social-service/src/main/java/com/nowcoder/community/social/service/ContentServiceClient.java` 替换为 Dubbo 调用，verify why.md#scn-social-content-resolve，depends on task 7.4
- [√] 8.10 gateway：将 `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java` 的 WebClient 调用替换为 Dubbo 调用（保持 best-effort），verify why.md#scn-gateway-analytics，depends on task 7.5

## 9. 依赖与配置：Dubbo + Zookeeper registry
- [√] 9.1 根与各模块补齐 Dubbo 依赖（`dubbo-spring-boot-starter` + zookeeper registry 依赖），修改各服务 `pom.xml`，verify why.md#req-rpc-migration
- [√] 9.2 各服务 `application.yml` 增加 Dubbo 配置（`dubbo.application.name`、`dubbo.registry.address=zookeeper://.../dubbo`、`dubbo.protocol.port`、默认 timeout/retries），verify why.md#scn-governance，depends on task 9.1
- [√] 9.3 更新 `deploy/docker-compose.yml`：为各服务注入 Zookeeper 地址（如 `DUBBO_REGISTRY_ADDR=zookeeper://zookeeper:2181/dubbo`），并补齐依赖启动顺序（`depends_on: zookeeper`），verify why.md#req-rpc-migration，depends on task 9.2

## 10. 清理：移除不再使用的 HTTP internal client 基建
- [√] 10.1 删除/下线各服务 `*RestClientConfig`（如 `auth-service/.../AuthRestClientConfig.java`、`message-service/.../MessageRestClientConfig.java` 等），并清理相关配置项（`*.base-url`、connect/read timeout 等），verify why.md#req-rpc-migration
- [√] 10.2 删除/下线 `search-service` 的 `ContentServiceClientProperties` 等仅用于 HTTP baseUrl 的配置类（迁移为 Dubbo reference 配置），verify why.md#scn-search-content-reindex

## 11. Security Check
- [√] 11.1 执行安全检查（G9）：确认 Dubbo 端口不对公网暴露；禁止透传 Authorization；检查敏感信息日志；确认网关继续拒绝 `/internal/**`，verify why.md#req-governance-observability

## 12. 文档同步（Knowledge Base）
- [√] 12.1 更新 `.helloagents/arch.md`：补充 Dubbo RPC 与 Zookeeper registry 架构图与说明，verify why.md#req-rpc-migration
- [√] 12.2 更新 `.helloagents/api.md`：标注 internal HTTP 端点的“运维/兼容”定位，并补充 RPC 约定，verify why.md#req-api-modules

## 13. 测试与验收
- [√] 13.1 跑单测：`mvn test`，重点关注 `auth-service`、`user-service`、`gateway`，verify why.md#scn-auth-user
- [?] 13.2 冒烟用例：登录/刷新、私信发送、发帖评论、点赞关注、搜索 reindex（运维入口）、analytics 采集不影响主链路，verify why.md#req-rpc-migration
