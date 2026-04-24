package com.nowcoder.community.event;

import com.nowcoder.community.notice.event.NoticeProjectionListener;
import com.nowcoder.community.notice.service.NoticeProjectionApplicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventAdapterConstructorSelectionTest {

    @Test
    void localSpringEventAdaptersShouldExposeOnlyTheirOwnerApplicationServiceConstructor() {
        assertThat(NoticeProjectionListener.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(NoticeProjectionApplicationService.class));
    }
}
