# 技术设计：移除 internal 接口鉴权与 token 机制（/internal/** 全量放行）

## Technical Solution

### Core Technologies
- Spring Boot + Spring Security（Servlet / WebFlux）
- RestTemplate（服务间 internal 调用）
- Vue（frontend 运维入口 UI）
- Nacos 配置（`deploy/nacos-config/*.yaml`）与 Docker Compose

### Implementation Key Points
1. **common：内部客户端 headers 统一收敛**
   - 调整 `InternalClientSupport`：`jsonHeaders` 仅负责 JSON 的 `Accept/Content-Type`，不再设置 `X-Internal-Token`
2. **各服务：删除 internal-token/ops-internal-token 读写路径**
   - 删除 `@ConfigurationProperties` / `@Value` 注入的 `internal-token`、`ops-internal-token` 字段
   - 更新所有 internal client 调用点，不再向 `InternalClientSupport` 传入 token
3. **frontend/scripts：移除 X-Ops-Token 发送与相关 UI/提示**
   - 删除 Ops Console / Search View 中的 `X-Ops-Token` 输入与提示文案
   - `reindex` 请求不再携带 `X-Ops-Token`
4. **deploy/docs：删除 token 配置项与 runbook 约定**
   - 清理 `deploy/nacos-config/*` 中的 `*-internal-token*` 与 `ops.*token*` 配置
   - 更新 `docs/*` 与 `helloagents/wiki/runbooks/*`，确保“以代码为准”的一致性

## Architecture Decision ADR

### ADR-001：服务端不再对 `/internal/**` 校验 token
**Context：**
仓库内存在 internal token 的发送逻辑，但缺少服务端校验实现，形成“发不验”的不一致状态，且会在部署/网关约定漂移时引入两极风险（全不可用/保护变弱）。

**Decision：**
- 明确策略：各服务端对 `/internal/**` 不做认证与鉴权（保持 `permitAll`），并移除 token 发送逻辑与相关配置/文档。
- gateway 继续对 `/internal/**` 做 denyAll 兜底（防误配对外暴露），对外运维入口走 `/api/ops/**` 并在网关侧用角色收敛。

**Alternatives：**
- 方案 A：实现 `InternalTokenFilter`（服务端强制校验 `X-Internal-Token`）→ 拒绝原因：与本次需求目标相反，且会引入 token 轮转/灰度/故障模式治理成本
- 方案 B：实现 `InternalOpsGuardFilter`（break-glass + allowlist + ops-token）→ 拒绝原因：与本次需求目标相反，会增加配置复杂度与误用风险

**Impact：**
- 安全边界外移到网关/网络隔离；误暴露的爆炸半径更大，需要更严格的部署侧约束
- internal 调用链路更简单（不再存在 token/配置漂移导致的 403）

## Security and Performance
- **Security：** 本次变更目标即移除 internal token/ops guard；建议保留 gateway 对 `/internal/**` 的显式拒绝，并在基础设施层加强网络隔离与限流审计。
- **Performance：** 移除 header 注入与配置读取，性能影响可忽略。

## Testing and Deployment
- **Testing：**
  - 运行全量单测：`mvn test`
  - 重点回归：internal controller contract tests（应继续允许无 token 访问）、以及 internal clients 的调用单测（不再断言 header）
  - 静态检查：全仓库搜索确认不再出现 `X-Internal-Token` / `X-Ops-Token`（除历史文档或明确标注的废弃文档）
- **Deployment：**
  - 清理 Nacos 配置中的 internal-token/ops-token key（避免误导）
  - 清理 compose 环境变量（`*_INTERNAL_TOKEN` / `OPS_*_TOKEN`）或保留但明确“已无效”（以代码为准）
