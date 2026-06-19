# Auth/User Security Boundary Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden auth/user session consistency, token freshness, registration/password-reset concurrency, refresh-token storage, DDD boundaries, and input validation according to the approved design.

**Architecture:** Keep `auth` as the owner of authentication flows and token strategy, and `user` as the owner of account facts, role, password hash, moderation state, auth security version, and DB refresh session facts. Inbound adapters enter same-domain `*ApplicationService`; auth application calls user owner `api.*` for cross-domain facts; infrastructure implements repositories and technical ports.

**Tech Stack:** Java 17, Spring Boot, Spring Security resource server, MyBatis, Redis Lua via `StringRedisTemplate`, JUnit 5, Mockito, AssertJ, Maven, H2 test schema, MySQL init SQL.

---

## Spec And Current Code Context

Approved spec:

- `docs/superpowers/specs/2026-06-19-auth-user-security-boundary-hardening-design.md`

Key current files:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/DbRefreshTokenRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/jwt/JwtTokenService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- `backend/community-app/src/main/resources/mapper/user_mapper.xml`
- `deploy/mysql/community/020_schema_identity.sql`
- `backend/community-app/src/test/resources/schema.sql`

## File Structure Map

Create:

- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserRole.java`
  Domain role value object for legal `user.type` values and authority mapping hints.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationService.java`
  Auth same-domain application boundary for high-risk path token freshness checks.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/TokenFreshnessResult.java`
  Application result describing accepted, stale, or denied token freshness status.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginTokenIssuer.java`
  Application component that signs JWT access tokens and issues refresh token cookies.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaChallengeComponent.java`
  Application component for captcha required/verify error conversion.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/web/TokenFreshnessFilter.java`
  Servlet filter for high-risk paths; it only adapts request/JWT and calls auth application.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/web/TokenFreshnessFilterTest.java`

Move:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/DbRefreshTokenRepository.java`
  to `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepository.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/DbRefreshTokenRepositoryTest.java`
  to `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepositoryTest.java`

Modify:

- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRoleDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserCredentialDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserCredentialResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserAuthenticationResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserCredentialView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserAuthenticationResultView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserCredentialApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/UserDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/UserMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepository.java`
- `backend/community-app/src/main/resources/mapper/user_mapper.xml`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/port/AuthTokenPort.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/jwt/JwtTokenService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationCodeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetRequestRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetConfirmRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterCodeVerifyRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterCodeResendRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java`
- `deploy/mysql/community/020_schema_identity.sql`
- `backend/community-app/src/test/resources/schema.sql`
- `docs/handbook/security.md`
- `docs/handbook/auth-login-session-flow.md`
- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/user.md`
- `docs/handbook/data-and-storage.md`

## Implementation Rules

- Do not add controller/listener/job/filter direct calls to foreign owner APIs. `TokenFreshnessFilter` must call auth `TokenFreshnessApplicationService`; that application service may call `UserCredentialQueryApi`.
- Do not add `ApplicationService -> MyBatis mapper` dependencies.
- Keep DTOs free of application/domain models.
- Keep domain free of Spring, MyBatis, HTTP DTO, infrastructure, and `api.*`.
- Keep every task commit scoped to its changed files.
- Run focused tests after each task and the full architecture tests at the end.

---

### Task 1: Add User Auth Security Version And Role Invariant

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserRole.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRoleDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserCredentialDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserCredentialResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserCredentialView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/UserDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/UserMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepository.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_mapper.xml`
- Modify: `deploy/mysql/community/020_schema_identity.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserRoleDomainServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepositoryTest.java`

- [ ] **Step 1: Add failing role invariant tests**

Add to `UserRoleDomainServiceTest`:

```java
@Test
void requireValidCommandShouldRejectUnknownRoleType() {
    UserRoleDomainService service = new UserRoleDomainService();

    Throwable thrown = catchThrowable(() -> service.requireValidCommand(
            true,
            UUID.fromString("00000000-0000-7000-8000-000000000001"),
            99,
            "invalid role",
            true
    ));

    assertThat(thrown).isInstanceOf(BusinessException.class)
            .hasMessage("用户角色类型非法");
}
```

Ensure imports include:

```java
import com.nowcoder.community.common.exception.BusinessException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
```

- [ ] **Step 2: Add failing securityVersion projection test**

Update the existing `getByUserIdShouldProjectCredentialResult` assertion in `UserCredentialApplicationServiceTest` so it also checks `securityVersion`:

```java
assertThat(credential).extracting(
        UserCredentialResult::userId,
        UserCredentialResult::username,
        UserCredentialResult::status,
        UserCredentialResult::type,
        UserCredentialResult::headerUrl,
        UserCredentialResult::securityVersion
).containsExactly(userId, "alice", 1, 0, "h7", 0L);
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRoleDomainServiceTest,UserCredentialApplicationServiceTest' -DfailIfNoTests=false
```

Expected: fails because `securityVersion()` does not exist and unknown role type is not rejected.

- [ ] **Step 4: Add the `UserRole` value object**

Create `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserRole.java`:

```java
package com.nowcoder.community.user.domain.model;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.Arrays;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public enum UserRole {
    USER(0, List.of("ROLE_USER")),
    ADMIN(1, List.of("ROLE_ADMIN")),
    MODERATOR(2, List.of("ROLE_MODERATOR"));

    private final int type;
    private final List<String> authorities;

    UserRole(int type, List<String> authorities) {
        this.type = type;
        this.authorities = authorities;
    }

    public int type() {
        return type;
    }

    public List<String> authorities() {
        return authorities;
    }

    public static UserRole requireValid(int type) {
        return Arrays.stream(values())
                .filter(role -> role.type == type)
                .findFirst()
                .orElseThrow(() -> new BusinessException(INVALID_ARGUMENT, "用户角色类型非法"));
    }
}
```

- [ ] **Step 5: Add `securityVersion` to domain/result/API/dataobject**

Update `UserAccount` record fields:

```java
        Instant banUntil,
        long policyVersion,
        long securityVersion
) {
```

Update its compatibility constructor:

```java
        this(id, username, encodedPassword, salt, email, type, status, headerUrl, createTime, muteUntil, banUntil, 0L, 0L);
```

Add a second compatibility constructor if many tests construct with `policyVersion`:

```java
    public UserAccount(
            UUID id,
            String username,
            String encodedPassword,
            String salt,
            String email,
            int type,
            int status,
            String headerUrl,
            Date createTime,
            Instant muteUntil,
            Instant banUntil,
            long policyVersion
    ) {
        this(id, username, encodedPassword, salt, email, type, status, headerUrl, createTime, muteUntil, banUntil, policyVersion, 0L);
    }
```

Update `UserCredentialResult`:

```java
public record UserCredentialResult(
        UUID userId,
        String username,
        int status,
        int type,
        String headerUrl,
        long securityVersion
) {
}
```

Update `UserCredentialView`:

```java
public record UserCredentialView(
        UUID userId,
        String username,
        int status,
        int type,
        String headerUrl,
        long securityVersion
) {
}
```

Update `UserDataObject` with field/getter/setter:

```java
    private long securityVersion;

    public long getSecurityVersion() {
        return securityVersion;
    }

    public void setSecurityVersion(long securityVersion) {
        this.securityVersion = securityVersion;
    }
```

- [ ] **Step 6: Add repository contract methods for security version**

Update `UserRepository`:

```java
    void updateRole(UUID userId, int type, long securityVersion);

    void updateStatus(UUID userId, int status, long securityVersion);

    void updatePassword(UUID userId, String encodedPassword, long securityVersion);

    long nextUserSecurityVersion(UUID userId);

    long currentUserSecurityVersion();
```

Keep existing methods as default compatibility wrappers:

```java
    default void updateRole(UUID userId, int type) {
        updateRole(userId, type, nextUserSecurityVersion(userId));
    }

    default void updateStatus(UUID userId, int status) {
        updateStatus(userId, status, nextUserSecurityVersion(userId));
    }

    default void updatePassword(UUID userId, String encodedPassword) {
        updatePassword(userId, encodedPassword, nextUserSecurityVersion(userId));
    }
```

- [ ] **Step 7: Update MyBatis mapper and XML**

Update `UserMapper`:

```java
    int updateStatus(@Param("id") UUID id, @Param("status") int status, @Param("securityVersion") long securityVersion);

    int updatePassword(@Param("id") UUID id, @Param("password") String password, @Param("securityVersion") long securityVersion);

    int updateType(@Param("id") UUID id, @Param("type") int type, @Param("securityVersion") long securityVersion);

    int upsertSecurityVersionCounter(@Param("id") int id);

    long selectSecurityVersionCounterForUpdate(@Param("id") int id);

    int updateSecurityVersionCounter(@Param("id") int id, @Param("version") long version);

    long selectSecurityVersionCounter(@Param("id") int id);
```

Update `user_mapper.xml`:

