# Community App DDD Boundary Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden `backend/community-app` DDD boundaries so application code is transport-neutral and event/outbox adapters delegate business decisions to same-domain application services.

**Architecture:** Add executable ArchUnit guardrails first, then fix the smallest behavior-preserving boundary leaks. User avatar upload uses an application-owned upload abstraction, search outbox handling moves current-state projection decisions into search application, and content post contract payload assembly moves into content application while infrastructure remains a technical event bridge.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Mockito, AssertJ, ArchUnit, Maven.

---

## File Structure

Architecture guardrails:

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
  - Tighten application transport dependency rules to include Spring Web upload packages.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`
  - Extend inbound adapter checks from `*Listener` to event/job/outbox `*Listener`, `*Handler`, `*Bridge`, `*Enqueuer`, and `*Job`.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
  - Add a reusable condition that forbids foreign owner-domain `api.query`, `api.action`, and `api.model` before the same-domain application boundary.

User avatar boundary:

- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/AvatarUploadContent.java`
  - Application-neutral upload input with stream supplier, content type, size, and empty flag.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java`
  - Replace `MultipartFile` with `AvatarUploadContent`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
  - Accept `AvatarUploadContent` in upload use case.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserFileApplicationService.java`
  - Accept a file key, not a raw request URI.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
  - Convert `MultipartFile` to `AvatarUploadContent` at the controller boundary.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
  - Extract and decode `/files/**` key at the controller boundary.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java`
  - Delegate neutral upload content to avatar infrastructure.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarService.java`
  - Accept neutral upload content.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarStorageProvider.java`
  - Accept neutral upload content.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/LocalAvatarStorageProvider.java`
  - Validate and stream neutral upload content.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/R2AvatarStorageProvider.java`
  - Validate and stream neutral upload content.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`

Search outbox boundary:

- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java`
  - Command carrying post id and source event metadata from outbox.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
  - Owns content current-state lookup and search sync/delete decision.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java`
  - Thin technical adapter that only deserializes outbox payload and calls search application.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`

Content event payload assembly:

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventPublisher.java`
  - Application-owned port for publishing content contract events.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java`
  - Assembles `PostPayload` from domain repositories and `ContentTextCodec`.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostContractEventApplicationService.java`
  - Publishes post contract events with application-owned payload assembly.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostDomainEventBridge.java`
  - Delegate post domain event handling to `PostContractEventApplicationService`.
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostPayloadAssembler.java`
  - Replaced by application-owned `ContentPostPayloadAssembler`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventPublisher.java`
  - Remove this infrastructure package interface after moving it to application.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
  - Implement `content.application.ContentEventPublisher`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/InMemoryContentEventPublisher.java`
  - Implement `content.application.ContentEventPublisher`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java`
  - Import application-owned `ContentEventPublisher`.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentPostPayloadAssemblerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostContractEventApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostDomainEventBridgeTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridgeTest.java`

Documentation:

- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`
- Modify: `docs/superpowers/specs/2026-05-02-community-app-ddd-boundary-hardening-design.md`

---

### Task 1: Add RED Architecture Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`

- [ ] **Step 1: Tighten application transport dependency rule**

In `DddLayeringArchTest`, update `application_must_not_depend_on_web_transport_types` to include Spring Web and multipart packages:

```java
    @ArchTest
    static final ArchRule application_must_not_depend_on_web_transport_types =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.http..",
                            "org.springframework.core.io..",
                            "org.springframework.web..",
                            "org.springframework.web.multipart..",
                            "jakarta.servlet.."
                    )
                    .because("HTTP transport details belong in controllers or web adapters");
```

- [ ] **Step 2: Add foreign owner API condition helper**

In `ArchitectureRulesSupport`, add this method before `notDependOnSameDomainOwnerApiPackages`:

```java
    static ArchCondition<JavaClass> notDependOnForeignOwnerApiPackages(Set<String> legacyOriginWhitelist) {
        return new ArchCondition<>("not depend on foreign owner api packages before application boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (isWhitelisted(item, legacyOriginWhitelist)) {
                    return;
                }
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetDomain = domainOf(target);
                    if (originDomain.equals(targetDomain) || !CORE_DOMAINS.contains(targetDomain)) {
                        continue;
                    }
                    if (!residesInPackagePrefixes(target, Set.of("api.query", "api.action", "api.model"))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }
```

- [ ] **Step 3: Add event/job adapter guardrails**

Replace `ListenerBoundaryArchTest` with:

