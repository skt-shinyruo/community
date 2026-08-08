package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
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

import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MarketWalletActionProcessorTransactionOperationsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderMapper orderMapper;

    @Autowired
    private MarketWalletActionProcessorTransactionOperations transactionOperations;

    @Autowired
    private MarketWalletActionRecoveryTransactionOperations recoveryTransactionOperations;

    @MockBean
    private MarketWalletActionRepository walletActionRepository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_order");
    }

    @Test
    void actionCasLossShouldRollbackOrderSagaTransition() {
        UUID orderId = uuid(951);
        UUID walletTxnId = uuid(952);
        MarketOrder pendingOrder = order(orderId)
                .listingId(uuid(953))
                .sellerUserId(uuid(954))
                .buyerUserId(uuid(955))
                .totalAmount(12_900L)
                .status("RELEASE_PENDING")
                .build();
        orderMapper.insert(MarketOrderDataObject.from(pendingOrder));
        MarketWalletAction action = releaseAction(orderId);
        MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), uuid(956));
        when(walletActionRepository.lockClaimed(eq(lease), any())).thenReturn(action);
        when(walletActionRepository.markSucceeded(lease, walletTxnId, "APPLIED")).thenReturn(0);

        assertThatThrownBy(() -> transactionOperations.completeWalletSuccess(
                action,
                lease,
                walletTxnId,
                Date.from(Instant.parse("2026-08-05T10:00:00Z"))
        )).isInstanceOf(MarketWalletActionProcessorTransactionOperations.LeaseLostException.class);

        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo("RELEASE_PENDING");
        assertThat(orderMapper.selectById(orderId).getReleaseTxnId()).isNull();
    }

    @Test
    void recoveryCasLossShouldRollbackOrderSagaTransition() {
        UUID orderId = uuid(958);
        UUID walletTxnId = uuid(959);
        MarketOrder pendingOrder = order(orderId)
                .listingId(uuid(960))
                .sellerUserId(uuid(961))
                .buyerUserId(uuid(962))
                .totalAmount(12_900L)
                .status("RELEASE_PENDING")
                .build();
        orderMapper.insert(MarketOrderDataObject.from(pendingOrder));
        MarketWalletAction action = releaseAction(orderId);
        action.setStatus("FAILED");
        action.setWalletTxnId(walletTxnId);
        when(walletActionRepository.findById(action.getActionId())).thenReturn(action);
        when(walletActionRepository.lockById(action.getActionId())).thenReturn(action);
        when(walletActionRepository.markRecoveredSucceeded(
                action.getActionId(),
                "FAILED",
                walletTxnId,
                "APPLIED"
        )).thenReturn(0);

        assertThatThrownBy(() -> recoveryTransactionOperations.reconcileWalletTxnAction(action.getActionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed after saga advance");

        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo("RELEASE_PENDING");
        assertThat(orderMapper.selectById(orderId).getReleaseTxnId()).isNull();
    }

    private MarketWalletAction releaseAction(UUID orderId) {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(uuid(957));
        action.setOrderId(orderId);
        action.setActionType("RELEASE");
        action.setRequestId("market-order:" + orderId + ":release");
        action.setWalletBizId("market-order:" + orderId);
        action.setActorUserId(uuid(954));
        action.setCounterpartyUserId(uuid(955));
        action.setAmount(12_900L);
        action.setStatus("PROCESSING");
        return action;
    }
}
