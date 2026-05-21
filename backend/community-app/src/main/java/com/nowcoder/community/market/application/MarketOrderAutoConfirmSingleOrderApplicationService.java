package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class MarketOrderAutoConfirmSingleOrderApplicationService {

    private final MarketOrderRepository marketOrderRepository;
    private final MarketWalletActionApplicationService marketWalletActionService;

    public MarketOrderAutoConfirmSingleOrderApplicationService(
            MarketOrderRepository marketOrderRepository,
            MarketWalletActionApplicationService marketWalletActionService
    ) {
        this.marketOrderRepository = marketOrderRepository;
        this.marketWalletActionService = marketWalletActionService;
    }

    @Transactional
    public boolean confirmOneDueOrder(UUID orderId, Date now) {
        MarketOrder locked = marketOrderRepository.lockById(orderId);
        if (locked == null || !locked.isAutoConfirmDue(now)) {
            return false;
        }
        int updated = marketOrderRepository.markReleasePending(locked.requestRelease().orderId());
        if (updated != 1) {
            return false;
        }
        marketWalletActionService.enqueueRelease(
                locked.getOrderId(),
                locked.getSellerUserId(),
                locked.getBuyerUserId(),
                locked.getTotalAmount()
        );
        return true;
    }
}