```java
package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ListenerBoundaryArchTest {

    private static final Set<String> LEGACY_LISTENER_APPLICATION_BOUNDARY = Set.of();
    private static final Set<String> LEGACY_INBOUND_FOREIGN_API_BOUNDARY = Set.of();

    @Test
    void listenerApplicationBoundaryShouldNotRequireLegacyExceptions() {
        assertThat(LEGACY_LISTENER_APPLICATION_BOUNDARY).isEmpty();
    }

    @Test
    void inboundForeignApiBoundaryShouldNotRequireLegacyExceptions() {
        assertThat(LEGACY_INBOUND_FOREIGN_API_BOUNDARY).isEmpty();
    }

    @ArchTest
    static final ArchRule listeners_must_not_depend_on_same_domain_non_application_entry_points =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnSameDomainServicesExceptApplicationServices(
                            LEGACY_LISTENER_APPLICATION_BOUNDARY
                    ));

    @ArchTest
    static final ArchRule inbound_adapters_must_not_depend_on_foreign_owner_apis =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnForeignOwnerApiPackages(
                            LEGACY_INBOUND_FOREIGN_API_BOUNDARY
                    ));
}
```

- [ ] **Step 4: Run architecture tests and verify RED**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='DddLayeringArchTest,ListenerBoundaryArchTest' test
```

Do not use a package-style Surefire selector such as `-Dtest='com.nowcoder.community.app.arch.*'`; with this module's Surefire settings it can exit successfully without running the intended ArchUnit classes.

Expected: FAIL. The failure must include:

- `UserAvatarApplicationService` or `AvatarStoragePort` depending on `org.springframework.web.multipart.MultipartFile`;
- `PostOutboxHandler` depending on `content.api.query.PostScanQueryApi` or `content.api.model.PostScanView`.

- [ ] **Step 5: Commit RED guardrails**

Run:

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java
git commit -m "test: harden ddd boundary guardrails"
```

---

### Task 2: Refactor User Avatar Upload Boundary

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/AvatarUploadContent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserFileApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarStorageProvider.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/LocalAvatarStorageProvider.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/R2AvatarStorageProvider.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`

- [ ] **Step 1: Update application tests to remove MultipartFile**

In `UserAvatarApplicationServiceTest`, remove:

```java
import org.springframework.web.multipart.MultipartFile;
```

Add:

```java
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
```

Remove the `MultipartFile file` mock field:

```java
    @Mock
    private MultipartFile file;
```

Add this helper before `uuid`:

```java
    private static AvatarUploadContent uploadContent() {
        return new AvatarUploadContent(
                () -> new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                "image/png",
                6,
                false
        );
    }
```

Change `uploadShouldRejectNonSelfActor` to:

```java
    @Test
    void uploadShouldRejectNonSelfActor() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        String fileName = "avatar/" + targetUserId + "/0123456789abcdef0123456789abcdef";
        AvatarUploadContent content = uploadContent();

        Throwable thrown = catchThrowable(() -> service.upload(actorUserId, targetUserId, fileName, content));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("只能操作自己的头像");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verifyNoInteractions(avatarStoragePort, userRepository);
    }
```

Change `uploadShouldDelegateToAvatarStoragePort` to:

```java
    @Test
    void uploadShouldDelegateToAvatarStoragePort() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID userId = uuid(7);
        String fileName = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        AvatarUploadContent content = uploadContent();

        service.upload(userId, userId, fileName, content);

        verify(avatarStoragePort).upload(userId, fileName, content);
        verifyNoInteractions(userRepository);
    }
```

- [ ] **Step 2: Update file service test to pass file key**

In `UserFileApplicationServiceTest`, change calls from URI to key:

```java
        AvatarFileResult result = service.loadAvatarOrNull(key);
```

and:

```java
        Throwable thrown = catchThrowable(() -> service.loadAvatarOrNull("avatar/../secret"));
```

Keep the existing assertions for `fileKey 非法` and `INVALID_ARGUMENT`.

- [ ] **Step 3: Run user application tests and verify RED**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='UserAvatarApplicationServiceTest,UserFileApplicationServiceTest' test
```

Expected: FAIL because `AvatarUploadContent` does not exist and `UserAvatarApplicationService.upload(...)` still accepts `MultipartFile`.

- [ ] **Step 4: Add application-neutral upload content**

Create `AvatarUploadContent.java`:

```java
package com.nowcoder.community.user.application;

import java.io.IOException;
import java.io.InputStream;

public record AvatarUploadContent(
        UploadStream uploadStream,
        String contentType,
        long size,
        boolean empty
) {

    public AvatarUploadContent {
        contentType = contentType == null ? "" : contentType.trim().toLowerCase();
    }

    public InputStream openStream() throws IOException {
        if (uploadStream == null) {
            return InputStream.nullInputStream();
        }
        return uploadStream.openStream();
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
```

- [ ] **Step 5: Update application port and application services**

In `AvatarStoragePort`, replace the file import and upload signature with:

```java
package com.nowcoder.community.user.application.port;

import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;

import java.util.UUID;

public interface AvatarStoragePort {

    AvatarUploadTokenResult createUploadToken(UUID userId);

    void upload(UUID userId, String fileName, AvatarUploadContent content);

    void assertAndConsumeUploadTicket(UUID userId, String fileName);

    String buildAvatarUrl(String fileName);

    AvatarFileResult loadAvatarOrNull(String fileKey);
}
```

