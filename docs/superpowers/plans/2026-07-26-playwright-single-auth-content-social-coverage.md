# Playwright Single Auth and Community Capability Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cover every user-reachable auth, content, social, profile, notice, search, and user-visible growth capability through browser-only single-topology journeys.

**Architecture:** Use run-scoped UI-created users and content, switch identities within a single serial journey when a state transition crosses users, and observe eventual projections through rendered pages. Capability tags are added now; the final manifest plan binds those tags to source operations and CI.

**Tech Stack:** Playwright Test, Vue 3, MailHog browser UI, Spring auth/content/social/notice/search/growth services, Docker Compose single topology.

## Global Constraints

- Use only page navigation, locators, file inputs, browser contexts, and the visible MailHog inbox; do not import API clients or invoke direct HTTP from these specs/fixtures.
- Every expected HTTP rejection must use the exact one-shot `allowExpectedHttpError(page, { method, path, status }, performUiAction, assertVisible)` wrapper. It arms the UI-triggered response before the action and consumes exactly one matching response after the visible error/redirect assertion. Client-side validation that sends no request and WebSocket policy rejection use no exception.
- Use `data` values derived from `SINGLE_TEST_RUN_ID`; a retained post must be created in the same journey that consumes it.
- Do not let a block, password reset, role change, or deletion escape its owning journey and break later domains.

---

## Task 1: Add Full Authentication Journeys

**Files:**
- Modify: `tests/playwright-single/tests/01-auth.spec.ts`
- Modify: `tests/playwright-single/fixtures/auth.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Modify: `tests/playwright-single/fixtures/mailhog.ts`
- Test: `tests/playwright-single/tests/01-auth.spec.ts`

**Interfaces:**
- Consumes `mailhogBaseUrl`, `openResetLinkFromMailbox(page, recipient)`, `accounts`, `loginViaUi`, `uniqueName`, `allowExpectedHttpError`, and `newAuditedContext`.
- Produces tags `@cap:auth.login`, `@cap:auth.logout`, `@cap:auth.registration`, `@cap:auth.password-reset`, and `@cap:auth.session-restore`.

- [ ] **Step 1: Write failing registration and reset browser cases**

Add a serial registration test whose visible inputs are filled with:

```ts
const username = uniqueName('pw-auth-user').replace(/\s+/g, '-').toLowerCase()
const email = `${username}@example.test`
const password = 'PwAuth9!x'

await page.getByPlaceholder('请输入用户名').fill(username)
await page.getByPlaceholder('name@example.com').fill(email)
await page.getByPlaceholder('请输入密码').fill(password)
await page.getByPlaceholder('请输入验证码').fill('2468')
await page.getByRole('button', { name: '注册' }).click()
```

Assert the visible `开发 / 测试验证码` state appears. Fill `请输入重发所需的图形验证码` with `2468`, click `重新发送验证码`, assert `验证码已重新发送至` is visible, then read the replacement six-digit code from that same development state and submit it through `请输入邮箱验证码`. Assert the browser reaches `/posts` with the new username visible. Then use the visible password-reset page with the same email and fixed captcha, obtain the reset link through `openResetLinkFromMailbox`, enter a new valid password on the product reset page, and prove the old password is rejected while the new one logs in. Wrap the old-password login click in `await allowExpectedHttpError(page, { method: 'POST', path: '/api/auth/login', status: 401 }, performUiAction, assertVisible)`; `performUiAction` clicks the visible login button and `assertVisible` requires the invalid-credential message.

- [ ] **Step 2: Run the auth file and observe the red failure**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:smoke -- tests/01-auth.spec.ts
```

Expected: FAIL before the suite is implemented because no auth capability tags or MailHog browser helper exist.

- [ ] **Step 3: Implement login, logout, risk, registration, reset, and restore assertions**

Keep the existing three seeded-account login check, but add these visible outcomes:

- call logout through the topbar and assert the login route;
- submit an invalid password twice, assert `需要验证码` and the visible captcha input, fill visible `2468`, then log in successfully with a valid seeded account;
- after a successful login, reload a protected route in the same browser context and assert the session restores without returning to login;
- retain empty registration/reset validation tests and assert their exact messages;
- tag each test with the capability IDs above and `@regression`.

For the invalid-login test, wrap each real rejecting submit separately: wrap the wrong-password click with the exact `401` entry and an invalid-credential assertion, then wrap the visible bad-captcha submit with the exact `400` entry and its captcha feedback assertion. Do not arm two entries for one action. No other HTTP error is permitted.

