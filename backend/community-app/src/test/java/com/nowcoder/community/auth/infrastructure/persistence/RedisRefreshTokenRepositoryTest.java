package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRefreshTokenRepositoryTest {

    private static final long SECURITY_VERSION = 42L;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final UUID LEASE_ID = UUID.fromString("00000000-0000-7000-8000-000000000099");

    @Test
    void storeShouldHashBearerTokenAndKeepEveryScriptKeyInOneClusterSlot() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        RedisRefreshTokenRepository repository = repository(redis);

        repository.store("raw-bearer-token", USER_ID, "family-1", SECURITY_VERSION, Instant.now().plusSeconds(120));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> record = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(
                script.capture(),
                keys.capture(),
                record.capture(),
                anyString(),
                anyString()
        );
        assertClusterSafeHashedKeys(keys.getValue(), "raw-bearer-token");
        assertThat(keys.getValue()).hasSize(4);
        assertThat(((DefaultRedisScript<?>) script.getValue()).getScriptAsString())
                .contains("redis.call('exists', KEYS[2])")
                .contains("redis.call('exists', KEYS[3])");
        assertThat(JsonMappers.standard().readTree(record.getValue()).path("securityVersionAtIssue").asLong())
                .isEqualTo(SECURITY_VERSION);
        assertThat(record.getValue()).doesNotContain("raw-bearer-token");
    }

    @Test
    void beginRotationShouldUseResolvedFamilyMarkerAndReturnOpaqueLease() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Instant expiresAt = Instant.now().plusSeconds(120);
        Instant pendingUntil = Instant.now().plusSeconds(30);
        when(values.get(anyString())).thenReturn(activeJson(expiresAt));
        String pendingJson = jsonCodec().toJson(Map.of(
                "tokenId", "digest",
                "userId", USER_ID,
                "familyId", "family-1",
                "securityVersionAtIssue", SECURITY_VERSION,
                "expiresAt", expiresAt,
                "state", "PENDING_ROTATION",
                "pendingExpiresAtEpochMs", pendingUntil.toEpochMilli(),
                "rotationLeaseId", LEASE_ID.toString()
        ));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(pendingJson);
        RedisRefreshTokenRepository repository = repository(redis);

        RefreshTokenRepository.StoredRefreshToken pending = repository.beginRotation(
                "raw-bearer-token",
                pendingUntil,
                LEASE_ID
        );

        assertThat(pending.rotationLeaseId()).isEqualTo(LEASE_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(
                script.capture(),
                keys.capture(),
                anyString(),
                eq(LEASE_ID.toString())
        );
        assertThat(keys.getValue().get(1)).endsWith("family-revoked:family-1");
        assertClusterSafeHashedKeys(keys.getValue(), "raw-bearer-token");
        assertThat(((DefaultRedisScript<?>) script.getValue()).getScriptAsString())
                .contains("redis.call('TIME')")
                .contains("record.pendingExpiresAtEpochMs = requestedExpiresAtMs");
    }

    @Test
    void finishRotationShouldFenceLeaseAndBindEveryPendingIdentityField() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(1L);
        RedisRefreshTokenRepository repository = repository(redis);

        assertThat(repository.finishRotation(
                "old-raw-token",
                "new-raw-token",
                USER_ID,
                "family-1",
                SECURITY_VERSION,
                Instant.now().plusSeconds(120),
                LEASE_ID
        )).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(
                script.capture(),
                keys.capture(),
                anyString(), anyString(), anyString(), anyString(), eq(LEASE_ID.toString()),
                anyString(), eq(USER_ID.toString()), eq("family-1"), eq("42")
        );
        assertClusterSafeHashedKeys(keys.getValue(), "old-raw-token", "new-raw-token");
        assertThat(keys.getValue()).hasSize(6);
        assertThat(((DefaultRedisScript<?>) script.getValue()).getScriptAsString())
                .contains("record.rotationLeaseId ~= ARGV[5]")
                .contains("record.tokenId ~= ARGV[6]")
                .contains("record.userId ~= ARGV[7]")
                .contains("record.familyId ~= ARGV[8]")
                .contains("record.securityVersionAtIssue) ~= tonumber(ARGV[9]")
                .contains("redis.call('TIME')");
    }

    @Test
    void rollbackShouldRequireTheOwningLease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        RedisRefreshTokenRepository repository = repository(redis);

        assertThat(repository.rollbackPendingRotation(" raw-token ", LEASE_ID)).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), eq(LEASE_ID.toString()));
        assertClusterSafeHashedKeys(keys.getValue(), "raw-token");
    }

    @Test
    void revokeFamilyShouldWriteAuthoritativeMarkerWithoutDynamicallyAddressingTokenKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(2L);
        RedisRefreshTokenRepository repository = repository(redis);

        repository.revokeFamily("family-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), keys.capture(), eq("604800"));
        assertClusterSafeHashedKeys(keys.getValue());
        assertThat(((DefaultRedisScript<?>) script.getValue()).getScriptAsString())
                .contains("redis.call('psetex', KEYS[2]")
                .contains("redis.call('scard', KEYS[1])")
                .doesNotContain("ARGV[3]")
                .doesNotContain("ARGV[4]");
    }

    @Test
    void revokeByPresentedPendingTokenShouldResolveFamilyThenUseMarkerScript() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString()))
                .thenReturn(activeJson(Instant.now().plusSeconds(120)))
                .thenReturn(null);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        RedisRefreshTokenRepository repository = repository(redis);

        assertThat(repository.revokeFamilyByPresentedToken("raw-token")).isTrue();

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of(
                        "auth:refresh:{auth-refresh}:family:family-1",
                        "auth:refresh:{auth-refresh}:family-revoked:family-1"
                )),
                eq("604800")
        );
    }

    @Test
    void scriptsShouldNotConstructRedisKeysFromArgumentsOrKeyPrefixes() throws Exception {
        for (String fieldName : List.of(
                "STORE_SCRIPT",
                "CONSUME_SCRIPT",
                "BEGIN_ROTATION_SCRIPT",
                "FINISH_ROTATION_SCRIPT",
                "ROLLBACK_ROTATION_SCRIPT",
                "REVOKE_SCRIPT",
                "REVOKE_FAMILY_SCRIPT"
        )) {
            var field = RedisRefreshTokenRepository.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            DefaultRedisScript<?> script = (DefaultRedisScript<?>) field.get(null);
            assertThat(script.getScriptAsString())
                    .doesNotContain("redis.call('get', ARGV")
                    .doesNotContain("redis.call('set', ARGV")
                    .doesNotContain("redis.call('del', ARGV")
                    .doesNotContain("KEYS[1] ..")
                    .doesNotContain("KEYS[2] ..")
                    .doesNotContain("KEYS[3] ..")
                    .doesNotContain("KEYS[4] ..");
        }
    }

    private static RedisRefreshTokenRepository repository(StringRedisTemplate redis) {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshTokenTtlSeconds(604800L);
        return new RedisRefreshTokenRepository(redis, jsonCodec(), properties, java.time.Clock.systemUTC());
    }

    private static String activeJson(Instant expiresAt) throws Exception {
        return jsonCodec().toJson(Map.of(
                "tokenId", "digest",
                "userId", USER_ID,
                "familyId", "family-1",
                "securityVersionAtIssue", SECURITY_VERSION,
                "expiresAt", expiresAt,
                "state", "ACTIVE"
        ));
    }

    private static JacksonJsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    private static void assertClusterSafeHashedKeys(List<String> keys, String... rawTokens) {
        assertThat(keys).isNotEmpty().allSatisfy(key -> assertThat(key).contains("{auth-refresh}"));
        assertThat(keys).extracting(SlotHash::getSlot).containsOnly(SlotHash.getSlot(keys.get(0)));
        for (String rawToken : rawTokens) {
            assertThat(keys).noneMatch(key -> key.contains(rawToken));
        }
    }
}
