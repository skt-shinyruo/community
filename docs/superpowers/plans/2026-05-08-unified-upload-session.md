# Unified Upload Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move browser-facing file upload flows to a unified upload-session contract so frontend code executes upload instructions without knowing storage providers such as local, R2, OSS, S3, Garage, or buckets.

**Architecture:** `community-oss` remains the object storage owner and hides storage-provider details behind its existing `ObjectStore` infrastructure port. Business domains expose owner-specific upload-session endpoints that return a generic browser upload instruction shape; the frontend calls a shared upload-session client that knows HTTP mechanics only. The first implementation migrates avatar upload end-to-end and leaves the reusable contract ready for later post images, attachments, admin imports, and IM files.

**Tech Stack:** Vue 3, Vitest, Spring Boot MVC, Java records/DTOs, Maven, existing `community-oss-client`.

---

## Scope

This plan intentionally implements the first vertical slice: avatar upload plus reusable frontend upload-session client. It does not migrate future upload surfaces that do not exist yet in the current frontend. Later domains should reuse the same response shape and frontend client instead of creating provider-specific branches.

## Target Browser Contract

Business endpoints return this frontend-facing shape:

```json
{
  "uploadId": "uuid-or-server-token",
  "fileKey": "opaque-server-file-key",
  "upload": {
    "url": "/api/users/{userId}/avatar/upload",
    "method": "POST",
    "fileField": "file",
    "fields": {
      "fileKey": "opaque-server-file-key"
    },
    "headers": {}
  },
  "constraints": {
    "maxBytes": 2097152,
    "mimeTypes": ["image/jpeg", "image/png", "image/webp", "image/gif"]
  },
  "expiresAt": "2026-05-08T12:00:00Z"
}
```

Rules:

- Frontend must not read or branch on `provider`.
- Frontend treats `fileKey` as opaque and only sends it back where instructed.
- Frontend does not construct `/files/**` preview URLs from `fileKey`.
- Upload execution is driven by `upload.url`, `upload.method`, `upload.fileField`, `upload.fields`, and `upload.headers`.
- `GET /avatar/upload-token` is replaced by `POST /avatar/upload-sessions` for new frontend code because session creation mutates server-side ticket state.

## Files

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarUploadTokenResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/AvatarUploadTokenResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UpdateAvatarRequest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapterTest.java`
- Create: `frontend/src/api/uploadSession.js`
- Create: `frontend/src/api/uploadSession.test.js`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/SettingsView.test.js`
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/business-logic/user.md`
- Modify: `docs/superpowers/specs/2026-05-07-community-oss-service-design.md`

## Task 1: Backend Avatar Upload Session Contract

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarUploadTokenResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapter.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapterTest.java`

- [ ] **Step 1: Rename the application result concept in tests first**

Change `UserAvatarApplicationServiceTest#createUploadTokenShouldDelegateToAvatarStoragePort` to expect a provider-free upload session result. Use this exact constructor shape after the record is changed:

```java
AvatarUploadTokenResult session = new AvatarUploadTokenResult(
        "upload-session-id",
        "avatar/" + userId + "/0123456789abcdef0123456789abcdef",
        "/api/users/" + userId + "/avatar/upload",
        "POST",
        "file",
        "fileKey",
        2_097_152L,
        "image/png;image/jpeg",
        Instant.parse("2026-05-08T12:00:00Z")
);
when(avatarStoragePort.createUploadToken(userId)).thenReturn(session);

AvatarUploadTokenResult result = service.createUploadToken(userId, userId);

assertThat(result).isEqualTo(session);
```

Add `import java.time.Instant;`.

- [ ] **Step 2: Run the focused application test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=UserAvatarApplicationServiceTest test
```

Expected: compilation failure because `AvatarUploadTokenResult` still has `provider`, `uploadToken`, and `bucketUrl`.

- [ ] **Step 3: Change `AvatarUploadTokenResult` to upload-session semantics**

Replace the record fields in `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarUploadTokenResult.java` with:

```java
package com.nowcoder.community.user.application.result;

import java.time.Instant;

