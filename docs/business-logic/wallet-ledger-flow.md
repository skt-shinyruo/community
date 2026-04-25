# 钱包与总账链路实现说明

本文档说明当前仓库中 `wallet` 域的实际实现路径，聚焦以下问题：

- 钱包余额与状态从哪里读取
- 充值、提现、转账分别如何落库
- 双分录总账是如何保证借贷平衡的
- `requestId` 在钱包链路里的重放 / 幂等语义是什么
- 管理员冻结与冲正如何落地
- 奖励发放和市场托管如何复用钱包总账

相关文档：

- `docs/business-logic/market-order-dispute-flow.md`
- `docs/business-logic/content-post-comment-bookmark-subscription-flow.md`
- `docs/business-logic/report-moderation-flow.md`

---

## 1. 参与组件

- `WalletController`：用户钱包对外入口
- `AdminWalletController`：管理员冻结 / 冲正入口
- `WalletQueryService`：钱包摘要读模型
- `WalletAccountService`：账户创建、查询、加锁、余额 / 状态更新
- `WalletLedgerService`：统一总账记账入口
- `RechargeService` / `WithdrawService` / `TransferService`：三条用户资金链路
- `AdminWalletOpsService`：管理员冻结与冲正
- `WalletRewardService`：奖励 / 积分投影写入钱包
- `WalletMarketApplicationService`：市场托管、放款、退款写入钱包
- MySQL：
  - `wallet_account`
  - `wallet_txn`
  - `wallet_entry`
  - `recharge_order`
  - `withdraw_order`
  - `transfer_order`
  - `wallet_admin_action`

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/AdminWalletController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java`

---

## 2. 对外接口

- `GET /api/wallet/summary`
- `POST /api/wallet/recharges`
- `POST /api/wallet/withdrawals`
- `POST /api/wallet/transfers`
- `POST /api/wallet/admin/freeze`
- `POST /api/wallet/admin/reverse`

当前钱包对外是“写入 + 摘要”模型，还没有用户交易流水查询接口。

---

## 3. 钱包主模型

### 3.1 账户

`WalletAccountService` 区分两类 owner：

- `USER`
- `SYSTEM`

用户主钱包固定为：

- `accountType = USER_WALLET`

系统账户当前允许的类型包括：

- `PLATFORM_CASH`
- `PLATFORM_REWARD_EXPENSE`
- `WITHDRAW_PENDING`
- `ORDER_ESCROW`
- `RISK_FROZEN`
- `MIGRATION_HOLD`

账户状态：

- `ACTIVE`
- `FROZEN`

### 3.2 交易与分录

`WalletLedgerService.post(...)` 的主模型是：

- 一笔 `wallet_txn`
- 多条 `wallet_entry`

当前交易类型：

- `REWARD_ISSUE`
- `RECHARGE`
- `WITHDRAW`
- `TRANSFER`
- `ORDER_ESCROW`
- `ORDER_RELEASE`
- `ORDER_REFUND`
- `REVERSAL`

每个 `WalletPosting` 都必须是：

- `DEBIT`
- `CREDIT`

且金额必须为正数。

---

## 4. 钱包摘要读路径

`GET /api/wallet/summary` 的链路很薄：

1. `WalletController.summary(...)`
2. `WalletQueryService.summary(userId)`
3. `WalletAccountService.balanceOfUser(userId)`
4. `WalletAccountService.statusOfUser(userId)`

返回的是：

- `userId`
- 当前钱包余额
- 当前钱包状态

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletQueryService.java`

---

## 5. 总账记账：所有资金动作共用一条主线

### 5.1 `WalletLedgerService.post(...)`

无论是充值、提现、转账、奖励，还是市场托管 / 放款 / 退款，最终都通过 `WalletLedgerService.post(...)` 落账。

它的关键步骤是：

