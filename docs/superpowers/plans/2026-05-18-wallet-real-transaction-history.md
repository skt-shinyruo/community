# Wallet Real Transaction History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace wallet page session-local "recent transactions" with persisted wallet ledger history loaded from a new authenticated backend endpoint.

**Architecture:** The backend reads current-user wallet entries from the existing double-entry ledger, joins transaction metadata, computes signed user-perspective amounts, and exposes the data through `WalletApplicationService` and `WalletController`. The frontend reloads both summary and transaction history from the backend and stops synthesizing local history entries after write actions.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML mappers, JUnit 5, AssertJ, MockMvc, Vue 3, Vitest, Vue Test Utils.

---

## File Structure

Backend files to create:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/command/ListWalletTransactionsCommand.java`: application command carrying authenticated user id and optional limit.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/result/WalletTransactionResult.java`: application result returned to controllers.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/WalletTransactionResponse.java`: HTTP DTO for `/api/wallet/transactions`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletLedgerItem.java`: domain read model for joined `wallet_entry` plus `wallet_txn` rows.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/dataobject/WalletLedgerItemDataObject.java`: MyBatis result object that converts to `WalletLedgerItem`.
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceTransactionHistoryTest.java`: application-level transaction history tests.

Backend files to modify:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletAccountApplicationService.java`: add read-only `findUserWallet`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletApplicationService.java`: inject ledger service and expose `recentTransactions`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`: convert repository ledger rows into signed `WalletTransactionResult` rows.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`: add `GET /api/wallet/transactions`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/WalletLedgerRepository.java`: add recent ledger item query.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisWalletLedgerRepository.java`: implement recent query.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/mapper/WalletEntryMapper.java`: add mapper method.
- `backend/community-app/src/main/resources/mapper/wallet_entry_mapper.xml`: add join query.
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/persistence/WalletEntryMapperPersistenceTest.java`: add mapper persistence coverage.
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`: add controller contract coverage.
- `docs/handbook/business-logic/wallet.md`: document the new endpoint and signed amount rules.

Frontend files to modify:

- `frontend/src/api/services/walletService.js`: add `getWalletTransactions`.
- `frontend/src/views/WalletView.vue`: load real transactions during reload and remove local history prepend behavior.
- `frontend/src/views/WalletView.test.js`: assert backend history loading and write-triggered reloads.
- `frontend/src/views/walletState.test.js`: cover backend transaction row shape.

---

### Task 1: Persistence Read Model

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletLedgerItem.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/dataobject/WalletLedgerItemDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/WalletLedgerRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisWalletLedgerRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/mapper/WalletEntryMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/wallet_entry_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/persistence/WalletEntryMapperPersistenceTest.java`

- [ ] **Step 1: Write the failing mapper persistence test**

Add the imports:

```java
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject;

import java.util.List;
```

Add this field:

```java
    @Autowired
    private WalletEntryMapper walletEntryMapper;
```

Replace `setUp()` with:

```java
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
    }
```

Add this test and helper methods:

