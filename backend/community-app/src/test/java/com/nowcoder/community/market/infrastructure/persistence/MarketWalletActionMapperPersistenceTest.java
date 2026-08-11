package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionClaim;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.model.MarketWalletActionLeaseRecovery;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
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

        Date claimedAt = Date.from(Instant.parse("2026-04-25T09:59:00Z"));
        Date leaseUntil = Date.from(Instant.parse("2026-04-25T10:00:00Z"));
        MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), uuid(903));
        int updated = mapper.claimProcessing(claim(lease, "PENDING", 0, claimedAt, leaseUntil, 8));

        assertThat(updated).isEqualTo(1);
        MarketWalletAction loaded = mapper.selectClaimed(lease);
        assertThat(loaded.getStatus()).isEqualTo("PROCESSING");
        assertThat(loaded.getProcessingLeaseUntil()).isEqualTo(leaseUntil);
        assertThat(loaded.getLeaseToken()).isEqualTo(lease.token());
    }

    @Test
    void staleLeaseShouldNotApplyAnyProcessorOwnedTransition() {
        MarketWalletActionLease leaseA = claimFreshAction(204, uuid(904));
        Date recoveredAt = Date.from(Instant.parse("2026-04-25T10:01:01Z"));

        MarketWalletAction processing = mapper.selectById(leaseA.actionId());
        assertThat(mapper.recoverExpiredProcessing(recovery(
                processing,
                recoveredAt,
                Date.from(Instant.parse("2026-04-25T10:01:06Z")),
                8
        ))).isEqualTo(1);
        MarketWalletAction recovered = mapper.selectById(leaseA.actionId());
        assertThat(recovered.getStatus()).isEqualTo("RETRYING");
        assertThat(recovered.getProcessingLeaseUntil()).isNull();
        assertThat(recovered.getLeaseToken()).isNull();

        MarketWalletActionLease leaseB = new MarketWalletActionLease(leaseA.actionId(), uuid(905));
        Date retryDueAt = Date.from(Instant.parse("2026-04-25T10:01:06Z"));
        Date leaseBUntil = Date.from(Instant.parse("2026-04-25T10:02:00Z"));
        assertThat(mapper.claimProcessing(claim(leaseB, "RETRYING", 1, retryDueAt, leaseBUntil, 8))).isEqualTo(1);

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
        assertThat(loaded.getLastError()).isEqualTo("processing lease expired");
        assertThat(loaded.getRetryCount()).isEqualTo(1);
    }

    @Test
    void claimShouldFenceStaleCandidatesBackoffAndRetryBudget() {
        MarketWalletAction action = pendingAction("market-action-claim-cas", "RELEASE");
        mapper.insert(MarketWalletActionDataObject.from(action));
        MarketWalletAction staleCandidate = mapper.selectById(action.getActionId());
        MarketWalletActionLease firstLease = new MarketWalletActionLease(action.getActionId(), uuid(922));
        Date firstClaimedAt = Date.from(Instant.parse("2026-04-25T10:00:00Z"));

        assertThat(mapper.claimProcessing(claim(
                firstLease,
                "PENDING",
                staleCandidate.getRetryCount(),
                firstClaimedAt,
                Date.from(Instant.parse("2026-04-25T10:01:00Z")),
                2
        ))).isEqualTo(1);
        Date nextRetryAt = Date.from(Instant.parse("2026-04-25T10:05:00Z"));
        assertThat(mapper.markRetrying(firstLease, nextRetryAt, "retry later")).isEqualTo(1);

        MarketWalletActionLease staleLease = new MarketWalletActionLease(action.getActionId(), uuid(923));
        assertThat(mapper.claimProcessing(claim(
                staleLease,
                "PENDING",
                staleCandidate.getRetryCount(),
                Date.from(Instant.parse("2026-04-25T10:10:00Z")),
                Date.from(Instant.parse("2026-04-25T10:11:00Z")),
                2
        ))).isZero();

        MarketWalletAction refreshed = mapper.selectById(action.getActionId());
        MarketWalletActionLease earlyLease = new MarketWalletActionLease(action.getActionId(), uuid(924));
        assertThat(mapper.claimProcessing(claim(
                earlyLease,
                "RETRYING",
                refreshed.getRetryCount(),
                Date.from(Instant.parse("2026-04-25T10:04:59Z")),
                Date.from(Instant.parse("2026-04-25T10:05:59Z")),
                2
        ))).isZero();

        MarketWalletActionLease exhaustedLease = new MarketWalletActionLease(action.getActionId(), uuid(925));
        assertThat(mapper.claimProcessing(claim(
                exhaustedLease,
                "RETRYING",
                refreshed.getRetryCount(),
                nextRetryAt,
                Date.from(Instant.parse("2026-04-25T10:06:00Z")),
                1
        ))).isZero();

        MarketWalletActionLease currentLease = new MarketWalletActionLease(action.getActionId(), uuid(926));
        assertThat(mapper.claimProcessing(claim(
                currentLease,
                "RETRYING",
                refreshed.getRetryCount(),
                nextRetryAt,
                Date.from(Instant.parse("2026-04-25T10:06:00Z")),
                2
        ))).isEqualTo(1);
        assertThat(mapper.selectClaimed(staleLease)).isNull();
        assertThat(mapper.selectClaimed(currentLease).getRetryCount()).isEqualTo(1);
    }

    @Test
    void dueScanShouldExcludeFutureAndExhaustedActions() {
        Date asOf = Date.from(Instant.parse("2026-04-25T10:00:00Z"));
        MarketWalletAction due = pendingAction("market-due:boundary", "RELEASE");
        due.setRetryCount(7);
        due.setNextRetryAt(asOf);
        MarketWalletAction future = pendingAction("market-due:future", "RELEASE");
        future.setNextRetryAt(Date.from(Instant.parse("2026-04-25T10:00:01Z")));
        MarketWalletAction exhausted = pendingAction("market-due:exhausted", "RELEASE");
        exhausted.setRetryCount(8);
        mapper.insert(MarketWalletActionDataObject.from(due));
        mapper.insert(MarketWalletActionDataObject.from(future));
        mapper.insert(MarketWalletActionDataObject.from(exhausted));

        assertThat(mapper.selectDue(asOf, 8, 10))
                .extracting(MarketWalletAction::getActionId)
                .containsExactly(due.getActionId());
    }

    @Test
    void expiredLeaseRecoveryShouldBeLimitedAndCasFenced() {
        Date firstExpiry = Date.from(Instant.parse("2026-04-25T09:58:00Z"));
        Date secondExpiry = Date.from(Instant.parse("2026-04-25T09:59:00Z"));
        MarketWalletAction first = processingAction("market-expired:first", uuid(927), firstExpiry, 0);
        MarketWalletAction second = processingAction("market-expired:second", uuid(928), secondExpiry, 0);
        mapper.insert(MarketWalletActionDataObject.from(first));
        mapper.insert(MarketWalletActionDataObject.from(second));
        Date asOf = Date.from(Instant.parse("2026-04-25T10:00:00Z"));

        assertThat(mapper.selectExpiredProcessing(asOf, 1))
                .extracting(MarketWalletAction::getActionId)
                .containsExactly(first.getActionId());

        MarketWalletActionLeaseRecovery firstRecovery = recovery(
                first,
                asOf,
                Date.from(Instant.parse("2026-04-25T10:00:05Z")),
                8
        );
        assertThat(mapper.recoverExpiredProcessing(firstRecovery)).isEqualTo(1);
        assertThat(mapper.recoverExpiredProcessing(firstRecovery)).isZero();

        MarketWalletAction recovered = mapper.selectById(first.getActionId());
        assertThat(recovered.getStatus()).isEqualTo("RETRYING");
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getNextRetryAt()).isEqualTo(Date.from(Instant.parse("2026-04-25T10:00:05Z")));
        assertThat(recovered.getLeaseToken()).isNull();
        assertThat(mapper.selectById(second.getActionId()).getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void expiredLeaseRecoveryShouldStopAtRetryBudget() {
        Date expiredAt = Date.from(Instant.parse("2026-04-25T09:59:00Z"));
        MarketWalletAction action = processingAction("market-expired:exhausted", uuid(929), expiredAt, 7);
        mapper.insert(MarketWalletActionDataObject.from(action));
        Date asOf = Date.from(Instant.parse("2026-04-25T10:00:00Z"));

        assertThat(mapper.recoverExpiredProcessing(recovery(
                action,
                asOf,
                Date.from(Instant.parse("2026-04-25T10:05:00Z")),
                8
        ))).isEqualTo(1);

        MarketWalletAction recovered = mapper.selectById(action.getActionId());
        assertThat(recovered.getStatus()).isEqualTo("DEAD");
        assertThat(recovered.getRetryCount()).isEqualTo(7);
        assertThat(recovered.getNextRetryAt()).isNull();
        assertThat(recovered.getLastError()).isEqualTo("processing lease expired");
        assertThat(recovered.getLeaseToken()).isNull();
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
                0,
                nextRetryAt,
                8,
                "wrong fact"
        )).isZero();
        assertThat(mapper.rescheduleFailed(
                failedLease.actionId(),
                "17004",
                0,
                nextRetryAt,
                8,
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
                0,
                nextRetryAt,
                8,
                "must not retry"
        )).isZero();
        assertThat(mapper.selectById(walletTxnLease.actionId()).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void failedRescheduleShouldRespectRetryCasAndBudget() {
        MarketWalletAction action = pendingAction("market-repair:exhausted", "REFUND");
        action.setStatus("FAILED");
        action.setFailureCode("17004");
        action.setRetryCount(7);
        mapper.insert(MarketWalletActionDataObject.from(action));
        Date nextRetryAt = Date.from(Instant.parse("2026-04-25T10:05:00Z"));

        assertThat(mapper.rescheduleFailed(
                action.getActionId(),
                "17004",
                6,
                nextRetryAt,
                8,
                "stale repair"
        )).isZero();
        assertThat(mapper.rescheduleFailed(
                action.getActionId(),
                "17004",
                7,
                nextRetryAt,
                8,
                "retry budget exhausted"
        )).isEqualTo(1);

        MarketWalletAction repaired = mapper.selectById(action.getActionId());
        assertThat(repaired.getStatus()).isEqualTo("DEAD");
        assertThat(repaired.getRetryCount()).isEqualTo(7);
        assertThat(repaired.getNextRetryAt()).isNull();
        assertThat(repaired.getLastError()).isEqualTo("retry budget exhausted");
    }

    @Test
    void walletTransactionShouldBePersistedOnlyByCurrentUnexpiredLease() {
        MarketWalletActionLease lease = claimFreshAction(214, uuid(922));
        UUID walletTxnId = uuid(923);

        assertThat(mapper.recordWalletTxn(
                new MarketWalletActionLease(lease.actionId(), uuid(924)),
                walletTxnId,
                Date.from(Instant.parse("2026-04-25T10:00:30Z"))
        )).isZero();
        assertThat(mapper.recordWalletTxn(
                lease,
                walletTxnId,
                Date.from(Instant.parse("2026-04-25T10:01:01Z"))
        )).isZero();
        assertThat(mapper.recordWalletTxn(
                lease,
                walletTxnId,
                Date.from(Instant.parse("2026-04-25T10:00:30Z"))
        )).isEqualTo(1);

        assertThat(mapper.selectById(lease.actionId()).getWalletTxnId()).isEqualTo(walletTxnId);
    }

    @Test
    void cancellationShouldOnlyNoopNeverClaimedPendingEscrow() {
        MarketWalletAction pending = pendingAction("market-cancel:pending", "ESCROW");
        MarketWalletAction retrying = pendingAction("market-cancel:retrying", "ESCROW");
        retrying.setStatus("RETRYING");
        mapper.insert(MarketWalletActionDataObject.from(pending));
        mapper.insert(MarketWalletActionDataObject.from(retrying));

        assertThat(mapper.cancelPendingEscrow(pending.getRequestId(), "NOOP")).isEqualTo(1);
        assertThat(mapper.cancelPendingEscrow(retrying.getRequestId(), "NOOP")).isZero();
        assertThat(mapper.selectById(pending.getActionId()).getStatus()).isEqualTo("CANCELLED");
        assertThat(mapper.selectById(retrying.getActionId()).getStatus()).isEqualTo("RETRYING");
    }

    @Test
    void deferredWalletRecoveryShouldMovePoisonActionOutOfCurrentPrefix() {
        MarketWalletAction first = pendingAction("market-recovery:first", "RELEASE");
        first.setStatus("FAILED");
        first.setWalletTxnId(uuid(925));
        MarketWalletAction second = pendingAction("market-recovery:second", "RELEASE");
        second.setStatus("FAILED");
        second.setWalletTxnId(uuid(926));
        mapper.insert(MarketWalletActionDataObject.from(first));
        mapper.insert(MarketWalletActionDataObject.from(second));
        MarketWalletAction selected = mapper.selectUnfinishedWithWalletTxn(1).get(0);

        assertThat(mapper.deferWalletTxnRecovery(
                selected.getActionId(),
                selected.getStatus(),
                selected.getWalletTxnId(),
                Date.from(Instant.parse("2099-01-01T00:00:00Z")),
                "deferred poison action"
        )).isEqualTo(1);

        assertThat(mapper.selectUnfinishedWithWalletTxn(1))
                .extracting(MarketWalletAction::getActionId)
                .containsExactly(selected.getActionId().equals(first.getActionId())
                        ? second.getActionId()
                        : first.getActionId());
    }

    private MarketWalletActionLease claimFreshAction(int seed, UUID token) {
        MarketWalletAction action = pendingAction("market-action-fencing:" + seed, "RELEASE");
        mapper.insert(MarketWalletActionDataObject.from(action));
        MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), token);
        Date claimedAt = Date.from(Instant.parse("2026-04-25T10:00:00Z"));
        Date leaseUntil = Date.from(Instant.parse("2026-04-25T10:01:00Z"));
        assertThat(mapper.claimProcessing(claim(lease, "PENDING", 0, claimedAt, leaseUntil, 8))).isEqualTo(1);
        return lease;
    }

    private MarketWalletActionClaim claim(
            MarketWalletActionLease lease,
            String expectedStatus,
            int expectedRetryCount,
            Date claimedAt,
            Date leaseUntil,
            int maxRetryAttempts
    ) {
        return new MarketWalletActionClaim(
                lease,
                expectedStatus,
                expectedRetryCount,
                claimedAt,
                leaseUntil,
                maxRetryAttempts
        );
    }

    private MarketWalletActionLeaseRecovery recovery(
            MarketWalletAction action,
            Date asOf,
            Date nextRetryAt,
            int maxRetryAttempts
    ) {
        return new MarketWalletActionLeaseRecovery(
                action.getActionId(),
                action.getLeaseToken(),
                action.getProcessingLeaseUntil(),
                action.getRetryCount(),
                asOf,
                nextRetryAt,
                maxRetryAttempts,
                "processing lease expired"
        );
    }

    private MarketWalletAction processingAction(
            String requestId,
            UUID leaseToken,
            Date leaseUntil,
            int retryCount
    ) {
        MarketWalletAction action = pendingAction(requestId, "RELEASE");
        action.setStatus("PROCESSING");
        action.setLeaseToken(leaseToken);
        action.setProcessingLeaseUntil(leaseUntil);
        action.setRetryCount(retryCount);
        return action;
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
