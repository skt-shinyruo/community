# Playwright Single Drive and IM Capability Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cover every user-reachable private/public drive operation and IM conversation operation with browser-only Playwright journeys, including upload, recovery, revocation, realtime delivery, read state, reconnect, and block policy.

**Architecture:** Drive data is created by visible folder and file-picker controls in the disposable topology. The current ambiguous move action becomes a visible destination browser rather than relying on a hidden target ID. IM tests create conversations from the profile's visible message link, use only the product-created WebSocket, and assert inbox/thread state rendered by the product.

**Tech Stack:** Playwright Test, Vue 3/Vitest, browser File API, Drive/OSS services, IM Core, IM Gateway, realtime WebSocket service, Docker Compose single topology.

## Global Constraints

- Do not use direct HTTP, product API clients, database storage, object-store clients, or API-based assertions from Playwright specs or fixtures. `scripts/health-check.mjs` remains the only HTTP probe exception.
- Use visible product UI for upload, download initiation, public-share validation, and IM interactions. A `waitForResponse` listener may observe a UI-triggered request only; do not parse its body.
- Every file, folder, share, and message name includes `SINGLE_TEST_RUN_ID`. Data is created in the test that consumes it and never relies on another spec's state.
- Do not hide a capability behind a test hook. Every frontend change must give users a visible, accessible control and preserve named service ownership.
- Keep Chromium-only serial execution with zero retries. After reading the visible share URL and deriving `shareToken`, the invalid-share-password case calls `await allowExpectedHttpError(page, entry, performUiAction, assertVisible)` with method `POST`, the concrete path `/api/drive/shares/${shareToken}/verify`, and status `403`; the callbacks click `访问分享` and assert visible `提取码错误`. Before reopening the revoked share, it uses the same one-shot wrapper for method `GET`, path `/api/drive/shares/${shareToken}`, status `404`, navigation to the visible link, and `分享链接不可用`. The blocked-message case uses no HTTP exception because it is a WebSocket policy rejection and asserts its visible send-failure feedback.
- Browser offline simulation is allowed only with Playwright's browser-context network control to exercise UI-established WebSocket recovery. It must not make or inspect HTTP requests.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `tests/playwright-single/tests/06-drive.spec.ts` | Private drive, upload, search, move, trash, download initiation, public share, verification, and revocation journeys. |
| `tests/playwright-single/tests/08-im.spec.ts` | Browser-created IM session, inbox, delivery, read state, reconnect, and block policy journeys. |
| `tests/playwright-single/fixtures/test-data.ts` | Run-scoped drive names and IM message text. |
| `frontend/src/views/DriveView.vue` | Accessible move-destination dialog and visible download-start outcome. |
| `frontend/src/views/DriveView.test.js` | Unit coverage for destination browsing, move call, and download status. |
| `frontend/src/views/DriveShareView.test.js` | Unit coverage for public-share password gating and entry visibility. |
| `frontend/src/views/ConversationDetailView.test.js` | Unit coverage retained for thread state and visible send failure. |

## Task 1: Make Drive Move and Download Outcomes Visible to a User

**Files:**
- Modify: `frontend/src/views/DriveView.vue`
- Modify: `frontend/src/views/DriveView.test.js`
- Modify: `frontend/src/views/driveState.test.js`

**Interfaces:**
- Produces a visible `移动` control that opens a `role="dialog"` destination browser named `选择移动目标` with root selection, folder navigation, `确认移动`, and `取消`.
- Produces visible status `已打开下载链接` after a successful browser download action.

- [ ] **Step 1: Write failing move-dialog and download-status unit tests**

Mock root entries `source.txt` and `Destination`, then mock `listDriveEntries({ parentId: 'destination-id' })`. Select `source.txt`, click `移动`, enter `Destination`, select `移动到此文件夹`, click `确认移动`, and assert `moveDriveEntry('source-id', { targetParentId: 'destination-id' })`. Add a second test which clicks `下载` and asserts the rendered page includes `已打开下载链接`.

- [ ] **Step 2: Run the red drive-view tests**

Run:

```bash
npm --prefix frontend test -- src/views/DriveView.test.js src/views/driveState.test.js
```

Expected: FAIL because `移动到当前目录` has no selectable destination and download has no visible completion state.

