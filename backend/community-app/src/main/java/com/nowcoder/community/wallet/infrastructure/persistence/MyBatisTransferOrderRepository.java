package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.TransferOrder;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.TransferOrderRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.TransferOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.TransferOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisTransferOrderRepository implements TransferOrderRepository {

    private final TransferOrderMapper mapper;

    public MyBatisTransferOrderRepository(TransferOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TransferOrder findByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Override
    public TransferOrder findByFromUserIdAndRequestId(UUID fromUserId, String requestId) {
        return mapper.selectByFromUserIdAndRequestId(fromUserId, requestId);
    }

    @Override
    public CreationOutcome<TransferOrder> create(TransferOrder order) {
        try {
            return mapper.insert(TransferOrderDataObject.from(order)) == 1
                    ? CreationOutcome.created(order)
                    : CreationOutcome.conflict();
        } catch (DuplicateKeyException exception) {
            TransferOrder existing = mapper.selectByFromUserIdAndRequestId(
                    order.getFromUserId(),
                    order.getRequestId()
            );
            return existing == null
                    ? CreationOutcome.conflict()
                    : CreationOutcome.alreadyExists(existing);
        }
    }

    @Override
    public int updateStatus(UUID fromUserId, String requestId, String fromStatus, String toStatus) {
        return mapper.updateStatus(fromUserId, requestId, fromStatus, toStatus);
    }
}
