# 安全运行手册（旁路防护 / 默认安全态）

## 目标
- 只对外暴露 `gateway`（或 ingress），禁止直接暴露 `auth-service` / `user-service` / `content-service` 等下游服务端口
- 将“安全策略”收敛到可验证的边界：网关 + 服务侧兜底（fail-closed）
- 降低误配置、旁路访问、端口误暴露带来的爆炸半径

## 核心原则
1. **对外入口只有一个**：所有客户端流量必须经过 `gateway`
2. **服务侧必须可自洽**：对外业务接口由服务端 JWT 鉴权兜底；internal 面不做 header token 鉴权，因此必须依赖网络隔离确保不可对外暴露
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
- [ ] gateway 显式拒绝 `/internal/**`，且部署层确认下游服务端口不对外暴露（internal 面不做 token 鉴权）
- [ ] 对外运维入口 `/api/ops/**` 已在网关侧按角色收敛（仅管理员），并开启必要的审计与限流（建议）
- [ ] 可信代理与 `X-Forwarded-For` 解析规则已配置（只在可信链路信任 XFF）：
  - `gateway.trusted-proxy.enabled=true` 时必须配置 `gateway.trusted-proxy.cidrs`（CIDR allowlist）
  - 禁止 `0.0.0.0/0` / `::/0`（全量信任会导致 XFF 伪造）
  - prod profile 下 `StartupValidation` 会对危险配置 fail-closed 阻断启动
- [ ] 审计/错误日志不得打印敏感信息（token/密码/邮箱等），仅允许 traceId/用户 ID 等低敏字段

---

## Feed 请求预算自检（R3 / N+1 防回归）

### 目标

在上线/发版前，用“可执行”的方式快速确认 Feed（帖子列表）不会出现明显的性能与安全回归：
- ✅ 不发生 N+1 请求风暴（用户信息/点赞元信息走 batch）
- ✅ 匿名态不调用需要登录的 batch statuses 接口
- ✅ 前端 TTL 缓存（60 秒）在短窗口内生效，减少重复补水请求

### 适用范围

- 页面：`/#/` 或 `/#/posts`（帖子列表 / Feed）
- 关键接口（本次重点）：
  - `POST /api/users/batch-summary`（批量用户摘要）
  - `GET /api/likes/counts`（批量点赞计数，公开）
  - `GET /api/likes/statuses`（批量点赞状态，需要登录）

### 前置条件

1) 本地环境已启动（Docker Compose 或同等能力）
- gateway 默认：`http://localhost:12882`

2) 浏览器 DevTools 可用

3) 如需验证登录态：准备一个可登录账号
- 可先跑 `scripts/smoke-i0-auth.sh` 获取/验证账号链路（dev profile 下可开 onboarding 流程）

### 检查步骤（浏览器 Network）

Step 1：打开页面与 Network 面板
1) 打开 `/#/posts`
2) 打开 DevTools -> Network
3) 勾选 `Preserve log`（保留跳转日志）
4) 过滤建议：`Fetch/XHR` + Filter 输入 `/api/`

Step 2：验证“请求预算”是否稳定
- 重点观察是否出现反模式：
  - ❌ 对每一条帖子逐条调用 `GET /api/users/{id}`（用户信息 N+1）
  - ❌ 对每一条帖子逐条调用点赞接口（点赞元信息 N+1）
- 期望行为（同一批次补水）：
  - ✅ `POST /api/users/batch-summary`：最多 1 次（每次列表刷新/翻页可出现 1 次）
  - ✅ `GET /api/likes/counts`：最多 1 次（每次列表刷新/翻页可出现 1 次）
  - ✅ `GET /api/likes/statuses`：
    - 匿名态：不应出现
    - 登录态：最多 1 次（每次列表刷新/翻页可出现 1 次）

> 注：帖子列表本身的“内容接口”（例如 posts list）不在此预算统计范围内；这里关注的是 **附加补水请求是否线性膨胀**。

Step 3：验证 TTL 缓存是否生效（60 秒窗口）
1) 保持页面停留在 Feed
2) 在 60 秒内做一次“进入详情后返回”或“路由切换后回到 Feed”
3) 观察短窗口内不应反复触发同一批次的用户/点赞补水请求（除非列表内容发生变化或强制刷新）

### 失败时排查建议

- 观察到大量 `GET /api/users/{id}`：
  - 优先确认前端是否已升级为 batch 补水版本（PostsView）
  - 确认网关/安全配置已放行 `POST /api/users/batch-summary`
- batch 接口返回 403/404：
  - 检查 gateway 路由与 security 白名单
  - 检查服务是否启动/注册到 discovery（若使用 Nacos）

### 记录与验收

建议在一次发版前至少保留以下信息（截图或文本均可）：
- Feed 初次加载时 Network 截图（可看到 batch 请求数量）
- 登录态与匿名态各 1 次的接口行为对比
- 如触发问题：记录具体 path、返回码、traceId（如有）
