package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.command.CreateMarketListingCommand;
import com.nowcoder.community.market.application.command.UpdateMarketListingCommand;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.service.MarketListingDomainService;
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
public class MarketListingApplicationService {

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

    private final MarketListingRepository marketListingMapper;
    private final MarketInventoryApplicationService marketInventoryService;
    private final UuidV7Generator idGenerator;
    private final MarketListingDomainService listingDomainService = new MarketListingDomainService();

    @Autowired
    public MarketListingApplicationService(MarketListingRepository marketListingMapper,
                                MarketInventoryApplicationService marketInventoryService) {
        this(marketListingMapper, marketInventoryService, new UuidV7Generator());
    }

    MarketListingApplicationService(MarketListingRepository marketListingMapper,
                         MarketInventoryApplicationService marketInventoryService,
                         UuidV7Generator idGenerator) {
        this.marketListingMapper = marketListingMapper;
        this.marketInventoryService = marketInventoryService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketListingResult createListing(CreateMarketListingCommand command) {
        validateCreateRequest(command);
        listingDomainService.validateListingBasics(command.sellerUserId(), command.title(), command.unitPrice());

        MarketListing listing = new MarketListing();
        listing.setListingId(idGenerator.next());
        listing.setSellerUserId(command.sellerUserId());
        listing.setGoodsType(command.goodsType().trim());
        listing.setTitle(command.title().trim());
        listing.setDescription(command.description().trim());
        listing.setUnitPrice(command.unitPrice());
        listing.setDeliveryMode(normalizeOptional(command.deliveryMode()));
        listing.setStockMode(normalizeOptional(command.stockMode()));
        listing.setStockTotal(initialStockTotal(command));
        listing.setStockAvailable(initialStockAvailable(command));
        listing.setMinPurchaseQuantity(command.minPurchaseQuantity());
        listing.setMaxPurchaseQuantity(command.maxPurchaseQuantity());
        listing.setStatus(initialStatus(command));
        marketListingMapper.insert(listing);

        if (GOODS_TYPE_VIRTUAL.equals(listing.getGoodsType()) && DELIVERY_MODE_PRELOADED.equals(listing.getDeliveryMode())) {
            AddMarketInventoryBatchCommand inventory = command.inventory();
            marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                    listing.getListingId(),
                    command.sellerUserId(),
                    inventory.payloadType(),
                    inventory.payloads()
            ));
        }

