# Registration Verify-First High Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace pending-user registration with Verify-First registration so unverified traffic creates only expiring auth drafts and verified users are inserted once the email code succeeds.

**Architecture:** Auth owns registration draft and code orchestration. User owns registration material preparation, final active user creation, duplicate translation, and `UserPolicyChanged(userExists=true)` publication. The old `RegistrationSessionRepository` token-to-user-id store is deleted and replaced by draft resolution. Auth registration no longer creates, resolves, or activates pending users; any `status=0` row cleanup is historical data migration only and is not part of the new flow.

**Tech Stack:** Spring Boot, Java records, Redis via `StringRedisTemplate`, Jackson `ObjectMapper`, MyBatis user repository, JUnit 5, Mockito, AssertJ, Maven.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-03-registration-verify-first-high-concurrency-design.md`

## File Structure

Create:

- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/PreparedRegistrationUserView.java` - published user-domain prepared registration material.
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/VerifiedRegistrationUserCommand.java` - published user-domain final creation command.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/PreparedRegistrationUserResult.java` - user application result for prepared material.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/CreateVerifiedRegistrationUserCommand.java` - user application command for final creation.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PreparedRegistrationDraft.java` - auth-domain draft stored behind registration token.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationDraftRepository.java` - auth-domain draft repository contract.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationDraftRepository.java` - local/test draft repository.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java` - Redis draft repository.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationDraftRepositoryTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepositoryTest.java`

Modify:

- `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRegistrationActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`
- `docs/handbook/business-flows.md`
- `docs/handbook/core-logic-index.md`

Delete:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationSessionRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationSessionRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationSessionRepository.java`

Keep unchanged except for compile-driven imports:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/job/PendingRegistrationUserCleanupJob.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/job/PendingRegistrationUserCleanupHandler.java`

---

### Task 1: Add User-Domain Prepare And Final Create APIs

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/PreparedRegistrationUserView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/VerifiedRegistrationUserCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/PreparedRegistrationUserResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/CreateVerifiedRegistrationUserCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRegistrationActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`

- [ ] **Step 1: Write failing user application tests**

Add these test methods to `UserRegistrationApplicationServiceTest`:

```java
@Test
void prepareRegistrationUserShouldReturnPreparedMaterialWithoutWritingUserOrPublishingEvent() {
    UserRegistrationApplicationService service = service();

    PreparedRegistrationUserResult prepared = service.prepareRegistrationUser(
            "  alice  ",
            "  secret12  ",
            "  alice@example.com  "
    );

    assertThat(prepared.userId()).isNotNull();
    assertThat(prepared.username()).isEqualTo("alice");
    assertThat(prepared.email()).isEqualTo("alice@example.com");
    assertThat(new BCryptPasswordEncoder().matches("secret12", prepared.encodedPassword())).isTrue();
    assertThat(prepared.headerUrl()).startsWith("http://images.nowcoder.com/head/");
    verify(userRepository, never()).insertUser(any());
    verify(userRepository, never()).insertPendingUser(any());
    verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class));
}

@Test
void createVerifiedRegistrationUserShouldInsertActiveUserAndPublishExistenceEvent() {
    UserRegistrationApplicationService service = service();
    UUID userId = userId(21);

    UserCredentialResult result = service.createVerifiedRegistrationUser(new CreateVerifiedRegistrationUserCommand(
            userId,
            "alice",
            "encoded-password",
            "alice@example.com",
            "http://images.nowcoder.com/head/1t.png"
    ));

    ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
    verify(userRepository).insertUser(userCaptor.capture());
    UserAccount inserted = userCaptor.getValue();
    assertThat(inserted.id()).isEqualTo(userId);
    assertThat(inserted.username()).isEqualTo("alice");
    assertThat(inserted.email()).isEqualTo("alice@example.com");
    assertThat(inserted.encodedPassword()).isEqualTo("encoded-password");
    assertThat(inserted.status()).isEqualTo(1);
    assertThat(inserted.type()).isEqualTo(0);
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.status()).isEqualTo(1);
    verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId), eq(true), any(Instant.class));
}

@Test
void createVerifiedRegistrationUserShouldTranslateDuplicateEmailRace() {
    UserRegistrationApplicationService service = service();
    doThrow(new DuplicateKeyException("uk_user_email")).when(userRepository).insertUser(any());

    assertThatThrownBy(() -> service.createVerifiedRegistrationUser(new CreateVerifiedRegistrationUserCommand(
            userId(22),
            "alice",
            "encoded-password",
            "alice@example.com",
            "h"
    )))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

    verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class));
}
```

Add imports:

```java
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PreparedRegistrationUserResult;

