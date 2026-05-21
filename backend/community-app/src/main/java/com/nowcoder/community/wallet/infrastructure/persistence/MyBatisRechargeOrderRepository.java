package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.domain.model.RechargeOrderTransition;
import com.nowcoder.community.wallet.domain.repository.RechargeOrderRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.RechargeOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.RechargeOrderMapper;
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
    public int insert(RechargeOrder order) {
        return mapper.insert(RechargeOrderDataObject.from(order));
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
