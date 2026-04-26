package com.nowcoder.community.market.service;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.market.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.CreateMarketDisputeRequest;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.dto.CreateMarketOrderRequest;
import com.nowcoder.community.market.dto.DeliverMarketOrderRequest;
import com.nowcoder.community.market.dto.SellerDisputeDecisionRequest;
import com.nowcoder.community.market.dto.ShipMarketOrderRequest;
import com.nowcoder.community.market.dto.UpdateMarketAddressRequest;
import com.nowcoder.community.market.dto.UpdateMarketListingRequest;
import com.nowcoder.community.market.exception.MarketErrorCode;
import com.nowcoder.community.market.model.MarketAddressView;
import com.nowcoder.community.market.model.MarketDisputeResult;
import com.nowcoder.community.market.model.MarketInventoryUnitView;
import com.nowcoder.community.market.model.MarketListingDetailView;
import com.nowcoder.community.market.model.MarketListingResult;
import com.nowcoder.community.market.model.MarketOrderDetailView;
import com.nowcoder.community.market.model.MarketOrderResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MarketApplicationService {

    private final MarketListingService marketListingService;
    private final MarketInventoryService marketInventoryService;
    private final MarketQueryService marketQueryService;
    private final MarketOrderService marketOrderService;
    private final MarketDisputeService marketDisputeService;
    private final MarketAddressService marketAddressService;
    private final IdempotencyGuard idempotencyGuard;

    public MarketApplicationService(
            MarketListingService marketListingService,
            MarketInventoryService marketInventoryService,
            MarketQueryService marketQueryService,
            MarketOrderService marketOrderService,
            MarketDisputeService marketDisputeService,
            MarketAddressService marketAddressService,
            IdempotencyGuard idempotencyGuard
    ) {
        this.marketListingService = marketListingService;
        this.marketInventoryService = marketInventoryService;
        this.marketQueryService = marketQueryService;
        this.marketOrderService = marketOrderService;
        this.marketDisputeService = marketDisputeService;
        this.marketAddressService = marketAddressService;
        this.idempotencyGuard = idempotencyGuard;
    }

    public List<MarketListingResult> listPublicListings() {
        return marketQueryService.listPublicListings();
    }

    public MarketListingDetailView getListingDetail(UUID listingId) {
        return marketQueryService.getListingDetail(listingId);
    }

    public List<MarketListingResult> listSellerListings(UUID sellerUserId) {
        return marketQueryService.listSellerListings(sellerUserId);
    }

    public MarketListingResult createListing(UUID sellerUserId, CreateMarketListingRequest request) {
        return marketListingService.createListing(sellerUserId, request, request.getInventory());
    }

    public MarketListingResult updateListing(UUID sellerUserId, UUID listingId, UpdateMarketListingRequest request) {
        return marketListingService.updateListing(sellerUserId, listingId, request);
    }

    public MarketListingResult pauseListing(UUID sellerUserId, UUID listingId) {
        return marketListingService.pauseListing(sellerUserId, listingId);
    }

    public MarketListingResult resumeListing(UUID sellerUserId, UUID listingId) {
        return marketListingService.resumeListing(sellerUserId, listingId);
    }

    public MarketListingResult closeListing(UUID sellerUserId, UUID listingId) {
        return marketListingService.closeListing(sellerUserId, listingId);
    }

    public List<MarketInventoryUnitView> listInventory(UUID listingId, UUID sellerUserId) {
        return marketInventoryService.listInventory(listingId, sellerUserId);
    }

    public void addInventory(UUID listingId, UUID sellerUserId, AddMarketInventoryBatchRequest request) {
        marketInventoryService.appendInventory(listingId, sellerUserId, request);
    }

    public void invalidateInventory(UUID inventoryUnitId, UUID sellerUserId) {
        marketInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
    }

    public List<MarketAddressView> listAddresses(UUID userId) {
        return marketAddressService.listAddresses(userId);
    }

    public MarketAddressView createAddress(UUID userId, CreateMarketAddressRequest request) {
        return marketAddressService.createAddress(userId, request);
    }

    public MarketAddressView updateAddress(UUID userId, UUID addressId, UpdateMarketAddressRequest request) {
        return marketAddressService.updateAddress(userId, addressId, request);
    }

    public void deleteAddress(UUID userId, UUID addressId) {
        marketAddressService.deleteAddress(userId, addressId);
    }

    public MarketOrderResult createOrder(UUID buyerUserId, CreateMarketOrderRequest request) {
        return createOrder(buyerUserId, request, null);
    }

    public MarketOrderResult createOrder(UUID buyerUserId, CreateMarketOrderRequest request, String idempotencyKey) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(idempotencyKey, request.getRequestId());
        String requestHash = RequestFingerprint.sha256(
                "market:create_order|listingId=" + request.getListingId()
                        + "|quantity=" + request.getQuantity()
                        + "|addressId=" + (request.getAddressId() == null ? "" : request.getAddressId())
        );
        return idempotencyGuard.executeRequired(
                "market:create_order",
                buyerUserId,
                effective.value(),
                requestHash,
                MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                MarketOrderResult.class,
                () -> marketOrderService.createOrder(
                        effective.value(),
                        buyerUserId,
                        request.getListingId(),
                        request.getQuantity(),
                        request.getAddressId()
                )
        );
    }

    public List<MarketOrderResult> listBuyingOrders(UUID buyerUserId) {
        return marketQueryService.listBuyingOrders(buyerUserId);
    }

    public List<MarketOrderResult> listSellingOrders(UUID sellerUserId) {
        return marketQueryService.listSellingOrders(sellerUserId);
    }

    public MarketOrderDetailView getOrderDetail(UUID orderId, UUID actorUserId) {
        return marketQueryService.getOrderDetail(orderId, actorUserId);
    }

    public MarketOrderResult deliverOrder(UUID orderId, UUID sellerUserId, DeliverMarketOrderRequest request) {
        return marketOrderService.deliverVirtualOrder(orderId, sellerUserId, request.getDeliveryContent());
    }

    public MarketOrderResult shipOrder(UUID orderId, UUID sellerUserId, ShipMarketOrderRequest request) {
        return marketOrderService.shipPhysicalOrder(
                orderId,
                sellerUserId,
                request.getCarrierName(),
                request.getTrackingNo(),
                request.getShippingRemark()
        );
    }

    public MarketOrderResult confirmOrder(UUID orderId, UUID buyerUserId) {
        return marketOrderService.confirmOrder(orderId, buyerUserId);
    }

    public MarketOrderResult cancelOrder(UUID orderId, UUID buyerUserId) {
        return marketOrderService.cancelOrder(orderId, buyerUserId);
    }

    public MarketDisputeResult openDispute(UUID orderId, UUID buyerUserId, CreateMarketDisputeRequest request) {
        return marketDisputeService.openDispute(orderId, buyerUserId, request.getReason(), request.getBuyerNote());
    }

    public MarketDisputeResult sellerAccept(UUID disputeId, UUID sellerUserId, SellerDisputeDecisionRequest request) {
        return marketDisputeService.sellerAcceptRefund(disputeId, sellerUserId, request.getNote());
    }

    public MarketDisputeResult sellerReject(UUID disputeId, UUID sellerUserId, SellerDisputeDecisionRequest request) {
        return marketDisputeService.sellerRejectRefund(disputeId, sellerUserId, request.getNote());
    }
}