import static org.mockito.ArgumentMatchers.anyBoolean;
```

- [ ] **Step 2: Run the focused user test and confirm it fails**

Run:

```bash
mvn -pl :community-app -Dtest=UserRegistrationApplicationServiceTest test
```

Expected: compilation fails because `PreparedRegistrationUserResult`, `CreateVerifiedRegistrationUserCommand`, `insertUser`, `prepareRegistrationUser`, and `createVerifiedRegistrationUser` do not exist.

- [ ] **Step 3: Add user API and application model records**

Create `PreparedRegistrationUserView.java`:

```java
package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record PreparedRegistrationUserView(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl
) {
}
```

Create `VerifiedRegistrationUserCommand.java`:

```java
package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record VerifiedRegistrationUserCommand(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl
) {
}
```

Create `PreparedRegistrationUserResult.java`:

```java
package com.nowcoder.community.user.application.result;

import java.util.UUID;

public record PreparedRegistrationUserResult(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl
) {
}
```

Create `CreateVerifiedRegistrationUserCommand.java`:

```java
package com.nowcoder.community.user.application.command;

import java.util.UUID;

public record CreateVerifiedRegistrationUserCommand(
        UUID userId,
        String username,
        String encodedPassword,
        String email,
        String headerUrl
) {
}
```

- [ ] **Step 4: Add repository and domain-service support for active user insert**

Modify `UserRepository`:

```java
void insertUser(UserAccount user);

void insertPendingUser(UserAccount user);
```

Modify `MyBatisUserRepository`:

```java
@Override
public void insertUser(UserAccount user) {
    int inserted = userMapper.insertUser(toDataObject(user));
    if (inserted <= 0) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
    }
}

@Override
public void insertPendingUser(UserAccount user) {
    insertUser(user);
}
```

Modify `UserRegistrationDomainService` with this method:

```java
public UserAccount verifiedUser(
        java.util.UUID userId,
        String username,
        String encodedPassword,
        String email,
        String headerUrl
) {
    return new UserAccount(
            userId,
            safeTrim(username),
            safeTrim(encodedPassword),
            "",
            safeTrim(email),
            0,
            1,
            safeTrim(headerUrl),
            Date.from(Instant.now(clock)),
            0,
            null,
            null
    );
}
```

- [ ] **Step 5: Implement user application methods**

Modify `UserRegistrationApplicationService` imports:

```java
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PreparedRegistrationUserResult;
```

Add methods:

```java
public PreparedRegistrationUserResult prepareRegistrationUser(String username, String password, String email) {
    RegistrationInput input = userRegistrationDomainService.requireValidRegistration(username, password, email);
    UserAccount prepared = userRegistrationDomainService.pendingUser(
            idGenerator.next(),
            input,
            passwordEncoder.encode(input.password()),
            randomHeaderUrl()
    );
    return new PreparedRegistrationUserResult(
            prepared.id(),
            prepared.username(),
            prepared.email(),
            prepared.encodedPassword(),
            prepared.headerUrl()
    );
}

@Transactional
public UserCredentialResult createVerifiedRegistrationUser(CreateVerifiedRegistrationUserCommand command) {
    if (command == null || command.userId() == null) {
        throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
    }
    if (!hasText(command.username()) || !hasText(command.email()) || !hasText(command.encodedPassword())) {
        throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
    }
    UserAccount user = userRegistrationDomainService.verifiedUser(
            command.userId(),
            command.username(),
            command.encodedPassword(),
            command.email(),
            command.headerUrl()
    );
    try {
        userRepository.insertUser(user);
    } catch (DataIntegrityViolationException ex) {
        translateDuplicateInsert(new RegistrationInput(user.username(), user.encodedPassword(), user.email()), ex);
    }
    publishUserPolicyChanged(user.id(), true);
    return toCredentialResult(user, 1);
}

private boolean hasText(String value) {
    return value != null && !value.isBlank();
}
```

- [ ] **Step 6: Publish the methods through user API adapter**

Modify `UserRegistrationActionApi`:

```java
PreparedRegistrationUserView prepareRegistrationUser(String username, String password, String email);

UserCredentialView createVerifiedRegistrationUser(VerifiedRegistrationUserCommand command);
```

Add imports:

```java
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
```

Modify `UserRegistrationApiAdapter`:

```java
@Override
public PreparedRegistrationUserView prepareRegistrationUser(String username, String password, String email) {
    PreparedRegistrationUserResult result = applicationService.prepareRegistrationUser(username, password, email);
    return new PreparedRegistrationUserView(
            result.userId(),
            result.username(),
            result.email(),
            result.encodedPassword(),
            result.headerUrl()
    );
}

