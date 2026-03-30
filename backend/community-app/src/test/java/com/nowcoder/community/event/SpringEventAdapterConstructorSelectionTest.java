package com.nowcoder.community.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.event.TaskProgressOutboxEnqueuer;
import com.nowcoder.community.growth.event.TaskProgressOutboxHandler;
import com.nowcoder.community.growth.event.TaskProgressProjectionListener;
import com.nowcoder.community.growth.service.TaskProgressProjectionService;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.notice.event.NoticeOutboxEnqueuer;
import com.nowcoder.community.notice.event.NoticeOutboxHandler;
import com.nowcoder.community.notice.event.NoticeProjectionListener;
import com.nowcoder.community.notice.service.NoticeProjectionService;
import com.nowcoder.community.user.event.PointsOutboxEnqueuer;
import com.nowcoder.community.user.event.PointsOutboxHandler;
import com.nowcoder.community.user.event.PointsProjectionListener;
import com.nowcoder.community.user.service.PointsProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventAdapterConstructorSelectionTest {

    @Test
    void multiConstructorSpringEventAdaptersShouldMarkProductionConstructorWithAutowired() {
        assertThat(List.of(
                expectation(TaskProgressProjectionListener.class, TaskProgressProjectionService.class),
                expectation(TaskProgressOutboxHandler.class, ObjectMapper.class, TaskProgressProjectionService.class),
                expectation(TaskProgressOutboxEnqueuer.class, ObjectMapper.class, JdbcOutboxEventStore.class, TaskProgressProjectionService.class),
                expectation(PointsProjectionListener.class, PointsProjectionService.class),
                expectation(PointsOutboxHandler.class, ObjectMapper.class, PointsProjectionService.class),
                expectation(PointsOutboxEnqueuer.class, ObjectMapper.class, JdbcOutboxEventStore.class, PointsProjectionService.class),
                expectation(NoticeProjectionListener.class, NoticeProjectionService.class),
                expectation(NoticeOutboxHandler.class, ObjectMapper.class, NoticeProjectionService.class),
                expectation(NoticeOutboxEnqueuer.class, ObjectMapper.class, JdbcOutboxEventStore.class, NoticeProjectionService.class)
        )).allSatisfy(expectation -> {
            Constructor<?>[] constructors = expectation.type.getDeclaredConstructors();

            assertThat(constructors)
                    .withFailMessage("%s should keep its test-only convenience constructor", expectation.type.getName())
                    .hasSizeGreaterThan(1);

            assertThat(constructors)
                    .filteredOn(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .singleElement()
                    .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                            .containsExactly(expectation.injectionParameterTypes));
        });
    }

    private static ConstructorExpectation expectation(Class<?> type, Class<?>... injectionParameterTypes) {
        return new ConstructorExpectation(type, injectionParameterTypes);
    }

    private static final class ConstructorExpectation {
        private final Class<?> type;
        private final Class<?>[] injectionParameterTypes;

        private ConstructorExpectation(Class<?> type, Class<?>[] injectionParameterTypes) {
            this.type = type;
            this.injectionParameterTypes = injectionParameterTypes;
        }
    }
}
