package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketAddressRepository;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketDeliveryRepository;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketInventoryRepository;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketListingRepository;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketShipmentRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketAddressMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketDeliveryMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketListingMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketShipmentMapper;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrderApplicationServiceUnitTest {

    @Mock
    private MarketListingMapper marketListingMapper;

    @Mock
    private MarketInventoryUnitMapper marketInventoryUnitMapper;

    @Mock
    private MarketOrderMapper marketOrderMapper;

    @Mock
    private MarketAddressMapper marketAddressMapper;

    @Mock
    private MarketDeliveryMapper marketDeliveryMapper;

    @Mock
    private MarketShipmentMapper marketShipmentMapper;

    @Mock
    private MarketWalletActionApplicationService marketWalletActionService;

    @Mock
    private MarketOrderSagaApplicationService marketOrderSagaService;

    @Test
    void createOrderShouldReturnExistingReplayAfterListingLockEvenIfListingIsAlreadySoldOut() {
        MarketOrderApplicationService service = new MarketOrderApplicationService(
                new MyBatisMarketListingRepository(marketListingMapper),
                new MyBatisMarketInventoryRepository(marketInventoryUnitMapper),
                new MyBatisMarketOrderRepository(marketOrderMapper),
                new MyBatisMarketAddressRepository(marketAddressMapper),
                new MyBatisMarketDeliveryRepository(marketDeliveryMapper),
                new MyBatisMarketShipmentRepository(marketShipmentMapper),
                marketWalletActionService,
                marketOrderSagaService,
                new UuidV7Generator()
        );
        UUID buyerUserId = UUID.fromString("00000000-0000-7000-8000-000000000009");
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        String requestId = "market:req-replay-after-lock";

        when(marketOrderMapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId)).thenReturn(null);
        when(marketListingMapper.selectByIdForUpdate(listingId)).thenReturn(MarketListingDataObject.from(soldOutListing(listingId)));
        when(marketOrderMapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId))
                .thenReturn(MarketOrderDataObject.from(existingOrder(requestId, listingId, buyerUserId, 1)));

        MarketOrderResult response = service.createOrder(requestId, buyerUserId, listingId, 1, null);

        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.listingId()).isEqualTo(listingId);
        verify(marketWalletActionService, never()).enqueueEscrow(any(), any(), any(), anyLong());
    }

    @Test
    void createOrderShouldReloadExistingOrderWhenInsertHitsDuplicateRequestId() {
        MarketOrderApplicationService service = new MarketOrderApplicationService(
                new MyBatisMarketListingRepository(marketListingMapper),
                new MyBatisMarketInventoryRepository(marketInventoryUnitMapper),
                new MyBatisMarketOrderRepository(marketOrderMapper),
                new MyBatisMarketAddressRepository(marketAddressMapper),
                new MyBatisMarketDeliveryRepository(marketDeliveryMapper),
                new MyBatisMarketShipmentRepository(marketShipmentMapper),
                marketWalletActionService,
                marketOrderSagaService,
                new UuidV7Generator()
        );
        UUID buyerUserId = UUID.fromString("00000000-0000-7000-8000-000000000009");
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        String requestId = "market:req-duplicate-insert";
        MarketOrder duplicated = existingOrder(requestId, listingId, buyerUserId, 1);

        when(marketOrderMapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId)).thenReturn(null);
        when(marketListingMapper.selectByIdForUpdate(listingId)).thenReturn(MarketListingDataObject.from(activeListing(listingId)));
        when(marketOrderMapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId))
                .thenReturn(null, MarketOrderDataObject.from(duplicated));
        when(marketOrderMapper.insert(any(MarketOrderDataObject.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate requestId"));

        MarketOrderResult response = service.createOrder(requestId, buyerUserId, listingId, 1, null);

        assertThat(response.orderId()).isEqualTo(duplicated.getOrderId());
        assertThat(response.requestId()).isEqualTo(requestId);
        verify(marketWalletActionService, never()).enqueueEscrow(any(), any(), any(), anyLong());
    }

    private MarketListing activeListing(UUID listingId) {
        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(UUID.fromString("00000000-0000-7000-8000-000000000008"));
        listing.setGoodsType("VIRTUAL");
        listing.setTitle("邀请码");
        listing.setDescription("手工交付");
        listing.setUnitPrice(1_200L);
        listing.setDeliveryMode("MANUAL");
        listing.setStockMode("FINITE");
        listing.setStockTotal(1);
        listing.setStockAvailable(1);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("ACTIVE");
        return listing;
    }

    private MarketListing soldOutListing(UUID listingId) {
        MarketListing listing = activeListing(listingId);
        listing.setStockAvailable(0);
        listing.setStatus("SOLD_OUT");
        return listing;
    }

    private MarketOrder existingOrder(String requestId, UUID listingId, UUID buyerUserId, int quantity) {
        MarketOrder order = new MarketOrder();
        order.setOrderId(UUID.fromString("00000000-0000-7000-8000-000000000101"));
        order.setRequestId(requestId);
        order.setListingId(listingId);
        order.setGoodsType("VIRTUAL");
        order.setSellerUserId(UUID.fromString("00000000-0000-7000-8000-000000000008"));
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(quantity);
        order.setUnitPriceSnapshot(1_200L);
        order.setTotalAmount(1_200L);
        order.setDeliveryModeSnapshot("MANUAL");
        order.setListingTitleSnapshot("邀请码");
        order.setStatus("ESCROWED");
        order.setEscrowTxnId(UUID.fromString("00000000-0000-7000-8000-000000000123"));
        return order;
    }
}
