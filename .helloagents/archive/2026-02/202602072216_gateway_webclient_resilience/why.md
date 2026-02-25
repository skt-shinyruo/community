# 变更提案：网关 WebClient 全局超时与连接池兜底

## 需求背景

当前网关侧 `GatewayWebClientConfig` 仅提供 `WebClient.builder()`，缺少对 Reactor Netty `HttpClient` 的统一兜底配置（连接超时/响应超时/读写超时/连接池等）。在极端网络条件（下游半开连接、DNS 抖动、网络黑洞等）下，局部链路若未显式设置超时，容易出现请求悬挂、连接堆积与事件循环压力增大，最终把网关拖垮。

现状中 `AnalyticsCollectDispatcher` 通过 `.timeout(...)` 对采集链路做了局部兜底，但这更像“补丁式保护”：它无法覆盖网关内未来新增的所有出站调用，也无法从连接层面限制资源占用（例如连接池排队无限增长）。

## 变更内容

1. 为网关侧 `WebClient` 增加统一底座：在 `GatewayWebClientConfig` 中构建并注入带兜底配置的 `ReactorClientHttpConnector(HttpClient)`。
2. 建立可配置的 `gateway.webclient.*` 参数集（超时 + 连接池），提供安全默认值，并支持按环境覆盖（本地/测试/生产）。
3. 保持 analytics 采集链路“可丢弃、主链路优先”的原则：全局超时/连接池兜底作为第一道防线；`AnalyticsCollectDispatcher` 的有界队列/并发上限/局部超时作为第二道容量保护。

## 影响范围

- **Modules:** `gateway`
- **Files:** `GatewayWebClientConfig.java`、新增 WebClient 配置属性类、网关配置（`application.yml` / `deploy/nacos-config/gateway.yaml`）、网关相关测试、知识库文档
- **APIs:** 无对外 API 变更（仅影响网关内部出站调用的稳定性）
- **Data:** 无

## 核心场景

### Requirement: gateway-webclient-global-resilience（网关 WebClient 全局兜底）
**Module:** gateway
为网关出站调用提供统一且可配置的连接池与超时兜底，避免极端网络条件下的资源耗尽。

#### Scenario: downstream-hang（下游无响应/网络黑洞）
下游服务发生网络黑洞或长时间无响应
- 期望：请求在统一的响应超时内失败并释放资源
- 期望：连接池排队有上限，避免无限堆积导致网关雪崩

#### Scenario: downstream-slow（下游慢响应/抖动）
下游服务响应变慢或出现瞬时抖动
- 期望：连接超时/响应超时/读写超时能快速止损，避免悬挂连接长期占用
- 期望：通过可配置参数在“误伤率”与“资源保护”之间可调优

### Requirement: analytics-collect-capacity-guard（采集链路容量保护协同）
**Module:** gateway
确保 analytics 采集链路不会成为主链路瓶颈，即使 analytics-service 异常也不影响转发。

#### Scenario: analytics-down（analytics-service 不可用）
analytics-service 宕机或持续超时
- 期望：采集任务超时/丢弃，不影响主请求转发
- 期望：采集侧指标可观测（queued / dropped / timeout / error 等）

## 风险评估

- **Risk:** 统一超时配置过于激进可能造成“误超时”，影响网关内部调用的可用性
  - **Mitigation:** 提供保守默认值 + 可配置覆盖；在生产通过指标与报警逐步调参
- **Risk:** 连接池参数设置不当导致吞吐下降或排队过长
  - **Mitigation:** 显式限制最大连接数与 pending acquire；为不同环境提供推荐值与运维说明