- [ ] **Step 4: Run the focused auth regression**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/01-auth.spec.ts
```

Expected: registration verifies through the product UI, reset follows a browser-visible email link, and no direct test API call appears in the audit artifact.

- [ ] **Step 5: Commit auth coverage**

```bash
git add tests/playwright-single/tests/01-auth.spec.ts tests/playwright-single/fixtures/auth.ts tests/playwright-single/fixtures/test-data.ts tests/playwright-single/fixtures/mailhog.ts
git commit -m "test(e2e): cover authentication user journeys"
```

## Task 2: Cover Content Authoring, Media, Comments, and Author State Changes

**Files:**
- Modify: `tests/playwright-single/tests/02-content-social-profile.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Test: `tests/playwright-single/tests/02-content-social-profile.spec.ts`

**Interfaces:**
- Produces tags `@cap:content.feed`, `@cap:content.detail`, `@cap:content.taxonomy-tags`, `@cap:content.publish`, `@cap:content.media-upload`, `@cap:content.edit-delete`, `@cap:content.comment-reply`, and `@cap:content.bookmark`.
- Produces `data.retainedPostTitle`, `data.discardedPostTitle`, `data.retainedTag`, `data.commentBody`, `data.replyBody`, and `data.editedPostTitle`. Define `data.retainedTag` as `pw-${runId.replace(/[^a-z0-9]/gi, '').slice(-12).toLowerCase()}` so it remains run-scoped and below the 20-character tag limit.

- [ ] **Step 1: Write a failing authored-content journey**

Create one serial test as `aaa` that creates two posts through the composer. For the retained post, select the seeded `技术` category from `post-category`, enter `data.retainedTag`, use the file input to upload a small browser file payload, and wait for the visible `上传完成` state before clicking `发布`. Assert the title, paragraph content, `技术` category, tag, and rendered media on the detail page. Return to `/posts`, select `技术` in the visible `版块` filter, assert the retained title is still rendered, then click `清空` and assert the unfiltered feed returns.

Reopen the composer, focus an empty `post-tag-draft`, and assert `data.retainedTag` is offered as a visible `role=option` hot-tag suggestion. Type the first six characters of that tag and assert the same option remains visible before selecting it. This gives browser evidence for category loading, hot tags, and tag search rather than treating taxonomy as a page-load-only read. For the discarded post, use the author edit UI, assert the edited title is visible, then confirm author deletion and assert its deleted/absent state in the feed.

Use browser file input data, not a storage API:

```ts
await composer.locator('input[type="file"]').setInputFiles({
  name: `post-${runId}.txt`,
  mimeType: 'text/plain',
  buffer: Buffer.from(`post media ${runId}`)
})
await expect(composer.getByText('上传完成')).toBeVisible()
```

- [ ] **Step 2: Run the new content capability test to verify it fails**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/02-content-social-profile.spec.ts
```

Expected: FAIL until the retained/discarded data, locators, and browser assertions are added.

- [ ] **Step 3: Add comment, reply, edit, and bookmark assertions**

On the retained post, submit a top-level comment, open its reply action, submit a reply, edit the original comment through its visible edit control, and assert all resulting text in the thread. Click `收藏`, open `/bookmarks`, and assert the retained title in the rendered list. Return to its detail page, click `已收藏`, reopen `/bookmarks`, and assert `暂无收藏`; this covers both bookmark and unbookmark through visible state. Do not parse bookmark response JSON; if observing the UI-triggered request, assert only its HTTP status and then assert the page content.

Use no expected-error wrapper for this success journey. Any `4xx`/`5xx` is a product failure.

- [ ] **Step 4: Run content and existing frontend component tests**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/02-content-social-profile.spec.ts
npm --prefix frontend test -- src/views/PostsView.test.js src/views/PostDetailView.test.js src/components/posts/PostBlockEditor.test.js
```

Expected: authored state is only created through UI, the retained post is visible in bookmarks, and existing UI component contracts remain green.

- [ ] **Step 5: Commit content coverage**

```bash
git add tests/playwright-single/tests/02-content-social-profile.spec.ts tests/playwright-single/fixtures/test-data.ts
git commit -m "test(e2e): cover content authoring journeys"
```

## Task 3: Cover Social, Profile, Avatar, and Block Reversal Behavior

