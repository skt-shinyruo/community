package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        action.setLeaseToken(uuid(901));

        mapper.insert(MarketWalletActionDataObject.from(action));

        MarketWalletAction loaded = mapper.selectByRequestId(action.getRequestId());
        assertThat(loaded.getActionId()).isEqualTo(action.getActionId());
        assertThat(loaded.getOrderId()).isEqualTo(action.getOrderId());
        assertThat(loaded.getStatus()).isEqualTo("PENDING");
        assertThat(loaded.getResultType()).isNull();
        assertThat(loaded.getWalletTxnId()).isNull();
        assertThat(loaded.getLeaseToken()).isEqualTo(action.getLeaseToken());
    }

    @Test
    void leaseShouldRejectNullActionIdOrToken() {
        assertThatThrownBy(() -> new MarketWalletActionLease(null, uuid(902)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("actionId must not be null");
        assertThatThrownBy(() -> new MarketWalletActionLease(uuid(203), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("token must not be null");
    }

    @Test
    void claimProcessingShouldMovePendingActionToProcessingWithLease() {
        MarketWalletAction action = pendingAction("market-order:" + uuid(202) + ":release", "RELEASE");
        mapper.insert(MarketWalletActionDataObject.from(action));

        Date leaseUntil = Date.from(Instant.parse("2026-04-25T10:00:00Z"));
        MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), uuid(903));
        int updated = mapper.claimProcessing(lease, leaseUntil);

        assertThat(updated).isEqualTo(1);
        MarketWalletAction loaded = mapper.selectById(action.getActionId());
        assertThat(loaded.getStatus()).isEqualTo("PROCESSING");
        assertThat(loaded.getProcessingLeaseUntil()).isEqualTo(leaseUntil);
        assertThat(loaded.getLeaseToken()).isEqualTo(lease.token());
    }

    @Test
    void staleLeaseShouldNotApplyAnyProcessorOwnedTransition() {
        MarketWalletActionLease leaseA = claimFreshAction(204, uuid(904));
        Date recoveredAt = Date.from(Instant.parse("2026-04-25T10:01:01Z"));

        assertThat(mapper.recoverExpiredProcessing(recoveredAt)).isEqualTo(1);
        MarketWalletAction recovered = mapper.selectById(leaseA.actionId());
        assertThat(recovered.getStatus()).isEqualTo("RETRYING");
        assertThat(recovered.getProcessingLeaseUntil()).isNull();
        assertThat(recovered.getLeaseToken()).isNull();

        MarketWalletActionLease leaseB = new MarketWalletActionLease(leaseA.actionId(), uuid(905));
        Date leaseBUntil = Date.from(Instant.parse("2026-04-25T10:02:00Z"));
        assertThat(mapper.claimProcessing(leaseB, leaseBUntil)).isEqualTo(1);

        assertThat(mapper.markSucceeded(leaseA, uuid(906), "APPLIED")).isZero();
        assertThat(mapper.markCancelled(leaseA, "NOOP")).isZero();
        assertThat(mapper.markRetrying(
                leaseA,
                Date.from(Instant.parse("2026-04-25T10:03:00Z")),
                "stale retry"
        )).isZero();
        assertThat(mapper.markFailed(leaseA, "STALE", "stale failure")).isZero();
        assertThat(mapper.markRecoveryPending(
                leaseA,
                uuid(907),
                "SAGA_STATE_NOT_ADVANCED",
                "stale recovery"
        )).isZero();
        assertThat(mapper.markDead(leaseA, "stale dead")).isZero();

        MarketWalletAction loaded = mapper.selectById(leaseB.actionId());
        assertThat(loaded.getStatus()).isEqualTo("PROCESSING");
        assertThat(loaded.getProcessingLeaseUntil()).isEqualTo(leaseBUntil);
        assertThat(loaded.getLeaseToken()).isEqualTo(leaseB.token());
        assertThat(loaded.getWalletTxnId()).isNull();
        assertThat(loaded.getResultType()).isNull();
        assertThat(loaded.getFailureCode()).isNull();
        assertThat(loaded.getLastError()).isNull();
        assertThat(loaded.getRetryCount()).isEqualTo(1);
    }

    @Test
    void currentLeaseShouldMarkSucceededAndClearOwnership() {
        MarketWalletActionLease lease = claimFreshAction(205, uuid(908));
        UUID walletTxnId = uuid(909);

        assertThat(mapper.markSucceeded(lease, walletTxnId, "APPLIED")).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "SUCCEEDED");
        assertThat(loaded.getWalletTxnId()).isEqualTo(walletTxnId);
        assertThat(loaded.getResultType()).isEqualTo("APPLIED");
        assertThat(loaded.getFailureCode()).isNull();
        assertThat(loaded.getLastError()).isNull();
    }

    @Test
    void currentLeaseShouldMarkCancelledAndClearOwnership() {
        MarketWalletActionLease lease = claimFreshAction(206, uuid(910));

        assertThat(mapper.markCancelled(lease, "NOOP")).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "CANCELLED");
        assertThat(loaded.getResultType()).isEqualTo("NOOP");
    }

    @Test
    void currentLeaseShouldMarkRetryingAndClearOwnership() {
        MarketWalletActionLease lease = claimFreshAction(207, uuid(911));
        Date nextRetryAt = Date.from(Instant.parse("2026-04-25T10:05:00Z"));

        assertThat(mapper.markRetrying(lease, nextRetryAt, "retry later")).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "RETRYING");
        assertThat(loaded.getRetryCount()).isEqualTo(1);
        assertThat(loaded.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(loaded.getLastError()).isEqualTo("retry later");
    }

    @Test
    void currentLeaseShouldMarkFailedAndClearOwnership() {
        MarketWalletActionLease lease = claimFreshAction(208, uuid(912));

        assertThat(mapper.markFailed(lease, "17001", "terminal failure")).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "FAILED");
        assertThat(loaded.getFailureCode()).isEqualTo("17001");
        assertThat(loaded.getLastError()).isEqualTo("terminal failure");
    }

    @Test
    void currentLeaseShouldMarkRecoveryPendingAndClearOwnership() {
        MarketWalletActionLease lease = claimFreshAction(209, uuid(913));
        UUID walletTxnId = uuid(914);

        assertThat(mapper.markRecoveryPending(
                lease,
                walletTxnId,
                "SAGA_STATE_NOT_ADVANCED",
                "saga pending"
        )).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "FAILED");
        assertThat(loaded.getWalletTxnId()).isEqualTo(walletTxnId);
        assertThat(loaded.getFailureCode()).isEqualTo("SAGA_STATE_NOT_ADVANCED");
        assertThat(loaded.getLastError()).isEqualTo("saga pending");
    }

    @Test
    void currentLeaseShouldMarkDeadAndClearOwnership() {
        MarketWalletActionLease lease = claimFreshAction(210, uuid(915));

        assertThat(mapper.markDead(lease, "retry budget exhausted")).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "DEAD");
        assertThat(loaded.getLastError()).isEqualTo("retry budget exhausted");
    }

    @Test
    void recoveredSuccessShouldRequireExpectedStatusAndWalletTransaction() {
        MarketWalletActionLease lease = claimFreshAction(211, uuid(916));
        UUID walletTxnId = uuid(917);
        assertThat(mapper.markRecoveryPending(
                lease,
                walletTxnId,
                "SAGA_STATE_NOT_ADVANCED",
                "saga pending"
        )).isEqualTo(1);

        assertThat(mapper.markRecoveredSucceeded(
                lease.actionId(),
                "RETRYING",
                walletTxnId,
                "APPLIED"
        )).isZero();
        assertThat(mapper.markRecoveredSucceeded(
                lease.actionId(),
                "FAILED",
                uuid(918),
                "APPLIED"
        )).isZero();
        assertThat(mapper.markRecoveredSucceeded(
                lease.actionId(),
                "FAILED",
                walletTxnId,
                "APPLIED"
        )).isEqualTo(1);

        MarketWalletAction loaded = mapper.selectById(lease.actionId());
        assertTerminalOwnershipCleared(loaded, "SUCCEEDED");
        assertThat(loaded.getWalletTxnId()).isEqualTo(walletTxnId);
        assertThat(loaded.getResultType()).isEqualTo("APPLIED");
    }

    @Test
    void failedRescheduleShouldRequireExpectedFailureAndNoWalletTransaction() {
        MarketWalletActionLease failedLease = claimFreshAction(212, uuid(919));
        assertThat(mapper.markFailed(failedLease, "17004", "wallet conflict")).isEqualTo(1);
        Date nextRetryAt = Date.from(Instant.parse("2026-04-25T10:06:00Z"));

        assertThat(mapper.rescheduleFailed(
                failedLease.actionId(),
                "17001",
                nextRetryAt,
                "wrong fact"
        )).isZero();
        assertThat(mapper.rescheduleFailed(
                failedLease.actionId(),
                "17004",
                nextRetryAt,
                "retry wallet conflict"
        )).isEqualTo(1);

        MarketWalletAction rescheduled = mapper.selectById(failedLease.actionId());
        assertTerminalOwnershipCleared(rescheduled, "RETRYING");
        assertThat(rescheduled.getRetryCount()).isEqualTo(1);
        assertThat(rescheduled.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(rescheduled.getLastError()).isEqualTo("retry wallet conflict");

        MarketWalletActionLease walletTxnLease = claimFreshAction(213, uuid(920));
        assertThat(mapper.markRecoveryPending(
                walletTxnLease,
                uuid(921),
                "17004",
                "wallet already changed"
        )).isEqualTo(1);
        assertThat(mapper.rescheduleFailed(
                walletTxnLease.actionId(),
                "17004",
                nextRetryAt,
                "must not retry"
        )).isZero();
        assertThat(mapper.selectById(walletTxnLease.actionId()).getStatus()).isEqualTo("FAILED");
    }

    private MarketWalletActionLease claimFreshAction(int seed, UUID token) {
        MarketWalletAction action = pendingAction("market-action-fencing:" + seed, "RELEASE");
        mapper.insert(MarketWalletActionDataObject.from(action));
        MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), token);
        Date leaseUntil = Date.from(Instant.parse("2026-04-25T10:01:00Z"));
        assertThat(mapper.claimProcessing(lease, leaseUntil)).isEqualTo(1);
        return lease;
    }

    private void assertTerminalOwnershipCleared(MarketWalletAction action, String expectedStatus) {
        assertThat(action.getStatus()).isEqualTo(expectedStatus);
        assertThat(action.getProcessingLeaseUntil()).isNull();
        assertThat(action.getLeaseToken()).isNull();
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
