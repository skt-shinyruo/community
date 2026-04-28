package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.infrastructure.persistence.mapper.*;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.WalletEntry;
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
class WalletEntryMapperPersistenceTest {

    private static final UUID ENTRY_ID = UUID.fromString("00000000-0000-7000-8000-000000000661");
    private static final UUID TXN_ID = UUID.fromString("00000000-0000-7000-8000-000000000662");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-7000-8000-000000000663");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
    }

    @Test
    void walletEntryPrimaryKeyShouldRoundTripAsApplicationAssignedUuid() throws Exception {
        int inserted = jdbcTemplate.update(
                "insert into wallet_entry(entry_id, txn_id, account_id, direction, amount, balance_after, create_time) values (?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(ENTRY_ID),
                BinaryUuidCodec.toBytes(TXN_ID),
                BinaryUuidCodec.toBytes(ACCOUNT_ID),
                "CREDIT",
                900L,
                1900L
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedEntryId = jdbcTemplate.queryForObject(
                "select entry_id from wallet_entry where txn_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(TXN_ID)
        );
        assertThat(storedEntryId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedEntryId)).isEqualTo(ENTRY_ID);

        Method entryIdGetter = WalletEntry.class.getMethod("getEntryId");
        assertThat(entryIdGetter.getReturnType()).isEqualTo(UUID.class);

        Method accountIdGetter = WalletEntry.class.getMethod("getAccountId");
        assertThat(accountIdGetter.getReturnType()).isEqualTo(UUID.class);
    }
}