public record AvatarUploadTokenResult(
        String uploadId,
        String fileKey,
        String uploadUrl,
        String uploadMethod,
        String fileField,
        String fileKeyField,
        long maxBytes,
        String mimeLimit,
        Instant expiresAt
) {
}
```

Keep the class name for this task to minimize churn. A later cleanup can rename it to `AvatarUploadSessionResult`.

- [ ] **Step 4: Update `OssAvatarStorageAdapter#createUploadToken`**

Return provider-free upload-session data:

```java
return new AvatarUploadTokenResult(
        response.sessionId() == null ? "" : response.sessionId().toString(),
        fileName,
        "/api/users/" + userId + "/avatar/upload",
        "POST",
        "file",
        "fileKey",
        MAX_AVATAR_BYTES,
        MIME_LIMIT,
        response.expiresAt()
);
```

- [ ] **Step 5: Rename upload method parameters from `fileName` to `fileKey` where they are API-facing**

In `AvatarStoragePort`, `UserAvatarApplicationService`, and `OssAvatarStorageAdapter`, rename method parameters and local variables from `fileName` to `fileKey` for upload/update validation paths. Do not change the internal Redis key logic except variable names.

Keep generated keys under the current `avatar/{userId}/{uuid}` format inside the backend; that key remains an opaque `fileKey` to frontend callers.

- [ ] **Step 6: Update `OssAvatarStorageAdapterTest`**

Replace:

```java
assertThat(token.provider()).isEqualTo("oss");
assertThat(token.fileName()).startsWith("avatar/" + userId + "/");
assertThat(adapter.buildAvatarUrl(token.fileName())).isEqualTo(
        "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
);
```

with:

```java
assertThat(token.uploadId()).isEqualTo(sessionId.toString());
assertThat(token.fileKey()).startsWith("avatar/" + userId + "/");
assertThat(token.uploadUrl()).isEqualTo("/api/users/" + userId + "/avatar/upload");
assertThat(token.uploadMethod()).isEqualTo("POST");
assertThat(token.fileField()).isEqualTo("file");
assertThat(token.fileKeyField()).isEqualTo("fileKey");
assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-05-07T00:15:00Z"));
assertThat(adapter.buildAvatarUrl(token.fileKey())).isEqualTo(
        "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
);
```

Also call `adapter.upload(userId, token.fileKey(), ...)`.

- [ ] **Step 7: Run backend focused tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest' test
```

Expected: PASS for the two focused tests.

## Task 2: Controller DTO and New Session Endpoint

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/AvatarUploadTokenResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UpdateAvatarRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`

- [ ] **Step 1: Change controller logging test first**

In `UserControllerLoggingTest#uploadTokenShouldLogSecurityEventWithoutUploadTokenMaterial`, replace the result construction with:

```java
when(avatarStoragePort.createUploadToken(userId))
        .thenReturn(new AvatarUploadTokenResult(
                "secret-upload-session",
                fileName,
                "/api/users/" + userId + "/avatar/upload",
                "POST",
                "file",
                "fileKey",
                2_097_152L,
                "image/png;image/jpeg",
                Instant.parse("2026-05-08T12:00:00Z")
        ));
```

Assert:

```java
assertThat(result.getData().getUploadId()).isEqualTo("secret-upload-session");
assertThat(result.getData().getFileKey()).isEqualTo(fileName);
assertThat(result.getData().getUpload()).isNotNull();
assertThat(result.getData().getUpload().getUrl()).isEqualTo("/api/users/" + userId + "/avatar/upload");
assertThat(output.getAll())
        .contains("community.category=security")
        .contains("community.action=avatar_upload_session")
        .contains("community.outcome=success")
        .contains("user.id=" + userId)
        .contains("community.target_type=user")
        .contains("community.target_id=" + userId)
        .contains("community.avatar_file_key=" + fileName)
        .doesNotContain("community.avatar_provider")
        .doesNotContain("secret-upload-session");
```

Add `import java.time.Instant;`.

