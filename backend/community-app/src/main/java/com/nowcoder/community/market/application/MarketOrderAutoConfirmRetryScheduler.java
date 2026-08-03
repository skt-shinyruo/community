package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class MarketOrderAutoConfirmRetryScheduler {

    private final MarketOrderRepository marketOrderRepository;

    public MarketOrderAutoConfirmRetryScheduler(MarketOrderRepository marketOrderRepository) {
        this.marketOrderRepository = marketOrderRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean defer(UUID orderId, Date asOf, Date nextAttemptAt) {
        return marketOrderRepository.deferAutoConfirm(orderId, asOf, nextAttemptAt) == 1;
    }
}
