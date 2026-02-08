# 任务清单：移除 internal 接口鉴权与 token 机制（/internal/** 全量放行）

Directory: `helloagents/plan/202602081447_remove_internal_auth/`

---

## 1. common（internal client headers）
- [√] 1.1 移除 `X-Internal-Token` header 注入与相关常量，收敛 `InternalClientSupport.jsonHeaders`，file: `common/src/main/java/com/nowcoder/community/common/web/internalclient/InternalClientSupport.java`，verify why.md#/internal-不做服务端鉴权

## 2. auth-service（user-service internal client）
- [√] 2.1 删除 `UserServiceClientProperties` 中 `internalToken/opsInternalToken` 字段，file: `auth-service/src/main/java/com/nowcoder/community/auth/config/UserServiceClientProperties.java`，verify why.md#仓库内不再出现“发-token-但不验”的实现
- [√] 2.2 更新 `UserServiceInternalClient` 不再发送 internal token header，file: `auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java`，depends on task 1.1
- [√] 2.3 更新单测移除对 `X-Internal-Token` 的断言，file: `auth-service/src/test/java/com/nowcoder/community/auth/service/UserServiceInternalClientTest.java`
- [√] 2.4 清理本地配置中的 `auth.user-client.internal-token`，file: `auth-service/src/main/resources/application.yml`

## 3. search-service（content-service internal client）
- [√] 3.1 删除 `ContentServiceClientProperties.internalToken`，file: `search-service/src/main/java/com/nowcoder/community/search/config/ContentServiceClientProperties.java`
- [√] 3.2 更新 `ContentServiceClient` 不再发送 internal token header，file: `search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`，depends on task 1.1
- [√] 3.3 清理 `search.internal-token` 与 `search.content-client.internal-token` 配置，file: `search-service/src/main/resources/application.yml`

## 4. user-service（social-service internal client）
- [√] 4.1 删除 `SocialServiceClientProperties.internalToken`，file: `user-service/src/main/java/com/nowcoder/community/user/config/SocialServiceClientProperties.java`
- [√] 4.2 更新 `SocialServiceClient` 不再发送 internal token header，file: `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`，depends on task 1.1

## 5. content-service（internal clients）
- [√] 5.1 更新 `SocialBlockClient` 移除 `clients.social.internal-token` 依赖，file: `content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`，depends on task 1.1
- [√] 5.2 更新 `UserModerationClient` 移除 `clients.user.internal-token/ops-internal-token` 依赖，file: `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`，depends on task 1.1
- [√] 5.3 更新 `SocialLikeScanClient` 移除 `clients.social.internal-token` 依赖，file: `content-service/src/main/java/com/nowcoder/community/content/like/SocialLikeScanClient.java`，depends on task 1.1

## 6. social-service（content-service internal client）
- [√] 6.1 更新 `ContentServiceClient` 移除 internal token header 注入，file: `social-service/src/main/java/com/nowcoder/community/social/service/ContentServiceClient.java`，depends on task 1.1

## 7. message-service（internal clients）
- [√] 7.1 更新 `UserServiceClient` 移除 `clients.user.internal-token` 与 header 注入，file: `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`，depends on task 1.1
- [√] 7.2 更新 `SocialServiceClient` 移除 `clients.social.internal-token` 与 header 注入，file: `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`，depends on task 1.1
- [√] 7.3 更新 `UserModerationClient` 移除 internal token header 注入，file: `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationClient.java`，depends on task 1.1

## 8. frontend/scripts（ops token 清理）
- [√] 8.1 移除 `reindex` 请求的 `X-Ops-Token` header 与入参，file: `frontend/src/api/services/searchService.js`
- [√] 8.2 删除 Ops Console 中的 ops token 输入与 break-glass 提示，file: `frontend/src/views/OpsConsoleView.vue`
- [√] 8.3 删除 Search View 中的 ops token 输入与提示，file: `frontend/src/views/SearchView.vue`
- [√] 8.4 更新 `scripts/search-reindex.sh` 不再依赖 `OPS_TOKEN`/`X-Ops-Token`，file: `scripts/search-reindex.sh`

## 9. deploy/docs/knowledge base（配置与文档对齐）
- [√] 9.1 清理 `deploy/nacos-config/*` 中的 `internal-token` / `ops.*token*` 配置片段（全量），files: `deploy/nacos-config/*.yaml`
- [√] 9.2 清理 `deploy/docker-compose.yml` 中的 `*_INTERNAL_TOKEN` / `OPS_*_TOKEN` 环境变量，file: `deploy/docker-compose.yml`
- [√] 9.3 更新部署说明不再把 internal token 作为必填项，file: `deploy/README.md`
- [√] 9.4 更新安全/架构文档中的 internal-token/ops-token 叙述，files: `docs/SECURITY.md`, `docs/SYSTEM_DESIGN.md`, `docs/DEPLOYMENT.md`
- [√] 9.5 更新 helloagents runbooks（移除或明确废弃 internal-token/ops guard 机制），files: `helloagents/wiki/runbooks/internal-ops.md`, `helloagents/wiki/runbooks/internal-token-rotation.md`

## 10. Security Check
- [√] 10.1 执行安全检查（G9）：确认 internal token/ops token 已完全移除且无残留校验/发送逻辑；确认 gateway 仍拒绝 `/internal/**`，避免误配对外暴露

## 11. Testing
- [√] 11.1 运行后端单测：`mvn test`（BUILD SUCCESS）
- [√] 11.2 关键回归：`InternalAnalyticsControllerContractTest`、`InternalSearchControllerSecurityContractTest`、以及 internal client 单测

## 12. Documentation Update
- [√] 12.1 更新 `helloagents/CHANGELOG.md` 记录本次变更（Removed/Changed）
