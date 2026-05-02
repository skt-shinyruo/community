package com.nowcoder.community.market.controller.dto;

import com.nowcoder.community.market.application.result.MarketAddressResult;

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
        boolean defaultAddress,
        String status,
        Date createTime,
        Date updateTime
) {

    public static MarketAddressResponse from(MarketAddressResult address) {
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
