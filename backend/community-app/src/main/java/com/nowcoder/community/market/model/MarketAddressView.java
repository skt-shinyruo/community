package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketAddress;

import java.util.Date;
import java.util.UUID;

public record MarketAddressView(
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

    public static MarketAddressView from(MarketAddress address) {
        return new MarketAddressView(
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
