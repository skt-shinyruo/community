package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.application.WalletTestCreditQuotaPort.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class WalletTestCreditQuotaPersistenceTest {

    @Autowired
    private MyBatisWalletTestCreditQuotaAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_test_credit_quota");
    }

    @Test
    void reservationsShouldBeDurableAndRejectAmountsBeyondEachQuota() {
        UUID userId = uuid(101);

        assertThat(adapter.tryReserveGrant(userId, 600L, 1000L)).isTrue();
        assertThat(adapter.tryReserveGrant(userId, 400L, 1000L)).isTrue();
        assertThat(adapter.tryReserveGrant(userId, 1L, 1000L)).isFalse();
        assertThat(adapter.tryReserveDiscard(userId, 250L, 300L)).isTrue();
        assertThat(adapter.tryReserveDiscard(userId, 51L, 300L)).isFalse();

        assertThat(adapter.usage(userId)).isEqualTo(new Usage(1000L, 250L));
        assertThat(adapter.usage(uuid(202))).isEqualTo(Usage.empty());
    }

    @Test
    void discardShouldNeverExceedCreditsGrantedToTheSameUser() {
        UUID grantedUserId = uuid(101);
        UUID otherUserId = uuid(202);

        assertThat(adapter.tryReserveGrant(grantedUserId, 400L, 1000L)).isTrue();
        assertThat(adapter.tryReserveDiscard(grantedUserId, 400L, 1000L)).isTrue();
        assertThat(adapter.tryReserveDiscard(grantedUserId, 1L, 1000L)).isFalse();
        assertThat(adapter.tryReserveDiscard(otherUserId, 1L, 1000L)).isFalse();

        assertThat(adapter.usage(grantedUserId)).isEqualTo(new Usage(400L, 400L));
        assertThat(adapter.usage(otherUserId)).isEqualTo(Usage.empty());
    }
}
