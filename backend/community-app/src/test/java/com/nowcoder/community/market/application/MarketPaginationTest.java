package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.application.result.MarketPageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketPaginationTest {

    @Test
    void shouldUseOneExtraRowToDetermineWhetherAnotherPageExists() {
        MarketPagination.Window window = MarketPagination.window(2, 2);

        MarketPageResult<String> result = MarketPagination.result(List.of("a", "b", "c"), window);

        assertThat(window.offset()).isEqualTo(4L);
        assertThat(window.queryLimit()).isEqualTo(3);
        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.hasNext()).isTrue();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void exactPageShouldNotAdvertiseAFalseNextPage() {
        MarketPagination.Window window = MarketPagination.window(0, 2);

        assertThat(MarketPagination.result(List.of("a", "b"), window).hasNext()).isFalse();
    }

    @Test
    void shouldRejectPathologicalDeepOffsetsAndBoundPageSize() {
        assertThat(MarketPagination.window(0, 10_000).size()).isEqualTo(100);
        assertThatThrownBy(() -> MarketPagination.window(10_001, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page");
    }
}
