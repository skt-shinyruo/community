# Playwright Single 网盘公开分享 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 使用真实提取码完成公开网盘分享校验，返回 200 和 ticket，并从浏览器读取分享目录或明确的空目录状态。

**Architecture:** DrivePublicShareController 只做公开 HTTP 绑定和 visitor fingerprint，DriveShareApplicationService 负责 share/access 记录、密码校验、ticket 编解码和目录范围；Garage/OSS 只通过 DriveObjectStoragePort 参与下载 URL。公开校验失败必须保持明确错误，不把 storage 或 ticket 故障伪装成成功。

**Tech Stack:** Spring Boot、JUnit 5、MockMvc、Vue 3/Vitest、Playwright Test、Garage/OSS single Compose topology。

## Global Constraints

- 合法分享校验是 HTTP 200、有效 ticket 和分享条目/空状态；不得保留 expected 503。
- ticket 必须由 DriveShareApplicationService 通过 DriveShareTicketCodec 生成并校验，不能在 Controller 生成。
- 公开 GET/POST/entries/download-url 不得被错误地要求登录；分享范围和 ticket 校验必须保留。
- 只在实际部署契约证明配置不一致时修改 deployment/infrastructure；不能把 Controller 的错误状态改成 200。
- E2E 使用 SINGLE_TEST_RUN_ID 生成唯一文件夹名，并保留用户现有网盘/README 改动。

---

### Task 1: Add the Empty Folder and Valid Ticket Red Tests

**Files:**
- Modify: backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java
- Modify: frontend/src/views/DriveShareView.test.js
- Read: backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveShareApplicationService.java
- Read: frontend/src/views/DriveShareView.vue

- [ ] **Step 1: Add the application empty-directory test**

Create a folder with no children, create a share with password 1234 and a future expiry, verify it, then call listShareEntries:

~~~java
@Test
void emptyFolderShareShouldReturnValidTicketAndEmptyEntries() {
    TestDriveFixture fixture = TestDriveFixture.create();
    DriveShareApplicationService service = fixture.shareService();
    UUID ownerId = uuid(7);
    DriveEntryResult folder = fixture.entryService()
            .createFolder(new CreateDriveFolderCommand(ownerId, null, "Empty"));
    DriveShareResult share = service.createShare(new CreateDriveShareCommand(
            ownerId, folder.entryId(), "1234", Instant.parse("2026-05-10T00:00:00Z")
    ));

    DriveShareResult verified = service.verifyShare(
            new VerifyDriveShareCommand(share.shareToken(), "1234", "pw-empty")
    );

    assertThat(verified.ticket()).isNotBlank();
    assertThat(service.listShareEntries(share.shareToken(), verified.ticket(), null))
            .isEmpty();
}
~~~

Run:

~~~bash
cd backend
mvn -pl :community-app -Dtest=DriveShareApplicationServiceTest test
~~~

Expected before the implementation: the new test is absent or fails when the ticket/empty directory path is not wired.

- [ ] **Step 2: Add the frontend verified-state assertion**

In DriveShareView.test.js, retain the existing mocked gate and verification response, then assert after flushPromises:

~~~js
expect(verifyDriveShare).toHaveBeenCalledWith('token-a', '1234')
expect(wrapper.text()).toContain('验证成功')
expect(wrapper.text()).toContain('此文件夹为空')
expect(listDriveShareEntries).toHaveBeenCalledWith('token-a', 'ticket-a', '')
~~~

Also keep the invalid-password test, which must show an error message and must not render the verified directory.

### Task 2: Verify Application, Controller, and Ticket Boundaries

**Files:**
- Modify: backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DrivePublicShareControllerUnitTest.java
- Modify: backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/security/HmacDriveShareTicketCodec.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DriveObjectStoragePort.java

- [ ] **Step 1: Assert controller success and unauthenticated access**

Keep or extend the existing MockMvc test for:

~~~java
mockMvc.perform(post("/api/drive/shares/token-a/verify")
                .contentType("application/json")
                .content("{"password":"1234"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.shareToken").value("token-a"))
        .andExpect(jsonPath("$.data.ticket").value("ticket-a"));
~~~

Verify the captured command contains token-a, password 1234, and a 64-character visitorFingerprint; do not pass HttpServletRequest or a response type into application code.

- [ ] **Step 2: Assert application security behavior**

Retain tests for wrong password, inactive entry, invalid ticket, parent outside the share tree, and valid descendant file. Add assertions that successful verification records one successful access and that ticketCodec.issued() has one token. A wrong password must record false and return the existing DRIVE_SHARE_PASSWORD_INVALID business error.

- [ ] **Step 3: Run backend drive tests**

~~~bash
cd backend
mvn -pl :community-app -Dtest=DriveShareApplicationServiceTest,DrivePublicShareControllerUnitTest test
~~~

Expected: valid verification returns a nonblank ticket and empty/real entries; public endpoints return 200; invalid password/ticket/scope remains an explicit business failure.

### Task 3: Check the Single Deployment Ticket and Storage Configuration

**Files:**
- Read: deploy/.env.single.example
- Read: deploy/nacos/config/community-app.yaml
- Read: deploy/compose.runtime.services.single.yml
- Read: deploy/compose.infra.garage.single.yml

- [ ] **Step 1: Verify the secret is declared and injected**

Run:

~~~bash
rg -n "DRIVE_SHARE_TICKET_SECRET|ticket-secret|OSS_OBJECT_STORE_ACCESS_KEY|OSS_OBJECT_STORE_SECRET_KEY" deploy/.env.single.example deploy/nacos/config/community-app.yaml deploy/compose.runtime.services.single.yml
./deploy/deployment.sh config --topology single --no-observability --env-file deploy/.env.single.example > /tmp/community-single-drive-compose.yml
test -s /tmp/community-single-drive-compose.yml
~~~

