package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.market.application.command.CreateMarketOrderCommand;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.model.MarketAddressSnapshot;
import com.nowcoder.community.market.domain.model.MarketDelivery;
import com.nowcoder.community.market.domain.model.MarketDeliveryMode;
import com.nowcoder.community.market.domain.model.MarketInventoryUnit;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.model.MarketOrderPlacement;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
import com.nowcoder.community.market.domain.repository.MarketDeliveryRepository;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketShipmentRepository;
import com.nowcoder.community.market.domain.service.MarketOrderDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketOrderApplicationService {

    private static final String DELIVERY_TYPE_MANUAL_TEXT = "MANUAL_TEXT";
    private static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";
    private static final long MAX_ORDER_TOTAL_AMOUNT = 100_000_000L;

    private final MarketListingRepository marketListingRepository;
    private final MarketInventoryRepository marketInventoryRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final MarketAddressRepository marketAddressRepository;
    private final MarketDeliveryRepository marketDeliveryRepository;
    private final MarketShipmentRepository marketShipmentRepository;
    private final MarketWalletActionApplicationService marketWalletActionService;
    private final MarketOrderSagaApplicationService marketOrderSagaService;
    private final IdempotencyGuard idempotencyGuard;
    private final UuidV7Generator idGenerator;
    private final MarketOrderDomainService orderDomainService = new MarketOrderDomainService();

    @Autowired
    public MarketOrderApplicationService(MarketListingRepository marketListingRepository,
                              MarketInventoryRepository marketInventoryRepository,
                              MarketOrderRepository marketOrderRepository,
                              MarketAddressRepository marketAddressRepository,
                              MarketDeliveryRepository marketDeliveryRepository,
                              MarketShipmentRepository marketShipmentRepository,
                              MarketWalletActionApplicationService marketWalletActionService,
                              MarketOrderSagaApplicationService marketOrderSagaService,
                              IdempotencyGuard idempotencyGuard) {
        this(marketListingRepository,
                marketInventoryRepository,
                marketOrderRepository,
                marketAddressRepository,
                marketDeliveryRepository,
                marketShipmentRepository,
                marketWalletActionService,
                marketOrderSagaService,
                idempotencyGuard,
                new UuidV7Generator());
    }

    MarketOrderApplicationService(MarketListingRepository marketListingRepository,
                       MarketInventoryRepository marketInventoryRepository,
                       MarketOrderRepository marketOrderRepository,
                       MarketAddressRepository marketAddressRepository,
                       MarketDeliveryRepository marketDeliveryRepository,
                       MarketShipmentRepository marketShipmentRepository,
                       MarketWalletActionApplicationService marketWalletActionService,
                       MarketOrderSagaApplicationService marketOrderSagaService,
                       IdempotencyGuard idempotencyGuard,
                       UuidV7Generator idGenerator) {
        this.marketListingRepository = marketListingRepository;
        this.marketInventoryRepository = marketInventoryRepository;
        this.marketOrderRepository = marketOrderRepository;
        this.marketAddressRepository = marketAddressRepository;
        this.marketDeliveryRepository = marketDeliveryRepository;
        this.marketShipmentRepository = marketShipmentRepository;
        this.marketWalletActionService = marketWalletActionService;
        this.marketOrderSagaService = marketOrderSagaService;
        this.idempotencyGuard = idempotencyGuard;
        this.idGenerator = idGenerator;
    }

    MarketOrderApplicationService(MarketListingRepository marketListingRepository,
                       MarketInventoryRepository marketInventoryRepository,
                       MarketOrderRepository marketOrderRepository,
                       MarketAddressRepository marketAddressRepository,
                       MarketDeliveryRepository marketDeliveryRepository,
                       MarketShipmentRepository marketShipmentRepository,
                       MarketWalletActionApplicationService marketWalletActionService,
                       MarketOrderSagaApplicationService marketOrderSagaService,
                       UuidV7Generator idGenerator) {
        this(marketListingRepository,
                marketInventoryRepository,
                marketOrderRepository,
                marketAddressRepository,
                marketDeliveryRepository,
                marketShipmentRepository,
                marketWalletActionService,
                marketOrderSagaService,
                null,
                idGenerator);
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
                () -> createOrder(
                        effective.value(),
                        command.buyerUserId(),
                        command.listingId(),
                        command.quantity(),
                        command.addressId()
                )
        );
    }

    @Transactional
    public MarketOrderResult createOrder(String requestId, UUID buyerUserId, UUID listingId, int quantity, UUID addressId) {
        validateCreateOrderRequest(requestId, buyerUserId, listingId, quantity);
        MarketOrder existing = marketOrderRepository.findByBuyerUserIdAndRequestId(buyerUserId, requestId);
        if (existing != null) {
            existing.assertReplayMatches(buyerUserId, listingId, quantity, addressId,
                    replayAddressSnapshot(existing, buyerUserId, addressId));
            return MarketOrderResult.from(existing);
        }

        MarketListing listing = requireListingForUpdate(listingId);
        existing = marketOrderRepository.lockByBuyerUserIdAndRequestId(buyerUserId, requestId);
        if (existing != null) {
            existing.assertReplayMatches(buyerUserId, listingId, quantity, addressId,
                    replayAddressSnapshot(existing, buyerUserId, addressId));
            return MarketOrderResult.from(existing);
        }

        requireActiveListing(listing);
        validateBuyerAndQuantity(buyerUserId, listing, quantity);
        long totalAmount = calculateTotalAmount(listing, quantity);
        List<MarketInventoryUnit> reservedUnits = reserveInventoryIfNeeded(listing, quantity);

        UUID orderId = idGenerator.next();
        MarketAddressSnapshot addressSnapshot = null;
        if (listing.goodsType().isPhysical()) {
            addressSnapshot = MarketAddressSnapshot.from(requireActiveAddress(addressId, buyerUserId));
        }

        MarketOrder order = MarketOrder.place(new MarketOrderPlacement(
                orderId,
                requestId,
                listing.getListingId(),
                listing.goodsType(),
                listing.getSellerUserId(),
                buyerUserId,
                quantity,
                listing.getUnitPrice(),
                totalAmount,
                deliveryModeSnapshot(listing),
                listing.getTitle(),
                addressSnapshot
        ));
        try {
            marketOrderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            MarketOrder duplicated = marketOrderRepository.lockByBuyerUserIdAndRequestId(buyerUserId, requestId);
            if (duplicated != null) {
                duplicated.assertReplayMatches(buyerUserId, listingId, quantity, addressId,
                        replayAddressSnapshot(duplicated, buyerUserId, addressId));
                return MarketOrderResult.from(duplicated);
            }
            throw ex;
        }

        adjustFiniteStockAfterOrder(listing, quantity);
        if (listing.isPreloadedDelivery()) {
            reserveUnitsForOrder(order.getOrderId(), reservedUnits);
        }
        marketWalletActionService.enqueueEscrow(
                order.getOrderId(),
                buyerUserId,
                listing.getSellerUserId(),
                totalAmount
        );

        return MarketOrderResult.from(reloadOrder(order.getOrderId()));
    }

    @Transactional
    public MarketOrderResult deliverVirtualOrder(UUID orderId, UUID sellerUserId, String deliveryContent) {
        if (!StringUtils.hasText(deliveryContent)) {
            throw new BusinessException(INVALID_ARGUMENT, "deliveryContent must not be blank");
        }
        MarketOrder order = requireOrderForUpdate(orderId);
        order.assertSeller(sellerUserId);
        order.assertVirtual();
        order.assertEscrowed();
        order.assertManualDelivery();

        MarketDelivery delivery = new MarketDelivery();
        delivery.setDeliveryId(idGenerator.next());
        delivery.setOrderId(orderId);
        delivery.setSellerUserId(sellerUserId);
        delivery.setDeliveryType(DELIVERY_TYPE_MANUAL_TEXT);
        delivery.setDeliveryContent(deliveryContent.trim());
        delivery.setStatus(DELIVERY_STATUS_DELIVERED);
        delivery.setDeliveredAt(new Date());
        marketDeliveryRepository.save(delivery);

        MarketOrderTransition transition = order.markDelivered(Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        marketOrderRepository.markDelivered(transition.orderId(), transition.autoConfirmAt());
        return MarketOrderResult.from(reloadOrder(orderId));
    }

    @Transactional
    public MarketOrderResult confirmOrder(UUID orderId, UUID buyerUserId) {
        MarketOrder order = requireOrderForUpdate(orderId);
        order.assertBuyer(buyerUserId);
        MarketOrderTransition transition = order.requestRelease();

        int updated = marketOrderRepository.markReleasePending(transition.orderId());
        if (updated == 1) {
            marketWalletActionService.enqueueRelease(
                    transition.orderId(),
                    order.getSellerUserId(),
                    order.getBuyerUserId(),
                    order.getTotalAmount()
            );
        }
        return MarketOrderResult.from(reloadOrder(orderId));
    }

    @Transactional
    public MarketOrderResult shipPhysicalOrder(UUID orderId,
                                               UUID sellerUserId,
                                               String carrierName,
                                               String trackingNo,
                                               String remark) {
        if (!StringUtils.hasText(carrierName) || !StringUtils.hasText(trackingNo)) {
            throw new BusinessException(INVALID_ARGUMENT, "carrierName and trackingNo must not be blank");
        }
        MarketOrder order = requireOrderForUpdate(orderId);
        order.assertSeller(sellerUserId);
        order.assertPhysical();
        order.assertEscrowed();

        var shipment = new com.nowcoder.community.market.domain.model.MarketShipment();
        shipment.setShipmentId(idGenerator.next());
        shipment.setOrderId(orderId);
        shipment.setSellerUserId(sellerUserId);
        shipment.setCarrierName(carrierName.trim());
        shipment.setTrackingNo(trackingNo.trim());
        shipment.setShippingRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        shipment.setShippedAt(new Date());
        marketShipmentRepository.save(shipment);

        MarketOrderTransition transition = order.markShipped(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        marketOrderRepository.markShipped(transition.orderId(), transition.autoConfirmAt());
        return MarketOrderResult.from(reloadOrder(orderId));
    }

    @Transactional
    public MarketOrderResult cancelOrder(UUID orderId, UUID buyerUserId) {
        MarketOrder order = requireOrderForUpdate(orderId);
        order.assertBuyer(buyerUserId);
        if (order.status() == MarketOrderStatus.ESCROWED) {
            MarketOrderTransition transition = order.requestRefund();
            int updated = marketOrderRepository.markRefundPending(transition.orderId());
            if (updated == 1) {
                marketWalletActionService.enqueueRefund(orderId, buyerUserId, order.getSellerUserId(), order.getTotalAmount());
            }
            return MarketOrderResult.from(reloadOrder(orderId));
        }
        if (order.status() == MarketOrderStatus.ESCROW_PENDING) {
            MarketOrderTransition transition = order.requestEscrowCancel();
            int updated = marketOrderRepository.markEscrowCancelPending(transition.orderId());
            if (updated == 1 && marketWalletActionService.cancelPendingEscrowIfPossible(orderId)) {
                marketOrderSagaService.completeEscrowNoop(orderId);
            }
            return MarketOrderResult.from(reloadOrder(orderId));
        }
        throw new BusinessException(INVALID_ARGUMENT, "order status mismatch: orderId=" + order.getOrderId());
    }

    private void validateCreateOrderRequest(String requestId, UUID buyerUserId, UUID listingId, int quantity) {
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException(INVALID_ARGUMENT, "market order requestId must not be blank");
        }
        if (buyerUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "buyerUserId must not be null");
        }
        if (listingId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "listingId must not be null");
        }
        if (quantity <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity must be positive");
        }
    }

    private MarketListing requireListingForUpdate(UUID listingId) {
        MarketListing listing = marketListingRepository.lockById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        return listing;
    }

    private void requireActiveListing(MarketListing listing) {
        if (!listing.isActive()) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing is not active: listingId=" + listing.getListingId());
        }
    }

    private void validateBuyerAndQuantity(UUID buyerUserId, MarketListing listing, int quantity) {
        orderDomainService.validateCreateOrder(buyerUserId, listing.getSellerUserId(), quantity);
        if (quantity < listing.getMinPurchaseQuantity() || quantity > listing.getMaxPurchaseQuantity()) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity is outside listing purchase limits: listingId=" + listing.getListingId());
        }
        if (listing.isFiniteStock() && listing.getStockAvailable() < quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "listing stock is insufficient: listingId=" + listing.getListingId());
        }
    }

    private List<MarketInventoryUnit> reserveInventoryIfNeeded(MarketListing listing, int quantity) {
        if (!listing.goodsType().isVirtual() || !listing.isPreloadedDelivery()) {
            return List.of();
        }
        List<MarketInventoryUnit> units = marketInventoryRepository.lockAvailable(listing.getListingId(), quantity);
        if (units.size() != quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "preloaded inventory is insufficient: listingId=" + listing.getListingId());
        }
        return units;
    }

    private long calculateTotalAmount(MarketListing listing, int quantity) {
        long totalAmount;
        try {
            totalAmount = Math.multiplyExact(listing.getUnitPrice(), (long) quantity);
        } catch (ArithmeticException ex) {
            throw new BusinessException(INVALID_ARGUMENT, "market order total amount overflow: listingId=" + listing.getListingId());
        }
        if (totalAmount > MAX_ORDER_TOTAL_AMOUNT) {
            throw new BusinessException(INVALID_ARGUMENT, "market order total amount exceeds maximum: listingId=" + listing.getListingId());
        }
        return totalAmount;
    }

    private void adjustFiniteStockAfterOrder(MarketListing listing, int quantity) {
        if (!listing.isFiniteStock()) {
            return;
        }
        marketListingRepository.adjustStock(
                listing.getListingId(),
                listing.getSellerUserId(),
                0,
                -quantity,
                listing.statusAfterStockDecreasedBy(quantity)
        );
    }

    private void reserveUnitsForOrder(UUID orderId, List<MarketInventoryUnit> units) {
        for (MarketInventoryUnit unit : units) {
            int updated = marketInventoryRepository.reserveForOrder(unit.getInventoryUnitId(), orderId);
            if (updated != 1) {
                throw new BusinessException(INVALID_ARGUMENT, "inventory reservation failed: inventoryUnitId=" + unit.getInventoryUnitId());
            }
        }
    }

    private MarketAddress requireActiveAddress(UUID addressId, UUID buyerUserId) {
        if (addressId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "addressId must not be null for physical order");
        }
        MarketAddress address = marketAddressRepository.findById(addressId);
        if (address == null || !"ACTIVE".equals(address.getStatus())) {
            throw new BusinessException(NOT_FOUND, "market address not found: addressId=" + addressId);
        }
        if (!Objects.equals(address.getUserId(), buyerUserId)) {
            throw new BusinessException(INVALID_ARGUMENT, "address does not belong to buyer: addressId=" + addressId);
        }
        return address;
    }

    private MarketAddressSnapshot replayAddressSnapshot(MarketOrder order, UUID buyerUserId, UUID addressId) {
        if (!order.goodsType().isPhysical() || order.getAddressIdSnapshot() != null || addressId == null) {
            return null;
        }
        MarketAddress address = marketAddressRepository.findById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), buyerUserId)) {
            return null;
        }
        return MarketAddressSnapshot.from(address);
    }

    private MarketDeliveryMode deliveryModeSnapshot(MarketListing listing) {
        if (!StringUtils.hasText(listing.getDeliveryMode())) {
            return null;
        }
        return listing.deliveryMode();
    }

    private MarketOrder reloadOrder(UUID orderId) {
        MarketOrder order = marketOrderRepository.findById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found after write: orderId=" + orderId);
        }
        return order;
    }

    private MarketOrder requireOrderForUpdate(UUID orderId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        return order;
    }

}
