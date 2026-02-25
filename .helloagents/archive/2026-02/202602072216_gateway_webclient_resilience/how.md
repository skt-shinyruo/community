# 技术设计：网关 WebClient 全局超时与连接池兜底

## Technical Solution

### Core Technologies
- Spring Cloud Gateway（WebFlux）
- Spring `WebClient`
- Reactor Netty `HttpClient` + `ConnectionProvider`
- `ReactorClientHttpConnector`

### Implementation Key Points
1. 新增 `gateway.webclient.*` 配置项（建议以 `@ConfigurationProperties` 方式绑定），覆盖：
   - connect timeout（连接建立超时）
   - response timeout（请求响应超时）
   - read/write timeout（socket 读写超时）
   - 连接池：`maxConnections`、`pendingAcquireTimeout`、`pendingAcquireMaxCount`
   - 连接回收：`maxIdleTime`、`maxLifeTime`、后台清理周期（如 `evictInBackground`）
2. 在 `GatewayWebClientConfig` 中统一构建出站 `HttpClient`：
   - 基于 `ConnectionProvider` 创建 `HttpClient`
   - 设置 `CONNECT_TIMEOUT_MILLIS`、`responseTimeout`
   - 在连接建立后挂载 `ReadTimeoutHandler` / `WriteTimeoutHandler`
3. 将统一 `HttpClient` 通过 `ReactorClientHttpConnector` 注入到 `@LoadBalanced WebClient.Builder`，确保网关内部 `lb://...` 调用全量覆盖兜底。
4. 与 analytics 采集链路协同：
   - 全局兜底提供“连接层面”的第一道保护（避免悬挂连接/无限排队）
   - `AnalyticsCollectDispatcher` 保持“有界队列 + 并发上限 + 局部 timeout”，确保采集链路可丢弃，不会挤占主链路资源

## Architecture Decision ADR

### ADR-001: 统一网关 WebClient 的超时与连接池底座
**Context:** 目前网关 WebClient 缺少统一兜底配置，极端网络条件下存在资源耗尽风险；局部 `.timeout(...)` 不能覆盖连接层面的堆积风险。  
**Decision:** 在 `GatewayWebClientConfig` 统一构建 `HttpClient(ConnectionProvider)` 并注入到 `WebClient.Builder`，形成全局可配置的稳定性底座。  
**Rationale:** 统一底座能覆盖所有出站调用，降低“新增链路忘配超时”的演进风险；连接池的显式上限能在下游异常时保护网关。  
**Alternatives:**
- 仅在调用点补 `.timeout(...)` → 无法覆盖连接层资源占用，且容易遗漏
- 为 analytics 单独隔离连接池 → 有价值但属于增强项，可在后续迭代引入
**Impact:** 网关出站调用在异常网络下能更快失败并释放资源；需要运维在不同环境调参并观测指标。

## Security and Performance
- **Security:** 统一超时/连接池限制可降低“慢下游/黑洞”导致的资源耗尽型风险；不引入新的鉴权或外部暴露面。
- **Performance:** 显式限制 `maxConnections` 与 pending acquire 有助于网关在下游异常时保持可用；需根据并发与下游容量调整参数，避免误限流。

## Testing and Deployment
- **Testing:** 增加网关侧配置绑定测试与端到端超时行为验证（例如本地延迟响应 server，确保在 response timeout 内失败）。
- **Deployment:** 在 `gateway.yaml`（nacos）提供默认推荐值；上线后观察超时与 pending acquire 指标，逐步调优。

