package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.infrastructure.persistence.mapper.*;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletTxnMapperPersistenceTest {

    private static final UUID TXN_ID = UUID.fromString("00000000-0000-7000-8000-000000000651");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletTxnMapper walletTxnMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_txn");
    }

    @Test
    void walletTxnPrimaryKeyShouldRoundTripAsApplicationAssignedUuid() throws Exception {
        int inserted = jdbcTemplate.update(
                "insert into wallet_txn(txn_id, request_id, txn_type, biz_type, biz_id, status, amount, remark, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
                BinaryUuidCodec.toBytes(TXN_ID),
                "wallet-txn:req-1",
                "REWARD_ISSUE",
                "REWARD_ISSUE",
                "wallet-txn:req-1",
                "SUCCEEDED",
                900L,
                "persisted by test"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedTxnId = jdbcTemplate.queryForObject(
                "select txn_id from wallet_txn where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "wallet-txn:req-1"
        );
        assertThat(storedTxnId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedTxnId)).isEqualTo(TXN_ID);

        WalletTxn txn = walletTxnMapper.selectByRequestId("wallet-txn:req-1");
        assertThat(txn).isNotNull();

        Method getter = WalletTxn.class.getMethod("getTxnId");
        Object txnId = getter.invoke(txn);
        assertThat(txnId).isInstanceOf(UUID.class);
        assertThat(txnId).isEqualTo(TXN_ID);
    }
}
