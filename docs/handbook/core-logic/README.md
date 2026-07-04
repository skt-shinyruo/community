# 核心逻辑专题

本目录存放跨业务域、跨模块或跨适配器的核心运行时机制专题。它补充 [../business-logic/](../business-logic/) 下的领域手册，以及 [../core-logic-index.md](../core-logic-index.md) 的代码导航索引。

本目录适合记录安全新鲜度、异步事件骨干、IM runtime、恢复与补偿、前端业务状态 / API 编排等稳定运行时主题。不要按类逐个建文档；专题页应解释一组相关类共同形成的行为，并从核心逻辑索引回链到相关源码入口。

专题页只描述当前已经实现的行为。未来目标、迁移设想和修复任务应放在审计或计划文档里，不要混入读者会当作运行事实的 handbook 页面。

## 专题地图

| 专题 | 说明 |
| --- | --- |
| [Token Freshness 与高风险请求安全](security-token-freshness.md) | 高风险 URI 的 JWT `security_version` 校验、401/403 映射和只读失败语义。 |
| [异步事件骨干](async-event-backbone.md) | owner contract event、DB outbox、Kafka dispatch、projection topic、retry / dedupe / DLQ 边界。 |
| [IM Core Runtime](im-core-runtime.md) | IM schema 兼容、消息持久化、幂等、outbox、未读、fanout routing 和 presence。 |
| [Runtime Configuration](runtime-configuration.md) | `/api/runtime-config` 的前端运行时配置快照和上传策略边界。 |
| [Runtime Observability](runtime-observability.md) | lifecycle、runtime snapshot、slow HTTP access 和 Kafka 技术事件 hook。 |
| [Gateway Runtime](gateway-runtime.md) | gateway route、IM edge route、canary instance filtering 和动态配置刷新。 |
| [前端业务状态与 API 编排](frontend-business-state.md) | 前端 API service、业务状态投影、route-to-page 能力和异步一致性提示。 |

只有同时满足以下条件时，才新增专题页：

1. 覆盖至少两个核心类，或一个跨域 / 跨模块机制。
2. 只读领域 handbook 会让读者误解当前行为。
3. 说明状态、失败语义、一致性或补偿。
4. 不是某个单类的源码复述。
5. 可以从 [../core-logic-index.md](../core-logic-index.md) 链接。
