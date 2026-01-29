# 安全运行手册（旁路防护 / 默认安全态）

## 目标
- 只对外暴露 `gateway`（或 ingress），禁止直接暴露 `auth-service` / `user-service` / `content-service` 等下游服务端口
- 将“安全策略”收敛到可验证的边界：网关 + 服务侧兜底（fail-closed）
- 降低误配置、旁路访问、内部 token 泄露带来的爆炸半径

## 核心原则
1. **对外入口只有一个**：所有客户端流量必须经过 `gateway`
2. **服务侧必须可自洽**：即使绕过网关直连服务，也不能降低安全性（例如 auth 的 OriginGuard、internal 的 token 兜底）
3. **默认拒绝（fail-closed）**：关键依赖（配置中心/限流/鉴权/幂等/运维开关）不可用时，应返回错误而非静默放行

## Docker Compose（旁路禁止）
- 默认：`deploy/docker-compose.yml` 不暴露业务服务端口（fail-closed）
- 本地联调：叠加 `deploy/docker-compose.ports.yml`，只暴露 `gateway`

命令示例：
- `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.ports.yml up -d`

## 必查清单（上线/演练）
- [ ] 外部网络仅可访问 `gateway`（以及必要的运维入口：例如观测系统，必须鉴权）
- [ ] 下游服务容器端口未映射到宿主机（或仅绑定 `127.0.0.1` 且有明确时限）
- [ ] auth-service 启用 OriginGuard（login/refresh/logout），与 gateway allowlist 一致
- [ ] internal 接口强制校验 `X-Internal-Token`，并避免使用“全局兜底 token”
- [ ] user-service 高权限 internal 写入口已分域 token：`user.ops.internal-token`（改密码/治理处置），调用方已切换对应 token
- [ ] internal OPS 运维入口默认关闭（break-glass），需要时按 runbook 临时开启：`helloagents/wiki/runbooks/internal-ops.md`
- [ ] internal OPS 运维入口具备 `X-Ops-Token` + allowlist + single-flight/rate-limit，且 Redis 不可用时 fail-closed 返回 503
- [ ] 可信代理与 `X-Forwarded-For` 解析规则已配置（只在可信链路信任 XFF）
- [ ] 审计/错误日志不得打印敏感信息（token/密码/邮箱等），仅允许 traceId/用户 ID 等低敏字段