- [ ] **Step 2: Run controller logging test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=UserControllerLoggingTest test
```

Expected: compilation failure and/or assertion failure because the controller still exposes provider/token DTO fields and logs `avatar_upload_token`.

- [ ] **Step 3: Replace `AvatarUploadTokenResponse` fields with nested upload instructions**

Change `AvatarUploadTokenResponse` to fields:

```java
private String uploadId;
private String fileKey;
private UploadInstruction upload;
private Constraints constraints;
private String expiresAt;
```

Add nested static DTO classes with getters/setters:

```java
public static class UploadInstruction {
    private String url;
    private String method;
    private String fileField;
    private java.util.Map<String, String> fields = java.util.Map.of();
    private java.util.Map<String, String> headers = java.util.Map.of();
}

public static class Constraints {
    private long maxBytes;
    private java.util.List<String> mimeTypes = java.util.List.of();
}
```

Do not keep `provider`, `uploadToken`, or `bucketUrl` on this response.

- [ ] **Step 4: Add `fileKey` support to `UpdateAvatarRequest`**

Change `UpdateAvatarRequest` to expose `fileKey` instead of `fileName`:

```java
private String fileKey;

public String getFileKey() {
    return fileKey;
}

public void setFileKey(String fileKey) {
    this.fileKey = fileKey;
}
```

Do not keep the old getter/setter unless a compatibility requirement is explicitly added later.

- [ ] **Step 5: Add `POST /avatar/upload-sessions` and update upload/update params**

In `UserController`:

- Replace `@GetMapping("/{userId}/avatar/upload-token")` with `@PostMapping("/{userId}/avatar/upload-sessions")`.
- Rename the method to `createAvatarUploadSession`.
- Log action `avatar_upload_session`.
- Log `community.avatar_file_key`, not provider.
- Change upload param from `@RequestParam("fileName") String fileName` to `@RequestParam("fileKey") String fileKey`.
- Change update request usage from `request.getFileName()` to `request.getFileKey()`.

The mapper should build:

```java
AvatarUploadTokenResponse response = new AvatarUploadTokenResponse();
response.setUploadId(token.uploadId());
response.setFileKey(token.fileKey());

AvatarUploadTokenResponse.UploadInstruction upload = new AvatarUploadTokenResponse.UploadInstruction();
upload.setUrl(token.uploadUrl());
upload.setMethod(token.uploadMethod());
upload.setFileField(token.fileField());
upload.setFields(java.util.Map.of(token.fileKeyField(), token.fileKey()));
upload.setHeaders(java.util.Map.of());
response.setUpload(upload);

AvatarUploadTokenResponse.Constraints constraints = new AvatarUploadTokenResponse.Constraints();
constraints.setMaxBytes(token.maxBytes());
constraints.setMimeTypes(parseMimeTypes(token.mimeLimit()));
response.setConstraints(constraints);
response.setExpiresAt(token.expiresAt() == null ? "" : token.expiresAt().toString());
return response;
```

Add helper:

```java
private static java.util.List<String> parseMimeTypes(String mimeLimit) {
    if (mimeLimit == null || mimeLimit.isBlank()) {
        return java.util.List.of();
    }
    return java.util.Arrays.stream(mimeLimit.split(";"))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
}
```

- [ ] **Step 6: Run controller tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=UserControllerLoggingTest test
```

Expected: PASS.

## Task 3: Frontend Generic Upload Session Client

**Files:**
- Create: `frontend/src/api/uploadSession.js`
- Create: `frontend/src/api/uploadSession.test.js`

- [ ] **Step 1: Write tests for provider-free upload execution**

Create `frontend/src/api/uploadSession.test.js`:

