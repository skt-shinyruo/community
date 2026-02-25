# Change Proposal: content 写路径引入 Domain Event + BEFORE_COMMIT Outbox 自动入队

## Requirement Background

当前 content 写路径在“命令入口分散 + payload 拼装重复 + 事件发布点位不统一”的组合下，放大了 Outbox 模式的关键一致性风险：

- **事务边界不一致**：部分命令链路在 Controller 或非事务服务中“先更新业务表，再 publish 事件”。在 Outbox 开启时，`OutboxEventService.enqueue` 发生异常会导致“业务已更新但 outbox 未入队”，用户侧可能看到失败响应但状态已变化，且难以通过测试覆盖所有入口分支。
- **写路径职责混杂**：以 `PostController` 为例同时承担读（list/detail/comments/replies）与写（top/wonderful/delete 等管理动作），依赖注入过多，导致写路径扩展与一致性治理成本持续上升。
- **payload 漂移风险**：同一事件（例如 `PostUpdated`）在不同发布点位使用不同字段集合拼装，存在“字段缺失/覆盖为空”的风险；例如热度刷新链路只填充部分字段，可能导致下游索引把 `tags/categoryId` 覆盖为空。

目标是将“业务表更新 + outbox enqueue”纳入同一事务提交，并通过 Domain Event 机制减少手写 publish 点位，降低未来引入新命令分支时遗漏事件的一致性风险。

## Change Content

1. **引入 content 域 Domain Event（写路径只 emit，不直接 publish Kafka/Outbox）**  
   将 `PostPublished/PostUpdated/PostDeleted` 等作为领域事件在事务内发布（Spring ApplicationEvent），业务代码只表达“发生了什么”，不关心“如何投递”。

2. **建立 Domain Event → Outbox 的统一桥接层（`@TransactionalEventListener(BEFORE_COMMIT)`）**  
   在事务提交前（BEFORE_COMMIT）统一将领域事件转为 `ContentEventPublisher` 调用，使 Outbox 入队与业务更新处于同一事务域，保证原子性。

3. **统一 PostPayload 拼装（assembler/mapper 单一来源）**  
   提供 `PostPayloadAssembler`，从权威数据源（同事务内可见的 `DiscussPost` + tags）构造完整 payload，确保 `categoryId/tags/title/content/type/status/createTime/score` 等字段一致，消除漂移与覆盖风险。

4. **收敛帖子状态变更命令入口到事务应用服务**  
   将 `top/wonderful/delete` 等管理动作统一下沉到 `@Transactional` 的应用服务（扩展 `PostCommandService` 或新增专用命令服务），Controller 仅保留鉴权/参数校验/DTO 映射与审计日志。

5. **修复旁路写路径的一致性缺口（例如热度刷新链路）**  
   将 “updateScore + PostUpdated 事件” 收敛到同一事务域，并复用 assembler，避免下游索引被部分字段覆盖。

## Impact Scope

- **Modules：** `content-service`（核心改造），影响下游消费行为仅体现在 payload 字段更稳定（契约不变）
- **Files：** 预计涉及 `content-service` 的 controller/service/event/score 等多处代码；新增 domain-event/assembler 相关类
- **APIs：** 对外 HTTP API 路由与响应结构保持不变；Kafka topic/type/envelope 维持现有契约
- **Data：** 不新增业务表；继续复用既有 outbox 表（`outbox_event`）作为可靠投递队列

## Core Scenarios

### Requirement: 帖子管理命令路径事务一致性（Outbox 原子入队）
**Module:** content

#### Scenario: 管理员置顶/加精/软删帖子
- 业务状态更新（`type/status` 等）与 Outbox 入队在同一事务提交
- 任一环节失败会触发整体回滚，避免“状态已改但 outbox 未入队”
- Controller 不再直接拼装 `PostPayload` 或调用 `ContentEventPublisher`

### Requirement: PostUpdated payload 字段完整且稳定（消除漂移）
**Module:** content

#### Scenario: 热度刷新更新 score 触发 PostUpdated
- 事件 payload 必须包含 `categoryId/tags/title/content/type/status/createTime/score`
- 下游（如 search-service）在 upsert 时不会因字段缺失导致索引信息被覆盖为空

### Requirement: 写路径事件发布点收敛（降低遗漏风险）
**Module:** content

#### Scenario: 新增一个“帖子状态变更”命令
- 业务代码仅需发布领域事件（或调用命令服务的统一方法）
- Outbox 入队与 envelope 构造由统一桥接层完成，无需重复拼装/重复埋点

## Risk Assessment

- **风险：领域事件发布未处于事务中导致监听器不触发（silent drop）**  
  **缓解：** 写路径统一收敛到 `@Transactional` 应用服务；DomainEventPublisher 在无事务时 fail-fast（抛异常或显式告警），避免静默丢事件。

- **风险：BEFORE_COMMIT 阶段组装 payload 需要额外 DB 查询，增加开销/锁冲突概率**  
  **缓解：** assembler 尽量复用同事务内已加载的实体数据；必要查询控制在主键查询与 tags 查询（可批量/缓存），并保持短事务。

- **风险：迁移期出现双发/漏发（新旧 publish 混用）**  
  **缓解：** 采用分步骤迁移；每条写路径“要么走 Domain Event，要么走旧 publish”，并增加测试与 metrics 对齐（queued/published 计数）。

