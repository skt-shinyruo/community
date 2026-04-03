package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.market.entity.VirtualDelivery;
import com.nowcoder.community.market.entity.VirtualInventoryUnit;
import com.nowcoder.community.market.entity.VirtualListing;
import com.nowcoder.community.market.entity.VirtualOrder;
import com.nowcoder.community.market.mapper.VirtualDeliveryMapper;
import com.nowcoder.community.market.mapper.VirtualInventoryUnitMapper;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
import com.nowcoder.community.market.mapper.VirtualOrderMapper;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class VirtualOrderService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_ESCROWED = "ESCROWED";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STOCK_MODE_FINITE = "FINITE";
    private static final String DELIVERY_MODE_MANUAL = "MANUAL";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String INVENTORY_STATUS_DELIVERED = "DELIVERED";
    private static final String DELIVERY_TYPE_PRELOADED_BATCH = "PRELOADED_BATCH";
    private static final String DELIVERY_TYPE_MANUAL_TEXT = "MANUAL_TEXT";
    private static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";

    private final VirtualListingMapper virtualListingMapper;
    private final VirtualInventoryUnitMapper virtualInventoryUnitMapper;
    private final VirtualOrderMapper virtualOrderMapper;
    private final VirtualDeliveryMapper virtualDeliveryMapper;
    private final WalletMarketActionApi walletMarketActionApi;

    public VirtualOrderService(VirtualListingMapper virtualListingMapper,
                               VirtualInventoryUnitMapper virtualInventoryUnitMapper,
                               VirtualOrderMapper virtualOrderMapper,
                               VirtualDeliveryMapper virtualDeliveryMapper,
                               WalletMarketActionApi walletMarketActionApi) {
        this.virtualListingMapper = virtualListingMapper;
        this.virtualInventoryUnitMapper = virtualInventoryUnitMapper;
        this.virtualOrderMapper = virtualOrderMapper;
        this.virtualDeliveryMapper = virtualDeliveryMapper;
        this.walletMarketActionApi = walletMarketActionApi;
    }

    @Transactional
    public VirtualOrderResponse createOrder(String requestId, int buyerUserId, long listingId, int quantity) {
        validateCreateOrderRequest(requestId, buyerUserId, listingId, quantity);
        VirtualOrder existing = virtualOrderMapper.selectByRequestId(requestId);
        if (existing != null) {
            return VirtualOrderResponse.from(existing);
        }

        VirtualListing listing = requireActiveListingForUpdate(listingId);
        validateBuyerAndQuantity(buyerUserId, listing, quantity);
        List<VirtualInventoryUnit> reservedUnits = reserveInventoryIfNeeded(listing, quantity);

        long totalAmount = listing.getUnitPrice() * quantity;
        WalletMarketTxnView escrowTxn = walletMarketActionApi.escrowOrder(
                requestId + ":escrow",
                buyerUserId,
                totalAmount,
                "virtual-order:" + requestId
        );

        adjustFiniteStockAfterOrder(listing, quantity);

        VirtualOrder order = new VirtualOrder();
        order.setRequestId(requestId);
        order.setListingId(listing.getListingId());
        order.setSellerUserId(listing.getSellerUserId());
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(quantity);
        order.setUnitPriceSnapshot(listing.getUnitPrice());
        order.setTotalAmount(totalAmount);
        order.setDeliveryModeSnapshot(listing.getDeliveryMode());
        order.setListingTitleSnapshot(listing.getTitle());
        order.setStatus(STATUS_ESCROWED);
        order.setEscrowTxnId(escrowTxn.txnId());
        virtualOrderMapper.insert(order);

        if (DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            reserveUnitsForOrder(order.getOrderId(), reservedUnits);
            String deliveryContent = reservedUnits.stream()
                    .map(VirtualInventoryUnit::getPayloadContent)
                    .toList()
                    .stream()
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            insertDeliveredPayload(order.getOrderId(), listing.getSellerUserId(), deliveryContent);
            Date autoConfirmAt = Date.from(Instant.now().plus(24, ChronoUnit.HOURS));
            virtualOrderMapper.markDelivered(order.getOrderId(), autoConfirmAt);
            virtualInventoryUnitMapper.markDeliveredByOrder(order.getOrderId(), INVENTORY_STATUS_DELIVERED, new Date());
        }

        return VirtualOrderResponse.from(reloadOrder(order.getOrderId()));
    }

    @Transactional
    public VirtualOrderResponse deliverOrder(long orderId, int sellerUserId, String deliveryContent) {
        if (!StringUtils.hasText(deliveryContent)) {
            throw new BusinessException(INVALID_ARGUMENT, "deliveryContent must not be blank");
        }
        VirtualOrder order = requireOrderForUpdate(orderId);
        if (order.getSellerUserId() != sellerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "seller does not own order: orderId=" + orderId);
        }
        if (!STATUS_ESCROWED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not escrowed: orderId=" + orderId);
        }
        if (!DELIVERY_MODE_MANUAL.equals(order.getDeliveryModeSnapshot())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not MANUAL delivery: orderId=" + orderId);
        }

        insertDelivery(orderId, sellerUserId, DELIVERY_TYPE_MANUAL_TEXT, deliveryContent.trim());
        virtualOrderMapper.markDelivered(orderId, Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        return VirtualOrderResponse.from(reloadOrder(orderId));
    }

    @Transactional
    public VirtualOrderResponse confirmOrder(long orderId, int buyerUserId) {
        VirtualOrder order = requireOrderForUpdate(orderId);
        if (order.getBuyerUserId() != buyerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer does not own order: orderId=" + orderId);
        }
        if (!STATUS_DELIVERED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not delivered: orderId=" + orderId);
        }

        WalletMarketTxnView releaseTxn = walletMarketActionApi.releaseOrder(
                "virtual-order:" + orderId + ":release",
                order.getSellerUserId(),
                order.getTotalAmount(),
                "virtual-order:" + orderId
        );
        virtualOrderMapper.markCompleted(orderId, releaseTxn.txnId());
        return VirtualOrderResponse.from(reloadOrder(orderId));
    }

    @Transactional
    public VirtualOrderResponse cancelOrder(long orderId, int buyerUserId) {
        VirtualOrder order = requireOrderForUpdate(orderId);
        if (order.getBuyerUserId() != buyerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer does not own order: orderId=" + orderId);
        }
        if (!STATUS_ESCROWED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not escrowed: orderId=" + orderId);
        }

        WalletMarketTxnView refundTxn = walletMarketActionApi.refundOrder(
                "virtual-order:" + orderId + ":refund",
                order.getBuyerUserId(),
                order.getTotalAmount(),
                "virtual-order:" + orderId
        );

        VirtualListing listing = virtualListingMapper.selectByIdForUpdate(order.getListingId());
        if (listing != null && STOCK_MODE_FINITE.equals(listing.getStockMode())) {
            virtualListingMapper.adjustStock(listing.getListingId(), listing.getSellerUserId(), 0, order.getQuantity(), STATUS_ACTIVE);
        }
        if (DELIVERY_MODE_PRELOADED.equals(order.getDeliveryModeSnapshot())) {
            virtualInventoryUnitMapper.releaseReservedByOrder(orderId);
        }
        virtualOrderMapper.markCancelled(orderId, refundTxn.txnId());
        return VirtualOrderResponse.from(reloadOrder(orderId));
    }

    private void validateCreateOrderRequest(String requestId, int buyerUserId, long listingId, int quantity) {
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual order requestId must not be blank");
        }
        if (buyerUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "buyerUserId must be positive");
        }
        if (listingId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "listingId must be positive");
        }
        if (quantity <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity must be positive");
        }
    }

    private VirtualListing requireActiveListingForUpdate(long listingId) {
        VirtualListing listing = virtualListingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "virtual listing not found: listingId=" + listingId);
        }
        if (!STATUS_ACTIVE.equals(listing.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing is not active: listingId=" + listingId);
        }
        return listing;
    }

    private void validateBuyerAndQuantity(int buyerUserId, VirtualListing listing, int quantity) {
        if (listing.getSellerUserId() == buyerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer cannot purchase own listing: listingId=" + listing.getListingId());
        }
        if (quantity < listing.getMinPurchaseQuantity() || quantity > listing.getMaxPurchaseQuantity()) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity is outside listing purchase limits: listingId=" + listing.getListingId());
        }
        if (STOCK_MODE_FINITE.equals(listing.getStockMode()) && listing.getStockAvailable() < quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "listing stock is insufficient: listingId=" + listing.getListingId());
        }
    }

    private List<VirtualInventoryUnit> reserveInventoryIfNeeded(VirtualListing listing, int quantity) {
        if (!DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            return List.of();
        }
        List<VirtualInventoryUnit> units = virtualInventoryUnitMapper.selectAvailableForUpdate(listing.getListingId(), quantity);
        if (units.size() != quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "preloaded inventory is insufficient: listingId=" + listing.getListingId());
        }
        return units;
    }

    private void adjustFiniteStockAfterOrder(VirtualListing listing, int quantity) {
        if (!STOCK_MODE_FINITE.equals(listing.getStockMode())) {
            return;
        }
        int nextAvailable = listing.getStockAvailable() - quantity;
        String nextStatus = nextAvailable <= 0 ? STATUS_SOLD_OUT : listing.getStatus();
        virtualListingMapper.adjustStock(listing.getListingId(), listing.getSellerUserId(), 0, -quantity, nextStatus);
    }

    private void reserveUnitsForOrder(long orderId, List<VirtualInventoryUnit> units) {
        for (VirtualInventoryUnit unit : units) {
            int updated = virtualInventoryUnitMapper.reserveForOrder(unit.getInventoryUnitId(), orderId);
            if (updated != 1) {
                throw new BusinessException(INVALID_ARGUMENT, "inventory reservation failed: inventoryUnitId=" + unit.getInventoryUnitId());
            }
        }
    }

    private void insertDeliveredPayload(long orderId, int sellerUserId, String deliveryContent) {
        insertDelivery(orderId, sellerUserId, DELIVERY_TYPE_PRELOADED_BATCH, deliveryContent);
    }

    private VirtualOrder reloadOrder(long orderId) {
        VirtualOrder order = virtualOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "virtual order not found after write: orderId=" + orderId);
        }
        return order;
    }

    private VirtualOrder requireOrderForUpdate(long orderId) {
        VirtualOrder order = virtualOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "virtual order not found: orderId=" + orderId);
        }
        return order;
    }

    private void insertDelivery(long orderId, int sellerUserId, String deliveryType, String deliveryContent) {
        VirtualDelivery delivery = new VirtualDelivery();
        delivery.setOrderId(orderId);
        delivery.setSellerUserId(sellerUserId);
        delivery.setDeliveryType(deliveryType);
        delivery.setDeliveryContent(deliveryContent);
        delivery.setStatus(DELIVERY_STATUS_DELIVERED);
        delivery.setDeliveredAt(new Date());
        virtualDeliveryMapper.insert(delivery);
    }
}