1. 校验 `requestId`、`txnType`、`postings`
2. 校验借贷平衡：`debitTotal == creditTotal`
3. 先按 `requestId` 查重：
   - 已存在则校验交易类型、业务 id、金额和账本分录语义一致，再返回已有 `txnId + status`
   - 不一致则抛 `REQUEST_REPLAY_CONFLICT`
4. 新建 `wallet_txn(status=PENDING)`
5. 对每条 posting：
   - `lock(accountId)`
   - 根据账户类型和 posting 方向计算余额增量
   - 做乐观锁更新
   - 写一条 `wallet_entry`
6. 全部分录完成后，把 `wallet_txn` 标记为 `SUCCEEDED`

这条链路的关键特征是：

- 总账是单一资金事实来源
- 请求重放按 `requestId` 去重，但不是简单返回旧交易
- 余额更新不是“直接 set 值”，而是“锁定账户后按 delta 更新”

钱包 `requestId` 重放不是简单返回旧交易。重放必须匹配交易类型、业务 id、金额和账本分录语义；
不匹配时返回 `REQUEST_REPLAY_CONFLICT`，避免错误复用幂等键。

### 5.2 账户的借贷方向不是统一的

`WalletAccountService.normalDirectionOf(...)` 定义了不同账户的自然方向：

- `PLATFORM_CASH`、`PLATFORM_REWARD_EXPENSE`、`MIGRATION_HOLD`：自然方向是 `DEBIT`
- `USER_WALLET`、`WITHDRAW_PENDING`、`ORDER_ESCROW`、`RISK_FROZEN`：自然方向是 `CREDIT`

这决定了：

- 同样一条 `DEBIT` posting，在不同账户上增减余额的符号可能不同

这是当前代码里最容易误读的地方之一。

---

## 6. 三条用户资金链路

### 6.1 充值

入口：

- `POST /api/wallet/recharges`

主链路：

1. `RechargeService.complete(requestId, userId, amount)`
2. 校验 `requestId` 和金额
3. 先按 `requestId` 查 `recharge_order`
4. 若无则创建 `status=CREATED`
5. 通过总账写入：
   - `PLATFORM_CASH -> USER_WALLET`
6. 成功后把订单状态改为 `PAID`

重放语义：

- 同一个 `requestId` 再次请求
  - 参数相同：复用已有结果
  - 参数不同：抛 `REQUEST_REPLAY_CONFLICT`

### 6.2 提现

入口：

- `POST /api/wallet/withdrawals`

主链路：

1. `WithdrawService.request(...)`
2. 校验 `requestId` 和金额
3. 要求用户钱包必须 `ACTIVE`
4. 如果平台 `PLATFORM_CASH` 余额不足，会拒绝新提现
5. 若无旧单则创建 `withdraw_order(status=REQUESTED)`
6. 第一段记账：
   - `USER_WALLET -> WITHDRAW_PENDING`
   - 订单状态：`REQUESTED -> PROCESSING`
7. 第二段记账：
   - `WITHDRAW_PENDING -> PLATFORM_CASH`
   - 订单状态：`PROCESSING -> SUCCEEDED`

这说明当前提现实现是一个两段式挂账模型，而不是一步到位直接扣余额。

### 6.3 转账

入口：

- `POST /api/wallet/transfers`

主链路：

1. `TransferService.create(...)`
2. 校验 `requestId`、用户 id、金额
3. 禁止给自己转账
4. 要求转出方钱包必须 `ACTIVE`
5. 通过总账写入：
   - `USER_WALLET:from -> USER_WALLET:to`
6. 之后落一条 `transfer_order(status=SUCCEEDED)`

关键特点：

- 转账本身没有多阶段状态机
- 主事实仍是总账，`transfer_order` 更像一张业务索引表

---

## 7. 管理员冻结与冲正

### 7.1 冻结钱包

入口：

- `POST /api/wallet/admin/freeze`

主链路：

