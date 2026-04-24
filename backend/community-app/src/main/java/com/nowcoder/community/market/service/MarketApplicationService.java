package com.nowcoder.community.market.service;

import com.nowcoder.community.market.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.CreateMarketDisputeRequest;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.dto.CreateMarketOrderRequest;
import com.nowcoder.community.market.dto.DeliverMarketOrderRequest;
import com.nowcoder.community.market.dto.MarketAddressResponse;
import com.nowcoder.community.market.dto.MarketDisputeResponse;
import com.nowcoder.community.market.dto.MarketInventoryUnitResponse;
import com.nowcoder.community.market.dto.MarketListingDetailResponse;
import com.nowcoder.community.market.dto.MarketListingResponse;
import com.nowcoder.community.market.dto.MarketOrderDetailResponse;
import com.nowcoder.community.market.dto.MarketOrderResponse;
import com.nowcoder.community.market.dto.SellerDisputeDecisionRequest;
import com.nowcoder.community.market.dto.ShipMarketOrderRequest;
import com.nowcoder.community.market.dto.UpdateMarketAddressRequest;
import com.nowcoder.community.market.dto.UpdateMarketListingRequest;
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

    public MarketApplicationService(
            MarketListingService marketListingService,
            MarketInventoryService marketInventoryService,
            MarketQueryService marketQueryService,
            MarketOrderService marketOrderService,
            MarketDisputeService marketDisputeService,
            MarketAddressService marketAddressService
    ) {
        this.marketListingService = marketListingService;
        this.marketInventoryService = marketInventoryService;
        this.marketQueryService = marketQueryService;
        this.marketOrderService = marketOrderService;
        this.marketDisputeService = marketDisputeService;
        this.marketAddressService = marketAddressService;
    }

    public List<MarketListingResponse> listPublicListings() {
        return marketQueryService.listPublicListings();
    }

    public MarketListingDetailResponse getListingDetail(UUID listingId) {
        return marketQueryService.getListingDetail(listingId);
    }

    public List<MarketListingResponse> listSellerListings(UUID sellerUserId) {
        return marketQueryService.listSellerListings(sellerUserId);
    }

    public MarketListingResponse createListing(UUID sellerUserId, CreateMarketListingRequest request) {
        return marketListingService.createListing(sellerUserId, request, request.getInventory());
    }

    public MarketListingResponse updateListing(UUID sellerUserId, UUID listingId, UpdateMarketListingRequest request) {
        return marketListingService.updateListing(sellerUserId, listingId, request);
    }

    public MarketListingResponse pauseListing(UUID sellerUserId, UUID listingId) {
        return marketListingService.pauseListing(sellerUserId, listingId);
    }

    public MarketListingResponse resumeListing(UUID sellerUserId, UUID listingId) {
        return marketListingService.resumeListing(sellerUserId, listingId);
    }

    public MarketListingResponse closeListing(UUID sellerUserId, UUID listingId) {
        return marketListingService.closeListing(sellerUserId, listingId);
    }

    public List<MarketInventoryUnitResponse> listInventory(UUID listingId, UUID sellerUserId) {
        return marketInventoryService.listInventory(listingId, sellerUserId);
    }

    public void addInventory(UUID listingId, UUID sellerUserId, AddMarketInventoryBatchRequest request) {
        marketInventoryService.appendInventory(listingId, sellerUserId, request);
    }

    public void invalidateInventory(UUID inventoryUnitId, UUID sellerUserId) {
        marketInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
    }

    public List<MarketAddressResponse> listAddresses(UUID userId) {
        return marketAddressService.listAddresses(userId);
    }

    public MarketAddressResponse createAddress(UUID userId, CreateMarketAddressRequest request) {
        return marketAddressService.createAddress(userId, request);
    }

    public MarketAddressResponse updateAddress(UUID userId, UUID addressId, UpdateMarketAddressRequest request) {
        return marketAddressService.updateAddress(userId, addressId, request);
    }

    public void deleteAddress(UUID userId, UUID addressId) {
        marketAddressService.deleteAddress(userId, addressId);
    }

    public MarketOrderResponse createOrder(UUID buyerUserId, CreateMarketOrderRequest request) {
        return marketOrderService.createOrder(
                request.getRequestId(),
                buyerUserId,
                request.getListingId(),
                request.getQuantity(),
                request.getAddressId()
        );
    }

    public List<MarketOrderResponse> listBuyingOrders(UUID buyerUserId) {
        return marketQueryService.listBuyingOrders(buyerUserId);
    }

    public List<MarketOrderResponse> listSellingOrders(UUID sellerUserId) {
        return marketQueryService.listSellingOrders(sellerUserId);
    }

    public MarketOrderDetailResponse getOrderDetail(UUID orderId, UUID actorUserId) {
        return marketQueryService.getOrderDetail(orderId, actorUserId);
    }

    public MarketOrderResponse deliverOrder(UUID orderId, UUID sellerUserId, DeliverMarketOrderRequest request) {
        return marketOrderService.deliverVirtualOrder(orderId, sellerUserId, request.getDeliveryContent());
    }

    public MarketOrderResponse shipOrder(UUID orderId, UUID sellerUserId, ShipMarketOrderRequest request) {
        return marketOrderService.shipPhysicalOrder(
                orderId,
                sellerUserId,
                request.getCarrierName(),
                request.getTrackingNo(),
                request.getShippingRemark()
        );
    }

    public MarketOrderResponse confirmOrder(UUID orderId, UUID buyerUserId) {
        return marketOrderService.confirmOrder(orderId, buyerUserId);
    }

    public MarketOrderResponse cancelOrder(UUID orderId, UUID buyerUserId) {
        return marketOrderService.cancelOrder(orderId, buyerUserId);
    }

    public MarketDisputeResponse openDispute(UUID orderId, UUID buyerUserId, CreateMarketDisputeRequest request) {
        return marketDisputeService.openDispute(orderId, buyerUserId, request.getReason(), request.getBuyerNote());
    }

    public MarketDisputeResponse sellerAccept(UUID disputeId, UUID sellerUserId, SellerDisputeDecisionRequest request) {
        return marketDisputeService.sellerAcceptRefund(disputeId, sellerUserId, request.getNote());
    }

    public MarketDisputeResponse sellerReject(UUID disputeId, UUID sellerUserId, SellerDisputeDecisionRequest request) {
        return marketDisputeService.sellerRejectRefund(disputeId, sellerUserId, request.getNote());
    }
}
