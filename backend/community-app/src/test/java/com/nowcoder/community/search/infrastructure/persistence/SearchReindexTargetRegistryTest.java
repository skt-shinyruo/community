package com.nowcoder.community.search.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchReindexTargetRegistryTest {

    private static final String TARGET_INDEX = "community_posts_v20260803010101";
    private static final Duration TARGET_TTL = Duration.ofSeconds(30);

    @Test
    void currentIndexShouldReturnTheClusterVisibleRebuildTarget() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("search:reindex:active-target"))
                .thenReturn(TARGET_INDEX);

        Optional<String> target = new SearchReindexTargetRegistry(redisTemplate).currentIndex();

        assertThat(target).contains(TARGET_INDEX);
    }

    @Test
    void currentIndexShouldFailClosedWhenRedisCannotDetermineWhetherARebuildIsActive() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("search:reindex:active-target")).thenThrow(redisFailure);

        assertThatThrownBy(() -> new SearchReindexTargetRegistry(redisTemplate).currentIndex())
                .isInstanceOf(IllegalStateException.class)
                .hasCause(redisFailure);
    }

    @Test
    void activateShouldExposeAnUnknownSetNxOutcomeInsteadOfReportingAConfirmedConflict() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        RuntimeException redisFailure = new RuntimeException("SET response lost");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("search:reindex:active-target", TARGET_INDEX, TARGET_TTL))
                .thenThrow(redisFailure);

        assertThatThrownBy(() -> new SearchReindexTargetRegistry(redisTemplate).activate(TARGET_INDEX, TARGET_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome is unknown")
                .hasCause(redisFailure);
    }

    @Test
    void activateShouldTreatANullSetNxResponseAsUnknown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("search:reindex:active-target", TARGET_INDEX, TARGET_TTL))
                .thenReturn(null);

        assertThatThrownBy(() -> new SearchReindexTargetRegistry(redisTemplate).activate(TARGET_INDEX, TARGET_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome is unknown");
    }

    @Test
    void deactivateShouldExposeAnUnknownCompareAndDeleteOutcome() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RuntimeException redisFailure = new RuntimeException("DEL response lost");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(TARGET_INDEX)))
                .thenThrow(redisFailure);

        assertThatThrownBy(() -> new SearchReindexTargetRegistry(redisTemplate).deactivate(TARGET_INDEX))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome is unknown")
                .hasCause(redisFailure);
    }

    @Test
    void deactivateShouldTreatANullCompareAndDeleteResponseAsUnknown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(TARGET_INDEX)))
                .thenReturn(null);

        assertThatThrownBy(() -> new SearchReindexTargetRegistry(redisTemplate).deactivate(TARGET_INDEX))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome is unknown");
    }

    @Test
    void deactivateShouldAcceptAConfirmedNotOwnerResponse() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(TARGET_INDEX)))
                .thenReturn(0L);

        assertThatCode(() -> new SearchReindexTargetRegistry(redisTemplate).deactivate(TARGET_INDEX))
                .doesNotThrowAnyException();
    }
}