```xml
<sql id="insertFields">
    id, username, password, salt, email, type, status, header_url, create_time, policy_version, security_version
</sql>

<sql id="selectFields">
    id, username, password, salt, email, type, status, header_url, create_time, mute_until, ban_until, policy_version, security_version
</sql>
```

Update insert values:

```xml
values(#{id, jdbcType=BINARY}, #{username}, #{password}, #{salt}, #{email}, #{type}, #{status}, #{headerUrl},
#{createTime}, #{policyVersion}, #{securityVersion})
```

Update mutations:

```xml
<update id="updateStatus">
    update user
    set status = #{status},
        security_version = #{securityVersion}
    where id = #{id, jdbcType=BINARY}
</update>

<update id="updatePassword">
    update user
    set password = #{password},
        security_version = #{securityVersion}
    where id = #{id, jdbcType=BINARY}
</update>

<update id="updateType">
    update user
    set type = #{type},
        security_version = #{securityVersion}
    where id = #{id, jdbcType=BINARY}
</update>
```

Add counter queries:

```xml
<insert id="upsertSecurityVersionCounter">
    insert into user_security_version_counter(id, current_version)
    values(#{id}, 0)
    on duplicate key update current_version = current_version
</insert>

<select id="selectSecurityVersionCounterForUpdate" resultType="long">
    select current_version
    from user_security_version_counter
    where id = #{id}
    for update
</select>

<update id="updateSecurityVersionCounter">
    update user_security_version_counter
    set current_version = #{version}
    where id = #{id}
</update>

<select id="selectSecurityVersionCounter" resultType="long">
    select coalesce(max(current_version), 0)
    from user_security_version_counter
    where id = #{id}
</select>
```

- [ ] **Step 8: Update `MyBatisUserRepository` mapping**

Add constant:

```java
private static final int USER_SECURITY_VERSION_COUNTER_ID = 1;
```

Update mutations:

```java
@Override
public void updateRole(UUID userId, int type, long securityVersion) {
    int updated = userMapper.updateType(userId, type, securityVersion);
    if (updated <= 0) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户角色失败");
    }
}

@Override
public void updateStatus(UUID userId, int status, long securityVersion) {
    int updated = userMapper.updateStatus(userId, status, securityVersion);
    if (updated <= 0) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户状态失败");
    }
}

@Override
public void updatePassword(UUID userId, String encodedPassword, long securityVersion) {
    int updated = userMapper.updatePassword(userId, encodedPassword, securityVersion);
    if (updated <= 0) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新密码失败");
    }
}
```

Add security version counter methods:

```java
@Override
public long nextUserSecurityVersion(UUID userId) {
    userMapper.upsertSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID);
    long current = userMapper.selectSecurityVersionCounterForUpdate(USER_SECURITY_VERSION_COUNTER_ID);
    long next = Math.max(current + 1L, legacyCompatibleVersionFloor());
    userMapper.updateSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID, next);
    return next;
}

@Override
public long currentUserSecurityVersion() {
    userMapper.upsertSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID);
    return userMapper.selectSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID);
}
```

Update `toAccount` and `toDataObject`:

```java
row.getPolicyVersion(),
row.getSecurityVersion()
```

```java
row.setSecurityVersion(user.securityVersion());
```

- [ ] **Step 9: Update SQL schemas**

In `backend/community-app/src/test/resources/schema.sql`, add to `user`:

```sql
  security_version bigint not null default 0,
  constraint ck_user_type check (type in (0, 1, 2)),
```

Create counter table after `user_policy_version_counter`:

```sql
create table if not exists user_security_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

merge into user_security_version_counter(id, current_version)
key(id)
values (1, 0);
```

In `deploy/mysql/community/020_schema_identity.sql`, add `security_version` to table creation after `policy_version`:

```sql
  security_version bigint not null default 0,
```

Add idempotent column migration:

```sql
set @col_user_security_version := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'security_version'
);
set @sql := if(@col_user_security_version = 0, 'alter table user add column security_version bigint not null default 0 after policy_version', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
```

Add idempotent check constraint for MySQL 8:

```sql
set @ck_user_type_valid := (
  select count(*)
  from information_schema.check_constraints
  where constraint_schema = database()
    and constraint_name = 'ck_user_type'
);
set @sql := if(@ck_user_type_valid = 0, 'alter table user add constraint ck_user_type check (type in (0, 1, 2))', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
```

Seed security versions:

```sql
set @user_security_seed_version := cast(floor(unix_timestamp(current_timestamp(3)) * 1000) * 4096 as unsigned);
update user
set security_version = @user_security_seed_version
where security_version = 0;

create table if not exists user_security_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @user_security_current_version := greatest(
  @user_security_seed_version,
  (select coalesce(max(security_version), 0) from user)
);

insert into user_security_version_counter(id, current_version)
values (1, @user_security_current_version)
on duplicate key update current_version = greatest(current_version, values(current_version));
```

- [ ] **Step 10: Wire role validation and authority mapping**

In `UserRoleDomainService.requireValidCommand(...)`, call:

```java
UserRole.requireValid(type);
```

In `UserCredentialDomainService.authoritiesForType(...)`, use:

```java
return UserRole.requireValid(type).authorities();
```

- [ ] **Step 11: Update projections and tests for new constructors**

In `UserCredentialApplicationService.toCredentialResult(...)`:

```java
return new UserCredentialResult(
        user.id(),
        user.username(),
        user.status(),
        user.type(),
        user.headerUrl(),
        user.securityVersion()
);
```

In `UserCredentialApiAdapter.toCredentialView(...)`:

```java
return new UserCredentialView(
        result.userId(),
        result.username(),
        result.status(),
        result.type(),
        result.headerUrl(),
        result.securityVersion()
);
```

In `UserCredentialApiAdapter.toCredentialResult(...)`:

```java
return new UserCredentialResult(
        user.userId(),
        user.username(),
        user.status(),
        user.type(),
        user.headerUrl(),
        user.securityVersion()
);
```

Update all tests constructing `new UserCredentialView(...)` or `new UserCredentialResult(...)` to pass a final `0L` unless a specific version is asserted.

- [ ] **Step 12: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRoleDomainServiceTest,UserCredentialApplicationServiceTest,MyBatisUserRepositoryTest,UserCredentialApiAdapterTest' -DfailIfNoTests=false
```

Expected: all selected tests pass.

- [ ] **Step 13: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user \
  backend/community-app/src/main/resources/mapper/user_mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/user \
  backend/community-app/src/test/resources/schema.sql \
  deploy/mysql/community/020_schema_identity.sql
git commit -m "feat: add user auth security version"
```

---

### Task 2: Revoke Refresh Sessions On Role, Password, And Active Ban Changes

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java`

- [ ] **Step 1: Add failing role session revocation test**

Update `AdminUserApplicationServiceTest` to mock `RefreshTokenSessionRepository`.

Add field:

```java
@Mock
private RefreshTokenSessionRepository refreshTokenSessionRepository;
```

Update `service()`:

```java
private AdminUserApplicationService service() {
    return new AdminUserApplicationService(userRepository, refreshTokenSessionRepository, new UserRoleDomainService(), userAuditLogPort);
}
```

Add test:

```java
@Test
void updateRoleShouldIncrementSecurityVersionAndRevokeTargetRefreshSessions() {
    AdminUserApplicationService service = service();
    UpdateUserRoleCommand command = new UpdateUserRoleCommand(ACTOR_ID, TARGET_ID, 2, "delegate moderation", true);
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "target", "target@example.com", 1, 1, "h8", new Date())));
    when(userRepository.nextUserSecurityVersion(TARGET_ID)).thenReturn(123L);

    service.updateRole(command);

    InOrder inOrder = inOrder(userRepository, refreshTokenSessionRepository, userAuditLogPort);
    inOrder.verify(userRepository).findById(TARGET_ID);
    inOrder.verify(userRepository).nextUserSecurityVersion(TARGET_ID);
    inOrder.verify(userRepository).updateRole(TARGET_ID, 2, 123L);
    inOrder.verify(refreshTokenSessionRepository).revokeByUserId(TARGET_ID);
    inOrder.verify(userAuditLogPort).recordRoleUpdated(ACTOR_ID, TARGET_ID, 1, 2, "delegate moderation");
}
```

Ensure import:

```java
import com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository;
```

- [ ] **Step 2: Add failing password security version test**

In `UserCredentialApplicationServiceTest.resetPasswordAndRevokeRefreshSessionsShouldPersistPasswordAndRevokeUserSessions`, add:

```java
when(userRepository.nextUserSecurityVersion(userId)).thenReturn(456L);
```

Replace password update verify:

```java
verify(userRepository).updatePassword(eq(userId), passwordCaptor.capture(), eq(456L));
```

- [ ] **Step 3: Add failing active ban test**

In `UserModerationApplicationServiceTest`, add a `RefreshTokenSessionRepository` mock and update service constructor. Add:

```java
@Test
void applyModerationShouldIncrementSecurityVersionAndRevokeSessionsWhenBanBecomesActive() {
    UUID userId = uuid(7);
    UserModerationApplicationService service = service();
    UserAccount target = account(userId, null, null);
    when(userRepository.findById(userId)).thenReturn(Optional.of(target));
    when(userRepository.nextUserPolicyVersion(userId)).thenReturn(33L);
    when(userRepository.nextUserSecurityVersion(userId)).thenReturn(44L);

    UserModerationStatus result = service.applyModeration(new ApplyUserModerationCommand(userId, "ban", 3600));

    assertThat(result.banUntil()).isAfter(Instant.now());
    verify(userRepository).updateModerationUntil(eq(userId), isNull(), any(Instant.class), eq(33L), eq(44L));
    verify(refreshTokenSessionRepository).revokeByUserId(userId);
}
```

Add these imports to the test file:

```java
import com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
```

- [ ] **Step 4: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='AdminUserApplicationServiceTest,UserCredentialApplicationServiceTest,UserModerationApplicationServiceTest' -DfailIfNoTests=false
```

