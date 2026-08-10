package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.application.MarketAddressApplicationService;
import com.nowcoder.community.market.application.MarketDisputeApplicationService;
import com.nowcoder.community.market.application.MarketInventoryApplicationService;
import com.nowcoder.community.market.application.MarketListingApplicationService;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.market.application.MarketOrderApplicationService.CreateOrderCommand;
import com.nowcoder.community.market.application.MarketQueryApplicationService;
import com.nowcoder.community.market.application.MarketAddressApplicationService.CreateMarketAddressCommand;
import com.nowcoder.community.market.application.MarketAddressApplicationService.UpdateMarketAddressCommand;
import com.nowcoder.community.market.application.MarketListingApplicationService.CreateMarketListingCommand;
import com.nowcoder.community.market.application.MarketListingApplicationService.UpdateMarketListingCommand;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.result.MarketAddressResult;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.application.MarketInventoryApplicationService.MarketInventoryUnitResult;
import com.nowcoder.community.market.application.result.MarketListingDetailResult;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.application.result.MarketOrderDetailResult;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.market.application.result.MarketPageResult;
import com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketDisputeRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketOrderRequest;
import com.nowcoder.community.market.controller.dto.DeliverMarketOrderRequest;
import com.nowcoder.community.market.controller.dto.SellerDisputeDecisionRequest;
import com.nowcoder.community.market.controller.dto.ShipMarketOrderRequest;
import com.nowcoder.community.market.controller.dto.UpdateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.UpdateMarketListingRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketListingApplicationService marketListingService;
    private final MarketInventoryApplicationService marketInventoryService;
    private final MarketQueryApplicationService marketQueryService;
    private final MarketOrderApplicationService marketOrderService;
    private final MarketDisputeApplicationService marketDisputeService;
    private final MarketAddressApplicationService marketAddressService;

    public MarketController(
            MarketListingApplicationService marketListingService,
            MarketInventoryApplicationService marketInventoryService,
            MarketQueryApplicationService marketQueryService,
            MarketOrderApplicationService marketOrderService,
            MarketDisputeApplicationService marketDisputeService,
            MarketAddressApplicationService marketAddressService
    ) {
        this.marketListingService = marketListingService;
        this.marketInventoryService = marketInventoryService;
        this.marketQueryService = marketQueryService;
        this.marketOrderService = marketOrderService;
        this.marketDisputeService = marketDisputeService;
        this.marketAddressService = marketAddressService;
    }

    private static AddMarketInventoryBatchCommand toCommand(
            UUID listingId,
            UUID sellerUserId,
            AddMarketInventoryBatchRequest request
    ) {
        if (request == null) {
            return null;
        }
        return new AddMarketInventoryBatchCommand(listingId, sellerUserId, request.payloadType(), request.payloads());
    }

    @GetMapping("/listings")
    public Result<MarketPageResult<MarketListingResult>> listPublicListings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(marketQueryService.listPublicListings(page, size));
    }

    @GetMapping("/listings/{listingId}")
    public Result<MarketListingDetailResult> getListingDetail(
            @PathVariable UUID listingId
    ) {
        return Result.ok(marketQueryService.getListingDetail(listingId));
    }

    @GetMapping("/my-listings")
    public Result<MarketPageResult<MarketListingResult>> listSellerListings(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.listSellerListings(sellerUserId, page, size));
    }

    @PostMapping("/listings")
    public Result<MarketListingResult> createListing(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.createListing(new CreateMarketListingCommand(
                sellerUserId,
                request.goodsType(),
                request.title(),
                request.description(),
                request.unitPrice(),
                request.deliveryMode(),
                request.stockMode(),
                request.stockTotal(),
                request.minPurchaseQuantity(),
                request.maxPurchaseQuantity(),
                toCommand(null, sellerUserId, request.inventory())
        )));
    }

    @PutMapping("/listings/{listingId}")
    public Result<MarketListingResult> updateListing(Authentication authentication,
                                                       @PathVariable UUID listingId,
                                                       @RequestBody @Valid UpdateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.updateListing(new UpdateMarketListingCommand(
                sellerUserId,
                listingId,
                request.title(),
                request.description(),
                request.unitPrice(),
                request.minPurchaseQuantity(),
                request.maxPurchaseQuantity()
        )));
    }

    @PostMapping("/listings/{listingId}/pause")
    public Result<MarketListingResult> pauseListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.pauseListing(sellerUserId, listingId));
    }

    @PostMapping("/listings/{listingId}/resume")
    public Result<MarketListingResult> resumeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.resumeListing(sellerUserId, listingId));
    }

    @PostMapping("/listings/{listingId}/close")
    public Result<MarketListingResult> closeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketListingService.closeListing(sellerUserId, listingId));
    }

    @GetMapping("/listings/{listingId}/inventory")
    public Result<MarketPageResult<MarketInventoryUnitResult>> listInventory(
            Authentication authentication,
            @PathVariable UUID listingId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketInventoryService.listInventory(listingId, sellerUserId, page, size));
    }

    @PostMapping("/listings/{listingId}/inventory")
    public Result<Void> addInventory(Authentication authentication,
                                     @PathVariable UUID listingId,
                                     @RequestBody @Valid AddMarketInventoryBatchRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        marketInventoryService.appendInventory(toCommand(listingId, sellerUserId, request));
        return Result.ok();
    }

    @PostMapping("/inventory/{inventoryUnitId}/invalidate")
    public Result<Void> invalidateInventory(Authentication authentication, @PathVariable UUID inventoryUnitId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        marketInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
        return Result.ok();
    }

    @GetMapping("/addresses")
    public Result<List<MarketAddressResult>> listAddresses(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketAddressService.listAddresses(userId));
    }

    @PostMapping("/addresses")
    public Result<MarketAddressResult> createAddress(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketAddressService.createAddress(new CreateMarketAddressCommand(
                userId,
                request.receiverName(),
                request.receiverPhone(),
                request.province(),
                request.city(),
                request.district(),
                request.detailAddress(),
                request.postalCode(),
                request.defaultAddress()
        )));
    }

    @PutMapping("/addresses/{addressId}")
    public Result<MarketAddressResult> updateAddress(Authentication authentication,
                                                       @PathVariable UUID addressId,
                                                       @RequestBody @Valid UpdateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketAddressService.updateAddress(new UpdateMarketAddressCommand(
                userId,
                addressId,
                request.receiverName(),
                request.receiverPhone(),
                request.province(),
                request.city(),
                request.district(),
                request.detailAddress(),
                request.postalCode(),
                request.defaultAddress()
        )));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result<Void> deleteAddress(Authentication authentication, @PathVariable UUID addressId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        marketAddressService.deleteAddress(userId, addressId);
        return Result.ok();
    }

    @PostMapping("/orders")
    public Result<MarketOrderResult> createOrder(Authentication authentication,
                                                   @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                                   @RequestBody @Valid CreateMarketOrderRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.createOrder(new CreateOrderCommand(
                buyerUserId,
                request.getListingId(),
                request.getQuantity(),
                request.getAddressId(),
                idempotencyKey
        )));
    }

    @GetMapping("/orders/buying")
    public Result<MarketPageResult<MarketOrderResult>> listBuyingOrders(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.listBuyingOrders(buyerUserId, page, size));
    }

    @GetMapping("/orders/selling")
    public Result<MarketPageResult<MarketOrderResult>> listSellingOrders(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.listSellingOrders(sellerUserId, page, size));
    }

    @GetMapping("/orders/{orderId}")
    public Result<MarketOrderDetailResult> getOrderDetail(
            Authentication authentication,
            @PathVariable UUID orderId
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketQueryService.getOrderDetail(orderId, actorUserId));
    }

    @PostMapping("/orders/{orderId}/deliver")
    public Result<MarketOrderResult> deliverOrder(Authentication authentication,
                                                    @PathVariable UUID orderId,
                                                    @RequestBody @Valid DeliverMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.deliverVirtualOrder(
                orderId,
                sellerUserId,
                request.deliveryContent()
        ));
    }

    @PostMapping("/orders/{orderId}/ship")
    public Result<MarketOrderResult> shipOrder(Authentication authentication,
                                                 @PathVariable UUID orderId,
                                                 @RequestBody @Valid ShipMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.shipPhysicalOrder(
                orderId,
                sellerUserId,
                request.carrierName(),
                request.trackingNo(),
                request.shippingRemark()
        ));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public Result<MarketOrderResult> confirmOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.confirmOrder(orderId, buyerUserId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Result<MarketOrderResult> cancelOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketOrderService.cancelOrder(orderId, buyerUserId));
    }

    @PostMapping("/orders/{orderId}/disputes")
    public Result<MarketDisputeResult> openDispute(Authentication authentication,
                                                     @PathVariable UUID orderId,
                                                     @RequestBody @Valid CreateMarketDisputeRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        MarketDisputeResult dispute = marketDisputeService.openDispute(
                orderId,
                buyerUserId,
                request.reason(),
                request.buyerNote()
        );
        return Result.ok(dispute);
    }

    @PostMapping("/disputes/{disputeId}/seller-accept")
    public Result<MarketDisputeResult> sellerAccept(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeService.sellerAcceptRefund(
                disputeId,
                sellerUserId,
                request.getNote()
        ));
    }

    @PostMapping("/disputes/{disputeId}/seller-reject")
    public Result<MarketDisputeResult> sellerReject(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeService.sellerRejectRefund(
                disputeId,
                sellerUserId,
                request.getNote()
        ));
    }
}