In `UserAvatarApplicationService`, remove the `MultipartFile` import and change upload to:

```java
    public void upload(UUID actorUserId, UUID userId, String fileName, AvatarUploadContent content) {
        requireSelf(actorUserId, userId);
        avatarStoragePort.upload(userId, fileName, content);
    }
```

In `UserFileApplicationService`, replace `loadAvatarOrNull` and remove `resolveKey`:

```java
    public AvatarFileResult loadAvatarOrNull(String fileKey) {
        String key = fileKey == null ? "" : fileKey.trim();
        if (!StringUtils.hasText(key) || !AVATAR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        return avatarStoragePort.loadAvatarOrNull(key);
    }
```

- [ ] **Step 6: Update controller boundaries**

In `UserController`, add imports:

```java
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.AvatarUploadContent;

import java.io.IOException;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
```

Change `uploadAvatar` to:

```java
    @PostMapping(value = "/{userId}/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> uploadAvatar(Authentication authentication, @PathVariable UUID userId, @RequestParam("file") MultipartFile file, @RequestParam("fileName") String fileName) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        userAvatarApplicationService.upload(currentUserId, userId, fileName, toAvatarUploadContent(file));
        SecurityEventLogger.info(
                log,
                "avatar_upload",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_file_name", fileName,
                "community.file_content_type", file == null ? null : file.getContentType(),
                "community.file_size_bytes", file == null ? null : file.getSize()
        );
        return Result.ok();
    }
```

Add this helper near other private helpers:

```java
    private static AvatarUploadContent toAvatarUploadContent(MultipartFile file) {
        return new AvatarUploadContent(
                () -> {
                    if (file == null) {
                        return java.io.InputStream.nullInputStream();
                    }
                    try {
                        return file.getInputStream();
                    } catch (IOException e) {
                        throw new BusinessException(INTERNAL_ERROR, "读取头像失败", e);
                    }
                },
                file == null ? "" : file.getContentType(),
                file == null ? 0 : file.getSize(),
                file == null || file.isEmpty()
        );
    }
```

In `FilesController`, add imports:

```java
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
```

Change `get` to:

```java
    @GetMapping("/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        AvatarFileResult stored = userFileApplicationService.loadAvatarOrNull(resolveFileKey(request));
        if (stored == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.parseMediaType(stored.contentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        headers.set("X-Content-Type-Options", "nosniff");

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers);
        if (stored.contentLength() >= 0) {
            builder.contentLength(stored.contentLength());
        }
        return builder.body(new InputStreamResource(stored.content()));
    }
```

Add:

```java
    private String resolveFileKey(HttpServletRequest request) {
        String uri = request == null ? "" : request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String prefix = "/files/";
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            return "";
        }
        String raw = uri.substring(idx + prefix.length());
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
```

- [ ] **Step 7: Update avatar infrastructure to accept neutral content**

In `UserAvatarStorageAdapter`, change upload to:

```java
    @Override
    public void upload(UUID userId, String fileName, AvatarUploadContent content) {
        avatarService.upload(userId, fileName, content);
    }
```

In `AvatarService`, replace `MultipartFile` import with:

```java
import com.nowcoder.community.user.application.AvatarUploadContent;
```

and change upload to:

```java
    public void upload(UUID userId, String fileName, AvatarUploadContent content) {
        assertUploadTicketOwner(userId, fileName);
        currentProvider().upload(userId, fileName.trim(), content);
    }
```

In `AvatarStorageProvider`, replace the upload signature with:

```java
    void upload(UUID userId, String fileName, AvatarUploadContent content);
```

and import:

```java
import com.nowcoder.community.user.application.AvatarUploadContent;
```

In `LocalAvatarStorageProvider`, replace `MultipartFile` import with `AvatarUploadContent` and change upload to:

```java
    @Override
    public void upload(UUID userId, String fileName, AvatarUploadContent content) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (content == null || content.empty()) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        if (content.size() > AvatarConstraints.MAX_AVATAR_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "头像文件过大（maxBytes=" + AvatarConstraints.MAX_AVATAR_BYTES + "）");
        }
        String contentType = StringUtils.hasText(content.contentType()) ? content.contentType().trim().toLowerCase() : "";
        if (!AvatarConstraints.ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的图片格式（mime=" + contentType + "）");
        }
        if (!StringUtils.hasText(fileName) || !fileName.startsWith(AvatarConstraints.KEY_PREFIX + userId + "/")) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }

        String baseDir = properties.getFilesBaseDir();
        if (!StringUtils.hasText(baseDir)) {
            throw new BusinessException(INVALID_ARGUMENT, "filesBaseDir 未配置");
        }

        try {
            Path base = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(base);

            Path target = base.resolve(fileName).normalize();
            if (!target.startsWith(base)) {
                throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
            }

            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(target)) {
                throw new BusinessException(INVALID_ARGUMENT, "文件已存在，请重试");
            }

            try (InputStream in = content.openStream()) {
                Files.copy(in, target);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException | RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "保存头像失败", e);
        }
    }
```

