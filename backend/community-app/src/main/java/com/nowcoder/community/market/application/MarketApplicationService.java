package com.nowcoder.community.market.application;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
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
import com.nowcoder.community.market.application.result.MarketListingDetailResult;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.application.result.MarketOrderDetailResult;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MarketApplicationService {

    private final MarketListingApplicationService marketListingService;
    private final MarketInventoryApplicationService marketInventoryService;
    private final MarketQueryApplicationService marketQueryService;
    private final MarketOrderApplicationService marketOrderService;
    private final MarketDisputeApplicationService marketDisputeService;
    private final MarketAddressApplicationService marketAddressService;
    private final IdempotencyGuard idempotencyGuard;

    public MarketApplicationService(
            MarketListingApplicationService marketListingService,
            MarketInventoryApplicationService marketInventoryService,
            MarketQueryApplicationService marketQueryService,
            MarketOrderApplicationService marketOrderService,
            MarketDisputeApplicationService marketDisputeService,
            MarketAddressApplicationService marketAddressService,
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

    public MarketListingDetailResult getListingDetail(UUID listingId) {
        return marketQueryService.getListingDetail(listingId);
    }

    public List<MarketListingResult> listSellerListings(UUID sellerUserId) {
        return marketQueryService.listSellerListings(sellerUserId);
    }

    public MarketListingResult createListing(CreateMarketListingCommand command) {
        return marketListingService.createListing(command);
    }

    public MarketListingResult updateListing(UpdateMarketListingCommand command) {
        return marketListingService.updateListing(command);
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

    public List<MarketInventoryUnitResult> listInventory(UUID listingId, UUID sellerUserId) {
        return marketInventoryService.listInventory(listingId, sellerUserId);
    }

    public void addInventory(AddMarketInventoryBatchCommand command) {
        marketInventoryService.appendInventory(command);
    }

    public void invalidateInventory(UUID inventoryUnitId, UUID sellerUserId) {
        marketInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
    }

    public List<MarketAddressResult> listAddresses(UUID userId) {
        return marketAddressService.listAddresses(userId);
    }

    public MarketAddressResult createAddress(CreateMarketAddressCommand command) {
        return marketAddressService.createAddress(command);
    }

    public MarketAddressResult updateAddress(UpdateMarketAddressCommand command) {
        return marketAddressService.updateAddress(command);
    }

    public void deleteAddress(UUID userId, UUID addressId) {
        marketAddressService.deleteAddress(userId, addressId);
    }

    public MarketOrderResult createOrder(CreateMarketOrderCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        String requestHash = RequestFingerprint.sha256(
                "market:create_order|listingId=" + command.listingId()
                        + "|quantity=" + command.quantity()
                        + "|addressId=" + (command.addressId() == null ? "" : command.addressId())
        );
        return idempotencyGuard.executeRequired(
                "market:create_order",
                command.buyerUserId(),
                effective.value(),
                requestHash,
                MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                MarketOrderResult.class,
                () -> marketOrderService.createOrder(
                        effective.value(),
                        command.buyerUserId(),
                        command.listingId(),
                        command.quantity(),
                        command.addressId()
                )
        );
    }

    public List<MarketOrderResult> listBuyingOrders(UUID buyerUserId) {
        return marketQueryService.listBuyingOrders(buyerUserId);
    }

    public List<MarketOrderResult> listSellingOrders(UUID sellerUserId) {
        return marketQueryService.listSellingOrders(sellerUserId);
    }

    public MarketOrderDetailResult getOrderDetail(UUID orderId, UUID actorUserId) {
        return marketQueryService.getOrderDetail(orderId, actorUserId);
    }

    public MarketOrderResult deliverOrder(DeliverMarketOrderCommand command) {
        return marketOrderService.deliverVirtualOrder(command.orderId(), command.sellerUserId(), command.deliveryContent());
    }

    public MarketOrderResult shipOrder(ShipMarketOrderCommand command) {
        return marketOrderService.shipPhysicalOrder(
                command.orderId(),
                command.sellerUserId(),
                command.carrierName(),
                command.trackingNo(),
                command.shippingRemark()
        );
    }

    public MarketOrderResult confirmOrder(UUID orderId, UUID buyerUserId) {
        return marketOrderService.confirmOrder(orderId, buyerUserId);
    }

    public MarketOrderResult cancelOrder(UUID orderId, UUID buyerUserId) {
        return marketOrderService.cancelOrder(orderId, buyerUserId);
    }

    public MarketDisputeResult openDispute(CreateMarketDisputeCommand command) {
        return marketDisputeService.openDispute(command.orderId(), command.buyerUserId(), command.reason(), command.buyerNote());
    }

    public MarketDisputeResult sellerAccept(SellerDisputeDecisionCommand command) {
        return marketDisputeService.sellerAcceptRefund(command.disputeId(), command.sellerUserId(), command.note());
    }

    public MarketDisputeResult sellerReject(SellerDisputeDecisionCommand command) {
        return marketDisputeService.sellerRejectRefund(command.disputeId(), command.sellerUserId(), command.note());
    }
}
