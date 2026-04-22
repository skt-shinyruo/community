package com.nowcoder.community.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRefreshTokenStoreTest {

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void storeShouldUseAtomicScriptToStoreTokenAndIndexFamily() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate, objectMapper());
        store.store("t1", userId, "f1", Instant.now().plusSeconds(120));

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                keysCaptor.capture(),
                anyString(),
                anyString(),
                eq("t1")
        );
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('exists', KEYS[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('set', KEYS[2], ARGV[1]");
        assertThat(script.getScriptAsString()).contains("redis.call('sadd', KEYS[3], ARGV[3])");
        assertThat(script.getScriptAsString()).contains("redis.call('expire', KEYS[3], ARGV[2])");
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:refresh:family:revoked:f1",
                "auth:refresh:t1",
                "auth:refresh:family:f1"
        );
    }

    @Test
    void revokeFamilyShouldSetRevokedMarkerBeforeDeletingTokens() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.getExpire(eq("auth:refresh:family:f1"), eq(TimeUnit.SECONDS))).thenReturn(120L);
        when(setOps.members(eq("auth:refresh:family:f1"))).thenReturn(Set.of("t1", "t2"));

        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate, objectMapper());
        store.revokeFamily("f1");

        verify(valueOps).set(eq("auth:refresh:family:revoked:f1"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete(eq("auth:refresh:t1"));
        verify(redisTemplate).delete(eq("auth:refresh:t2"));
        verify(redisTemplate).delete(eq("auth:refresh:family:f1"));
    }

    @Test
    void consumeShouldTrimTokenForKeyAndFamilyRemoval() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        String json = objectMapper().writeValueAsString(
                new RefreshTokenStore.StoredRefreshToken("t1", userId, "f1", Instant.now().plusSeconds(60))
        );
        when(valueOps.getAndDelete(eq("auth:refresh:t1"))).thenReturn(json);

        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate, objectMapper());
        RefreshTokenStore.StoredRefreshToken found = store.consume("  t1  ");

        assertThat(found).isNotNull();
        assertThat(found.refreshToken()).isEqualTo("t1");
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.familyId()).isEqualTo("f1");
        verify(valueOps).getAndDelete(eq("auth:refresh:t1"));
        verify(setOps).remove(eq("auth:refresh:family:f1"), eq("t1"));
    }

    @Test
    void revokeShouldTrimTokenForKeyAndFamilyRemoval() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        String json = objectMapper().writeValueAsString(
                new RefreshTokenStore.StoredRefreshToken("t1", userId, "f1", Instant.now().plusSeconds(60))
        );
        when(valueOps.get(eq("auth:refresh:t1"))).thenReturn(json);

        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate, objectMapper());
        store.revoke("  t1  ");

        verify(redisTemplate).delete(eq("auth:refresh:t1"));
        verify(setOps).remove(eq("auth:refresh:family:f1"), eq("t1"));
    }
}