In `R2AvatarStorageProvider`, replace `MultipartFile` import with `AvatarUploadContent` and change upload to:

```java
    @Override
    public void upload(UUID userId, String fileName, AvatarUploadContent content) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (content == null || content.empty()) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        if (content.size() > AvatarConstraints.MAX_AVATAR_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "头像文件过大（maxBytes=" + AvatarConstraints.MAX_AVATAR_BYTES + "）");
        }
        String contentType = StringUtils.hasText(content.contentType()) ? content.contentType().trim().toLowerCase() : "";
        if (!AvatarConstraints.ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的图片格式（mime=" + contentType + "）");
        }
        if (!StringUtils.hasText(fileName) || !fileName.startsWith(AvatarConstraints.KEY_PREFIX + userId + "/")) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }

        String bucket = requireBucketName();
        String key = fileName.trim();
        try (InputStream in = content.openStream()) {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(req, RequestBody.fromInputStream(in, content.size()));
        } catch (S3Exception e) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败", e);
        } catch (java.io.IOException | RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败", e);
        }
    }
```

- [ ] **Step 8: Update controller storage routing test stub**

In `FilesControllerStorageRoutingTest`, replace the `MultipartFile` import with:

```java
import com.nowcoder.community.user.application.AvatarUploadContent;
```

Change the stub provider upload method to:

```java
            @Override
            public void upload(UUID userId, String fileName, AvatarUploadContent content) {
                throw new UnsupportedOperationException("upload not needed");
            }
```

- [ ] **Step 9: Run user tests and architecture tests**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='UserAvatarApplicationServiceTest,UserFileApplicationServiceTest,FilesControllerStorageRoutingTest' test
mvn -q -f backend/pom.xml -pl community-app -Dtest='DddLayeringArchTest,ListenerBoundaryArchTest' test
```

Expected:

- focused user tests PASS;
- architecture tests still FAIL only for remaining search/content boundary issues.

- [ ] **Step 10: Commit user boundary cleanup**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/application/AvatarUploadContent.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/application/UserFileApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarService.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarStorageProvider.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/LocalAvatarStorageProvider.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/R2AvatarStorageProvider.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java
git commit -m "refactor: keep avatar upload application neutral"
```

---

### Task 3: Move Search Outbox Projection Decisions Into Application

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`

- [ ] **Step 1: Replace handler tests with thin adapter expectations**

Replace `PostOutboxHandlerTest` with:

```java
package com.nowcoder.community.search.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostOutboxHandlerTest {

    @Test
    void handlerShouldDeserializePayloadAndDelegateToApplication() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchPostProjectionApplicationService projectionApplicationService = mock(SearchPostProjectionApplicationService.class);
        UUID postId = uuid(101);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, projectionApplicationService);

        handler.handle(outboxEvent(objectMapper, postId, "src-s1", "PostUpdated"));

        ArgumentCaptor<ProjectPostOutboxCommand> captor = ArgumentCaptor.forClass(ProjectPostOutboxCommand.class);
        verify(projectionApplicationService).projectPostFromOutbox(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(postId);
        assertThat(captor.getValue().sourceEventId()).isEqualTo("src-s1");
        assertThat(captor.getValue().sourceEventType()).isEqualTo("PostUpdated");
    }

    @Test
    void handlerShouldIgnoreBlankPayload() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchPostProjectionApplicationService projectionApplicationService = mock(SearchPostProjectionApplicationService.class);
        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, projectionApplicationService);

        handler.handle(new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                "aggregate",
                PostOutboxHandler.TOPIC,
                "key",
                " ",
                "PENDING",
                0,
                null,
                null
        ));

        verifyNoInteractions(projectionApplicationService);
    }

    @Test
    void handlerShouldFailInvalidPayload() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchPostProjectionApplicationService projectionApplicationService = mock(SearchPostProjectionApplicationService.class);
        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, projectionApplicationService);

        Throwable thrown = catchThrowable(() -> handler.handle(new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                "aggregate",
                PostOutboxHandler.TOPIC,
                "key",
                "{",
                "PENDING",
                0,
                null,
                null
        )));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("search outbox payload 反序列化失败");
        verifyNoInteractions(projectionApplicationService);
    }

    private static OutboxEvent outboxEvent(
            ObjectMapper objectMapper,
            UUID postId,
            String sourceEventId,
            String sourceEventType
    ) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "postId", postId,
                "sourceEventId", sourceEventId,
                "sourceEventType", sourceEventType
        ));
        return new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                sourceEventId + ":search_post",
                PostOutboxHandler.TOPIC,
                postId.toString(),
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );
    }
}
```

- [ ] **Step 2: Add projection application service tests**

Create `SearchPostProjectionApplicationServiceTest.java`:

```java
package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchPostProjectionApplicationServiceTest {

    @Test
    void projectPostFromOutboxShouldIgnoreNullPostId() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(null, "src", "PostUpdated"));

        verifyNoInteractions(postScanQueryApi, searchApplicationService);
    }

    @Test
    void projectPostFromOutboxShouldDeleteWhenProjectionNoLongerExists() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(101))).thenReturn(null);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-s3", "PostDeleted"));

        verify(searchApplicationService).deletePost(new DeleteIndexedPostCommand(uuid(101)));
        verify(searchApplicationService, never()).syncPostProjection(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void projectPostFromOutboxShouldSyncCurrentProjectionWhenPostExists() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                uuid(101),
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                0,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(101))).thenReturn(doc);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-s1", "PostUpdated"));

        ArgumentCaptor<SyncPostProjectionCommand> captor = ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        verify(searchApplicationService).syncPostProjection(captor.capture());
        verify(searchApplicationService, never()).deletePost(org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().postId()).isEqualTo(uuid(101));
        assertThat(captor.getValue().userId()).isEqualTo(uuid(7));
        assertThat(captor.getValue().categoryId()).isEqualTo(uuid(3));
        assertThat(captor.getValue().tags()).containsExactly("java");
        assertThat(captor.getValue().title()).isEqualTo("title");
        assertThat(captor.getValue().content()).isEqualTo("content");
        assertThat(captor.getValue().status()).isEqualTo(0);
    }
}
```

- [ ] **Step 3: Run search tests and verify RED**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='PostOutboxHandlerTest,SearchPostProjectionApplicationServiceTest' test
```