Expected: fails due constructor/contract changes not implemented.

- [ ] **Step 5: Update application services**

Update `AdminUserApplicationService` constructor and fields:

```java
private final RefreshTokenSessionRepository refreshTokenSessionRepository;

public AdminUserApplicationService(
        UserRepository userRepository,
        RefreshTokenSessionRepository refreshTokenSessionRepository,
        UserRoleDomainService userRoleDomainService,
        UserAuditLogPort userAuditLogPort
) {
    this.userRepository = userRepository;
    this.refreshTokenSessionRepository = refreshTokenSessionRepository;
    this.userRoleDomainService = userRoleDomainService;
    this.userAuditLogPort = userAuditLogPort;
}
```

Update role mutation:

```java
long securityVersion = userRepository.nextUserSecurityVersion(command.targetUserId());
userRepository.updateRole(command.targetUserId(), toType, securityVersion);
refreshTokenSessionRepository.revokeByUserId(command.targetUserId());
userAuditLogPort.recordRoleUpdated(command.actorUserId(), command.targetUserId(), fromType, toType, reason);
```

Update `UserCredentialApplicationService.updatePasswordOnly(...)`:

```java
long securityVersion = userRepository.nextUserSecurityVersion(userId);
userRepository.updatePassword(userId, passwordEncoder.encode(validatedPassword), securityVersion);
```

- [ ] **Step 6: Extend moderation repository contract**

Update `UserRepository`:

```java
void updateModerationUntil(UUID userId, Instant muteUntil, Instant banUntil, long policyVersion, long securityVersion);

default void updateModerationUntil(UUID userId, Instant muteUntil, Instant banUntil, long policyVersion) {
    updateModerationUntil(userId, muteUntil, banUntil, policyVersion, 0L);
}
```

Update `MyBatisUserRepository.updateModerationUntil(...)` to pass `securityVersion`:

```java
int updated = userMapper.updateModerationUntil(
        userId,
        muteUntil == null ? null : Date.from(muteUntil),
        banUntil == null ? null : Date.from(banUntil),
        policyVersion,
        securityVersion
);
```

Update `UserMapper`:

```java
int updateModerationUntil(UUID id, java.util.Date muteUntil, java.util.Date banUntil, long policyVersion, long securityVersion);
```

Update XML:

```xml
<update id="updateModerationUntil">
    update user
    set mute_until = #{muteUntil},
        ban_until = #{banUntil},
        policy_version = #{policyVersion},
        security_version = case
            when #{securityVersion} &gt; 0 then #{securityVersion}
            else security_version
        end
    where id = #{id, jdbcType=BINARY}
</update>
```

- [ ] **Step 7: Wire active ban revocation**

Update `UserModerationApplicationService` constructor:

```java
private final RefreshTokenSessionRepository refreshTokenSessionRepository;

public UserModerationApplicationService(
        UserRepository userRepository,
        RefreshTokenSessionRepository refreshTokenSessionRepository,
        UserModerationDomainService userModerationDomainService,
        UserPolicyEventPublisher userPolicyEventPublisher
) {
    this.userRepository = userRepository;
    this.refreshTokenSessionRepository = refreshTokenSessionRepository;
    this.userModerationDomainService = userModerationDomainService;
    this.userPolicyEventPublisher = userPolicyEventPublisher;
}
```

Add helper:

```java
private boolean isActiveBan(Instant banUntil, Instant now) {
    return banUntil != null && banUntil.isAfter(now);
}
```

Update `applyModeration(...)` around version assignment:

```java
Instant now = Instant.now();
UserModerationStatus current = toStatus(user);
UserModerationStatus next = userModerationDomainService.applyModeration(
        current,
        action,
        command.durationSeconds(),
        now
);
long version = userRepository.nextUserPolicyVersion(userId);
boolean securityRelevant = isActiveBan(current.banUntil(), now) != isActiveBan(next.banUntil(), now)
        || (isActiveBan(current.banUntil(), now) && isActiveBan(next.banUntil(), now) && !next.banUntil().equals(current.banUntil()));
long securityVersion = securityRelevant ? userRepository.nextUserSecurityVersion(userId) : 0L;
UserModerationStatus versionedNext = new UserModerationStatus(next.userId(), next.muteUntil(), next.banUntil(), version);
userRepository.updateModerationUntil(userId, versionedNext.muteUntil(), versionedNext.banUntil(), version, securityVersion);
if (securityRelevant && isActiveBan(versionedNext.banUntil(), now)) {
    refreshTokenSessionRepository.revokeByUserId(userId);
}
```

- [ ] **Step 8: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='AdminUserApplicationServiceTest,UserCredentialApplicationServiceTest,UserModerationApplicationServiceTest,MyBatisUserRepositoryTest' -DfailIfNoTests=false
```

Expected: all selected tests pass.

- [ ] **Step 9: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user \
  backend/community-app/src/main/resources/mapper/user_mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/user
git commit -m "feat: revoke sessions on credential policy changes"
```

---

