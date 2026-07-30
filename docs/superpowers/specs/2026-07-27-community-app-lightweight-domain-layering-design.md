# Community App 轻量领域分层设计

## 背景

`community-app` 已通过 DDD Tactical Layering 明确 owner、事务和基础设施边界，但固定的类名与调用链也产生了机械转发：相同字段在 controller result、application result、API model 之间重复，domain event 经 Spring bridge 再进入 ApplicationService，简单查询与 saga、账本使用同一套结构模板。

本设计保留真实边界，删除没有独立变化理由的结构。目标不是最少文件，而是让业务变化到达稳定边界后停止传播。

## 决策

### 强制边界

1. Controller 不访问 domain、repository、mapper 或 infrastructure，只进入同域 ApplicationService。
2. Application 不依赖 HTTP、broker、MyBatis mapper 或 dataobject；写事务由 application entry 持有。
3. Domain 是 plain Java，不依赖 Spring、application、infrastructure 或 owner API。
4. Foreign domain 只能使用 owner 的 `api.query`、`api.action`、`api.model` 或 `contracts.event`。
5. 持久化事件使用独立 contract，并通过与 owner 主事实同事务的 outbox 发布。

### 按需结构

1. 单用例 command/result 使用嵌套 record；复用或独立演化时再建立顶层类型。
2. Application result、owner API model 与 HTTP response 的字段语义和生命周期一致、且不含 transport type 时，可以复用同一个值；same-domain controller 仍只调用 ApplicationService，不注入 owner API entry。
3. Owner ApplicationService 可以直接实现本域同步 API。只有实质转换存在时才添加 API adapter。
4. 简单查询可以使用 application query port，不强制创建 domain model。
5. ApplicationService 可以直接把 integration event 写入 outbox port。Domain event 与 Spring bridge 只用于独立的本地订阅关系。
6. Focused application helper 使用真实角色命名，例如 `Assembler`、`Scheduler`、`Publisher`，不伪装成 ApplicationService。

## 标准链路

```text
simple query
  Controller -> ApplicationService -> QueryPort

local write
  Controller -> ApplicationService -> Domain + Repository

synchronous owner collaboration
  CallerApplicationService -> OwnerApi -> OwnerApplicationService

durable asynchronous collaboration
  OwnerApplicationService -> ContractEventOutboxPort
  -> OutboxHandler -> Broker -> Listener -> ConsumerApplicationService
```

## 守卫策略

ArchUnit 主要守卫越层、owner 泄漏、transport 泄漏和事务位置。它不要求 command/result 独占文件，不要求 owner API 必须由 infrastructure adapter 实现，也不要求 integration event 经过 local domain-event bridge。

新增结构规则必须说明它防止的真实故障、适用范围和误报成本。只约束类名或固定跳数、但不能阻止业务错误的规则不进入强制守卫。

同步协作守卫只检查两件事：business / adapter domain application 的跨域依赖必须落在 published `api.query`、`api.action` 或 `api.model`，核心域同步依赖图必须无环。它不再用逐类、逐类型 edge baseline 冻结当前实现。已清零的迁移例外直接删除，不保留空 set、空 map、透传参数或“集合必须为空”的测试；未来真实例外必须以具体规则和理由重新评审。

`infrastructure.api` 是一个有意保持狭窄的例外包。静态分析无法可靠判断方法体是否只是 identity forwarding，因此 `InfraBoundaryArchTest` 对当前 6 个实质 adapter 使用 reviewed set。新 adapter 不是被禁止，但必须连同转换/策略理由更新 reviewed set 和架构文档。

## 全仓审计结果

本次对 `community-app` 的同步 API、事件发布链、ApplicationService 以及 application command/result 做了全仓审计：

- 删除 18 个只做字段搬运或 delegate 的 infrastructure API adapter，owner ApplicationService 直接实现对应 API。
- 删除 31 个镜像或无独立语义的 standalone application command/result；用 owner API model、嵌套 record 或原始 use-case 参数表达。`CommentCreateResult` 和 `HotPathPrewarmResult` 分别收回唯一 owner ApplicationService 作为 public nested record。
- 删除 content 的 3 个 local event bridge、2 个 Spring event publisher、7 个仅服务单一外部反应的 domain event/publisher 类型，以及 2 个 forwarding contract-event ApplicationService。
- content 主代码中的 `*Bridge` 数量归零；帖子和评论 contract event 都在 owner 事务内直接写 outbox。
- 删除只验证 delegate forwarding 的 adapter/bridge 测试，业务断言移到 ApplicationService、最终 contract payload 和集成测试。
- 内部事务组件使用职责名：`MarketOrderAutoConfirmer` 表达单订单确认，`MarketWalletActionCoordinator` 表达 durable action 的写入、取消和重放协调；只有真实用例入口继续使用 `ApplicationService` 后缀。

当前保留的 `infrastructure.api` adapter 及理由：

| Adapter | 实质职责 |
| --- | --- |
| `AnalyticsIngestActionApiAdapter` | 配置驱动的 DAU capture policy。 |
| `PostReadQueryApiAdapter` | content read result 到 profile 所需 published author activity view 的收缩。 |
| `SocialLikeQueryAdapter` | foreign API 到本地 port 的 entity type 和空值策略。 |
| `UserCredentialApiAdapter` | not-found、authentication failure 和 credential view 翻译。 |

## 保留的复杂结构

文件数量本身不是删除依据。wallet ledger 的双分录与重放保护、market saga 的 durable command/lease/recovery、Drive/OSS upload recovery、owner outbox dispatch 的反序列化和 envelope 校验都保留，因为这些类型分别承载状态机、事务切分、补偿或协议校验。后续只在出现独立变化理由时新增层或顶层 model。
