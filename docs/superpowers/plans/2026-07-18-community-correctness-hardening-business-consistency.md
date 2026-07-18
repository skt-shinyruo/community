# Business Consistency Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复市场库存二次补偿、网盘配额重复释放、钱包特权冲正失败和并发审核重复副作用四个业务一致性问题。

**Architecture:** 每个 owner domain 保留独立不变量：市场由 `MarketOrder` 暴露补偿事实；网盘在一个 `REQUIRES_NEW` 中锁 space、CAS entry 并按 winner 字节释放 quota；钱包用不可由客户端控制的 `WalletPostingPolicy` 区分普通记账和特权纠正；内容审核用 `PENDING -> PROCESSING` CAS 和唯一 action 约束串行化决策。Controller/Listener 仍只调用同域 `*ApplicationService`，持久化条件更新通过 domain repository 实现。

**Tech Stack:** Java 21、Spring transactions、MyBatis、MySQL 8、H2、Flyway、JUnit 5、Mockito、Testcontainers、Maven。

---

## 市场托管补偿

### 写重复补偿 RED 测试

**Files:**

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrder.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationService.java`

- [ ] 在 `MarketOrderTest` 固定领域事实：`ESCROW_CANCEL_PENDING` 仍持有预留库存，`ESCROW_FAILED` 已完成库存补偿；其他非补偿状态返回 false。
- [ ] 在 saga test 让订单以 `ESCROW_FAILED` 进入 `completeEscrowNoop`，断言 repository 应用 `CANCELLED` transition，但 `MarketListingRepository.adjustStock` 和 `MarketInventoryRepository.releaseReservedByOrderIfNeeded` 调用次数均为 0。
- [ ] 对 `ESCROW_CANCEL_PENDING` 断言 cancel transition 成功后库存/预加载 inventory 各恢复一次。
- [ ] 在 processor test 注入“订单已 `recordEscrowFailed` 并恢复库存后，wallet action 完成状态写入抛异常”的故障；重放 recovery/processor 后断言最终 `CANCELLED`，stock 不超过下单前值，释放预留调用总数为 1。
- [ ] repository `apply` 返回 stale/no-op 时断言不补偿。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='MarketOrderTest,MarketOrderSagaApplicationServiceTest,MarketWalletActionProcessorApplicationServiceTest' test
  ```

  预期：当前 `completeEscrowNoop` 对 `ESCROW_FAILED` 再次调用恢复库存，至少调用次数断言失败。

### 用领域事实约束补偿

- [ ] 在 `MarketOrder` 增加明确命名的方法：

  ```java
  public boolean holdsReservedInventoryForEscrowCancellation() {
      return status() == MarketOrderStatus.ESCROW_CANCEL_PENDING;
  }
  ```

  不能从 ApplicationService 用 status 集合重复推导同一业务事实。
- [ ] `completeEscrowNoop` 在 transition 前保存 `restoreInventory` 领域事实；仅当 `apply == APPLIED && restoreInventory` 时调用 `restoreReservedInventoryAndStock`。
- [ ] 保持 `markEscrowTerminalFailed` 的首次补偿语义：`ESCROW_PENDING -> ESCROW_FAILED` 的 winner 恢复一次；`ESCROW_CANCEL_PENDING -> CANCELLED` 的 winner 恢复一次。
- [ ] 在 structured log/metric 记录 `orderId`、from status、transition affected、inventory compensation attempted；不记录地址快照。
- [ ] 运行 GREEN，预期三个测试类全部通过。
- [ ] 提交市场修复：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/market \
          backend/community-app/src/test/java/com/nowcoder/community/market
  git commit -m "fix(market): avoid duplicate escrow inventory compensation"
  ```

## 网盘永久删除与配额

### 写并发与 OSS 重试 RED 测试

**Files:**

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveTrashApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveTrashApplicationServiceSpringTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveEntryRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveTrashApplicationService.java`

