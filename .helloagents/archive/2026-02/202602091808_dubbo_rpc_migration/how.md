# 技术方案：Dubbo RPC 服务间调用迁移（保留网关 HTTP 路由）

## Technical Solution

### Core Technologies
- Java 17 / Spring Boot 3（现有）
- Spring Cloud Gateway（对外 HTTP 入口保持不变）
- Apache Dubbo（服务间同步调用）
- Zookeeper（Dubbo registry；建议使用 chroot：`/dubbo`）
- Micrometer + Prometheus（统一指标口径）
- Kafka（异步事件通道保持不变）

### Implementation Key Points
1. **分层与依赖收敛（`*-api`）**
   - 新增 `user-api` / `social-api` / `content-api` / `analytics-api`
   - 仅在 `*-api` 中定义：Dubbo service interface + DTO（跨服务可见）
   - `*-service` 只实现接口，不暴露实现包给其他服务依赖
2. **RPC 返回协议选择（避免异常序列化坑）**
   - Dubbo service method 统一返回 `Result<T>`（业务错误编码在 `Result.code/message`，便于跨服务语义一致）
   - consumer 侧沿用 `InternalClientSupport.unwrap(Result<T>, serviceName)` 做解包与 `traceId` 保真
   - 网络/超时等基础设施错误由 Dubbo 抛出（consumer wrapper 负责映射为 `SERVICE_UNAVAILABLE` 并打点）
3. **调用治理最佳实践落点**
   - 默认 `timeout` 可配置且“偏短”（对齐当前 HTTP internal client 口径）
   - 默认 `retries=0`（避免放大写路径与下游抖动）；仅对明确幂等读路径开启 1 次重试
   - 写路径建议 `cluster=failfast`；读路径按需 `failover`（或保持 failfast + 业务层降级）
   - 提供统一 Dubbo Filter：traceId attachment 透传 + Micrometer 指标埋点
4. **对外入口保持不变**
   - gateway 仍使用 `spring.cloud.gateway.routes` 做 `/api/**` 路由转发
   - 仅替换“代码层服务间同步调用”；不要求前端接入 Dubbo
5. **网关内部出站调用收敛（analytics 采集）**
   - `AnalyticsCollectDispatcher` 由 WebClient 调用 `/internal/analytics/**` 迁移为 Dubbo 调用（best-effort）
   - 仍保留“有界队列 + 并发上限 + 超时 + 允许丢弃”的总体策略

## Architecture Design

```mermaid
flowchart TD
  FE[Frontend SPA] -->|HTTP /api/**| GW[gateway]
  GW -->|HTTP reverse proxy| SVC1[auth/user/content/...]
  GW -->|Dubbo RPC (best-effort)| ANA[analytics-service]

  SVC1 -->|Dubbo RPC| SVC2[other services]
  ZK[(Zookeeper /dubbo)] <-->|registry| SVC1
  ZK <-->|registry| SVC2

  NACOS[(Nacos)] <-->|Spring Cloud discovery/config| GW
  NACOS <-->|Spring Cloud discovery/config| SVC1
  NACOS <-->|Spring Cloud discovery/config| SVC2
```

## Architecture Decision ADR

### ADR-018: 保留 Spring Cloud Gateway HTTP 路由，仅迁移“服务间同步调用”为 Dubbo
**Context:** 项目已稳定运行在 “gateway 路由 + Nacos discovery/config + HTTP internal client + Kafka 事件” 体系上；本次目标是引入 Dubbo 提升服务间调用的治理能力与一致性，但用户要求“一次性切换替换”，不希望同时做基础设施大迁移。

**Decision:**
- 对外 HTTP 入口与网关路由保持不变（`/api/**` 继续由 gateway 反向代理到各服务）
- 服务间同步调用（原 `RestTemplate`/`WebClient` internal client）迁移为 Dubbo RPC
- Dubbo registry 使用 Zookeeper，并与 Kafka 使用同一 ZK 实例隔离到 chroot：`/dubbo`
- Nacos 继续保留用于 Spring Cloud Gateway 与服务配置管理（避免同时替换 discovery/config）

**Rationale:**
- 变更面可控：避免把“协议迁移（HTTP→Dubbo）”与“基础设施迁移（Nacos→ZK）”叠加成一次性高风险变更
- 与现有架构兼容：gateway 的反向代理与鉴权/限流/审计/trace 逻辑无需重写
- 一次性替换仍可实现：服务间调用点集中在 `*ServiceClient/*InternalClient`，迁移路径清晰

**Alternatives:**
- 统一注册中心到 Zookeeper（Nacos 全量替换） → 拒绝原因：涉及配置中心/服务发现/部署与运维体系的大迁移，风险与工作量显著更高
- 网关 BFF 化（gateway controller + Dubbo 下游调用） → 拒绝原因：需要迁移/重写全部 HTTP API 入口层，回归成本过高，不符合“最小破坏”原则