### Task 3: Make Auth Login, Refresh, And JWT Respect Security Version And Ban State

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserAuthenticationResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserAuthenticationResultView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserCredentialApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/port/AuthTokenPort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/jwt/JwtTokenService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginTokenIssuer.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/jwt/JwtTokenServiceTest.java` if missing, create it under that path.

- [ ] **Step 1: Add failing banned-user auth tests**

In `UserCredentialApplicationServiceTest`, add:

```java
@Test
void authenticateShouldRejectActivelyBannedUser() {
    UserCredentialApplicationService service = service();
    UserAccount user = new UserAccount(
            uuid(7),
            "alice",
            new BCryptPasswordEncoder().encode("secret12"),
            "",
            "alice@example.com",
            0,
            1,
            "h7",
            Date.from(Instant.now()),
            null,
            Instant.now().plusSeconds(600),
            0L,
            99L
    );
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

    UserAuthenticationResult result = service.authenticate("alice", "secret12");

    assertThat(result.failure()).isEqualTo(UserAuthenticationResult.Failure.USER_DISABLED);
    assertThat(result.user().securityVersion()).isEqualTo(99L);
}
```

In `LoginApplicationServiceTest`, add refresh test:

```java
@Test
void refreshShouldRejectBannedUserAndRevokeRefreshFamily() {
    UUID userId = uuid(21);
    RefreshTokenRepository.StoredRefreshToken consumed =
            new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-ban", Instant.now().plusSeconds(600));
    UserCredentialView banned = new UserCredentialView(userId, "alice", 1, 0, "h1", 77L, false, false);
    when(refreshTokenService.consume("old-refresh")).thenReturn(consumed);
    when(userCredentialQueryApi.getByUserId(userId)).thenReturn(banned);

    Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

    assertThat(thrown).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
    verify(refreshTokenService).revokeFamily("family-ban");
    verify(refreshTokenService, never()).issueInFamily(any(UUID.class), anyString());
}
```

This uses the target `UserCredentialView` constructor with `securityVersion`, `loginAllowed`, and `refreshAllowed`.

- [ ] **Step 2: Add failing JWT claim test**

Create `JwtTokenServiceTest`:

```java
package com.nowcoder.community.auth.infrastructure.jwt;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void createAccessTokenShouldIncludeSecurityVersionClaim() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setIssuer("community-auth-test");
        properties.setAccessTokenTtlSeconds(900);
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        JwtDecoder decoder = JwtCodecs.jwtDecoder(properties);
        JwtTokenService service = new JwtTokenService(encoder, properties);

        String token = service.createAccessToken(
                UUID.fromString("00000000-0000-7000-8000-000000000007"),
                "alice",
                List.of("ROLE_USER"),
                123L
        );

        assertThat(decoder.decode(token).getClaim("security_version")).isEqualTo(123L);
    }
}
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserCredentialApplicationServiceTest,LoginApplicationServiceTest,JwtTokenServiceTest' -DfailIfNoTests=false
```

Expected: fails due missing constructor fields, methods, and JWT claim support.

- [ ] **Step 4: Extend auth-facing credential model**

Update `UserCredentialResult` and `UserCredentialView` to:

```java
public record UserCredentialView(
        UUID userId,
        String username,
        int status,
        int type,
        String headerUrl,
        long securityVersion,
        boolean loginAllowed,
        boolean refreshAllowed
) {
    public UserCredentialView(UUID userId, String username, int status, int type, String headerUrl, long securityVersion) {
        this(userId, username, status, type, headerUrl, securityVersion, status != 0, status != 0);
    }
}
```

Use the same field shape for `UserCredentialResult`.

- [ ] **Step 5: Update user credential decisions**

In `UserCredentialApplicationService`, add:

```java
private boolean activeBan(UserAccount user) {
    return user != null && user.banUntil() != null && user.banUntil().isAfter(java.time.Instant.now());
}
```

Update authentication check:

```java
if (user.status() == 0 || activeBan(user)) {
    return UserAuthenticationResult.userDisabled(toCredentialResult(user));
}
```

Update `toCredentialResult(...)`:

```java
boolean allowed = user.status() != 0 && !activeBan(user);
return new UserCredentialResult(
        user.id(),
        user.username(),
        user.status(),
        user.type(),
        user.headerUrl(),
        user.securityVersion(),
        allowed,
        allowed
);
```

Update `UserCredentialApiAdapter` conversions with all fields.

- [ ] **Step 6: Update token port and JWT service**

Update `AuthTokenPort`:

```java
String createAccessToken(UUID userId, String username, List<String> authorities, long securityVersion);
```

Update `JwtTokenService.createAccessToken(...)` signature and claims:

```java
.claim("security_version", securityVersion)
```

Update all call sites and tests to pass `user.securityVersion()`.

- [ ] **Step 7: Introduce `LoginTokenIssuer`**

Create `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginTokenIssuer.java`:

```java
package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginTokenIssuer {

    private final UserCredentialQueryApi userCredentialQueryApi;
    private final AuthTokenPort authTokenPort;
    private final RefreshTokenApplicationService refreshTokenService;

    public LoginTokenIssuer(
            UserCredentialQueryApi userCredentialQueryApi,
            AuthTokenPort authTokenPort,
            RefreshTokenApplicationService refreshTokenService
    ) {
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.authTokenPort = authTokenPort;
        this.refreshTokenService = refreshTokenService;
    }

    public LoginResult issueLoginResult(UserCredentialView user) {
        List<String> authorities = userCredentialQueryApi.authoritiesOf(user);
        String accessToken = authTokenPort.createAccessToken(
                user.userId(),
                user.username(),
                authorities,
                user.securityVersion()
        );
        RefreshTokenApplicationService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.userId());
        return new LoginResult(accessToken, refreshToken.cookie());
    }

    public String issueAccessToken(UserCredentialView user) {
        List<String> authorities = userCredentialQueryApi.authoritiesOf(user);
        return authTokenPort.createAccessToken(user.userId(), user.username(), authorities, user.securityVersion());
    }
}
```

- [ ] **Step 8: Update `LoginApplicationService`**

Constructor replaces `AuthTokenPort` dependency with `LoginTokenIssuer`.

Field:

```java
private final LoginTokenIssuer loginTokenIssuer;
```

In `login(...)`:

```java
LoginResult loginResult = loginTokenIssuer.issueLoginResult(user);
```

In `refresh(...)`, replace status check:

```java
if (credentialView == null || !credentialView.refreshAllowed()) {
    refreshTokenService.revokeFamily(consumed.familyId());
    throw new BusinessException(AuthErrorCode.USER_DISABLED);
}
String accessToken = loginTokenIssuer.issueAccessToken(credentialView);
```

Keep refresh token rotation after user validation.

- [ ] **Step 9: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserCredentialApplicationServiceTest,UserCredentialApiAdapterTest,LoginApplicationServiceTest,JwtTokenServiceTest' -DfailIfNoTests=false
```

Expected: all selected tests pass.

- [ ] **Step 10: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth \
  backend/community-app/src/main/java/com/nowcoder/community/user \
  backend/community-app/src/test/java/com/nowcoder/community/auth \
  backend/community-app/src/test/java/com/nowcoder/community/user
git commit -m "feat: enforce auth security version in tokens"
```

---

### Task 4: Add High-Risk Token Freshness Checks Through Auth Application

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/TokenFreshnessResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/web/TokenFreshnessFilter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/web/TokenFreshnessFilterTest.java`

- [ ] **Step 1: Add failing application tests**

Create `TokenFreshnessApplicationServiceTest`:

```java
package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenFreshnessApplicationServiceTest {

    private final UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
    private final TokenFreshnessApplicationService service = new TokenFreshnessApplicationService(userCredentialQueryApi);

    @Test
    void verifyShouldAcceptFreshActiveToken() {
        UUID userId = uuid(7);
        when(userCredentialQueryApi.getByUserId(userId))
                .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 123L, true, true));

        TokenFreshnessResult result = service.verify(userId, 123L);

        assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.ACCEPTED);
    }

    @Test
    void verifyShouldRejectStaleTokenVersion() {
        UUID userId = uuid(7);
        when(userCredentialQueryApi.getByUserId(userId))
                .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 124L, true, true));

        TokenFreshnessResult result = service.verify(userId, 123L);

        assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.STALE);
    }

    @Test
    void verifyShouldDenyDisabledOrBannedActor() {
        UUID userId = uuid(7);
        when(userCredentialQueryApi.getByUserId(userId))
                .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 123L, false, false));

        TokenFreshnessResult result = service.verify(userId, 123L);

        assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.DENIED);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

- [ ] **Step 2: Add failing filter tests**

Create `TokenFreshnessFilterTest` with Spring mock request/response:

```java
package com.nowcoder.community.auth.infrastructure.web;

