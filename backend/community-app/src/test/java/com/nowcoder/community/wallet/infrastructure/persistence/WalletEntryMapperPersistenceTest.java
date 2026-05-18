package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.infrastructure.persistence.mapper.*;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletEntryMapperPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletEntryMapper walletEntryMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
    }

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
        insertTxn(txnId, "wallet:transfer:plan-test", "TRANSFER", 300L, "transfer remark");
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
        assertThat(row.getRemark()).isEqualTo("transfer remark");
        assertThat(row.getEntryCreateTime()).isNotNull();
        assertThat(row.getEntryCreateTime().getTime()).isEqualTo(Timestamp.valueOf("2026-05-18 10:00:00").getTime());
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
        insertTxn(oldTxnId, "wallet:reward:old", "REWARD_ISSUE", 100L, null);
        insertTxn(newTxnId, "wallet:reward:new", "REWARD_ISSUE", 200L, null);
        insertTxn(otherTxnId, "wallet:reward:other", "REWARD_ISSUE", 900L, null);
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

    private void insertTxn(UUID txnId, String requestId, String txnType, long amount, String remark) {
        jdbcTemplate.update(
                "insert into wallet_txn(txn_id, request_id, txn_type, biz_type, biz_id, status, amount, remark, create_time, update_time) values (?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, current_timestamp, current_timestamp)",
                BinaryUuidCodec.toBytes(txnId),
                requestId,
                txnType,
                txnType,
                requestId,
                amount,
                remark
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
}