- [ ] 在 Spring test 建立一个 space、一个 trashed folder、两个 file，总 `usedBytes` 精确等于 file size 总和。
- [ ] 用 `CountDownLatch` 同时启动两个 `deletePermanently(userId, rootId)`；等待两线程结束，断言 entry 全部 `DELETED`、space used bytes 只减一次且不为负、每个 object cleanup 最终至少尝试一次。
- [ ] 注入 OSS 第一次 delete 抛 `DRIVE_STORAGE_UNAVAILABLE`；第二次调用对已 `DELETED` root 重试 OSS，断言 used bytes 不再变化。
- [ ] 单元测试捕获事务调用顺序：`requiresNew` 内先 lock space，再重读 root/subtree，再条件转换和 quota save；OSS port 只在 `requiresNew` 返回后调用。
- [ ] winner 为空时断言 released bytes 为 0；部分子项已由另一个事务删除时，只汇总当前 CAS 成功的 file。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='DriveTrashApplicationServiceTest,DriveTrashApplicationServiceSpringTest' test
  ```

  预期：当前方法在新事务外读取并对所有 target 盲写/汇总，双线程可能重复释放配额，断言失败。

### 增加 `TRASHED -> DELETED` 条件仓储操作

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveEntryRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveEntryMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/drive_entry_mapper.xml`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveEntryRepositoryTest.java`

- [ ] 在 domain repository 增加：

  ```java
  boolean markDeletedIfTrashed(DriveEntry deletedEntry);
  ```

  入参必须是 `item.delete(now)` 产生的领域状态；返回 true 表示当前事务赢得状态转换。
- [ ] Mapper SQL 使用 space/id/status guard：

  ```sql
  update drive_entry
  set status = 'DELETED', updated_at = #{updatedAt},
      trashed_at = #{trashedAt}, delete_after = #{deleteAfter}, trash_root_id = #{trashRootId}
  where space_id = #{spaceId} and entry_id = #{entryId} and status = 'TRASHED'
  ```

- [ ] repository test 断言第一调用 true、第二调用 false；ACTIVE/DELETED row 返回 false；不得 fallback insert。
- [ ] 运行 repository test，预期通过后提交持久化 CAS：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveEntryRepository.java \
          backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence \
          backend/community-app/src/main/resources/mapper/drive_entry_mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence
  git commit -m "feat(drive): conditionally win permanent deletion"
  ```

### 把锁、重读、CAS 和 quota 放入一个事务

- [ ] `deletePermanently` 只在外层做参数规范化，然后调用：

  ```java
  PermanentDeletionWork work = transactionOperations.requiresNew(
      () -> preparePermanentDeletion(actorUserId, entryId)
  );
  deleteObjects(work.cleanupTargets(), actorUserId);
  ```

- [ ] `preparePermanentDeletion` 在 `REQUIRES_NEW` 内调用 `loadOrCreateSpace`、`spaceRepository.lockById`，锁后重新 `findById` 和 `listDescendantIds`；删除方法外的旧快照不能参与 quota 计算。
- [ ] 对 TRASHED subtree 逐项调用 `markDeletedIfTrashed`，只把返回 true 的领域条目加入 `winners`；`releasedBytes = winners.filter(file).sum(sizeBytes)`。
- [ ] 在同一事务读取已锁定 space 的最新状态并 `spaceRepository.save(latest.release(releasedBytes, now))`；winner 为空不写 quota。
- [ ] 对 root 已 DELETED，返回现有 DELETED file cleanup targets，不执行 CAS/quota；ACTIVE 保持原业务错误。
- [ ] `PermanentDeletionWork` 是 application-private record，只包含 cleanup 所需 object id/领域条目，不泄漏 mapper dataobject。
- [ ] OSS 清理失败抛现有 storage unavailable；数据库事务已提交，重试走 DELETED 分支。
- [ ] 再运行 drive 单元/并发测试，预期全部通过。
- [ ] 提交事务编排：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveTrashApplicationService.java \
          backend/community-app/src/test/java/com/nowcoder/community/drive/application
  git commit -m "fix(drive): release quota only for deletion winners"
  ```

## 钱包特权冲正

### 写领域与持久化 RED 测试

**Files:**

- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletPostingPolicy.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletAccount.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletAccountChange.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/model/WalletAccountTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/persistence/WalletAccountRepositoryApplyTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/persistence/WalletAccountMapperPersistenceTest.java`

