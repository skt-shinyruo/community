package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.MarketAddressResponse;
import com.nowcoder.community.market.entity.MarketAddress;
import com.nowcoder.community.market.mapper.MarketAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class MarketAddressService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MarketAddressMapper marketAddressMapper;

    public MarketAddressService(MarketAddressMapper marketAddressMapper) {
        this.marketAddressMapper = marketAddressMapper;
    }

    @Transactional
    public MarketAddressResponse createAddress(int userId, CreateMarketAddressRequest request) {
        validateUserId(userId);
        validateCreateRequest(request);
        if (request.isDefault()) {
            marketAddressMapper.clearDefaultByUserId(userId);
        }

        MarketAddress address = new MarketAddress();
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

    public List<MarketAddressResponse> listAddresses(int userId) {
        validateUserId(userId);
        return marketAddressMapper.selectByUserId(userId).stream()
                .map(MarketAddressResponse::from)
                .toList();
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

    private void validateUserId(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId must be positive");
        }
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "market address " + fieldName + " must not be blank");
        }
        return value.trim();
    }
}
