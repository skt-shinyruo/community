package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.VirtualListingDetailResponse;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.dto.VirtualOrderDetailResponse;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.market.entity.VirtualListing;
import com.nowcoder.community.market.entity.VirtualOrder;
import com.nowcoder.community.market.mapper.VirtualDeliveryMapper;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
import com.nowcoder.community.market.mapper.VirtualOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class VirtualMarketQueryService {

    private final VirtualListingMapper virtualListingMapper;
    private final VirtualOrderMapper virtualOrderMapper;
    private final VirtualDeliveryMapper virtualDeliveryMapper;

    public VirtualMarketQueryService(VirtualListingMapper virtualListingMapper,
                                     VirtualOrderMapper virtualOrderMapper,
                                     VirtualDeliveryMapper virtualDeliveryMapper) {
        this.virtualListingMapper = virtualListingMapper;
        this.virtualOrderMapper = virtualOrderMapper;
        this.virtualDeliveryMapper = virtualDeliveryMapper;
    }

    public List<VirtualListingResponse> listPublicListings() {
        return virtualListingMapper.selectPublicListings().stream()
                .map(VirtualListingResponse::from)
                .toList();
    }

    public VirtualListingDetailResponse getListingDetail(long listingId) {
        VirtualListing listing = virtualListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "virtual listing not found: listingId=" + listingId);
        }
        return VirtualListingDetailResponse.from(listing);
    }

    public List<VirtualListingResponse> listSellerListings(int sellerUserId) {
        return virtualListingMapper.selectBySellerUserId(sellerUserId).stream()
                .map(VirtualListingResponse::from)
                .toList();
    }

    public List<VirtualOrderResponse> listBuyingOrders(int buyerUserId) {
        return virtualOrderMapper.selectByBuyerUserId(buyerUserId).stream()
                .map(VirtualOrderResponse::from)
                .toList();
    }

    public List<VirtualOrderResponse> listSellingOrders(int sellerUserId) {
        return virtualOrderMapper.selectBySellerUserId(sellerUserId).stream()
                .map(VirtualOrderResponse::from)
                .toList();
    }

    public VirtualOrderDetailResponse getOrderDetail(long orderId, int actorUserId) {
        VirtualOrder order = virtualOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "virtual order not found: orderId=" + orderId);
        }
        if (order.getBuyerUserId() != actorUserId && order.getSellerUserId() != actorUserId) {
            throw new BusinessException(FORBIDDEN, "virtual order does not belong to actor: orderId=" + orderId);
        }
        return VirtualOrderDetailResponse.from(order, virtualDeliveryMapper.selectByOrderId(orderId));
    }
}
