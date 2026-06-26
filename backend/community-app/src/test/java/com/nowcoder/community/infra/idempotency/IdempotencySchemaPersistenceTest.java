package com.nowcoder.community.infra.idempotency;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class IdempotencySchemaPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from http_idempotency");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_txn");
    }

    @Test
    void publicBusinessTablesShouldScopeRequestIdByActor() {
        insertRecharge(uuid(801), "same-public-key", uuid(1), 100);
        assertThatThrownBy(() -> insertRecharge(uuid(802), "same-public-key", uuid(1), 100))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertRecharge(uuid(803), "same-public-key", uuid(2), 100))
                .doesNotThrowAnyException();

        insertWithdraw(uuid(811), "same-withdraw-key", uuid(1), 100);
        assertThatThrownBy(() -> insertWithdraw(uuid(812), "same-withdraw-key", uuid(1), 100))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertWithdraw(uuid(813), "same-withdraw-key", uuid(2), 100))
                .doesNotThrowAnyException();

        insertTransfer(uuid(821), "same-transfer-key", uuid(1), uuid(2), 100);
        assertThatThrownBy(() -> insertTransfer(uuid(822), "same-transfer-key", uuid(1), uuid(3), 100))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertTransfer(uuid(823), "same-transfer-key", uuid(2), uuid(1), 100))
                .doesNotThrowAnyException();

        insertMarketOrder(uuid(831), "same-market-key", uuid(9), uuid(7), uuid(701));
        assertThatThrownBy(() -> insertMarketOrder(uuid(832), "same-market-key", uuid(9), uuid(7), uuid(702)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertMarketOrder(uuid(833), "same-market-key", uuid(10), uuid(7), uuid(703)))
                .doesNotThrowAnyException();
    }

    @Test
    void walletTxnRequestIdShouldRemainGloballyUnique() {
        insertWalletTxn(uuid(841), "wallet-command-key", "biz-1");

        assertThatThrownBy(() -> insertWalletTxn(uuid(842), "wallet-command-key", "biz-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void documented128CharacterRequestIdsShouldFitPersistenceSchema() {
        String requestId128 = "r".repeat(128);
        String bizId128 = "b".repeat(128);

        assertThatCode(() -> insertRecharge(uuid(861), requestId128, uuid(1), 100))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertWithdraw(uuid(862), requestId128, uuid(1), 100))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertTransfer(uuid(863), requestId128, uuid(1), uuid(2), 100))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertMarketOrder(uuid(864), requestId128, uuid(9), uuid(7), uuid(701)))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertWalletTxn(uuid(865), requestId128, bizId128))
                .doesNotThrowAnyException();
    }

    @Test
    void httpIdempotencyShouldPersistRequestHash() {
        jdbcTemplate.update(
                """
                        insert into http_idempotency(
                          id, operation, user_id, idem_key, request_hash, status, response_json,
                          processing_expires_at, success_expires_at, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp, current_timestamp, current_timestamp)
                        """,
                bytes(uuid(851)),
                "wallet:recharge",
                bytes(uuid(1)),
                "idem-key",
                "sha256-value",
                "SUCCESS",
                "{}"
        );

        String requestHash = jdbcTemplate.queryForObject(
                "select request_hash from http_idempotency where operation = ? and idem_key = ?",
                String.class,
                "wallet:recharge",
                "idem-key"
        );

        assertThat(requestHash).isEqualTo("sha256-value");
    }

    private void insertRecharge(UUID orderId, String requestId, UUID userId, long amount) {
        jdbcTemplate.update(
                "insert into recharge_order(order_id, request_id, user_id, amount, status) values (?, ?, ?, ?, ?)",
                bytes(orderId),
                requestId,
                bytes(userId),
                amount,
                "PAID"
        );
    }

    private void insertWithdraw(UUID orderId, String requestId, UUID userId, long amount) {
        jdbcTemplate.update(
                "insert into withdraw_order(order_id, request_id, user_id, amount, status) values (?, ?, ?, ?, ?)",
                bytes(orderId),
                requestId,
                bytes(userId),
                amount,
                "SUCCEEDED"
        );
    }

    private void insertTransfer(UUID orderId, String requestId, UUID fromUserId, UUID toUserId, long amount) {
        jdbcTemplate.update(
                "insert into transfer_order(order_id, request_id, from_user_id, to_user_id, amount, status) values (?, ?, ?, ?, ?, ?)",
                bytes(orderId),
                requestId,
                bytes(fromUserId),
                bytes(toUserId),
                amount,
                "SUCCEEDED"
        );
    }

    private void insertMarketOrder(UUID orderId, String requestId, UUID buyerUserId, UUID sellerUserId, UUID listingId) {
        jdbcTemplate.update(
                """
                        insert into market_order(
                          order_id, request_id, listing_id, goods_type, seller_user_id, buyer_user_id,
                          quantity, unit_price_snapshot, total_amount, listing_title_snapshot, status
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                bytes(orderId),
                requestId,
                bytes(listingId),
                "VIRTUAL",
                bytes(sellerUserId),
                bytes(buyerUserId),
                1,
                100L,
                100L,
                "listing",
                "ESCROWED"
        );
    }

    private void insertWalletTxn(UUID txnId, String requestId, String bizId) {
        jdbcTemplate.update(
                "insert into wallet_txn(txn_id, request_id, txn_type, biz_type, biz_id, status, amount) values (?, ?, ?, ?, ?, ?, ?)",
                bytes(txnId),
                requestId,
                "ORDER_ESCROW",
                "market-order",
                bizId,
                "SUCCEEDED",
                100L
        );
    }

    private byte[] bytes(UUID uuid) {
        return BinaryUuidCodec.toBytes(uuid);
    }
}
