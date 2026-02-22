# Technical Design: Gateway 安全策略 SSOT 收敛（删除 legacy-matrix + CI 安全契约对齐）

## Technical Solution

### Core Technologies
- Spring Cloud Gateway（Reactive）
- Spring Security（WebFlux/Servlet）
- JUnit 5 + MockMvc/WebTestClient（契约测试）
- Nimbus JOSE JWT（测试 token 构造）

### Implementation Key Points
1. **Gateway 安全链路简化**
   - 移除 `gateway.security.mode` 与 `legacy-matrix` 分支，避免双模式长期共存。
   - 保留并固定以下 3 层链路（按 matcher + order 组合）：
     - `/internal/**`：denyAll（边界护栏）
     - `/api/ops/**`：JWT + `hasRole("ADMIN")`（运维入口双保险）
     - `anyExchange()`：permitAll（透明转发，不维护业务授权矩阵）
2. **配置口径清理**
   - 清理 `gateway/src/main/resources/application.yml` 与 `deploy/nacos-config/gateway.yaml` 的 `gateway.security.mode` 相关配置与注释，避免误配。
3. **CI 安全契约对齐检查（阻断型）**
   - 以“匿名访问不应返回 401/403”作为公开 GET 白名单的判定信号。
   - 以“未登录 401 / 角色不匹配 403”作为管理/治理接口的判定信号。
   - 对于“缺少 handler 的路径”，沿用既有测试模式：授权通过后以 404 作为“安全链路已放行”的稳定信号（避免依赖业务语义）。
4. **文档同步**
   - 更新架构/模块文档：明确网关透明模式为唯一策略，授权 SSOT 下沉到各服务。

## Security and Performance
- **Security**
  - SSOT 下沉：业务授权只在各服务执行，避免 gateway 与服务两套规则漂移。
  - 网关保留最小边界护栏与运维入口收口，降低误暴露风险。
  - CI 阻断型契约测试：覆盖公开 GET / 管理接口，减少“改接口忘改安全规则”的事故概率。
- **Performance**
  - 透明模式下，网关对大多数请求不做 JWT 解析与鉴权判断，减少每请求开销与链路复杂度。
  - 限流/OriginGuard/审计等横切能力保留，但通过开关可控，避免放大故障面。

## Testing and Deployment
- **Testing**
  - 执行 `mvn test`（根目录）作为门禁。
  - 覆盖重点：
    - gateway：`/internal/**` deny、`/api/ops/**` ADMIN、公开 GET 不被网关拦截
    - user-service：`/api/users/*` 公开、`/files/**` 公开、`/api/users/admin/**` ADMIN
    - content-service：`/api/posts**`/`/api/categories**`/`/api/tags**` 公开、治理入口受限
    - social-service：公开计数/列表接口可匿名访问，写/状态接口需登录
    - analytics/ops/search：现有契约测试继续作为回归保护
- **Deployment**
  - 配置中心（Nacos）与默认配置文件移除 `gateway.security.mode`，避免遗留配置误导。
  - 若出现紧急误放行风险，优先使用网关 `blocked-path-patterns` 临时隐藏高风险入口，并通过回滚恢复；不再依赖 `legacy-matrix` 双模式开关。

