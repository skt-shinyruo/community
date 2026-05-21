# Market 核心类细分

本文是 [../market.md](../market.md) 的类级补充。market 负责 listing、库存、订单、纠纷和 wallet saga。

## 先读顺序

1. `MarketListingApplicationService`
2. `MarketInventoryApplicationService`
3. `MarketOrderApplicationService`
4. `MarketDisputeApplicationService`
5. `MarketWalletActionApplicationService`
6. `MarketWalletActionProcessorApplicationService` / `MarketWalletActionRecoveryApplicationService`
7. `MarketOrderSagaApplicationService`

## 入口适配器

| 类 | 层 | 角色 |
| --- | --- | --- |
| `market.controller.MarketController` | controller | buyer / seller HTTP 入口。 |
| `market.controller.AdminMarketController` | controller | 市场治理 HTTP 入口。 |

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `market.application.MarketQueryApplicationService` | listing / order 查询和订单详情装配。 | 看它如何把订单和交付内容拼成视图。 |
| `market.application.MarketListingApplicationService` | listing 创建、更新、暂停、恢复、关闭。 | 看 listing 生命周期和库存挂钩。 |
| `market.application.MarketInventoryApplicationService` | 预加载虚拟库存追加、查询、失效和 listing 库存联动。 | 看库存单元和 listing 的绑定。 |
| `market.application.MarketAddressApplicationService` | 收货地址簿。 | 看地址快照和用户绑定规则。 |
| `market.application.MarketOrderApplicationService` | 下单、取消、交付、发货、确认。 | 看订单状态机和总额校验。 |
| `market.application.MarketDisputeApplicationService` | 买家争议、卖家处理、管理员裁决。 | 看争议如何推进订单终态。 |
| `market.application.MarketWalletActionApplicationService` | escrow / release / refund durable command 写入。 | 看 durable command 的 requestId 规则。 |
| `market.application.MarketWalletActionProcessorApplicationService` | due action claim、调用 wallet、推进 saga。 | 看 lease、重试和 wallet 回执。 |
| `market.application.MarketWalletActionRecoveryApplicationService` | lease 恢复、缺失 command 补写、已有 wallet 结果应用。 | 看崩溃恢复时如何补状态。 |
| `market.application.MarketOrderSagaApplicationService` | wallet action 后的订单 / 争议条件状态推进。 | 看 market 状态如何跟随 wallet 结果收敛。 |
| `market.application.MarketOrderAutoConfirmApplicationService` | 自动确认批任务入口。 | 看 due order 的批处理入口。 |
| `market.application.MarketOrderAutoConfirmSingleOrderApplicationService` | 单订单锁定和自动确认。 | 看单订单锁和重复执行幂等。 |

## 领域服务

| 类 | 核心职责 |
| --- | --- |
| `market.domain.service.MarketListingDomainService` | listing 发布和库存规则。 |
| `market.domain.service.MarketOrderDomainService` | 订单状态、购买数量和金额规则。 |
| `market.domain.service.MarketDisputeDomainService` | 争议发起和裁决规则。 |
| `market.domain.service.MarketWalletActionDomainService` | market wallet action requestId 和终态规则。 |

## 基础设施

| 类 | 核心职责 |
| --- | --- |
| `market.infrastructure.api.MarketOrderAutoConfirmActionApiAdapter` | 自动确认动作的同步适配。 |
| `market.infrastructure.job.MarketOrderAutoConfirmHandler` | XXL 自动确认 job。 |
| `market.infrastructure.job.MarketWalletActionProcessorHandler` | XXL wallet action processor job。 |
| `market.infrastructure.job.MarketWalletActionRecoveryHandler` | XXL recovery job。 |
| `market.infrastructure.persistence.*` | listing、inventory、order、dispute、delivery、shipment、wallet action 的持久化。 |

## 关键语义

- `market_wallet_action` 是 durable business command，不是普通事件投影。
- wallet 只是 market 的资金执行者，market 决定订单语义。
- recovery 任务是 saga 的一部分，不是补锅脚本。
- 自动确认必须和订单状态机一起看。
