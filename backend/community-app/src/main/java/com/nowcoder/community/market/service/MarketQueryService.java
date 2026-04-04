package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.MarketListingDetailResponse;
import com.nowcoder.community.market.dto.MarketListingResponse;
import com.nowcoder.community.market.dto.MarketOrderDetailResponse;
import com.nowcoder.community.market.dto.MarketOrderResponse;
import com.nowcoder.community.market.entity.MarketInventoryUnit;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import com.nowcoder.community.market.mapper.MarketShipmentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketQueryService {

    private final MarketListingMapper marketListingMapper;
    private final MarketOrderMapper marketOrderMapper;
    private final MarketInventoryUnitMapper marketInventoryUnitMapper;
    private final MarketShipmentMapper marketShipmentMapper;

    public MarketQueryService(MarketListingMapper marketListingMapper,
                              MarketOrderMapper marketOrderMapper,
                              MarketInventoryUnitMapper marketInventoryUnitMapper,
                              MarketShipmentMapper marketShipmentMapper) {
        this.marketListingMapper = marketListingMapper;
        this.marketOrderMapper = marketOrderMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
        this.marketShipmentMapper = marketShipmentMapper;
    }

    public List<MarketListingResponse> listPublicListings() {
        return marketListingMapper.selectPublicListings().stream()
                .map(MarketListingResponse::from)
                .toList();
    }

    public MarketListingDetailResponse getListingDetail(long listingId) {
        MarketListing listing = marketListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        return MarketListingDetailResponse.from(listing);
    }

    public List<MarketListingResponse> listSellerListings(int sellerUserId) {
        return marketListingMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketListingResponse::from)
                .toList();
    }

    public List<MarketOrderResponse> listBuyingOrders(int buyerUserId) {
        return marketOrderMapper.selectByBuyerUserId(buyerUserId).stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    public List<MarketOrderResponse> listSellingOrders(int sellerUserId) {
        return marketOrderMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    public MarketOrderDetailResponse getOrderDetail(long orderId, int actorUserId) {
        MarketOrder order = marketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        if (order.getBuyerUserId() != actorUserId && order.getSellerUserId() != actorUserId) {
            throw new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=" + orderId);
        }
        List<String> deliveryContents = "VIRTUAL".equals(order.getGoodsType())
                ? marketInventoryUnitMapper.selectByReservedOrderId(orderId).stream()
                .filter(unit -> "DELIVERED".equals(unit.getStatus()))
                .map(MarketInventoryUnit::getPayloadContent)
                .toList()
                : List.of();
        return MarketOrderDetailResponse.from(order, deliveryContents, marketShipmentMapper.selectByOrderId(orderId));
    }
}