import com.nowcoder.community.auth.application.TokenFreshnessApplicationService;
import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenFreshnessFilterTest {

    private final TokenFreshnessApplicationService tokenFreshnessApplicationService = mock(TokenFreshnessApplicationService.class);
    private final TokenFreshnessFilter filter = new TokenFreshnessFilter(tokenFreshnessApplicationService);

    @Test
    void shouldBypassNonHighRiskPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenFreshnessApplicationService, never()).verify(null, 0L);
    }

    @Test
    void shouldRejectStaleHighRiskToken() throws Exception {
        UUID userId = uuid(7);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("security_version", 9L)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(tokenFreshnessApplicationService.verify(userId, 9L))
                .thenReturn(TokenFreshnessResult.stale());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/admin/role");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
        SecurityContextHolder.clearContext();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='TokenFreshnessApplicationServiceTest,TokenFreshnessFilterTest' -DfailIfNoTests=false
```

Expected: fails because classes do not exist.

- [ ] **Step 4: Implement result and application service**

Create `TokenFreshnessResult`:

```java
package com.nowcoder.community.auth.application.result;

public record TokenFreshnessResult(Status status) {
    public enum Status {
        ACCEPTED,
        STALE,
        DENIED
    }

    public static TokenFreshnessResult accepted() {
        return new TokenFreshnessResult(Status.ACCEPTED);
    }

    public static TokenFreshnessResult stale() {
        return new TokenFreshnessResult(Status.STALE);
    }

    public static TokenFreshnessResult denied() {
        return new TokenFreshnessResult(Status.DENIED);
    }
}
```

Create `TokenFreshnessApplicationService`:

```java
package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TokenFreshnessApplicationService {

    private final UserCredentialQueryApi userCredentialQueryApi;

    public TokenFreshnessApplicationService(UserCredentialQueryApi userCredentialQueryApi) {
        this.userCredentialQueryApi = userCredentialQueryApi;
    }

    public TokenFreshnessResult verify(UUID userId, long tokenSecurityVersion) {
        if (userId == null || tokenSecurityVersion <= 0) {
            return TokenFreshnessResult.stale();
        }
        UserCredentialView credential = userCredentialQueryApi.getByUserId(userId);
        if (credential == null || !credential.loginAllowed()) {
            return TokenFreshnessResult.denied();
        }
        if (credential.securityVersion() != tokenSecurityVersion) {
            return TokenFreshnessResult.stale();
        }
        return TokenFreshnessResult.accepted();
    }
}
```

- [ ] **Step 5: Implement filter**

Create `TokenFreshnessFilter`:

```java
package com.nowcoder.community.auth.infrastructure.web;

import com.nowcoder.community.auth.application.TokenFreshnessApplicationService;
import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class TokenFreshnessFilter extends OncePerRequestFilter {

    private static final List<String> HIGH_RISK_PREFIXES = List.of(
            "/api/users/admin/",
            "/api/ops/",
            "/api/admin/market/",
            "/api/wallet/admin/"
    );

    private final TokenFreshnessApplicationService tokenFreshnessApplicationService;

    public TokenFreshnessFilter(TokenFreshnessApplicationService tokenFreshnessApplicationService) {
        this.tokenFreshnessApplicationService = tokenFreshnessApplicationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isHighRisk(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt = currentJwt();
        UUID userId = parseSubject(jwt);
        Number versionClaim = jwt == null ? null : jwt.getClaim("security_version");
        long version = versionClaim == null ? 0L : versionClaim.longValue();
        TokenFreshnessResult result = tokenFreshnessApplicationService.verify(userId, version);
        if (result.status() == TokenFreshnessResult.Status.ACCEPTED) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(result.status() == TokenFreshnessResult.Status.STALE ? 401 : 403);
    }

    private boolean isHighRisk(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return HIGH_RISK_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }

    private UUID parseSubject(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
```

- [ ] **Step 6: Register filter after JWT authentication**

In `CommunitySecurityConfig`, add parameter:

```java
TokenFreshnessFilter tokenFreshnessFilter,
```

Add to chain before `.build()`:

```java
.addFilterAfter(tokenFreshnessFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
```

- [ ] **Step 7: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='TokenFreshnessApplicationServiceTest,TokenFreshnessFilterTest' -DfailIfNoTests=false
```

Expected: token freshness tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application \
  backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/web \
  backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth
git commit -m "feat: enforce token freshness on high-risk paths"
```

---

### Task 5: Fix Password Reset Email Rate Limit Ordering And DTO Bounds

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetRequestRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetConfirmRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

- [ ] **Step 1: Replace failing password reset quota test**

In `PasswordResetApplicationServiceTest`, replace `requestResetShouldRejectTooManyRequestsBeforeLookingUpUser` with:

```java
@Test
void requestResetShouldNotConsumeEmailQuotaForUnknownEmail() {
    properties.setRequestWindowSeconds(300);
    properties.setMaxRequestsPerEmail(1);
    properties.setMaxRequestsPerIp(20);
    when(captchaService.verify("cid", "1234")).thenReturn(true);
    when(resetRequestRateLimitRepository.increment("auth:pwdreset:req:ip:203.0.113.10", 300)).thenReturn(1);
    when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(null);

    PasswordResetRequestResult result = service.requestReset(new RequestPasswordResetCommand(
            " alice@example.com ",
            "cid",
            "1234",
            "203.0.113.10"
    ));

    assertThat(result.issued()).isTrue();
    verify(resetRequestRateLimitRepository).increment("auth:pwdreset:req:ip:203.0.113.10", 300);
    verify(resetRequestRateLimitRepository, never()).increment("auth:pwdreset:req:email:alice@example.com", 300);
    verify(tokenStore, never()).store(anyString(), any(UUID.class), any(Duration.class));
}
```

Add new existing-user quota test:

```java
@Test
void requestResetShouldConsumeEmailQuotaOnlyForKnownUsableUser() {
    UUID userId = uuid(7);
    UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null, 1L, true, true);
    properties.setRequestWindowSeconds(300);
    properties.setMaxRequestsPerEmail(1);
    properties.setMaxRequestsPerIp(20);
    when(captchaService.verify("cid", "1234")).thenReturn(true);
    when(resetRequestRateLimitRepository.increment("auth:pwdreset:req:ip:203.0.113.10", 300)).thenReturn(1);
    when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(user);
    when(resetRequestRateLimitRepository.increment("auth:pwdreset:req:email:alice@example.com", 300)).thenReturn(2);

    assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand(
            " alice@example.com ",
            "cid",
            "1234",
            "203.0.113.10"
    )))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

    verify(tokenStore, never()).store(anyString(), any(UUID.class), any(Duration.class));
    verify(mailService, never()).sendPasswordResetMail(anyString(), anyString());
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='PasswordResetApplicationServiceTest' -DfailIfNoTests=false
```

Expected: first new test fails because email quota is currently consumed before user lookup.

- [ ] **Step 3: Split IP and email rate-limit methods**

In `PasswordResetApplicationService`, replace `enforceRequestRateLimit(...)` with:

```java
private void enforceIpRequestRateLimit(String clientIp) {
    if (resetRequestRateLimitRepository == null) {
        return;
    }
    int maxRequestsPerIp = properties.getMaxRequestsPerIp();
    String ip = clientIp == null ? "" : clientIp.trim();
    if (maxRequestsPerIp > 0 && StringUtils.hasText(ip)) {
        int windowSeconds = Math.max(1, properties.getRequestWindowSeconds());
        String ipKey = RATE_LIMIT_IP_KEY_PREFIX + ip;
        int ipCount = resetRequestRateLimitRepository.increment(ipKey, windowSeconds);
        if (ipCount > maxRequestsPerIp) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }
}

private void enforceEmailRequestRateLimit(String normalizedEmail) {
    if (resetRequestRateLimitRepository == null) {
        return;
    }
    int maxRequestsPerEmail = properties.getMaxRequestsPerEmail();
    if (maxRequestsPerEmail > 0 && StringUtils.hasText(normalizedEmail)) {
        int windowSeconds = Math.max(1, properties.getRequestWindowSeconds());
        String emailKey = RATE_LIMIT_EMAIL_KEY_PREFIX + normalizedEmail.toLowerCase(Locale.ROOT);
        int emailCount = resetRequestRateLimitRepository.increment(emailKey, windowSeconds);
        if (emailCount > maxRequestsPerEmail) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }
}
```

Update request flow:

```java
String normalizedEmail = email.trim();
enforceIpRequestRateLimit(clientIp);
UserCredentialView user = userCredentialQueryApi.findByEmailOrNull(normalizedEmail);
if (user == null || user.userId() == null || !user.loginAllowed()) {
    SecurityEventLogger.info(log, "password_reset_request", "skipped",
            "community.reason_code", "hidden_noop",
            "masked.email", maskEmail(normalizedEmail));
    return new PasswordResetRequestResult(true, "");
}
enforceEmailRequestRateLimit(normalizedEmail);
```

- [ ] **Step 4: Add DTO bounds**

In `ValidationLimits`, ensure constants exist:

```java
public static final int TOKEN_MAX = 256;
public static final int CAPTCHA_ID_MAX = 64;
public static final int CAPTCHA_CODE_MAX = 16;
```

In `PasswordResetRequestRequest`:

```java
@Size(max = ValidationLimits.CAPTCHA_ID_MAX)
private String captchaId;

@Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
private String captchaCode;
```

In `PasswordResetConfirmRequest`:

```java
@NotBlank
@Size(max = ValidationLimits.TOKEN_MAX)
private String resetToken;

@Size(max = ValidationLimits.CAPTCHA_ID_MAX)
private String captchaId;

@Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
private String captchaCode;
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='PasswordResetApplicationServiceTest,AuthControllerUnitTest' -DfailIfNoTests=false
```

Expected: selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetRequestRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetConfirmRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth
git commit -m "fix: avoid password reset email quota denial"
```

---

### Task 6: Make Registration Code Verification Two-Phase

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationCodeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepositoryTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`

- [ ] **Step 1: Add failing repository pending-state test**

In `RedisRegistrationCodeRepositoryTest`, add:

```java
@Test
void verifyForConsumptionShouldMarkPendingAndBlockSecondVerifier() {
    UUID userId = uuid(7);
    repository.issue(userId, "123456", Duration.ofMinutes(5), Duration.ZERO);

    RegistrationCodeRepository.VerifyResult first = repository.verifyForConsumption(userId, "123456", Duration.ofSeconds(60));
    RegistrationCodeRepository.VerifyResult second = repository.verifyForConsumption(userId, "123456", Duration.ofSeconds(60));

    assertThat(first).isEqualTo(RegistrationCodeRepository.VerifyResult.PENDING);
    assertThat(second).isEqualTo(RegistrationCodeRepository.VerifyResult.PENDING_CONFLICT);
}

@Test
void restorePendingShouldAllowRetryWithSameCode() {
    UUID userId = uuid(8);
    repository.issue(userId, "123456", Duration.ofMinutes(5), Duration.ZERO);
    assertThat(repository.verifyForConsumption(userId, "123456", Duration.ofSeconds(60)))
            .isEqualTo(RegistrationCodeRepository.VerifyResult.PENDING);

    repository.restorePending(userId);

    assertThat(repository.verifyForConsumption(userId, "123456", Duration.ofSeconds(60)))
            .isEqualTo(RegistrationCodeRepository.VerifyResult.PENDING);
}
```

- [ ] **Step 2: Add failing application restore test**

In `RegistrationVerificationApplicationServiceTest`, add a test where `createVerifiedRegistrationUser(...)` throws a duplicate/invalid exception after code pending:

```java
@Test
void verifyAndLoginShouldRestorePendingCodeWhenUserCreationFailsBeforeActivation() {
    UUID userId = uuid(7);
    PreparedRegistrationDraft draft = draft(userId);
    when(registrationDraftRepository.find("reg-token")).thenReturn(Optional.of(draft));
    when(registrationCodeStore.verifyForConsumption(draft.userId(), "123456", Duration.ofSeconds(60)))
            .thenReturn(RegistrationCodeRepository.VerifyResult.PENDING);
    RuntimeException createFailure = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "用户名或邮箱已存在");
    when(userRegistrationActionApi.createVerifiedRegistrationUser(any()))
            .thenThrow(createFailure);

    assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("reg-token", "123456")))
            .isSameAs(createFailure);

    verify(registrationCodeStore).restorePending(draft.userId());
    verify(registrationDraftRepository, never()).delete("reg-token");
}
```

Use the existing `draft(UUID userId)` and `uuid(long suffix)` helpers already defined in the file.

- [ ] **Step 3: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='RedisRegistrationCodeRepositoryTest,RegistrationVerificationApplicationServiceTest' -DfailIfNoTests=false
```

