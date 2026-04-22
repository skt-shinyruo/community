package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
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
import com.nowcoder.community.market.service.MarketAddressService;
import com.nowcoder.community.market.service.MarketDisputeService;
import com.nowcoder.community.market.service.MarketInventoryService;
import com.nowcoder.community.market.service.MarketListingService;
import com.nowcoder.community.market.service.MarketOrderService;
import com.nowcoder.community.market.service.MarketQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketListingService marketListingService;
    private final MarketInventoryService marketInventoryService;
    private final MarketQueryService marketQueryService;
    private final MarketOrderService marketOrderService;
    private final MarketDisputeService marketDisputeService;
    private final MarketAddressService marketAddressService;

    public MarketController(MarketListingService marketListingService,
                            MarketInventoryService marketInventoryService,
                            MarketQueryService marketQueryService,
                            MarketOrderService marketOrderService,
                            MarketDisputeService marketDisputeService,
                            MarketAddressService marketAddressService) {
        this.marketListingService = marketListingService;
        this.marketInventoryService = marketInventoryService;
        this.marketQueryService = marketQueryService;
        this.marketOrderService = marketOrderService;
        this.marketDisputeService = marketDisputeService;
        this.marketAddressService = marketAddressService;
    }

    @GetMapping("/listings")
    public Result<List<MarketListingResponse>> listPublicListings() {
        return Result.ok(marketQueryService.listPublicListings());
    }

    @GetMapping("/listings/{listingId}")
    public Result<MarketListingDetailResponse> getListingDetail(@PathVariable UUID listingId) {
        return Result.ok(marketQueryService.getListingDetail(listingId));
    }

    @GetMapping("/my-listings")
    public Result<List<MarketListingResponse>> listSellerListings(Authentication authentication) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.listSellerListings(sellerUserId));
    }

    @PostMapping("/listings")
    public Result<MarketListingResponse> createListing(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.createListing(sellerUserId, request, request.getInventory()));
    }

    @PutMapping("/listings/{listingId}")
    public Result<MarketListingResponse> updateListing(Authentication authentication,
                                                       @PathVariable UUID listingId,
                                                       @RequestBody @Valid UpdateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.updateListing(sellerUserId, listingId, request));
    }

    @PostMapping("/listings/{listingId}/pause")
    public Result<MarketListingResponse> pauseListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.pauseListing(sellerUserId, listingId));
    }

    @PostMapping("/listings/{listingId}/resume")
    public Result<MarketListingResponse> resumeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.resumeListing(sellerUserId, listingId));
    }

    @PostMapping("/listings/{listingId}/close")
    public Result<MarketListingResponse> closeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.closeListing(sellerUserId, listingId));
    }

    @GetMapping("/listings/{listingId}/inventory")
    public Result<List<MarketInventoryUnitResponse>> listInventory(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketInventoryService.listInventory(listingId, sellerUserId));
    }

    @PostMapping("/listings/{listingId}/inventory")
    public Result<Void> addInventory(Authentication authentication,
                                     @PathVariable UUID listingId,
                                     @RequestBody @Valid AddMarketInventoryBatchRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        marketInventoryService.appendInventory(listingId, sellerUserId, request);
        return Result.ok();
    }

    @PostMapping("/inventory/{inventoryUnitId}/invalidate")
    public Result<Void> invalidateInventory(Authentication authentication, @PathVariable UUID inventoryUnitId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        marketInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
        return Result.ok();
    }

    @GetMapping("/addresses")
    public Result<List<MarketAddressResponse>> listAddresses(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketAddressService.listAddresses(userId));
    }

    @PostMapping("/addresses")
    public Result<MarketAddressResponse> createAddress(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketAddressService.createAddress(userId, request));
    }

    @PutMapping("/addresses/{addressId}")
    public Result<MarketAddressResponse> updateAddress(Authentication authentication,
                                                       @PathVariable UUID addressId,
                                                       @RequestBody @Valid UpdateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketAddressService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result<Void> deleteAddress(Authentication authentication, @PathVariable UUID addressId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        marketAddressService.deleteAddress(userId, addressId);
        return Result.ok();
    }

    @PostMapping("/orders")
    public Result<MarketOrderResponse> createOrder(Authentication authentication,
                                                   @RequestBody @Valid CreateMarketOrderRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.createOrder(
                request.getRequestId(),
                buyerUserId,
                request.getListingId(),
                request.getQuantity(),
                request.getAddressId()
        ));
    }

    @GetMapping("/orders/buying")
    public Result<List<MarketOrderResponse>> listBuyingOrders(Authentication authentication) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.listBuyingOrders(buyerUserId));
    }

    @GetMapping("/orders/selling")
    public Result<List<MarketOrderResponse>> listSellingOrders(Authentication authentication) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.listSellingOrders(sellerUserId));
    }

    @GetMapping("/orders/{orderId}")
    public Result<MarketOrderDetailResponse> getOrderDetail(Authentication authentication, @PathVariable UUID orderId) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.getOrderDetail(orderId, actorUserId));
    }

    @PostMapping("/orders/{orderId}/deliver")
    public Result<MarketOrderResponse> deliverOrder(Authentication authentication,
                                                    @PathVariable UUID orderId,
                                                    @RequestBody @Valid DeliverMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.deliverVirtualOrder(orderId, sellerUserId, request.getDeliveryContent()));
    }

    @PostMapping("/orders/{orderId}/ship")
    public Result<MarketOrderResponse> shipOrder(Authentication authentication,
                                                 @PathVariable UUID orderId,
                                                 @RequestBody @Valid ShipMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.shipPhysicalOrder(
                orderId,
                sellerUserId,
                request.getCarrierName(),
                request.getTrackingNo(),
                request.getShippingRemark()
        ));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public Result<MarketOrderResponse> confirmOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.confirmOrder(orderId, buyerUserId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Result<MarketOrderResponse> cancelOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.cancelOrder(orderId, buyerUserId));
    }

    @PostMapping("/orders/{orderId}/disputes")
    public Result<MarketDisputeResponse> openDispute(Authentication authentication,
                                                     @PathVariable UUID orderId,
                                                     @RequestBody @Valid CreateMarketDisputeRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeService.openDispute(orderId, buyerUserId, request.getReason(), request.getBuyerNote()));
    }

    @PostMapping("/disputes/{disputeId}/seller-accept")
    public Result<MarketDisputeResponse> sellerAccept(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeService.sellerAcceptRefund(disputeId, sellerUserId, request.getNote()));
    }

    @PostMapping("/disputes/{disputeId}/seller-reject")
    public Result<MarketDisputeResponse> sellerReject(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeService.sellerRejectRefund(disputeId, sellerUserId, request.getNote()));
    }
}
