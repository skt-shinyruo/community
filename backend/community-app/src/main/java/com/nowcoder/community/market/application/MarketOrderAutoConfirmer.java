package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class MarketOrderAutoConfirmer {

    private final MarketOrderRepository marketOrderRepository;
    private final MarketWalletActionCoordinator marketWalletActionCoordinator;

    public MarketOrderAutoConfirmer(
            MarketOrderRepository marketOrderRepository,
            MarketWalletActionCoordinator marketWalletActionCoordinator
    ) {
        this.marketOrderRepository = marketOrderRepository;
        this.marketWalletActionCoordinator = marketWalletActionCoordinator;
    }

    @Transactional
    public boolean confirmOneDueOrder(UUID orderId, Date now) {
        MarketOrder locked = marketOrderRepository.lockById(orderId);
        if (locked == null || !locked.isAutoConfirmDue(now)) {
            return false;
        }
        if (marketOrderRepository.apply(locked.requestRelease()) != MarketOrderRepository.ApplyStatus.APPLIED) {
            return false;
        }
        marketWalletActionCoordinator.enqueueRelease(
                locked.getOrderId(),
                locked.getSellerUserId(),
                locked.getBuyerUserId(),
                locked.getTotalAmount()
        );
        return true;
    }
}