- [ ] 固定策略枚举只有：

  ```java
  public enum WalletPostingPolicy {
      NORMAL,
      PRIVILEGED_CORRECTION
  }
  ```

- [ ] `WalletAccountTest` 断言 NORMAL：冻结扣款拒绝、余额不足拒绝、正常入账/扣款不变。
- [ ] 断言 PRIVILEGED_CORRECTION：冻结账户可扣款、可从 3 扣 5 得 -2、仍检查 long overflow；后续 NORMAL credit 3 得 1。
- [ ] `reconstitute` 允许数据库中的负余额；新开账户仍为 0。`WalletAccountChange` 只有 privileged policy 允许 previous/next balance 为负。
- [ ] persistence test 断言 NORMAL SQL 保留 `balance + delta >= 0`，privileged SQL 不带该条件但仍带 version guard；policy 不能来自 Controller DTO。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='WalletAccountTest,WalletAccountRepositoryApplyTest,WalletAccountMapperPersistenceTest' test
  ```

  预期：当前构造器/change/SQL 都禁止负余额，privileged 用例失败。

### 实现领域 policy 与双 SQL

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/WalletAccountRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisWalletAccountRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/mapper/WalletAccountMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/wallet_account_mapper.xml`

- [ ] 保留兼容方法 `post(long delta)` 并固定委托 `post(delta, NORMAL)`；新增：

  ```java
  public WalletAccountChange post(long delta, WalletPostingPolicy policy)
  ```

- [ ] NORMAL 执行冻结/最低余额检查；PRIVILEGED_CORRECTION 只绕过这两项，不绕过 account id、version、status 合法性和 overflow。
- [ ] `WalletAccountChange` 增加非空 `WalletPostingPolicy policy`；所有 freeze/unfreeze change 固定 NORMAL。
- [ ] Mapper 提供两个明确方法 `updateNormalBalanceWithVersion(...)` 和 `updatePrivilegedBalanceWithVersion(...)`；禁止一个客户端可控 boolean 拼 SQL。
- [ ] repository 只根据领域 change policy 选择 SQL；NORMAL affected=0 且余额不足返回 `INSUFFICIENT_FUNDS`，privileged affected=0 只可能是 not found/version conflict。
- [ ] 运行领域/持久化 GREEN，预期全部通过。
- [ ] 提交钱包领域与 persistence：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/wallet/domain \
          backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence \
          backend/community-app/src/main/resources/mapper/wallet_account_mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/wallet/domain \
          backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/persistence
  git commit -m "feat(wallet): model privileged correction postings"
  ```

### 限制特权入口并验证双分录

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletAccountApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletAdminOpsApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletAdminOpsApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRewardApplicationServiceTest.java`

- [ ] `WalletLedgerApplicationService` 的三个 public `post(...)` 固定 `NORMAL`；新增 package-private 明确方法：

  ```java
  WalletTxnResult postPrivilegedCorrection(
      String requestId, WalletTxnType txnType, List<WalletPosting> postings
  )
  ```

  两条路径共同调用 private `postInsideTransaction(command, policy)`，双分录校验和 replay 校验不能复制。
