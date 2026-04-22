package com.nowcoder.community.market.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.MarketAddressResponse;
import com.nowcoder.community.market.dto.UpdateMarketAddressRequest;
import com.nowcoder.community.market.entity.MarketAddress;
import com.nowcoder.community.market.mapper.MarketAddressMapper;
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
public class MarketAddressService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MarketAddressMapper marketAddressMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MarketAddressService(MarketAddressMapper marketAddressMapper) {
        this(marketAddressMapper, new UuidV7Generator());
    }

    MarketAddressService(MarketAddressMapper marketAddressMapper, UuidV7Generator idGenerator) {
        this.marketAddressMapper = marketAddressMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketAddressResponse createAddress(UUID userId, CreateMarketAddressRequest request) {
        validateUserId(userId);
        validateCreateRequest(request);
        if (request.isDefault()) {
            marketAddressMapper.clearDefaultByUserId(userId);
        }

        MarketAddress address = new MarketAddress();
        address.setAddressId(idGenerator.next());
        address.setUserId(userId);
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setCity(request.getCity().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setDetailAddress(request.getDetailAddress().trim());
        address.setPostalCode(StringUtils.hasText(request.getPostalCode()) ? request.getPostalCode().trim() : null);
        address.setDefault(request.isDefault());
        address.setStatus(STATUS_ACTIVE);
        marketAddressMapper.insert(address);
        return MarketAddressResponse.from(marketAddressMapper.selectById(address.getAddressId()));
    }

    public List<MarketAddressResponse> listAddresses(UUID userId) {
        validateUserId(userId);
        return marketAddressMapper.selectByUserId(userId).stream()
                .map(MarketAddressResponse::from)
                .toList();
    }

    @Transactional
    public MarketAddressResponse updateAddress(UUID userId, UUID addressId, UpdateMarketAddressRequest request) {
        validateUserId(userId);
        validateUpdateRequest(request);
        requireOwnedAddress(addressId, userId);
        if (request.isDefault()) {
            marketAddressMapper.clearDefaultByUserId(userId);
        }

        MarketAddress address = new MarketAddress();
        address.setAddressId(addressId);
        address.setUserId(userId);
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setCity(request.getCity().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setDetailAddress(request.getDetailAddress().trim());
        address.setPostalCode(StringUtils.hasText(request.getPostalCode()) ? request.getPostalCode().trim() : null);
        address.setDefault(request.isDefault());
        address.setStatus(STATUS_ACTIVE);
        if (marketAddressMapper.update(address) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "market address update failed: addressId=" + addressId);
        }
        return MarketAddressResponse.from(requireOwnedAddress(addressId, userId));
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        validateUserId(userId);
        requireOwnedAddress(addressId, userId);
        if (marketAddressMapper.softDelete(addressId, userId) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "market address delete failed: addressId=" + addressId);
        }
    }

    private void validateCreateRequest(CreateMarketAddressRequest request) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market address request must not be null");
        }
        requireText(request.getReceiverName(), "receiverName");
        requireText(request.getReceiverPhone(), "receiverPhone");
        requireText(request.getProvince(), "province");
        requireText(request.getCity(), "city");
        requireText(request.getDistrict(), "district");
        requireText(request.getDetailAddress(), "detailAddress");
    }

    private void validateUpdateRequest(UpdateMarketAddressRequest request) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market address update request must not be null");
        }
        requireText(request.getReceiverName(), "receiverName");
        requireText(request.getReceiverPhone(), "receiverPhone");
        requireText(request.getProvince(), "province");
        requireText(request.getCity(), "city");
        requireText(request.getDistrict(), "district");
        requireText(request.getDetailAddress(), "detailAddress");
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
