package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRefreshTokenRepositoryTest {

    private static JacksonJsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
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

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());
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

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());
        store.revokeFamily("f1");

        verify(valueOps).set(eq("auth:refresh:family:revoked:f1"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete(eq("auth:refresh:t1"));
        verify(redisTemplate).delete(eq("auth:refresh:t2"));
        verify(redisTemplate).delete(eq("auth:refresh:family:f1"));
    }

    @Test
    void consumeShouldUseAtomicScriptToDeleteTokenWriteTombstoneAndRemoveFamilyMember() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String json = jsonCodec().toJson(
                new RefreshTokenRepository.StoredRefreshToken("t1", userId, "f1", Instant.now().plusSeconds(60))
        );
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(json);

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());
        RefreshTokenRepository.StoredRefreshToken found = store.consume("  t1  ");

        assertThat(found).isNotNull();
        assertThat(found.refreshToken()).isEqualTo("t1");
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.familyId()).isEqualTo("f1");
        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                keysCaptor.capture(),
                anyString(),
                eq("t1")
        );
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("cjson.decode");
        assertThat(script.getScriptAsString()).contains("redis.call('exists', KEYS[3] .. record.familyId)");
        assertThat(script.getScriptAsString()).contains("redis.call('del', KEYS[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('set', KEYS[2], tombstone, 'px', ttl)");
        assertThat(script.getScriptAsString()).contains("redis.call('srem', KEYS[4] .. record.familyId, member)");
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:refresh:t1",
                "auth:refresh:revoked:t1",
                "auth:refresh:family:revoked:",
                "auth:refresh:family:"
        );
        verify(redisTemplate, never()).delete(eq("auth:refresh:family:f1"));
        verify(valueOps, never()).set(eq("auth:refresh:revoked:t1"), anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void findRevokedShouldReturnConsumedTokenTombstoneMetadataAndStoreStillRejectsRevokedFamily() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        Instant expiresAt = Instant.now().plusSeconds(120);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        JacksonJsonCodec codec = jsonCodec();
        String activeJson = codec.toJson(
                new RefreshTokenRepository.StoredRefreshToken("t1", userId, "f1", expiresAt)
        );
        String tombstoneJson = codec.toJson(Map.of(
                "userId", userId,
                "familyId", "f1",
                "expiresAt", expiresAt,
                "revokedAt", Instant.now().minusSeconds(2)
        ));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(activeJson, (String) null);
        when(valueOps.get(eq("auth:refresh:revoked:t1"))).thenReturn(tombstoneJson);

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, codec);

        assertThat(store.consume("t1")).isNotNull();
        assertThat(store.consume("t1")).isNull();
        RefreshTokenRepository.RevokedRefreshToken revoked = store.findRevoked("t1");
        assertThatThrownBy(() -> store.store("t3", userId, "f1", expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token family");

        assertThat(revoked).isNotNull();
        assertThat(revoked.refreshToken()).isEqualTo("t1");
        assertThat(revoked.userId()).isEqualTo(userId);
        assertThat(revoked.familyId()).isEqualTo("f1");
        verify(valueOps, never()).set(eq("auth:refresh:revoked:t1"), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verify(valueOps, never()).set(eq("auth:refresh:family:revoked:f1"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate, never()).delete(eq("auth:refresh:family:f1"));
    }

    @Test
    void consumeMissingTokenShouldNotReadTombstoneOrRevokeFamily() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(null);

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());

        assertThat(store.consume("t1")).isNull();

        verify(valueOps, never()).get(eq("auth:refresh:revoked:t1"));
        verify(valueOps, never()).set(eq("auth:refresh:family:revoked:f1"), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate, never()).delete(eq("auth:refresh:family:f1"));
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

        String json = jsonCodec().toJson(
                new RefreshTokenRepository.StoredRefreshToken("t1", userId, "f1", Instant.now().plusSeconds(60))
        );
        when(valueOps.get(eq("auth:refresh:t1"))).thenReturn(json);

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());
        store.revoke("  t1  ");

        verify(redisTemplate).delete(eq("auth:refresh:t1"));
        verify(setOps).remove(eq("auth:refresh:family:f1"), eq("t1"));
        verify(valueOps).set(eq("auth:refresh:revoked:t1"), anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void findShouldReturnNullForMalformedStoredJson() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:refresh:t1")).thenReturn("{bad");

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());

        assertThat(store.find("t1")).isNull();
    }

    @Test
    void findRevokedShouldReturnNullForMalformedTombstoneJson() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:refresh:revoked:t1")).thenReturn("{bad");

        RedisRefreshTokenRepository store = new RedisRefreshTokenRepository(redisTemplate, jsonCodec());

        assertThat(store.findRevoked("t1")).isNull();
    }

}