@Override
public UserCredentialView createVerifiedRegistrationUser(VerifiedRegistrationUserCommand command) {
    if (command == null) {
        return null;
    }
    return toCredentialView(applicationService.createVerifiedRegistrationUser(new CreateVerifiedRegistrationUserCommand(
            command.userId(),
            command.username(),
            command.encodedPassword(),
            command.email(),
            command.headerUrl()
    )));
}
```

Add imports:

```java
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PreparedRegistrationUserResult;
```

- [ ] **Step 7: Run the focused user test and commit**

Run:

```bash
mvn -pl :community-app -Dtest=UserRegistrationApplicationServiceTest test
```

Expected: all tests in `UserRegistrationApplicationServiceTest` pass.

Commit:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java
git commit -m "feat: add verify-first user registration APIs"
```

---

### Task 2: Add Registration Draft Repository

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PreparedRegistrationDraft.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationDraftRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationDraftRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationDraftRepositoryTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepositoryTest.java`

- [ ] **Step 1: Write draft repository tests**

Create `InMemoryRegistrationDraftRepositoryTest.java`:

```java
package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRegistrationDraftRepositoryTest {

    @Test
    void issueFindAndDeleteShouldRoundTripDraft() {
        InMemoryRegistrationDraftRepository repository = new InMemoryRegistrationDraftRepository();
        PreparedRegistrationDraft draft = draft();

        String token = repository.issue(draft, Duration.ofMinutes(30));

        assertThat(token).matches("[a-f0-9]{32}");
        assertThat(repository.find(token)).contains(draft);
        repository.delete(token);
        assertThat(repository.find(token)).isEmpty();
    }

    @Test
    void findShouldRemoveExpiredDraft() throws Exception {
        InMemoryRegistrationDraftRepository repository = new InMemoryRegistrationDraftRepository();
        String token = repository.issue(draft(), Duration.ofMillis(1));

        Thread.sleep(5);

        assertThat(repository.find(token)).isEmpty();
        assertThat(repository.find(token)).isEmpty();
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
```

Create `RedisRegistrationDraftRepositoryTest.java`:

```java
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
import static org.mockito.ArgumentMatchers.anyString;
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
```

- [ ] **Step 2: Run draft tests and confirm they fail**

Run:

```bash
mvn -pl :community-app -Dtest='InMemoryRegistrationDraftRepositoryTest,RedisRegistrationDraftRepositoryTest' test
```

Expected: compilation fails because draft model and repositories do not exist.

- [ ] **Step 3: Add draft model and repository interface**

Create `PreparedRegistrationDraft.java`:

```java
package com.nowcoder.community.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PreparedRegistrationDraft(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl,
        Instant issuedAt,
        Instant expiresAt
) {
}
```

Create `RegistrationDraftRepository.java`:

```java
package com.nowcoder.community.auth.domain.repository;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;

import java.time.Duration;
import java.util.Optional;

public interface RegistrationDraftRepository {

    String issue(PreparedRegistrationDraft draft, Duration ttl);

    Optional<PreparedRegistrationDraft> find(String registrationToken);

    void delete(String registrationToken);
}
```

- [ ] **Step 4: Add in-memory implementation**

Create `InMemoryRegistrationDraftRepository.java`:

```java
package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.registration.draft.store", havingValue = "memory")
public class InMemoryRegistrationDraftRepository implements RegistrationDraftRepository {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String issue(PreparedRegistrationDraft draft, Duration ttl) {
        if (draft == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }
        long expiresAtMs = System.currentTimeMillis() + ttl.toMillis();
        for (int i = 0; i < 5; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (store.putIfAbsent(token, new Entry(draft, expiresAtMs)) == null) {
                return token;
            }
        }
        return null;
    }

    @Override
    public Optional<PreparedRegistrationDraft> find(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return Optional.empty();
        }
        String token = registrationToken.trim();
        Entry entry = store.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMs() < System.currentTimeMillis()) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.draft());
    }

    @Override
    public void delete(String registrationToken) {
        if (StringUtils.hasText(registrationToken)) {
            store.remove(registrationToken.trim());
        }
    }

    private record Entry(PreparedRegistrationDraft draft, long expiresAtMs) {
    }
}
```

- [ ] **Step 5: Add Redis implementation**

Create `RedisRegistrationDraftRepository.java`:

```java
package com.nowcoder.community.auth.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.registration.draft.store", havingValue = "redis", matchIfMissing = true)
public class RedisRegistrationDraftRepository implements RegistrationDraftRepository {

