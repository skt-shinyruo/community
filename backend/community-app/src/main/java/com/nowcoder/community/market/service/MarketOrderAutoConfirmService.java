package com.nowcoder.community.market.service;

import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketOrderAutoConfirmService {

    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_SHIPPED = "SHIPPED";

    private final MarketOrderMapper marketOrderMapper;
    private final MarketWalletActionService marketWalletActionService;

    public MarketOrderAutoConfirmService(MarketOrderMapper marketOrderMapper,
                                         MarketWalletActionService marketWalletActionService) {
        this.marketOrderMapper = marketOrderMapper;
        this.marketWalletActionService = marketWalletActionService;
    }

    @Transactional
    public boolean confirmOneDueOrder(UUID orderId, Date now) {
        MarketOrder locked = marketOrderMapper.selectByIdForUpdate(orderId);
        if (locked == null
                || !Set.of(STATUS_DELIVERED, STATUS_SHIPPED).contains(locked.getStatus())
                || locked.getAutoConfirmAt() == null
                || locked.getAutoConfirmAt().after(now)) {
            return false;
        }
        int updated = marketOrderMapper.markReleasePending(locked.getOrderId());
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
