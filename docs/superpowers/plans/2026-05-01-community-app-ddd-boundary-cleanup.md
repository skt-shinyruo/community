# Community App DDD Boundary Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement phase one of `2026-05-01-community-app-ddd-boundary-cleanup-design.md`: keep domain code free of Spring, keep application results free of HTTP transport types, and preserve auth/file HTTP behavior.

**Architecture:** Add executable ArchUnit guardrails first, then move HTTP cookie and file response expression to controller/web adapter boundaries. Application services continue to own use-case decisions and token/file lookup semantics, while domain services become plain Java objects.

**Tech Stack:** Java 17, Spring Boot 3.2, ArchUnit, JUnit 5, Mockito, Maven.

---

## File Structure

Modify architecture guardrails:

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
  - Add rules for domain Spring dependency, application transport dependency, and public ApplicationService return types.

Modify auth boundary:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshCookieSpec.java`
  - New application-neutral cookie specification record.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/LoginResult.java`
  - Replace `ResponseCookie` field with `RefreshCookieSpec`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshResult.java`
  - Replace `ResponseCookie` field with `RefreshCookieSpec`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java`
  - Build `RefreshCookieSpec` instead of `ResponseCookie`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
  - Remove `ResponseCookie` return surface.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java`
  - Return `RefreshCookieSpec` from clear-cookie path.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
  - Convert `RefreshCookieSpec` to `ResponseCookie` at HTTP boundary.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RefreshTokenApplicationServiceTest.java`

Modify user file boundary:

- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarFileResult.java`
  - Replace Spring `Resource` / `MediaType` with Java `InputStream`, content type string, and length.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java`
  - Convert infrastructure `StoredAvatar` into application-neutral `AvatarFileResult`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
  - Convert `AvatarFileResult` to Spring `InputStreamResource`, `MediaType`, and `ResponseEntity`.
- `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`