    private static final String KEY_PREFIX = "auth:regdraft:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRegistrationDraftRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String issue(PreparedRegistrationDraft draft, Duration ttl) {
        if (draft == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(draft), ttl);
            return token;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("registration draft 序列化失败", ex);
        }
    }

    @Override
    public Optional<PreparedRegistrationDraft> find(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return Optional.empty();
        }
        String token = registrationToken.trim();
        String raw = redisTemplate.opsForValue().get(key(token));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, PreparedRegistrationDraft.class));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(key(token));
            return Optional.empty();
        }
    }

    @Override
    public void delete(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return;
        }
        try {
            redisTemplate.delete(key(registrationToken.trim()));
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
```

- [ ] **Step 6: Run draft tests and commit**

Run:

```bash
mvn -pl :community-app -Dtest='InMemoryRegistrationDraftRepositoryTest,RedisRegistrationDraftRepositoryTest' test
```

Expected: both draft repository tests pass.

Commit:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PreparedRegistrationDraft.java backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationDraftRepository.java backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationDraftRepository.java backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationDraftRepositoryTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepositoryTest.java
git commit -m "feat: add registration draft repository"
```

---

### Task 3: Change Register To Create Draft And Code Only

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java`

- [ ] **Step 1: Rewrite registration application tests**

Update mocks in `RegistrationApplicationServiceTest`:

```java
@Mock
private RegistrationDraftRepository registrationDraftRepository;
```

Remove the `RegistrationSessionRepository` mock from this test. Add imports:

```java
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
```

Construct the service with `registrationDraftRepository`:

```java
service = new RegistrationApplicationService(
        userRegistrationActionApi,
        properties,
        mailService,
        captchaService,
        registrationCodeStore,
        registrationDraftRepository,
        new RegistrationDomainService()
);
```

Replace `registerShouldIssueEmailCodeAndReturnMaskedEmailAndDebugCode` setup and verification:

```java
PreparedRegistrationUserView prepared = new PreparedRegistrationUserView(
        userId,
        "alice",
        "alice@example.com",
        "encoded-password",
        "h"
);

when(captchaService.verify("cid", "abcd")).thenReturn(true);
when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
when(registrationDraftRepository.issue(any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30))))
        .thenReturn("0123456789abcdef0123456789abcdef");
when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
        .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);
```

Replace verification lines:

```java
verify(userRegistrationActionApi).prepareRegistrationUser("alice", "secret", "alice@example.com");
verify(userRegistrationActionApi, never()).registerPendingUser(any(), any(), any(), any());
verify(userRegistrationActionApi, never()).deletePendingUser(any(UUID.class));
verify(registrationDraftRepository).issue(any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30)));
verify(registrationCodeStore).issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
verify(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));
```

Replace rollback test assertions:

```java
verify(registrationDraftRepository).delete("0123456789abcdef0123456789abcdef");
verify(registrationCodeStore).delete(userId);
verify(userRegistrationActionApi, never()).deletePendingUser(any(UUID.class));
```

Replace session-creation failure test with draft-creation failure:

```java
@Test
void registerShouldFailBeforeIssuingCodeWhenRegistrationDraftCreationFails() {
    UUID userId = uuid(9);
    RegisterCommand command = registerCommand();
    PreparedRegistrationUserView prepared = new PreparedRegistrationUserView(userId, "alice", "alice@example.com", "encoded-password", "h");

    when(captchaService.verify("cid", "abcd")).thenReturn(true);
    when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
    when(registrationDraftRepository.issue(any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30)))).thenReturn(null);

    assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR);

    verify(registrationCodeStore, never()).issue(any(), any(), any(), any());
    verify(mailService, never()).sendRegistrationCodeMail(any(), any());
    verify(registrationCodeStore).delete(userId);
    verify(userRegistrationActionApi, never()).deletePendingUser(any(UUID.class));
}
```

- [ ] **Step 2: Run registration application test and confirm it fails**

Run:

```bash
mvn -pl :community-app -Dtest=RegistrationApplicationServiceTest test
```

Expected: compilation fails because `RegistrationApplicationService` still depends on `RegistrationSessionRepository` and calls pending-user APIs.

- [ ] **Step 3: Refactor `RegistrationApplicationService` constructor and fields**

Replace field:

```java
private final RegistrationDraftRepository registrationDraftRepository;
```

Replace constructor parameter:

```java
RegistrationDraftRepository registrationDraftRepository,
```

Assign:

```java
this.registrationDraftRepository = registrationDraftRepository;
```

Add imports:

```java
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
```

Remove imports:

```java
import com.nowcoder.community.auth.domain.repository.RegistrationSessionRepository;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
```

- [ ] **Step 4: Replace pending-user creation with draft creation**

Replace the pending-user block in `register` with:

```java
Duration pendingUserTtl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
PreparedRegistrationUserView prepared = userRegistrationActionApi.prepareRegistrationUser(username, password, email);
if (prepared == null || prepared.userId() == null) {
    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
}
String targetEmail = StringUtils.hasText(prepared.email()) ? prepared.email() : email;

