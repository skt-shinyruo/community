# Technical Design: Origin 白名单收敛到 gateway

## Technical Solution

### Core Technologies
- Spring Cloud Gateway（Reactive）
- GlobalFilter（短路拒绝，不进入下游路由）
- Jackson ObjectMapper（输出统一 Result JSON）

### Implementation Key Points
1. **gateway 增加 OriginGuardGlobalFilter**
   - 仅对 `POST /api/auth/login|refresh|logout` 生效
   - 读取请求头 `Origin`：
     - 空/缺失：放行（与旧行为保持一致，兼容非浏览器客户端）
     - 不在 allowlist：返回 403（JSON Result），不转发到下游
   - allowlist 作为配置项集中在 gateway，并与 CORS allowlist 复用同一份列表（避免配置漂移）

2. **auth-service 移除 Origin allowlist 校验**
   - 删除 AuthService 中对 `Origin` 的强制校验逻辑与配置字段
   - 保持 auth-service 内网-only 的部署假设：外部调用统一走 gateway

## Architecture Decision ADR

### ADR-001: Origin allowlist 从 auth-service 收敛到 gateway
**Context:** 当前 Origin 规则分散在 gateway（CORS）与 auth-service（业务服务强校验）两处，导致本地端口变化需要多点同步；同时项目架构中 gateway 被定义为对外统一入口，auth-service 预期为内网-only。  
**Decision:** 将 Origin allowlist 校验收敛到 gateway，对敏感接口统一校验；auth-service 移除 Origin 校验逻辑。  
**Rationale:** 简化配置面、减少漂移；同时在“唯一入口”前提下仍保持安全控制点。  
**Alternatives:** 保留双层校验（更安全但维护成本更高）；改为同源部署（更彻底但改动面更大）。  
**Impact:** gateway 成为唯一策略点，需保证网络隔离与 gateway 配置正确；需要测试与文档明确边界。

## Security and Performance
- **Security:**
  - OriginGuard 仅覆盖敏感接口（login/refresh/logout）
  - allowlist 默认严格限制为本地 `localhost`（示例），生产需显式配置
  - 强假设：auth-service 不对外暴露（网络层隔离做实）
- **Performance:** 仅对少量路径进行常量匹配与列表包含判断，开销可忽略

## Testing and Deployment
- **Testing:**
  - gateway：新增单元测试覆盖 OriginGuard（允许/拒绝/缺失 Origin）
  - auth-service：运行现有单测，确保登录/刷新链路未回归
- **Deployment:**
  - 只需在 gateway 侧维护 Origin allowlist（以及 CORS allowlist）
  - 若启用 Nacos Config，需同步 gateway 的相关配置项（以配置中心为准）

