package com.nowcoder.community.market.application;

import com.nowcoder.community.market.api.action.MarketOrderAutoConfirmActionApi;
import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class MarketOrderAutoConfirmApplicationService implements MarketOrderAutoConfirmActionApi {

    private final MarketOrderRepository marketOrderRepository;
    private final MarketOrderAutoConfirmer autoConfirmer;

    public MarketOrderAutoConfirmApplicationService(
            MarketOrderRepository marketOrderRepository,
            MarketOrderAutoConfirmer autoConfirmer
    ) {
        this.marketOrderRepository = marketOrderRepository;
        this.autoConfirmer = autoConfirmer;
    }

    @Override
    public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
        int completed = 0;
        int skipped = 0;
        Date now = new Date();
        for (MarketOrder dueOrder : marketOrderRepository.findDueForAutoConfirm(now)) {
            try {
                if (autoConfirmer.confirmOneDueOrder(dueOrder.getOrderId(), now)) {
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