String code = generateCode();
Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
String registrationToken = null;
try {
    Instant issuedAt = Instant.now();
    registrationToken = registrationDraftRepository == null ? null : registrationDraftRepository.issue(
            new PreparedRegistrationDraft(
                    prepared.userId(),
                    prepared.username(),
                    prepared.email(),
                    prepared.encodedPassword(),
                    prepared.headerUrl(),
                    issuedAt,
                    issuedAt.plus(pendingUserTtl)
            ),
            pendingUserTtl
    );
    if (!StringUtils.hasText(registrationToken)) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
    }

    RegistrationCodeRepository.IssueResult issueResult = registrationCodeStore.issue(prepared.userId(), code, ttl, cooldown);
    if (issueResult != RegistrationCodeRepository.IssueResult.ISSUED) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册验证码签发失败");
    }

    mailService.sendRegistrationCodeMail(targetEmail, code);
} catch (RuntimeException ex) {
    rollbackFailedRegistration(prepared.userId(), registrationToken);
    throw ex;
}
```

Add import:

```java
import java.time.Instant;
```

Update security event and response to use `prepared.userId()`:

```java
"user.id", prepared.userId(),
```

```java
return new RegisterResult(
        prepared.userId(),
        registrationToken,
        true,
        registrationDomainService.maskEmail(targetEmail),
        properties.getCode().isExposeCode() ? code : null
);
```

- [ ] **Step 5: Remove pending-user rollback**

Replace `rollbackFailedRegistration` with:

```java
private void rollbackFailedRegistration(UUID userId, String registrationToken) {
    if (StringUtils.hasText(registrationToken) && registrationDraftRepository != null) {
        try {
            registrationDraftRepository.delete(registrationToken);
        } catch (RuntimeException cleanupEx) {
            log.warn("[registration] failed to cleanup draft for userId={}: {}", userId, cleanupEx.toString());
        }
    }

    if (userId != null && registrationCodeStore != null) {
        try {
            registrationCodeStore.delete(userId);
        } catch (RuntimeException cleanupEx) {
            log.warn("[registration] failed to cleanup code for userId={}: {}", userId, cleanupEx.toString());
        }
    }
}
```

- [ ] **Step 6: Verify register no longer uses session store, run registration test, and commit**

Run:

```bash
rg -n "RegistrationSessionRepository|RedisRegistrationSessionRepository|InMemoryRegistrationSessionRepository|auth:regsession" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: `RegistrationApplicationService` and `RegistrationApplicationServiceTest` no longer reference `RegistrationSessionRepository`, `RedisRegistrationSessionRepository`, `InMemoryRegistrationSessionRepository`, or `auth:regsession`. References may still remain in `RegistrationVerificationApplicationService`, `RegistrationVerificationApplicationServiceTest`, and the old session repository interface/implementations because Task 4 deletes them while switching verification to drafts.

Run:

```bash
mvn -pl :community-app -Dtest=RegistrationApplicationServiceTest test
```

Expected: `RegistrationApplicationServiceTest` passes.

Commit:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java
git commit -m "feat: issue registration drafts before verification"
```

---

### Task 4: Change Resend And Verify To Use Drafts

**Files:**
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationSessionRepository.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationSessionRepository.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationSessionRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`

- [ ] **Step 1: Rewrite verification application test setup**

Remove `UserPendingRegistrationQueryApi` and the old `RegistrationSessionRepository` mocks/imports from `RegistrationVerificationApplicationServiceTest`. Add:

```java
@Mock
private RegistrationDraftRepository registrationDraftRepository;
```

Add imports:

```java
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
```

Construct the service:

