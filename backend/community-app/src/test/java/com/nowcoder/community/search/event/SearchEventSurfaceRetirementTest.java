package com.nowcoder.community.search.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchEventSurfaceRetirementTest {

    @Test
    void syncPostProjectionListenerShouldBeRetired() {
        assertThatThrownBy(() -> Class.forName("com.nowcoder.community.search.event.PostProjectionListener"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
