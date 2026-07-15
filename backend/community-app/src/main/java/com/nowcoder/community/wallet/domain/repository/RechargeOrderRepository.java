package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.domain.model.RechargeOrderTransition;

import java.util.UUID;

public interface RechargeOrderRepository {

    RechargeOrder findByRequestId(String requestId);

    RechargeOrder findByUserIdAndRequestId(UUID userId, String requestId);

    CreationOutcome<RechargeOrder> create(RechargeOrder order);

    int updateStatus(UUID userId, String requestId, String fromStatus, String toStatus);

    int applyTransition(RechargeOrderTransition transition);
}