```java
service = new RegistrationVerificationApplicationService(
        userRegistrationActionApi,
        properties,
        registrationCodeStore,
        mailService,
        captchaService,
        registrationDraftRepository,
        authService,
        new RegistrationDomainService()
);
```

Add helper:

```java
private static PreparedRegistrationDraft draft(UUID userId) {
    return new PreparedRegistrationDraft(
            userId,
            "alice",
            "alice@example.com",
            "encoded-password",
            "h",
            java.time.Instant.parse("2026-05-03T01:00:00Z"),
            java.time.Instant.parse("2026-05-03T01:30:00Z")
    );
}
```

- [ ] **Step 2: Update resend tests to read drafts**

In `resendCodeShouldRequireCaptchaAndReturnIssuedResponse`, replace pending setup:

```java
when(registrationDraftRepository.find("token")).thenReturn(java.util.Optional.of(draft(userId)));
```

Remove:

```java
when(registrationSessionStore.findUserId("token")).thenReturn(userId);
when(userPendingRegistrationQueryApi.getPendingUser(userId, Duration.ofMinutes(30))).thenReturn(user);
```

Keep code issue and mail assertions. Verify no final create during resend:

```java
verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
```

Update cooldown and missing-token tests the same way.

- [ ] **Step 3: Update verify success and invalid-code tests**

In `verifyAndLoginShouldActivateInactiveUserAndReturnLoginResult`, replace setup:

```java
when(registrationDraftRepository.find("token")).thenReturn(java.util.Optional.of(draft(userId)));
when(registrationCodeStore.verifyAndConsume(userId, "222222")).thenReturn(RegistrationCodeRepository.VerifyResult.SUCCESS);
when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class))).thenReturn(activatedUser);
when(authService.issueLoginResult(activatedUser)).thenReturn(new LoginResult("access-token", cookie));
```

Replace assertions:

```java
ArgumentCaptor<VerifiedRegistrationUserCommand> commandCaptor = ArgumentCaptor.forClass(VerifiedRegistrationUserCommand.class);
verify(userRegistrationActionApi).createVerifiedRegistrationUser(commandCaptor.capture());
assertThat(commandCaptor.getValue().userId()).isEqualTo(userId);
assertThat(commandCaptor.getValue().username()).isEqualTo("alice");
assertThat(commandCaptor.getValue().email()).isEqualTo("alice@example.com");
assertThat(commandCaptor.getValue().encodedPassword()).isEqualTo("encoded-password");
verify(userRegistrationActionApi, never()).activatePendingUser(any(UUID.class));
verify(registrationDraftRepository).delete("token");
verify(authService).issueLoginResult(activatedUser);
```

Add import:

```java
import org.mockito.ArgumentCaptor;
```

In invalid-code test, replace pending setup:

```java
when(registrationDraftRepository.find("token")).thenReturn(java.util.Optional.of(draft(userId)));
when(registrationCodeStore.verifyAndConsume(userId, "111111")).thenReturn(RegistrationCodeRepository.VerifyResult.MISMATCH);
```

Assert:

```java
verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class));
verify(registrationDraftRepository, never()).delete(any());
```

- [ ] **Step 4: Run verification test and confirm it fails**

Run:

```bash
mvn -pl :community-app -Dtest=RegistrationVerificationApplicationServiceTest test
```

Expected: compilation fails because the service constructor and implementation still use pending-user query and old session APIs that are removed later in this task.

- [ ] **Step 5: Delete old registration session store files**

Delete:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationSessionRepository.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationSessionRepository.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationSessionRepository.java
```

Run:

```bash
rg -n "RegistrationSessionRepository|RedisRegistrationSessionRepository|InMemoryRegistrationSessionRepository|auth:regsession" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: references remain only in `RegistrationVerificationApplicationService` and `RegistrationVerificationApplicationServiceTest` before this task finishes.

- [ ] **Step 6: Refactor `RegistrationVerificationApplicationService` constructor and fields**

Replace fields with:

```java
private final UserRegistrationActionApi userRegistrationActionApi;
private final RegistrationDraftRepository registrationDraftRepository;
```

Remove:

```java
private final UserPendingRegistrationQueryApi userPendingRegistrationQueryApi;
private final RegistrationSessionRepository registrationSessionStore;
```

Replace constructor signature:

```java
public RegistrationVerificationApplicationService(
        UserRegistrationActionApi userRegistrationActionApi,
        RegistrationProperties properties,
        RegistrationCodeRepository registrationCodeStore,
        MailPort mailService,
        CaptchaApplicationService captchaService,
        RegistrationDraftRepository registrationDraftRepository,
        LoginApplicationService authService,
        RegistrationDomainService registrationDomainService
)
```