Expected: fails because new repository methods and enum values do not exist.

- [ ] **Step 4: Extend repository contract**

Update `RegistrationCodeRepository`:

```java
VerifyResult verifyForConsumption(UUID userId, String code, Duration pendingTtl);

void consumePending(UUID userId);

void restorePending(UUID userId);
```

Add enum values:

```java
PENDING,
PENDING_CONFLICT
```

Keep `verifyAndConsume(...)` for existing callers by implementing it in Redis as immediate consume or defaulting through two-phase with a short pending TTL and `consumePending(...)`.

- [ ] **Step 5: Update Redis payload format**

Use five fields:

```text
code|expiresAtMs|failures|issuedAtMs|state
```

For backward compatibility, parser treats four fields as `ACTIVE`.

Add states in script strings:

```text
ACTIVE
PENDING
```

Update issue script to write:

```lua
local payload = ARGV[1] .. '|' .. tostring(nowMs + ttlMs) .. '|0|' .. tostring(nowMs) .. '|ACTIVE'
```

Add `VERIFY_PENDING_SCRIPT`:

```lua
local raw = redis.call('GET', KEYS[1])
if not raw or raw == '' then return 'NOT_FOUND' end
local storedCode, expiresAtMs, failures, issuedAtMs, state = string.match(raw, '([^|]*)|([^|]*)|([^|]*)|([^|]*)|?([^|]*)')
if not storedCode or not expiresAtMs or not failures or not issuedAtMs then redis.call('DEL', KEYS[1]); return 'NOT_FOUND' end
if not state or state == '' then state = 'ACTIVE' end
local expires = tonumber(expiresAtMs)
local failureCount = tonumber(failures)
local nowMs = tonumber(ARGV[2])
local maxFailures = tonumber(ARGV[3])
local pendingTtlMs = tonumber(ARGV[4])
if not expires or not failureCount or not nowMs or not maxFailures or not pendingTtlMs then redis.call('DEL', KEYS[1]); return 'NOT_FOUND' end
if expires < nowMs then redis.call('DEL', KEYS[1]); return 'EXPIRED' end
if state == 'PENDING' then return 'PENDING_CONFLICT' end
if storedCode == ARGV[1] then
  local ttl = redis.call('PTTL', KEYS[1])
  local nextTtl = ttl
  if pendingTtlMs > 0 and (not ttl or ttl < 0 or ttl > pendingTtlMs) then nextTtl = pendingTtlMs end
  local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. failures .. '|' .. issuedAtMs .. '|PENDING'
  if nextTtl and nextTtl > 0 then redis.call('SET', KEYS[1], nextRaw, 'PX', nextTtl) else redis.call('SET', KEYS[1], nextRaw) end
  return 'PENDING'
end
local nextFailures = failureCount + 1
if nextFailures >= maxFailures then redis.call('DEL', KEYS[1]); return 'TOO_MANY_ATTEMPTS' end
local ttl = redis.call('PTTL', KEYS[1])
local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. tostring(nextFailures) .. '|' .. issuedAtMs .. '|ACTIVE'
if ttl and ttl > 0 then redis.call('SET', KEYS[1], nextRaw, 'PX', ttl) else redis.call('SET', KEYS[1], nextRaw) end
return 'MISMATCH'
```

Add `RESTORE_PENDING_SCRIPT`:

```lua
local raw = redis.call('GET', KEYS[1])
if not raw or raw == '' then return 0 end
local storedCode, expiresAtMs, failures, issuedAtMs, state = string.match(raw, '([^|]*)|([^|]*)|([^|]*)|([^|]*)|?([^|]*)')
if not storedCode or not expiresAtMs or not failures or not issuedAtMs then redis.call('DEL', KEYS[1]); return 0 end
if state ~= 'PENDING' then return 0 end
local ttl = redis.call('PTTL', KEYS[1])
local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. failures .. '|' .. issuedAtMs .. '|ACTIVE'
if ttl and ttl > 0 then redis.call('SET', KEYS[1], nextRaw, 'PX', ttl) else redis.call('SET', KEYS[1], nextRaw) end
return 1
```

`consumePending(UUID)` can `DEL` the key. This is acceptable after the user row has been created.

- [ ] **Step 6: Update application flow**

In `RegistrationVerificationApplicationService.verifyAndLogin(...)`, replace `verifyAndConsume(...)` with:

```java
Duration pendingTtl = Duration.ofSeconds(60);
RegistrationCodeRepository.VerifyResult result = registrationCodeStore.verifyForConsumption(draft.userId(), code.trim(), pendingTtl);
if (result == RegistrationCodeRepository.VerifyResult.PENDING) {
    boolean activated = false;
    try {
        UserCredentialView activatedUser = userRegistrationActionApi.createVerifiedRegistrationUser(...);
        activated = true;
        LoginResult loginResult = loginTokenIssuer.issueLoginResult(activatedUser);
        SecurityEventLogger.info(log, "registration_verify", "success", "user.id", activatedUser.userId(), "username", activatedUser.username());
        return loginResult;
    } catch (RuntimeException ex) {
        if (!activated) {
            registrationCodeStore.restorePending(draft.userId());
        }
        if (activated) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED, ex);
        }
        throw ex;
    } finally {
        if (activated) {
            registrationCodeStore.consumePending(draft.userId());
            deleteDraftQuietly(registrationToken);
        }
    }
}
if (result == RegistrationCodeRepository.VerifyResult.PENDING_CONFLICT) {
    throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_INVALID);
}
```

Replace `authService.issueLoginResult(...)` with `LoginTokenIssuer.issueLoginResult(...)` from Task 3.

- [ ] **Step 7: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='RedisRegistrationCodeRepositoryTest,RegistrationVerificationApplicationServiceTest' -DfailIfNoTests=false
```

Expected: selected tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationCodeRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth
git commit -m "fix: make registration verification two-phase"
```

---

### Task 7: Make Redis Refresh Consume Atomic And Move DB Refresh Repository To Infrastructure

**Files:**
- Move: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/DbRefreshTokenRepository.java`
- Move: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/DbRefreshTokenRepositoryTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepositoryTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepositoryTest.java`

- [ ] **Step 1: Move DB repository package with git**

Run:

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/auth/application/DbRefreshTokenRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepository.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/application/DbRefreshTokenRepositoryTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepositoryTest.java
```

Update package declaration in both files:

```java
package com.nowcoder.community.auth.infrastructure.persistence;
```

- [ ] **Step 2: Run package move tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='DbRefreshTokenRepositoryTest' -DfailIfNoTests=false
```

Expected: DB repository tests pass after imports/package references are fixed.

- [ ] **Step 3: Add failing Redis atomic consume tests**

In `RedisRefreshTokenRepositoryTest`, add:

```java
@Test
void consumeShouldWriteTombstoneAtomicallySoReuseCanRevokeFamily() {
    UUID userId = uuid(7);
    Instant expiresAt = Instant.now().plusSeconds(600);
    repository.store("refresh-1", userId, "family-1", expiresAt);

    RefreshTokenRepository.StoredRefreshToken consumed = repository.consume("refresh-1");

    assertThat(consumed).isNotNull();
    RefreshTokenRepository.RevokedRefreshToken revoked = repository.findRevoked("refresh-1");
    assertThat(revoked).isNotNull();
    assertThat(revoked.userId()).isEqualTo(userId);
    assertThat(revoked.familyId()).isEqualTo("family-1");
}

@Test
void consumeShouldReturnNullWhenFamilyAlreadyRevoked() {
    UUID userId = uuid(8);
    repository.store("refresh-2", userId, "family-2", Instant.now().plusSeconds(600));
    repository.revokeFamily("family-2");

    RefreshTokenRepository.StoredRefreshToken consumed = repository.consume("refresh-2");

    assertThat(consumed).isNull();
}

@Test
void repositoryShouldUseLuaScriptForConsume() throws Exception {
    assertThat(RedisRefreshTokenRepository.class.getDeclaredField("CONSUME_SCRIPT")).isNotNull();
}
```

