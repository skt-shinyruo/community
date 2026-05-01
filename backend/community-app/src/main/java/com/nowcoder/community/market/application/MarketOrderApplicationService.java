package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.model.MarketDelivery;
import com.nowcoder.community.market.domain.model.MarketInventoryUnit;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.exception.MarketErrorCode;
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

    private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
    private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ESCROW_PENDING = "ESCROW_PENDING";
    private static final String STATUS_ESCROWED = "ESCROWED";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_SHIPPED = "SHIPPED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STOCK_MODE_FINITE = "FINITE";
    private static final String DELIVERY_MODE_MANUAL = "MANUAL";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String DELIVERY_TYPE_MANUAL_TEXT = "MANUAL_TEXT";
    private static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";

    private final MarketListingRepository marketListingRepository;
    private final MarketInventoryRepository marketInventoryRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final MarketAddressRepository marketAddressRepository;
    private final MarketDeliveryRepository marketDeliveryRepository;
    private final MarketShipmentRepository marketShipmentRepository;
    private final MarketWalletActionApplicationService marketWalletActionService;
    private final MarketOrderSagaApplicationService marketOrderSagaService;
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
                              MarketOrderSagaApplicationService marketOrderSagaService) {
        this(marketListingRepository,
                marketInventoryRepository,
                marketOrderRepository,
                marketAddressRepository,
                marketDeliveryRepository,
                marketShipmentRepository,
                marketWalletActionService,
                marketOrderSagaService,
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
                       UuidV7Generator idGenerator) {
        this.marketListingRepository = marketListingRepository;
        this.marketInventoryRepository = marketInventoryRepository;
        this.marketOrderRepository = marketOrderRepository;
        this.marketAddressRepository = marketAddressRepository;
        this.marketDeliveryRepository = marketDeliveryRepository;
        this.marketShipmentRepository = marketShipmentRepository;
        this.marketWalletActionService = marketWalletActionService;
        this.marketOrderSagaService = marketOrderSagaService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketOrderResult createOrder(String requestId, UUID buyerUserId, UUID listingId, int quantity, UUID addressId) {
        validateCreateOrderRequest(requestId, buyerUserId, listingId, quantity);
        MarketOrder existing = marketOrderRepository.findByBuyerUserIdAndRequestId(buyerUserId, requestId);
        if (existing != null) {
            ensureReplayMatches(existing, buyerUserId, listingId, quantity, addressId);
            return MarketOrderResult.from(existing);
        }

        MarketListing listing = requireListingForUpdate(listingId);
        existing = marketOrderRepository.lockByBuyerUserIdAndRequestId(buyerUserId, requestId);
        if (existing != null) {
            ensureReplayMatches(existing, buyerUserId, listingId, quantity, addressId);
            return MarketOrderResult.from(existing);
        }

        requireActiveListing(listing);
        validateBuyerAndQuantity(buyerUserId, listing, quantity);
        List<MarketInventoryUnit> reservedUnits = reserveInventoryIfNeeded(listing, quantity);

        long totalAmount = listing.getUnitPrice() * quantity;
        UUID orderId = idGenerator.next();

        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setListingId(listing.getListingId());
        order.setGoodsType(listing.getGoodsType());
        order.setSellerUserId(listing.getSellerUserId());
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(quantity);
        order.setUnitPriceSnapshot(listing.getUnitPrice());
        order.setTotalAmount(totalAmount);
        order.setDeliveryModeSnapshot(listing.getDeliveryMode());
        order.setListingTitleSnapshot(listing.getTitle());
        order.setStatus(STATUS_ESCROW_PENDING);
        order.setEscrowTxnId(null);
        if (GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType())) {
            MarketAddress address = requireActiveAddress(addressId, buyerUserId);
            order.setAddressIdSnapshot(address.getAddressId());
            snapshotAddress(order, address);
        }
        try {
            marketOrderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            MarketOrder duplicated = marketOrderRepository.lockByBuyerUserIdAndRequestId(buyerUserId, requestId);
            if (duplicated != null) {
                ensureReplayMatches(duplicated, buyerUserId, listingId, quantity, addressId);
                return MarketOrderResult.from(duplicated);
            }
            throw ex;
        }

        adjustFiniteStockAfterOrder(listing, quantity);
        if (DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
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
        requireSeller(order, sellerUserId);
        requireGoodsType(order, GOODS_TYPE_VIRTUAL);
        requireStatus(order, STATUS_ESCROWED);
        if (!DELIVERY_MODE_MANUAL.equals(order.getDeliveryModeSnapshot())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not MANUAL delivery: orderId=" + orderId);
        }

        MarketDelivery delivery = new MarketDelivery();
        delivery.setDeliveryId(idGenerator.next());
        delivery.setOrderId(orderId);
        delivery.setSellerUserId(sellerUserId);
        delivery.setDeliveryType(DELIVERY_TYPE_MANUAL_TEXT);
        delivery.setDeliveryContent(deliveryContent.trim());
        delivery.setStatus(DELIVERY_STATUS_DELIVERED);
        delivery.setDeliveredAt(new Date());
        marketDeliveryRepository.save(delivery);

        marketOrderRepository.markDelivered(orderId, Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        return MarketOrderResult.from(reloadOrder(orderId));
    }

    @Transactional
    public MarketOrderResult confirmOrder(UUID orderId, UUID buyerUserId) {
        MarketOrder order = requireOrderForUpdate(orderId);
        requireBuyer(order, buyerUserId);
        if (!STATUS_DELIVERED.equals(order.getStatus()) && !STATUS_SHIPPED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not confirmable: orderId=" + orderId);
        }

        int updated = marketOrderRepository.markReleasePending(orderId);
        if (updated == 1) {
            marketWalletActionService.enqueueRelease(
                    orderId,
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
        requireSeller(order, sellerUserId);
        requireGoodsType(order, GOODS_TYPE_PHYSICAL);
        requireStatus(order, STATUS_ESCROWED);

        var shipment = new com.nowcoder.community.market.domain.model.MarketShipment();
        shipment.setShipmentId(idGenerator.next());
        shipment.setOrderId(orderId);
        shipment.setSellerUserId(sellerUserId);
        shipment.setCarrierName(carrierName.trim());
        shipment.setTrackingNo(trackingNo.trim());
        shipment.setShippingRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        shipment.setShippedAt(new Date());
        marketShipmentRepository.save(shipment);

        marketOrderRepository.markShipped(orderId, Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        return MarketOrderResult.from(reloadOrder(orderId));
    }

    @Transactional
    public MarketOrderResult cancelOrder(UUID orderId, UUID buyerUserId) {
        MarketOrder order = requireOrderForUpdate(orderId);
        requireBuyer(order, buyerUserId);
        if (STATUS_ESCROWED.equals(order.getStatus())) {
            int updated = marketOrderRepository.markRefundPending(orderId);
            if (updated == 1) {
                marketWalletActionService.enqueueRefund(orderId, buyerUserId, order.getSellerUserId(), order.getTotalAmount());
            }
            return MarketOrderResult.from(reloadOrder(orderId));
        }
        if (STATUS_ESCROW_PENDING.equals(order.getStatus())) {
            int updated = marketOrderRepository.markEscrowCancelPending(orderId);
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
        if (!STATUS_ACTIVE.equals(listing.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing is not active: listingId=" + listing.getListingId());
        }
    }

    private void validateBuyerAndQuantity(UUID buyerUserId, MarketListing listing, int quantity) {
        orderDomainService.validateCreateOrder(buyerUserId, listing.getSellerUserId(), quantity);
        if (quantity < listing.getMinPurchaseQuantity() || quantity > listing.getMaxPurchaseQuantity()) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity is outside listing purchase limits: listingId=" + listing.getListingId());
        }
        boolean finiteStock = GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType()) || STOCK_MODE_FINITE.equals(listing.getStockMode());
        if (finiteStock && listing.getStockAvailable() < quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "listing stock is insufficient: listingId=" + listing.getListingId());
        }
    }

    private boolean isFiniteStock(MarketListing listing) {
        return GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType()) || STOCK_MODE_FINITE.equals(listing.getStockMode());
    }

    private List<MarketInventoryUnit> reserveInventoryIfNeeded(MarketListing listing, int quantity) {
        if (!GOODS_TYPE_VIRTUAL.equals(listing.getGoodsType()) || !DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            return List.of();
        }
        List<MarketInventoryUnit> units = marketInventoryRepository.lockAvailable(listing.getListingId(), quantity);
        if (units.size() != quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "preloaded inventory is insufficient: listingId=" + listing.getListingId());
        }
        return units;
    }

    private void adjustFiniteStockAfterOrder(MarketListing listing, int quantity) {
        boolean finiteStock = isFiniteStock(listing);
        if (!finiteStock) {
            return;
        }
        int nextAvailable = listing.getStockAvailable() - quantity;
        String nextStatus = nextAvailable <= 0 ? STATUS_SOLD_OUT : listing.getStatus();
        marketListingRepository.adjustStock(listing.getListingId(), listing.getSellerUserId(), 0, -quantity, nextStatus);
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

    private void ensureReplayMatches(MarketOrder order, UUID buyerUserId, UUID listingId, int quantity, UUID addressId) {
        if (!Objects.equals(order.getBuyerUserId(), buyerUserId)
                || !Objects.equals(order.getListingId(), listingId)
                || order.getQuantity() != quantity
                || !addressMatchesReplay(order, buyerUserId, addressId)) {
            throw new BusinessException(
                    MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }

    private boolean addressMatchesReplay(MarketOrder order, UUID buyerUserId, UUID addressId) {
        if (!GOODS_TYPE_PHYSICAL.equals(order.getGoodsType())) {
            return true;
        }
        if (order.getAddressIdSnapshot() != null) {
            return Objects.equals(order.getAddressIdSnapshot(), addressId);
        }
        if (addressId == null) {
            return false;
        }
        MarketAddress address = marketAddressRepository.findById(addressId);
        return address != null
                && Objects.equals(address.getUserId(), buyerUserId)
                && Objects.equals(address.getReceiverName(), order.getReceiverNameSnapshot())
                && Objects.equals(address.getReceiverPhone(), order.getReceiverPhoneSnapshot())
                && Objects.equals(address.getProvince(), order.getProvinceSnapshot())
                && Objects.equals(address.getCity(), order.getCitySnapshot())
                && Objects.equals(address.getDistrict(), order.getDistrictSnapshot())
                && Objects.equals(address.getDetailAddress(), order.getDetailAddressSnapshot())
                && Objects.equals(address.getPostalCode(), order.getPostalCodeSnapshot());
    }

    private void snapshotAddress(MarketOrder order, MarketAddress address) {
        order.setReceiverNameSnapshot(address.getReceiverName());
        order.setReceiverPhoneSnapshot(address.getReceiverPhone());
        order.setProvinceSnapshot(address.getProvince());
        order.setCitySnapshot(address.getCity());
        order.setDistrictSnapshot(address.getDistrict());
        order.setDetailAddressSnapshot(address.getDetailAddress());
        order.setPostalCodeSnapshot(address.getPostalCode());
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

    private void requireSeller(MarketOrder order, UUID sellerUserId) {
        orderDomainService.validateSellerAction(sellerUserId, order.getSellerUserId());
    }

    private void requireBuyer(MarketOrder order, UUID buyerUserId) {
        orderDomainService.validateBuyerAction(buyerUserId, order.getBuyerUserId());
    }

    private void requireGoodsType(MarketOrder order, String goodsType) {
        if (!goodsType.equals(order.getGoodsType())) {
            throw new BusinessException(INVALID_ARGUMENT, "order goodsType mismatch: orderId=" + order.getOrderId());
        }
    }

    private void requireStatus(MarketOrder order, String status) {
        if (!status.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order status mismatch: orderId=" + order.getOrderId());
        }
    }
}
