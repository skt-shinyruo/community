package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.WithdrawOrder;
import com.nowcoder.community.wallet.domain.repository.WithdrawOrderRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WithdrawOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WithdrawOrderMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisWithdrawOrderRepository implements WithdrawOrderRepository {

    private final WithdrawOrderMapper mapper;

    public MyBatisWithdrawOrderRepository(WithdrawOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public WithdrawOrder findByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Override
    public WithdrawOrder findByUserIdAndRequestId(UUID userId, String requestId) {
        return mapper.selectByUserIdAndRequestId(userId, requestId);
    }

    @Override
    public int insert(WithdrawOrder order) {
        return mapper.insert(WithdrawOrderDataObject.from(order));
    }

    @Override
    public int updateStatus(UUID userId, String requestId, String fromStatus, String toStatus) {
        return mapper.updateStatus(userId, requestId, fromStatus, toStatus);
    }
}
