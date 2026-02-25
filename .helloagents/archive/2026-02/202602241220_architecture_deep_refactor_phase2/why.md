# 变更提案：架构深度重构（Phase 2：Reactive 去 ThreadLocal + Dubbo trace 对齐）

## 需求背景

上一阶段已完成 contracts 纯化、trace SSOT 收敛、outbox-only 默认安全态与 Security 去漂移，并通过架构门禁测试固化边界。

但在网关（WebFlux）侧仍存在一个典型“ThreadLocal 在 reactive 链路被显式使用”的残余点：`gateway` 的 analytics 采集异步调度器为了让 Dubbo 调用能带上原始请求的 traceId，会在 worker 线程中手动 `TraceContext.set/clear`。虽然当前实现已尽量在 finally 中清理，但该模式仍会：

1. **诱导误用回潮**：其他 reactive 代码可能复制该写法，将 ThreadLocal 带入更多 reactive 链路，造成隐蔽的串线风险。
2. **语义不够“显式”**：traceId 的传播依赖 ThreadLocal/MDC，而不是通过 RPC 协议层（attachments）显式透传。
3. **consumer 侧 trace 可能不一致**：当调用方已显式注入 attachment traceId 时，Dubbo consumer filter 仍可能生成一个“无关的”traceId 写入 TraceContext，导致 consumer 侧日志 trace 与实际透传的 trace 不一致。

## 目标（本次变更）

- **R1：Reactive 侧去 ThreadLocal**：网关 analytics 调度器不再依赖 `TraceContext/TraceId` ThreadLocal；trace 透传改为 Dubbo attachments 显式传递。
- **R2：Dubbo trace 一致性**：当 invocation 已携带 traceId attachment 时，consumer/provider 两侧都以该 traceId 为准，避免“consumer 侧日志 trace 漂移”。
- **R3：可回归防回潮**：补齐最小门禁测试，阻止 gateway 生产代码再次引入 `TraceContext`。

## 非目标

- 不在本次内推进 message-service 的同步耦合进一步治理（投影化深挖、回放策略升级），避免扩大变更爆炸半径。

