# Change Proposal: 运行时风险点修复（Redis unionKey / Gateway 采集 / TraceId / OriginGuard / internal-ops）

## Requirement Background
当前系统在“统计查询、网关采集、链路追踪、反代部署兼容性、internal/ops 配置治理”方面存在若干确定问题与风险点，集中表现为：
1. analytics-service 在 UV/DAU 区间查询时生成的 unionKey 未设置 TTL 且未删除，导致 Redis key/内存随后台查询增长而膨胀。
2. gateway 的 UV/DAU 采集在 WebFlux filter 中手动 `.subscribe()`，会引入不可控订阅与生命周期脱钩的副作用，压测与排障成本增大。
3. gateway 内 traceId 注入存在 WebFilter + GlobalFilter 两套重复实现，增加维护成本并存在“覆盖/不一致”的潜在困惑。
4. Origin allowlist 的同源判定在反向代理/HTTPS offload 场景依赖 scheme/host/port 获取，可能误判并意外拦截登录/刷新链路。
5. internal/ops 安全边界虽然严格，但配置约定隐式且分散，跨服务路由/命名调整时易出现大面积 403，且新增入口时容易遗漏治理覆盖面。

## Change Content
1. **analytics-service**：UV/DAU 区间统计的 unionKey 改为“临时 key”，并在异常与正常路径均保证清理（delete + 短 TTL 兜底），避免 Redis 膨胀与并发互相干扰。
2. **gateway**：UV/DAU 采集链路移除手动 `.subscribe()`，将“采集投递”纳入返回的 reactive 链路中执行，并保持“采集失败不影响主请求转发”的隔离属性。
3. **gateway**：traceId 注入统一为单一实现（保留一处、删除/禁用另一处），并统一引用 `TraceIdSupport` 常量，降低维护成本。
4. **gateway**：OriginGuard 的同源判定在“可信代理”场景下支持 Forwarded/X-Forwarded-*，避免反代/HTTPS offload 误拦；非可信来源仍不信任 forwarded 头，维持安全边界。
5. **common + docs(helloagents/wiki)**：补齐 internal-token / ops-token 的配置契约说明与运维手册（含路由映射、命名约定、轮转与排障 checklist），并按需要增强启动期校验提示，降低误配成本。

## Impact Scope
- **Modules:**
  - `analytics-service`（Redis 存储实现与测试）
  - `gateway`（filter/trace/origin/analytics 采集链路与测试）
  - `common`（启动期校验与治理契约补强，视实现选择）
  - `helloagents/wiki`（runbook 与模块文档更新）
- **Files (expected):**
  - `analytics-service/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdWebFilter.java`
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdGlobalFilter.java`（删除/禁用方案之一）
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilter.java`
  - `common/src/main/java/com/nowcoder/community/common/startup/StartupValidation.java`（可选增强）
  - `helloagents/wiki/runbooks/internal-ops.md`
  - `helloagents/wiki/runbooks/internal-token-rotation.md`
  - `helloagents/wiki/runbooks/gateway-analytics-collect.md`（如需同步说明）
- **APIs:** 不新增对外 API；internal 行为保持兼容（仅修复/增强治理与可观测性一致性）。
- **Data:** 不变更业务数据模型；仅改变 Redis 临时计算 key 的生命周期管理。

## Core Scenarios

### Requirement: 区间统计不产生永久 unionKey
**Module:** analytics-service

#### Scenario: 查询 UV/DAU 区间统计
在任意查询区间（≤ maxDaysRange）下：
- 期望：Redis 不遗留 `uv:*:*` / `dau:*:*` 的永久 unionKey（查询后清理；异常时 TTL 兜底）。
- 期望：并发查询相同区间互不干扰（避免共享同一个 unionKey 被提前删除/覆盖）。

### Requirement: 网关采集不使用手动 subscribe
**Module:** gateway

#### Scenario: 任意请求转发 + 采集投递
- 期望：代码中不再出现手动 `.subscribe()` 来触发采集副作用。
- 期望：采集链路失败/超时/鉴权上下文异常不会影响主请求转发与响应。

### Requirement: traceId 注入单一化且一致
**Module:** gateway

#### Scenario: 任意请求的 traceId/traceparent
- 期望：traceId 注入只保留一处实现；请求与响应头一致，便于日志/告警关联。
- 期望：后续修改 trace 规范时只需修改一处。

### Requirement: OriginGuard 兼容反代/HTTPS offload
**Module:** gateway

#### Scenario: 反向代理/HTTPS offload 场景下的 login/refresh/logout
- 期望：同源判定在可信代理场景能正确识别原始 scheme/host/port。
- 期望：非可信来源的 forwarded 头不被信任，避免被伪造绕过 allowlist。

### Requirement: internal/ops 配置契约可操作、可排障
**Module:** common + docs

#### Scenario: 新增/调整 internal 与 ops 路由
- 期望：文档清晰给出“路径 → 配置 key → header → 调用方/被调方”的映射。
- 期望：在 prod 配置缺失时尽量 fail-fast（或至少给出明确提示），避免上线后大面积 403 才发现问题。

## Risk Assessment
- **风险：** traceId 注入实现合并可能影响个别测试/边缘链路。  
  **缓解：** 保留 WebFilter 注入作为统一入口，补齐/更新 gateway 单测与冒烟测试。
- **风险：** OriginGuard 同源判定改动若错误信任 forwarded 头可能引入绕过。  
  **缓解：** 严格绑定可信代理 CIDR（复用现有 trusted-proxy 模型），非可信来源不读取 forwarded 信息。
- **风险：** analytics unionKey 临时化可能导致无法利用“区间缓存”。  
  **缓解：** 以删除为默认安全策略；必要时通过“短 TTL + 随机 key”或显式缓存开关扩展（作为可选项）。

