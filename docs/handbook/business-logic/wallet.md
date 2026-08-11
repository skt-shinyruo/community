# Wallet 钱包业务逻辑

钱包域拥有账户、余额、总账交易和复式分录。所有涉及余额变化的业务最终都应通过 wallet owner action 或 wallet application service 完成。

## Owner / SSOT

- wallet owns `wallet_account`、`wallet_txn`、`wallet_entry`、测试积分发放/销毁订单、转账订单和管理员钱包动作。
- market owns 市场订单和资金动作请求状态，但不 owns 钱包余额。
- content/social owns 奖励来源事件事实，growth owns 任务奖励触发；wallet owns 标准内容/点赞奖励投影规则和最终入账事实。

## 入口

HTTP：

- `GET /api/wallet/summary`
- `GET /api/wallet/transactions`
- `GET /api/wallet/capabilities`
- `POST /api/wallet/recharges`：兼容路径，语义是受配置和累计配额约束的测试积分发放。
- `POST /api/wallet/withdrawals`：兼容路径，语义是测试积分销毁，不代表外部出款。
- `POST /api/wallet/transfers`
- `POST /api/wallet/admin/freeze`
- `POST /api/wallet/admin/reverse`

owner API：

- `WalletMarketActionApi`：market escrow/release/refund。
- `WalletRewardActionApi`：growth 等外域提交显式奖励 issue/revoke。
- `WalletRewardKafkaListener`：从 `content.events` / `social.events` 接收标准内容和点赞奖励信号。

## 数据流

钱包域的数据流全部收敛到总账和账户两个层面：

1. 测试积分发放 / 销毁 / 转账：HTTP 写入口先做 `Idempotency-Key` 归一化，再进入对应的 application service。测试积分入口还必须通过 feature flag、单次上限和持久化累计配额校验；每个业务再按 `userId + requestId` 查找或创建订单，最后通过总账和仓储条件更新状态。
2. 余额事实：`wallet_account` 不是随意读写的缓存，而是由总账分录和条件更新共同维护。所有借贷动作都要先锁定账户，再按 transaction 指纹保证幂等。
3. 市场协作：market 只通过 `WalletMarketActionApi` 提交 escrow / release / refund，不直接写余额。钱包返回 `wallet_txn_id` 后，market 再推进自己的 saga 状态。
4. 奖励协作：growth 的任务奖励通过 `WalletRewardActionApi` 提交稳定 requestId；标准内容/点赞奖励由 wallet 自己的 Kafka listener 和 projection application 从 owner event 映射。两条路径最终都以 wallet requestId 作为总账幂等键。
5. 管理动作：冻结、冲正和管理员调整都写新的总账交易和审计记录，不直接修改旧交易或旧分录。

## 账户模型

`WalletAccountApplicationService` 管理账户：

- `ensureUserWallet(userId)`：确保用户钱包存在。
- `ensureSystemAccount(accountType)`：确保系统账户存在。
- `balanceOfUser(userId)`：查询用户余额。
- `statusOfUser(userId)`：查询用户钱包状态。
- `requireUserWalletActive(userId)`：校验用户钱包可主动出账。
- `setStatus(accountId, nextStatus)`：设置账户状态。
- `lock(accountId)` / `lockAll(accountIds)`：事务内用 `FOR UPDATE` 锁定单个或一组账户；批量结果按数据库 `account_id` 顺序返回。
- `apply(account, delta)`：条件更新余额。

账户状态：

- `ACTIVE`：正常。
- `FROZEN`：冻结，主动出账受限。
- 其他状态视为非法或 unknown。

系统入账类动作可以不受普通用户主动操作限制，否则退款/放款/奖励可能因收款方冻结永久卡死。

## 复式总账

`WalletLedgerApplicationService.post(...)` 是所有资金变化的核心：

