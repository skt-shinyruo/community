package com.nowcoder.community.notice.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeProjectionListenerStructureTest {

    @Test
    void noticeProjectionListenerShouldNotDependOnEventsOutboxProperty() {
        assertThat(NoticeProjectionListener.class.isAnnotationPresent(ConditionalOnProperty.class)).isFalse();
    }
}
