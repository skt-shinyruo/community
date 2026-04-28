package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.result.MarketInventoryUnitResult;
import com.nowcoder.community.market.domain.model.MarketInventoryUnit;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketInventoryApplicationService {

    private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String INVENTORY_STATUS_AVAILABLE = "AVAILABLE";
    private static final String INVENTORY_STATUS_INVALID = "INVALID";

    private final MarketListingRepository marketListingMapper;
    private final MarketInventoryRepository marketInventoryUnitMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MarketInventoryApplicationService(MarketListingRepository marketListingMapper,
                                  MarketInventoryRepository marketInventoryUnitMapper) {
        this(marketListingMapper, marketInventoryUnitMapper, new UuidV7Generator());
    }

    MarketInventoryApplicationService(MarketListingRepository marketListingMapper,
                           MarketInventoryRepository marketInventoryUnitMapper,
                           UuidV7Generator idGenerator) {
        this.marketListingMapper = marketListingMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void appendInventory(AddMarketInventoryBatchCommand command) {
        validateInventoryRequest(command);
        MarketListing listing = requireOwnedListingForUpdate(command.listingId(), command.sellerUserId());
        ensurePreloadedListing(listing);

        for (String payload : command.payloads()) {
            MarketInventoryUnit unit = new MarketInventoryUnit();
            unit.setInventoryUnitId(idGenerator.next());
            unit.setListingId(command.listingId());
            unit.setSellerUserId(command.sellerUserId());
            unit.setPayloadType(command.payloadType().trim());
            unit.setPayloadContent(payload.trim());
            unit.setStatus(INVENTORY_STATUS_AVAILABLE);
            marketInventoryUnitMapper.insert(unit);
        }

        String nextStatus = STATUS_SOLD_OUT.equals(listing.getStatus()) ? STATUS_ACTIVE : listing.getStatus();
        int delta = command.payloads().size();
        marketListingMapper.adjustStock(command.listingId(), command.sellerUserId(), delta, delta, nextStatus);
    }

    public List<MarketInventoryUnitResult> listInventory(UUID listingId, UUID sellerUserId) {
        requireOwnedListing(listingId, sellerUserId);
        return marketInventoryUnitMapper.selectByListingId(listingId).stream()
                .map(MarketInventoryUnitResult::from)
                .toList();
    }

    @Transactional
    public void invalidateInventory(UUID inventoryUnitId, UUID sellerUserId) {
        MarketInventoryUnit unit = marketInventoryUnitMapper.selectById(inventoryUnitId);
        if (unit == null) {
            throw new BusinessException(NOT_FOUND, "market inventory not found: inventoryUnitId=" + inventoryUnitId);
        }
        if (!Objects.equals(unit.getSellerUserId(), sellerUserId)) {
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

    private void validateInventoryRequest(AddMarketInventoryBatchCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory request must not be null");
        }
        if (!StringUtils.hasText(command.payloadType())) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory payloadType must not be blank");
        }
        if (command.payloads() == null || command.payloads().isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory payloads must not be empty");
        }
        boolean hasBlankPayload = command.payloads().stream().anyMatch(payload -> !StringUtils.hasText(payload));
        if (hasBlankPayload) {
            throw new BusinessException(INVALID_ARGUMENT, "inventory payload must not be blank");
        }
    }

    private MarketListing requireOwnedListing(UUID listingId, UUID sellerUserId) {
        MarketListing listing = marketListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (!Objects.equals(listing.getSellerUserId(), sellerUserId)) {
            throw new BusinessException(FORBIDDEN, "market listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private MarketListing requireOwnedListingForUpdate(UUID listingId, UUID sellerUserId) {
        MarketListing listing = marketListingMapper.selectByIdForUpdate(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (!Objects.equals(listing.getSellerUserId(), sellerUserId)) {
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