```java
    @Test
    void selectRecentItemsByAccountIdShouldJoinTxnAndCounterpartUser() {
        UUID senderUserId = UUID.fromString("00000000-0000-7000-8000-000000000101");
        UUID receiverUserId = UUID.fromString("00000000-0000-7000-8000-000000000202");
        UUID senderAccountId = UUID.fromString("00000000-0000-7000-8000-000000000701");
        UUID receiverAccountId = UUID.fromString("00000000-0000-7000-8000-000000000702");
        UUID txnId = UUID.fromString("00000000-0000-7000-8000-000000000703");
        UUID senderEntryId = UUID.fromString("00000000-0000-7000-8000-000000000704");
        UUID receiverEntryId = UUID.fromString("00000000-0000-7000-8000-000000000705");

        insertUserAccount(senderAccountId, senderUserId, 700L);
        insertUserAccount(receiverAccountId, receiverUserId, 300L);
        insertTxn(txnId, "wallet:transfer:plan-test", "TRANSFER", 300L);
        insertEntry(senderEntryId, txnId, senderAccountId, "DEBIT", 300L, 700L, "2026-05-18 10:00:00");
        insertEntry(receiverEntryId, txnId, receiverAccountId, "CREDIT", 300L, 300L, "2026-05-18 10:00:00");

        List<WalletLedgerItemDataObject> rows = walletEntryMapper.selectRecentItemsByAccountId(senderAccountId, 12);

        assertThat(rows).hasSize(1);
        WalletLedgerItemDataObject row = rows.get(0);
        assertThat(row.getEntryId()).isEqualTo(senderEntryId);
        assertThat(row.getTxnId()).isEqualTo(txnId);
        assertThat(row.getAccountId()).isEqualTo(senderAccountId);
        assertThat(row.getDirection()).isEqualTo("DEBIT");
        assertThat(row.getEntryAmount()).isEqualTo(300L);
        assertThat(row.getBalanceAfter()).isEqualTo(700L);
        assertThat(row.getRequestId()).isEqualTo("wallet:transfer:plan-test");
        assertThat(row.getTxnType()).isEqualTo("TRANSFER");
        assertThat(row.getBizType()).isEqualTo("TRANSFER");
        assertThat(row.getBizId()).isEqualTo("wallet:transfer:plan-test");
        assertThat(row.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(row.getCounterpartUserId()).isEqualTo(receiverUserId);
    }

    @Test
    void selectRecentItemsByAccountIdShouldFilterByAccountAndApplyLimitNewestFirst() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000101");
        UUID otherUserId = UUID.fromString("00000000-0000-7000-8000-000000000202");
        UUID accountId = UUID.fromString("00000000-0000-7000-8000-000000000711");
        UUID otherAccountId = UUID.fromString("00000000-0000-7000-8000-000000000712");
        UUID oldTxnId = UUID.fromString("00000000-0000-7000-8000-000000000713");
        UUID newTxnId = UUID.fromString("00000000-0000-7000-8000-000000000714");
        UUID otherTxnId = UUID.fromString("00000000-0000-7000-8000-000000000715");

        insertUserAccount(accountId, userId, 300L);
        insertUserAccount(otherAccountId, otherUserId, 900L);
        insertTxn(oldTxnId, "wallet:reward:old", "REWARD_ISSUE", 100L);
        insertTxn(newTxnId, "wallet:reward:new", "REWARD_ISSUE", 200L);
        insertTxn(otherTxnId, "wallet:reward:other", "REWARD_ISSUE", 900L);
        insertEntry(UUID.fromString("00000000-0000-7000-8000-000000000716"), oldTxnId, accountId, "CREDIT", 100L, 100L, "2026-05-18 09:00:00");
        insertEntry(UUID.fromString("00000000-0000-7000-8000-000000000717"), newTxnId, accountId, "CREDIT", 200L, 300L, "2026-05-18 11:00:00");
        insertEntry(UUID.fromString("00000000-0000-7000-8000-000000000718"), otherTxnId, otherAccountId, "CREDIT", 900L, 900L, "2026-05-18 12:00:00");

        List<WalletLedgerItemDataObject> rows = walletEntryMapper.selectRecentItemsByAccountId(accountId, 1);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTxnId()).isEqualTo(newTxnId);
        assertThat(rows.get(0).getRequestId()).isEqualTo("wallet:reward:new");
    }

    private void insertUserAccount(UUID accountId, UUID userId, long balance) {
        jdbcTemplate.update(
                "insert into wallet_account(account_id, owner_type, owner_id, account_type, balance, status, version) values (?, 'USER', ?, 'USER_WALLET', ?, 'ACTIVE', 0)",
                BinaryUuidCodec.toBytes(accountId),
                BinaryUuidCodec.toBytes(userId),
                balance
        );
    }

    private void insertTxn(UUID txnId, String requestId, String txnType, long amount) {
        jdbcTemplate.update(
                "insert into wallet_txn(txn_id, request_id, txn_type, biz_type, biz_id, status, amount, remark, create_time, update_time) values (?, ?, ?, ?, ?, 'SUCCEEDED', ?, null, current_timestamp, current_timestamp)",
                BinaryUuidCodec.toBytes(txnId),
                requestId,
                txnType,
                txnType,
                requestId,
                amount
        );
    }

    private void insertEntry(UUID entryId, UUID txnId, UUID accountId, String direction, long amount, long balanceAfter, String createTime) {
        jdbcTemplate.update(
                "insert into wallet_entry(entry_id, txn_id, account_id, direction, amount, balance_after, create_time) values (?, ?, ?, ?, ?, ?, timestamp '" + createTime + "')",
                BinaryUuidCodec.toBytes(entryId),
                BinaryUuidCodec.toBytes(txnId),
                BinaryUuidCodec.toBytes(accountId),
                direction,
                amount,
                balanceAfter
        );
    }
```

- [ ] **Step 2: Run the mapper test to verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletEntryMapperPersistenceTest
```

Expected: compilation fails because `WalletLedgerItemDataObject` and `selectRecentItemsByAccountId` do not exist.

- [ ] **Step 3: Add the domain read model**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletLedgerItem.java`:

```java
package com.nowcoder.community.wallet.domain.model;

import java.util.Date;
import java.util.UUID;

public record WalletLedgerItem(
        UUID entryId,
        UUID txnId,
        UUID accountId,
        String direction,
        long entryAmount,
        long balanceAfter,
        Date entryCreateTime,
        String requestId,
        String txnType,
        String bizType,
        String bizId,
        String status,
        String remark,
        UUID counterpartUserId
) {
}
```

- [ ] **Step 4: Add the MyBatis data object**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/dataobject/WalletLedgerItemDataObject.java`:

```java
package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;

