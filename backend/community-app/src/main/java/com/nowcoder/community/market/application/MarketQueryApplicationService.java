package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.domain.model.MarketDelivery;
import com.nowcoder.community.market.domain.model.MarketInventoryUnit;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketDeliveryRepository;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketShipmentRepository;
import com.nowcoder.community.market.application.result.MarketListingDetailResult;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.application.result.MarketOrderDetailResult;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketQueryApplicationService {

    private final MarketListingRepository marketListingMapper;
    private final MarketOrderRepository marketOrderMapper;
    private final MarketInventoryRepository marketInventoryUnitMapper;
    private final MarketDeliveryRepository marketDeliveryMapper;
    private final MarketShipmentRepository marketShipmentMapper;

    public MarketQueryApplicationService(MarketListingRepository marketListingMapper,
                              MarketOrderRepository marketOrderMapper,
                              MarketInventoryRepository marketInventoryUnitMapper,
                              MarketDeliveryRepository marketDeliveryMapper,
                              MarketShipmentRepository marketShipmentMapper) {
        this.marketListingMapper = marketListingMapper;
        this.marketOrderMapper = marketOrderMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
        this.marketDeliveryMapper = marketDeliveryMapper;
        this.marketShipmentMapper = marketShipmentMapper;
    }

    public List<MarketListingResult> listPublicListings() {
        return marketListingMapper.selectPublicListings().stream()
                .map(MarketListingResult::from)
                .toList();
    }

    public MarketListingDetailResult getListingDetail(UUID listingId) {
        MarketListing listing = marketListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        return MarketListingDetailResult.from(listing);
    }

    public List<MarketListingResult> listSellerListings(UUID sellerUserId) {
        return marketListingMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketListingResult::from)
                .toList();
    }

    public List<MarketOrderResult> listBuyingOrders(UUID buyerUserId) {
        return marketOrderMapper.selectByBuyerUserId(buyerUserId).stream()
                .map(MarketOrderResult::from)
                .toList();
    }

    public List<MarketOrderResult> listSellingOrders(UUID sellerUserId) {
        return marketOrderMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketOrderResult::from)
                .toList();
    }

    public MarketOrderDetailResult getOrderDetail(UUID orderId, UUID actorUserId) {
        MarketOrder order = marketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        if (!Objects.equals(order.getBuyerUserId(), actorUserId) && !Objects.equals(order.getSellerUserId(), actorUserId)) {
            throw new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=" + orderId);
        }
        List<String> deliveryContents = loadDeliveryContents(orderId, order.getGoodsType());
        return MarketOrderDetailResult.from(order, deliveryContents, marketShipmentMapper.selectByOrderId(orderId));
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
