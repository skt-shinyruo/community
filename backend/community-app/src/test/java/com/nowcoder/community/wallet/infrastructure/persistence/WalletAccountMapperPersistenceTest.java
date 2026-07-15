package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.infrastructure.persistence.mapper.*;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletAccountMapperPersistenceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-7000-8000-000000000641");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletAccountMapper walletAccountMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void walletAccountPrimaryKeyShouldRoundTripAsApplicationAssignedUuid() throws Exception {
        UUID userId = uuid(101);
        int inserted = jdbcTemplate.update(
                "insert into wallet_account(account_id, owner_type, owner_id, account_type, balance, status, version, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
                BinaryUuidCodec.toBytes(ACCOUNT_ID),
                "USER",
                BinaryUuidCodec.toBytes(userId),
                "USER_WALLET",
                800L,
                "ACTIVE",
                0L
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedAccountId = jdbcTemplate.queryForObject(
                "select account_id from wallet_account where owner_type = ? and owner_id = ? and account_type = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "USER",
                BinaryUuidCodec.toBytes(userId),
                "USER_WALLET"
        );
        assertThat(storedAccountId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedAccountId)).isEqualTo(ACCOUNT_ID);

        WalletAccount account = walletAccountMapper.selectByOwner("USER", userId, "USER_WALLET").toDomain();
        assertThat(account).isNotNull();

        Method getter = WalletAccount.class.getMethod("getAccountId");
        Object accountId = getter.invoke(account);
        assertThat(accountId).isInstanceOf(UUID.class);
        assertThat(accountId).isEqualTo(ACCOUNT_ID);
    }
}
