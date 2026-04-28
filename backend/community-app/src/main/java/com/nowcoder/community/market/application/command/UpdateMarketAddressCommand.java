package com.nowcoder.community.market.application.command;

import java.util.UUID;

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
