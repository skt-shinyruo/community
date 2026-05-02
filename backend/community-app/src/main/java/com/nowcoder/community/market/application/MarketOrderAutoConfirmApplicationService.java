package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.result.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class MarketOrderAutoConfirmApplicationService {

    private final MarketOrderRepository marketOrderRepository;
    private final MarketOrderAutoConfirmSingleOrderApplicationService singleOrderApplicationService;

    public MarketOrderAutoConfirmApplicationService(
            MarketOrderRepository marketOrderRepository,
            MarketOrderAutoConfirmSingleOrderApplicationService singleOrderApplicationService
    ) {
        this.marketOrderRepository = marketOrderRepository;
        this.singleOrderApplicationService = singleOrderApplicationService;
    }

    public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
        int completed = 0;
        int skipped = 0;
        Date now = new Date();
        for (MarketOrder dueOrder : marketOrderRepository.findDueForAutoConfirm(now)) {
            try {
                if (singleOrderApplicationService.confirmOneDueOrder(dueOrder.getOrderId(), now)) {
                    completed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        return new MarketOrderAutoConfirmResult(completed, skipped);
    }
}