- [ ] **Step 3: Implement a user-facing destination browser**

Replace `移动到当前目录` with `移动`. Store the selected source separately, open a modal at root, call the existing `listDriveEntries({ parentId })` while the user enters folders, and show only folder rows as targets. On `确认移动`, call `moveDriveEntry(source.entryId, { targetParentId })`, close the dialog, reload, and show `条目已移动`.

After `window.open(data.url, '_blank', 'noopener,noreferrer')` succeeds in `downloadSelected`, set `statusMessage` to `已打开下载链接`. Preserve the presigned URL service and backend contract.

- [ ] **Step 4: Run focused frontend tests**

Run:

```bash
npm --prefix frontend test -- src/views/DriveView.test.js src/views/driveState.test.js
```

Expected: PASS; a user chooses a target folder and sees download initiation without treating a URL as a test fixture.

- [ ] **Step 5: Commit the drive usability slice**

```bash
git add frontend/src/views/DriveView.vue frontend/src/views/DriveView.test.js frontend/src/views/driveState.test.js
git commit -m "feat(drive): expose move destination and download state"
```

## Task 2: Cover Private Drive, Upload, Search, Recovery, and Public Shares

**Files:**
- Modify: `tests/playwright-single/tests/06-drive.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Modify: `frontend/src/views/DriveShareView.test.js`
- Test: `tests/playwright-single/tests/06-drive.spec.ts`

**Interfaces:**
- Produces tags `@cap:drive.space-list`, `@cap:drive.folder-move-search`, `@cap:drive.upload-download`, `@cap:drive.trash-recovery`, `@cap:drive.share-verify`, and `@cap:drive.share-revoke`.
- Produces `data.driveSourceFolder`, `data.driveDestinationFolder`, `data.driveUploadedFile`, `data.driveMovedFile`, `data.driveShareFolder`, `data.driveShareChildFile`, and `data.shareCode`.
- Consumes `allowExpectedHttpError(page, entry, performUiAction, assertVisible)` from the shared test fixture for the two dynamic public-share denials only, and `newAuditedContext()` for the second IM actor.

- [ ] **Step 1: Write failing private-drive browser cases**

As `bbb`, open `/drive`, assert quota plus `我的文件`, create source/destination folders, and upload a browser `File` named `data.driveUploadedFile` into the source through the visible `上传` input. Assert `已上传 1 个文件`, use the `移动` dialog to place it in destination, and assert both `条目已移动` and the file name in that folder.

Use the visible `搜索文件` input to locate the moved file, rename it to `data.driveMovedFile`, and assert the result. Click `下载` and assert `已打开下载链接`; do not inspect the destination URL or make a storage request.

- [ ] **Step 2: Run the private-drive case to verify failure**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/06-drive.spec.ts
```

Expected: FAIL until UI-created folders/files, move control, status assertions, and tags exist.

- [ ] **Step 3: Add trash, restore, and permanent-delete browser cases**

Create a run-scoped folder, select it, click `删除`, and assert `条目已移至回收站`. Switch to `回收站`, click `恢复`, return to `我的文件`, and assert the folder. Delete it again, select it in `回收站`, click `彻底删除`, and assert `条目已彻底删除` plus absence from the visible list.

- [ ] **Step 4: Add public-share verification and revocation cases**

Create a share folder containing a run-scoped uploaded child file, select it, open `分享`, enter `data.shareCode`, and click `生成分享链接`. Read visible `.drive-share-link` text through a locator, open it in a fresh page, enter an incorrect code, and assert visible password feedback. Enter the correct code, assert `验证成功` and the child file in the public list, then invoke its visible download action and assert its UI outcome.

Read the visible share link into `shareUrl`, derive `shareToken` from its browser URL, and wrap the wrong-password UI submit as follows:

```ts
await allowExpectedHttpError(
  page,
  {
    method: 'POST',
    path: `/api/drive/shares/${shareToken}/verify`,
    status: 403
  },
  () => page.getByRole('button', { name: '访问分享' }).click(),
  () => expect(page.getByText('提取码错误')).toBeVisible()
)
```

Do not parse a response body. Return to the owner page, click `撤销`, and assert `分享已撤销`. Wrap reopening the public link in the exact revocation response:

