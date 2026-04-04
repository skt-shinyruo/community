package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.MarketOrderResponse;
import com.nowcoder.community.market.entity.MarketAddress;
import com.nowcoder.community.market.entity.MarketInventoryUnit;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketAddressMapper;
import com.nowcoder.community.market.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
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
public class MarketOrderService {

    private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
    private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ESCROWED = "ESCROWED";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STOCK_MODE_FINITE = "FINITE";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String INVENTORY_STATUS_DELIVERED = "DELIVERED";

    private final MarketListingMapper marketListingMapper;
    private final MarketInventoryUnitMapper marketInventoryUnitMapper;
    private final MarketOrderMapper marketOrderMapper;
    private final MarketAddressMapper marketAddressMapper;
    private final WalletMarketActionApi walletMarketActionApi;

    public MarketOrderService(MarketListingMapper marketListingMapper,
                              MarketInventoryUnitMapper marketInventoryUnitMapper,
                              MarketOrderMapper marketOrderMapper,
                              MarketAddressMapper marketAddressMapper,
                              WalletMarketActionApi walletMarketActionApi) {
        this.marketListingMapper = marketListingMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
        this.marketOrderMapper = marketOrderMapper;
        this.marketAddressMapper = marketAddressMapper;
        this.walletMarketActionApi = walletMarketActionApi;
    }

    @Transactional
    public MarketOrderResponse createOrder(String requestId, int buyerUserId, long listingId, int quantity, Long addressId) {
        validateCreateOrderRequest(requestId, buyerUserId, listingId, quantity);
        MarketOrder existing = marketOrderMapper.selectByRequestId(requestId);
        if (existing != null) {
            return MarketOrderResponse.from(existing);
        }

        MarketListing listing = requireActiveListingForUpdate(listingId);
        validateBuyerAndQuantity(buyerUserId, listing, quantity);
        List<MarketInventoryUnit> reservedUnits = reserveInventoryIfNeeded(listing, quantity);

        long totalAmount = listing.getUnitPrice() * quantity;
        WalletMarketTxnView escrowTxn = walletMarketActionApi.escrowOrder(
                requestId + ":escrow",
                buyerUserId,
                totalAmount,
                "market-order:" + requestId
        );

        adjustFiniteStockAfterOrder(listing, quantity);

        MarketOrder order = new MarketOrder();
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
        order.setStatus(STATUS_ESCROWED);
        order.setEscrowTxnId(escrowTxn.txnId());
        if (GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType())) {
            snapshotAddress(order, requireActiveAddress(addressId, buyerUserId));
        }
        marketOrderMapper.insert(order);

        if (DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            reserveUnitsForOrder(order.getOrderId(), reservedUnits);
            marketOrderMapper.markDelivered(order.getOrderId(), Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
            marketInventoryUnitMapper.markDeliveredByOrder(order.getOrderId(), INVENTORY_STATUS_DELIVERED, new Date());
        }

        return MarketOrderResponse.from(reloadOrder(order.getOrderId()));
    }

    private void validateCreateOrderRequest(String requestId, int buyerUserId, long listingId, int quantity) {
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException(INVALID_ARGUMENT, "market order requestId must not be blank");
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

    private MarketListing requireActiveListingForUpdate(long listingId) {
        MarketListing listing = marketListingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (!STATUS_ACTIVE.equals(listing.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing is not active: listingId=" + listingId);
        }
        return listing;
    }

    private void validateBuyerAndQuantity(int buyerUserId, MarketListing listing, int quantity) {
        if (listing.getSellerUserId() == buyerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer cannot purchase own listing: listingId=" + listing.getListingId());
        }
        if (quantity < listing.getMinPurchaseQuantity() || quantity > listing.getMaxPurchaseQuantity()) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity is outside listing purchase limits: listingId=" + listing.getListingId());
        }
        boolean finiteStock = GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType()) || STOCK_MODE_FINITE.equals(listing.getStockMode());
        if (finiteStock && listing.getStockAvailable() < quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "listing stock is insufficient: listingId=" + listing.getListingId());
        }
    }

    private List<MarketInventoryUnit> reserveInventoryIfNeeded(MarketListing listing, int quantity) {
        if (!GOODS_TYPE_VIRTUAL.equals(listing.getGoodsType()) || !DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            return List.of();
        }
        List<MarketInventoryUnit> units = marketInventoryUnitMapper.selectAvailableForUpdate(listing.getListingId(), quantity);
        if (units.size() != quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "preloaded inventory is insufficient: listingId=" + listing.getListingId());
        }
        return units;
    }

    private void adjustFiniteStockAfterOrder(MarketListing listing, int quantity) {
        boolean finiteStock = GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType()) || STOCK_MODE_FINITE.equals(listing.getStockMode());
        if (!finiteStock) {
            return;
        }
        int nextAvailable = listing.getStockAvailable() - quantity;
        String nextStatus = nextAvailable <= 0 ? STATUS_SOLD_OUT : listing.getStatus();
        marketListingMapper.adjustStock(listing.getListingId(), listing.getSellerUserId(), 0, -quantity, nextStatus);
    }

    private void reserveUnitsForOrder(long orderId, List<MarketInventoryUnit> units) {
        for (MarketInventoryUnit unit : units) {
            int updated = marketInventoryUnitMapper.reserveForOrder(unit.getInventoryUnitId(), orderId);
            if (updated != 1) {
                throw new BusinessException(INVALID_ARGUMENT, "inventory reservation failed: inventoryUnitId=" + unit.getInventoryUnitId());
            }
        }
    }

    private MarketAddress requireActiveAddress(Long addressId, int buyerUserId) {
        if (addressId == null || addressId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "addressId must be positive for physical order");
        }
        MarketAddress address = marketAddressMapper.selectById(addressId);
        if (address == null || !"ACTIVE".equals(address.getStatus())) {
            throw new BusinessException(NOT_FOUND, "market address not found: addressId=" + addressId);
        }
        if (address.getUserId() != buyerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "address does not belong to buyer: addressId=" + addressId);
        }
        return address;
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

    private MarketOrder reloadOrder(long orderId) {
        MarketOrder order = marketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found after write: orderId=" + orderId);
        }
        return order;
    }
}
