# Avatar Storage (local + R2, served via `/files/**`) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace Qiniu avatar storage with Cloudflare R2, keep upload as server-side multipart, and keep external access stable via `/files/**` for both `local` and `r2`.

**Architecture:** Keep a pluggable backend storage provider selected by `user.avatar.storage` (`local` / `r2`). Upload always goes through backend (`POST /api/users/{id}/avatar/upload`). Reading always goes through backend (`GET /files/avatar/...`), where the controller delegates to the current provider (local reads disk; r2 streams from R2 via S3 API).

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring MVC, AWS SDK v2 (S3 client for R2), Vue3 + Vite (minimal UI update).

---

## Work Area / Branch

This plan assumes execution inside a dedicated git worktree:

- Worktree: `.worktrees/avatar-storage-r2/`
- Branch: `feat/avatar-storage-r2`

All file paths below are **relative to the repo root**, unless explicitly prefixed with `.worktrees/avatar-storage-r2/`.

---

### Task 0: Create worktree + baseline verification

**Files:** none

**Step 1: Create a worktree**

Run:
- `git worktree add .worktrees/avatar-storage-r2 -b feat/avatar-storage-r2`

Expected:
- New directory `.worktrees/avatar-storage-r2/` created.

**Step 2: Verify baseline backend tests**