Expected: FAIL because `ProjectPostOutboxCommand` and `SearchPostProjectionApplicationService` do not exist and `PostOutboxHandler` has the old constructor.

- [ ] **Step 4: Add search command and application service**

Create `ProjectPostOutboxCommand.java`:

```java
package com.nowcoder.community.search.application.command;

import java.util.UUID;

public record ProjectPostOutboxCommand(
        UUID postId,
        String sourceEventId,
        String sourceEventType
) {
}
```

Create `SearchPostProjectionApplicationService.java`:

```java
package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.springframework.stereotype.Service;

@Service
public class SearchPostProjectionApplicationService {

    private final PostScanQueryApi postScanQueryApi;
    private final SearchApplicationService searchApplicationService;

    public SearchPostProjectionApplicationService(
            PostScanQueryApi postScanQueryApi,
            SearchApplicationService searchApplicationService
    ) {
        this.postScanQueryApi = postScanQueryApi;
        this.searchApplicationService = searchApplicationService;
    }

    public void projectPostFromOutbox(ProjectPostOutboxCommand command) {
        if (command == null || command.postId() == null) {
            return;
        }
        PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(command.postId());
        if (projection == null || projection.postId() == null) {
            searchApplicationService.deletePost(new DeleteIndexedPostCommand(command.postId()));
            return;
        }
        searchApplicationService.syncPostProjection(PostSearchPayloadMapper.toSyncCommand(projection));
    }
}
```

- [ ] **Step 5: Thin PostOutboxHandler**

In `PostOutboxHandler`, remove imports:

```java
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.PostSearchPayloadMapper;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
```

Add imports:

```java
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
```

Replace fields and constructor with:

```java
    private final ObjectMapper objectMapper;
    private final SearchPostProjectionApplicationService projectionApplicationService;

    public PostOutboxHandler(
            ObjectMapper objectMapper,
            SearchPostProjectionApplicationService projectionApplicationService
    ) {
        this.objectMapper = objectMapper;
        this.projectionApplicationService = projectionApplicationService;
    }
```

Replace the post-id processing block in `handle` with:

```java
        if (payload.getPostId() == null) {
            return;
        }
        projectionApplicationService.projectPostFromOutbox(new ProjectPostOutboxCommand(
                payload.getPostId(),
                payload.getSourceEventId(),
                payload.getSourceEventType()
        ));
```

- [ ] **Step 6: Run search tests and architecture tests**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='PostOutboxHandlerTest,SearchPostProjectionApplicationServiceTest,SearchApplicationServiceTest' test
mvn -q -f backend/pom.xml -pl community-app -Dtest='DddLayeringArchTest,ListenerBoundaryArchTest' test
```

Expected:

- search focused tests PASS;
- architecture tests no longer report `PostOutboxHandler -> content.api.*`;
- architecture tests may still fail for content event payload assembly until Task 4 completes.

- [ ] **Step 7: Commit search boundary cleanup**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java \
        backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandlerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java
git commit -m "refactor: route search outbox through application"
```

---

