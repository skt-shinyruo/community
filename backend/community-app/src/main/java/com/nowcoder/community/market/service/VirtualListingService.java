package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.UpdateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.entity.VirtualListing;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class VirtualListingService {

    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
    private static final String DELIVERY_MODE_MANUAL = "MANUAL";
    private static final String STOCK_MODE_FINITE = "FINITE";
    private static final String STOCK_MODE_UNLIMITED = "UNLIMITED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PAUSED = "PAUSED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STATUS_CLOSED = "CLOSED";

    private final VirtualListingMapper virtualListingMapper;
    private final VirtualInventoryService virtualInventoryService;

    public VirtualListingService(VirtualListingMapper virtualListingMapper,
                                 VirtualInventoryService virtualInventoryService) {
        this.virtualListingMapper = virtualListingMapper;
        this.virtualInventoryService = virtualInventoryService;
    }

    @Transactional
    public VirtualListingResponse createListing(int sellerUserId,
                                                CreateVirtualListingRequest request,
                                                AddVirtualInventoryBatchRequest inventoryRequest) {
        validateSellerUserId(sellerUserId);
        validateCreateRequest(request, inventoryRequest);

        VirtualListing listing = new VirtualListing();
        listing.setSellerUserId(sellerUserId);
        listing.setTitle(request.getTitle().trim());
        listing.setDescription(request.getDescription().trim());
        listing.setUnitPrice(request.getUnitPrice());
        listing.setDeliveryMode(request.getDeliveryMode().trim());
        listing.setStockMode(request.getStockMode().trim());
        listing.setStockTotal(initialStockTotal(request));
        listing.setStockAvailable(initialStockAvailable(request));
        listing.setMinPurchaseQuantity(request.getMinPurchaseQuantity());
        listing.setMaxPurchaseQuantity(request.getMaxPurchaseQuantity());
        listing.setStatus(initialStatus(request));
        virtualListingMapper.insert(listing);

        if (DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            virtualInventoryService.appendInventory(listing.getListingId(), sellerUserId, inventoryRequest);
        }

        return VirtualListingResponse.from(requireOwnedListing(listing.getListingId(), sellerUserId));
    }

    @Transactional
    public VirtualListingResponse updateListing(int sellerUserId, long listingId, UpdateVirtualListingRequest request) {
        validateSellerUserId(sellerUserId);
        validateUpdateRequest(request);
        VirtualListing listing = requireOwnedListing(listingId, sellerUserId);
        listing.setTitle(request.getTitle().trim());
        listing.setDescription(request.getDescription().trim());
        listing.setUnitPrice(request.getUnitPrice());
        listing.setMinPurchaseQuantity(request.getMinPurchaseQuantity());
        listing.setMaxPurchaseQuantity(request.getMaxPurchaseQuantity());
        virtualListingMapper.updateEditable(listing);
        return VirtualListingResponse.from(requireOwnedListing(listingId, sellerUserId));
    }

    @Transactional
    public VirtualListingResponse pauseListing(int sellerUserId, long listingId) {
        return transitionStatus(sellerUserId, listingId, STATUS_PAUSED);
    }

    @Transactional
    public VirtualListingResponse resumeListing(int sellerUserId, long listingId) {
        VirtualListing listing = requireOwnedListing(listingId, sellerUserId);
        String nextStatus = listing.getStockMode().equals(STOCK_MODE_FINITE) && listing.getStockAvailable() <= 0
                ? STATUS_SOLD_OUT
                : STATUS_ACTIVE;
        virtualListingMapper.updateStatus(listingId, sellerUserId, nextStatus);
        return VirtualListingResponse.from(requireOwnedListing(listingId, sellerUserId));
    }

    @Transactional
    public VirtualListingResponse closeListing(int sellerUserId, long listingId) {
        return transitionStatus(sellerUserId, listingId, STATUS_CLOSED);
    }

    private VirtualListingResponse transitionStatus(int sellerUserId, long listingId, String nextStatus) {
        validateSellerUserId(sellerUserId);
        requireOwnedListing(listingId, sellerUserId);
        virtualListingMapper.updateStatus(listingId, sellerUserId, nextStatus);
        return VirtualListingResponse.from(requireOwnedListing(listingId, sellerUserId));
    }

    private void validateCreateRequest(CreateVirtualListingRequest request, AddVirtualInventoryBatchRequest inventoryRequest) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing request must not be null");
        }
        validateCommonFields(request.getTitle(), request.getDescription(), request.getUnitPrice(),
                request.getMinPurchaseQuantity(), request.getMaxPurchaseQuantity());
        String deliveryMode = normalizeRequired(request.getDeliveryMode(), "deliveryMode");
        String stockMode = normalizeRequired(request.getStockMode(), "stockMode");
        if (!DELIVERY_MODE_PRELOADED.equals(deliveryMode) && !DELIVERY_MODE_MANUAL.equals(deliveryMode)) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing deliveryMode is invalid: " + deliveryMode);
        }
        if (!STOCK_MODE_FINITE.equals(stockMode) && !STOCK_MODE_UNLIMITED.equals(stockMode)) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing stockMode is invalid: " + stockMode);
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
            if (request.getStockTotal() != inventoryRequest.getPayloads().size()) {
                throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing stockTotal must equal inventory payload count");
            }
        }
        if (DELIVERY_MODE_MANUAL.equals(deliveryMode) && STOCK_MODE_FINITE.equals(stockMode) && request.getStockTotal() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "FINITE MANUAL listing stockTotal must be positive");
        }
    }

    private void validateUpdateRequest(UpdateVirtualListingRequest request) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing update request must not be null");
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
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing title is too long");
        }
        if (normalizedDescription.length() > 1000) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing description is too long");
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing unitPrice must be positive");
        }
        if (minPurchaseQuantity == null || minPurchaseQuantity <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing minPurchaseQuantity must be positive");
        }
        if (maxPurchaseQuantity == null || maxPurchaseQuantity < minPurchaseQuantity) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing maxPurchaseQuantity must be >= minPurchaseQuantity");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "virtual listing " + fieldName + " must not be blank");
        }
        return value.trim();
    }

    private int initialStockTotal(CreateVirtualListingRequest request) {
        return DELIVERY_MODE_PRELOADED.equals(request.getDeliveryMode().trim())
                ? 0
                : (STOCK_MODE_UNLIMITED.equals(request.getStockMode().trim()) ? 0 : request.getStockTotal());
    }

    private int initialStockAvailable(CreateVirtualListingRequest request) {
        return DELIVERY_MODE_PRELOADED.equals(request.getDeliveryMode().trim())
                ? 0
                : (STOCK_MODE_UNLIMITED.equals(request.getStockMode().trim()) ? 0 : request.getStockTotal());
    }

    private String initialStatus(CreateVirtualListingRequest request) {
        if (STOCK_MODE_FINITE.equals(request.getStockMode().trim())
                && DELIVERY_MODE_MANUAL.equals(request.getDeliveryMode().trim())
                && request.getStockTotal() == 0) {
            return STATUS_SOLD_OUT;
        }
        return STATUS_ACTIVE;
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

    private void validateSellerUserId(int sellerUserId) {
        if (sellerUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "sellerUserId must be positive");
        }
    }
}
