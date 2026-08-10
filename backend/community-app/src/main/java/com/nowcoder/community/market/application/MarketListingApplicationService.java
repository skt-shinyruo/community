package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.domain.model.MarketDeliveryMode;
import com.nowcoder.community.market.domain.model.MarketGoodsType;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketListingStatus;
import com.nowcoder.community.market.domain.model.MarketStockMode;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.service.MarketListingDomainService;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketListingApplicationService {

    public record CreateMarketListingCommand(
            UUID sellerUserId,
            String goodsType,
            String title,
            String description,
            Long unitPrice,
            String deliveryMode,
            String stockMode,
            Integer stockTotal,
            Integer minPurchaseQuantity,
            Integer maxPurchaseQuantity,
            AddMarketInventoryBatchCommand inventory
    ) {
    }

    public record UpdateMarketListingCommand(
            UUID sellerUserId,
            UUID listingId,
            String title,
            String description,
            Long unitPrice,
            Integer minPurchaseQuantity,
            Integer maxPurchaseQuantity
    ) {
    }

    private final MarketListingRepository marketListingRepository;
    private final MarketInventoryApplicationService marketInventoryService;
    private final UuidV7Generator idGenerator;
    private final MarketListingDomainService listingDomainService = new MarketListingDomainService();

    @Autowired
    public MarketListingApplicationService(MarketListingRepository marketListingRepository,
                                           MarketInventoryApplicationService marketInventoryService,
                                           UuidV7Generator idGenerator) {
        this.marketListingRepository = Objects.requireNonNull(marketListingRepository, "marketListingRepository must not be null");
        this.marketInventoryService = Objects.requireNonNull(marketInventoryService, "marketInventoryService must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Transactional
    public MarketListingResult createListing(CreateMarketListingCommand command) {
        Objects.requireNonNull(command, "command must not be null");
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
        marketListingRepository.save(listing);

        if (listing.goodsType().isVirtual() && listing.isPreloadedDelivery()) {
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
        Objects.requireNonNull(command, "command must not be null");
        validateUpdateRequest(command);
        listingDomainService.validateListingBasics(command.sellerUserId(), command.title(), command.unitPrice());
        MarketListing listing = requireOwnedListingForUpdate(command.listingId(), command.sellerUserId());
        if (MarketListingStatus.CLOSED.code().equals(listing.getStatus())) {
            throw listingTransitionConflict(command.listingId());
        }
        listing.setTitle(command.title().trim());
        listing.setDescription(command.description().trim());
        listing.setUnitPrice(command.unitPrice());
        listing.setMinPurchaseQuantity(command.minPurchaseQuantity());
        listing.setMaxPurchaseQuantity(command.maxPurchaseQuantity());
        if (marketListingRepository.saveEditable(listing) != 1) {
            throw listingTransitionConflict(command.listingId());
        }
        return MarketListingResult.from(requireOwnedListing(command.listingId(), command.sellerUserId()));
    }

    @Transactional
    public MarketListingResult pauseListing(UUID sellerUserId, UUID listingId) {
        MarketListing listing = requireOwnedListingForUpdate(listingId, sellerUserId);
        MarketListingStatus currentStatus = listingStatus(listing);
        if (currentStatus != MarketListingStatus.ACTIVE && currentStatus != MarketListingStatus.SOLD_OUT) {
            throw listingTransitionConflict(listingId);
        }
        return applyStatusTransition(listing, MarketListingStatus.PAUSED);
    }

    @Transactional
    public MarketListingResult resumeListing(UUID sellerUserId, UUID listingId) {
        MarketListing listing = requireOwnedListingForUpdate(listingId, sellerUserId);
        if (listingStatus(listing) != MarketListingStatus.PAUSED) {
            throw listingTransitionConflict(listingId);
        }
        MarketListingStatus nextStatus = listing.isFiniteStock() && listing.getStockAvailable() <= 0
                ? MarketListingStatus.SOLD_OUT
                : MarketListingStatus.ACTIVE;
        return applyStatusTransition(listing, nextStatus);
    }

    @Transactional
    public MarketListingResult closeListing(UUID sellerUserId, UUID listingId) {
        MarketListing listing = requireOwnedListingForUpdate(listingId, sellerUserId);
        if (listingStatus(listing) == MarketListingStatus.CLOSED) {
            throw listingTransitionConflict(listingId);
        }
        return applyStatusTransition(listing, MarketListingStatus.CLOSED);
    }

    private MarketListingResult applyStatusTransition(MarketListing listing, MarketListingStatus nextStatus) {
        UUID listingId = listing.getListingId();
        MarketListingRepository.StatusTransitionResult result = marketListingRepository.transitionStatus(
                listingId,
                listing.getSellerUserId(),
                listing.getStatus(),
                nextStatus.code()
        );
        if (result != MarketListingRepository.StatusTransitionResult.APPLIED) {
            throw listingTransitionConflict(listingId);
        }
        return MarketListingResult.from(requireOwnedListing(listingId, listing.getSellerUserId()));
    }

    private void validateCreateRequest(CreateMarketListingCommand command) {
        validateCommonFields(command.title(), command.description(), command.unitPrice(),
                command.minPurchaseQuantity(), command.maxPurchaseQuantity());
        MarketGoodsType goodsType = marketGoodsType(command.goodsType());
        if (goodsType.isVirtual()) {
            MarketDeliveryMode deliveryMode = marketDeliveryMode(command.deliveryMode());
            MarketStockMode stockMode = marketStockMode(command.stockMode());
            if (command.stockTotal() == null || command.stockTotal() < 0) {
                throw new BusinessException(INVALID_ARGUMENT, "virtual listing stockTotal must be non-negative");
            }
            if (deliveryMode.isPreloaded()) {
                if (!stockMode.isFinite()) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing must use FINITE stock");
                }
                if (command.inventory() == null || command.inventory().payloads() == null || command.inventory().payloads().isEmpty()) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing requires inventory payloads");
                }
                if (!command.stockTotal().equals(command.inventory().payloads().size())) {
                    throw new BusinessException(INVALID_ARGUMENT, "PRELOADED listing stockTotal must equal inventory payload count");
                }
            }
            if (deliveryMode.isManual() && stockMode.isFinite() && command.stockTotal() <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "FINITE MANUAL listing stockTotal must be positive");
            }
            return;
        }

        if (command.stockTotal() == null || command.stockTotal() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "physical listing stockTotal must be positive");
        }
        if (StringUtils.hasText(command.deliveryMode()) || StringUtils.hasText(command.stockMode()) || command.inventory() != null) {
            throw new BusinessException(INVALID_ARGUMENT, "physical listing must not use virtual delivery fields");
        }
    }

    private void validateUpdateRequest(UpdateMarketListingCommand command) {
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

    private MarketGoodsType marketGoodsType(String value) {
        String code = normalizeRequired(value, "goodsType");
        try {
            return MarketGoodsType.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid goodsType: " + code);
        }
    }

    private MarketDeliveryMode marketDeliveryMode(String value) {
        String code = normalizeRequired(value, "deliveryMode");
        try {
            return MarketDeliveryMode.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid deliveryMode: " + code);
        }
    }

    private MarketStockMode marketStockMode(String value) {
        String code = normalizeRequired(value, "stockMode");
        try {
            return MarketStockMode.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid stockMode: " + code);
        }
    }

    private int initialStockTotal(CreateMarketListingCommand command) {
        MarketGoodsType goodsType = marketGoodsType(command.goodsType());
        if (goodsType.isPhysical()) {
            return command.stockTotal();
        }
        MarketDeliveryMode deliveryMode = marketDeliveryMode(command.deliveryMode());
        MarketStockMode stockMode = marketStockMode(command.stockMode());
        return deliveryMode.isPreloaded()
                ? 0
                : (!stockMode.isFinite() ? 0 : command.stockTotal());
    }

    private int initialStockAvailable(CreateMarketListingCommand command) {
        MarketGoodsType goodsType = marketGoodsType(command.goodsType());
        if (goodsType.isPhysical()) {
            return command.stockTotal();
        }
        MarketDeliveryMode deliveryMode = marketDeliveryMode(command.deliveryMode());
        MarketStockMode stockMode = marketStockMode(command.stockMode());
        return deliveryMode.isPreloaded()
                ? 0
                : (!stockMode.isFinite() ? 0 : command.stockTotal());
    }

    private String initialStatus(CreateMarketListingCommand command) {
        MarketGoodsType goodsType = marketGoodsType(command.goodsType());
        if (goodsType.isPhysical()) {
            return command.stockTotal() <= 0 ? MarketListingStatus.SOLD_OUT.code() : MarketListingStatus.ACTIVE.code();
        }
        MarketDeliveryMode deliveryMode = marketDeliveryMode(command.deliveryMode());
        MarketStockMode stockMode = marketStockMode(command.stockMode());
        if (stockMode.isFinite()
                && deliveryMode.isManual()
                && command.stockTotal() == 0) {
            return MarketListingStatus.SOLD_OUT.code();
        }
        return MarketListingStatus.ACTIVE.code();
    }

    private MarketListing requireOwnedListing(UUID listingId, UUID sellerUserId) {
        validateSellerUserId(sellerUserId);
        MarketListing listing = marketListingRepository.findById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (!Objects.equals(listing.getSellerUserId(), sellerUserId)) {
            throw new BusinessException(FORBIDDEN, "market listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private MarketListing requireOwnedListingForUpdate(UUID listingId, UUID sellerUserId) {
        validateSellerUserId(sellerUserId);
        MarketListing listing = marketListingRepository.lockById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        if (!Objects.equals(listing.getSellerUserId(), sellerUserId)) {
            throw new BusinessException(FORBIDDEN, "market listing does not belong to seller: listingId=" + listingId);
        }
        return listing;
    }

    private MarketListingStatus listingStatus(MarketListing listing) {
        try {
            return MarketListingStatus.fromCode(listing.getStatus());
        } catch (IllegalArgumentException exception) {
            throw listingTransitionConflict(listing.getListingId());
        }
    }

    private BusinessException listingTransitionConflict(UUID listingId) {
        return new BusinessException(
                MarketErrorCode.LISTING_TRANSITION_CONFLICT,
                "market listing transition conflict: listingId=" + listingId
        );
    }

    private void validateSellerUserId(UUID sellerUserId) {
        if (sellerUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "sellerUserId must not be null");
        }
    }
}