**Impact:**
- 运行期会同时存在 Nacos 与 Zookeeper 两套基础设施依赖（但职责清晰：HTTP 路由/配置 vs RPC registry）
- 需要新增 `*-api` 模块并维护跨服务接口的版本兼容（建议随 parent 版本一起发布）

## API Design（Dubbo）

> 说明：以下为“对齐现有 internal HTTP client”的最小集合；最终以实际调用点为准。

### user-api
- `UserInternalRpcService.authenticate(username, password) -> Result<InternalAuthenticateResponse>`
- `UserInternalRpcService.sessionProfile(userId) -> Result<InternalSessionProfileResponse>`
- `UserInternalRpcService.register(...) -> Result<InternalRegisterResponse>`
- `UserInternalRpcService.activate(userId, activationCode) -> Result<InternalActivationResponse>`
- `UserInternalRpcService.findByEmail(email) -> Result<InternalUserByEmailResponse>`
- `UserInternalRpcService.updatePassword(userId, newPassword) -> Result<Void>`
- `UserInternalRpcService.refreshTokenStore/refreshTokenFind/revoke/... -> Result<Void|Record>`
- `UserInternalRpcService.batchSummary(userIds) -> Result<List<UserSummary>>`
- `UserModerationRpcService.getStatus/scan/apply -> Result<ModerationStatus|List<ModerationStatus>>`

### social-api
- `SocialReadRpcService.userProfileStats(userId, viewerId) -> Result<UserProfileStats>`
- `SocialReadRpcService.userLikeCount/followeeCount/followerCount/hasFollowed -> Result<Long|Boolean>`
- `SocialBlockRpcService.isEitherBlocked(userIdA, userIdB) -> Result<Boolean>`
- `SocialLikeScanRpcService.scan(entityType, afterEntityId, afterUserId, limit) -> Result<SocialLikeScanResponse>`

### content-api
- `ContentScanRpcService.scanPosts(afterId, limit) -> Result<ContentPostScanResponse>`
- `ContentEntityRpcService.resolveEntity(entityType, entityId) -> Result<EntityResolveResponse>`

### analytics-api
- `InternalAnalyticsRpcService.recordUv(ip, date) -> Result<Void>`
- `InternalAnalyticsRpcService.recordDau(userId, date) -> Result<Void>`

## Security and Performance

- **Security**
  - Dubbo 端口属于“内网服务间通信”，必须确保仅在集群/内网可达（网络隔离/安全组/NetworkPolicy）。
  - gateway 已显式拒绝 `/internal/**`（HTTP）对外暴露；Dubbo 迁移不改变这一约束，但要避免把 Dubbo 端口暴露到公网。
  - 禁止在 RPC 附件中透传用户 `Authorization`，避免鉴权耦合与意外泄露；仅透传 traceId 等观测字段。
- **Performance / Governance Best Practices**
  - **Timeout：** 默认 consumer 超时设置为“偏短 + 可配置”，按调用重要性分级；后台任务可单独放宽。
  - **Retries：** 默认 `retries=0`；仅对明确幂等读接口配置 `retries=1`，并配合总超时（避免重试把尾延迟放大）。
  - **Fail Strategy：** 写接口 `failfast`；读接口可选 `failover` 或“业务层降级（fail-open）”。
  - **Thread Isolation：** provider 侧限制线程池与队列，避免单个下游变慢拖垮 JVM（必要时为重接口单独线程池/限流）。
  - **Metrics：** 在 consumer/provider Filter 统一记录调用次数与时延（tag：service/method/outcome/timeout/degraded），与现有 Prometheus 体系对齐。
  - **Tracing：** consumer Filter 写入 attachment（`X-Trace-Id`/`traceparent`）；provider Filter 注入 `TraceContext/MDC` 并 finally 清理，避免线程复用串线。

## Testing and Deployment

- **Testing**
  - 编译与单测：`mvn test`（重点覆盖 `auth-service` 与 `user-service` 的鉴权链路）
  - 冒烟验证：登录/刷新/注册、发帖/评论、点赞/关注、私信发送、搜索、analytics 采集不影响主链路
  - 关键链路观测：确认 Dubbo 调用指标、traceId 贯穿日志可串联
- **Deployment（Docker Compose）**
  - 复用现有 `deploy/docker-compose.yml` 的 `zookeeper` 服务作为 Dubbo registry（建议 chroot `/dubbo`）
  - 为每个服务补齐 Dubbo 环境变量（registry 地址、协议端口/随机端口等）
  - 允许多服务同机运行：建议 `dubbo.protocol.port=-1`（随机端口）或按服务配置独立端口，避免 20880 冲突
