package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketOrderAutoConfirmSingleOrderApplicationService {

    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_SHIPPED = "SHIPPED";

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
        MarketOrder locked = marketOrderRepository.selectByIdForUpdate(orderId);
        if (locked == null
                || !Set.of(STATUS_DELIVERED, STATUS_SHIPPED).contains(locked.getStatus())
                || locked.getAutoConfirmAt() == null
                || locked.getAutoConfirmAt().after(now)) {
            return false;
        }
        int updated = marketOrderRepository.markReleasePending(locked.getOrderId());
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
