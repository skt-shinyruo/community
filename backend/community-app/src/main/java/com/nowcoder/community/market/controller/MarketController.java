package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.application.MarketAddressApplicationService;
import com.nowcoder.community.market.application.MarketDisputeApplicationService;
import com.nowcoder.community.market.application.MarketInventoryApplicationService;
import com.nowcoder.community.market.application.MarketListingApplicationService;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.market.application.MarketQueryApplicationService;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.command.CreateMarketAddressCommand;
import com.nowcoder.community.market.application.command.CreateMarketDisputeCommand;
import com.nowcoder.community.market.application.command.CreateMarketListingCommand;
import com.nowcoder.community.market.application.command.CreateMarketOrderCommand;
import com.nowcoder.community.market.application.command.DeliverMarketOrderCommand;
import com.nowcoder.community.market.application.command.SellerDisputeDecisionCommand;
import com.nowcoder.community.market.application.command.ShipMarketOrderCommand;
import com.nowcoder.community.market.application.command.UpdateMarketAddressCommand;
import com.nowcoder.community.market.application.command.UpdateMarketListingCommand;
import com.nowcoder.community.market.application.result.MarketAddressResult;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.application.result.MarketInventoryUnitResult;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketDisputeRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketOrderRequest;
import com.nowcoder.community.market.controller.dto.DeliverMarketOrderRequest;
import com.nowcoder.community.market.controller.dto.MarketAddressResponse;
import com.nowcoder.community.market.controller.dto.MarketDisputeResponse;
import com.nowcoder.community.market.controller.dto.MarketInventoryUnitResponse;
import com.nowcoder.community.market.controller.dto.MarketListingDetailResponse;
import com.nowcoder.community.market.controller.dto.MarketListingResponse;
import com.nowcoder.community.market.controller.dto.MarketOrderDetailResponse;
import com.nowcoder.community.market.controller.dto.MarketOrderResponse;
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

    private static List<MarketInventoryUnitResponse> toInventoryResponses(List<MarketInventoryUnitResult> units) {
        return units.stream()
                .map(MarketInventoryUnitResponse::from)
                .toList();
    }

    private static List<MarketAddressResponse> toAddressResponses(List<MarketAddressResult> addresses) {
        return addresses.stream()
                .map(MarketAddressResponse::from)
                .toList();
    }

    private static AddMarketInventoryBatchCommand toCommand(
            UUID listingId,
            UUID sellerUserId,
            AddMarketInventoryBatchRequest request
    ) {
        if (request == null) {
            return null;
        }
        return new AddMarketInventoryBatchCommand(listingId, sellerUserId, request.getPayloadType(), request.getPayloads());
    }

    @GetMapping("/listings")
    public Result<List<MarketListingResponse>> listPublicListings() {
        return Result.ok(toListingResponses(marketQueryService.listPublicListings()));
    }

    @GetMapping("/listings/{listingId}")
    public Result<MarketListingDetailResponse> getListingDetail(@PathVariable UUID listingId) {
        return Result.ok(MarketListingDetailResponse.from(marketQueryService.getListingDetail(listingId)));
    }

    @GetMapping("/my-listings")
    public Result<List<MarketListingResponse>> listSellerListings(Authentication authentication) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toListingResponses(marketQueryService.listSellerListings(sellerUserId)));
    }

    @PostMapping("/listings")
    public Result<MarketListingResponse> createListing(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketListingService.createListing(new CreateMarketListingCommand(
                sellerUserId,
                request.getGoodsType(),
                request.getTitle(),
                request.getDescription(),
                request.getUnitPrice(),
                request.getDeliveryMode(),
                request.getStockMode(),
                request.getStockTotal(),
                request.getMinPurchaseQuantity(),
                request.getMaxPurchaseQuantity(),
                toCommand(null, sellerUserId, request.getInventory())
        ))));
    }

    @PutMapping("/listings/{listingId}")
    public Result<MarketListingResponse> updateListing(Authentication authentication,
                                                       @PathVariable UUID listingId,
                                                       @RequestBody @Valid UpdateMarketListingRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketListingService.updateListing(new UpdateMarketListingCommand(
                sellerUserId,
                listingId,
                request.getTitle(),
                request.getDescription(),
                request.getUnitPrice(),
                request.getMinPurchaseQuantity(),
                request.getMaxPurchaseQuantity()
        ))));
    }

    @PostMapping("/listings/{listingId}/pause")
    public Result<MarketListingResponse> pauseListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketListingService.pauseListing(sellerUserId, listingId)));
    }

    @PostMapping("/listings/{listingId}/resume")
    public Result<MarketListingResponse> resumeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketListingService.resumeListing(sellerUserId, listingId)));
    }

    @PostMapping("/listings/{listingId}/close")
    public Result<MarketListingResponse> closeListing(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketListingResponse.from(marketListingService.closeListing(sellerUserId, listingId)));
    }

    @GetMapping("/listings/{listingId}/inventory")
    public Result<List<MarketInventoryUnitResponse>> listInventory(Authentication authentication, @PathVariable UUID listingId) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toInventoryResponses(marketInventoryService.listInventory(listingId, sellerUserId)));
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
    public Result<List<MarketAddressResponse>> listAddresses(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toAddressResponses(marketAddressService.listAddresses(userId)));
    }

    @PostMapping("/addresses")
    public Result<MarketAddressResponse> createAddress(Authentication authentication,
                                                       @RequestBody @Valid CreateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketAddressResponse.from(marketAddressService.createAddress(new CreateMarketAddressCommand(
                userId,
                request.getReceiverName(),
                request.getReceiverPhone(),
                request.getProvince(),
                request.getCity(),
                request.getDistrict(),
                request.getDetailAddress(),
                request.getPostalCode(),
                request.isDefaultAddress()
        ))));
    }

    @PutMapping("/addresses/{addressId}")
    public Result<MarketAddressResponse> updateAddress(Authentication authentication,
                                                       @PathVariable UUID addressId,
                                                       @RequestBody @Valid UpdateMarketAddressRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketAddressResponse.from(marketAddressService.updateAddress(new UpdateMarketAddressCommand(
                userId,
                addressId,
                request.getReceiverName(),
                request.getReceiverPhone(),
                request.getProvince(),
                request.getCity(),
                request.getDistrict(),
                request.getDetailAddress(),
                request.getPostalCode(),
                request.isDefaultAddress()
        ))));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result<Void> deleteAddress(Authentication authentication, @PathVariable UUID addressId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        marketAddressService.deleteAddress(userId, addressId);
        return Result.ok();
    }

    @PostMapping("/orders")
    public Result<MarketOrderResponse> createOrder(Authentication authentication,
                                                   @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                                   @RequestBody @Valid CreateMarketOrderRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketOrderService.createOrder(new CreateMarketOrderCommand(
                buyerUserId,
                request.getListingId(),
                request.getQuantity(),
                request.getAddressId(),
                idempotencyKey
        ))));
    }

    @GetMapping("/orders/buying")
    public Result<List<MarketOrderResponse>> listBuyingOrders(Authentication authentication) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toOrderResponses(marketQueryService.listBuyingOrders(buyerUserId)));
    }

    @GetMapping("/orders/selling")
    public Result<List<MarketOrderResponse>> listSellingOrders(Authentication authentication) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toOrderResponses(marketQueryService.listSellingOrders(sellerUserId)));
    }

    @GetMapping("/orders/{orderId}")
    public Result<MarketOrderDetailResponse> getOrderDetail(Authentication authentication, @PathVariable UUID orderId) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderDetailResponse.from(marketQueryService.getOrderDetail(orderId, actorUserId)));
    }

    @PostMapping("/orders/{orderId}/deliver")
    public Result<MarketOrderResponse> deliverOrder(Authentication authentication,
                                                    @PathVariable UUID orderId,
                                                    @RequestBody @Valid DeliverMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        DeliverMarketOrderCommand command = new DeliverMarketOrderCommand(
                orderId,
                sellerUserId,
                request.getDeliveryContent()
        );
        return Result.ok(MarketOrderResponse.from(marketOrderService.deliverVirtualOrder(
                command.orderId(),
                command.sellerUserId(),
                command.deliveryContent()
        )));
    }

    @PostMapping("/orders/{orderId}/ship")
    public Result<MarketOrderResponse> shipOrder(Authentication authentication,
                                                 @PathVariable UUID orderId,
                                                 @RequestBody @Valid ShipMarketOrderRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        ShipMarketOrderCommand command = new ShipMarketOrderCommand(
                orderId,
                sellerUserId,
                request.getCarrierName(),
                request.getTrackingNo(),
                request.getShippingRemark()
        );
        return Result.ok(MarketOrderResponse.from(marketOrderService.shipPhysicalOrder(
                command.orderId(),
                command.sellerUserId(),
                command.carrierName(),
                command.trackingNo(),
                command.shippingRemark()
        )));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public Result<MarketOrderResponse> confirmOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketOrderService.confirmOrder(orderId, buyerUserId)));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Result<MarketOrderResponse> cancelOrder(Authentication authentication, @PathVariable UUID orderId) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketOrderResponse.from(marketOrderService.cancelOrder(orderId, buyerUserId)));
    }

    @PostMapping("/orders/{orderId}/disputes")
    public Result<MarketDisputeResponse> openDispute(Authentication authentication,
                                                     @PathVariable UUID orderId,
                                                     @RequestBody @Valid CreateMarketDisputeRequest request) {
        UUID buyerUserId = CurrentUser.requireUserUuid(authentication);
        CreateMarketDisputeCommand command = new CreateMarketDisputeCommand(
                orderId,
                buyerUserId,
                request.getReason(),
                request.getBuyerNote()
        );
        MarketDisputeResult dispute = marketDisputeService.openDispute(
                command.orderId(),
                command.buyerUserId(),
                command.reason(),
                command.buyerNote()
        );
        return Result.ok(MarketDisputeResponse.from(dispute));
    }

    @PostMapping("/disputes/{disputeId}/seller-accept")
    public Result<MarketDisputeResponse> sellerAccept(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        SellerDisputeDecisionCommand command = new SellerDisputeDecisionCommand(
                disputeId,
                sellerUserId,
                request.getNote()
        );
        return Result.ok(MarketDisputeResponse.from(marketDisputeService.sellerAcceptRefund(
                command.disputeId(),
                command.sellerUserId(),
                command.note()
        )));
    }

    @PostMapping("/disputes/{disputeId}/seller-reject")
    public Result<MarketDisputeResponse> sellerReject(Authentication authentication,
                                                      @PathVariable UUID disputeId,
                                                      @RequestBody @Valid SellerDisputeDecisionRequest request) {
        UUID sellerUserId = CurrentUser.requireUserUuid(authentication);
        SellerDisputeDecisionCommand command = new SellerDisputeDecisionCommand(
                disputeId,
                sellerUserId,
                request.getNote()
        );
        return Result.ok(MarketDisputeResponse.from(marketDisputeService.sellerRejectRefund(
                command.disputeId(),
                command.sellerUserId(),
                command.note()
        )));
    }
}