Add imports:

```java
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
```

Remove imports:

```java
import com.nowcoder.community.auth.domain.repository.RegistrationSessionRepository;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import com.nowcoder.community.user.api.query.UserPendingRegistrationQueryApi;
```

- [ ] **Step 7: Replace pending-user lookup with draft lookup**

Add helper:

```java
private PreparedRegistrationDraft resolveDraftOrThrow(String registrationToken) {
    if (!StringUtils.hasText(registrationToken)) {
        throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "registrationToken 不能为空");
    }
    if (registrationDraftRepository == null) {
        throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
    }
    return registrationDraftRepository.find(registrationToken.trim())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID));
}
```

Delete `resolveUserIdOrThrow` and `requirePendingUser`.

- [ ] **Step 8: Implement resend using draft**

Replace:

```java
UUID userId = resolveUserIdOrThrow(registrationToken);
PendingRegistrationUserView user = requirePendingUser(userId);
```

with:

```java
PreparedRegistrationDraft draft = resolveDraftOrThrow(registrationToken);
UUID userId = draft.userId();
```

Replace mail/response email usage:

```java
mailService.sendRegistrationCodeMail(draft.email(), code);
```

```java
registrationDomainService.maskEmail(draft.email())
```

- [ ] **Step 9: Implement verify using final user creation**

Replace the start of `verifyAndLogin` after argument validation:

```java
PreparedRegistrationDraft draft = resolveDraftOrThrow(registrationToken);
UUID userId = draft.userId();
```

Replace activation block:

```java
UserCredentialView activatedUser = userRegistrationActionApi.createVerifiedRegistrationUser(new VerifiedRegistrationUserCommand(
        draft.userId(),
        draft.username(),
        draft.email(),
        draft.encodedPassword(),
        draft.headerUrl()
));
if (activatedUser == null || activatedUser.userId() == null) {
    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
}
LoginResult loginResult = authService.issueLoginResult(activatedUser);
SecurityEventLogger.info(log, "registration_verify", "success",
        "user.id", activatedUser.userId(),
        "username", activatedUser.username());
try {
    registrationDraftRepository.delete(registrationToken);
} catch (RuntimeException ignored) {
    // best-effort cleanup
}
return loginResult;
```

- [ ] **Step 10: Verify old session references are gone, run verification tests, and commit**

Run:

```bash
rg -n "RegistrationSessionRepository|RedisRegistrationSessionRepository|InMemoryRegistrationSessionRepository|auth:regsession" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: no output.

Run:

```bash
mvn -pl :community-app -Dtest=RegistrationVerificationApplicationServiceTest test
```

Expected: `RegistrationVerificationApplicationServiceTest` passes.

Commit:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java
git add -u backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationSessionRepository.java backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationSessionRepository.java backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/InMemoryRegistrationSessionRepository.java
git commit -m "feat: verify registration drafts into active users"
```

---

### Task 5: Update Documentation And Removed-Session Guardrails

**Files:**
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/core-logic-index.md`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/job/PendingRegistrationUserCleanupJobTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapterIntegrationTest.java`

- [ ] **Step 1: Update business flow documentation**

In `docs/handbook/business-flows.md`, update the registration flow section to this text:

```markdown
### Register And Verify Email

The high-concurrency registration flow is Verify-First:

1. `AuthController.register` calls `RegistrationApplicationService.register`.
2. Auth validates captcha and request fields, then calls `user.api.action.UserRegistrationActionApi.prepareRegistrationUser`.
3. User prepares normalized username/email, generated provisional user id, BCrypt password hash, and default avatar URL without inserting a `user` row.
4. Auth stores `PreparedRegistrationDraft` behind an opaque `registrationToken` and issues a registration code.
5. `AuthController.verifyRegisterCode` calls `RegistrationVerificationApplicationService.verifyAndLogin`.
6. Auth resolves the draft, consumes the code, then calls `user.api.action.UserRegistrationActionApi.createVerifiedRegistrationUser`.
7. User inserts one active `user` row with `status=1` and publishes `UserPolicyChanged(userExists=true)`.
8. Auth issues login tokens and deletes the draft as best-effort cleanup.

Abandoned registrations expire from the draft/code stores and do not create user rows or IM policy events.
```

- [ ] **Step 2: Update core logic index**

In `docs/handbook/core-logic-index.md`, update registration entries to include:

