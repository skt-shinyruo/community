package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.WalletAdminAction;
import com.nowcoder.community.wallet.domain.repository.WalletAdminActionRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAdminActionDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletAdminActionMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisWalletAdminActionRepository implements WalletAdminActionRepository {

    private final WalletAdminActionMapper mapper;

    public MyBatisWalletAdminActionRepository(WalletAdminActionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(WalletAdminAction action) {
        return mapper.insert(WalletAdminActionDataObject.from(action));
    }

    @Override
    public WalletAdminAction findByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Override
    public List<WalletAdminAction> findRecentByTargetAccountId(UUID targetAccountId, int limit) {
        return new ArrayList<>(mapper.selectRecentByTargetAccountId(targetAccountId, limit));
    }
}
