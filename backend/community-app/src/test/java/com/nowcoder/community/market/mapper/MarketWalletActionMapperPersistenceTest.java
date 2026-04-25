package com.nowcoder.community.market.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.entity.MarketWalletAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketWalletActionMapperPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketWalletActionMapper mapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_wallet_action");
    }

    @Test
    void insertAndSelectByRequestIdShouldRoundTripAction() {
        MarketWalletAction action = pendingAction("market-order:" + uuid(201) + ":escrow", "ESCROW");

        mapper.insert(action);

        MarketWalletAction loaded = mapper.selectByRequestId(action.getRequestId());
        assertThat(loaded.getActionId()).isEqualTo(action.getActionId());
        assertThat(loaded.getOrderId()).isEqualTo(action.getOrderId());
        assertThat(loaded.getStatus()).isEqualTo("PENDING");
        assertThat(loaded.getResultType()).isNull();
        assertThat(loaded.getWalletTxnId()).isNull();
    }

    @Test
    void claimProcessingShouldMovePendingActionToProcessingWithLease() {
        MarketWalletAction action = pendingAction("market-order:" + uuid(202) + ":release", "RELEASE");
        mapper.insert(action);

        Date leaseUntil = Date.from(Instant.parse("2026-04-25T10:00:00Z"));
        int updated = mapper.claimProcessing(action.getActionId(), leaseUntil);

        assertThat(updated).isEqualTo(1);
        MarketWalletAction loaded = mapper.selectById(action.getActionId());
        assertThat(loaded.getStatus()).isEqualTo("PROCESSING");
        assertThat(loaded.getProcessingLeaseUntil()).isEqualTo(leaseUntil);
    }

    private MarketWalletAction pendingAction(String requestId, String actionType) {
        UUID orderId = uuid(Math.abs(requestId.hashCode() % 10_000) + 1);
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(uuid(Math.abs((requestId + ":action").hashCode() % 10_000) + 1));
        action.setOrderId(orderId);
        action.setActionType(actionType);
        action.setRequestId(requestId);
        action.setWalletBizId("market-order:" + orderId);
        action.setActorUserId(uuid(9));
        action.setCounterpartyUserId(uuid(7));
        action.setAmount(12_900L);
        action.setStatus("PENDING");
        return action;
    }
}
