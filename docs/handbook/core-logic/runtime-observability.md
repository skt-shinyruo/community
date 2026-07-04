# Runtime Observability

Runtime observability 是共享基础设施能力，补充 [../observability.md](../observability.md) 的信号契约和 [../operations.md](../operations.md) 的排障入口。这里记录的是代码里已经自动接入的运行时 hook：它们发出结构化日志，帮助定位生命周期、资源阈值、慢请求和 Kafka 技术事件，但不替代业务事件、SLO 定义或 runbook。

## 生命周期和快照

`RuntimeApplicationLifecycleListener` 监听 Spring lifecycle：

- `ApplicationStartedEvent` 记录 `app_startup`。
- `ApplicationReadyEvent` 记录 `app_ready`。
- `ContextClosedEvent` 记录关闭起点。
- `destroy()` 记录 `app_shutdown`，并在关闭耗时达到 `spring.lifecycle.timeout-per-shutdown-phase` 时额外记录 `graceful_shutdown_timeout`。

`RuntimeSnapshotScheduler` 通过 fixed delay 周期执行 runtime snapshot。间隔来自 `community.observability.runtime-logging.periodic-summary-interval`，空值、0 或负数会回退到 60 秒。每轮会记录 JVM 内存压力，并在对应 bean 存在时记录 direct memory / class loading、executor、Hikari datasource 和进程资源快照。

## HTTP access

`ServletAccessRuntimeLogFilter` 是 Servlet 侧的慢请求 hook。它默认排除 `/actuator/health` 和 `/actuator/info`，对未排除请求在 filter chain 完成后计算耗时；只有耗时达到 `community.observability.runtime-logging.http.slow-request-threshold-ms` 才记录 `event.category=access`、`event.action=http_slow_request`。

路径会去掉 query string 和 matrix 参数，日志字段记录 method、sanitized path、HTTP status、duration 和 threshold。401/403 的 outcome 是 `denied`，2xx/3xx 是 `success`，其他状态是 `failure`。它不记录请求 body、Authorization、Cookie 或查询参数。

## Kafka hooks

`RuntimeKafkaProducerListener` 接入 Spring Kafka producer listener 的错误回调。发送失败时记录 `kafka_producer_error`，包含 topic、partition 和异常类型。

`RuntimeKafkaRebalanceListener` 接入 consumer rebalance 生命周期，在 partition assigned、revoked before commit、revoked after commit、lost 时记录 `kafka_rebalance`。它会汇总 consumer group、reason、topic 和 partition count；读取 consumer group metadata 失败时回退为 `-`。

这些 hook 和 `RuntimeKafkaRecordInterceptor`、Kafka lag logger 一起补足 operations 文档里 Kafka 技术事件的查询入口。

## 失败语义

这些 runtime hook 只描述运行状态，不把日志是否发出作为业务成功条件。代码不会因为记录到 `http_slow_request`、`kafka_producer_error` 或 `kafka_rebalance` 就改写业务结果，也不会把 lifecycle/snapshot 日志反馈给 domain/application 决策。

当前实现也没有在 `RuntimeLogWriter` 外层做统一吞异常包装：如果底层 logger 或被调用的 snapshot logger 自身抛出运行时异常，语义由调用它的框架位置决定。已实现 hook 的边界是观测运行态，不把观测结果设计成业务成败条件。
