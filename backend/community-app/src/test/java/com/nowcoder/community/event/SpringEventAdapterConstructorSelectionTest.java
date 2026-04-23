package com.nowcoder.community.event;

import com.nowcoder.community.notice.event.NoticeProjectionListener;
import com.nowcoder.community.notice.service.NoticeProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventAdapterConstructorSelectionTest {

    @Test
    void multiConstructorSpringEventAdaptersShouldMarkProductionConstructorWithAutowired() {
        assertThat(List.of(
                expectation(NoticeProjectionListener.class, NoticeProjectionService.class)
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