- [ ] **Step 4: Run failing Redis test**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='RedisRefreshTokenRepositoryTest' -DfailIfNoTests=false
```

Expected: fails because `CONSUME_SCRIPT` does not exist yet.

- [ ] **Step 5: Add consume Lua script**

In `RedisRefreshTokenRepository`, add:

```java
private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>();
```

Initialize:

```java
CONSUME_SCRIPT.setResultType(String.class);
CONSUME_SCRIPT.setScriptText(
        "if redis.call('exists', KEYS[3]) == 1 then return '' end " +
        "local raw = redis.call('get', KEYS[1]) " +
        "if not raw or raw == '' then return '' end " +
        "redis.call('del', KEYS[1]) " +
        "redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2]) " +
        "redis.call('srem', KEYS[4], ARGV[3]) " +
        "return raw"
);
```

This script keys are:

1. active token key
2. revoked tombstone key
3. family revoked marker key
4. family active set key

- [ ] **Step 6: Update `consume(...)`**

Replace `getAndDelete` sequence with:

```java
String activeKey = KEY_PREFIX_TOKEN + token;
String tombstoneKey = KEY_PREFIX_TOKEN_REVOKED + token;
String json = redisTemplate.opsForValue().get(activeKey);
StoredRefreshToken found = readRecord(json);
if (found == null) {
    return null;
}
Instant now = Instant.now();
if (!found.expiresAt().isAfter(now)) {
    redisTemplate.delete(activeKey);
    return null;
}
String tombstoneJson;
try {
    tombstoneJson = jsonCodec.toJson(new Tombstone(found.userId(), found.familyId(), found.expiresAt(), now));
} catch (JsonCodecException e) {
    throw new IllegalStateException("refresh token tombstone 序列化失败", e);
}
long ttlSeconds = Math.max(1, found.expiresAt().getEpochSecond() - now.getEpochSecond());
String consumedJson = redisTemplate.execute(
        CONSUME_SCRIPT,
        List.of(activeKey, tombstoneKey, KEY_PREFIX_FAMILY_REVOKED + found.familyId(), KEY_PREFIX_FAMILY + found.familyId()),
        tombstoneJson,
        Long.toString(ttlSeconds),
        token
);
return readRecord(consumedJson);
```

The script returns empty string when family was revoked or token disappeared; `readRecord("")` returns null.

- [ ] **Step 7: Run focused tests and architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='RedisRefreshTokenRepositoryTest,DbRefreshTokenRepositoryTest' -DfailIfNoTests=false
mvn test -pl :community-app -am -Dtest='*ArchTest' -DfailIfNoTests=false
```

Expected: selected tests and architecture tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence \
  backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence
git commit -m "fix: consume redis refresh tokens atomically"
```

---

### Task 8: Add Captcha Issue Rate Limit And `/me` Subject Guard

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/IssueCaptchaCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/CaptchaApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

- [ ] **Step 1: Add failing captcha issue rate-limit test**

In `CaptchaApplicationServiceTest`, add a `LoginRateLimitRepository` mock named `issueLimiter` and construct the service with the new target constructor. Add:

```java
@Test
void issueShouldRejectWhenClientIpExceedsCaptchaIssueLimit() {
    LoginRateLimitRepository issueLimiter = mock(LoginRateLimitRepository.class);
    CaptchaApplicationService service = new CaptchaApplicationService(properties, captchaStore, issueLimiter, new CaptchaDomainService());
    when(issueLimiter.increment("auth:captcha:issue:ip:203.0.113.10", 60)).thenReturn(61);

    Throwable thrown = catchThrowable(() -> service.issue(new IssueCaptchaCommand("203.0.113.10")));

    assertThat(thrown).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
}
```

Add imports:

```java
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.common.exception.CommonErrorCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
```

- [ ] **Step 2: Add failing `/me` subject guard test**

In `AuthControllerUnitTest`, add a test that builds an Authentication with a JWT subject of `"not-a-uuid"` and calls `controller.me(authentication)`:

```java
@Test
void meShouldReturnAuthErrorForNonUuidSubject() {
    Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("not-a-uuid")
            .claim("username", "alice")
            .claim("authorities", List.of("ROLE_USER"))
            .build();
    Authentication authentication = new JwtAuthenticationToken(jwt);

    Throwable thrown = catchThrowable(() -> controller.me(authentication));

    assertThat(thrown).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.TOKEN_INVALID);
}
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='CaptchaApplicationServiceTest,AuthControllerUnitTest' -DfailIfNoTests=false
```

Expected: fails due constructor/command changes and `/me` raw UUID parsing.

- [ ] **Step 4: Update captcha command and service**

Change `IssueCaptchaCommand`:

```java
public record IssueCaptchaCommand(String clientIp) {
    public IssueCaptchaCommand() {
        this(null);
    }
}
```

Update `CaptchaApplicationService` constructor:

```java
private static final String ISSUE_RATE_LIMIT_KEY_PREFIX = "auth:captcha:issue:ip:";
private final LoginRateLimitRepository issueRateLimitRepository;
```

Add overloaded constructor if needed for tests:

```java
public CaptchaApplicationService(
        CaptchaProperties properties,
        CaptchaRepository captchaStore,
        LoginRateLimitRepository issueRateLimitRepository,
        CaptchaDomainService captchaDomainService
) {
    this.properties = properties;
    this.captchaStore = captchaStore;
    this.issueRateLimitRepository = issueRateLimitRepository;
    this.captchaDomainService = captchaDomainService;
}
```

At start of `issue(IssueCaptchaCommand command)`:

```java
enforceIssueRateLimit(command == null ? null : command.clientIp());
```

Add:

```java
private void enforceIssueRateLimit(String clientIp) {
    if (issueRateLimitRepository == null || !StringUtils.hasText(clientIp)) {
        return;
    }
    String ip = clientIp.trim();
    int count = issueRateLimitRepository.increment(ISSUE_RATE_LIMIT_KEY_PREFIX + ip, 60);
    if (count > 60) {
        throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "验证码获取过于频繁，请稍后再试");
    }
}
```

- [ ] **Step 5: Pass IP from controller**

In `AuthController.captcha(...)`, add `HttpServletRequest request` argument:

```java
public Result<CaptchaIssueResponse> captcha(HttpServletRequest request, HttpServletResponse response) {
    ClientIpResolver.ResolvedClientIp resolvedIp = clientIpResolver.resolve(request);
    CaptchaIssueResult result = captchaApplicationService.issue(new IssueCaptchaCommand(
            resolvedIp == null ? null : resolvedIp.ip()
    ));
    ...
}
```

- [ ] **Step 6: Guard `/me` subject**

Replace:

```java
me.setUserId(UUID.fromString(jwt.getSubject()));
```

with:

```java
try {
    me.setUserId(UUID.fromString(jwt.getSubject()));
} catch (RuntimeException ex) {
    throw new BusinessException(AuthErrorCode.TOKEN_INVALID);
}
```

- [ ] **Step 7: Run focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='CaptchaApplicationServiceTest,AuthControllerUnitTest' -DfailIfNoTests=false
```

Expected: selected tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/IssueCaptchaCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth
git commit -m "fix: bound captcha issue and auth subject parsing"
```

---

### Task 9: Refactor Captcha Helper Boundaries Without Changing Behavior

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaChallengeComponent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- Test: existing auth application tests.

- [ ] **Step 1: Create component**

Create `CaptchaChallengeComponent`:

```java
package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CaptchaChallengeComponent {

    private final CaptchaApplicationService captchaApplicationService;

    public CaptchaChallengeComponent(CaptchaApplicationService captchaApplicationService) {
        this.captchaApplicationService = captchaApplicationService;
    }

    public void requireValidCaptcha(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaApplicationService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }
    }

    public boolean verify(String captchaId, String captchaCode) {
        return captchaApplicationService.verify(captchaId, captchaCode);
    }
}
```

- [ ] **Step 2: Replace application helper calls**

In `RegistrationApplicationService`, `RegistrationVerificationApplicationService`, and `PasswordResetApplicationService`, inject `CaptchaChallengeComponent` instead of `CaptchaApplicationService` where the service only verifies a captcha.

Replace repeated blocks:

```java
if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
    throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
}
if (!captchaService.verify(captchaId, captchaCode)) {
    throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
}
```

with:

```java
captchaChallenge.requireValidCaptcha(captchaId, captchaCode);
```

In `LoginApplicationService`, keep special rate-limit failure recording semantics:

```java
boolean ok = captchaChallenge.verify(captchaId, captchaCode);
```

- [ ] **Step 3: Run auth application tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='LoginApplicationServiceTest,RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,PasswordResetApplicationServiceTest,CaptchaApplicationServiceTest' -DfailIfNoTests=false
```

Expected: selected tests pass.

- [ ] **Step 4: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application \
  backend/community-app/src/test/java/com/nowcoder/community/auth/application
git commit -m "refactor: isolate captcha challenge component"
```

---

### Task 10: Complete DTO Bounds For Registration Code Inputs

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterCodeResendRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterCodeVerifyRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

- [ ] **Step 1: Add failing DTO validation tests**

In `AuthControllerUnitTest`, add a validator field and tests:

```java
private final jakarta.validation.Validator validator =
        jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

