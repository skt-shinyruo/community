package com.nowcoder.community.market.infrastructure.api;

import com.nowcoder.community.market.api.action.MarketOrderAutoConfirmActionApi;
import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService;
import org.springframework.stereotype.Service;

@Service
public class MarketOrderAutoConfirmActionApiAdapter implements MarketOrderAutoConfirmActionApi {

    private final MarketOrderAutoConfirmApplicationService applicationService;

    public MarketOrderAutoConfirmActionApiAdapter(MarketOrderAutoConfirmApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
        com.nowcoder.community.market.application.result.MarketOrderAutoConfirmResult result =
                applicationService.autoConfirmDueOrders();
        return new MarketOrderAutoConfirmResult(result.completedCount(), result.skippedCount());
    }
}
