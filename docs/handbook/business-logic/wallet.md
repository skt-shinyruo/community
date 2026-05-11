# Wallet 钱包业务逻辑

钱包域拥有账户、余额、总账交易和复式分录。所有涉及余额变化的业务最终都应通过 wallet owner action 或 wallet application service 完成。

## Owner / SSOT

- wallet owns `wallet_account`、`wallet_txn`、`wallet_entry`、充值订单、提现订单、转账订单和管理员钱包动作。
- market owns 市场订单和资金动作请求状态，但不 owns 钱包余额。
- growth/user owns 奖励来源语义，但不 owns 余额事实。

## 入口

HTTP：

- `GET /api/wallet/summary`
- `POST /api/wallet/recharges`
- `POST /api/wallet/withdrawals`
- `POST /api/wallet/transfers`
- `POST /api/wallet/admin/freeze`
- `POST /api/wallet/admin/reverse`

owner API：

- `WalletMarketActionApi`：market escrow/release/refund。
- `WalletRewardActionApi`：growth/user reward issue/revoke。
- `WalletAccountQueryApi`：账户和余额查询。

## 数据流

钱包域的数据流全部收敛到总账和账户两个层面：

1. 充值 / 提现 / 转账：HTTP 写入口先做 `Idempotency-Key` 归一化，再进入对应的 application service。每个业务先按 `userId + requestId` 查找或创建订单，再调用 `WalletLedgerApplicationService.post(...)` 写双分录总账，最后更新订单状态。
2. 余额事实：`wallet_account` 不是随意读写的缓存，而是由总账分录和条件更新共同维护。所有借贷动作都要先锁定账户，再按 transaction 指纹保证幂等。
3. 市场协作：market 只通过 `WalletMarketActionApi` 提交 escrow / release / refund，不直接写余额。钱包返回 `wallet_txn_id` 后，market 再推进自己的 saga 状态。
4. 奖励协作：growth 或 user points 发放奖励时只传稳定 requestId，钱包以 requestId 作为总账幂等键，重复发放或撤销不会重复记账。
5. 管理动作：冻结、冲正和管理员调整都写新的总账交易和审计记录，不直接修改旧交易或旧分录。

## 账户模型

`WalletAccountApplicationService` 管理账户：

- `ensureUserWallet(userId)`：确保用户钱包存在。
- `ensureSystemAccount(accountType)`：确保系统账户存在。
- `balanceOfUser(userId)`：查询用户余额。
- `statusOfUser(userId)`：查询用户钱包状态。
- `requireUserWalletActive(userId)`：校验用户钱包可主动出账。
- `setStatus(accountId, nextStatus)`：设置账户状态。
- `lock(accountId)`：事务内锁定账户。
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
3. `WalletLedgerDomainService.validateBalancedPostings(...)` 要求借贷平衡。
4. 按 requestId 查询已有交易。
5. 已存在时校验 txnType、bizType、bizId、金额和分录指纹一致；一致返回已有结果，不一致返回 replay conflict。
6. 创建 `wallet_txn`，初始 `PENDING`。
7. 逐个锁定账户。
8. 根据账户类型和分录方向计算余额 delta。
9. 更新账户余额。
10. 写 `wallet_entry`，记录 balanceAfter。
11. 标记交易 `SUCCEEDED`。

总账 requestId 必须全局唯一，代表资金事实幂等键。

## 充值

HTTP `WalletApplicationService.recharge(...)`：

1. 从 `Idempotency-Key` 解析 HTTP 幂等键；body `requestId` 按未知字段返回参数错误。
2. 用 `wallet:recharge + userId + key + amount fingerprint` 做 HTTP 幂等。
3. `WalletRechargeApplicationService.complete(...)` 创建或复用充值订单。
4. 确保用户钱包和系统账户。
5. 写 RECHARGE 总账。
6. 返回充值订单结果。

当前实现是同步完成型充值，不包含真实第三方支付回调。

## 提现

提现流程：

1. HTTP 幂等 fingerprint 包含 amount。
2. `WalletWithdrawApplicationService.request(...)` 校验金额和用户。
3. 要求用户钱包 active。
4. 创建提现订单。
5. 写 WITHDRAW 总账，把用户余额转出到系统/冻结类账户。
6. 返回提现订单结果。

当前提现是请求即写账模型，不包含真实银行出款回调。

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

奖励 requestId 由上游业务语义生成，例如 growth 的 task reward grant id。钱包只保证同 requestId 不重复记账。

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

## 关键代码

- `wallet.controller.WalletController`
- `wallet.controller.AdminWalletController`
- `wallet.application.WalletApplicationService`
- `wallet.application.WalletAccountApplicationService`
- `wallet.application.WalletLedgerApplicationService`
- `wallet.application.WalletRechargeApplicationService`
- `wallet.application.WalletWithdrawApplicationService`
- `wallet.application.WalletTransferApplicationService`
- `wallet.application.WalletMarketApplicationService`
- `wallet.application.WalletRewardApplicationService`
- `wallet.application.WalletAdminOpsApplicationService`
- `wallet.domain.service.*`
- `wallet.infrastructure.api.*`
