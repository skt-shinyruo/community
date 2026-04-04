package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.dto.MarketInventoryUnitResponse;
import com.nowcoder.community.market.entity.MarketInventoryUnit;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketInventoryService {

    private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String INVENTORY_STATUS_AVAILABLE = "AVAILABLE";
    private static final String INVENTORY_STATUS_INVALID = "INVALID";

    private final MarketListingMapper marketListingMapper;
    private final MarketInventoryUnitMapper marketInventoryUnitMapper;

    public MarketInventoryService(MarketListingMapper marketListingMapper,
                                  MarketInventoryUnitMapper marketInventoryUnitMapper) {
        this.marketListingMapper = marketListingMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
    }

    @Transactional
    public void appendInventory(long listingId, int sellerUserId, AddMarketInventoryBatchRequest request) {
        validateInventoryRequest(request);
        MarketListing listing = requireOwnedListingForUpdate(listingId, sellerUserId);
        ensurePreloadedListing(listing);

        for (String payload : request.getPayloads()) {
            MarketInventoryUnit unit = new MarketInventoryUnit();
            unit.setListingId(listingId);
            unit.setSellerUserId(sellerUserId);
            unit.setPayloadType(request.getPayloadType().trim());
            unit.setPayloadContent(payload.trim());
            unit.setStatus(INVENTORY_STATUS_AVAILABLE);
            marketInventoryUnitMapper.insert(unit);
        }

        String nextStatus = STATUS_SOLD_OUT.equals(listing.getStatus()) ? STATUS_ACTIVE : listing.getStatus();
        int delta = request.getPayloads().size();
        marketListingMapper.adjustStock(listingId, sellerUserId, delta, delta, nextStatus);
    }

    public List<MarketInventoryUnitResponse> listInventory(long listingId, int sellerUserId) {
        requireOwnedListing(listingId, sellerUserId);
        return marketInventoryUnitMapper.selectByListingId(listingId).stream()
                .map(MarketInventoryUnitResponse::from)
                .toList();
    }

    @Transactional
    public void invalidateInventory(long inventoryUnitId, int sellerUserId) {
        MarketInventoryUnit unit = marketInventoryUnitMapper.selectById(inventoryUnitId);
        if (unit == null) {
            throw new BusinessException(NOT_FOUND, "market inventory not found: inventoryUnitId=" + inventoryUnitId);
        }
        if (unit.getSellerUserId() != sellerUserId) {
            throw new BusinessException(FORBIDDEN, "market inventory does not belong to seller: inventoryUnitId=" + inventoryUnitId);
        }
        if (!INVENTORY_STATUS_AVAILABLE.equals(unit.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "market inventory is not invalidatable: inventoryUnitId=" + inventoryUnitId);
        }

        MarketListing listing = requireOwnedListingForUpdate(unit.getListingId(), sellerUserId);
        if (marketInventoryUnitMapper.invalidateAvailable(inventoryUnitId, sellerUserId) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "market inventory invalidation failed: inventoryUnitId=" + inventoryUnitId);
        }

        String nextStatus = listing.getStockAvailable() - 1 <= 0 ? STATUS_SOLD_OUT : listing.getStatus();
        marketListingMapper.adjustStock(listing.getListingId(), sellerUserId, -1, -1, nextStatus);
    }

    private void validateInventoryRequest(AddMarketInventoryBatchRequest request) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory request must not be null");
        }
        if (!StringUtils.hasText(request.getPayloadType())) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory payloadType must not be blank");
        }
        if (request.getPayloads() == null || request.getPayloads().isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory payloads must not be empty");
        }
        boolean hasBlankPayload = request.getPayloads().stream().anyMatch(payload -> !StringUtils.hasText(payload));
        if (hasBlankPayload) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory payload must not be blank");
        }
    }

    private MarketListing requireOwnedListing(long listingId, int sellerUserId) {
        MarketListing listing = marketListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (listing.getSellerUserId() != sellerUserId) {
            throw new BusinessException(FORBIDDEN, "market listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private MarketListing requireOwnedListingForUpdate(long listingId, int sellerUserId) {
        MarketListing listing = marketListingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (listing.getSellerUserId() != sellerUserId) {
            throw new BusinessException(FORBIDDEN, "market listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private void ensurePreloadedListing(MarketListing listing) {
        if (!GOODS_TYPE_VIRTUAL.equals(listing.getGoodsType()) || !DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing is not PRELOADED virtual: listingId=" + listing.getListingId());
        }
    }
}
