package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketOrderTransition;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MarketOrderTransitionDataObject {

    private UUID orderId;
    private List<String> expectedStatuses;
    private String nextStatus;
    private UUID escrowTxnId;
    private UUID releaseTxnId;
    private UUID refundTxnId;
    private String autoConfirmPolicy;
    private Date autoConfirmAt;

    public static MarketOrderTransitionDataObject from(MarketOrderTransition transition) {
        MarketOrderTransitionDataObject dataObject = new MarketOrderTransitionDataObject();
        dataObject.orderId = transition.orderId();
        dataObject.expectedStatuses = transition.expectedStatuses().stream()
                .map(status -> status.code())
                .sorted()
                .toList();
        dataObject.nextStatus = transition.nextStatus().code();
        dataObject.escrowTxnId = transition.escrowTxnId();
        dataObject.releaseTxnId = transition.releaseTxnId();
        dataObject.refundTxnId = transition.refundTxnId();
        dataObject.autoConfirmPolicy = transition.autoConfirmPolicy().name();
        dataObject.autoConfirmAt = transition.autoConfirmAt();
        return dataObject;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public List<String> getExpectedStatuses() {
        return expectedStatuses;
    }

    public String getNextStatus() {
        return nextStatus;
    }

    public UUID getEscrowTxnId() {
        return escrowTxnId;
    }

    public UUID getReleaseTxnId() {
        return releaseTxnId;
    }

    public UUID getRefundTxnId() {
        return refundTxnId;
    }

    public String getAutoConfirmPolicy() {
        return autoConfirmPolicy;
    }

    public Date getAutoConfirmAt() {
        return autoConfirmAt;
    }
}