```markdown
- `auth.application.RegistrationApplicationService`: Verify-First registration start; creates registration draft and code after user-domain preparation.
- `auth.application.RegistrationVerificationApplicationService`: resolves registration drafts, resends codes, consumes verification codes, and asks user domain to create the active user.
- `auth.domain.repository.RegistrationDraftRepository`: opaque `registrationToken` to prepared registration draft store with TTL.
- Removed `auth.domain.repository.RegistrationSessionRepository`: registration tokens no longer resolve to user ids; they resolve to full registration drafts.
- `user.application.UserRegistrationApplicationService#prepareRegistrationUser`: validates and prepares registration material without database writes or events.
- `user.application.UserRegistrationApplicationService#createVerifiedRegistrationUser`: inserts the active user and publishes user policy existence.
```

- [ ] **Step 3: Keep pending cleanup compatibility tests explicit**

Add this assertion to `PendingRegistrationUserCleanupJobTest.cleanupShouldDelegateToUserRegistrationServiceWithConfiguredTtlWhenLocalSchedulerEnabled` after existing verifications:

```java
assertThat(properties.getPendingUser().isLocalSchedulerEnabled()).isTrue();
```

Add this comment above the test:

```java
// Compatibility cleanup remains for rows created by the previous pending-user registration flow.
```

Add this comment above `UserRegistrationApiAdapterIntegrationTest.expiredPendingLookupShouldCommitCleanupBeforeThrowingNotFound`:

```java
// Compatibility behavior for pending users created before Verify-First registration was deployed.
```

- [ ] **Step 4: Verify old registration sessions are absent**

Run:

```bash
rg -n "RegistrationSessionRepository|RedisRegistrationSessionRepository|InMemoryRegistrationSessionRepository|auth:regsession|auth\\.registration\\.session\\.store" backend/community-app/src/main/java backend/community-app/src/test/java docs/handbook
```

Expected: no output.

- [ ] **Step 5: Run doc-adjacent compatibility tests and commit**

Run:

```bash
mvn -pl :community-app -Dtest='PendingRegistrationUserCleanupJobTest,UserRegistrationApiAdapterIntegrationTest' test
```

Expected: both tests pass.

Commit:

```bash
git add docs/handbook/business-flows.md docs/handbook/core-logic-index.md backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/job/PendingRegistrationUserCleanupJobTest.java backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapterIntegrationTest.java
git commit -m "docs: document verify-first registration flow"
```

---

### Task 6: Final Verification And Guardrails

**Files:**
- Verify only; no source edits expected.

- [ ] **Step 1: Run focused registration suite**

Run:

```bash
mvn -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,InMemoryRegistrationDraftRepositoryTest,RedisRegistrationDraftRepositoryTest,PendingRegistrationUserCleanupJobTest,UserRegistrationApiAdapterIntegrationTest' test
```

Expected: build exits `0`.

- [ ] **Step 2: Run architecture guardrails**

Run:

```bash
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: all ArchUnit tests pass.

- [ ] **Step 3: Run full community-app test suite**

Run:

```bash
mvn -pl :community-app test
```

Expected: build exits `0`.

- [ ] **Step 4: Inspect registration event behavior**

Run:

```bash
rg -n "registerPendingUser\\(|activatePendingUser\\(|deletePendingUser\\(|UserPolicyChanged|publishUserPolicyChanged" backend/community-app/src/main/java/com/nowcoder/community/auth backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java
```

Expected:

- `auth` application code does not call `registerPendingUser`, `activatePendingUser`, or `deletePendingUser`.
- `prepareRegistrationUser` has no `publishUserPolicyChanged` call.
- `createVerifiedRegistrationUser` publishes `publishUserPolicyChanged(userId, true)`.
- old pending methods still publish compatibility events only inside user application.

- [ ] **Step 5: Verify old registration session store was deleted**

Run:

```bash
rg -n "RegistrationSessionRepository|RedisRegistrationSessionRepository|InMemoryRegistrationSessionRepository|auth:regsession|auth\\.registration\\.session\\.store" backend/community-app/src/main/java backend/community-app/src/test/java backend/community-app/src/main/resources backend/community-app/src/test/resources deploy docs/handbook
```

Expected: no output.

- [ ] **Step 6: Commit any verification-only fixes**

If Step 1, Step 2, or Step 3 fails, fix the failing code in the smallest relevant task area and rerun the failing command. Commit the fix:

```bash
git add backend/community-app/src/main/java backend/community-app/src/test/java docs/handbook
git commit -m "fix: align verify-first registration flow"
```

If every command passes and there are no source changes, do not create an empty commit.
