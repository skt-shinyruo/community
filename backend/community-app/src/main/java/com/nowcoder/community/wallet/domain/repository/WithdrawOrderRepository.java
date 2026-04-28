package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.WithdrawOrder;

import java.util.UUID;

public interface WithdrawOrderRepository {

    WithdrawOrder findByRequestId(String requestId);

    WithdrawOrder findByUserIdAndRequestId(UUID userId, String requestId);

    int insert(WithdrawOrder order);

    int updateStatus(UUID userId, String requestId, String fromStatus, String toStatus);
}