```ts
await allowExpectedHttpError(
  page,
  {
    method: 'GET',
    path: `/api/drive/shares/${shareToken}`,
    status: 404
  },
  () => page.goto(shareUrl),
  () => expect(page.getByText('分享链接不可用')).toBeVisible()
)
```

The navigation is a browser-visible route action; assert no response body and no direct request client is used.

- [ ] **Step 5: Run drive and frontend share tests**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/06-drive.spec.ts
npm --prefix frontend test -- src/views/DriveView.test.js src/views/DriveShareView.test.js src/views/driveState.test.js src/api/services/driveService.test.js
```

Expected: private lifecycle and public-share security are proven from rendered pages with no direct storage/backend operation.

- [ ] **Step 6: Commit drive coverage**

```bash
git add tests/playwright-single/tests/06-drive.spec.ts tests/playwright-single/fixtures/test-data.ts frontend/src/views/DriveShareView.test.js
git commit -m "test(e2e): cover drive user capabilities"
```

## Task 3: Cover IM Conversations, Read State, Reconnect, and Block Policy

**Files:**
- Modify: `tests/playwright-single/tests/08-im.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Modify: `frontend/src/views/ConversationDetailView.test.js`
- Modify: `frontend/src/views/ConversationsView.test.js`
- Modify: `frontend/src/im/imRealtimeClient.test.js`
- Test: `tests/playwright-single/tests/08-im.spec.ts`

**Interfaces:**
- Produces tags `@cap:im.inbox`, `@cap:im.send-receive`, `@cap:im.read-state`, `@cap:im.reconnect`, and `@cap:im.block-policy`.
- Produces `data.imMessageFromA`, `data.imMessageFromB`, and `data.imBlockedMessage`, each including the run ID.

- [ ] **Step 1: Write the failing two-user realtime journey**

Open `aaa`'s profile as `bbb` and click visible `发私信`; do not construct a conversation ID in test code. Wait for `实时已就绪`, fill `conversation-message` with `data.imMessageFromB`, click `发送消息`, and assert the message in `bbb`'s thread. Create `aaa`'s second login session with `newAuditedContext()`, log in through its visible UI, open `/messages`, assert an incoming thread with the message and unread state, open it, then assert the message and `线程已同步` after returning to the inbox. Do not call `browser.newContext()` directly.

- [ ] **Step 2: Run the realtime case to verify failure**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/08-im.spec.ts
```

Expected: FAIL until profile entry, realtime wait, message content, and visible read state are covered.

- [ ] **Step 3: Add bounded browser reconnect behavior**

With `实时已就绪` visible, call `context.setOffline(true)` and assert `实时未连接`. Call `context.setOffline(false)`, then use a bounded 15-attempt UI wait that reloads only the conversation page at two-second intervals until `实时已就绪` returns. Send `data.imMessageFromA` after recovery and assert it renders. Do not retry or inspect a backend request.

- [ ] **Step 4: Add reversible block-policy behavior**

As `bbb`, visit `aaa`'s visible profile, click `屏蔽`, and assert `已屏蔽`. In `aaa`'s existing conversation, send `data.imBlockedMessage` and assert the visible send failure produced by the WebSocket policy. Return to `bbb`, click `已屏蔽` to unblock, and assert `屏蔽` returns. Use no HTTP exception throughout this case; the WebSocket rejection must be proven from UI feedback.

- [ ] **Step 5: Run IM and frontend realtime tests**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/08-im.spec.ts
npm --prefix frontend test -- src/views/ConversationsView.test.js src/views/ConversationDetailView.test.js src/views/conversationDetailState.test.js src/im/imRealtimeClient.test.js src/api/services/imCoreChatService.test.js
```

Expected: delivery, inbox/read projection, reconnect, and block rejection are visible browser outcomes.

- [ ] **Step 6: Commit IM coverage**

```bash
git add tests/playwright-single/tests/08-im.spec.ts tests/playwright-single/fixtures/test-data.ts frontend/src/views/ConversationDetailView.test.js frontend/src/views/ConversationsView.test.js frontend/src/im/imRealtimeClient.test.js
git commit -m "test(e2e): cover IM user capabilities"
```