Modify domain services:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/service/*.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/*.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/*.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/domain/service/*.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/*.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/domain/service/*.java`

Modify docs after implementation:

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`

---

## Task 1: Add RED Architecture Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

- [ ] **Step 1: Add imports for custom ArchUnit return-type checks**

Add these imports near the top of `DddLayeringArchTest.java`:

```java
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;
```

Also add this static import:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
```

- [ ] **Step 2: Add the failing rules**

Insert these rules after `domain_must_not_depend_on_outer_layers`:

```java
    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_framework =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model..",
                            "..domain.service..",
                            "..domain.repository..",
                            "..domain.event.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("domain code must remain plain Java and must not depend on Spring");
```

Insert these rules after `application_must_not_depend_on_transport_or_infrastructure`:

```java
    @ArchTest
    static final ArchRule application_must_not_depend_on_web_transport_types =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.http..",
                            "org.springframework.core.io..",
                            "jakarta.servlet.."
                    )
                    .because("HTTP transport details belong in controllers or web adapters");

    @ArchTest
    static final ArchRule application_services_must_not_return_web_transport_types =
            classes()
                    .that().resideInAnyPackage("..application..")
                    .and().haveSimpleNameEndingWith("ApplicationService")
                    .should(notReturnWebTransportTypes());
```

Add this helper near the bottom of `DddLayeringArchTest`, before the closing brace:

```java
    private static ArchCondition<JavaClass> notReturnWebTransportTypes() {
        Set<String> forbiddenTypeNames = Set.of(
                "org.springframework.http.ResponseCookie",
                "org.springframework.http.ResponseEntity",
                "org.springframework.http.MediaType",
                "org.springframework.core.io.Resource",
                "jakarta.servlet.http.HttpServletRequest",
                "jakarta.servlet.http.HttpServletResponse"
        );
        return new ArchCondition<>("not return HTTP transport types from public application service methods") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    JavaClass returnType = method.getRawReturnType();
                    if (forbiddenTypeNames.contains(returnType.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                method.getFullName() + " returns " + returnType.getName()
                        ));
                    }
                }
            }
        };
    }
```

- [ ] **Step 3: Run architecture tests and verify RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: FAIL. The failure must mention current violations such as:

```text
auth.domain.service.* depends on org.springframework.stereotype.Service
auth.application.result.LoginResult depends on org.springframework.http.ResponseCookie
auth.application.RefreshTokenApplicationService depends on org.springframework.http.ResponseCookie
user.application.result.AvatarFileResult depends on org.springframework.core.io.Resource
```

- [ ] **Step 4: Commit the RED guardrail**

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java
git commit -m "test: expose ddd boundary leaks"
```

---

## Task 2: Move Auth Cookie HTTP Expression To Controller

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshCookieSpec.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/LoginResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RefreshTokenApplicationServiceTest.java`

- [ ] **Step 1: Update AuthControllerUnitTest to expect application-neutral cookie specs**

In `AuthControllerUnitTest`, remove:

```java
import org.springframework.http.ResponseCookie;
```

Add:

```java
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
```

Replace the cookie setup in `loginShouldSetRefreshCookieAndReturnAccessToken` with:

```java
        RefreshCookieSpec refreshCookie = issuedCookie("rt");

        when(authApplicationService.login(any(LoginCommand.class)))
                .thenReturn(new LoginResult("at", refreshCookie));
```

Replace the cookie setup in `refreshShouldSetRefreshCookieAndReturnAccessToken` with:

```java
        RefreshCookieSpec refreshCookie = issuedCookie("rt2");

        when(authApplicationService.refresh(any(RefreshCommand.class)))
                .thenReturn(new RefreshResult("at2", refreshCookie));
```

Replace the clear-cookie setup in both refresh failure tests and in `logoutShouldClearRefreshCookie` with:

```java
        RefreshCookieSpec clearCookie = clearedCookie();
```

Replace the register-code verification cookie setup with:

```java
        RefreshCookieSpec refreshCookie = issuedCookie("rt3");

        when(authApplicationService.verifyRegisterCode(any(VerifyRegisterCodeCommand.class)))
                .thenReturn(new LoginResult("at3", refreshCookie));
```

Add these helpers before the final closing brace:

```java
    private static RefreshCookieSpec issuedCookie(String value) {
        return new RefreshCookieSpec(
                "refresh_token",
                value,
                true,
                false,
                "/api/auth",
                "Lax",
                600
        );
    }

    private static RefreshCookieSpec clearedCookie() {
        return new RefreshCookieSpec(
                "refresh_token",
                "",
                true,
                false,
                "/api/auth",
                "Lax",
                0
        );
    }
```

- [ ] **Step 2: Update LoginApplicationServiceTest to assert cookie specs**

In `LoginApplicationServiceTest`, remove:

```java
import org.springframework.http.ResponseCookie;
```

Add:

```java
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
```

Replace the refresh token setup in `loginShouldResetRateLimitAfterSuccessfulAuthentication`:

```java
        RefreshCookieSpec cookie = issuedCookie("rt");
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")))).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("rt", cookie));
```

Replace:

```java
        assertThat(result.refreshCookie()).isEqualTo(cookie);
```

with:

```java
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        assertThat(result.refreshCookie().value()).isEqualTo("rt");
```

Replace the refresh token setup in `loginShouldRecordDauSupplementAfterSuccessfulAuthentication`:

```java
        when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("refresh-token", issuedCookie("refresh-token")));
```

Add this helper before the existing private helper methods:

```java
    private static RefreshCookieSpec issuedCookie(String value) {
        return new RefreshCookieSpec(
                "refresh_token",
                value,
                true,
                false,
                "/api/auth",
                "Lax",
                600
        );
    }
```

- [ ] **Step 3: Update RefreshTokenApplicationServiceTest expectations**

In `RefreshTokenApplicationServiceTest`, after `RefreshTokenApplicationService.IssuedRefreshToken issued = refreshTokenService.issue(USER_ID);`, add:

```java
        assertThat(issued.cookie().name()).isEqualTo("refresh_token");
        assertThat(issued.cookie().value()).isEqualTo(issued.refreshToken());
        assertThat(issued.cookie().path()).isEqualTo("/api/auth");
        assertThat(issued.cookie().maxAgeSeconds()).isEqualTo(600);
```

- [ ] **Step 4: Run auth tests and verify RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=AuthControllerUnitTest,LoginApplicationServiceTest,RefreshTokenApplicationServiceTest test
```

Expected: FAIL at compilation because `RefreshCookieSpec` does not exist and `LoginResult` / `RefreshResult` still take `ResponseCookie`.

- [ ] **Step 5: Add RefreshCookieSpec**

Create `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshCookieSpec.java`:

```java
package com.nowcoder.community.auth.application.result;

public record RefreshCookieSpec(
        String name,
        String value,
        boolean httpOnly,
        boolean secure,
        String path,
        String sameSite,
        long maxAgeSeconds
) {
    public RefreshCookieSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("cookie name must not be blank");
        }
        value = value == null ? "" : value;
        path = path == null || path.isBlank() ? "/" : path;
        sameSite = sameSite == null ? "" : sameSite;
        maxAgeSeconds = Math.max(0, maxAgeSeconds);
    }
}
```

- [ ] **Step 6: Change auth result records**

Replace `LoginResult.java` with:

```java
package com.nowcoder.community.auth.application.result;

public record LoginResult(String accessToken, RefreshCookieSpec refreshCookie) {
}
```

Replace `RefreshResult.java` with:

```java
package com.nowcoder.community.auth.application.result;

public record RefreshResult(String accessToken, RefreshCookieSpec refreshCookie) {
}
```

- [ ] **Step 7: Change RefreshTokenApplicationService to return cookie specs**

In `RefreshTokenApplicationService`, remove:

```java
import org.springframework.http.ResponseCookie;
```

Add:

```java
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
```

Replace `buildCookie` and `clearCookie` with:

```java
    public RefreshCookieSpec buildCookie(String refreshToken) {
        return new RefreshCookieSpec(
                jwtProperties.getRefreshCookieName(),
                refreshToken,
                true,
                jwtProperties.isRefreshCookieSecure(),
                jwtProperties.getRefreshCookiePath(),
                jwtProperties.getRefreshCookieSameSite(),
                jwtProperties.getRefreshTokenTtlSeconds()
        );
    }

    public RefreshCookieSpec clearCookie() {
        return new RefreshCookieSpec(
                jwtProperties.getRefreshCookieName(),
                "",
                true,
                jwtProperties.isRefreshCookieSecure(),
                jwtProperties.getRefreshCookiePath(),
                jwtProperties.getRefreshCookieSameSite(),
                0
        );
    }
```

Replace the nested record with:

```java
    public record IssuedRefreshToken(String refreshToken, RefreshCookieSpec cookie) {
    }
```

- [ ] **Step 8: Remove ResponseCookie from auth application services**

In `LoginApplicationService`, remove:

```java
import org.springframework.http.ResponseCookie;
```

Add:

```java
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
```

Change `clearRefreshCookie` to:

```java
    public RefreshCookieSpec clearRefreshCookie() {
        return refreshTokenService.clearCookie();
    }
```

In `AuthApplicationService`, remove:

```java
import org.springframework.http.ResponseCookie;
```

Add:

```java
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
```

Change `clearRefreshCookie` to:

```java
    public RefreshCookieSpec clearRefreshCookie() {
        return loginApplicationService.clearRefreshCookie();
    }
```

- [ ] **Step 9: Convert RefreshCookieSpec to ResponseCookie in AuthController**

In `AuthController`, add imports:

```java
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
```

Replace every direct call that adds `result.refreshCookie().toString()` with:

```java
        addRefreshCookie(response, result.refreshCookie());
```

Replace every direct call that adds `authApplicationService.clearRefreshCookie().toString()` with:

```java
                addRefreshCookie(response, authApplicationService.clearRefreshCookie());
```

or in logout:

```java
        addRefreshCookie(response, authApplicationService.clearRefreshCookie());
```

Add these helpers near `readRefreshToken`:

```java
    private void addRefreshCookie(HttpServletResponse response, RefreshCookieSpec spec) {
        if (response == null || spec == null) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, toResponseCookie(spec).toString());
    }

    private ResponseCookie toResponseCookie(RefreshCookieSpec spec) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(spec.name(), spec.value())
                .httpOnly(spec.httpOnly())
                .secure(spec.secure())
                .path(spec.path())
                .maxAge(spec.maxAgeSeconds());
        if (StringUtils.hasText(spec.sameSite())) {
            builder.sameSite(spec.sameSite());
        }
        return builder.build();
    }
```

- [ ] **Step 10: Run auth tests and verify GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=AuthControllerUnitTest,LoginApplicationServiceTest,RefreshTokenApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 11: Run architecture tests for auth-related transport leaks**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: still FAIL because user `AvatarFileResult` and domain Spring imports remain. It must no longer report `ResponseCookie` violations under `auth.application`.

- [ ] **Step 12: Commit auth boundary cleanup**

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshCookieSpec.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/LoginResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/application/RefreshTokenApplicationServiceTest.java
git commit -m "refactor: keep auth cookies at web boundary"
```

---

## Task 3: Move Avatar File HTTP Types To Controller

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarFileResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`

- [ ] **Step 1: Update UserFileApplicationServiceTest to use Java InputStream**

In `UserFileApplicationServiceTest`, remove:

```java
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
```

Add:

```java
import java.io.ByteArrayInputStream;
```

Replace the `AvatarFileResult file` construction with:

```java
        AvatarFileResult file = new AvatarFileResult(
                new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)),
                "text/plain",
                2
        );
```

- [ ] **Step 2: Strengthen FilesControllerStorageRoutingTest response assertions**

In `FilesControllerStorageRoutingTest`, keep the infrastructure stub returning `StoredAvatar` with `ByteArrayResource` and `MediaType.TEXT_PLAIN`. After the existing body assertion, add:

```java
        assertThat(resp.getBody().getInputStream().readAllBytes()).isEqualTo("ok".getBytes(StandardCharsets.UTF_8));
        assertThat(resp.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
```

- [ ] **Step 3: Run user file tests and verify RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UserFileApplicationServiceTest,FilesControllerStorageRoutingTest test
```

Expected: FAIL at compilation because `AvatarFileResult` still expects Spring `Resource` and `MediaType`.

- [ ] **Step 4: Replace AvatarFileResult**

Replace `AvatarFileResult.java` with:

```java
package com.nowcoder.community.user.application.result;

import java.io.InputStream;
import java.util.Objects;

public record AvatarFileResult(InputStream content, String contentType, long contentLength) {

    public AvatarFileResult {
        Objects.requireNonNull(content, "content");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        contentLength = Math.max(-1, contentLength);
    }
}
```

- [ ] **Step 5: Convert StoredAvatar in UserAvatarStorageAdapter**

In `UserAvatarStorageAdapter`, add:

```java
import java.io.IOException;
```

Replace `loadAvatarOrNull` with:

```java
    @Override
    public AvatarFileResult loadAvatarOrNull(String fileKey) {
        StoredAvatar stored = avatarStorageRouter.currentProviderOrThrow().loadOrNull(fileKey);
        if (stored == null) {
            return null;
        }
        try {
            long contentLength = stored.resource().contentLength();
            return new AvatarFileResult(
                    stored.resource().getInputStream(),
                    stored.mediaType().toString(),
                    contentLength
            );
        } catch (IOException e) {
            throw new IllegalStateException("avatar file load failed: " + fileKey, e);
        }
    }
```

- [ ] **Step 6: Convert AvatarFileResult in FilesController**

In `FilesController`, add:

```java
import org.springframework.core.io.InputStreamResource;
```

Replace:

```java
        MediaType mediaType = stored.mediaType();
```

with:

```java
        MediaType mediaType = MediaType.parseMediaType(stored.contentType());
```

Replace the final return statement with:

```java
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers);
        if (stored.contentLength() >= 0) {
            builder.contentLength(stored.contentLength());
        }
        return builder.body(new InputStreamResource(stored.content()));
```

- [ ] **Step 7: Run user file tests and verify GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UserFileApplicationServiceTest,FilesControllerStorageRoutingTest test
```

Expected: PASS.

- [ ] **Step 8: Run architecture tests for remaining leaks**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: still FAIL only because domain services import Spring. It must no longer report `user.application.result.AvatarFileResult` Spring `Resource` or `MediaType` violations.

- [ ] **Step 9: Commit user file boundary cleanup**

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarFileResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java
git commit -m "refactor: keep avatar file transport at controller"
```

---

## Task 4: Remove Spring Dependencies From Domain Services

**Files:**
- Modify domain services reported by:
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/service/*.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/*.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/*.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/search/domain/service/*.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/*.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/analytics/domain/service/*.java`
- Test: domain service tests under `backend/community-app/src/test/java/com/nowcoder/community/*/domain/service`
- Test: affected application tests under `backend/community-app/src/test/java/com/nowcoder/community/*/application`

- [ ] **Step 1: List current domain Spring imports**

Run:

```bash
cd /home/feng/code/project/community
rg -n "^import org\\.springframework|@Service|StringUtils|DigestUtils" backend/community-app/src/main/java/com/nowcoder/community/*/domain/service
```

Expected: output includes the current 22 domain service files, including `CaptchaDomainService`, `UserCredentialDomainService`, `FollowDomainService`, `PostSearchDomainService`, and `CommentDomainService`.

- [ ] **Step 2: Remove stereotype imports and annotations**

For every file reported by Step 1:

Remove:

```java
import org.springframework.stereotype.Service;
```

Remove:

```java
@Service
```

No application constructor should change in this step. Existing application services that receive domain services as constructor dependencies will still compile once Spring bean registration is supplied. Step 5 below adds explicit configuration for shared domain services if Spring context tests require it.

- [ ] **Step 3: Replace Spring StringUtils usage with local Java helpers**

For domain services that use `StringUtils.hasText(...)`, replace calls with a private helper in the same class:

```java
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
```

Examples:

```java
if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(code)) {
```

becomes:

```java
if (!hasText(captchaId) || !hasText(code)) {
```

```java
String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "";
```

becomes:

```java
String normalizedReason = hasText(reason) ? reason.trim() : "";
```

After replacing calls, remove:

```java
import org.springframework.util.StringUtils;
```

- [ ] **Step 4: Replace UserCredentialDomainService DigestUtils**

In `UserCredentialDomainService`, remove:

```java
import org.springframework.util.DigestUtils;
```

Add:

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
```

Replace the helper methods at the bottom with:

```java
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
```

Replace `StringUtils.hasText(...)` in that file with `hasText(...)`.

- [ ] **Step 5: Run domain and application tests and verify GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest='*DomainServiceTest,*ApplicationServiceTest' test
```

Expected: PASS for the selected tests. If Spring context wiring fails because a domain service is no longer a component, add the configuration in Step 6.

- [ ] **Step 6: Add explicit domain service configuration only if Spring wiring fails**

If Step 5 or a focused Spring test reports a missing domain service bean, create `backend/community-app/src/main/java/com/nowcoder/community/app/config/DomainServiceConfig.java`:

```java
package com.nowcoder.community.app.config;

import com.nowcoder.community.analytics.domain.service.AnalyticsDomainService;
import com.nowcoder.community.analytics.domain.service.AnalyticsIngestDomainService;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.domain.service.CaptchaDomainService;
import com.nowcoder.community.auth.domain.service.LoginRateLimitDomainService;
import com.nowcoder.community.auth.domain.service.PasswordResetDomainService;
import com.nowcoder.community.auth.domain.service.RefreshTokenDomainService;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.content.domain.service.ModerationDecisionDomainService;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.search.domain.service.PostSearchDomainService;
import com.nowcoder.community.search.domain.service.SearchReindexDomainService;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import com.nowcoder.community.user.domain.service.UserModerationDomainService;
import com.nowcoder.community.user.domain.service.UserReadDomainService;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.nowcoder.community.user.domain.service.UserRoleDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    AnalyticsDomainService analyticsDomainService() {
        return new AnalyticsDomainService();
    }

    @Bean
    AnalyticsIngestDomainService analyticsIngestDomainService() {
        return new AnalyticsIngestDomainService();
    }

    @Bean
    AuthDomainService authDomainService() {
        return new AuthDomainService();
    }

    @Bean
    CaptchaDomainService captchaDomainService() {
        return new CaptchaDomainService();
    }

    @Bean
    LoginRateLimitDomainService loginRateLimitDomainService() {
        return new LoginRateLimitDomainService();
    }

    @Bean
    PasswordResetDomainService passwordResetDomainService() {
        return new PasswordResetDomainService();
    }

    @Bean
    RefreshTokenDomainService refreshTokenDomainService() {
        return new RefreshTokenDomainService();
    }

    @Bean
    RegistrationDomainService registrationDomainService() {
        return new RegistrationDomainService();
    }

    @Bean
    CommentDomainService commentDomainService() {
        return new CommentDomainService();
    }

    @Bean
    ModerationDecisionDomainService moderationDecisionDomainService() {
        return new ModerationDecisionDomainService();
    }

    @Bean
    PostModerationDomainService postModerationDomainService() {
        return new PostModerationDomainService();
    }

    @Bean
    PostPublishingDomainService postPublishingDomainService() {
        return new PostPublishingDomainService();
    }

    @Bean
    PostSearchDomainService postSearchDomainService() {
        return new PostSearchDomainService();
    }

    @Bean
    SearchReindexDomainService searchReindexDomainService() {
        return new SearchReindexDomainService();
    }

    @Bean
    BlockDomainService blockDomainService() {
        return new BlockDomainService();
    }

    @Bean
    FollowDomainService followDomainService() {
        return new FollowDomainService();
    }

    @Bean
    LikeDomainService likeDomainService() {
        return new LikeDomainService();
    }

    @Bean
    UserCredentialDomainService userCredentialDomainService() {
        return new UserCredentialDomainService();
    }

    @Bean
    UserModerationDomainService userModerationDomainService() {
        return new UserModerationDomainService();
    }

    @Bean
    UserReadDomainService userReadDomainService() {
        return new UserReadDomainService();
    }

    @Bean
    UserRegistrationDomainService userRegistrationDomainService() {
        return new UserRegistrationDomainService();
    }

    @Bean
    UserRoleDomainService userRoleDomainService() {
        return new UserRoleDomainService();
    }
}
```

If Step 5 passes without this configuration, do not add the file.

- [ ] **Step 7: Verify no domain Spring references remain**

Run:

```bash
cd /home/feng/code/project/community
rg -n "^import org\\.springframework|@Service|StringUtils|DigestUtils" backend/community-app/src/main/java/com/nowcoder/community/*/domain/service
```

Expected: no output.

- [ ] **Step 8: Run architecture tests and verify GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,DomainBoundaryArchTest test
```

Expected: PASS.

- [ ] **Step 9: Commit domain purity cleanup**

If `DomainServiceConfig.java` was not needed:

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/main/java/com/nowcoder/community/*/domain/service/*.java
git commit -m "refactor: keep domain services framework-free"
```

If `DomainServiceConfig.java` was needed:

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/main/java/com/nowcoder/community/*/domain/service/*.java \
        backend/community-app/src/main/java/com/nowcoder/community/app/config/DomainServiceConfig.java
git commit -m "refactor: keep domain services framework-free"
```

---

## Task 5: Update Documentation And Run Final Verification

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Verify: all files touched in Tasks 1-4.

- [ ] **Step 1: Update docs/ARCHITECTURE.md DDD rules**

In `docs/ARCHITECTURE.md`, under the DDD Tactical Layering forced rules near the existing `domain` bullet, add:

```markdown
- `domain` 生产代码必须保持框架无关：不得依赖 `org.springframework..`、HTTP transport 类型、MyBatis mapper/dataobject、controller DTO 或 owner-domain `api.*`。无状态 domain service 可由 application 直接构造，或由外层配置类装配为 Bean；domain 类本身不使用 Spring stereotype。
- `application.result` 只表达用例结果，不返回 `ResponseCookie`、`ResponseEntity`、`Resource`、`MediaType`、servlet request/response 等 HTTP transport 类型；HTTP cookie/header/media type 转换属于 controller 或 web adapter。
```

- [ ] **Step 2: Update docs/SYSTEM_DESIGN.md DDD freeze rules**

In `docs/SYSTEM_DESIGN.md`, under `DDD Tactical Layering 冻结规则`, add:

```markdown
- `domain` 层保持 plain Java，不依赖 Spring stereotype 或 Spring util；
- `application` 层可以使用事务、幂等、配置等应用编排能力，但 application result 不暴露 HTTP transport 类型；
- Cookie、header、media type、`ResponseEntity`、`Resource` 等 HTTP 表达只能出现在 controller / web adapter / infrastructure 边界。
```

- [ ] **Step 3: Run focused backend verification**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=AuthControllerUnitTest,LoginApplicationServiceTest,RefreshTokenApplicationServiceTest,UserFileApplicationServiceTest,FilesControllerStorageRoutingTest test
```

Expected: PASS.

- [ ] **Step 4: Run architecture verification**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

- [ ] **Step 5: Run full community-app tests**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 6: Verify no transport leaks remain in application result**

Run:

```bash
cd /home/feng/code/project/community
rg -n "org\\.springframework\\.http|org\\.springframework\\.core\\.io|jakarta\\.servlet|ResponseCookie|ResponseEntity|MediaType|Resource" backend/community-app/src/main/java/com/nowcoder/community/*/application/result
```

Expected: no output.

- [ ] **Step 7: Verify no Spring references remain in domain services**

Run:

```bash
cd /home/feng/code/project/community
rg -n "org\\.springframework|@Service|StringUtils|DigestUtils" backend/community-app/src/main/java/com/nowcoder/community/*/domain/service
```

Expected: no output.

- [ ] **Step 8: Commit docs and final verification fixes**

```bash
cd /home/feng/code/project/community
git add docs/ARCHITECTURE.md docs/SYSTEM_DESIGN.md
git commit -m "docs: document ddd boundary purity rules"
```

If final verification required code fixes, include only those fix files in the same commit when they are directly related to this phase.

---

## Self-Review Checklist

### Spec Coverage

- Domain framework independence: covered by Task 1 and Task 4.
- Application result transport independence: covered by Task 1, Task 2, and Task 3.
- Auth cookie behavior unchanged: covered by Task 2 tests and Task 5 focused verification.
- `/files/**` behavior unchanged: covered by Task 3 tests and Task 5 focused verification.
- Documentation alignment: covered by Task 5.
- Out-of-scope content/market/frontend work: intentionally deferred; this plan does not touch those files beyond docs references.

### Placeholder Scan

This plan contains no placeholder steps. Conditional configuration in Task 4 Step 6 is explicit and includes the exact file content to add only if Spring wiring requires it.

### Type Consistency

- `RefreshCookieSpec` is the only application-layer cookie carrier.
- `LoginResult.refreshCookie()` and `RefreshResult.refreshCookie()` both return `RefreshCookieSpec`.
- `RefreshTokenApplicationService.IssuedRefreshToken.cookie()` returns `RefreshCookieSpec`.
- `AvatarFileResult` contains `InputStream content`, `String contentType`, and `long contentLength`.
- `FilesController` is the only phase-one caller that converts `AvatarFileResult` to `InputStreamResource` and `MediaType`.
