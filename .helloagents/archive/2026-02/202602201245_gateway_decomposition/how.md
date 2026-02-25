# Technical Design: Gateway 边界收敛与职责拆分（降爆炸半径）

## Technical Solution

### Core Technologies
- Java 17 / Spring Boot 3.x
- Spring Cloud Gateway（Reactive）
- Spring Security（OAuth2 Resource Server / JWT）
- Nacos Discovery / Config
- Dubbo RPC（运维调用与旁路调用）
- Micrometer + Prometheus / Loki（可观测）
- Kafka（可选：用于统计采集与审计的事件化解耦）

### Implementation Key Points
1. **Gateway 安全策略“透明化”与最小护栏**
   - gateway 只对“边界必须控制”的路径做强策略：
     - `/internal/**`：显式 deny（避免误暴露）
     - `/api/ops/**`：可选在 gateway 做 ADMIN 角色收敛（双保险）
   - 其余 `/api/**` 默认 permitAll（不维护业务白名单），授权由服务侧 SSOT 决定。
   - 为避免“无关路径携带坏 token 也触发 401”带来的兼容性风险，建议使用 **多条 SecurityWebFilterChain**：
     - chain A（order 高，path matcher 为 `/internal/**`、`/api/ops/**`、`/__gateway/**` 等）：启用 resource-server，并做严格授权
     - chain B（兜底）：permitAll，不启用 resource-server（避免解析 token）

2. **服务侧授权作为 SSOT（防止透明化导致安全回归）**
   - 逐服务梳理并补齐安全策略与单测：哪些路径公开、哪些路径必须登录、哪些路径必须角色。
   - 对关键写接口与管理接口建立契约测试（“没有 token 必须 401”、“非管理员必须 403”）。

3. **运维平面隔离（推荐）**
   - 将 gateway 内部运维 handler（原 `/__gateway/ops/**` + forward route）迁移到独立 `ops-service`：
     - `ops-service` 对外暴露 `/api/ops/**`
     - 内部通过 Dubbo 调用 search/content/social/user 等服务的 ops RPC
   - gateway 仅做路由转发：`/api/ops/** -> lb://ops-service`
   - 优点：运维能力变更/依赖升级不再影响主 gateway 转发面；发布风险与爆炸半径显著缩小。

4. **审计与统计旁路化（分阶段）**
   - 审计：优先保持“结构化日志 + 不阻塞”原则；业务审计（真正的写副作用）更适合在服务侧落库/出事件。
   - 统计采集（UV/DAU）：
     - 短期：保持 best-effort、有界队列、默认关闭/灰度开启
     - 中期（推荐）：改为事件驱动（gateway 仅产出事件/日志，analytics-service 消费与聚合），移除 gateway -> analytics-service 的强依赖链路

5. **限流分层**
   - gateway：边界 anti-abuse（登录/注册/验证码/密码重置）+ 极少数高风险入口
   - 服务侧：业务敏感限流（发帖/评论/点赞/关注等）由领域服务结合业务语义实现（可更精细、影响面更小）

## Architecture Design

### Target Shape (Recommended)
```mermaid
flowchart TD
  U[Browser / SPA] --> GW[Edge Gateway (thin)]
  GW --> Auth[auth-service]
  GW --> User[user-service]
  GW --> Content[content-service]
  GW --> Social[social-service]
  GW --> Msg[message-service]
  GW --> Search[search-service]
  GW --> Ana[analytics-service]

  U --> Ops[ops-service (admin plane)]
  GW --> Ops

  GW -. audit/metrics .-> Obs[(Loki/Prometheus)]
  GW -. optional events .-> Kafka[(Kafka)]
  Kafka --> Ana
```

## Architecture Decision ADR

### ADR-010: Gateway 授权透明化（服务侧授权为 SSOT）
**Context:**  
gateway 当前维护了大量业务路径级 `permitAll/hasRole` 矩阵。该矩阵随业务演进不断扩大，导致：
- gateway 变更频率增加（每次新增/调整接口都要改 gateway）
- matcher 顺序/通配误配风险增大（可能误封/误放）
- 一旦 gateway 发布/配置出错，爆炸半径为全站

**Decision:**  
将业务授权的 SSOT 移到服务侧：
- gateway 仅保留少量“边界护栏”（例如 `/internal/**` deny、`/api/ops/**` 管控）
- gateway 不再维护公开接口白名单与业务角色矩阵（默认 permitAll 透明转发）

**Rationale:**  
- 减少 gateway 对业务路径知识的耦合，降低变更频率与误配风险
- 将授权责任与代码所有权贴近领域（由对应服务负责），降低爆炸半径
- 通过契约测试与 smoke 门禁弥补“透明化”带来的防护空洞

**Alternatives:**  
- Alternative A：继续由 gateway 维护业务矩阵 → 拒绝原因：强耦合、高频变更、误配爆炸半径大  
- Alternative B：引入集中策略服务（Policy Service/OPA）→ 暂缓原因：工程投入与运维复杂度更高，可作为后续演进

**Impact:**  
- 需要补齐服务侧安全配置与测试（避免透明化导致安全回归）
- 网关的 401/403 将主要由服务侧产生，统一错误协议需在服务侧保持一致

## Security and Performance
- **Security:**
  - `/internal/**` 在 gateway 层显式拒绝，避免误路由暴露
  - 关键管理/写接口在服务侧强制鉴权与角色校验（SSOT）
  - 对 `/api/ops/**` 采取“双保险”（gateway + ops-service 均校验 ADMIN），并提供 blocked list 作为应急熔断
  - 透明化后引入“安全契约测试 + smoke”作为门禁，防止服务误配 `permitAll`
- **Performance:**
  - gateway 降低 per-request 业务逻辑负担（减少 matcher 与业务 filter）
  - 可丢弃链路（统计/审计）严格异步化/有界化，避免资源堆积拖垮网关

## Testing and Deployment
- **Testing:**
  - gateway：保留/增强现有安全与错误协议契约测试，新增“透明模式”下公开接口可达性测试
  - services：为关键接口补齐安全契约测试（401/403/匿名可读）
  - 回归：执行 `scripts/doctor.sh` 与 `scripts/smoke-*.sh`（按环境选择）
- **Deployment（推荐分阶段、可回滚）：**
  1. 先补齐服务侧安全契约测试并上线（确保 SSOT 完整）
  2. gateway 引入“双模式”（enforcing vs transparent），默认保持现状（enforcing）
  3. 灰度切换到 transparent（按环境/按实例/按路由逐步），观测 401/403、QPS、延迟、错误率
  4. （可选）上线 ops-service 并切换 `/api/ops/**` 路由；保留旧实现一段时间作为回滚
  5. （可选）将 analytics 采集改为事件驱动，解除 gateway->analytics 的强依赖
