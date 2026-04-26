package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.model.MarketAddressView;

import java.util.Date;
import java.util.UUID;

public record MarketAddressResponse(
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

    public static MarketAddressResponse from(MarketAddressView address) {
        return new MarketAddressResponse(
                address.addressId(),
                address.userId(),
                address.receiverName(),
                address.receiverPhone(),
                address.province(),
                address.city(),
                address.district(),
                address.detailAddress(),
                address.postalCode(),
                address.isDefault(),
                address.status(),
                address.createTime(),
                address.updateTime()
        );
    }
}
