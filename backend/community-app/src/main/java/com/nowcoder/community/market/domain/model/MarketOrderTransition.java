package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MarketOrderTransition {

    public enum AutoConfirmPolicy {
        KEEP,
        SET,
        CLEAR
    }

    private final UUID orderId;
    private final Set<MarketOrderStatus> expectedStatuses;
    private final MarketOrderStatus nextStatus;
    private final UUID escrowTxnId;
    private final UUID releaseTxnId;
    private final UUID refundTxnId;
    private final AutoConfirmPolicy autoConfirmPolicy;
    private final Date autoConfirmAt;

    private MarketOrderTransition(
            UUID orderId,
            Set<MarketOrderStatus> expectedStatuses,
            MarketOrderStatus nextStatus,
            UUID escrowTxnId,
            UUID releaseTxnId,
            UUID refundTxnId,
            AutoConfirmPolicy autoConfirmPolicy,
            Date autoConfirmAt
    ) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(expectedStatuses, "expectedStatuses must not be null");
        if (expectedStatuses.isEmpty()) {
            throw new IllegalArgumentException("expectedStatuses must not be empty");
        }
        this.expectedStatuses = Set.copyOf(expectedStatuses);
        this.nextStatus = Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        this.escrowTxnId = escrowTxnId;
        this.releaseTxnId = releaseTxnId;
        this.refundTxnId = refundTxnId;
        this.autoConfirmPolicy = Objects.requireNonNull(autoConfirmPolicy, "autoConfirmPolicy must not be null");
        if (autoConfirmPolicy == AutoConfirmPolicy.SET && autoConfirmAt == null) {
            throw new IllegalArgumentException("autoConfirmAt must not be null when policy is SET");
        }
        if (autoConfirmPolicy != AutoConfirmPolicy.SET && autoConfirmAt != null) {
            throw new IllegalArgumentException("autoConfirmAt must be null unless policy is SET");
        }
        this.autoConfirmAt = copy(autoConfirmAt);
    }

    MarketOrderTransition(
            UUID orderId,
            Set<MarketOrderStatus> expectedStatuses,
            MarketOrderStatus nextStatus,
            UUID escrowTxnId,
            UUID releaseTxnId,
            UUID refundTxnId,
            Date autoConfirmAt
    ) {
        this(
                orderId,
                expectedStatuses,
                nextStatus,
                escrowTxnId,
                releaseTxnId,
                refundTxnId,
                autoConfirmAt == null ? AutoConfirmPolicy.KEEP : AutoConfirmPolicy.SET,
                autoConfirmAt
        );
    }

    static MarketOrderTransition escrowSucceeded(UUID orderId, UUID escrowTxnId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.ESCROW_PENDING),
                MarketOrderStatus.ESCROWED,
                Objects.requireNonNull(escrowTxnId, "escrowTxnId must not be null"),
                null,
                null,
                AutoConfirmPolicy.KEEP,
                null
        );
    }

    static MarketOrderTransition escrowFailed(UUID orderId) {
        return transition(orderId, Set.of(MarketOrderStatus.ESCROW_PENDING), MarketOrderStatus.ESCROW_FAILED);
    }

    static MarketOrderTransition escrowCancelPending(UUID orderId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.ESCROW_PENDING),
                MarketOrderStatus.ESCROW_CANCEL_PENDING
        );
    }

    static MarketOrderTransition lateEscrowRefundPending(UUID orderId, UUID escrowTxnId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.ESCROW_CANCEL_PENDING),
                MarketOrderStatus.REFUND_PENDING,
                Objects.requireNonNull(escrowTxnId, "escrowTxnId must not be null"),
                null,
                null,
                AutoConfirmPolicy.KEEP,
                null
        );
    }

    static MarketOrderTransition cancelledWithoutRefund(UUID orderId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.ESCROW_CANCEL_PENDING, MarketOrderStatus.ESCROW_FAILED),
                MarketOrderStatus.CANCELLED
        );
    }

    static MarketOrderTransition delivered(UUID orderId, Date autoConfirmAt) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.ESCROWED),
                MarketOrderStatus.DELIVERED,
                null,
                null,
                null,
                AutoConfirmPolicy.SET,
                Objects.requireNonNull(autoConfirmAt, "autoConfirmAt must not be null")
        );
    }

    static MarketOrderTransition shipped(UUID orderId, Date autoConfirmAt) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.ESCROWED),
                MarketOrderStatus.SHIPPED,
                null,
                null,
                null,
                AutoConfirmPolicy.SET,
                Objects.requireNonNull(autoConfirmAt, "autoConfirmAt must not be null")
        );
    }

    static MarketOrderTransition releasePending(UUID orderId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED),
                MarketOrderStatus.RELEASE_PENDING
        );
    }

    static MarketOrderTransition refundPending(UUID orderId) {
        return transition(orderId, Set.of(MarketOrderStatus.ESCROWED), MarketOrderStatus.REFUND_PENDING);
    }

    static MarketOrderTransition disputed(UUID orderId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED),
                MarketOrderStatus.DISPUTED
        );
    }

    static MarketOrderTransition disputeRefundPending(UUID orderId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.DISPUTED),
                MarketOrderStatus.DISPUTE_REFUND_PENDING
        );
    }

    static MarketOrderTransition disputeReleasePending(UUID orderId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.DISPUTED),
                MarketOrderStatus.DISPUTE_RELEASE_PENDING
        );
    }

    static MarketOrderTransition releaseSucceeded(UUID orderId, UUID releaseTxnId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.RELEASE_PENDING, MarketOrderStatus.DISPUTE_RELEASE_PENDING),
                MarketOrderStatus.COMPLETED,
                null,
                Objects.requireNonNull(releaseTxnId, "releaseTxnId must not be null"),
                null,
                AutoConfirmPolicy.CLEAR,
                null
        );
    }

    static MarketOrderTransition refundSucceeded(UUID orderId, UUID refundTxnId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.REFUND_PENDING),
                MarketOrderStatus.CANCELLED,
                null,
                null,
                Objects.requireNonNull(refundTxnId, "refundTxnId must not be null"),
                AutoConfirmPolicy.KEEP,
                null
        );
    }

    static MarketOrderTransition disputeRefundSucceeded(UUID orderId, UUID refundTxnId) {
        return transition(
                orderId,
                Set.of(MarketOrderStatus.DISPUTE_REFUND_PENDING),
                MarketOrderStatus.REFUNDED,
                null,
                null,
                Objects.requireNonNull(refundTxnId, "refundTxnId must not be null"),
                AutoConfirmPolicy.KEEP,
                null
        );
    }

    private static MarketOrderTransition transition(
            UUID orderId,
            Set<MarketOrderStatus> expectedStatuses,
            MarketOrderStatus nextStatus
    ) {
        return transition(
                orderId,
                expectedStatuses,
                nextStatus,
                null,
                null,
                null,
                AutoConfirmPolicy.KEEP,
                null
        );
    }

    private static MarketOrderTransition transition(
            UUID orderId,
            Set<MarketOrderStatus> expectedStatuses,
            MarketOrderStatus nextStatus,
            UUID escrowTxnId,
            UUID releaseTxnId,
            UUID refundTxnId,
            AutoConfirmPolicy autoConfirmPolicy,
            Date autoConfirmAt
    ) {
        return new MarketOrderTransition(
                orderId,
                expectedStatuses,
                nextStatus,
                escrowTxnId,
                releaseTxnId,
                refundTxnId,
                autoConfirmPolicy,
                autoConfirmAt
        );
    }

    public UUID orderId() {
        return orderId;
    }

    public Set<MarketOrderStatus> expectedStatuses() {
        return expectedStatuses;
    }

    public MarketOrderStatus nextStatus() {
        return nextStatus;
    }

    public UUID escrowTxnId() {
        return escrowTxnId;
    }

    public UUID releaseTxnId() {
        return releaseTxnId;
    }

    public UUID refundTxnId() {
        return refundTxnId;
    }

    public AutoConfirmPolicy autoConfirmPolicy() {
        return autoConfirmPolicy;
    }

    public Date autoConfirmAt() {
        return copy(autoConfirmAt);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
