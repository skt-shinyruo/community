package com.nowcoder.community.market.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MarketAddressSnapshot(
        UUID addressId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode
) {
    public MarketAddressSnapshot {
        Objects.requireNonNull(addressId, "addressId must not be null");
    }

    public static MarketAddressSnapshot from(MarketAddress address) {
        return new MarketAddressSnapshot(
                address.getAddressId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode()
        );
    }
}
