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
import com.nowcoder.community.market.application.result.MarketPageResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketQueryApplicationService {

    private final MarketListingRepository marketListingRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final MarketInventoryRepository marketInventoryRepository;
    private final MarketDeliveryRepository marketDeliveryRepository;
    private final MarketShipmentRepository marketShipmentRepository;

    public MarketQueryApplicationService(MarketListingRepository marketListingRepository,
                              MarketOrderRepository marketOrderRepository,
                              MarketInventoryRepository marketInventoryRepository,
                              MarketDeliveryRepository marketDeliveryRepository,
                              MarketShipmentRepository marketShipmentRepository) {
        this.marketListingRepository = marketListingRepository;
        this.marketOrderRepository = marketOrderRepository;
        this.marketInventoryRepository = marketInventoryRepository;
        this.marketDeliveryRepository = marketDeliveryRepository;
        this.marketShipmentRepository = marketShipmentRepository;
    }

    public MarketPageResult<MarketListingResult> listPublicListings(Integer page, Integer size) {
        MarketPagination.Window window = MarketPagination.window(page, size);
        List<MarketListingResult> candidates = marketListingRepository
                .findPublicListings(window.offset(), window.queryLimit()).stream()
                .map(MarketListingResult::from)
                .toList();
        return MarketPagination.result(candidates, window);
    }

    public List<MarketListingResult> listPublicListings() {
        return listPublicListings(null, null).items();
    }

    public MarketListingDetailResult getListingDetail(UUID listingId) {
        MarketListing listing = marketListingRepository.findById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        return MarketListingDetailResult.from(listing);
    }

    public MarketPageResult<MarketListingResult> listSellerListings(UUID sellerUserId, Integer page, Integer size) {
        MarketPagination.Window window = MarketPagination.window(page, size);
        List<MarketListingResult> candidates = marketListingRepository
                .findBySellerUserId(sellerUserId, window.offset(), window.queryLimit()).stream()
                .map(MarketListingResult::from)
                .toList();
        return MarketPagination.result(candidates, window);
    }

    public List<MarketListingResult> listSellerListings(UUID sellerUserId) {
        return listSellerListings(sellerUserId, null, null).items();
    }

    public MarketPageResult<MarketOrderResult> listBuyingOrders(UUID buyerUserId, Integer page, Integer size) {
        MarketPagination.Window window = MarketPagination.window(page, size);
        List<MarketOrderResult> candidates = marketOrderRepository
                .findByBuyerUserId(buyerUserId, window.offset(), window.queryLimit()).stream()
                .map(MarketOrderResult::from)
                .toList();
        return MarketPagination.result(candidates, window);
    }

    public List<MarketOrderResult> listBuyingOrders(UUID buyerUserId) {
        return listBuyingOrders(buyerUserId, null, null).items();
    }

    public MarketPageResult<MarketOrderResult> listSellingOrders(UUID sellerUserId, Integer page, Integer size) {
        MarketPagination.Window window = MarketPagination.window(page, size);
        List<MarketOrderResult> candidates = marketOrderRepository
                .findBySellerUserId(sellerUserId, window.offset(), window.queryLimit()).stream()
                .map(MarketOrderResult::from)
                .toList();
        return MarketPagination.result(candidates, window);
    }

    public List<MarketOrderResult> listSellingOrders(UUID sellerUserId) {
        return listSellingOrders(sellerUserId, null, null).items();
    }

    public MarketOrderDetailResult getOrderDetail(UUID orderId, UUID actorUserId) {
        MarketOrder order = marketOrderRepository.findById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        if (!Objects.equals(order.getBuyerUserId(), actorUserId) && !Objects.equals(order.getSellerUserId(), actorUserId)) {
            throw new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=" + orderId);
        }
        List<String> deliveryContents = loadDeliveryContents(orderId, order.getGoodsType());
        return MarketOrderDetailResult.from(order, deliveryContents, marketShipmentRepository.findByOrderId(orderId));
    }

    private List<String> loadDeliveryContents(UUID orderId, String goodsType) {
        if (!"VIRTUAL".equals(goodsType)) {
            return List.of();
        }
        List<String> manualDeliveries = marketDeliveryRepository.findByOrderId(orderId).stream()
                .filter(delivery -> "DELIVERED".equals(delivery.getStatus()))
                .map(MarketDelivery::getDeliveryContent)
                .toList();
        if (!manualDeliveries.isEmpty()) {
            return manualDeliveries;
        }
        return marketInventoryRepository.findByReservedOrderId(orderId).stream()
                .filter(unit -> "DELIVERED".equals(unit.getStatus()))
                .map(MarketInventoryUnit::getPayloadContent)
                .toList();
    }
}
