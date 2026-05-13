# Wallet 核心类细分

本文是 [../wallet.md](../wallet.md) 的类级补充。wallet 关注账户、复式总账、充值提现转账、奖励和管理员操作。

## 先读顺序

1. `WalletApplicationService`
2. `WalletLedgerApplicationService`
3. `WalletAccountApplicationService`
4. `WalletRechargeApplicationService` / `WalletWithdrawApplicationService` / `WalletTransferApplicationService`
5. `WalletMarketApplicationService` / `WalletRewardApplicationService`
6. `WalletAdminOpsApplicationService`

## 入口适配器

| 类 | 层 | 角色 |
| --- | --- | --- |
| `wallet.controller.WalletController` | controller | wallet 对外 HTTP 入口。 |
| `wallet.controller.AdminWalletController` | controller | wallet 管理入口。 |

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `wallet.application.WalletApplicationService` | controller 聚合入口和 HTTP 幂等包装。 | 看 Idempotency-Key 如何统一到各业务动作。 |
| `wallet.application.WalletAccountApplicationService` | 账户创建、余额、状态、version 条件更新。 | 看余额和状态如何通过条件更新收敛。 |
| `wallet.application.WalletLedgerApplicationService` | 总账交易、双分录、requestId replay 校验。 | 看双分录 balance 和 replay 语义。 |
| `wallet.application.WalletRechargeApplicationService` | 充值订单和 RECHARGE 总账。 | 看订单和账本如何一起推进。 |
| `wallet.application.WalletWithdrawApplicationService` | 提现订单、两段 WITHDRAW 总账。 | 看提现申请和出账确认如何拆开。 |
| `wallet.application.WalletTransferApplicationService` | 转账订单和 TRANSFER 总账。 | 看转账 from/to 约束和幂等键。 |
| `wallet.application.WalletMarketApplicationService` | market escrow / release / refund owner action。 | 看 market 与 wallet 的资金协作接口。 |
| `wallet.application.WalletRewardApplicationService` | growth / reward 入账 owner action。 | 看奖励发放如何由 requestId 保护。 |
| `wallet.application.WalletAdminOpsApplicationService` | freeze / reverse 管理操作和审计。 | 看治理操作如何保持可追溯。 |

## 领域服务

| 类 | 核心职责 |
| --- | --- |
| `wallet.domain.service.WalletAccountDomainService` | 账户类型、冻结状态和分录方向规则。 |
| `wallet.domain.service.WalletLedgerDomainService` | 双分录平衡、金额上限和交易创建规则。 |
| `wallet.domain.service.WalletOrderDomainService` | 充值 / 提现 / 转账订单金额和转账规则。 |
| `wallet.domain.service.WalletAdminDomainService` | 管理员钱包操作 actor / reason 规则。 |
| `wallet.domain.service.WalletAmountPolicy` | 单次资金动作金额上限。 |

## 基础设施

| 类 | 核心职责 |
| --- | --- |
| `wallet.infrastructure.api.WalletAccountQueryApiAdapter` | 账户查询同步适配。 |
| `wallet.infrastructure.api.WalletMarketActionApiAdapter` | market 钱包动作同步适配。 |
| `wallet.infrastructure.api.WalletRewardActionApiAdapter` | reward 入账同步适配。 |
| `wallet.infrastructure.persistence.*` | account、ledger、order、admin action 的 MyBatis 持久化。 |

## 关键语义

- HTTP 幂等和总账 requestId 是两层不同语义。
- 双分录 balance 是资金事实的底线。
- market / growth / admin 都只是 wallet 的不同调用方。
- 冲正本身也是新的总账交易，不是简单回滚。