Expected: the example env declares a nonblank DRIVE_SHARE_TICKET_SECRET, community-app receives it, and Nacos maps it to drive.share.ticket-secret. Do not print the secret value in CI logs.

- [ ] **Step 2: Verify Garage/OSS reachability from the app boundary**

With the single stack running, inspect only the relevant service logs and health:

~~~bash
./deploy/deployment.sh ps --topology single --no-observability
docker compose -p community-single logs --tail=200 community-app community-oss garage garage-init
~~~

The successful folder verify path must not require an object-storage call. If download URL generation is tested, the returned URL must be nonblank and the object id must belong to the shared folder. Fix endpoint, bucket, or access-key configuration in compose/OSS infrastructure if the service log shows a connectivity or signature failure; do not alter DrivePublicShareController to hide it.

- [ ] **Step 3: Verify the ticket boundary**

Use the application tests plus a real HTTP response to prove the same secret signs and validates the ticket:

~~~bash
rg -n "HmacDriveShareTicketCodec|DRIVE_SHARE_TICKET_SECRET|ticket-secret" backend/community-app/src/main/java backend/community-app/src/test/java deploy
~~~

Expected: only DriveShareApplicationService issues/validates tickets through DriveShareTicketCodec, and the deployed community-app uses the configured secret.

### Task 4: Put Public Share Verification in the Drive Playwright Flow

**Files:**
- Modify: tests/playwright-single/tests/05-drive.spec.ts
- Modify: tests/playwright-single/fixtures/test-data.ts
- Read: frontend/src/api/services/driveService.js
- Read: frontend/src/views/DriveView.vue
- Read: frontend/src/views/DriveShareView.vue

- [ ] **Step 1: Capture a generated public URL**

Keep the existing bbb folder create/share flow and run-scoped retainedShareFolder. After 分享链接已生成, extract the URL and fail when it is empty:

~~~ts
const shareUrl = await page.evaluate((baseUrl) => {
  const candidate = document.body.innerText
    .split(/\s+/)
    .find(value => value.startsWith(baseUrl + '/#/drive/s/'))
  return candidate || ''
}, webBaseUrl.replace(/\/$/, ''))
expect(shareUrl).toMatch(/\/drive\/s\/[A-Za-z0-9_-]+$/)
~~~

Use the existing webBaseUrl helper and do not hardcode the default port.

- [ ] **Step 2: Verify with the real extraction code**

Navigate to the public URL, wait for the verify request, and assert its successful JSON contract:

~~~ts
const verifyResponsePromise = page.waitForResponse((response) => {
  const url = new URL(response.url())
  return response.request().method() === 'POST'
    && url.pathname.endsWith('/verify')
})
await page.goto(shareUrl)
await page.getByRole('textbox').fill(data.shareCode)
await page.getByRole('button', { name: '访问分享' }).click()
const verifyResponse = await verifyResponsePromise
expect(verifyResponse.status()).toBe(200)
const verifyBody = await verifyResponse.json()
expect(verifyBody.data?.ticket).toEqual(expect.any(String))
expect(verifyBody.data.ticket).not.toBe('')
await expect(page.getByText('验证成功')).toBeVisible()
~~~

The page must then show either a drive-share-entry-list item or the explicit text 此文件夹为空. The automatic entries request must also return 200 under the shared audit.

- [ ] **Step 3: Run the focused E2E**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/05-drive.spec.ts
~~~

Expected before the infrastructure/config fix: the old behavior fails on the 503 response or a missing ticket; after the fix, verify and entries are 200 and no audit error exists.

### Task 5: Remove the Stale Known-Issue Surface and Verify All Drive Contracts

**Files:**
- Delete: tests/playwright-single/tests/99-known-issues.spec.ts
- Modify: tests/playwright-single/fixtures/test-data.ts
- Modify: tests/playwright-single/README.md
- Modify: docs/handbook/testing.md

- [ ] **Step 1: Remove only stale data**

Delete knownIssueShareFolder and any known-issue-only request code. Keep shareCode, retainedShareFolder, and all normal Drive create/rename/trash/share data. Do not remove invalid-password coverage from frontend or backend unit tests.

- [ ] **Step 2: Scan for the old 503 meaning**

~~~bash
rg -n "drive.*503|verify.*503|toBe\(503\)|knownIssueShareFolder|expected.*503|test:known" tests/playwright-single frontend/src backend/community-app/src/test docs/handbook
~~~

Expected: no stale expected-503 assertion remains; negative tests may still assert a named business exception for invalid password/ticket.

- [ ] **Step 3: Run frontend/backend/Playwright checks**

~~~bash
cd frontend
npm test -- src/api/services/driveService.test.js src/views/DriveShareView.test.js src/views/DriveView.test.js
cd ../backend
mvn -pl :community-app -Dtest=DriveShareApplicationServiceTest,DrivePublicShareControllerUnitTest test
cd ..
npm --prefix tests/playwright-single run test:regression -- tests/05-drive.spec.ts
~~~

Expected: unit, controller, and deployed public-share verification all pass.

- [ ] **Step 4: Verify the final content contract**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/05-drive.spec.ts
npm --prefix tests/playwright-single run report
rg -n "验证成功|ticket|此文件夹为空|drive/s/" tests/playwright-single/tests/05-drive.spec.ts tests/playwright-single/reports/latest-results.json
~~~

Expected: the report contains the successful Drive public share flow, not a known-issue reproduction.