### Task 4: Move Content Post Payload Assembly Into Application

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostContractEventApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostDomainEventBridge.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostPayloadAssembler.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/InMemoryContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentPostPayloadAssemblerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostContractEventApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostDomainEventBridgeTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridgeTest.java`

- [ ] **Step 1: Add content application tests for post payload assembly**

Create `ContentPostPayloadAssemblerTest.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentPostPayloadAssemblerTest {

    @Test
    void assembleShouldLoadPostTagsAndDecodeText() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        TagContentRepository tagRepository = mock(TagContentRepository.class);
        ContentRenderProperties renderProperties = new ContentRenderProperties();
        ContentPostPayloadAssembler assembler =
                new ContentPostPayloadAssembler(postRepository, tagRepository, new ContentTextCodec(renderProperties));

        DiscussPost post = new DiscussPost();
        post.setId(uuid(11));
        post.setUserId(uuid(7));
        post.setCategoryId(uuid(3));
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;p&gt;body&lt;/p&gt;");
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(Date.from(java.time.Instant.parse("2026-04-29T09:30:00Z")));
        post.setScore(2.5);

        when(postRepository.getByIdAllowDeleted(uuid(11))).thenReturn(post);
        when(tagRepository.getTagsByPostIds(List.of(uuid(11)))).thenReturn(Map.of(uuid(11), List.of("java", "ddd")));

        PostPayload payload = assembler.assemble(uuid(11));

        assertThat(payload.getPostId()).isEqualTo(uuid(11));
        assertThat(payload.getUserId()).isEqualTo(uuid(7));
        assertThat(payload.getCategoryId()).isEqualTo(uuid(3));
        assertThat(payload.getTags()).containsExactly("java", "ddd");
        assertThat(payload.getTitle()).isEqualTo("<title>");
        assertThat(payload.getContent()).isEqualTo("<p>body</p>");
        assertThat(payload.getType()).isEqualTo(0);
        assertThat(payload.getStatus()).isEqualTo(0);
        assertThat(payload.getCreateTime()).isEqualTo(java.time.Instant.parse("2026-04-29T09:30:00Z"));
        assertThat(payload.getScore()).isEqualTo(2.5);
    }
}
```

- [ ] **Step 2: Add content application tests for post contract publication**

Create `PostContractEventApplicationServiceTest.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostContractEventApplicationServiceTest {

    @Test
    void publishPostPublishedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostContractEventApplicationService service =
                new PostContractEventApplicationService(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(11));
        when(assembler.assemble(uuid(11))).thenReturn(payload);

        service.publishPostPublished(uuid(11));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(11));
        inOrder.verify(eventPublisher).publishPostPublished(payload);
    }

    @Test
    void publishPostUpdatedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostContractEventApplicationService service =
                new PostContractEventApplicationService(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(12));
        when(assembler.assemble(uuid(12))).thenReturn(payload);

        service.publishPostUpdated(uuid(12));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(12));
        inOrder.verify(eventPublisher).publishPostUpdated(payload);
    }

    @Test
    void publishPostDeletedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostContractEventApplicationService service =
                new PostContractEventApplicationService(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(13));
        when(assembler.assemble(uuid(13))).thenReturn(payload);

        service.publishPostDeleted(uuid(13));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(13));
        inOrder.verify(eventPublisher).publishPostDeleted(payload);
    }
}
```

- [ ] **Step 3: Add post bridge delegation test**

Create `PostDomainEventBridgeTest.java`:

```java
package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.PostContractEventApplicationService;
import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.PostPublishedDomainEvent;
import com.nowcoder.community.content.domain.event.PostUpdatedDomainEvent;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostDomainEventBridgeTest {

    @Test
    void onPostPublishedShouldDelegateToApplicationService() {
        PostContractEventApplicationService applicationService = mock(PostContractEventApplicationService.class);
        PostDomainEventBridge bridge = new PostDomainEventBridge(applicationService);

        bridge.onPostPublished(new PostPublishedDomainEvent(uuid(11)));

        verify(applicationService).publishPostPublished(uuid(11));
    }

    @Test
    void onPostUpdatedShouldDelegateToApplicationService() {
        PostContractEventApplicationService applicationService = mock(PostContractEventApplicationService.class);
        PostDomainEventBridge bridge = new PostDomainEventBridge(applicationService);

        bridge.onPostUpdated(new PostUpdatedDomainEvent(uuid(12)));

        verify(applicationService).publishPostUpdated(uuid(12));
    }

    @Test
    void onPostDeletedShouldDelegateToApplicationService() {
        PostContractEventApplicationService applicationService = mock(PostContractEventApplicationService.class);
        PostDomainEventBridge bridge = new PostDomainEventBridge(applicationService);

        bridge.onPostDeleted(new PostDeletedDomainEvent(uuid(13)));

        verify(applicationService).publishPostDeleted(uuid(13));
    }
}
```

- [ ] **Step 4: Run content tests and verify RED**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='ContentPostPayloadAssemblerTest,PostContractEventApplicationServiceTest,PostDomainEventBridgeTest,CommentDomainEventBridgeTest' test
```