1. 校验 command、requestId、txnType 和 postings。
2. 校验 bizType、bizId 非空。
3. `WalletLedgerDomainService.validateBalancedPostings(...)` 要求借贷平衡，再按账户聚合同方向金额、抵消相反方向金额；净额为零的账户不产生分录，每个有效账户最多保留一条 posting。
4. 对聚合结果再次校验借贷平衡，并以聚合后的金额和分录指纹作为 requestId replay 语义。
5. 按 requestId 查询已有交易。
6. 已存在时校验 txnType、bizType、bizId、金额和分录指纹一致；一致返回已有结果，不一致返回 replay conflict。
7. 创建 `wallet_txn`，初始 `PENDING`。
8. 一次查询全部唯一账户，并由数据库按二进制 `account_id` 顺序执行 `ORDER BY account_id FOR UPDATE`；调用方 posting 顺序不改变锁顺序。
9. 严格按查询返回顺序，根据账户类型和聚合分录方向计算余额 delta；每个账户只做一次 version 条件更新。
10. 每个账户写一条 `wallet_entry`，记录 balanceAfter。
11. 标记交易 `SUCCEEDED`。

总账 requestId 必须全局唯一，代表资金事实幂等键。

## 最近流水

HTTP `GET /api/wallet/transactions` 返回当前登录用户钱包账户的最近流水。

读取路径：

1. `WalletController` 只提取当前登录用户和 `limit`。
2. `WalletLedgerApplicationService.recentTransactions(...)` 归一化 `limit`，默认 `12`，范围 `1..50`。
3. `WalletAccountApplicationService.findUserWallet(...)` 只读查询用户钱包账户；没有账户时返回空列表，不创建账户。
4. `WalletLedgerApplicationService` 从用户钱包账户对应的 `wallet_entry` 读取分录，并关联 `wallet_txn`。
5. 返回金额按当前用户账户视角计算：`USER_WALLET` 的 normal direction 是 `CREDIT`，所以 `CREDIT` 为正，`DEBIT` 为负。

钱包查询接口不得使用 `ensureUserWallet(...)` 或 `loadUserWallet(...)` 作为读路径入口，避免 GET 请求产生账户创建副作用。

## 测试积分发放

HTTP `WalletRechargeApplicationService.recharge(...)`：

1. `wallet.test-credits.enabled` 和 `grant-enabled` 必须同时开启；默认配置均关闭。
2. 校验单次领取上限，并在 `wallet_test_credit_quota` 原子预占用户累计配额。
3. 从 `Idempotency-Key` 解析 HTTP 幂等键，用 `wallet:recharge + userId + key + amount fingerprint` 去重。
4. 加载或创建 `recharge_order`，写 `TEST_CREDIT_GRANT` 双分录：借记 `PLATFORM_TEST_CREDIT_EXPENSE`、贷记用户钱包，再推进到 `PAID`。该路径不触碰 `PLATFORM_CASH`。
5. 同一事务失败会回滚配额预占；已完成请求重放不会再次占用配额。

该入口只发放内部测试积分，不接收真实资金，也没有第三方支付回调。生产环境必须保持开关关闭。

## 测试积分销毁

HTTP `WalletWithdrawApplicationService.withdraw(...)`：

1. `wallet.test-credits.enabled` 和 `discard-enabled` 必须同时开启，并校验单次及累计配额；原子预占还要求用户累计销毁量不超过其累计领取量。
2. HTTP 幂等 fingerprint 包含 amount，重放不会重复销毁或重复占用配额。
3. 要求用户钱包 active，创建 `withdraw_order`，再以单笔 `TEST_CREDIT_DISCARD` 双分录借记用户钱包、贷记 `PLATFORM_TEST_CREDIT_EXPENSE`，不创建提现待处理账目。
4. `GET /api/wallet/capabilities` 的可销毁余额取“销毁配额余量”和“该用户尚未销毁的领取量”两者较小值，并明确返回 `realPaymentsSupported=false` 和 `realPayoutsSupported=false`。

该入口不产生银行、支付机构或其他外部出款。

## 转账

转账流程：

1. HTTP 幂等 fingerprint 包含 `toUserId` 和 `amount`。
2. `WalletOrderDomainService.validateTransfer(...)` 校验 from/to/amount，禁止转给自己。
3. 要求付款方钱包 active。
4. 确保收款方钱包存在。
5. 创建转账订单。
6. 写 TRANSFER 总账：付款方 debit，收款方 credit。
7. 返回转账订单结果。

## 市场资金动作

`WalletMarketApplicationService` 提供 market owner action：

- `escrowOrder(...)`：买家资金进入托管。
- `releaseOrder(...)`：托管资金放给卖家。
- `refundOrder(...)`：托管资金退给买家。

