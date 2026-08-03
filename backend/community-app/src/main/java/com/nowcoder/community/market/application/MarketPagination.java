package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.result.MarketPageResult;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

final class MarketPagination {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_PAGE = 10_000;

    private MarketPagination() {
    }

    static Window window(Integer page, Integer size) {
        if (page != null && page > MAX_PAGE) {
            throw new BusinessException(INVALID_ARGUMENT, "page 不能超过 " + MAX_PAGE);
        }
        int normalizedPage = page == null ? 0 : Math.max(0, page);
        int normalizedSize = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        return new Window(normalizedPage, normalizedSize, (long) normalizedPage * normalizedSize);
    }

    static <T> MarketPageResult<T> result(List<T> candidates, Window window) {
        List<T> safeCandidates = candidates == null ? List.of() : candidates;
        boolean hasNext = safeCandidates.size() > window.size();
        List<T> items = hasNext ? safeCandidates.subList(0, window.size()) : safeCandidates;
        return new MarketPageResult<>(items, hasNext, window.page(), window.size());
    }

    record Window(int page, int size, long offset) {
        int queryLimit() {
            return size + 1;
        }
    }
}