Expected: FAIL because application-owned `ContentEventPublisher`, `ContentPostPayloadAssembler`, and `PostContractEventApplicationService` do not exist.

- [ ] **Step 5: Create application-owned content event publisher port**

Create `content/application/ContentEventPublisher.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;

public interface ContentEventPublisher {

    void publishPostPublished(PostPayload payload);

    void publishPostUpdated(PostPayload payload);

    void publishPostDeleted(PostPayload payload);

    void publishCommentCreated(CommentPayload payload);

    void publishCommentDeleted(CommentPayload payload);

    void publishModerationActionApplied(ModerationPayload payload);
}
```

Delete `content/infrastructure/event/ContentEventPublisher.java` after imports have been updated.

- [ ] **Step 6: Create application-owned post payload assembler**

Create `ContentPostPayloadAssembler.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ContentPostPayloadAssembler {

    private final PostContentRepository postContentPort;
    private final TagContentRepository tagContentPort;
    private final ContentTextCodec textCodec;

    public ContentPostPayloadAssembler(
            PostContentRepository postContentPort,
            TagContentRepository tagContentPort,
            ContentTextCodec textCodec
    ) {
        this.postContentPort = postContentPort;
        this.tagContentPort = tagContentPort;
        this.textCodec = textCodec;
    }

    public PostPayload assemble(UUID postId) {
        DiscussPost post = postContentPort.getByIdAllowDeleted(postId);
        List<String> tags = tagContentPort.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        return assemble(post, tags);
    }

    public PostPayload assemble(DiscussPost post, List<String> tags) {
        if (post == null || post.getId() == null) {
            throw new IllegalArgumentException("post 为空或非法");
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setTags(tags == null ? List.of() : tags);
        payload.setTitle(textCodec.decodeOnRead(post.getTitle()));
        payload.setContent(textCodec.decodeOnRead(post.getContent()));
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setScore(post.getScore());
        return payload;
    }
}
```

- [ ] **Step 7: Create post contract event application service**

Create `PostContractEventApplicationService.java`:

```java
package com.nowcoder.community.content.application;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostContractEventApplicationService {

    private final ContentPostPayloadAssembler postPayloadAssembler;
    private final ContentEventPublisher eventPublisher;

    public PostContractEventApplicationService(
            ContentPostPayloadAssembler postPayloadAssembler,
            ContentEventPublisher eventPublisher
    ) {
        this.postPayloadAssembler = postPayloadAssembler;
        this.eventPublisher = eventPublisher;
    }

    public void publishPostPublished(UUID postId) {
        eventPublisher.publishPostPublished(postPayloadAssembler.assemble(postId));
    }

    public void publishPostUpdated(UUID postId) {
        eventPublisher.publishPostUpdated(postPayloadAssembler.assemble(postId));
    }

    public void publishPostDeleted(UUID postId) {
        eventPublisher.publishPostDeleted(postPayloadAssembler.assemble(postId));
    }
}
```

- [ ] **Step 8: Thin post domain event bridge**

Replace `PostDomainEventBridge` with:

```java
package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.PostContractEventApplicationService;
import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.PostPublishedDomainEvent;
import com.nowcoder.community.content.domain.event.PostUpdatedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PostDomainEventBridge {

    private final PostContractEventApplicationService applicationService;

    public PostDomainEventBridge(PostContractEventApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostPublished(PostPublishedDomainEvent event) {
        applicationService.publishPostPublished(event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostUpdated(PostUpdatedDomainEvent event) {
        applicationService.publishPostUpdated(event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostDeleted(PostDeletedDomainEvent event) {
        applicationService.publishPostDeleted(event.postId());
    }
}
```

Delete `PostPayloadAssembler.java` from `content/infrastructure/event`.

- [ ] **Step 9: Update infrastructure event publisher imports**

In `LocalContentEventPublisher`, `InMemoryContentEventPublisher`, and `CommentDomainEventBridge`, add:

```java
import com.nowcoder.community.content.application.ContentEventPublisher;
```

Remove any implicit dependency on `com.nowcoder.community.content.infrastructure.event.ContentEventPublisher` by deleting the old infrastructure interface file.

- [ ] **Step 10: Run content tests and architecture tests**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='ContentPostPayloadAssemblerTest,PostContractEventApplicationServiceTest,PostDomainEventBridgeTest,CommentDomainEventBridgeTest' test
mvn -q -f backend/pom.xml -pl community-app -Dtest='DddLayeringArchTest,ListenerBoundaryArchTest' test
```

Expected:

- focused content tests PASS;
- architecture tests PASS unless a rule intentionally exposes another in-scope boundary leak.

- [ ] **Step 11: Commit content event boundary cleanup**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/PostContractEventApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostDomainEventBridge.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/InMemoryContentEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentPostPayloadAssemblerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/PostContractEventApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostDomainEventBridgeTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridgeTest.java
git add -u backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventPublisher.java \
           backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostPayloadAssembler.java
git commit -m "refactor: move content post payload assembly to application"
```