import java.util.Date;
import java.util.UUID;

public class WalletLedgerItemDataObject {

    private UUID entryId;
    private UUID txnId;
    private UUID accountId;
    private String direction;
    private long entryAmount;
    private long balanceAfter;
    private Date entryCreateTime;
    private String requestId;
    private String txnType;
    private String bizType;
    private String bizId;
    private String status;
    private String remark;
    private UUID counterpartUserId;

    public WalletLedgerItem toDomain() {
        return new WalletLedgerItem(
                entryId,
                txnId,
                accountId,
                direction,
                entryAmount,
                balanceAfter,
                entryCreateTime,
                requestId,
                txnType,
                bizType,
                bizId,
                status,
                remark,
                counterpartUserId
        );
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getTxnId() {
        return txnId;
    }

    public void setTxnId(UUID txnId) {
        this.txnId = txnId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public long getEntryAmount() {
        return entryAmount;
    }

    public void setEntryAmount(long entryAmount) {
        this.entryAmount = entryAmount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(long balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Date getEntryCreateTime() {
        return entryCreateTime;
    }

    public void setEntryCreateTime(Date entryCreateTime) {
        this.entryCreateTime = entryCreateTime;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public UUID getCounterpartUserId() {
        return counterpartUserId;
    }

    public void setCounterpartUserId(UUID counterpartUserId) {
        this.counterpartUserId = counterpartUserId;
    }
}
```

- [ ] **Step 5: Add mapper and repository methods**

Modify `WalletEntryMapper.java` imports and methods:

```java
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject;
```

```java
    List<WalletLedgerItemDataObject> selectRecentItemsByAccountId(@Param("accountId") UUID accountId, @Param("limit") int limit);
```

Modify `wallet_entry_mapper.xml` by adding:

```xml
    <select id="selectRecentItemsByAccountId" resultType="com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject">
        select e.entry_id,
               e.txn_id,
               e.account_id,
               e.direction,
               e.amount as entry_amount,
               e.balance_after,
               e.create_time as entry_create_time,
               t.request_id,
               t.txn_type,
               t.biz_type,
               t.biz_id,
               t.status,
               t.remark,
               (
                   select cp.owner_id
                   from wallet_entry ce
                   join wallet_account cp on cp.account_id = ce.account_id
                   where ce.txn_id = e.txn_id
                     and ce.account_id &lt;&gt; e.account_id
                     and cp.owner_type = 'USER'
                     and cp.account_type = 'USER_WALLET'
                   order by ce.entry_id asc
                   limit 1
               ) as counterpart_user_id
        from wallet_entry e
        join wallet_txn t on t.txn_id = e.txn_id
        where e.account_id = #{accountId, jdbcType=BINARY}
        order by e.create_time desc, e.entry_id desc
        limit #{limit}
    </select>
```

Modify `WalletLedgerRepository.java` imports and methods:

```java
import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;
```

```java
    List<WalletLedgerItem> findRecentItemsByAccountId(UUID accountId, int limit);
```

Modify `MyBatisWalletLedgerRepository.java` imports:

```java
import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;
```

Add:

```java
    @Override
    public List<WalletLedgerItem> findRecentItemsByAccountId(UUID accountId, int limit) {
        return entryMapper.selectRecentItemsByAccountId(accountId, limit).stream()
                .map(WalletLedgerItemDataObject::toDomain)
                .toList();
    }
```

Also add the missing import:

```java
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject;
```

- [ ] **Step 6: Run the mapper test to verify it passes**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletEntryMapperPersistenceTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit persistence read model**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/WalletLedgerItem.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/dataobject/WalletLedgerItemDataObject.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/WalletLedgerRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisWalletLedgerRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/mapper/WalletEntryMapper.java \
  backend/community-app/src/main/resources/mapper/wallet_entry_mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/persistence/WalletEntryMapperPersistenceTest.java
git commit -m "feat: add wallet ledger history persistence query"
```

---

### Task 2: Application History Query

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/command/ListWalletTransactionsCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/result/WalletTransactionResult.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceTransactionHistoryTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletAccountApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`

- [ ] **Step 1: Write the failing application test**

Create `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceTransactionHistoryTest.java`:

```java
package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.application.command.ListWalletTransactionsCommand;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletApplicationServiceTransactionHistoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletApplicationService walletApplicationService;

    @Autowired
    private WalletAccountApplicationService accountService;

    @Autowired
    private WalletLedgerApplicationService ledgerService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void recentTransactionsShouldReturnEmptyWithoutCreatingWalletAccount() {
        UUID userId = uuid(901);

        List<WalletTransactionResult> rows = walletApplicationService.recentTransactions(new ListWalletTransactionsCommand(userId, 12));

        assertThat(rows).isEmpty();
        assertThat(countRows("wallet_account")).isZero();
    }

    @Test
    void recentTransactionsShouldReturnSignedTransferRowsForSenderAndReceiver() {
        UUID senderUserId = uuid(101);
        UUID receiverUserId = uuid(202);
        UUID senderAccountId = accountService.ensureUserWallet(senderUserId);
        accountService.ensureUserWallet(receiverUserId);
        seedBalance(senderAccountId, 900L);

        ledgerService.post(
                "wallet:transfer:history",
                WalletTxnType.TRANSFER,
                List.of(
                        WalletPosting.debit(accountService.ensureUserWallet(senderUserId), 300),
                        WalletPosting.credit(accountService.ensureUserWallet(receiverUserId), 300)
                )
        );

        List<WalletTransactionResult> senderRows = walletApplicationService.recentTransactions(new ListWalletTransactionsCommand(senderUserId, 12));
        List<WalletTransactionResult> receiverRows = walletApplicationService.recentTransactions(new ListWalletTransactionsCommand(receiverUserId, 12));

        assertThat(senderRows).hasSize(1);
        assertThat(senderRows.get(0).txnRef()).isEqualTo("wallet:transfer:history");
        assertThat(senderRows.get(0).txnType()).isEqualTo("TRANSFER");
        assertThat(senderRows.get(0).amount()).isEqualTo(-300L);
        assertThat(senderRows.get(0).balanceAfter()).isEqualTo(600L);
        assertThat(senderRows.get(0).counterpartLabel()).isEqualTo("用户 " + receiverUserId);

        assertThat(receiverRows).hasSize(1);
        assertThat(receiverRows.get(0).amount()).isEqualTo(300L);
        assertThat(receiverRows.get(0).balanceAfter()).isEqualTo(300L);
        assertThat(receiverRows.get(0).counterpartLabel()).isEqualTo("用户 " + senderUserId);
    }

    @Test
    void recentTransactionsShouldReturnOnlyUserFacingWithdrawalEntry() {
        UUID userId = uuid(101);
        UUID userAccountId = accountService.ensureUserWallet(userId);
        UUID pendingAccountId = accountService.ensureSystemAccount("WITHDRAW_PENDING");
        UUID platformCashAccountId = accountService.ensureSystemAccount("PLATFORM_CASH");
        seedBalance(userAccountId, 500L);
        seedBalance(platformCashAccountId, 500L);

        ledgerService.post(
                "wallet:withdraw:history:request",
                WalletTxnType.WITHDRAW,
                "withdraw-order-1",
                List.of(
                        WalletPosting.debit(userAccountId, 200),
                        WalletPosting.credit(pendingAccountId, 200)
                )
        );
        ledgerService.post(
                "wallet:withdraw:history:settle",
                WalletTxnType.WITHDRAW,
                "withdraw-order-1",
                List.of(
                        WalletPosting.debit(pendingAccountId, 200),
                        WalletPosting.credit(platformCashAccountId, 200)
                )
        );

        List<WalletTransactionResult> rows = walletApplicationService.recentTransactions(new ListWalletTransactionsCommand(userId, 12));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).txnRef()).isEqualTo("wallet:withdraw:history:request");
        assertThat(rows.get(0).amount()).isEqualTo(-200L);
        assertThat(rows.get(0).counterpartLabel()).isEqualTo("提现申请");
    }

    @Test
    void recentTransactionsShouldClampLimitAndReturnNewestFirst() {
        UUID userId = uuid(101);
        UUID userAccountId = accountService.ensureUserWallet(userId);
        UUID rewardAccountId = accountService.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        for (int i = 1; i <= 55; i++) {
            ledgerService.post(
                    "wallet:reward:history:" + i,
                    WalletTxnType.REWARD_ISSUE,
                    List.of(
                            WalletPosting.debit(rewardAccountId, 1),
                            WalletPosting.credit(userAccountId, 1)
                    )
            );
        }

        List<WalletTransactionResult> rows = walletApplicationService.recentTransactions(new ListWalletTransactionsCommand(userId, 999));

        assertThat(rows).hasSize(50);
        assertThat(rows.get(0).txnRef()).isEqualTo("wallet:reward:history:55");
        assertThat(rows.get(49).txnRef()).isEqualTo("wallet:reward:history:6");
    }

    private void seedBalance(UUID accountId, long balance) {
        jdbcTemplate.update("update wallet_account set balance = ?, version = 0 where account_id = ?", balance, accountId);
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
```

- [ ] **Step 2: Run the application test to verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletApplicationServiceTransactionHistoryTest
```

Expected: compilation fails because `ListWalletTransactionsCommand`, `WalletTransactionResult`, and `WalletApplicationService.recentTransactions(...)` do not exist.

- [ ] **Step 3: Add command and result records**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/command/ListWalletTransactionsCommand.java`:

```java
package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record ListWalletTransactionsCommand(UUID userId, Integer limit) {
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/result/WalletTransactionResult.java`:

```java
package com.nowcoder.community.wallet.application.result;

import java.util.Date;
import java.util.UUID;

public record WalletTransactionResult(
        UUID txnId,
        String txnRef,
        String txnType,
        String bizType,
        String bizId,
        String status,
        long amount,
        long balanceAfter,
        String counterpartLabel,
        String remark,
        Date createTime
) {
}
```

- [ ] **Step 4: Add read-only account lookup**

Modify `WalletAccountApplicationService.java` by adding this method after `ensureSystemAccount(...)`:

```java
    public WalletAccount findUserWallet(UUID userId) {
        return walletAccountRepository.findByOwner(
                WalletAccountDomainService.OWNER_TYPE_USER,
                requireUserId(userId),
                WalletAccountDomainService.ACCOUNT_TYPE_USER_WALLET
        );
    }
```

- [ ] **Step 5: Add ledger application conversion**

Modify `WalletLedgerApplicationService.java` imports:

```java
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;
import com.nowcoder.community.wallet.domain.service.WalletAccountDomainService;
```

Add this method after `entriesOfTxn(...)`:

```java
    public List<WalletTransactionResult> recentTransactions(WalletAccount userAccount, int limit) {
        if (userAccount == null) {
            return List.of();
        }
        return walletLedgerRepository.findRecentItemsByAccountId(userAccount.getAccountId(), limit).stream()
                .map(item -> new WalletTransactionResult(
                        item.txnId(),
                        item.requestId(),
                        item.txnType(),
                        item.bizType(),
                        item.bizId(),
                        item.status(),
                        signedAmount(userAccount, item),
                        item.balanceAfter(),
                        counterpartLabelOf(item),
                        item.remark(),
                        item.entryCreateTime()
                ))
                .toList();
    }
```

Add these private helpers near the other private methods:

```java
    private long signedAmount(WalletAccount userAccount, WalletLedgerItem item) {
        WalletPosting posting = WalletAccountDomainService.DIRECTION_DEBIT.equals(item.direction())
                ? WalletPosting.debit(item.accountId(), item.entryAmount())
                : WalletPosting.credit(item.accountId(), item.entryAmount());
        return walletAccountService.deltaOf(userAccount, posting);
    }

    private String counterpartLabelOf(WalletLedgerItem item) {
        String txnType = item.txnType();
        if (WalletTxnType.RECHARGE.name().equals(txnType)) {
            return "平台入账";
        }
        if (WalletTxnType.WITHDRAW.name().equals(txnType)) {
            return "提现申请";
        }
        if (WalletTxnType.TRANSFER.name().equals(txnType)) {
            return item.counterpartUserId() == null ? "钱包转账" : "用户 " + item.counterpartUserId();
        }
        if (WalletTxnType.ORDER_ESCROW.name().equals(txnType)) {
            return "订单托管";
        }
        if (WalletTxnType.ORDER_RELEASE.name().equals(txnType)) {
            return "订单结算";
        }
        if (WalletTxnType.ORDER_REFUND.name().equals(txnType)) {
            return "订单退款";
        }
        if (WalletTxnType.REWARD_ISSUE.name().equals(txnType)) {
            return "活动奖励";
        }
        if (WalletTxnType.REVERSAL.name().equals(txnType)) {
            return "交易回滚";
        }
        String remark = item.remark();
        return remark == null || remark.isBlank() ? "系统记账" : remark.trim();
    }
```

- [ ] **Step 6: Add wallet application orchestration**

Modify `WalletApplicationService.java` imports:

```java
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.application.command.ListWalletTransactionsCommand;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.domain.model.WalletAccount;

import java.util.List;
```

Add this field:

```java
    private final WalletLedgerApplicationService ledgerApplicationService;
```

Change the constructor signature and body:

```java
    public WalletApplicationService(
            WalletAccountApplicationService accountService,
            WalletRechargeApplicationService rechargeApplicationService,
            WalletWithdrawApplicationService withdrawApplicationService,
            WalletTransferApplicationService transferApplicationService,
            WalletLedgerApplicationService ledgerApplicationService,
            IdempotencyGuard idempotencyGuard
    ) {
        this.accountService = accountService;
        this.rechargeApplicationService = rechargeApplicationService;
        this.withdrawApplicationService = withdrawApplicationService;
        this.transferApplicationService = transferApplicationService;
        this.ledgerApplicationService = ledgerApplicationService;
        this.idempotencyGuard = idempotencyGuard;
    }
```

Add this method after `summary(...)`:

```java
    public List<WalletTransactionResult> recentTransactions(ListWalletTransactionsCommand command) {
        if (command == null || command.userId() == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        int limit = normalizeLimit(command.limit());
        WalletAccount account = accountService.findUserWallet(command.userId());
        if (account == null) {
            return List.of();
        }
        return ledgerApplicationService.recentTransactions(account, limit);
    }
```

Add this private helper near the end of the class:

```java
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 12;
        }
        return Math.min(50, Math.max(1, limit));
    }
```

- [ ] **Step 7: Run application tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletApplicationServiceTransactionHistoryTest,WalletLedgerApplicationServiceTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit application history query**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/application/command/ListWalletTransactionsCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/result/WalletTransactionResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletAccountApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceTransactionHistoryTest.java
git commit -m "feat: expose wallet transaction history application query"
```

---

### Task 3: Wallet Transactions HTTP Endpoint

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/WalletTransactionResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Modify `WalletControllerTest.java` imports:

```java
import com.nowcoder.community.wallet.application.WalletLedgerApplicationService;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.domain.model.WalletAccount;

import java.util.Date;
import java.util.List;
```

Add this mock bean:

```java
    @MockBean
    private WalletLedgerApplicationService ledgerService;
```

Add this test:

```java
    @Test
    void walletTransactionsShouldReturnCurrentUserLedgerRows() throws Exception {
        UUID userId = uuid(1);
        UUID accountId = UUID.fromString("00000000-0000-7000-8000-000000000721");
        UUID txnId = UUID.fromString("00000000-0000-7000-8000-000000000722");
        WalletAccount account = userAccount(accountId, userId, 975L);
        when(accountService.findUserWallet(userId)).thenReturn(account);
        when(ledgerService.recentTransactions(eq(account), eq(12))).thenReturn(List.of(
                new WalletTransactionResult(
                        txnId,
                        "wallet:transfer:api-test",
                        "TRANSFER",
                        "TRANSFER",
                        "order-api-test",
                        "SUCCEEDED",
                        -25L,
                        975L,
                        "用户 " + uuid(2),
                        null,
                        new Date(1779100000000L)
                )
        ));

        mockMvc.perform(get("/api/wallet/transactions")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].txnId").value(txnId.toString()))
                .andExpect(jsonPath("$.data[0].txnRef").value("wallet:transfer:api-test"))
                .andExpect(jsonPath("$.data[0].txnType").value("TRANSFER"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data[0].amount").value(-25))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(975))
                .andExpect(jsonPath("$.data[0].counterpartLabel").value("用户 " + uuid(2)));
    }
```

Add this helper near `transferResponse(...)`:

```java
    private WalletAccount userAccount(UUID accountId, UUID userId, long balance) {
        WalletAccount account = new WalletAccount();
        account.setAccountId(accountId);
        account.setOwnerType("USER");
        account.setOwnerId(userId);
        account.setAccountType("USER_WALLET");
        account.setBalance(balance);
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        return account;
    }
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletControllerTest
```

Expected: compilation fails because `WalletTransactionResponse` and `/api/wallet/transactions` do not exist.

- [ ] **Step 3: Add the response DTO**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/WalletTransactionResponse.java`:

```java
package com.nowcoder.community.wallet.controller.dto;

import com.nowcoder.community.wallet.application.result.WalletTransactionResult;

import java.util.Date;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID txnId,
        String txnRef,
        String txnType,
        String bizType,
        String bizId,
        String status,
        long amount,
        long balanceAfter,
        String counterpartLabel,
        String remark,
        Date createTime
) {

    public static WalletTransactionResponse from(WalletTransactionResult result) {
        return new WalletTransactionResponse(
                result.txnId(),
                result.txnRef(),
                result.txnType(),
                result.bizType(),
                result.bizId(),
                result.status(),
                result.amount(),
                result.balanceAfter(),
                result.counterpartLabel(),
                result.remark(),
                result.createTime()
        );
    }
}
```

- [ ] **Step 4: Add the controller endpoint**

Modify `WalletController.java` imports:

```java
import com.nowcoder.community.wallet.application.command.ListWalletTransactionsCommand;
import com.nowcoder.community.wallet.controller.dto.WalletTransactionResponse;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
```

Add this method after `summary(...)`:

```java
    @GetMapping("/transactions")
    public Result<List<WalletTransactionResponse>> transactions(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(walletApplicationService.recentTransactions(new ListWalletTransactionsCommand(userId, limit))
                .stream()
                .map(WalletTransactionResponse::from)
                .toList());
    }
```

- [ ] **Step 5: Run controller and arch tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletControllerTest,*ArchTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit HTTP endpoint**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/WalletTransactionResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java
git commit -m "feat: add wallet transaction history endpoint"
```

---

### Task 4: Frontend Wallet History Loading

**Files:**
- Modify: `frontend/src/api/services/walletService.js`
- Modify: `frontend/src/views/WalletView.vue`
- Modify: `frontend/src/views/WalletView.test.js`
- Modify: `frontend/src/views/walletState.test.js`

- [ ] **Step 1: Write failing frontend tests**

Modify the hoisted mock block in `WalletView.test.js`:

```js
const {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletSummary,
  getWalletTransactions
} = vi.hoisted(() => ({
  createRecharge: vi.fn(),
  createTransfer: vi.fn(),
  createWithdrawal: vi.fn(),
  getWalletSummary: vi.fn(),
  getWalletTransactions: vi.fn()
}))
```

Modify the service mock:

```js
vi.mock('../api/services/walletService', () => ({
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletSummary,
  getWalletTransactions
}))
```

Modify `beforeEach()`:

```js
  beforeEach(() => {
    vi.clearAllMocks()
    getWalletSummary.mockResolvedValue({ data: { balance: 1000, status: 'ACTIVE' }, traceId: 'trace-wallet-summary' })
    getWalletTransactions.mockResolvedValue({ data: [], traceId: 'trace-wallet-transactions' })
    createRecharge.mockResolvedValue({ data: {}, traceId: 'trace-recharge' })
    createTransfer.mockResolvedValue({ data: { status: 'SUCCEEDED' }, traceId: 'trace-transfer' })
    createWithdrawal.mockResolvedValue({ data: {}, traceId: 'trace-withdrawal' })
  })
```

Add these tests:

```js
  it('loads persisted wallet transactions during initial reload', async () => {
    getWalletTransactions.mockResolvedValue({
      data: [
        {
          txnRef: 'wallet:transfer:history',
          txnType: 'TRANSFER',
          amount: -25,
          counterpartLabel: '用户 11111111-1111-7111-8111-111111111111',
          status: 'SUCCEEDED'
        }
      ],
      traceId: 'trace-wallet-transactions'
    })

    const wrapper = mountWalletView()
    await flushPromises()

    expect(getWalletSummary).toHaveBeenCalledTimes(1)
    expect(getWalletTransactions).toHaveBeenCalledWith(12)
    expect(wrapper.text()).toContain('转账转出')
    expect(wrapper.text()).toContain('-25 积分')
    expect(wrapper.text()).toContain('用户 11111111-1111-7111-8111-111111111111')
  })

  it('reloads persisted transactions after transfer instead of rendering local synthetic history', async () => {
    getWalletTransactions
      .mockResolvedValueOnce({ data: [], traceId: 'trace-wallet-transactions-1' })
      .mockResolvedValueOnce({
        data: [
          {
            txnRef: 'wallet:transfer:server',
            txnType: 'TRANSFER',
            amount: -25,
            counterpartLabel: '用户 server-confirmed',
            status: 'SUCCEEDED'
          }
        ],
        traceId: 'trace-wallet-transactions-2'
      })

    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await wrapper.findAll('button').find((button) => button.text() === '发起转账').trigger('click')
    await flushPromises()

    expect(createTransfer).toHaveBeenCalledTimes(1)
    expect(getWalletTransactions).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('用户 server-confirmed')
    expect(wrapper.text()).not.toContain('用户 11111111-1111-7111-8111-111111111111')
  })
```

Modify `walletState.test.js` by adding:

```js
  it('uses backend transaction references and signed amounts', () => {
    const state = buildWalletState({
      summary: { balance: 975, status: 'ACTIVE' },
      txns: [
        {
          txnId: '0198f4b6-9ad4-7a22-8df4-3c680e0d0d01',
          txnRef: 'wallet:transfer:history',
          txnType: 'TRANSFER',
          amount: -25,
          balanceAfter: 975,
          counterpartLabel: '用户 202',
          status: 'SUCCEEDED'
        }
      ]
    })

    expect(state.feed[0].key).toBe('wallet:transfer:history')
    expect(state.feed[0].label).toBe('转账转出')
    expect(state.feed[0].amountText).toBe('-25 积分')
    expect(state.feed[0].meta).toBe('用户 202')
  })
```

- [ ] **Step 2: Run frontend tests to verify they fail**

Run:

```bash
cd frontend
npm test -- src/views/WalletView.test.js src/views/walletState.test.js
```

Expected: `WalletView.test.js` fails because `getWalletTransactions` is not exported or not called.

- [ ] **Step 3: Add wallet transactions API function**

Modify `frontend/src/api/services/walletService.js` after `getWalletSummary()`:

```js
export async function getWalletTransactions(limit = 12) {
  const resp = await http.get('/api/wallet/transactions', { params: { limit } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询钱包流水')
  return { data: Array.isArray(data) ? data : [], traceId }
}
```

- [ ] **Step 4: Modify `WalletView.vue` imports and helpers**

Change the wallet service import:

```js
import {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletSummary,
  getWalletTransactions
} from '../api/services/walletService'
```

Replace `prependTxn(entry)` with:

```js
function normalizeTxns(data) {
  return Array.isArray(data) ? data.map((item) => ({ ...item })) : []
}
```

Replace `reload()` with:

```js
async function reload() {
  loading.value = true
  error.value = ''
  try {
    const [summaryResp, txnsResp] = await Promise.all([
      getWalletSummary(),
      getWalletTransactions(12)
    ])
    summary.value = normalizeSummary(summaryResp.data)
    txns.value = normalizeTxns(txnsResp.data)
    ready.value = true
  } catch (e) {
    error.value = e?.message || '加载钱包失败'
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 5: Remove local history prepends from write handlers**

In `submitRecharge()`, remove this block:

```js
    prependTxn({
      txnType: 'RECHARGE',
      amount,
      counterpartLabel: '平台入账',
      requestId: data?.requestId || '',
      status: data?.status || 'SUCCEEDED'
    })
```

Change:

```js
    const { data } = await createRecharge({ amount })
```

to:

```js
    await createRecharge({ amount })
```

In `submitWithdrawal()`, remove this block:

```js
    prependTxn({
      txnType: 'WITHDRAW',
      amount: -amount,
      counterpartLabel: '提现申请',
      requestId: data?.requestId || '',
      status: data?.status || 'PENDING'
    })
```

Change:

```js
    const { data } = await createWithdrawal({ amount })
```

to:

```js
    await createWithdrawal({ amount })
```

In `submitTransfer()`, remove this block:

```js
    prependTxn({
      txnType: 'TRANSFER',
      amount: -amount,
      counterpartLabel: `用户 ${toUserId}`,
      requestId: data?.requestId || '',
      status: data?.status || 'SUCCEEDED'
    })
```

Change:

```js
    const { data } = await createTransfer({ toUserId, amount })
```

to:

```js
    await createTransfer({ toUserId, amount })
```

- [ ] **Step 6: Run frontend tests**

Run:

```bash
cd frontend
npm test -- src/views/WalletView.test.js src/views/walletState.test.js
```

Expected: `PASS`.

- [ ] **Step 7: Commit frontend history loading**

```bash
git add frontend/src/api/services/walletService.js \
  frontend/src/views/WalletView.vue \
  frontend/src/views/WalletView.test.js \
  frontend/src/views/walletState.test.js
git commit -m "feat: load wallet transaction history from backend"
```

---

### Task 5: Documentation and Full Verification

**Files:**
- Modify: `docs/handbook/business-logic/wallet.md`

- [ ] **Step 1: Update wallet handbook entry list**

In `docs/handbook/business-logic/wallet.md`, under `HTTP：`, add:

```markdown
- `GET /api/wallet/transactions`
```

- [ ] **Step 2: Add wallet history read-path documentation**

After the current ledger data-flow section, add:

```markdown
## 最近流水

HTTP `GET /api/wallet/transactions` 返回当前登录用户钱包账户的最近流水。

读取路径：

1. `WalletController` 只提取当前登录用户和 `limit`。
2. `WalletApplicationService.recentTransactions(...)` 归一化 `limit`，默认 `12`，范围 `1..50`。
3. `WalletAccountApplicationService.findUserWallet(...)` 只读查询用户钱包账户；没有账户时返回空列表，不创建账户。
4. `WalletLedgerApplicationService.recentTransactions(...)` 从用户钱包账户对应的 `wallet_entry` 读取分录，并关联 `wallet_txn`。
5. 返回金额按当前用户账户视角计算：`USER_WALLET` 的 normal direction 是 `CREDIT`，所以 `CREDIT` 为正，`DEBIT` 为负。

钱包查询接口不得使用 `ensureUserWallet(...)` 或 `loadUserWallet(...)` 作为读路径入口，避免 GET 请求产生账户创建副作用。
```

- [ ] **Step 3: Run backend wallet and architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*Wallet*Test,*ArchTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run frontend wallet tests**

Run:

```bash
cd frontend
npm test -- src/views/WalletView.test.js src/views/walletState.test.js
```

Expected: `PASS`.

- [ ] **Step 5: Check changed files**

Run:

```bash
git status --short
```

Expected: only files from this wallet transaction history work are modified or staged. Existing unrelated drive/comment/outbox worktree changes may still appear; do not add or revert them.

- [ ] **Step 6: Commit docs and verification update**

```bash
git add docs/handbook/business-logic/wallet.md
git commit -m "docs: document wallet transaction history"
```

---

## Final Verification Checklist

- [ ] `GET /api/wallet/transactions` exists and requires authentication.
- [ ] The endpoint returns only the authenticated user's wallet-account entries.
- [ ] Users with no wallet account receive an empty list and no wallet account is created.
- [ ] Signed amounts use `USER_WALLET` normal direction semantics: `CREDIT` positive, `DEBIT` negative.
- [ ] Transfer sender and receiver see opposite signed amounts and the other user as counterpart metadata.
- [ ] Withdrawal settlement system-to-system entries are not shown in a user's history.
- [ ] Frontend initial wallet load calls both summary and transaction history APIs.
- [ ] Frontend write actions reload backend history and no longer create session-local fake transaction history.
- [ ] Handbook documents endpoint, read path, signed amount semantics, and no side-effect read rule.
