package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketDisputeRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDisputeApplicationServiceUnitTest {

    @Mock
    private MarketDisputeRepository marketDisputeRepository;

    @Mock
    private MarketOrderRepository marketOrderRepository;

    @Mock
    private MarketWalletActionCoordinator marketWalletActionCoordinator;

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
                marketWalletActionCoordinator,
                new UuidV7Generator(),
                Clock.systemUTC()
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
        when(marketOrderRepository.apply(any(MarketOrderTransition.class)))
                .thenReturn(MarketOrderRepository.ApplyStatus.APPLIED);
        when(marketDisputeRepository.findById(disputeId)).thenReturn(resolved);

        MarketDisputeResult result = new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionCoordinator,
                new UuidV7Generator(),
                Clock.systemUTC()
        ).adminResolveRelease(disputeId, adminUserId, "证据支持卖家");

        assertThat(result.status()).isEqualTo("ADMIN_RESOLVED");
        assertThat(result.resolutionType()).isEqualTo("RELEASE");
        verify(marketDisputeRepository).lockById(disputeId);
        verify(marketOrderRepository).lockById(orderId);
        verify(marketWalletActionCoordinator).enqueueDisputeRelease(orderId, disputeId, sellerUserId, buyerUserId, 12_900L);
    }

    @Test
    void adminResolveRefundWithBlankNoteShouldKeepSellerNote() {
        UUID disputeId = uuid(1);
        UUID orderId = uuid(2);
        UUID sellerUserId = uuid(3);
        UUID buyerUserId = uuid(4);
        UUID adminUserId = uuid(5);
        MarketDispute rejected = rejectedDispute(disputeId, orderId, sellerUserId, buyerUserId);
        MarketOrder order = disputedOrder(orderId, sellerUserId, buyerUserId, 12_900L);

        when(marketDisputeRepository.lockById(disputeId)).thenReturn(rejected);
        when(marketOrderRepository.lockById(orderId)).thenReturn(order);
        when(marketOrderRepository.apply(any(MarketOrderTransition.class)))
                .thenReturn(MarketOrderRepository.ApplyStatus.APPLIED);
        when(marketDisputeRepository.findById(disputeId)).thenReturn(copyDispute(rejected));

        new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionCoordinator,
                new UuidV7Generator(),
                Clock.systemUTC()
        ).adminResolveRefund(disputeId, adminUserId, "  ");

        ArgumentCaptor<MarketDispute> saved = ArgumentCaptor.forClass(MarketDispute.class);
        verify(marketDisputeRepository).saveChanges(saved.capture());
        assertThat(saved.getValue().getSellerNote()).isEqualTo("不同意退款");
    }

    @Test
    void adminResolveRefundWithNoteShouldReplaceSellerNote() {
        UUID disputeId = uuid(1);
        UUID orderId = uuid(2);
        UUID sellerUserId = uuid(3);
        UUID buyerUserId = uuid(4);
        UUID adminUserId = uuid(5);
        MarketDispute rejected = rejectedDispute(disputeId, orderId, sellerUserId, buyerUserId);
        MarketOrder order = disputedOrder(orderId, sellerUserId, buyerUserId, 12_900L);

        when(marketDisputeRepository.lockById(disputeId)).thenReturn(rejected);
        when(marketOrderRepository.lockById(orderId)).thenReturn(order);
        when(marketOrderRepository.apply(any(MarketOrderTransition.class)))
                .thenReturn(MarketOrderRepository.ApplyStatus.APPLIED);
        when(marketDisputeRepository.findById(disputeId)).thenReturn(copyDispute(rejected));

        new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionCoordinator,
                new UuidV7Generator(),
                Clock.systemUTC()
        ).adminResolveRefund(disputeId, adminUserId, " 证据支持买家 ");

        ArgumentCaptor<MarketDispute> saved = ArgumentCaptor.forClass(MarketDispute.class);
        verify(marketDisputeRepository).saveChanges(saved.capture());
        assertThat(saved.getValue().getSellerNote()).isEqualTo("证据支持买家");
    }

    @Test
    void listOpenDisputesShouldExposeOrderTotalAmount() {
        UUID disputeId = uuid(1);
        UUID orderId = uuid(2);
        UUID sellerUserId = uuid(3);
        UUID buyerUserId = uuid(4);
        MarketDispute rejected = rejectedDispute(disputeId, orderId, sellerUserId, buyerUserId);
        MarketOrder order = disputedOrder(orderId, sellerUserId, buyerUserId, 12_900L);

        when(marketDisputeRepository.findOpenDisputes()).thenReturn(List.of(rejected));
        when(marketOrderRepository.findById(orderId)).thenReturn(order);

        List<MarketDisputeResult> results = new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionCoordinator,
                new UuidV7Generator(),
                Clock.systemUTC()
        ).listOpenDisputes();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).totalAmount()).isEqualTo(12_900L);
    }

    @Test
    void listOpenDisputesShouldLeaveTotalAmountNullWhenOrderMissing() {
        UUID disputeId = uuid(1);
        UUID orderId = uuid(2);
        UUID sellerUserId = uuid(3);
        UUID buyerUserId = uuid(4);
        MarketDispute rejected = rejectedDispute(disputeId, orderId, sellerUserId, buyerUserId);

        when(marketDisputeRepository.findOpenDisputes()).thenReturn(List.of(rejected));

        List<MarketDisputeResult> results = new MarketDisputeApplicationService(
                marketDisputeRepository,
                marketOrderRepository,
                marketWalletActionCoordinator,
                new UuidV7Generator(),
                Clock.systemUTC()
        ).listOpenDisputes();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).totalAmount()).isNull();
    }

    private MarketDispute rejectedDispute(UUID disputeId, UUID orderId, UUID sellerUserId, UUID buyerUserId) {
        MarketDispute dispute = openDispute(disputeId, orderId, sellerUserId);
        dispute.setBuyerUserId(buyerUserId);
        dispute.setStatus("SELLER_REJECTED");
        dispute.setSellerNote("不同意退款");
        return dispute;
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
        return order(orderId)
                .sellerUserId(sellerUserId)
                .buyerUserId(buyerUserId)
                .status("DISPUTED")
                .totalAmount(totalAmount)
                .build();
    }
}