1. `AdminWalletOpsService.freezeWallet(actorUserId, targetUserId, reason)`
2. 校验操作者、目标用户和 reason
3. 加载目标用户钱包
4. 把账户状态改成 `FROZEN`
5. 写一条 `wallet_admin_action(actionType=FREEZE_WALLET)`

冻结不会创建总账分录，它改变的是账户状态，不是余额。

### 7.2 冲正交易

入口：

- `POST /api/wallet/admin/reverse`

主链路：

1. 按 `txnRef=requestId` 读取原始 `wallet_txn`
2. 只允许冲正这些类型：
   - `TRANSFER`
   - `ORDER_RELEASE`
   - `REWARD_ISSUE`
3. 读取原交易的全部 `wallet_entry`
4. 生成反向 postings：
   - 原 `DEBIT` -> 反向 `CREDIT`
   - 原 `CREDIT` -> 反向 `DEBIT`
5. 若反向交易会让某个账户变负，则拒绝冲正
6. 以 `reversal:<txnRef>` 为 `requestId` 调 `ledgerService.post(...)`
7. 再写一条 `wallet_admin_action(actionType=REVERSE_TXN)`

关键点：

- 冲正不是直接改旧交易或回滚余额
- 它本质上是一笔新的 `REVERSAL` 交易

---

## 8. 奖励与市场如何接入钱包

### 8.1 奖励 / 积分投影

`WalletRewardService` 是成长 / 积分体系进入钱包的边界：

- 正向发放：
  - `PLATFORM_REWARD_EXPENSE -> USER_WALLET`
- 负向撤销：
  - `USER_WALLET -> PLATFORM_REWARD_EXPENSE`

它被 `PointsProjectionService` 等上游投影调用。

### 8.2 市场托管 / 放款 / 退款

`WalletMarketApplicationService` 是 `market` 域进入钱包的边界：

- 托管：
  - `USER_WALLET:buyer -> ORDER_ESCROW`
- 放款：
  - `ORDER_ESCROW -> USER_WALLET:seller`
- 退款：
  - `ORDER_ESCROW -> USER_WALLET:buyer`

这保证了：

- 市场订单不直接改余额
- 资金动作仍统一落到钱包总账

---

## 9. 失败语义与一致性

### 9.1 `requestId` 是钱包链路的稳定幂等键

当前钱包链路没有接入 `IdempotencyGuard`，而是直接把业务 `requestId` 作为总账与业务订单的幂等键。

统一语义是：

- `requestId` 首次出现：真正执行
- `requestId` 重放且参数一致：复用结果
- `requestId` 重放但参数不同：冲突

### 9.2 余额不足 / 账户冻结 / 乐观锁冲突

常见失败点：

- `ACCOUNT_BALANCE_INSUFFICIENT`
- `ACCOUNT_FROZEN`
- `ACCOUNT_UPDATE_CONFLICT`
- `TXN_NOT_BALANCED`
- `PLATFORM_CASH_INSUFFICIENT`

这些错误都属于主链路内的强约束，不会被吞掉。

### 9.3 当前没有“交易历史接口”

虽然 `wallet_txn` / `wallet_entry` 已经完整存在，但当前对外接口只暴露：

- 钱包摘要
- 三类写接口
- 管理员动作

也就是说，链路已经落地，但用户侧账单阅读能力还没对外开放。

---

## 10. 建议源码阅读顺序

1. `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
2. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletQueryService.java`
3. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletAccountService.java`
4. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java`
5. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/RechargeService.java`
6. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WithdrawService.java`
7. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/TransferService.java`
8. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/AdminWalletOpsService.java`
9. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletRewardService.java`
10. `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`

---

## 11. 一句话总结

当前钱包实现的核心思路是：

- 账户、交易、分录三层建模
- 所有资金动作统一走 `WalletLedgerService.post(...)`
- 业务侧用 `requestId` 保证重放安全
- 冻结改状态，冲正走反向交易
- 奖励和市场都只能通过钱包域提供的 action API 进入资金主事实