- [ ] `WalletAccountApplicationService.apply(account, delta)` 固定 NORMAL；新增 package-private policy overload 供同包 ledger 调用。
- [ ] `WalletAdminOpsApplicationService.reverseTxn` 删除 `ensureReversalCanBeApplied`，改调 `postPrivilegedCorrection`；仍先校验 actor、reason、reversible txn type 和 audit replay。
- [ ] `WalletRewardApplicationService` 的 issue/正 delta 走 NORMAL，`revoke`/负 delta 走 privileged；外部 `WalletRewardCommand` 不增加 policy 字段。
- [ ] Admin test 将“收款人已花掉资金应拒绝”改为：冲正成功、收款人余额为负、原 txn + reversal 分录总和为 0、同 request replay 不重复。
- [ ] Reward test 验证撤奖可产生负余额；后续普通奖励入账先偿债；普通消费/转账仍不能透支。
- [ ] 运行：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='WalletLedgerApplicationServiceTest,WalletAdminOpsApplicationServiceTest,WalletRewardApplicationServiceTest,WalletAccountTest,WalletAccountRepositoryApplyTest' test
  ```

  预期：全部通过且 ledger 每个 txn 的 debit/credit 仍平衡。
- [ ] 静态扫描特权入口：

  ```bash
  rg -n 'PRIVILEGED_CORRECTION|postPrivilegedCorrection' \
    backend/community-app/src/main/java/com/nowcoder/community/wallet
  ```

  预期：仅 domain policy、wallet account/ledger、admin reversal、reward negative path 和 persistence 出现；Controller/api command 不出现。
- [ ] 提交应用入口：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/wallet/application \
          backend/community-app/src/test/java/com/nowcoder/community/wallet/application
  git commit -m "fix(wallet): allow audited corrections to create debt"
  ```

## 并发审核与 Community V010

### 写 migration RED 测试

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V010__enforce_unique_moderation_action_report.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`

- [ ] 在 V009 schema 上插入两个不同非空 report id action 和两个 null report id action，迁移成功；断言索引 `uk_moderation_action_report(report_id)` 唯一，多个 null 仍允许。
- [ ] 独立数据库插入同一个非空 report id 的两条 action，断言 V010 失败并保留两条历史数据，不删除、不合并。
- [ ] 空库 migration count 从 `9` 更新为 `10`；重复 migrate 为 0；V001/manifest 不变。
- [ ] H2 fixture 新增 `uk_moderation_action_report(report_id)` unique index，同时保留多个 null 的语义。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest=CommunityMigrationTest test
  ```

  预期：V010/unique index 不存在，断言失败。

### 实现原子重复检测和唯一约束

- [ ] V010 使用一条 MySQL 8 atomic DDL 同时替换索引：

  ```sql
  alter table moderation_action
    drop index idx_moderation_action_report,
    add unique key uk_moderation_action_report(report_id);
  ```

  添加 unique key 本身就是库内最终重复检测；若存在重复非空 `report_id`，DDL 必须因 duplicate key 失败且整条 `ALTER TABLE` 不生效，不能依赖需要 `CREATE ROUTINE` 权限的临时存储过程。
- [ ] migration test 捕获 MySQL duplicate-key migration failure，并断言旧 `idx_moderation_action_report`、两条冲突数据和 actor index 仍在；无重复路径断言旧 report index 被 unique key 精确替换。
- [ ] migration 中不改 action 内容、不回填 report id、不触碰 null rows。
- [ ] 运行 migration GREEN，预期成功/重复失败两条路径都通过。
- [ ] 提交 V010：

  ```bash
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V010__enforce_unique_moderation_action_report.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql
  git commit -m "feat(migration): enforce one action per moderation report"
  ```

### 写审核 claim 与 replay RED 测试

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/ReportStatuses.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/ReportRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/ModerationActionRepository.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationConcurrencySpringTest.java`

- [ ] 定义 `ReportStatuses.PROCESSING = 3`，不改现有 PENDING/PROCESSED/REJECTED 数值。
- [ ] repository 契约增加：

  ```java
  boolean claimPending(UUID reportId);
  boolean transitionStatus(UUID reportId, int expectedStatus, int nextStatus);
  Optional<ModerationActionRecord> findByReportId(UUID reportId);
  ```

- [ ] unit test：claim winner 执行副作用、action、终态、通知；loser 不执行副作用。
- [ ] 相同规范化 decision replay 返回已有 action id；actor 不作为业务 decision fingerprint，action/reason/resolved duration 相同即视为相同决定。
- [ ] action/reason/duration 任一不同返回 `ContentErrorCode.MODERATION_DECISION_CONFLICT`，HTTP kind 为 `CONFLICT`；不覆盖已有 action。
- [ ] concurrency Spring test 用两个 transaction/thread 对同一 report 提交相同决策，断言一个 action、一组 owner-domain 副作用、目标/举报人通知各一次，两个调用返回同一个 action id。
- [ ] 再用不同决策并发，断言一个成功、一个 conflict，仍只有一个 action/副作用集合。
- [ ] 注入 owner-domain side effect 或 notice outbox insert 抛异常，断言 claim/action/report 终态全部回滚为 PENDING/无 action。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='ModerationApplicationServiceTest,ModerationConcurrencySpringTest' test
  ```

  预期：当前先读 PENDING 再写且无 claim/唯一 replay，重复 action 或调用次数断言失败。

