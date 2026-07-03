package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketDisputeRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDisputeApplicationServiceUnitTest {

    @Mock
    private MarketDisputeRepository marketDisputeRepository;

    @Mock
    private MarketOrderRepository marketOrderRepository;

    @Mock
    private MarketWalletActionApplicationService marketWalletActionService;

    @Test
    void sellerRejectRefundShouldLockDisputeBeforeSavingDecision() {
        UUID disputeId = uuid(1);
        UUID orderId = uuid(2);
        UUID sellerUserId = uuid(3);
        MarketDispute open = openDispute(disputeId, orderId, sellerUserId);
        MarketDispute rejected = copyDispute(open);
        rejected.setStatus("SELLER_REJECTED");
        rejected.setSellerNote("不同意退款");

        when(marketDisputeRepository.lockById(disputeId)).thenReturn(open);
        when(marketDisputeRepository.findById(disputeId)).thenReturn(rejected);

        MarketDisputeResult result = new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionService
        ).sellerRejectRefund(disputeId, sellerUserId, "不同意退款");

        assertThat(result.status()).isEqualTo("SELLER_REJECTED");
        verify(marketDisputeRepository).lockById(disputeId);
    }

    @Test
    void adminResolveReleaseShouldLockDisputeBeforeOrderTransition() {
        UUID disputeId = uuid(1);
        UUID orderId = uuid(2);
        UUID sellerUserId = uuid(3);
        UUID buyerUserId = uuid(4);
        UUID adminUserId = uuid(5);
        MarketDispute open = openDispute(disputeId, orderId, sellerUserId);
        open.setBuyerUserId(buyerUserId);
        MarketDispute resolved = copyDispute(open);
        resolved.setStatus("ADMIN_RESOLVED");
        resolved.setResolutionType("RELEASE");
        resolved.setResolvedBy(adminUserId);
        resolved.setResolvedAt(new Date());
        MarketOrder order = disputedOrder(orderId, sellerUserId, buyerUserId, 12_900L);

        when(marketDisputeRepository.lockById(disputeId)).thenReturn(open);
        when(marketOrderRepository.lockById(orderId)).thenReturn(order);
        when(marketOrderRepository.markDisputeReleasePending(orderId)).thenReturn(1);
        when(marketDisputeRepository.findById(disputeId)).thenReturn(resolved);

        MarketDisputeResult result = new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionService
        ).adminResolveRelease(disputeId, adminUserId, "证据支持卖家");

        assertThat(result.status()).isEqualTo("ADMIN_RESOLVED");
        assertThat(result.resolutionType()).isEqualTo("RELEASE");
        verify(marketDisputeRepository).lockById(disputeId);
        verify(marketOrderRepository).lockById(orderId);
        verify(marketWalletActionService).enqueueDisputeRelease(orderId, disputeId, sellerUserId, buyerUserId, 12_900L);
    }

    private MarketDispute openDispute(UUID disputeId, UUID orderId, UUID sellerUserId) {
        MarketDispute dispute = new MarketDispute();
        dispute.setDisputeId(disputeId);
        dispute.setOrderId(orderId);
        dispute.setSellerUserId(sellerUserId);
        dispute.setStatus("OPEN");
        dispute.setReason("货不对板");
        dispute.setBuyerNote("和描述不一致");
        return dispute;
    }

    private MarketDispute copyDispute(MarketDispute source) {
        MarketDispute dispute = new MarketDispute();
        dispute.setDisputeId(source.getDisputeId());
        dispute.setOrderId(source.getOrderId());
        dispute.setGoodsType(source.getGoodsType());
        dispute.setBuyerUserId(source.getBuyerUserId());
        dispute.setSellerUserId(source.getSellerUserId());
        dispute.setStatus(source.getStatus());
        dispute.setReason(source.getReason());
        dispute.setBuyerNote(source.getBuyerNote());
        dispute.setSellerNote(source.getSellerNote());
        dispute.setResolutionType(source.getResolutionType());
        dispute.setResolvedBy(source.getResolvedBy());
        dispute.setResolvedAt(source.getResolvedAt());
        return dispute;
    }

    private MarketOrder disputedOrder(UUID orderId, UUID sellerUserId, UUID buyerUserId, long totalAmount) {
        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        order.setSellerUserId(sellerUserId);
        order.setBuyerUserId(buyerUserId);
        order.setStatus("DISPUTED");
        order.setTotalAmount(totalAmount);
        return order;
    }
}
