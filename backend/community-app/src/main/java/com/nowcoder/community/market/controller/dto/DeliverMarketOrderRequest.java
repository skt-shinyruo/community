package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliverMarketOrderRequest(@NotBlank String deliveryContent) {
}
