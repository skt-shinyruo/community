package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.TransferOrder;

import java.util.UUID;

public interface TransferOrderRepository {

    TransferOrder findByRequestId(String requestId);

    TransferOrder findByFromUserIdAndRequestId(UUID fromUserId, String requestId);

    int insert(TransferOrder order);

    int updateStatus(UUID fromUserId, String requestId, String fromStatus, String toStatus);
}
