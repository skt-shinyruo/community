package com.nowcoder.community.analytics.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsRepositorySelectionTest {

    @Test
    void analyticsShouldNotHaveInMemoryRepositoryImplementation() {
        assertThatThrownBy(() -> Class.forName("com.nowcoder.community.analytics.infrastructure.persistence.InMemoryAnalyticsRepository"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void redisRepositoryShouldNotBeSelectedByStorageProperty() {
        assertThat(RedisAnalyticsRepository.class.getAnnotation(ConditionalOnProperty.class)).isNull();
    }
}
