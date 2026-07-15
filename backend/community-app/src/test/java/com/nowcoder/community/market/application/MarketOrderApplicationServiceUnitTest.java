package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.exception.MarketErrorCode;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void createOrderShouldRejectNullCommand() {
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

        assertThatThrownBy(() -> service.createOrder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void createOrderShouldReturnExistingPhysicalReplayWithoutAddressLookup() {
        MarketOrderApplicationService service = service();
        UUID buyerUserId = uuid(9);
        UUID listingId = uuid(7);
        UUID addressId = uuid(5);
        String requestId = "market:req-physical-replay";
        MarketOrder existing = existingPhysicalOrder(requestId, listingId, buyerUserId, addressId);
        when(marketOrderMapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId))
                .thenReturn(MarketOrderDataObject.from(existing));

        MarketOrderResult response = service.createOrder(requestId, buyerUserId, listingId, 1, addressId);

        assertThat(response.orderId()).isEqualTo(existing.getOrderId());
        verifyNoInteractions(marketAddressMapper);
        verify(marketListingMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void createOrderShouldRejectPhysicalReplayWithMissingPersistedAddressWithoutAddressLookup() {
        MarketOrderApplicationService service = service();
        UUID buyerUserId = uuid(9);
        UUID listingId = uuid(7);
        UUID addressId = uuid(5);
        String requestId = "market:req-missing-address-snapshot";
        MarketOrder existing = existingPhysicalOrder(requestId, listingId, buyerUserId, null);
        when(marketOrderMapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId))
                .thenReturn(MarketOrderDataObject.from(existing));

        assertThatThrownBy(() -> service.createOrder(requestId, buyerUserId, listingId, 1, addressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
        verifyNoInteractions(marketAddressMapper);
        verify(marketListingMapper, never()).selectByIdForUpdate(any());
    }

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
                .thenThrow(new DuplicateKeyException("duplicate requestId"));

        MarketOrderResult response = service.createOrder(requestId, buyerUserId, listingId, 1, null);

        assertThat(response.orderId()).isEqualTo(duplicated.getOrderId());
        assertThat(response.requestId()).isEqualTo(requestId);
        verify(marketWalletActionService, never()).enqueueEscrow(any(), any(), any(), anyLong());
    }

    @Test
    void createOrderShouldRejectMismatchedAggregateReloadedAfterDuplicateInsert() {
        MarketOrderApplicationService service = service();
        UUID buyerUserId = UUID.fromString("00000000-0000-7000-8000-000000000009");
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        String requestId = "market:req-duplicate-mismatch";
        MarketOrder conflicting = existingOrder(requestId, listingId, buyerUserId, 2);

        when(marketOrderMapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId)).thenReturn(null);
        when(marketListingMapper.selectByIdForUpdate(listingId)).thenReturn(MarketListingDataObject.from(activeListing(listingId)));
        when(marketOrderMapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId))
                .thenReturn(null, MarketOrderDataObject.from(conflicting));
        when(marketOrderMapper.insert(any(MarketOrderDataObject.class)))
                .thenThrow(new DuplicateKeyException("duplicate requestId"));

        assertThatThrownBy(() -> service.createOrder(requestId, buyerUserId, listingId, 1, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
        verify(marketWalletActionService, never()).enqueueEscrow(any(), any(), any(), anyLong());
    }

    @Test
    void createOrderShouldPropagateUnknownIntegrityFailure() {
        MarketOrderApplicationService service = service();
        UUID buyerUserId = UUID.fromString("00000000-0000-7000-8000-000000000009");
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        String requestId = "market:req-unknown-integrity";
        DataIntegrityViolationException unknown = new DataIntegrityViolationException("unknown market constraint");

        when(marketOrderMapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId)).thenReturn(null);
        when(marketListingMapper.selectByIdForUpdate(listingId)).thenReturn(MarketListingDataObject.from(activeListing(listingId)));
        when(marketOrderMapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId)).thenReturn(null);
        when(marketOrderMapper.insert(any(MarketOrderDataObject.class))).thenThrow(unknown);

        assertThatThrownBy(() -> service.createOrder(requestId, buyerUserId, listingId, 1, null))
                .isSameAs(unknown);
        verify(marketWalletActionService, never()).enqueueEscrow(any(), any(), any(), anyLong());
    }

    private MarketOrderApplicationService service() {
        return new MarketOrderApplicationService(
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
        return order(UUID.fromString("00000000-0000-7000-8000-000000000101"))
                .requestId(requestId)
                .listingId(listingId)
                .goodsType("VIRTUAL")
                .sellerUserId(UUID.fromString("00000000-0000-7000-8000-000000000008"))
                .buyerUserId(buyerUserId)
                .quantity(quantity)
                .unitPriceSnapshot(1_200L)
                .totalAmount(1_200L)
                .deliveryModeSnapshot("MANUAL")
                .listingTitleSnapshot("邀请码")
                .status("ESCROWED")
                .escrowTxnId(UUID.fromString("00000000-0000-7000-8000-000000000123"))
                .build();
    }

    private MarketOrder existingPhysicalOrder(
            String requestId,
            UUID listingId,
            UUID buyerUserId,
            UUID addressIdSnapshot
    ) {
        return order(existingOrder(requestId, listingId, buyerUserId, 1))
                .goodsType("PHYSICAL")
                .addressIdSnapshot(addressIdSnapshot)
                .build();
    }

    private static UUID uuid(int value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }
}