### 实现 CAS claim 与决策重放

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/exception/ContentErrorCode.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisReportRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisModerationActionRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/ReportMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/ModerationActionMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/report-mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/moderationaction-mapper.xml`

- [ ] `ReportMapper.claimPending` 使用：

  ```sql
  update report set status = #{processing}
  where id = #{id} and status = #{pending}
  ```

- [ ] `ModerationApplicationService.takeAction` 先规范化 decision，再调用 claim。claim=false 时查询 `findByReportId`：存在且 fingerprint 相同返回 id；存在但不同抛 conflict；不存在表示 winner 尚未形成可见 action或历史异常，返回稳定 conflict/temporarily unavailable，不能执行副作用。
- [ ] winner 事务顺序：resolve target -> owner-domain side effect -> `writeAction` -> report `PROCESSED/REJECTED` -> notice outbox；所有调用保持在当前 `@Transactional` 内。
- [ ] `ReportRepository` 增加带 expected status 的 `transitionStatus(reportId, PROCESSING, PROCESSED)` 并校验 affected=1；`ReportMapper` 新增对应 guarded SQL。现有 `ReportContentRepository` 仍共享该 mapper，不能直接破坏其 `updateStatus` 编译契约；若保留旧 mapper 方法，审核路径禁止调用无前置状态更新。
- [ ] `ModerationActionMapper.selectActionsByReportId` 保持返回列表，repository 断言结果最多一行：空列表返回 empty，单行返回该 action，多行立即 fail closed 并记录数据异常；禁止使用 `LIMIT 1` 或任选首行掩盖历史重复数据。
- [ ] `ModerationDecision` 增加稳定 fingerprint 比较 helper 或 domain service method；ApplicationService 不用 JSON/string 拼接比较。
- [ ] notice publisher 必须是同事务 outbox adapter；如发现直接网络发送，先改成 application-owned port 的 JDBC outbox 实现，不能把网络副作用放进事务。
- [ ] 运行 GREEN，预期 unit/concurrency/rollback 测试全部通过。
- [ ] 提交审核实现：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/content \
          backend/community-app/src/main/resources/mapper/report-mapper.xml \
          backend/community-app/src/main/resources/mapper/moderationaction-mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/content/application
  git commit -m "fix(content): serialize moderation decisions by report claim"
  ```

## 综合验证

- [ ] 运行四域聚焦测试：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='Market*Test,DriveTrashApplicationService*Test,Wallet*Test,Moderation*Test' test
  ```

  预期：`BUILD SUCCESS`；并发测试无偶发超时。

- [ ] 运行 migration：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am test
  ```

  预期：本计划合入点 Community 10 个 migration；全部计划合并后总控验收 11 个。

- [ ] 运行架构守卫：

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：ApplicationService 不依赖 mapper/dataobject；审核跨 user owner-domain 只走 `UserModerationActionApi`；Controller/Listener 无跨域编排。

- [ ] 运行资金/配额不变量查询的 test fixture 断言：wallet 每 txn debit=credit；drive `used_bytes >= 0` 且等于未删除 file 汇总；market listing stock 不超过补偿前基线；report 非空 id action count <= 1。
- [ ] 发布 V010 前执行总控文档中的只读重复 report 预检；发现任何行立即中止，不让 migration 自动清理审计历史。
- [ ] 观察 `market compensation affected`、`drive deletion winner/released_bytes`、wallet privileged correction audit、`moderation claim/replay/conflict` 信号；不得记录地址、举报 detail 或钱包完整备注。
- [ ] `git diff --check`，预期无 whitespace 错误。
