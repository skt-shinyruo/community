package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.entity.WalletAdminAction;
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
class WalletAdminActionMapperPersistenceTest {

    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000611");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-7000-8000-000000000612");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletAdminActionMapper walletAdminActionMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_admin_action");
    }

    @Test
    void walletAdminActionPrimaryKeyShouldRoundTripAsApplicationAssignedUuid() throws Exception {
        int inserted = jdbcTemplate.update(
                "insert into wallet_admin_action(action_id, request_id, actor_user_id, target_account_id, action_type, amount, remark, create_time) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(ACTION_ID),
                "wallet-admin:req-1",
                1L,
                BinaryUuidCodec.toBytes(TARGET_ID),
                "FREEZE_WALLET",
                0L,
                "manual review"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select action_id from wallet_admin_action where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "wallet-admin:req-1"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(ACTION_ID);

        WalletAdminAction action = walletAdminActionMapper.selectByRequestId("wallet-admin:req-1");
        assertThat(action).isNotNull();

        Method getter = WalletAdminAction.class.getMethod("getActionId");
        Object actionId = getter.invoke(action);
        assertThat(actionId).isInstanceOf(UUID.class);
        assertThat(actionId).isEqualTo(ACTION_ID);

        byte[] storedTargetId = jdbcTemplate.queryForObject(
                "select target_account_id from wallet_admin_action where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "wallet-admin:req-1"
        );
        assertThat(storedTargetId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedTargetId)).isEqualTo(TARGET_ID);

        Method targetGetter = WalletAdminAction.class.getMethod("getTargetAccountId");
        Object targetId = targetGetter.invoke(action);
        assertThat(targetId).isInstanceOf(UUID.class);
        assertThat(targetId).isEqualTo(TARGET_ID);
    }
}