```js
import { describe, expect, it, vi } from 'vitest'
import { executeUploadSession, normalizeUploadSession } from './uploadSession'

describe('uploadSession', () => {
  it('normalizes a backend upload session without provider fields', () => {
    const session = normalizeUploadSession({
      uploadId: 'session-1',
      fileKey: 'avatar/user/key',
      upload: {
        url: '/api/users/7/avatar/upload',
        method: 'POST',
        fileField: 'file',
        fields: { fileKey: 'avatar/user/key' },
        headers: {}
      },
      constraints: {
        maxBytes: 1024,
        mimeTypes: ['image/png']
      },
      expiresAt: '2026-05-08T12:00:00Z'
    })

    expect(session.uploadId).toBe('session-1')
    expect(session.fileKey).toBe('avatar/user/key')
    expect(session.upload.url).toBe('/api/users/7/avatar/upload')
    expect(session.upload.method).toBe('POST')
    expect(session.upload.fileField).toBe('file')
    expect(session.upload.fields).toEqual({ fileKey: 'avatar/user/key' })
    expect(session.constraints.mimeTypes).toEqual(['image/png'])
  })

  it('posts multipart form data using generic upload instructions', async () => {
    const http = { post: vi.fn().mockResolvedValue({ data: { code: 0, data: {}, traceId: 'trace-upload' } }) }
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const session = normalizeUploadSession({
      upload: {
        url: '/api/users/7/avatar/upload',
        method: 'POST',
        fileField: 'file',
        fields: { fileKey: 'avatar/key' },
        headers: { 'X-Test': '1' }
      }
    })

    const result = await executeUploadSession({ http, session, file })

    expect(result.traceId).toBe('trace-upload')
    expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload', expect.any(FormData), {
      headers: { 'X-Test': '1' }
    })
    const form = http.post.mock.calls[0][1]
    expect(form.get('fileKey')).toBe('avatar/key')
    expect(form.get('file')).toBe(file)
  })

  it('rejects unsupported methods before sending a request', async () => {
    const http = { post: vi.fn() }
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const session = normalizeUploadSession({
      upload: {
        url: '/api/users/7/avatar/upload',
        method: 'PUT',
        fileField: 'file',
        fields: {}
      }
    })

    await expect(executeUploadSession({ http, session, file })).rejects.toThrow('暂不支持的上传方法')
    expect(http.post).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run frontend upload-session test and verify it fails**

Run:

```bash
cd frontend
npm test -- src/api/uploadSession.test.js
```

Expected: FAIL because `frontend/src/api/uploadSession.js` does not exist.

- [ ] **Step 3: Implement `uploadSession.js`**

Create `frontend/src/api/uploadSession.js`:

```js
import { unwrapResultBody } from './result'

export function normalizeUploadSession(raw = {}) {
  const upload = raw.upload || {}
  const constraints = raw.constraints || {}
  return {
    uploadId: String(raw.uploadId || ''),
    fileKey: String(raw.fileKey || ''),
    upload: {
      url: String(upload.url || ''),
      method: String(upload.method || 'POST').toUpperCase(),
      fileField: String(upload.fileField || 'file'),
      fields: { ...(upload.fields || {}) },
      headers: { ...(upload.headers || {}) }
    },
    constraints: {
      maxBytes: Number(constraints.maxBytes || 0),
      mimeTypes: Array.isArray(constraints.mimeTypes) ? constraints.mimeTypes.map(String) : []
    },
    expiresAt: String(raw.expiresAt || '')
  }
}

export async function executeUploadSession({ http, session, file, operation = 'Upload File' }) {
  const normalized = normalizeUploadSession(session)
  if (!normalized.upload.url) {
    throw new Error('upload.url 缺失，请重新获取上传参数')
  }
  if (normalized.upload.method !== 'POST') {
    throw new Error('暂不支持的上传方法，请重新获取上传参数')
  }

  const form = new FormData()
  Object.entries(normalized.upload.fields).forEach(([key, value]) => {
    form.append(key, value)
  })
  form.append(normalized.upload.fileField || 'file', file)

  const resp = await http.post(normalized.upload.url, form, {
    headers: normalized.upload.headers
  })
  return unwrapResultBody(resp.data, operation)
}
```

- [ ] **Step 4: Run frontend upload-session tests**

Run:

```bash
cd frontend
npm test -- src/api/uploadSession.test.js
```

Expected: PASS.

## Task 4: Migrate Settings Avatar Upload to Generic Session

**Files:**
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/SettingsView.test.js`

- [ ] **Step 1: Rewrite SettingsView tests around upload sessions**

In `SettingsView.test.js`, change the mocked session response to:

```js
http.post.mockImplementation((url, body) => {
  if (url === '/api/users/7/avatar/upload-sessions') {
    return Promise.resolve(okResult({
      uploadId: 'session-1',
      fileKey: 'avatar-upload-key',
      upload: {
        url: '/api/users/7/avatar/upload',
        method: 'POST',
        fileField: 'file',
        fields: { fileKey: 'avatar-upload-key' },
        headers: {}
      },
      constraints: {
        maxBytes: 256000,
        mimeTypes: ['image/png', 'image/jpeg']
      },
      expiresAt: '2026-05-08T12:00:00Z'
    }, 'trace-session'))
  }
  if (url === '/api/users/7/avatar/upload') {
    return Promise.resolve(okResult({}, 'trace-upload'))
  }
  return Promise.resolve(okResult({}, 'trace-post'))
})
```

Remove assertions that the page contains `OSS 服务` or `图片会通过后端代理上传到 OSS 服务`.

Update upload assertions:

```js
expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload', expect.any(FormData), {
  headers: {}
})
const form = http.post.mock.calls.find(([url]) => url === '/api/users/7/avatar/upload')[1]
expect(form.get('file')).toBe(file)
expect(form.get('fileKey')).toBe('avatar-upload-key')
expect(http.put).toHaveBeenCalledWith('/api/users/7/avatar', { fileKey: 'avatar-upload-key' })
```

Add a regression test:

```js
it('ignores storage provider fields returned by old servers', async () => {
  http.post.mockResolvedValueOnce(okResult({
    provider: 'unrecognized-provider',
    uploadId: 'session-1',
    fileKey: 'avatar-upload-key',
    upload: {
      url: '/api/users/7/avatar/upload',
      method: 'POST',
      fileField: 'file',
      fields: { fileKey: 'avatar-upload-key' },
      headers: {}
    }
  }, 'trace-session'))
  http.post.mockResolvedValueOnce(okResult({}, 'trace-upload'))

  const wrapper = mountView()
  await findUiButton(wrapper, '获取上传参数').trigger('click')
  await flushPromises()

  const file = new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })
  await wrapper.getComponent(UiFileInput).vm.$emit('update:modelValue', file)
  await nextTick()
  await findUiButton(wrapper, '上传并保存').trigger('click')
  await flushPromises()

  expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload', expect.any(FormData), {
    headers: {}
  })
  expect(wrapper.text()).not.toContain('未知存储策略')
})
```

- [ ] **Step 2: Run SettingsView tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/views/SettingsView.test.js
```

Expected: FAIL because `SettingsView` still calls `GET /avatar/upload-token`, stores `provider`, and posts `fileName`.

- [ ] **Step 3: Update SettingsView state and loading**

Import:

```js
import { executeUploadSession, normalizeUploadSession } from '../api/uploadSession'
```

Replace `token` with:

```js
const uploadSession = reactive(normalizeUploadSession())
```

Update status display to use `uploadSession.fileKey`.

Change `loadToken` to call:

```js
const resp = await http.post(`/api/users/${auth.userId}/avatar/upload-sessions`)
const { data, traceId } = unwrapResultBody(resp.data, 'Create Avatar Upload Session')
emit('trace', traceId || '')
Object.assign(uploadSession, normalizeUploadSession(data || {}))
```

- [ ] **Step 4: Update preview and upload behavior**

Make preview local-file based:

```js
const localPreviewUrl = ref('')

const previewUrl = computed(() => localPreviewUrl.value)
const displayAvatarUrl = computed(() => previewUrl.value || currentAvatarUrl.value)
```

When `pickedFile` changes, create and revoke object URLs:

```js
watch(pickedFile, (file, previousFile, onCleanup) => {
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = ''
  }
  if (file) {
    const objectUrl = URL.createObjectURL(file)
    localPreviewUrl.value = objectUrl
    onCleanup(() => URL.revokeObjectURL(objectUrl))
  }
})
```

Update `uploadAndUpdate`:

```js
if (!pickedFile.value || !uploadSession.fileKey) return
const { traceId } = await executeUploadSession({
  http,
  session: uploadSession,
  file: pickedFile.value,
  operation: 'Upload Avatar'
})
emit('trace', traceId || '')
await updateAvatar(uploadSession.fileKey)
```

Update `updateAvatar` to send `{ fileKey }`.

- [ ] **Step 5: Remove provider UI copy**

Remove the storage-position meta item or change it to session status:

```vue
<div class="settings-upload-meta-item">
  <span class="settings-upload-label">上传会话</span>
  <strong>{{ uploadSession.fileKey ? '已获取' : '等待获取上传参数' }}</strong>
