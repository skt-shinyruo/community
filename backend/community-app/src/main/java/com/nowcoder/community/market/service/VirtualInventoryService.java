package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.VirtualInventoryUnitResponse;
import com.nowcoder.community.market.entity.VirtualInventoryUnit;
import com.nowcoder.community.market.entity.VirtualListing;
import com.nowcoder.community.market.mapper.VirtualInventoryUnitMapper;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class VirtualInventoryService {

    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String INVENTORY_STATUS_AVAILABLE = "AVAILABLE";
    private static final String INVENTORY_STATUS_INVALID = "INVALID";

    private final VirtualListingMapper virtualListingMapper;
    private final VirtualInventoryUnitMapper virtualInventoryUnitMapper;

    public VirtualInventoryService(VirtualListingMapper virtualListingMapper,
                                   VirtualInventoryUnitMapper virtualInventoryUnitMapper) {
        this.virtualListingMapper = virtualListingMapper;
        this.virtualInventoryUnitMapper = virtualInventoryUnitMapper;
    }

    @Transactional
    public void appendInventory(long listingId, int sellerUserId, AddVirtualInventoryBatchRequest request) {
        validateInventoryRequest(request);
        VirtualListing listing = requireOwnedListingForUpdate(listingId, sellerUserId);
        ensurePreloadedListing(listing);

        for (String payload : request.getPayloads()) {
            VirtualInventoryUnit unit = new VirtualInventoryUnit();
            unit.setListingId(listingId);
            unit.setSellerUserId(sellerUserId);
            unit.setPayloadType(request.getPayloadType().trim());
            unit.setPayloadContent(payload.trim());
            unit.setStatus(INVENTORY_STATUS_AVAILABLE);
            virtualInventoryUnitMapper.insert(unit);
        }

        String nextStatus = STATUS_SOLD_OUT.equals(listing.getStatus()) ? STATUS_ACTIVE : listing.getStatus();
        virtualListingMapper.adjustStock(listingId, sellerUserId, request.getPayloads().size(), request.getPayloads().size(), nextStatus);
    }

    public List<VirtualInventoryUnitResponse> listInventory(long listingId, int sellerUserId) {
        requireOwnedListing(listingId, sellerUserId);
        return virtualInventoryUnitMapper.selectByListingId(listingId).stream()
                .map(VirtualInventoryUnitResponse::from)
                .toList();
    }

    @Transactional
    public void invalidateInventory(long inventoryUnitId, int sellerUserId) {
        VirtualInventoryUnit unit = virtualInventoryUnitMapper.selectById(inventoryUnitId);
        if (unit == null) {
            throw new BusinessException(NOT_FOUND, "virtual inventory not found: inventoryUnitId=" + inventoryUnitId);
        }
        if (unit.getSellerUserId() != sellerUserId) {
            throw new BusinessException(FORBIDDEN, "virtual inventory does not belong to seller: inventoryUnitId=" + inventoryUnitId);
        }
        if (!INVENTORY_STATUS_AVAILABLE.equals(unit.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual inventory is not invalidatable: inventoryUnitId=" + inventoryUnitId);
        }

        VirtualListing listing = requireOwnedListingForUpdate(unit.getListingId(), sellerUserId);
        if (virtualInventoryUnitMapper.invalidateAvailable(inventoryUnitId, sellerUserId) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual inventory invalidation failed: inventoryUnitId=" + inventoryUnitId);
        }

        String nextStatus = listing.getStockAvailable() - 1 <= 0 ? STATUS_SOLD_OUT : listing.getStatus();
        virtualListingMapper.adjustStock(listing.getListingId(), sellerUserId, -1, -1, nextStatus);
    }

    private void validateInventoryRequest(AddVirtualInventoryBatchRequest request) {
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

    private VirtualListing requireOwnedListing(long listingId, int sellerUserId) {
        VirtualListing listing = virtualListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "virtual listing not found: listingId=" + listingId);
        }
        if (listing.getSellerUserId() != sellerUserId) {
            throw new BusinessException(FORBIDDEN, "virtual listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private VirtualListing requireOwnedListingForUpdate(long listingId, int sellerUserId) {
        VirtualListing listing = virtualListingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "virtual listing not found: listingId=" + listingId);
        }
        if (listing.getSellerUserId() != sellerUserId) {
            throw new BusinessException(FORBIDDEN, "virtual listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private void ensurePreloadedListing(VirtualListing listing) {
        if (!DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing is not PRELOADED: listingId=" + listing.getListingId());
        }
    }
}
