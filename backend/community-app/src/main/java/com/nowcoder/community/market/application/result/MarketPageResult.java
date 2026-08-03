package com.nowcoder.community.market.application.result;

import java.util.List;

public record MarketPageResult<T>(
        List<T> items,
        boolean hasNext,
        int page,
        int size
) {
    public MarketPageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
