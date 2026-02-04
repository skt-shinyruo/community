# Technical Design: 运行时风险点修复（Redis unionKey / Gateway 采集 / TraceId / OriginGuard / internal-ops）

## Technical Solution

### Core Technologies
- Java 17 / Spring Boot 3（analytics-service）
- Spring Cloud Gateway + WebFlux（gateway）
- Redis（HyperLogLog/BitMap/BitOp，用于 UV/DAU 统计与网关限流等）
- Micrometer（指标与告警辅助）

### Implementation Key Points

#### 1) analytics-service：区间统计 unionKey 临时化 + 清理
- **问题根因：** 当前实现将 unionKey 写入 Redis，但没有 expire/delete，且 unionKey 由查询区间确定，容易累积与并发互相影响。
- **方案：**
  - unionKey 改为“每次查询生成随机临时 key”，避免并发冲突与互删。
  - 对 UV(HLL) 与 DAU(BITOP) 都采用 `try/finally`：先计算、后 `delete`；同时设置短 TTL 作为异常兜底（防止异常中断遗留 key）。
  - 如需减少 RTT，可使用 pipeline（可选优化，不作为第一阶段必须项）。
- **验证：**
  - 单测覆盖：生成的临时 key 不可预测但必须被删除；异常路径仍能设置 TTL/执行清理逻辑（至少调用 delete）。

#### 2) gateway：UV/DAU 采集去掉手动 subscribe
- **问题根因：** filter 内手动 `.subscribe()` 触发副作用，导致订阅生命周期脱离主链路，难以统一处理取消/背压/异常观测。
- **方案：**
  - 将“采集投递（dispatcher.trySubmit*）”构造成返回 Mono 的一部分（`then`/`doOnNext` + `onErrorResume`），由框架统一订阅与取消。
  - 保持“采集可丢弃”：任何采集错误都吞掉，只打指标/日志，不影响 `chain.filter(exchange)` 的转发与响应。
  - 维持当前 dispatcher 的有界队列与并发控制；filter 里只做轻量投递（不直接远程调用）。
- **验证：**
  - 单测/集成测覆盖：在有/无 principal、principal 解析异常、无 token 等情况下不影响主链路完成；同时确认采集投递被触发。

#### 3) gateway：traceId 注入合并
- **问题根因：** WebFilter 与 GlobalFilter 两套逻辑重复，常量引用也分散（部分代码引用 `TraceIdGlobalFilter.HEADER_TRACE_ID`）。
- **方案：**
  - 仅保留一处 trace 注入（优先保留 WebFilter，用于更早进入安全链路）。
  - 删除/禁用另一处实现，并将所有 header 常量统一改为引用 `TraceIdSupport`。
  - 更新/重写 gateway trace 相关测试（避免测试依赖已删除的类名/bean）。
- **验证：**
  - 冒烟：所有请求都返回 `X-Trace-Id` 与 `traceparent`；下游服务能透传/记录 traceId。

#### 4) gateway：OriginGuard 同源判定支持可信代理 forwarded 信息
- **问题根因：** 反代/HTTPS offload 时，`request.getURI()` 可能反映的是“内部协议/host”，与浏览器 Origin 不一致，导致误判并 403。
- **方案：**
  - 引入“有效请求 origin”解析：当且仅当 `gateway.trusted-proxy.enabled=true` 且 remoteIp 命中 CIDR allowlist 时，才读取 `Forwarded` 或 `X-Forwarded-Proto/Host/Port` 来计算 effective scheme/host/port。
  - 非可信来源：完全忽略 forwarded 头，回退到当前基于 requestUri/Host 的策略，避免伪造绕过。
  - `isSameOrigin` 基于 “effective request origin” 与 Origin header 做对比。
- **验证：**
  - 单测覆盖：无反代/有反代（可信/不可信）/缺失 forwarded 信息/异常 header 格式等情况。

#### 5) internal/ops：配置契约与治理收敛
- **问题根因：** internal/ops 配置 key 与路由映射依赖约定（segment 映射、users 特例、ops.guard 开关等），分散在代码与部署文件中，新人/运维容易踩坑。
- **方案：**
  - 文档收敛：在 `helloagents/wiki/runbooks/` 中补齐“路径 → header → 配置 key → 轮转策略 → 排障 checklist”。
  - 启动期校验增强（可选）：在 prod 下对关键开关（trusted-proxy、OriginGuard、ops.guard）给出更明确的缺失项提示，降低线上 403 才发现的概率。
- **验证：**
  - runbook 自检：按文档可完成 token 轮转、ops break-glass 开启与回滚、以及常见 403 排障。

## Security and Performance
- **Security：**
  - forwarded 头仅在可信代理 CIDR 命中时使用，禁止“全量信任”。
  - internal/ops 维持 fail-closed：internal-token/ops-token/allowlist 任一缺失默认拒绝。
- **Performance：**
  - analytics unionKey 临时化避免 Redis key 膨胀；区间计算仍是 O(days) 的合并，依赖 maxDaysRange 上限。
  - gateway 采集保持“轻量投递”，不引入额外远程同步调用。

## Testing and Deployment
- **Testing：**
  - analytics-service：新增 RedisAnalyticsRepository 的单测（验证临时 key 清理与并发安全策略）。
  - gateway：新增/更新单测，覆盖 analytics filter、trace 注入、OriginGuard（含 trusted proxy + forwarded 头）。
- **Deployment：**
  - 如处于反代/HTTPS offload，确保网关处于可信代理模式并配置 CIDR allowlist；必要时补齐代理层的 Forwarded/X-Forwarded-*。
  - 更新 runbook 后同步给运维与安全 review。

