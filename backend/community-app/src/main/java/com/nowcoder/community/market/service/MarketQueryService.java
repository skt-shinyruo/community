package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.MarketListingDetailResponse;
import com.nowcoder.community.market.dto.MarketListingResponse;
import com.nowcoder.community.market.dto.MarketOrderDetailResponse;
import com.nowcoder.community.market.dto.MarketOrderResponse;
import com.nowcoder.community.market.entity.MarketDelivery;
import com.nowcoder.community.market.entity.MarketInventoryUnit;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketDeliveryMapper;
import com.nowcoder.community.market.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import com.nowcoder.community.market.mapper.MarketShipmentMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketQueryService {

    private final MarketListingMapper marketListingMapper;
    private final MarketOrderMapper marketOrderMapper;
    private final MarketInventoryUnitMapper marketInventoryUnitMapper;
    private final MarketDeliveryMapper marketDeliveryMapper;
    private final MarketShipmentMapper marketShipmentMapper;

    public MarketQueryService(MarketListingMapper marketListingMapper,
                              MarketOrderMapper marketOrderMapper,
                              MarketInventoryUnitMapper marketInventoryUnitMapper,
                              MarketDeliveryMapper marketDeliveryMapper,
                              MarketShipmentMapper marketShipmentMapper) {
        this.marketListingMapper = marketListingMapper;
        this.marketOrderMapper = marketOrderMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
        this.marketDeliveryMapper = marketDeliveryMapper;
        this.marketShipmentMapper = marketShipmentMapper;
    }

    public List<MarketListingResponse> listPublicListings() {
        return marketListingMapper.selectPublicListings().stream()
                .map(MarketListingResponse::from)
                .toList();
    }

    public MarketListingDetailResponse getListingDetail(UUID listingId) {
        MarketListing listing = marketListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        return MarketListingDetailResponse.from(listing);
    }

    public List<MarketListingResponse> listSellerListings(UUID sellerUserId) {
        return marketListingMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketListingResponse::from)
                .toList();
    }

    public List<MarketOrderResponse> listBuyingOrders(UUID buyerUserId) {
        return marketOrderMapper.selectByBuyerUserId(buyerUserId).stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    public List<MarketOrderResponse> listSellingOrders(UUID sellerUserId) {
        return marketOrderMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    public MarketOrderDetailResponse getOrderDetail(UUID orderId, UUID actorUserId) {
        MarketOrder order = marketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        if (!Objects.equals(order.getBuyerUserId(), actorUserId) && !Objects.equals(order.getSellerUserId(), actorUserId)) {
            throw new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=" + orderId);
        }
        List<String> deliveryContents = loadDeliveryContents(orderId, order.getGoodsType());
        return MarketOrderDetailResponse.from(order, deliveryContents, marketShipmentMapper.selectByOrderId(orderId));
    }

    private List<String> loadDeliveryContents(UUID orderId, String goodsType) {
        if (!"VIRTUAL".equals(goodsType)) {
            return List.of();
        }
        List<String> manualDeliveries = marketDeliveryMapper.selectByOrderId(orderId).stream()
                .filter(delivery -> "DELIVERED".equals(delivery.getStatus()))
                .map(MarketDelivery::getDeliveryContent)
                .toList();
        if (!manualDeliveries.isEmpty()) {
            return manualDeliveries;
        }
        return marketInventoryUnitMapper.selectByReservedOrderId(orderId).stream()
                .filter(unit -> "DELIVERED".equals(unit.getStatus()))
                .map(MarketInventoryUnit::getPayloadContent)
                .toList();
    }
}
