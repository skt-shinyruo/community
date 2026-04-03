package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualDisputeRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.CreateVirtualOrderRequest;
import com.nowcoder.community.market.dto.DeliverVirtualOrderRequest;
import com.nowcoder.community.market.dto.SellerDisputeDecisionRequest;
import com.nowcoder.community.market.dto.UpdateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualDisputeResponse;
import com.nowcoder.community.market.dto.VirtualInventoryUnitResponse;
import com.nowcoder.community.market.dto.VirtualListingDetailResponse;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.dto.VirtualOrderDetailResponse;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.market.service.VirtualDisputeService;
import com.nowcoder.community.market.service.VirtualInventoryService;
import com.nowcoder.community.market.service.VirtualListingService;
import com.nowcoder.community.market.service.VirtualMarketQueryService;
import com.nowcoder.community.market.service.VirtualOrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/virtual")
public class VirtualMarketController {

    private final VirtualListingService virtualListingService;
    private final VirtualInventoryService virtualInventoryService;
    private final VirtualMarketQueryService virtualMarketQueryService;
    private final VirtualOrderService virtualOrderService;
    private final VirtualDisputeService virtualDisputeService;

    public VirtualMarketController(VirtualListingService virtualListingService,
                                   VirtualInventoryService virtualInventoryService,
                                   VirtualMarketQueryService virtualMarketQueryService,
                                   VirtualOrderService virtualOrderService,
                                   VirtualDisputeService virtualDisputeService) {
        this.virtualListingService = virtualListingService;
        this.virtualInventoryService = virtualInventoryService;
        this.virtualMarketQueryService = virtualMarketQueryService;
        this.virtualOrderService = virtualOrderService;
        this.virtualDisputeService = virtualDisputeService;
    }

    @GetMapping("/listings")
    public Result<List<VirtualListingResponse>> listPublicListings() {
        return Result.ok(virtualMarketQueryService.listPublicListings());
    }

    @GetMapping("/listings/{listingId}")
    public Result<VirtualListingDetailResponse> getListingDetail(@PathVariable long listingId) {
        return Result.ok(virtualMarketQueryService.getListingDetail(listingId));
    }

    @GetMapping("/my-listings")
    public Result<List<VirtualListingResponse>> listSellerListings(Authentication authentication) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualMarketQueryService.listSellerListings(sellerUserId));
    }

    @PostMapping("/listings")
    public Result<VirtualListingResponse> createListing(Authentication authentication,
                                                        @RequestBody @Valid CreateVirtualListingRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualListingService.createListing(sellerUserId, request, request.getInventory()));
    }

    @PutMapping("/listings/{listingId}")
    public Result<VirtualListingResponse> updateListing(Authentication authentication,
                                                        @PathVariable long listingId,
                                                        @RequestBody @Valid UpdateVirtualListingRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualListingService.updateListing(sellerUserId, listingId, request));
    }

    @PostMapping("/listings/{listingId}/pause")
    public Result<VirtualListingResponse> pauseListing(Authentication authentication, @PathVariable long listingId) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualListingService.pauseListing(sellerUserId, listingId));
    }

    @PostMapping("/listings/{listingId}/resume")
    public Result<VirtualListingResponse> resumeListing(Authentication authentication, @PathVariable long listingId) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualListingService.resumeListing(sellerUserId, listingId));
    }

    @PostMapping("/listings/{listingId}/close")
    public Result<VirtualListingResponse> closeListing(Authentication authentication, @PathVariable long listingId) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualListingService.closeListing(sellerUserId, listingId));
    }

    @GetMapping("/listings/{listingId}/inventory")
    public Result<List<VirtualInventoryUnitResponse>> listInventory(Authentication authentication, @PathVariable long listingId) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualInventoryService.listInventory(listingId, sellerUserId));
    }

    @PostMapping("/listings/{listingId}/inventory")
    public Result<Void> addInventory(Authentication authentication,
                                     @PathVariable long listingId,
                                     @RequestBody @Valid AddVirtualInventoryBatchRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        virtualInventoryService.appendInventory(listingId, sellerUserId, request);
        return Result.ok();
    }

    @PostMapping("/inventory/{inventoryUnitId}/invalidate")
    public Result<Void> invalidateInventory(Authentication authentication, @PathVariable long inventoryUnitId) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        virtualInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
        return Result.ok();
    }

    @PostMapping("/orders")
    public Result<VirtualOrderResponse> createOrder(Authentication authentication,
                                                    @RequestBody @Valid CreateVirtualOrderRequest request) {
        int buyerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualOrderService.createOrder(
                request.getRequestId(),
                buyerUserId,
                request.getListingId(),
                request.getQuantity()
        ));
    }

    @GetMapping("/orders/buying")
    public Result<List<VirtualOrderResponse>> listBuyingOrders(Authentication authentication) {
        int buyerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualMarketQueryService.listBuyingOrders(buyerUserId));
    }

    @GetMapping("/orders/selling")
    public Result<List<VirtualOrderResponse>> listSellingOrders(Authentication authentication) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualMarketQueryService.listSellingOrders(sellerUserId));
    }

    @GetMapping("/orders/{orderId}")
    public Result<VirtualOrderDetailResponse> getOrderDetail(Authentication authentication, @PathVariable long orderId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualMarketQueryService.getOrderDetail(orderId, actorUserId));
    }

    @PostMapping("/orders/{orderId}/deliver")
    public Result<VirtualOrderResponse> deliverOrder(Authentication authentication,
                                                     @PathVariable long orderId,
                                                     @RequestBody @Valid DeliverVirtualOrderRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualOrderService.deliverOrder(orderId, sellerUserId, request.getDeliveryContent()));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public Result<VirtualOrderResponse> confirmOrder(Authentication authentication, @PathVariable long orderId) {
        int buyerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualOrderService.confirmOrder(orderId, buyerUserId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Result<VirtualOrderResponse> cancelOrder(Authentication authentication, @PathVariable long orderId) {
        int buyerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualOrderService.cancelOrder(orderId, buyerUserId));
    }

    @PostMapping("/orders/{orderId}/disputes")
    public Result<VirtualDisputeResponse> openDispute(Authentication authentication,
                                                      @PathVariable long orderId,
                                                      @RequestBody @Valid CreateVirtualDisputeRequest request) {
        int buyerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualDisputeService.openDispute(orderId, buyerUserId, request.getReason(), request.getBuyerNote()));
    }

    @PostMapping("/disputes/{disputeId}/seller-accept")
    public Result<VirtualDisputeResponse> sellerAccept(Authentication authentication,
                                                       @PathVariable long disputeId,
                                                       @RequestBody @Valid SellerDisputeDecisionRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualDisputeService.sellerAcceptRefund(disputeId, sellerUserId, request.getNote()));
    }

    @PostMapping("/disputes/{disputeId}/seller-reject")
    public Result<VirtualDisputeResponse> sellerReject(Authentication authentication,
                                                       @PathVariable long disputeId,
                                                       @RequestBody @Valid SellerDisputeDecisionRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualDisputeService.sellerRejectRefund(disputeId, sellerUserId, request.getNote()));
    }
}
