package com.nowcoder.community.auth.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRegistrationDraftRepositoryTest {

    @Test
    void issueShouldStoreJsonWithTtlAndFindShouldReadIt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RedisRegistrationDraftRepository repository = new RedisRegistrationDraftRepository(redisTemplate, mapper);
        PreparedRegistrationDraft draft = draft();

        String token = repository.issue(draft, Duration.ofMinutes(30));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), jsonCaptor.capture(), eq(Duration.ofMinutes(30)));
        assertThat(token).matches("[a-f0-9]{32}");
        assertThat(keyCaptor.getValue()).isEqualTo("auth:regdraft:" + token);

        when(valueOps.get("auth:regdraft:" + token)).thenReturn(jsonCaptor.getValue());
        Optional<PreparedRegistrationDraft> found = repository.find(token);

        assertThat(found).contains(draft);
    }

    @Test
    void findShouldDeleteMalformedJsonAndReturnEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:regdraft:token")).thenReturn("{bad");

        RedisRegistrationDraftRepository repository =
                new RedisRegistrationDraftRepository(redisTemplate, new ObjectMapper().findAndRegisterModules());

        assertThat(repository.find("token")).isEmpty();
        verify(redisTemplate).delete("auth:regdraft:token");
    }

    @Test
    void deleteShouldTrimToken() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisRegistrationDraftRepository repository =
                new RedisRegistrationDraftRepository(redisTemplate, new ObjectMapper().findAndRegisterModules());

        repository.delete(" token ");

        verify(redisTemplate).delete("auth:regdraft:token");
    }

    private static PreparedRegistrationDraft draft() {
        Instant now = Instant.parse("2026-05-03T01:00:00Z");
        return new PreparedRegistrationDraft(
                UUID.fromString("00000000-0000-7000-8000-000000000007"),
                "alice",
                "alice@example.com",
                "encoded-password",
                "h",
                now,
                now.plusSeconds(1800)
        );
    }
}
