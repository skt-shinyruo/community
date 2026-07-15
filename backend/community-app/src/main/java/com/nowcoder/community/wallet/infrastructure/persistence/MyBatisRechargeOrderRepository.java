package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.domain.model.RechargeOrderTransition;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.RechargeOrderRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.RechargeOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.RechargeOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisRechargeOrderRepository implements RechargeOrderRepository {

    private final RechargeOrderMapper mapper;

    public MyBatisRechargeOrderRepository(RechargeOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RechargeOrder findByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Override
    public RechargeOrder findByUserIdAndRequestId(UUID userId, String requestId) {
        return mapper.selectByUserIdAndRequestId(userId, requestId);
    }

    @Override
    public CreationOutcome<RechargeOrder> create(RechargeOrder order) {
        try {
            return mapper.insert(RechargeOrderDataObject.from(order)) == 1
                    ? CreationOutcome.created(order)
                    : CreationOutcome.conflict();
        } catch (DuplicateKeyException exception) {
            RechargeOrder existing = mapper.selectByUserIdAndRequestId(order.getUserId(), order.getRequestId());
            return existing == null
                    ? CreationOutcome.conflict()
                    : CreationOutcome.alreadyExists(existing);
        }
    }

    @Override
    public int updateStatus(UUID userId, String requestId, String fromStatus, String toStatus) {
        return mapper.updateStatus(userId, requestId, fromStatus, toStatus);
    }

    @Override
    public int applyTransition(RechargeOrderTransition transition) {
        return updateStatus(
                transition.userId(),
                transition.requestId(),
                transition.fromStatus().code(),
                transition.toStatus().code()
        );
    }
}
