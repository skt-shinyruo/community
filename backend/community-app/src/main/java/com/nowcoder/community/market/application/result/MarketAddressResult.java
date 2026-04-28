package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketAddress;

import java.util.Date;
import java.util.UUID;

public record MarketAddressResult(
        UUID addressId,
        UUID userId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        boolean isDefault,
        String status,
        Date createTime,
        Date updateTime
) {

    public static MarketAddressResult from(MarketAddress address) {
        return new MarketAddressResult(
                address.getAddressId(),
                address.getUserId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode(),
                address.isDefault(),
                address.getStatus(),
                address.getCreateTime(),
                address.getUpdateTime()
        );
    }
}