        return MarketListingResult.from(requireOwnedListing(listing.getListingId(), command.sellerUserId()));
    }

    @Transactional
    public MarketListingResult updateListing(UpdateMarketListingCommand command) {
        validateUpdateRequest(command);
        listingDomainService.validateListingBasics(command.sellerUserId(), command.title(), command.unitPrice());
        MarketListing listing = requireOwnedListing(command.listingId(), command.sellerUserId());
        listing.setTitle(command.title().trim());
        listing.setDescription(command.description().trim());
        listing.setUnitPrice(command.unitPrice());
        listing.setMinPurchaseQuantity(command.minPurchaseQuantity());
        listing.setMaxPurchaseQuantity(command.maxPurchaseQuantity());
        marketListingMapper.updateEditable(listing);
        return MarketListingResult.from(requireOwnedListing(command.listingId(), command.sellerUserId()));
    }

    @Transactional
    public MarketListingResult pauseListing(UUID sellerUserId, UUID listingId) {
        return transitionStatus(sellerUserId, listingId, STATUS_PAUSED);
    }

    @Transactional
    public MarketListingResult resumeListing(UUID sellerUserId, UUID listingId) {
        MarketListing listing = requireOwnedListing(listingId, sellerUserId);
        String nextStatus = STOCK_MODE_FINITE.equals(listing.getStockMode()) && listing.getStockAvailable() <= 0
                ? STATUS_SOLD_OUT
                : STATUS_ACTIVE;
        marketListingMapper.updateStatus(listingId, sellerUserId, nextStatus);
        return MarketListingResult.from(requireOwnedListing(listingId, sellerUserId));
    }

    @Transactional
    public MarketListingResult closeListing(UUID sellerUserId, UUID listingId) {
        return transitionStatus(sellerUserId, listingId, STATUS_CLOSED);
    }

    private MarketListingResult transitionStatus(UUID sellerUserId, UUID listingId, String nextStatus) {
        validateSellerUserId(sellerUserId);
        requireOwnedListing(listingId, sellerUserId);
        marketListingMapper.updateStatus(listingId, sellerUserId, nextStatus);
        return MarketListingResult.from(requireOwnedListing(listingId, sellerUserId));
    }

    private void validateCreateRequest(CreateMarketListingCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing request must not be null");
        }
        validateCommonFields(command.title(), command.description(), command.unitPrice(),
                command.minPurchaseQuantity(), command.maxPurchaseQuantity());
        String goodsType = normalizeRequired(command.goodsType(), "goodsType");
        if (GOODS_TYPE_VIRTUAL.equals(goodsType)) {
            String deliveryMode = normalizeRequired(command.deliveryMode(), "deliveryMode");
            String stockMode = normalizeRequired(command.stockMode(), "stockMode");
            if (!Set.of(DELIVERY_MODE_PRELOADED, DELIVERY_MODE_MANUAL).contains(deliveryMode)) {
                throw new BusinessException(INVALID_ARGUMENT, "invalid deliveryMode: " + deliveryMode);
            }
            if (!Set.of(STOCK_MODE_FINITE, STOCK_MODE_UNLIMITED).contains(stockMode)) {
                throw new BusinessException(INVALID_ARGUMENT, "invalid stockMode: " + stockMode);
            }
            if (command.stockTotal() == null || command.stockTotal() < 0) {
                throw new BusinessException(INVALID_ARGUMENT, "virtual listing stockTotal must be non-negative");
            }
            if (DELIVERY_MODE_PRELOADED.equals(deliveryMode)) {
                if (!STOCK_MODE_FINITE.equals(stockMode)) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing must use FINITE stock");
                }
                if (command.inventory() == null || command.inventory().payloads() == null || command.inventory().payloads().isEmpty()) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing requires inventory payloads");
                }
                if (!command.stockTotal().equals(command.inventory().payloads().size())) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing stockTotal must equal inventory payload count");
                }
            }
            if (DELIVERY_MODE_MANUAL.equals(deliveryMode) && STOCK_MODE_FINITE.equals(stockMode) && command.stockTotal() <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "FINITE MANUAL listing stockTotal must be positive");
            }
            return;
        }

        if (!GOODS_TYPE_PHYSICAL.equals(goodsType)) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid goodsType: " + goodsType);
        }
        if (command.stockTotal() == null || command.stockTotal() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "physical listing stockTotal must be positive");
        }
        if (StringUtils.hasText(command.deliveryMode()) || StringUtils.hasText(command.stockMode()) || command.inventory() != null) {
            throw new BusinessException(INVALID_ARGUMENT, "physical listing must not use virtual delivery fields");
        }
    }

    private void validateUpdateRequest(UpdateMarketListingCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing update request must not be null");
        }
        validateCommonFields(command.title(), command.description(), command.unitPrice(),
                command.minPurchaseQuantity(), command.maxPurchaseQuantity());
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

    private int initialStockTotal(CreateMarketListingCommand command) {
        if (GOODS_TYPE_PHYSICAL.equals(command.goodsType().trim())) {
            return command.stockTotal();
        }
        return DELIVERY_MODE_PRELOADED.equals(command.deliveryMode().trim())
                ? 0
                : (STOCK_MODE_UNLIMITED.equals(command.stockMode().trim()) ? 0 : command.stockTotal());
    }

    private int initialStockAvailable(CreateMarketListingCommand command) {
        if (GOODS_TYPE_PHYSICAL.equals(command.goodsType().trim())) {
            return command.stockTotal();
        }
        return DELIVERY_MODE_PRELOADED.equals(command.deliveryMode().trim())
                ? 0
                : (STOCK_MODE_UNLIMITED.equals(command.stockMode().trim()) ? 0 : command.stockTotal());
    }

    private String initialStatus(CreateMarketListingCommand command) {
        if (GOODS_TYPE_PHYSICAL.equals(command.goodsType().trim())) {
            return command.stockTotal() <= 0 ? STATUS_SOLD_OUT : STATUS_ACTIVE;
        }
        if (STOCK_MODE_FINITE.equals(command.stockMode().trim())
                && DELIVERY_MODE_MANUAL.equals(command.deliveryMode().trim())
                && command.stockTotal() == 0) {
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
