package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MarketOrderTransition(
        UUID orderId,
        Set<MarketOrderStatus> expectedStatuses,
        MarketOrderStatus nextStatus,
        UUID escrowTxnId,
        UUID releaseTxnId,
        UUID refundTxnId,
        Date autoConfirmAt
) {
    public MarketOrderTransition {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(expectedStatuses, "expectedStatuses must not be null");
        if (expectedStatuses.isEmpty()) {
            throw new IllegalArgumentException("expectedStatuses must not be empty");
        }
        expectedStatuses = Set.copyOf(expectedStatuses);
        Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        autoConfirmAt = copy(autoConfirmAt);
    }

    public Date autoConfirmAt() {
        return copy(autoConfirmAt);
    }

    public static MarketOrderTransition delivered(UUID orderId, Date autoConfirmAt) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.ESCROWED),
                MarketOrderStatus.DELIVERED,
                null,
                null,
                null,
                autoConfirmAt
        );
    }

    public static MarketOrderTransition shipped(UUID orderId, Date autoConfirmAt) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.ESCROWED),
                MarketOrderStatus.SHIPPED,
                null,
                null,
                null,
                autoConfirmAt
        );
    }

    public static MarketOrderTransition releasePending(UUID orderId) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED),
                MarketOrderStatus.RELEASE_PENDING,
                null,
                null,
                null,
                null
        );
    }

    public static MarketOrderTransition refundPending(UUID orderId) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.ESCROWED),
                MarketOrderStatus.REFUND_PENDING,
                null,
                null,
                null,
                null
        );
    }

    public static MarketOrderTransition escrowCancelPending(UUID orderId) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.ESCROW_PENDING),
                MarketOrderStatus.ESCROW_CANCEL_PENDING,
                null,
                null,
                null,
                null
        );
    }

    public static MarketOrderTransition disputed(UUID orderId) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED),
                MarketOrderStatus.DISPUTED,
                null,
                null,
                null,
                null
        );
    }

    public static MarketOrderTransition disputeRefundPending(UUID orderId) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.DISPUTED),
                MarketOrderStatus.DISPUTE_REFUND_PENDING,
                null,
                null,
                null,
                null
        );
    }

    public static MarketOrderTransition disputeReleasePending(UUID orderId) {
        return new MarketOrderTransition(
                orderId,
                Set.of(MarketOrderStatus.DISPUTED),
                MarketOrderStatus.DISPUTE_RELEASE_PENDING,
                null,
                null,
                null,
                null
        );
    }

    private static Date copy(Date date) {
        return date == null ? null : new Date(date.getTime());
    }
}