Run (inside the worktree):
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -am test -q`

Expected:
- Exit code 0 (PASS). WARN logs acceptable.

**Step 3: Record baseline compose config**

Run:
- `cd .worktrees/avatar-storage-r2 && docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`

Expected:
- Exit code 0.

**Step 4: Commit nothing**

Expected:
- `git status -sb` is clean.

---

### Task 1: Introduce provider router + enable `/files/**` to use current provider (failing test first)

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarStorageRouter.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarStorageProvider.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/FilesController.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/user/api/FilesControllerStorageRoutingTest.java`

**Step 1: Write failing test for `/files/**` routing**

Create `backend/community-bootstrap/src/test/java/com/nowcoder/community/user/api/FilesControllerStorageRoutingTest.java`:

```java
package com.nowcoder.community.user.api;

import com.nowcoder.community.user.config.AvatarStorageProperties;
import com.nowcoder.community.user.service.AvatarStorageProvider;
import com.nowcoder.community.user.service.AvatarStorageRouter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilesControllerStorageRoutingTest {

    @Test
    void shouldServeFilesWhenStorageIsNotLocal() {
        AvatarStorageProperties props = new AvatarStorageProperties();
        props.setStorage("r2");
        props.setFilesBaseDir("/tmp/unused");
        props.setPublicBaseUrl("http://localhost:12881");

        AvatarStorageProvider stub = new AvatarStorageProvider() {
            @Override public String provider() { return "r2"; }
            @Override public com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse createUploadToken(int userId, String fileName) { return null; }
            @Override public void upload(int userId, String fileName, org.springframework.web.multipart.MultipartFile file) { }
            @Override public String buildAvatarUrl(String fileName) { return ""; }
            @Override public StoredAvatar loadOrNull(String key) {
                Resource res = new ByteArrayResource("ok".getBytes(StandardCharsets.UTF_8));
                return new StoredAvatar(res, MediaType.TEXT_PLAIN);
            }
        };

        AvatarStorageRouter router = new AvatarStorageRouter(props, List.of(stub));
        FilesController controller = new FilesController(router);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/files/avatar/1/0123456789abcdef0123456789abcdef");

        ResponseEntity<Resource> resp = controller.get(req);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(MediaType.TEXT_PLAIN, resp.getHeaders().getContentType());
        assertNotNull(resp.getBody());
    }
}
```

Expected: test FAILS to compile (because router + `StoredAvatar` do not exist yet; FilesController signature doesn’t match).

**Step 2: Run the single test and confirm it fails**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -Dtest=FilesControllerStorageRoutingTest test -q`

Expected:
- FAIL (compilation errors referencing missing classes/methods).

**Step 3: Add router + minimal retrieval DTO, wire FilesController**

1) Modify `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarStorageProvider.java`:
- Update docs: provider names `local/r2`
- Add a minimal retrieval API:

```java
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

record StoredAvatar(Resource resource, MediaType mediaType) {}

StoredAvatar loadOrNull(String key);
```

2) Create `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarStorageRouter.java`:

```java
package com.nowcoder.community.user.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class AvatarStorageRouter {

    private final AvatarStorageProperties properties;
    private final Map<String, AvatarStorageProvider> providers;

    public AvatarStorageRouter(AvatarStorageProperties properties, List<AvatarStorageProvider> providers) {
        this.properties = properties;
        this.providers = buildProviderMap(providers);
    }

    public AvatarStorageProvider currentProviderOrThrow() {
        String configured = properties == null ? "" : properties.getStorage();
        String key = StringUtils.hasText(configured) ? configured.trim().toLowerCase() : "local";
        AvatarStorageProvider provider = providers.get(key);
        if (provider == null) {
            throw new BusinessException(INVALID_ARGUMENT, "未知头像存储策略：" + key);
        }
        return provider;
    }

    private Map<String, AvatarStorageProvider> buildProviderMap(List<AvatarStorageProvider> list) {
        Map<String, AvatarStorageProvider> map = new HashMap<>();
        if (list == null) return map;
        for (AvatarStorageProvider p : list) {
            if (p == null || !StringUtils.hasText(p.provider())) continue;
            map.put(p.provider().trim().toLowerCase(), p);
        }
        return map;
    }
}
```

3) Modify `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/FilesController.java`:
- Replace direct `AvatarStorageProperties` usage with `AvatarStorageRouter`.
- Remove `isLocalEnabled()` early-return.
- Keep the key validation regex.
- Call `router.currentProviderOrThrow().loadOrNull(key)` and return 404 when null.
- Use `StoredAvatar.mediaType()` as `Content-Type`.

**Step 4: Run the test again**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -Dtest=FilesControllerStorageRoutingTest test -q`

Expected:
- PASS.

**Step 5: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarStorageProvider.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarStorageRouter.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/FilesController.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/test/java/com/nowcoder/community/user/api/FilesControllerStorageRoutingTest.java`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"feat(user): route /files via avatar storage provider\"`

---

### Task 2: Refactor `AvatarService` to use router (no behavior change)

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarService.java`
- Test: (reuse existing tests; no new test required)

**Step 1: Update `AvatarService` to depend on `AvatarStorageRouter`**

Change constructor to inject `AvatarStorageRouter` instead of `AvatarStorageProperties + List<AvatarStorageProvider>` map build.

Minimal change:
- Replace `currentProvider()` implementation with `router.currentProviderOrThrow()`.

**Step 2: Run focused backend tests**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -Dtest=FilesControllerStorageRoutingTest test -q`

Expected:
- PASS.

**Step 3: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarService.java`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"refactor(user): reuse storage router in AvatarService\"`

---

### Task 3: Implement `local` provider `loadOrNull` (keep current behavior)

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/LocalAvatarStorageProvider.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarMediaTypeSniffer.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/user/service/AvatarMediaTypeSnifferTest.java`

**Step 1: Write failing test for media type sniffing**

Create `backend/community-bootstrap/src/test/java/com/nowcoder/community/user/service/AvatarMediaTypeSnifferTest.java` with PNG/JPEG header byte arrays and assert `MediaType.IMAGE_PNG` / `MediaType.IMAGE_JPEG`.

Expected: FAIL (sniffer doesn’t exist).

**Step 2: Implement `AvatarMediaTypeSniffer`**

Create `AvatarMediaTypeSniffer` by moving the existing header checks from `FilesController`:
- `isJpeg/isPng/isGif/isWebp`
- API:

```java
static MediaType sniff(byte[] head);
```

**Step 3: Implement `loadOrNull` in `LocalAvatarStorageProvider`**

Logic:
- Resolve `baseDir` from `AvatarStorageProperties.filesBaseDir`
- Resolve target `base.resolve(key)` and `normalize()`, ensure startsWith(base)
- Ensure file exists and isRegularFile, else return null
- Read first 16 bytes for sniffing (new helper)
- Return `new StoredAvatar(new FileSystemResource(target), mediaType)`

**Step 4: Run the new test**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -Dtest=AvatarMediaTypeSnifferTest test -q`

Expected:
- PASS.

**Step 5: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/AvatarMediaTypeSniffer.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/LocalAvatarStorageProvider.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/test/java/com/nowcoder/community/user/service/AvatarMediaTypeSnifferTest.java`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"feat(user): serve local avatars via provider loadOrNull\"`

---

### Task 4: Add R2 config + S3 client wiring

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/R2Properties.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/R2ClientConfig.java`
- Modify: `backend/community-bootstrap/src/main/resources/application.yml`
- Modify: `backend/community-bootstrap/pom.xml`

**Step 1: Add AWS SDK dependency (S3)**

Modify `backend/community-bootstrap/pom.xml`:
- Add dependency `software.amazon.awssdk:s3`
- (If needed) add BOM import or explicit version aligned with Spring Boot 3.2.x (keep consistent, choose one version and document it in commit message).

**Step 2: Add `R2Properties`**

Create `R2Properties`:

```java
@ConfigurationProperties(prefix = "r2")
public class R2Properties {
  private String endpoint;
  private String accessKey;
  private String secretKey;
  private String bucketName;
  private String region = "auto";
  private boolean pathStyle = true;
  // getters/setters
}
```

**Step 3: Add `R2ClientConfig`**

Create a Spring `@Configuration` exposing an `S3Client` bean:
- endpoint override from `endpoint`
- `StaticCredentialsProvider` from access/secret
- `Region.of(region)` (default `auto`)
- `S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build()`

**Step 4: Wire env mapping**

Modify `backend/community-bootstrap/src/main/resources/application.yml` to include:

```yml
r2:
  endpoint: ${R2_ENDPOINT:}
  access-key: ${R2_ACCESS_KEY:}
  secret-key: ${R2_SECRET_KEY:}
  bucket-name: ${R2_BUCKET_NAME:}
  region: ${R2_REGION:auto}
  path-style: ${R2_PATH_STYLE:true}
```

**Step 5: Build backend**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -am test -q`

Expected:
- PASS.

**Step 6: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/pom.xml`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/R2Properties.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/R2ClientConfig.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/resources/application.yml`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"feat(user): add R2 config and S3 client\"`

---

### Task 5: Implement `r2` storage provider (upload + load)

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/R2AvatarStorageProvider.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/AvatarStorageProperties.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/user/service/R2AvatarStorageProviderUnitTest.java`

**Step 1: Write failing unit test (mock S3 client)**

Create `R2AvatarStorageProviderUnitTest` using Mockito:
- mock `S3Client`
- set `R2Properties` with bucketName/endpoint/accessKey/secretKey (only bucketName required for provider behavior test)
- call `upload(userId, key, file)` with a small `MockMultipartFile`
- assert `s3.putObject(...)` invoked with bucket/key and content-type

Expected: FAIL (provider does not exist).

**Step 2: Implement `R2AvatarStorageProvider`**

Key behavior:
- `provider()` returns `"r2"`
- `createUploadToken(...)` returns a response compatible with server-side upload:
  - `provider=r2`
  - `uploadMethod=POST`
  - `uploadUrl=/api/users/{id}/avatar/upload`
- `upload(...)`:
  - validate `userId`, `fileName` prefix `avatar/{userId}/`
  - validate size + MIME same as local (reuse existing constraints)
  - call `s3.putObject(PutObjectRequest.builder().bucket(bucket).key(fileName).contentType(file.getContentType()).build(), RequestBody.fromInputStream(...))`
- `loadOrNull(key)`:
  - call `s3.getObject(...)` and return `StoredAvatar(new InputStreamResource(stream), parsedContentType)`
  - when key missing, return null (map S3 “NoSuchKey/404” to null)

**Step 3: Run the new unit test**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -Dtest=R2AvatarStorageProviderUnitTest test -q`

Expected:
- PASS.

**Step 4: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/R2AvatarStorageProvider.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/AvatarStorageProperties.java`
- `cd .worktrees/avatar-storage-r2 && git add backend/community-bootstrap/src/test/java/com/nowcoder/community/user/service/R2AvatarStorageProviderUnitTest.java`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"feat(user): add R2 avatar storage provider\"`

---

### Task 6: Remove Qiniu implementation + config + dependency

**Files:**
- Delete: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/QiniuAvatarStorageProvider.java`
- Delete: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/QiniuProperties.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/dto/AvatarUploadTokenResponse.java`
- Modify: `backend/community-bootstrap/src/main/resources/application.yml`
- Modify: `backend/community-bootstrap/pom.xml`

**Step 1: Remove qiniu dependency**

Modify `backend/community-bootstrap/pom.xml`:
- delete `com.qiniu:qiniu-java-sdk`

**Step 2: Remove qiniu config block**

Modify `backend/community-bootstrap/src/main/resources/application.yml`:
- remove the `qiniu:` section

**Step 3: Delete qiniu classes**

Delete:
- `QiniuAvatarStorageProvider`
- `QiniuProperties`

**Step 4: Update DTO docs**

Modify `AvatarUploadTokenResponse` JavaDoc:
- provider comment from `local/qiniu` → `local/r2`

**Step 5: Run full backend tests**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -am test -q`

Expected:
- PASS.

**Step 6: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add -A backend/community-bootstrap`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"chore(user): remove qiniu storage support\"`

---

### Task 7: Update deploy env + compose env injection for R2

**Files:**
- Modify: `deploy/.env.example`
- Modify: `deploy/docker-compose.yml`

**Step 1: Update `deploy/.env.example`**

Changes:
- Replace qiniu mention in avatar section with r2
- Add `R2_*` variables with comments

**Step 2: Update `deploy/docker-compose.yml`**

Changes (service `community-app`):
- Remove `QINIU_*` env passthrough
- Add:
  - `R2_ENDPOINT=${R2_ENDPOINT:-}`
  - `R2_ACCESS_KEY=${R2_ACCESS_KEY:-}`
  - `R2_SECRET_KEY=${R2_SECRET_KEY:-}`
  - `R2_BUCKET_NAME=${R2_BUCKET_NAME:-}`
  - `R2_REGION=${R2_REGION:-auto}`
  - `R2_PATH_STYLE=${R2_PATH_STYLE:-true}`

**Step 3: Validate compose config**

Run:
- `cd .worktrees/avatar-storage-r2 && docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`

Expected:
- Exit code 0.

**Step 4: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add deploy/.env.example deploy/docker-compose.yml`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"docs(deploy): switch avatar storage env from qiniu to r2\"`

---

### Task 8: Update frontend to remove qiniu direct upload branch

**Files:**
- Modify: `frontend/src/views/SettingsView.vue`

**Step 1: Simplify upload logic**

Changes:
- Remove `uploadToQiniu` function
- Remove template text for `token.provider === 'qiniu'`
- Treat `provider === 'local' || provider === 'r2'` as server-side upload
- Preview URL always uses `/files/${fileName}` (or absolute when `baseURL` is set), since `/files/**` is unified

**Step 2: Run frontend unit tests**

Run:
- `cd .worktrees/avatar-storage-r2/frontend && npm test`

Expected:
- Exit code 0 (PASS). If no tests exist, ensure at least `npm run build` passes.

**Step 3: Commit**

Run:
- `cd .worktrees/avatar-storage-r2 && git add frontend/src/views/SettingsView.vue`
- `cd .worktrees/avatar-storage-r2 && git commit -m \"refactor(frontend): remove qiniu avatar upload path\"`

---

### Task 9: End-to-end verification checklist (manual)

**Files:** none

**Step 1: Local mode sanity**

- `USER_AVATAR_STORAGE=local`
- Upload avatar and verify:
  - `PUT /api/users/{id}/avatar` succeeds
  - `GET /files/avatar/...` returns 200

**Step 2: R2 mode sanity**

- `USER_AVATAR_STORAGE=r2` + set `R2_*`
- Upload avatar and verify:
  - object appears in bucket with key `avatar/{id}/{uuid}`
  - `GET /files/avatar/...` returns 200

**Step 3: Run full backend + frontend builds**

Run:
- `cd .worktrees/avatar-storage-r2 && mvn -pl backend/community-bootstrap -am test -q`
- `cd .worktrees/avatar-storage-r2/frontend && npm run build`

Expected:
- Both succeed.

