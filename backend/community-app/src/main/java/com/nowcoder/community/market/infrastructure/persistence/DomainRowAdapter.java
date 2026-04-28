package com.nowcoder.community.market.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

final class DomainRowAdapter {

    private DomainRowAdapter() {
    }

    static <T> List<T> asDomainList(List<? extends T> rows) {
        if (rows == null) {
            return List.of();
        }
        return new ArrayList<>(rows);
    }
}
