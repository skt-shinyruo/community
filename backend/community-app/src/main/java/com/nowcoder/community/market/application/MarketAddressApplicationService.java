package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.command.CreateMarketAddressCommand;
import com.nowcoder.community.market.application.command.UpdateMarketAddressCommand;
import com.nowcoder.community.market.application.result.MarketAddressResult;
import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
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

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MarketAddressRepository marketAddressMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MarketAddressApplicationService(MarketAddressRepository marketAddressMapper) {
        this(marketAddressMapper, new UuidV7Generator());
    }

    MarketAddressApplicationService(MarketAddressRepository marketAddressMapper, UuidV7Generator idGenerator) {
        this.marketAddressMapper = marketAddressMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketAddressResult createAddress(CreateMarketAddressCommand command) {
        validateUserId(command.userId());
        validateCreateRequest(command);
        if (command.defaultAddress()) {
            marketAddressMapper.clearDefaultByUserId(command.userId());
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
        marketAddressMapper.insert(address);
        return MarketAddressResult.from(marketAddressMapper.selectById(address.getAddressId()));
    }

    public List<MarketAddressResult> listAddresses(UUID userId) {
        validateUserId(userId);
        return marketAddressMapper.selectByUserId(userId).stream()
                .map(MarketAddressResult::from)
                .toList();
    }

    @Transactional
    public MarketAddressResult updateAddress(UpdateMarketAddressCommand command) {
        validateUserId(command.userId());
        validateUpdateRequest(command);
        requireOwnedAddress(command.addressId(), command.userId());
        if (command.defaultAddress()) {
            marketAddressMapper.clearDefaultByUserId(command.userId());
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
        if (marketAddressMapper.update(address) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "market address update failed: addressId=" + command.addressId());
        }
        return MarketAddressResult.from(requireOwnedAddress(command.addressId(), command.userId()));
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        validateUserId(userId);
        requireOwnedAddress(addressId, userId);
        if (marketAddressMapper.softDelete(addressId, userId) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "market address delete failed: addressId=" + addressId);
        }
    }

    private void validateCreateRequest(CreateMarketAddressCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market address request must not be null");
        }
        requireText(command.receiverName(), "receiverName");
        requireText(command.receiverPhone(), "receiverPhone");
        requireText(command.province(), "province");
        requireText(command.city(), "city");
        requireText(command.district(), "district");
        requireText(command.detailAddress(), "detailAddress");
    }

    private void validateUpdateRequest(UpdateMarketAddressCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market address update request must not be null");
        }
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
        MarketAddress address = marketAddressMapper.selectById(addressId);
        if (address == null || !STATUS_ACTIVE.equals(address.getStatus())) {
            throw new BusinessException(NOT_FOUND, "market address not found: addressId=" + addressId);
        }
        if (!Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException(INVALID_ARGUMENT, "market address does not belong to user: addressId=" + addressId);
        }
        return address;
    }
}