</div>
```

Replace upload note with provider-free copy:

```vue
<div class="settings-upload-note">
  <span>图片会按后端签发的上传会话提交，保存后同步到公开资料。</span>
</div>
```

- [ ] **Step 6: Run SettingsView tests**

Run:

```bash
cd frontend
npm test -- src/api/uploadSession.test.js src/views/SettingsView.test.js
```

Expected: PASS.

## Task 5: Backend/Frontend Integration Verification

**Files:**
- Modify as needed only if tests reveal missed references from prior tasks.

- [ ] **Step 1: Search for provider leakage in frontend upload code**

Run:

```bash
rg -n "provider|local|r2|oss|bucketUrl|uploadToken|fileName" frontend/src/views/SettingsView.vue frontend/src/views/SettingsView.test.js frontend/src/api/uploadSession.js frontend/src/api/uploadSession.test.js
```

Expected:

- No `provider`, `local`, `r2`, `oss`, `bucketUrl`, or `uploadToken` references in the listed frontend upload files.
- `fileName` may appear only as the browser `File.name` display in `UiFileInput`, not as the server upload key.

- [ ] **Step 2: Run focused backend tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest,UserControllerLoggingTest' test
```

Expected: PASS.

- [ ] **Step 3: Run frontend tests**

Run:

```bash
cd frontend
npm test -- src/api/uploadSession.test.js src/views/SettingsView.test.js
```

Expected: PASS.

## Task 6: Documentation Alignment

**Files:**
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/business-logic/user.md`
- Modify: `docs/superpowers/specs/2026-05-07-community-oss-service-design.md`

- [ ] **Step 1: Update docs to describe upload sessions**

Add or update the avatar upload flow to say:

```markdown
Avatar upload uses a browser-facing upload session:

1. The frontend requests `POST /api/users/{userId}/avatar/upload-sessions`.
2. `community-app` creates a user-owned upload ticket and prepares an OSS upload session through `community-oss-client`.
3. The response contains only generic upload instructions: `uploadId`, opaque `fileKey`, upload URL/method/form fields, constraints, and expiry.
4. The frontend executes those instructions and does not inspect storage provider, bucket, object-store mode, or physical path details.
5. The frontend confirms the avatar with `PUT /api/users/{userId}/avatar` and `{ "fileKey": "..." }`.
6. `community-app` resolves the final public URL through its storage port and stores the user header URL projection.
```

- [ ] **Step 2: Search docs for stale provider phrasing**

Run:

```bash
rg -n "avatar_provider|upload-token|upload token|provider|local/r2|Cloudflare R2|bucketUrl|uploadToken" docs/handbook docs/superpowers/specs/2026-05-07-community-oss-service-design.md
```

Expected: no stale browser-facing provider/upload-token wording for avatar upload. Storage-provider discussion may remain inside `community-oss` infrastructure sections only.

- [ ] **Step 3: Run final focused verification**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest,UserControllerLoggingTest' test
cd ../frontend
npm test -- src/api/uploadSession.test.js src/views/SettingsView.test.js
```

Expected: PASS.

## Rollout Notes

- This is a coordinated frontend/backend API change. Deploy `community-app` and frontend together.
- The old `GET /api/users/{userId}/avatar/upload-token` endpoint should not be used by new frontend code. Keep it only if a compatibility requirement is explicitly introduced.
- Future upload surfaces must return the same upload-session response shape and use `frontend/src/api/uploadSession.js`.
- Do not add frontend branches for `provider`, `bucket`, `local`, `r2`, `oss`, `s3`, `garage`, or physical object-store modes.

## Self-Review

- Spec coverage: Covers provider removal, generic upload instructions, avatar migration, test coverage, and docs alignment.
- Placeholder scan: No `TBD`, `TODO`, or undefined future implementation steps remain.
- Type consistency: The plan consistently uses `uploadId`, `fileKey`, `upload`, `constraints`, `fileField`, and `fields.fileKey`.