market 侧传入 requestId、orderId、buyer、seller 和 amount。wallet 侧用 requestId 幂等，返回 wallet txn id 给 market saga 推进订单状态。

## 奖励

`WalletRewardApplicationService`：

- `issue(...)`：发放奖励。
- `revoke(...)`：撤销奖励。
- `applyDelta(...)`：按正负 delta 写奖励/撤销总账。

显式奖励 requestId 由上游业务语义生成，例如 growth 的 task reward grant id。钱包只保证同 requestId 不重复记账。

标准社区行为奖励走 wallet 自己的异步投影：

```text
content.events / social.events
  -> WalletRewardKafkaListener
  -> WalletRewardProjectionApplicationService
  -> WalletRewardApplicationService
  -> WalletLedgerApplicationService
```

当前映射规则：

| owner event | 收益用户 | delta | sourceId |
| --- | --- | ---: | --- |
| `POST_PUBLISHED` | 发帖人 | `+10` | `post-published:<postId>` |
| `COMMENT_CREATED` | 评论人 | `+2` | `comment-created:<commentId>` |
| `LIKE_CREATED` | 被点赞实体 owner | `+1` | `<relationKey>:created` |
| `LIKE_REMOVED` | 被点赞实体 owner | `-1` | `<relationKey>:removed` |

点赞 actor 与实体 owner 相同时返回 no-op，不产生奖励或撤销。有效命令的总账 requestId 固定为 `wallet-reward:<sourceId>`；重复 Kafka 投递因此不会重复入账。已识别事件缺少 event ID、正数 owner version、发生时间或必需 payload 时 listener 抛错，进入 Kafka retry / `.dlq`；非目标事件直接忽略。

Kafka consumer 的配置键仍沿用 `user.reward.kafka.consumer.*` 以保持部署兼容，这只是历史配置命名，不表示 user 仍拥有奖励 projection。

## 管理员操作

`WalletAdminOpsApplicationService`：

- `freezeWallet(actorUserId, targetUserId, reason)`：冻结目标用户钱包，写管理员动作。
- `reverseTxn(actorUserId, txnRef, reason)`：根据交易引用执行冲正，写管理员动作和总账。

规则：

- actorUserId 必须存在。
- reason 必须非空。
- 冲正必须能定位原交易。
- 冲正本身也必须是新的幂等总账交易。

## 失败和幂等

- HTTP 幂等和总账 requestId 是两层不同语义。
- HTTP replay fingerprint 不一致返回 replay conflict。
- 总账 requestId replay 指纹不一致返回 replay conflict。
- 分录不平衡直接拒绝。
- 金额必须为正且不超过 `WalletAmountPolicy` 上限。
- 账户余额更新使用锁和条件更新防止并发覆盖。
- 所有带 `@Transactional` 的 wallet application 入口由 wallet deadlock retry advisor 从事务外层包裹。数据库死锁或悲观锁获取失败会回滚当前完整事务，再重新进入 transaction advisor；默认最多 `3` 次、退避 `10ms`，上限分别钳制为 `5` 次和 `1s`。
- 已在 wallet 事务内部的嵌套 application 调用不自行重试，异常必须传播到最外层 wallet 事务边界，避免只重放总账子步骤。非锁异常不重试；次数耗尽后传播最后一次锁异常。
- 重试参数由 `wallet.deadlock-retry.max-attempts` 和 `wallet.deadlock-retry.backoff` 配置。requestId / 业务订单幂等仍是安全重放的前提，重试不会绕过 replay fingerprint。

## 关键代码

- `wallet.controller.WalletController`
- `wallet.controller.AdminWalletController`
- `wallet.application.WalletAccountApplicationService`
- `wallet.application.WalletLedgerApplicationService`
- `wallet.application.WalletRechargeApplicationService`
- `wallet.application.WalletWithdrawApplicationService`
- `wallet.application.WalletTransferApplicationService`
- `wallet.application.WalletMarketApplicationService`
- `wallet.application.WalletRewardApplicationService`
- `wallet.application.WalletRewardProjectionApplicationService`
- `wallet.application.WalletAdminOpsApplicationService`
- `wallet.infrastructure.event.WalletRewardKafkaListener`
- `wallet.infrastructure.retry.WalletDeadlockRetryConfiguration`
- `wallet.domain.service.*`
- `wallet.infrastructure.api.*`
