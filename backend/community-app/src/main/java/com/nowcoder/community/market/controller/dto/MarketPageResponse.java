package com.nowcoder.community.market.controller.dto;

import java.util.List;

public record MarketPageResponse<T>(
        List<T> items,
        boolean hasNext,
        int page,
        int size
) {
    public MarketPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
