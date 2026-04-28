package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.result.MarketDisputeResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdminMarketApplicationService {

    private final MarketDisputeApplicationService marketDisputeService;

    public AdminMarketApplicationService(MarketDisputeApplicationService marketDisputeService) {
        this.marketDisputeService = marketDisputeService;
    }

    public List<MarketDisputeResult> listOpenDisputes() {
        return marketDisputeService.listOpenDisputes();
    }

    public MarketDisputeResult resolveRefund(UUID disputeId, UUID actorUserId, String note) {
        return marketDisputeService.adminResolveRefund(disputeId, actorUserId, note);
    }

    public MarketDisputeResult resolveRelease(UUID disputeId, UUID actorUserId, String note) {
        return marketDisputeService.adminResolveRelease(disputeId, actorUserId, note);
    }
}