**Files:**
- Modify: `tests/playwright-single/tests/02-content-social-profile.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Test: `tests/playwright-single/tests/02-content-social-profile.spec.ts`

**Interfaces:**
- Produces tags `@cap:social.like`, `@cap:social.follow`, `@cap:social.follow-lists`, `@cap:social.block`, `@cap:social.blocked-feed`, `@cap:profile.read`, `@cap:profile.activity`, and `@cap:profile.avatar`.
- Creates and consumes its own run-scoped social post within the same browser test; it never depends on Task 2 or another test's retained content.

- [ ] **Step 1: Write failing cross-user social cases**

Create a run-scoped social post as `aaa` at the beginning of the same test. As `bbb`, open that visible post, click `点赞` and `关注作者`, add a run-scoped comment, and assert the active labels plus the rendered comment. Create the `aaa` actor with `newAuditedContext()`, log in through its visible UI, and use its audited page to open `/users/${accounts.aaa.userId}/followers` and assert `bbb`; use the same audited page for `/users/${accounts.bbb.userId}/followees` and assert `aaa`. Open `/users/${accounts.aaa.userId}` and assert the social-post title in recent posts; open `/users/${accounts.bbb.userId}` and assert the run-scoped comment in recent comments. Never call `browser.newContext()` directly.

Still as `bbb`, click `屏蔽作者`, assert `已屏蔽`, return to `/posts`, and assert the social-post title is absent while `已隐藏` is visible. Use `bbb`'s settings page to select a small image file through the visible avatar file input, submit `上传并保存`, and assert `头像已更新。`, the current `bbb` identity, and the changed rendered avatar source.

- [ ] **Step 2: Run the social capability test to verify it fails**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/02-content-social-profile.spec.ts
```

Expected: FAIL until the user switching, avatar payload, and visible relation assertions are implemented.

- [ ] **Step 3: Implement reversible social state verification**

After asserting block behavior, click the same visible control to unblock, assert `屏蔽` returns, reload `/posts`, and assert the social-post title is visible again. Click `取关作者`, assert `关注作者` returns, then reload both follower and followee pages and assert the counterpart is absent. Do not use a backend cleanup call. Add a negative self-action assertion where the UI disables or omits follow/block controls on the self profile.

- [ ] **Step 4: Run social/profile checks**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/02-content-social-profile.spec.ts
npm --prefix frontend test -- src/views/UserProfileView.test.js src/views/SettingsView.test.js src/views/userProfileSurface.test.js
```

Expected: relation and avatar state are browser-visible, reversible, and do not contaminate later journeys.

- [ ] **Step 5: Commit social/profile coverage**

```bash
git add tests/playwright-single/tests/02-content-social-profile.spec.ts tests/playwright-single/fixtures/test-data.ts
git commit -m "test(e2e): cover social and profile capabilities"
```

## Task 4: Cover Notice, Search, and User-Visible Growth Projections

**Files:**
- Create: `tests/playwright-single/tests/03-notice-search-growth.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Test: `tests/playwright-single/tests/03-notice-search-growth.spec.ts`

**Interfaces:**
- Produces tags `@cap:search.query`, `@cap:notice.list-read`, and `@cap:growth.profile-projection`.
- Uses a `waitForVisibleProjection(page, action)` helper that reloads the target UI at a bounded interval and throws the final visible text after 30 seconds.

- [ ] **Step 1: Write failing bounded projection cases**

Create a post from `aaa`, then use the search page's `search-keyword` input and `搜索` button to wait until its title appears. Trigger a like/comment/follow from `bbb`, then open `aaa`'s notice page and wait for a visible unread notification. Open the matching notice topic, click `标记本页已读`, and assert the page renders `已读` state. Finally open `aaa`'s profile and assert its visible growth/level summary is loaded after the qualifying content action.

Implement bounded UI polling as:

```ts
export async function waitForVisibleProjection(page, visit, expected: string) {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    await visit()
    if (await page.getByText(expected).first().isVisible().catch(() => false)) return
    await page.waitForTimeout(2_000)
  }
  throw new Error(`projection never became visible: ${expected}`)
}
```

- [ ] **Step 2: Run the projection cases to verify they fail**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/03-notice-search-growth.spec.ts
```

Expected: FAIL because the spec and bounded UI polling helper do not yet exist.

- [ ] **Step 3: Implement only user-visible eventual-consistency assertions**

Use `gotoHash`, `page.reload`, form controls, and rendered text. Do not poll `/api/search`, `/api/notices`, Kafka, or Redis. Keep retries finite and attach the final page screenshot/trace through normal Playwright failure artifacts. For empty/no-new-notification outcomes, fail the journey rather than accepting an empty state after the triggering action.

- [ ] **Step 4: Run projection and frontend state tests**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/03-notice-search-growth.spec.ts
npm --prefix frontend test -- src/views/SearchView.test.js src/views/NoticeDetailView.test.js src/views/NoticesView.test.js
```

Expected: each projection becomes visible via the product UI within the bounded window, with no unexpected audit error.

- [ ] **Step 5: Commit projection coverage**

```bash
git add tests/playwright-single/tests/03-notice-search-growth.spec.ts tests/playwright-single/fixtures
git commit -m "test(e2e): cover notice search and growth projections"
```