---

### Task 5: Align Architecture Documentation

**Files:**
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`
- Modify: `docs/superpowers/specs/2026-05-02-community-app-ddd-boundary-hardening-design.md`

- [ ] **Step 1: Update architecture handbook**

In `docs/handbook/architecture.md`, update the application layer bullet that already mentions transport neutrality so it explicitly includes upload transport:

```markdown
- `application.command` / `application.result` / application-owned ports only express application semantics. They must not expose HTTP transport types such as `ResponseEntity`, `ResponseCookie`, `Resource`, `MediaType`, Servlet request/response types, or Spring Web upload types such as `MultipartFile`.
```

Add this bullet to the Controller / Listener / Job section:

```markdown
- Inbound adapters include controllers, local event listeners, outbox handlers, event bridges, enqueuers, and scheduled jobs. They adapt input and call same-domain application services; they must not perform foreign owner `api.*` collaboration before entering the same-domain application layer.
```

- [ ] **Step 2: Update system design handbook**

In `docs/handbook/system-design.md`, replace the search projection wording that says the handler returns to content current state with wording that places the decision in search application:

```markdown
- 投影入口：content 事件 -> search outbox -> `PostOutboxHandler` -> search application。
- `PostOutboxHandler` 只负责 outbox payload 适配；search application 回源 content owner 当前状态，再 upsert/delete ES，避免乱序事件把已删除内容复活。
```

- [ ] **Step 3: Update strict DDD design spec**

In `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`, update the application neutrality sentence to include Spring Web upload types:

```markdown
It must not depend directly on MyBatis mapper/dataobject types or HTTP DTOs. Application command/result types and application-owned ports must also stay transport-neutral: they must not expose HTTP response/cookie/header/file abstractions such as `ResponseCookie`, `ResponseEntity`, `MediaType`, `Resource`, Servlet request/response types, or Spring Web upload types such as `MultipartFile`.
```

Also update the executable guardrail sentence near the end so it mentions inbound handler coverage:

```markdown
The current executable guardrails include `DddLayeringArchTest`, `ControllerBoundaryArchTest`, `ListenerBoundaryArchTest`, and `DtoBoundaryArchTest`. They protect retired root legacy packages, same-domain controller/listener/handler boundaries, domain Spring independence, application transport neutrality, and DTO leakage.
```

- [ ] **Step 4: Update hardening spec status**

In `docs/superpowers/specs/2026-05-02-community-app-ddd-boundary-hardening-design.md`, change:

```markdown
**Status:** Draft for review
```

to:

```markdown
**Status:** Approved for first-batch implementation
```

- [ ] **Step 5: Commit documentation alignment**

Run:

```bash
git add docs/handbook/architecture.md \
        docs/handbook/system-design.md \
        docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md \
        docs/superpowers/specs/2026-05-02-community-app-ddd-boundary-hardening-design.md
git commit -m "docs: align ddd boundary hardening rules"
```

---

### Task 6: Final Verification

**Files:**
- Read: all files changed in Tasks 1-5.

- [ ] **Step 1: Run focused boundary tests**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='UserAvatarApplicationServiceTest,UserFileApplicationServiceTest,FilesControllerStorageRoutingTest,PostOutboxHandlerTest,SearchPostProjectionApplicationServiceTest,SearchApplicationServiceTest,ContentPostPayloadAssemblerTest,PostContractEventApplicationServiceTest,PostDomainEventBridgeTest,CommentDomainEventBridgeTest' test
```

Expected: PASS.

- [ ] **Step 2: Run architecture tests**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='DddLayeringArchTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,DtoBoundaryArchTest,InfraBoundaryArchTest,ListenerBoundaryArchTest,TransactionBoundaryArchTest' test
```

Expected: PASS.

- [ ] **Step 3: Run community-app test suite**

Run:

```bash
mvn -q -f backend/pom.xml -pl community-app test
```

Expected: PASS.

- [ ] **Step 4: Inspect forbidden dependencies**

Run:

```bash
rg -n "org\\.springframework\\.web\\.multipart|MultipartFile" backend/community-app/src/main/java/com/nowcoder/community/*/application
rg -n "import com\\.nowcoder\\.community\\.content\\.api\\." backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java
rg -n "PostPayloadAssembler|PostContentRepository|TagContentRepository" backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event
```

Expected:

- first command prints no matches;
- second command prints no matches;
- third command prints no infrastructure event matches for `PostPayloadAssembler`, `PostContentRepository`, or `TagContentRepository`.

- [ ] **Step 5: Check worktree**

Run:

```bash
git status --short
```

Expected: no output.

If output exists, inspect it. Commit only intentional changes:

```bash
git add <intentional-files>
git commit -m "test: verify ddd boundary hardening"
```

If no files changed during verification, do not create an empty commit.
