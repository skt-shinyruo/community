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
        return applicationService.autoConfirmDueOrders();
    }
}
