# Task List: Gateway 安全策略 SSOT 收敛（删除 legacy-matrix + CI 安全契约对齐）

Directory: `helloagents/plan/202602212346_gateway_security_ssot/`

---

## 1. Gateway（删除 legacy-matrix + 固化 transparent）
- [√] 1.1 简化网关安全链路：删除 `legacy-matrix` 分支与 `gateway.security.mode` 条件装配，固化为“/internal deny + /api/ops ADMIN + 其余 permitAll”，修改 `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`，verify why.md#requirement-gateway-security-ssot-scenario-internal-deny
- [√] 1.2 清理网关默认配置口径：移除 `gateway.security.mode` 相关配置与注释，修改 `gateway/src/main/resources/application.yml`，verify why.md#requirement-gateway-security-ssot-scenario-transparent-forward
- [√] 1.3 清理配置模板口径：移除 `gateway.security.mode` 相关配置与注释，修改 `deploy/nacos-config/gateway.yaml`，verify why.md#requirement-gateway-security-ssot-scenario-transparent-forward
- [√] 1.4 更新网关安全契约测试（如需）：调整 `gateway/src/test/java/com/nowcoder/community/gateway/GatewaySecurityConfigTest.java` 以匹配“仅透明模式”的新实现，verify why.md#requirement-gateway-security-ssot-scenario-ops-admin-gate

## 2. CI 安全契约对齐检查（公开 GET / 管理接口）
- [√] 2.1 user-service：新增/补齐安全契约测试覆盖 `/files/**` 公开、`/api/users/*` 公开、`/api/users/admin/**` 仅 ADMIN，新增 `user-service/src/test/java/com/nowcoder/community/user/api/UserSecurityContractTest.java`，verify why.md#requirement-ci-security-contract
- [√] 2.2 content-service：新增安全契约测试覆盖 `/api/categories**`、`/api/tags**` 公开，以及治理入口（`/api/moderation/**`、`/api/posts/*/(top|wonderful|delete)`）受限，新增 `content-service/src/test/java/com/nowcoder/community/content/api/ContentSecurityContractTest.java`，verify why.md#requirement-ci-security-contract
- [√] 2.3 social-service：新增安全契约测试覆盖公开计数/列表接口（`/api/likes/count*`、`/api/follows/*`）可匿名访问、其余接口需登录，新增 `social-service/src/test/java/com/nowcoder/community/social/api/SocialSecurityContractTest.java`，verify why.md#requirement-ci-security-contract
- [√] 2.4 回归现有契约测试：确认 `search-service/src/test/java/.../SearchSecurityContractTest.java`、`analytics-service/src/test/java/.../AnalyticsSecurityContractTest.java`、`ops-service/src/test/java/.../OpsSecurityContractTest.java` 仍覆盖关键约束并通过，verify why.md#requirement-ci-security-contract

## 3. Security Check
- [√] 3.1 安全自查：确认网关不再维护业务授权矩阵、各服务 `*SecurityConfig` 为 SSOT；确认未引入“匿名访问管理入口”或“认证绕过”路径；确认应急手段为 `blocked-path-patterns` + 回滚（不再依赖 legacy-matrix 双模式）

## 4. Documentation Update（知识库同步）
- [√] 4.1 更新架构与模块文档，移除 `legacy-matrix` 相关口径并明确 SSOT 策略，修改 `docs/ARCHITECTURE.md`、`helloagents/wiki/modules/gateway.md`，verify why.md#requirement-gateway-security-ssot
- [√] 4.2 更新 `helloagents/CHANGELOG.md` 记录本次治理变更（Removed/Changed/Test 等），verify why.md#requirement-ci-security-contract

## 5. Testing
- [√] 5.1 执行 `mvn test`（根目录）并确认新增契约测试为阻断门禁，记录关键失败信息（如有）并修复，verify why.md#requirement-ci-security-contract

## 6. Solution Package Lifecycle
- [√] 6.1 开发实现完成后，将方案包迁移到 `helloagents/history/YYYY-MM/` 并更新 `helloagents/history/index.md`（按 G11），标记 task 状态
