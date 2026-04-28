package com.nowcoder.community.market.application;

import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketOrderAutoConfirmApplicationService {

    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_SHIPPED = "SHIPPED";

    private final MarketOrderRepository marketOrderMapper;
    private final MarketWalletActionApplicationService marketWalletActionService;

    public MarketOrderAutoConfirmApplicationService(MarketOrderRepository marketOrderMapper,
                                         MarketWalletActionApplicationService marketWalletActionService) {
        this.marketOrderMapper = marketOrderMapper;
        this.marketWalletActionService = marketWalletActionService;
    }

    public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
        int completed = 0;
        int skipped = 0;
        Date now = new Date();
        for (MarketOrder dueOrder : marketOrderMapper.selectDueForAutoConfirm(now)) {
            try {
                if (confirmOneDueOrder(dueOrder.getOrderId(), now)) {
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
