package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.result.MarketAddressResult;
import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketAddressApplicationService {

    public record CreateMarketAddressCommand(
            UUID userId,
            String receiverName,
            String receiverPhone,
            String province,
            String city,
            String district,
            String detailAddress,
            String postalCode,
            boolean defaultAddress
    ) {
    }

    public record UpdateMarketAddressCommand(
            UUID userId,
            UUID addressId,
            String receiverName,
            String receiverPhone,
            String province,
            String city,
            String district,
            String detailAddress,
            String postalCode,
            boolean defaultAddress
    ) {
    }

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MarketAddressRepository marketAddressRepository;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MarketAddressApplicationService(
            MarketAddressRepository marketAddressRepository,
            UuidV7Generator idGenerator
    ) {
        this.marketAddressRepository = Objects.requireNonNull(marketAddressRepository, "marketAddressRepository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Transactional
    public MarketAddressResult createAddress(CreateMarketAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateUserId(command.userId());
        validateCreateRequest(command);
        if (command.defaultAddress()) {
            marketAddressRepository.clearDefaultByUserId(command.userId());
        }

        MarketAddress address = new MarketAddress();
        address.setAddressId(idGenerator.next());
        address.setUserId(command.userId());
        address.setReceiverName(command.receiverName().trim());
        address.setReceiverPhone(command.receiverPhone().trim());
        address.setProvince(command.province().trim());
        address.setCity(command.city().trim());
        address.setDistrict(command.district().trim());
        address.setDetailAddress(command.detailAddress().trim());
        address.setPostalCode(StringUtils.hasText(command.postalCode()) ? command.postalCode().trim() : null);
        address.setDefault(command.defaultAddress());
        address.setStatus(STATUS_ACTIVE);
        requireAddressWrite(marketAddressRepository.save(address), address.getAddressId());
        return MarketAddressResult.from(marketAddressRepository.findById(address.getAddressId()));
    }

    public List<MarketAddressResult> listAddresses(UUID userId) {
        validateUserId(userId);
        return marketAddressRepository.findByUserId(userId).stream()
                .map(MarketAddressResult::from)
                .toList();
    }

    @Transactional
    public MarketAddressResult updateAddress(UpdateMarketAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateUserId(command.userId());
        validateUpdateRequest(command);
        requireOwnedAddress(command.addressId(), command.userId());
        if (command.defaultAddress()) {
            marketAddressRepository.clearDefaultByUserId(command.userId());
        }

        MarketAddress address = new MarketAddress();
        address.setAddressId(command.addressId());
        address.setUserId(command.userId());
        address.setReceiverName(command.receiverName().trim());
        address.setReceiverPhone(command.receiverPhone().trim());
        address.setProvince(command.province().trim());
        address.setCity(command.city().trim());
        address.setDistrict(command.district().trim());
        address.setDetailAddress(command.detailAddress().trim());
        address.setPostalCode(StringUtils.hasText(command.postalCode()) ? command.postalCode().trim() : null);
        address.setDefault(command.defaultAddress());
        address.setStatus(STATUS_ACTIVE);
        requireAddressWrite(marketAddressRepository.saveChanges(address), command.addressId());
        return MarketAddressResult.from(requireOwnedAddress(command.addressId(), command.userId()));
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        validateUserId(userId);
        requireOwnedAddress(addressId, userId);
        if (marketAddressRepository.softDelete(addressId, userId) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "market address delete failed: addressId=" + addressId);
        }
    }

    private void validateCreateRequest(CreateMarketAddressCommand command) {
        requireText(command.receiverName(), "receiverName");
        requireText(command.receiverPhone(), "receiverPhone");
        requireText(command.province(), "province");
        requireText(command.city(), "city");
        requireText(command.district(), "district");
        requireText(command.detailAddress(), "detailAddress");
    }

    private void validateUpdateRequest(UpdateMarketAddressCommand command) {
        requireText(command.receiverName(), "receiverName");
        requireText(command.receiverPhone(), "receiverPhone");
        requireText(command.province(), "province");
        requireText(command.city(), "city");
        requireText(command.district(), "district");
        requireText(command.detailAddress(), "detailAddress");
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId must not be null");
        }
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "market address " + fieldName + " must not be blank");
        }
        return value.trim();
    }

    private MarketAddress requireOwnedAddress(UUID addressId, UUID userId) {
        MarketAddress address = marketAddressRepository.findById(addressId);
        if (address == null || !STATUS_ACTIVE.equals(address.getStatus())) {
            throw new BusinessException(NOT_FOUND, "market address not found: addressId=" + addressId);
        }
        if (!Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException(INVALID_ARGUMENT, "market address does not belong to user: addressId=" + addressId);
        }
        return address;
    }

    private void requireAddressWrite(MarketAddressRepository.WriteResult result, UUID addressId) {
        if (result == MarketAddressRepository.WriteResult.APPLIED) {
            return;
        }
        if (result == MarketAddressRepository.WriteResult.DEFAULT_CONFLICT) {
            throw new BusinessException(
                    MarketErrorCode.DEFAULT_ADDRESS_CONFLICT,
                    "market default address conflict: addressId=" + addressId
            );
        }
        throw new BusinessException(INVALID_ARGUMENT, "market address update failed: addressId=" + addressId);
    }
}
