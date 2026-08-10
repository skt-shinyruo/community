package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPasswordResetTokenRepositoryTest {

    private static final UUID USER_ID = uuid(42);
    private static final UUID LEASE_ID = uuid(99);

    @Test
    void storeShouldPersistVersionedActiveRecordWithoutRawBearerToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());

        repository.store("raw-reset-token", USER_ID, 17L, Duration.ofMinutes(10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(
                any(RedisScript.class),
                keys.capture(),
                eq(USER_ID + "|17|ACTIVE||"),
                eq("600000")
        );
        assertClusterSafeHashedKeys(keys.getValue(), "raw-reset-token");
        assertThat(keys.getValue()).hasSize(2);
    }

    @Test
    void beginConfirmationShouldAcquireOpaqueFencedLease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(USER_ID + "|17|ACTIVE||");
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(USER_ID + "|17|PENDING|123456|" + LEASE_ID);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());

        PasswordResetTokenRepository.PendingPasswordResetToken pending = repository.beginConfirmation(
                "raw-reset-token",
                Instant.ofEpochMilli(123456L),
                LEASE_ID
        );

        assertThat(pending).isEqualTo(
                new PasswordResetTokenRepository.PendingPasswordResetToken(USER_ID, 17L, LEASE_ID)
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(
                script.capture(),
                keys.capture(),
                eq("123456"),
                anyString(),
                eq(LEASE_ID.toString())
        );
        assertClusterSafeHashedKeys(keys.getValue(), "raw-reset-token");
        assertThat(((DefaultRedisScript<?>) script.getValue()).getScriptAsString())
                .contains("state == 'PENDING'")
                .contains("lease = ''")
                .contains("ARGV[3]");
    }

    @Test
    void finishAndRollbackShouldRequireUserVersionAndOwningLease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(1L);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());

        assertThat(repository.finishConfirmation("raw-reset-token", USER_ID, 17L, LEASE_ID)).isTrue();
        assertThat(repository.rollbackConfirmation("raw-reset-token", USER_ID, 17L, LEASE_ID)).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scripts = ArgumentCaptor.forClass(RedisScript.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, org.mockito.Mockito.times(2)).execute(
                scripts.capture(),
                keys.capture(),
                eq(USER_ID.toString()),
                eq("17"),
                eq(LEASE_ID.toString())
        );
        assertThat(keys.getAllValues()).allSatisfy(value -> assertClusterSafeHashedKeys(value, "raw-reset-token"));
        assertThat(scripts.getAllValues())
                .extracting(script -> ((DefaultRedisScript<?>) script).getScriptAsString())
                .allSatisfy(script -> assertThat(script).contains("lease ~= ARGV[3]"));
    }

    @Test
    void deleteShouldResolveRecordThenDeleteOnlyTheSameActiveToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(USER_ID + "|17|ACTIVE||");
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());

        repository.delete("raw-reset-token");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(
                any(RedisScript.class),
                keys.capture(),
                eq(USER_ID.toString()),
                eq("17")
        );
        assertClusterSafeHashedKeys(keys.getValue(), "raw-reset-token");
    }

    @Test
    void revokeGenerationShouldUseOneExplicitClusterSafeMarkerKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());

        repository.revokeGeneration(USER_ID, 17L, Duration.ofMinutes(10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), eq("600000"));
        assertThat(keys.getValue()).containsExactly(
                "auth:pwdreset:{password-reset}:generation:" + USER_ID + ":17"
        );
        assertClusterSafeHashedKeys(keys.getValue());
    }

    @Test
    void everyScriptShouldAccessRedisKeysOnlyThroughExplicitKeysArguments() throws Exception {
        for (String fieldName : List.of(
                "STORE_SCRIPT",
                "BEGIN_CONFIRMATION_SCRIPT",
                "FINISH_CONFIRMATION_SCRIPT",
                "ROLLBACK_CONFIRMATION_SCRIPT",
                "DELETE_ACTIVE_SCRIPT",
                "REVOKE_GENERATION_SCRIPT"
        )) {
            var field = RedisPasswordResetTokenRepository.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            DefaultRedisScript<?> script = (DefaultRedisScript<?>) field.get(null);
            assertThat(script.getScriptAsString())
                    .doesNotContain("redis.call('GET', ARGV")
                    .doesNotContain("redis.call('SET', ARGV")
                    .doesNotContain("redis.call('DEL', ARGV")
                    .doesNotContain("KEYS[1] ..")
                    .doesNotContain("KEYS[2] ..");
        }
    }

    private static void assertClusterSafeHashedKeys(List<String> keys, String... rawTokens) {
        assertThat(keys).isNotEmpty().allSatisfy(key -> assertThat(key).contains("{password-reset}"));
        assertThat(keys).extracting(SlotHash::getSlot).containsOnly(SlotHash.getSlot(keys.get(0)));
        for (String rawToken : rawTokens) {
            assertThat(keys).noneMatch(key -> key.contains(rawToken));
        }
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