@Test
void registerCodeVerifyRequestShouldRejectOversizedRegistrationToken() {
    RegisterCodeVerifyRequest request = new RegisterCodeVerifyRequest();
    request.setRegistrationToken("x".repeat(ValidationLimits.REGISTRATION_TOKEN_MAX + 1));
    request.setCode("123456");

    assertThat(validator.validate(request))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("registrationToken"));
}

@Test
void registerCodeResendRequestShouldRejectOversizedCaptchaFields() {
    RegisterCodeResendRequest request = new RegisterCodeResendRequest();
    request.setRegistrationToken("token");
    request.setCaptchaId("c".repeat(ValidationLimits.CAPTCHA_ID_MAX + 1));
    request.setCaptchaCode("9".repeat(ValidationLimits.CAPTCHA_CODE_MAX + 1));

    assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("captchaId", "captchaCode");
}
```

Add imports:

```java
import com.nowcoder.community.auth.controller.dto.RegisterCodeResendRequest;
import com.nowcoder.community.auth.controller.dto.RegisterCodeVerifyRequest;
import com.nowcoder.community.common.constants.ValidationLimits;
```

- [ ] **Step 2: Run failing DTO tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='AuthControllerUnitTest' -DfailIfNoTests=false
```

Expected: fails because `REGISTRATION_TOKEN_MAX` is not defined or DTO fields are not all bounded by the target constants.

- [ ] **Step 3: Add validation constants**

In `ValidationLimits`, ensure:

```java
public static final int REGISTRATION_TOKEN_MAX = 256;
public static final int REGISTRATION_CODE_MAX = 16;
```

- [ ] **Step 4: Add DTO annotations**

In `RegisterRequest`:

```java
@Size(max = ValidationLimits.CAPTCHA_ID_MAX)
private String captchaId;

@Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
private String captchaCode;
```

In `RegisterCodeResendRequest`:

```java
@NotBlank
@Size(max = ValidationLimits.REGISTRATION_TOKEN_MAX)
private String registrationToken;

@Size(max = ValidationLimits.CAPTCHA_ID_MAX)
private String captchaId;

@Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
private String captchaCode;
```

In `RegisterCodeVerifyRequest`:

```java
@NotBlank
@Size(max = ValidationLimits.REGISTRATION_TOKEN_MAX)
private String registrationToken;

@NotBlank
@Size(max = ValidationLimits.REGISTRATION_CODE_MAX)
private String code;
```

- [ ] **Step 5: Run controller tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='AuthControllerUnitTest' -DfailIfNoTests=false
```

Expected: test suite passes.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto \
  backend/community-app/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java
git commit -m "fix: bound auth registration request fields"
```

---

### Task 11: Update Documentation For Auth/User Security Semantics

**Files:**
- Modify: `docs/handbook/security.md`
- Modify: `docs/handbook/auth-login-session-flow.md`
- Modify: `docs/handbook/business-logic/auth.md`
- Modify: `docs/handbook/business-logic/user.md`
- Modify: `docs/handbook/data-and-storage.md`

- [ ] **Step 1: Update security model**

In `docs/handbook/security.md`, update JWT/refresh section to state:

```markdown
Access token 仍是短期无状态 JWT。角色、密码、账号状态或账号级封禁变化后，已签发 access token 最多继续有效到 `security.jwt.access-token-ttl-seconds`。高风险 admin/ops 写入口会校验 JWT 中的 `security_version`，版本落后时要求刷新或重新登录。

Refresh token 是强一致会话失效边界。密码重置、角色变化、账号禁用和账号级封禁会撤销该用户 refresh sessions；后续 refresh 失败并清 cookie。
```

Add ban/mute definition:

```markdown
`banUntil` 是账号级暂停，影响 login、refresh、头像和敏感写操作。`muteUntil` 是发言级限制，只影响发帖、评论、回复和 IM/内容侧发言能力。
```

- [ ] **Step 2: Update auth login flow**

In `docs/handbook/auth-login-session-flow.md`, add JWT claim row:

```markdown
| `security_version` | user owner 当前认证授权版本，用于高风险入口 freshness 校验 |
```

Update refresh flow:

```markdown
refresh 回源 user owner 后校验 `loginAllowed` / `refreshAllowed` 和当前 `securityVersion`。用户被禁用、账号级封禁或不存在时，auth 撤销 refresh family 并清 cookie。
```

- [ ] **Step 3: Update auth business logic**

In `docs/handbook/business-logic/auth.md`, update registration verification:

```markdown
注册验证码采用两阶段消费：验证码正确后先进入 pending，user 创建成功后才 consumed 并删除 draft；创建失败且用户未激活时恢复 pending code，避免并发唯一约束竞态吞掉验证码。
```

Update password reset:

```markdown
密码重置请求先执行 captcha 和 IP 限流，再查询 user owner。邮箱维度限流只对存在且可用的账号计数；未知或不可用邮箱仍返回统一受理响应。
```

- [ ] **Step 4: Update user business logic**

In `docs/handbook/business-logic/user.md`, add:

```markdown
`securityVersion` 是 user owner 的认证授权版本。角色、密码、账号状态和账号级封禁变化会递增该版本，并撤销 refresh sessions；mute 只影响发言能力，不影响登录 refresh。
```

- [ ] **Step 5: Update data storage**

In `docs/handbook/data-and-storage.md`, add `user.security_version` and `user_security_version_counter` to the MySQL identity table list near existing user policy version content.

- [ ] **Step 6: Verify docs have no stale contradiction**

Run:

```bash
rg -n "角色变化通常要等|邮箱不存在的请求也会计数|GET /api/auth/me 直接读取已验证 JWT claim|banUntil|muteUntil|security_version|securityVersion" docs/handbook
```

Expected: no remaining line says unknown password-reset emails count against email quota; lines mentioning JWT claim-only behavior also mention high-risk freshness validation.

- [ ] **Step 7: Commit**

```bash
git add docs/handbook/security.md \
  docs/handbook/auth-login-session-flow.md \
  docs/handbook/business-logic/auth.md \
  docs/handbook/business-logic/user.md \
  docs/handbook/data-and-storage.md
git commit -m "docs: document auth user security semantics"
```

---

### Task 12: Run Final Verification And Fix Architecture Guardrails

**Files:**
- Modify if needed: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/*.java`
- Verify all files changed by previous tasks.

- [ ] **Step 1: Run targeted auth/user suite**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*Auth*,*Login*,*Refresh*,*PasswordReset*,*Registration*,*Captcha*,*UserCredential*,*AdminUser*,*UserModeration*,*UserRole*,*MyBatisUser*,*RedisRefreshToken*,*RedisRegistrationCode*' -DfailIfNoTests=false
```

Expected: all selected tests pass.

- [ ] **Step 2: Run architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest' -DfailIfNoTests=false
```

Expected: all architecture tests pass. On any failure, stop implementation work, inspect the failing ArchUnit message, fix code by routing inbound adapters through same-domain application boundaries, then rerun this exact command until it exits 0.

- [ ] **Step 3: Run community-app test slice**

Run:

```bash
cd backend
mvn test -pl :community-app -am -DfailIfNoTests=false
```

Expected: Maven exits 0. When execution constraints prevent this full command from completing, record the exact stopping point and still provide the targeted suite plus ArchUnit command outputs in the final handoff.

- [ ] **Step 4: Inspect git diff**

Run:

```bash
git status --short
git diff --stat
git diff --check
```

Expected: no whitespace errors; only files in this plan are changed, except test fixtures touched to update constructors.

- [ ] **Step 5: Commit final guardrail fixes when Step 2 or Step 3 changed files**

Run this only when Step 2 or Step 3 produced code/test changes:

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch backend/community-app/src/main/java backend/community-app/src/test/java
git commit -m "test: align auth user security guardrails"
```

When Step 2 and Step 3 produced no changes, skip this step without creating an empty commit.

## Final Handoff Checklist

- [ ] `security_version` is present in test schema and MySQL init schema.
- [ ] `UserCredentialView` exposes enough information for auth to decide login/refresh without owning user rules.
- [ ] Role changes, password resets, status changes, and active bans increment auth security version.
- [ ] Role changes, password resets, and active bans revoke refresh sessions.
- [ ] JWT contains `security_version`.
- [ ] High-risk filters call auth application boundary, not user API directly.
- [ ] Password reset unknown emails do not consume email quota.
- [ ] Registration code verify uses pending/consume/restore.
- [ ] Redis refresh consume writes tombstone atomically.
- [ ] `DbRefreshTokenRepository` lives under auth infrastructure.
- [ ] DTO bounds cover captcha, reset token, registration token, and registration code fields.
- [ ] `/api/auth/me` handles non-UUID subject with auth error.
- [ ] Handbook docs match implementation.
- [ ] Targeted tests and ArchUnit tests pass with captured command output.
