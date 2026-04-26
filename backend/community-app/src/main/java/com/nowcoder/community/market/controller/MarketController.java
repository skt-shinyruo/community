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
import com.nowcoder.community.market.model.MarketAddressView;
import com.nowcoder.community.market.model.MarketDisputeResult;
import com.nowcoder.community.market.model.MarketInventoryUnitView;
import com.nowcoder.community.market.model.MarketListingResult;
import com.nowcoder.community.market.model.MarketOrderResult;
import com.nowcoder.community.market.service.MarketApplicationService;
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

    private final MarketApplicationService marketApplicationService;

    public MarketController(MarketApplicationService marketApplicationService) {
        this.marketApplicationService = marketApplicationService;
    }

    private static List<MarketOrderResponse> toOrderResponses(List<MarketOrderResult> orders) {
        return orders.stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    private static List<MarketListingResponse> toListingResponses(List<MarketListingResult> listings) {
        return listings.stream()
                .map(MarketListingResponse::from)
                .toList();
    }

    private static List<MarketInventoryUnitResponse> toInventoryResponses(List<MarketInventoryUnitView> units) {
        return units.stream()
                .map(MarketInventoryUnitResponse::from)
                .toList();
    }

    private static List<MarketAddressResponse> toAddressResponses(List<MarketAddressView> addresses) {
        return addresses.stream()
                .map(MarketAddressResponse::from)
                .toList();
    }

    @GetMapping("/listings")
    public Result<List<MarketListingResponse>> listPublicListings() {
        return Result.ok(toListingResponses(marketApplicationService.listPublicListings()));
    }

    @GetMapping("/listings/{listingId}")
    public Result<MarketListingDetailResponse> getListingDetail(@PathVariable UUID listingId) {
        return Result.ok(MarketListingDetailResponse.from(marketApplicationService.getListingDetail(listingId)));
    }

    @GetMapping("/my-listings")
    public Result<List<MarketListingResponse>> listSellerListings(Authentication authentication) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toListingResponses(marketApplicationService.listSellerListings(sellerUserId)));
    }

    @PostMapping("/listings")
    public Result<MarketListingResponse> createListing(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketApplicationService.createListing(sellerUserId, request)));
    }

    @PutMapping("/listings/{listingId}")
    public Result<MarketListingResponse> updateListing(Authentication authentication,
                                                       @PathVariable UUID listingId,
                                                       @RequestBody @Valid UpdateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketApplicationService.updateListing(sellerUserId, listingId, request)));
    }

    @PostMapping("/listings/{listingId}/pause")
    public Result<MarketListingResponse> pauseListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketApplicationService.pauseListing(sellerUserId, listingId)));
    }

    @PostMapping("/listings/{listingId}/resume")
    public Result<MarketListingResponse> resumeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketApplicationService.resumeListing(sellerUserId, listingId)));
    }

    @PostMapping("/listings/{listingId}/close")
    public Result<MarketListingResponse> closeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketApplicationService.closeListing(sellerUserId, listingId)));
    }

    @GetMapping("/listings/{listingId}/inventory")
    public Result<List<MarketInventoryUnitResponse>> listInventory(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toInventoryResponses(marketApplicationService.listInventory(listingId, sellerUserId)));
    }

    @PostMapping("/listings/{listingId}/inventory")
    public Result<Void> addInventory(Authentication authentication,
                                     @PathVariable UUID listingId,
                                     @RequestBody @Valid AddMarketInventoryBatchRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        marketApplicationService.addInventory(listingId, sellerUserId, request);
        return Result.ok();
    }

    @PostMapping("/inventory/{inventoryUnitId}/invalidate")
    public Result<Void> invalidateInventory(Authentication authentication, @PathVariable UUID inventoryUnitId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        marketApplicationService.invalidateInventory(inventoryUnitId, sellerUserId);
        return Result.ok();
    }

    @GetMapping("/addresses")
    public Result<List<MarketAddressResponse>> listAddresses(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toAddressResponses(marketApplicationService.listAddresses(userId)));
    }

    @PostMapping("/addresses")
    public Result<MarketAddressResponse> createAddress(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketAddressResponse.from(marketApplicationService.createAddress(userId, request)));
    }

    @PutMapping("/addresses/{addressId}")
    public Result<MarketAddressResponse> updateAddress(Authentication authentication,
                                                       @PathVariable UUID addressId,
                                                       @RequestBody @Valid UpdateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketAddressResponse.from(marketApplicationService.updateAddress(userId, addressId, request)));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result<Void> deleteAddress(Authentication authentication, @PathVariable UUID addressId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        marketApplicationService.deleteAddress(userId, addressId);
        return Result.ok();
    }

    @PostMapping("/orders")
    public Result<MarketOrderResponse> createOrder(Authentication authentication,
                                                   @RequestBody @Valid CreateMarketOrderRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketApplicationService.createOrder(buyerUserId, request)));
    }

    @GetMapping("/orders/buying")
    public Result<List<MarketOrderResponse>> listBuyingOrders(Authentication authentication) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toOrderResponses(marketApplicationService.listBuyingOrders(buyerUserId)));
    }

    @GetMapping("/orders/selling")
    public Result<List<MarketOrderResponse>> listSellingOrders(Authentication authentication) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toOrderResponses(marketApplicationService.listSellingOrders(sellerUserId)));
    }

    @GetMapping("/orders/{orderId}")
    public Result<MarketOrderDetailResponse> getOrderDetail(Authentication authentication, @PathVariable UUID orderId) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderDetailResponse.from(marketApplicationService.getOrderDetail(orderId, actorUserId)));
    }

    @PostMapping("/orders/{orderId}/deliver")
    public Result<MarketOrderResponse> deliverOrder(Authentication authentication,
                                                    @PathVariable UUID orderId,
                                                    @RequestBody @Valid DeliverMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketApplicationService.deliverOrder(orderId, sellerUserId, request)));
    }

    @PostMapping("/orders/{orderId}/ship")
    public Result<MarketOrderResponse> shipOrder(Authentication authentication,
                                                 @PathVariable UUID orderId,
                                                 @RequestBody @Valid ShipMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketApplicationService.shipOrder(orderId, sellerUserId, request)));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public Result<MarketOrderResponse> confirmOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketApplicationService.confirmOrder(orderId, buyerUserId)));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Result<MarketOrderResponse> cancelOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketApplicationService.cancelOrder(orderId, buyerUserId)));
    }

    @PostMapping("/orders/{orderId}/disputes")
    public Result<MarketDisputeResponse> openDispute(Authentication authentication,
                                                     @PathVariable UUID orderId,
                                                     @RequestBody @Valid CreateMarketDisputeRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        MarketDisputeResult dispute = marketApplicationService.openDispute(orderId, buyerUserId, request);
        return Result.ok(MarketDisputeResponse.from(dispute));
    }

    @PostMapping("/disputes/{disputeId}/seller-accept")
    public Result<MarketDisputeResponse> sellerAccept(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketDisputeResponse.from(marketApplicationService.sellerAccept(disputeId, sellerUserId, request)));
    }

    @PostMapping("/disputes/{disputeId}/seller-reject")
    public Result<MarketDisputeResponse> sellerReject(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketDisputeResponse.from(marketApplicationService.sellerReject(disputeId, sellerUserId, request)));
    }
}
