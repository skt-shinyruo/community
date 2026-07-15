package com.nowcoder.community.common.pagination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationTest {

    @Test
    void safeOffsetShouldClampInputsAndSaturateOverflow() {
        assertThat(Pagination.safeOffset(-1, 50)).isZero();
        assertThat(Pagination.safeOffset(5, 0)).isEqualTo(5);
        assertThat(Pagination.safeOffset(Integer.MAX_VALUE, 50)).isEqualTo(Integer.MAX_VALUE);
    }
}
