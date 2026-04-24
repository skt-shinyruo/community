package com.nowcoder.community.market.service;

import com.nowcoder.community.market.dto.MarketDisputeResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdminMarketApplicationService {

    private final MarketDisputeService marketDisputeService;

    public AdminMarketApplicationService(MarketDisputeService marketDisputeService) {
        this.marketDisputeService = marketDisputeService;
    }

    public List<MarketDisputeResponse> listOpenDisputes() {
        return marketDisputeService.listOpenDisputes();
    }

    public MarketDisputeResponse resolveRefund(UUID disputeId, UUID actorUserId, String note) {
        return marketDisputeService.adminResolveRefund(disputeId, actorUserId, note);
    }

    public MarketDisputeResponse resolveRelease(UUID disputeId, UUID actorUserId, String note) {
        return marketDisputeService.adminResolveRelease(disputeId, actorUserId, note);
    }
}
