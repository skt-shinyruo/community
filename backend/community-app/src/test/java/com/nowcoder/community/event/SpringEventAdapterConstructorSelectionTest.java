package com.nowcoder.community.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.infrastructure.event.PostHotFeedProjectionLocalListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventAdapterConstructorSelectionTest {

    @Test
    void localSpringEventAdaptersShouldExposeOnlyTheirExplicitCollaboratorConstructor() {
        assertThat(PostHotFeedProjectionLocalListener.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(JsonCodec.class, PostHotFeedProjectionApplicationService.class));
    }
}
