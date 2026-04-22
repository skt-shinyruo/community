package com.nowcoder.community.market.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.dto.MarketListingResponse;
import com.nowcoder.community.market.dto.UpdateMarketListingRequest;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketListingService {

    private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
    private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String DELIVERY_MODE_MANUAL = "MANUAL";
    private static final String STOCK_MODE_FINITE = "FINITE";
    private static final String STOCK_MODE_UNLIMITED = "UNLIMITED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PAUSED = "PAUSED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STATUS_CLOSED = "CLOSED";

    private final MarketListingMapper marketListingMapper;
    private final MarketInventoryService marketInventoryService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MarketListingService(MarketListingMapper marketListingMapper,
                                MarketInventoryService marketInventoryService) {
        this(marketListingMapper, marketInventoryService, new UuidV7Generator());
    }

    MarketListingService(MarketListingMapper marketListingMapper,
                         MarketInventoryService marketInventoryService,
                         UuidV7Generator idGenerator) {
        this.marketListingMapper = marketListingMapper;
        this.marketInventoryService = marketInventoryService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketListingResponse createListing(UUID sellerUserId,
                                               CreateMarketListingRequest request,
                                               AddMarketInventoryBatchRequest inventoryRequest) {
        validateSellerUserId(sellerUserId);
        validateCreateRequest(request, inventoryRequest);

        MarketListing listing = new MarketListing();
        listing.setListingId(idGenerator.next());
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType(request.getGoodsType().trim());
        listing.setTitle(request.getTitle().trim());
        listing.setDescription(request.getDescription().trim());
        listing.setUnitPrice(request.getUnitPrice());
        listing.setDeliveryMode(normalizeOptional(request.getDeliveryMode()));
        listing.setStockMode(normalizeOptional(request.getStockMode()));
        listing.setStockTotal(initialStockTotal(request));
        listing.setStockAvailable(initialStockAvailable(request));
        listing.setMinPurchaseQuantity(request.getMinPurchaseQuantity());
        listing.setMaxPurchaseQuantity(request.getMaxPurchaseQuantity());
        listing.setStatus(initialStatus(request));
        marketListingMapper.insert(listing);

        if (GOODS_TYPE_VIRTUAL.equals(listing.getGoodsType()) && DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            marketInventoryService.appendInventory(listing.getListingId(), sellerUserId, inventoryRequest);
        }

        return MarketListingResponse.from(requireOwnedListing(listing.getListingId(), sellerUserId));
    }

    @Transactional
    public MarketListingResponse updateListing(UUID sellerUserId, UUID listingId, UpdateMarketListingRequest request) {
        validateSellerUserId(sellerUserId);
        validateUpdateRequest(request);
        MarketListing listing = requireOwnedListing(listingId, sellerUserId);
        listing.setTitle(request.getTitle().trim());
        listing.setDescription(request.getDescription().trim());
        listing.setUnitPrice(request.getUnitPrice());
        listing.setMinPurchaseQuantity(request.getMinPurchaseQuantity());
        listing.setMaxPurchaseQuantity(request.getMaxPurchaseQuantity());
        marketListingMapper.updateEditable(listing);
        return MarketListingResponse.from(requireOwnedListing(listingId, sellerUserId));
    }

    @Transactional
    public MarketListingResponse pauseListing(UUID sellerUserId, UUID listingId) {
        return transitionStatus(sellerUserId, listingId, STATUS_PAUSED);
    }

    @Transactional
    public MarketListingResponse resumeListing(UUID sellerUserId, UUID listingId) {
        MarketListing listing = requireOwnedListing(listingId, sellerUserId);
        String nextStatus = STOCK_MODE_FINITE.equals(listing.getStockMode()) && listing.getStockAvailable() <= 0
                ? STATUS_SOLD_OUT
                : STATUS_ACTIVE;
        marketListingMapper.updateStatus(listingId, sellerUserId, nextStatus);
        return MarketListingResponse.from(requireOwnedListing(listingId, sellerUserId));
    }

    @Transactional
    public MarketListingResponse closeListing(UUID sellerUserId, UUID listingId) {
        return transitionStatus(sellerUserId, listingId, STATUS_CLOSED);
    }

    private MarketListingResponse transitionStatus(UUID sellerUserId, UUID listingId, String nextStatus) {
        validateSellerUserId(sellerUserId);
        requireOwnedListing(listingId, sellerUserId);
        marketListingMapper.updateStatus(listingId, sellerUserId, nextStatus);
        return MarketListingResponse.from(requireOwnedListing(listingId, sellerUserId));
    }

    private void validateCreateRequest(CreateMarketListingRequest request, AddMarketInventoryBatchRequest inventoryRequest) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing request must not be null");
        }
        validateCommonFields(request.getTitle(), request.getDescription(), request.getUnitPrice(),
                request.getMinPurchaseQuantity(), request.getMaxPurchaseQuantity());
        String goodsType = normalizeRequired(request.getGoodsType(), "goodsType");
        if (GOODS_TYPE_VIRTUAL.equals(goodsType)) {
            String deliveryMode = normalizeRequired(request.getDeliveryMode(), "deliveryMode");
            String stockMode = normalizeRequired(request.getStockMode(), "stockMode");
            if (!Set.of(DELIVERY_MODE_PRELOADED, DELIVERY_MODE_MANUAL).contains(deliveryMode)) {
                throw new BusinessException(INVALID_ARGUMENT, "invalid deliveryMode: " + deliveryMode);
            }
            if (!Set.of(STOCK_MODE_FINITE, STOCK_MODE_UNLIMITED).contains(stockMode)) {
                throw new BusinessException(INVALID_ARGUMENT, "invalid stockMode: " + stockMode);
            }
            if (request.getStockTotal() == null || request.getStockTotal() < 0) {
                throw new BusinessException(INVALID_ARGUMENT, "virtual listing stockTotal must be non-negative");
            }
            if (DELIVERY_MODE_PRELOADED.equals(deliveryMode)) {
                if (!STOCK_MODE_FINITE.equals(stockMode)) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing must use FINITE stock");
                }
                if (inventoryRequest == null || inventoryRequest.getPayloads() == null || inventoryRequest.getPayloads().isEmpty()) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing requires inventory payloads");
                }
                if (!request.getStockTotal().equals(inventoryRequest.getPayloads().size())) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing stockTotal must equal inventory payload count");
                }
            }
            if (DELIVERY_MODE_MANUAL.equals(deliveryMode) && STOCK_MODE_FINITE.equals(stockMode) && request.getStockTotal() <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "FINITE MANUAL listing stockTotal must be positive");
            }
            return;
        }

        if (!GOODS_TYPE_PHYSICAL.equals(goodsType)) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid goodsType: " + goodsType);
        }
        if (request.getStockTotal() == null || request.getStockTotal() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "physical listing stockTotal must be positive");
        }
        if (StringUtils.hasText(request.getDeliveryMode()) || StringUtils.hasText(request.getStockMode()) || inventoryRequest != null) {
            throw new BusinessException(INVALID_ARGUMENT, "physical listing must not use virtual delivery fields");
        }
    }

    private void validateUpdateRequest(UpdateMarketListingRequest request) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing update request must not be null");
        }
        validateCommonFields(request.getTitle(), request.getDescription(), request.getUnitPrice(),
                request.getMinPurchaseQuantity(), request.getMaxPurchaseQuantity());
    }

    private void validateCommonFields(String title,
                                      String description,
                                      Long unitPrice,
                                      Integer minPurchaseQuantity,
                                      Integer maxPurchaseQuantity) {
        String normalizedTitle = normalizeRequired(title, "title");
        String normalizedDescription = normalizeRequired(description, "description");
        if (normalizedTitle.length() > 128) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing title is too long");
        }
        if (normalizedDescription.length() > 1000) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing description is too long");
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing unitPrice must be positive");
        }
        if (minPurchaseQuantity == null || minPurchaseQuantity <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing minPurchaseQuantity must be positive");
        }
        if (maxPurchaseQuantity == null || maxPurchaseQuantity < minPurchaseQuantity) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing maxPurchaseQuantity must be >= minPurchaseQuantity");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing " + fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int initialStockTotal(CreateMarketListingRequest request) {
        if (GOODS_TYPE_PHYSICAL.equals(request.getGoodsType().trim())) {
            return request.getStockTotal();
        }
        return DELIVERY_MODE_PRELOADED.equals(request.getDeliveryMode().trim())
                ? 0
                : (STOCK_MODE_UNLIMITED.equals(request.getStockMode().trim()) ? 0 : request.getStockTotal());
    }

    private int initialStockAvailable(CreateMarketListingRequest request) {
        if (GOODS_TYPE_PHYSICAL.equals(request.getGoodsType().trim())) {
            return request.getStockTotal();
        }
        return DELIVERY_MODE_PRELOADED.equals(request.getDeliveryMode().trim())
                ? 0
                : (STOCK_MODE_UNLIMITED.equals(request.getStockMode().trim()) ? 0 : request.getStockTotal());
    }

    private String initialStatus(CreateMarketListingRequest request) {
        if (GOODS_TYPE_PHYSICAL.equals(request.getGoodsType().trim())) {
            return request.getStockTotal() <= 0 ? STATUS_SOLD_OUT : STATUS_ACTIVE;
        }
        if (STOCK_MODE_FINITE.equals(request.getStockMode().trim())
                && DELIVERY_MODE_MANUAL.equals(request.getDeliveryMode().trim())
                && request.getStockTotal() == 0) {
            return STATUS_SOLD_OUT;
        }
        return STATUS_ACTIVE;
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

    private void validateSellerUserId(UUID sellerUserId) {
        if (sellerUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "sellerUserId must not be null");
        }
    }
}
