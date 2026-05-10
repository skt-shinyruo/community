# Community Playwright Full Regression Matrix

This file is the project-specific source of truth for full browser regression of the `community` app. Execute it as a checklist. Do not silently skip an item; mark it `PASS`, `FAIL`, `BLOCKED`, or `NO_UI`, and include evidence for anything other than `PASS`.

`PASS` means the UI rendered, the expected request or WebSocket path behaved correctly, and the state change survived a realistic verification point such as refresh, list reload, detail reopen, recipient login, or admin lookup.

## Runtime invariants

- Repository root: the current `community` repository root.
- Frontend base URL: `http://localhost:12881`
- Gateway/API base URL: `http://localhost:12880`
- Health endpoint: `http://127.0.0.1:12880/actuator/health`
- Always browse with `http://localhost:12881`, not `127.0.0.1`, to avoid CORS, cookie, and runtime-config origin mismatches.
- Runtime endpoint resolution must be verified from `frontend/scripts/renderRuntimeConfig.mjs`, `frontend/src/config/endpointResolution.js`, and `frontend/src/config/runtimeConfig.js`: generated `dist/app-config.js`, `apiBaseUrl`, `imHttpBaseUrl`, the IM WebSocket bootstrap URL, and `/files/**` links must stay on the expected local gateway origin.
- Use run-specific data prefixes such as `pw-YYYYMMDDHHMMSS`.
- Use Playwright page actions for privileged workflows. Do not extract bearer tokens from storage or call privileged APIs directly to bypass the UI.
- A full run must use both desktop and one narrow mobile viewport. A targeted run may skip mobile only when the changed area is not layout-sensitive and the report says so.
- Prefer condition-based waits on visible UI state or network completion. Do not use fixed sleeps as the main readiness signal.

## Coverage freshness gate

Run this before browser execution and record the result in the final report.

- [ ] Run `node .agents/skills/community-playwright-regression/scripts/check-matrix-freshness.mjs` from repository root.
- [ ] Compare all route paths in `frontend/src/router/index.js` with the route inventory below. Any new route must be added to this matrix or marked `NO_UI` with a reason before the run can pass.
- [ ] Compare non-test files in `frontend/src/api/services` with the API/service coverage map. Any new service must have visible UI coverage, component/API coverage, or an explicit `NO_UI` reason.
- [ ] Compare direct HTTP/WebSocket calls outside `frontend/src/api/services` with the direct HTTP surface map. Any new direct call must be moved behind a service or explicitly accounted for.
- [ ] Compare `frontend/src/api/http.js`, `frontend/src/api/imCoreHttp.js`, `frontend/src/api/uploadSession.js`, `frontend/scripts/renderRuntimeConfig.mjs`, `frontend/src/config/endpointResolution.js`, and `frontend/src/config/runtimeConfig.js` with the client infrastructure coverage map.
- [ ] Compare critical backend endpoint families and controller inventory used outside direct route inventory: `RoomController`, `OssObjectController`, internal projection/reference controllers, upload/download `/files/**`, auth mail/captcha flows, IM unread, and market state-transition endpoints.
- [ ] Compare `frontend/src/router/navigation.js` with the navigation-shell checks. Any new nav item must be verified for visibility, active state, and role gating.
- [ ] Record whether the run is `full` or `targeted`. For targeted runs, name the changed files or feature area that scoped the run.

## Deployment checklist

- [ ] Run from repository root.
- [ ] Redeploy local single topology:

  ```bash
  ./deploy/deployment.sh up --topology single --no-observability --env-file deploy/.env.single.example -p community-single
  ```

- [ ] Confirm `docker ps` shows the `community-single-*` runtime containers up.
- [ ] Confirm `community-app`, `community-gateway`, `community-im-gateway`, `im-core`, `im-realtime`, `community-oss`, MySQL, Redis, Kafka, Elasticsearch, Garage, nginx, and MailHog are healthy or otherwise ready.
- [ ] Confirm `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] Confirm gateway routes proxy `community-app`, `im-core`, `im-realtime`, `community-oss`, and `/files/**` instead of leaking service-internal hostnames.
- [ ] Confirm `http://localhost:12881` loads the SPA shell.
- [ ] Confirm frontend runtime config is generated and loaded: `dist/app-config.js` contains `apiBaseUrl` and `imHttpBaseUrl` from `GATEWAY_PUBLIC_BASE_URL`, `FRONTEND_RUNTIME_API_BASE_URL`, or `FRONTEND_RUNTIME_IM_HTTP_BASE_URL`, and it is loaded before the app bundle.
- [ ] Open MailHog at `http://localhost:8025` when registration or password reset is in scope.
- [ ] Record any unhealthy supporting service, especially Garage, Elasticsearch, IM, OSS, MailHog, and nginx.

## Seed accounts

- `aaa / aaa` - regular user, id `00000000-0000-7000-8000-000000000001`
- `bbb / aaa` - second regular user, id `00000000-0000-7000-8000-000000000002`
- `admin / aaa` - admin user, id `00000000-0000-7000-8000-000000000003`

## Role and ownership matrix

Use these identities deliberately instead of treating login as a generic setup step.

| Identity | Primary use | Required ownership checks |
| --- | --- | --- |
| anonymous | public discovery, auth guard | Public routes render; protected routes redirect or deny without leaking private data. |
| `aaa` | creator, owner, seller, sender | Can manage own posts, comments, listings, wallet, drive files, messages, and settings. |
| `bbb` | second user, buyer, recipient, non-owner | Cannot edit `aaa` owned resources; can receive follows, messages, notices, orders, and shared links. |
| `admin` | privileged operator | Can open admin routes; regular users cannot; admin actions require visible reason or confirmation when the UI exposes it. |
| moderator | moderation/analytics operator if seeded or safely granted | If no moderator seed exists, use `/admin/users` to temporarily grant `ROLE_MODERATOR` to a disposable or restorable user only when the UI provides rollback; otherwise record moderator-specific access as `BLOCKED` and name the missing seed/test setup. |

## Evidence rules

- [ ] Capture network failures with method, URL, status, route, role, and action.
- [ ] Capture console errors after every failing feature group.
- [ ] Capture screenshot for blank pages, layout failures, broken dialogs, and unexpected 4xx/5xx UI states.
- [ ] Verify expected 401/403 behavior only on protected pages; unexpected 401/403 is a failure.
- [ ] Treat unexpected `500`, `503`, persistent `409`, unexpected `429`, WebSocket failure, missing persistence, and blank routed views as failures.
- [ ] If a feature has no visible UI route, mark `NO_UI` and name the frontend service/API that would need component or API-level coverage.
- [ ] For every persistent mutation, record the before/after UI state and one verification point after refresh, route reopen, account switch, or list reload.
- [ ] For every role-protected route, record the expected allowed role and at least one denied role.
- [ ] For every uploaded or downloaded file path, record the file name and verify content or a successful download URL/status when the UI exposes it.

## Assertion depth

Each feature area must include these assertion types unless the UI has no applicable surface.

| Assertion | Minimum expectation |
| --- | --- |
| Render | Route shows nonblank, domain-specific content and no blocking console error. |
| Network | Expected API or WebSocket request reaches the right `localhost:12880` path and returns the expected status. |
| Positive action | One meaningful create/update/send/query action succeeds through the UI. |
| Persistence | The changed state survives refresh, route reopen, account switch, or list/detail reload. |
| Negative path | Validation, permission, ownership, empty state, wrong password, insufficient balance, or unavailable action behaves correctly. |
| Cleanup | Run-specific data is removed or explicitly recorded as intentionally retained. |

## Complete route inventory

| Route | Name | Access | Required roles | Required checks |
| --- | --- | --- | --- | --- |
| `/` | redirect | public | none | Redirects to `/posts`. |
| `/login` | login alias | public | none | Redirects/renders the same state as `/auth/login`. |
| `/auth/login` | `login` | public | none | Login form, captcha fallback if shown, failed login message, successful login. |
| `/auth/register` | `register` | public | none | Form render, validation, registration submit path, email-code state if enabled. |
| `/auth/password/reset` | `passwordReset` | public | none | Request reset form, validation, confirm/reset state if reachable. |
| `/posts` | `posts` | public | none | Feed, filters, categories, tags, create post when authenticated. |
| `/posts/:postId` | `postDetail` | public | none | Detail, comments, replies, likes, bookmark, report, moderation controls when authorized. |
| `/search` | `search` | public | none | Search workbench, keyword query, category/tag filters, result navigation, failed-search evidence. |
| `/market` | `market` | public | none | Listing browse, filters/sort if present, empty/error states. |
| `/market/listings/:listingId` | `marketDetail` | public | none | Listing details, buy action, seller/stock state. |
| `/wallet` | `wallet` | auth | any user | Summary, ledger, recharge, withdrawal, transfer. |
| `/market/publish` | `marketPublish` | auth | any user | Publish virtual and physical listing paths when UI exposes both. |
| `/market/my-listings` | `marketMyListings` | auth | any user | Seller listings, edit/pause/resume/close actions if visible. |
| `/market/my-listings/:listingId/inventory` | `marketInventory` | auth | seller | List inventory, add inventory, invalidate inventory if visible. |
| `/market/orders/buying` | `marketBuyingOrders` | auth | buyer | Buying orders, order state transitions. |
| `/market/orders/selling` | `marketSellingOrders` | auth | seller | Selling orders, deliver/ship/dispute actions. |
| `/market/orders/:orderId` | `marketOrderDetail` | auth | participant | Detail, escrow, delivery, confirm/cancel/dispute status. |
| `/market/addresses` | `marketAddresses` | auth | any user | List, create, edit, delete, default address. |
| `/drive` | `drive` | auth | any user | Space, folders, files, upload, download, move, rename, trash, restore, permanent delete if intentionally tested, share management. |
| `/drive/s/:shareToken` | `driveShare` | public | none | Share metadata, password verify, ticket state, download-url action. |
| `/admin/wallet` | `walletAdmin` | auth | `ROLE_ADMIN` | Freeze, reverse, audit results, regular-user forbidden. |
| `/admin/market/disputes` | `adminMarketDisputes` | auth | `ROLE_ADMIN` | List disputes, resolve refund/release when safe test data exists. |
| `/preview/editorial` | preview redirect | public | none | Redirects to preview B. |
| `/preview/editorial/a` | `editorialPreviewA` | public | none | Preview A renders. |
| `/preview/editorial/b` | `editorialPreviewB` | public | none | Preview B renders. |
| `/preview/editorial/c` | `editorialPreviewC` | public | none | Preview C renders. |
| `/messages` | `messages` | auth | any user | Inbox, unread summary, conversation open, WebSocket bootstrap. |
| `/messages/:conversationId` | `messageDetail` | auth | participant | History, send, read state, realtime accepted/committed state if visible. |
| `/notices` | `notices` | auth | any user | Topic summary, unread count, list, mark read. |
| `/notices/:topic` | `noticeDetail` | auth | any user | Topic-specific notices, read state after open. |
| `/bookmarks` | `bookmarks` | auth | any user | Bookmark list and navigation back to post. |
| `/analytics` | `analytics` | auth | `ROLE_ADMIN` or `ROLE_MODERATOR` | UV/DAU query, date range controls, regular-user forbidden. |
| `/moderation` | `moderation` | auth | `ROLE_ADMIN` or `ROLE_MODERATOR` | Reports list, action history, take action if safe test report exists. |
| `/ops` | `opsConsole` | auth | `ROLE_ADMIN` | Search reindex UI, status/result handling, no direct token bypass. |
| `/admin/users` | `userManagement` | auth | `ROLE_ADMIN` | Search by id/username/email, role update controls, audit reason/confirm if tested. |
| `/settings` | `settings` | auth | any user | Profile/settings fields, avatar upload/update path if visible. |
| `/users/:userId` | `userProfile` | public | none | Profile, recent posts/comments, follow, block if visible, message action. |
| `/users/:userId/followees` | `followees` | public | none | Followee list, empty state, user navigation. |
| `/users/:userId/followers` | `followers` | public | none | Follower list, empty state, user navigation. |
| `/dev` | `dev` | auth | any user | Auth-gated dev/home view renders; logged-out redirect. |
| `/403` | `forbidden` | public | none | Forbidden page renders. |
| wildcard | `notFound` | public | none | Unknown route renders 404 page. |

## Full execution checklist

### 1. Browser preflight

- [ ] Start a clean Playwright browser context.
- [ ] Clear cookies, localStorage, and sessionStorage.
- [ ] Visit `/`.
- [ ] Confirm `/` redirects to `/posts`.
- [ ] Confirm the app shell/sidebar/topbar renders.
- [ ] Confirm no console error appears on first render.
- [ ] Confirm runtime API calls target `localhost:12880`.
- [ ] Inspect runtime config source and record whether `__COMMUNITY_RUNTIME_CONFIG__`, `VITE_API_BASE_URL`, `VITE_IM_CORE_BASE_URL`, or local port inference selected the API origins.
- [ ] Confirm browser loaded `app-config.js` before application code and that `globalThis.__COMMUNITY_RUNTIME_CONFIG__` has non-empty `apiBaseUrl` and `imHttpBaseUrl` in compose.
- [ ] Confirm `GATEWAY_PUBLIC_BASE_URL`, `FRONTEND_RUNTIME_API_BASE_URL`, and `FRONTEND_RUNTIME_IM_HTTP_BASE_URL` overrides, if set, do not point to container hostnames such as `nginx`, `community-gateway`, `im-core`, or `community-oss`.
- [ ] Confirm `http.js` uses `http://localhost:12880` for `/api/**` and `imCoreHttp.js` uses the same gateway origin for IM core HTTP calls.
- [ ] Confirm no request is sent to `127.0.0.1:12880` while browsing from `localhost:12881`, unless the configured origin intentionally uses that host and cookies/CORS still work.
- [ ] Confirm public `/files/**` and signed download links resolve through the gateway/frontend-visible origin, not container hostnames.

### 2. Auth, session, refresh, and route guard

- [ ] Open `/auth/login`.
- [ ] Submit invalid credentials and confirm a visible error or stable failed state.
- [ ] Repeat invalid login only enough to verify captcha or rate-limit UI if it appears; record `429` as expected only when the UI explains the limit.
- [ ] Log in as `aaa / aaa`.
- [ ] Confirm authenticated user state appears in nav/topbar.
- [ ] Visit `/auth/login` while authenticated and confirm redirect-away or logged-in state.
- [ ] Refresh the browser and confirm session restoration through the refresh cookie.
- [ ] Open `/wallet` after refresh and confirm it remains authenticated.
- [ ] Force or wait for one access-token expiry when feasible; confirm a protected request gets one `401`, `POST /api/auth/refresh` succeeds, and the original request retries once without user-visible data loss.
- [ ] If refresh fails or the refresh cookie is absent, confirm `http.js` clears session state and redirects to `/#/auth/login` without exposing private cached UI.
- [ ] Confirm `imCoreHttp.js` follows the same best-effort refresh path for IM core `401` responses.
- [ ] Log out.
- [ ] Confirm `/wallet`, `/drive`, `/messages`, `/notices`, `/bookmarks`, `/settings`, `/dev` redirect or deny as logged out.
- [ ] Confirm logout clears visible private state and back/refresh does not expose the previous authenticated page.
- [ ] Render `/auth/register`.
- [ ] Submit incomplete registration and confirm validation.
- [ ] Submit a duplicate username/email path only if the UI exposes local test-safe values; confirm a stable validation or conflict message.
- [ ] Request captcha and confirm `GET /api/auth/captcha` returns an image and `captchaId`; click the image or refresh control and confirm a new captcha replaces the old one.
- [ ] Register a disposable user using a run-specific username and email.
- [ ] Confirm `POST /api/auth/register` moves to email-code verification instead of silently creating an unusable account.
- [ ] Retrieve the registration code from MailHog at `http://localhost:8025`, or from `debugEmailCode` only when `AUTH_REGISTRATION_EXPOSE_CODE=true` is explicitly enabled for local debugging.
- [ ] Prefer `node .agents/skills/community-playwright-regression/scripts/read-mailhog-message.mjs --to <email> --contains <runId>` to retrieve local registration/reset mail when MailHog is available; record the message id or subject used.
- [ ] Test resend with a fresh captcha and confirm `POST /api/auth/register/code/resend` updates the verification state or shows cooldown/validation.
- [ ] Submit an invalid email code and confirm a stable error state.
- [ ] Submit the correct email code and confirm `POST /api/auth/register/code/verify` logs in or completes activation as designed.
- [ ] Render `/auth/password/reset`.
- [ ] Submit incomplete reset request and confirm validation.
- [ ] Request password reset for a disposable account or account whose password will be restored; do not permanently change seeded `aaa`, `bbb`, or `admin` unless rollback is performed in the same run.
- [ ] Retrieve reset link from MailHog, or from `debugResetLink` when the local response exposes it.
- [ ] Open `/auth/password/reset?token=...`, submit invalid captcha/new-password cases, then confirm a valid reset succeeds.
- [ ] Log in with the new password, then restore the disposable or seeded password if the account is reused.

### 3. Posts, taxonomy, media, comments, and bookmarks

- [ ] Open `/posts` as anonymous and confirm feed renders.
- [ ] Exercise feed controls: latest/hot order, all/unread filter, category select, subscribed toggle if visible.
- [ ] Confirm categories load from `/api/categories`.
- [ ] Confirm hot/suggested tags load if the UI exposes tags.
- [ ] Log in as `aaa`.
- [ ] Create a post with title `${runId} Playwright 全功能巡检帖`.
- [ ] Add rich block/body content through `PostBlockEditor`: at minimum one paragraph block and one code block with language.
- [ ] Add category and tags if controls are present.
- [ ] Upload/attach image, video, and file blocks if the UI exposes those controls; verify upload state transitions from pending to complete.
- [ ] Confirm the submit action is disabled or safely rejected while any media upload is pending or failed.
- [ ] Confirm shared upload execution through `uploadSession.js` preserves upload URL, form fields, headers, file field name, and visible error behavior for failed upload sessions.
- [ ] Confirm `POST /api/posts` includes an `Idempotency-Key` header and succeeds, then navigates or inserts the new post.
- [ ] Open the created post detail.
- [ ] Confirm `GET /api/posts/{postId}` and comments load.
- [ ] Confirm `PostBlockRenderer` displays paragraph/code/media blocks correctly and file/download links work when media was attached.
- [ ] Confirm post list/detail hydration calls such as `POST /api/posts/batch-summary`, `POST /api/users/batch-summary`, `/api/likes/counts`, and `/api/likes/statuses` return 200 when the UI issues them, and hydrated author/like fields are not blank.
- [ ] Add a top-level comment and confirm `POST /api/posts/{postId}/comments` includes an `Idempotency-Key` header.
- [ ] Refresh post detail and confirm the created post body and comment remain visible.
- [ ] Add a reply to a comment if reply UI exists.
- [ ] Edit own post if edit controls are present.
- [ ] Edit own comment if edit controls are present.
- [ ] Log in as `bbb` and confirm edit/delete controls for `aaa`'s post/comment are absent or rejected.
- [ ] Like and unlike the post.
- [ ] Like and unlike a comment if visible.
- [ ] Bookmark the post.
- [ ] Open `/bookmarks` and confirm the post appears.
- [ ] Unbookmark and confirm state changes.
- [ ] Report the post or comment with safe test reason if report UI exists.
- [ ] Confirm duplicate report, empty report reason, or invalid report target shows a safe validation state if the UI exposes that path.
- [ ] Delete own post only if the test can tolerate persistent data cleanup; otherwise leave test data and record it.
- [ ] If duplicate post/comment submit can be triggered through the UI, confirm retry/idempotency does not create duplicate records inside the 10-second idempotency window.

### 4. Search and reindex readiness

- [ ] Open `/search`.
- [ ] Confirm keyword input, category/tag controls, result container, and empty state render.
- [ ] Search for the created post title.
- [ ] Confirm `GET /api/search/posts` returns 200 or record exact error status/body.
- [ ] If result appears, open it and confirm navigation to `/posts/:postId`.
- [ ] Confirm search-result hydration uses batch post/user summary endpoints where issued and does not render blank author/title metadata.
- [ ] Search with empty keyword and confirm validation or default state.
- [ ] Search for a nonsense `${runId}` keyword and confirm empty state is explicit, not a blank panel.
- [ ] Search by category/tag if controls exist.
- [ ] As `admin`, open `/ops`.
- [ ] Trigger reindex only through the UI and only once per run.
- [ ] Record `POST /api/ops/search/reindex` status. `409 already_running` is a failure unless there is visible running-task evidence.
- [ ] Re-run search after reindex if reindex succeeds.

### 5. User profile, settings, avatar, social, and block

- [ ] Open `/users/00000000-0000-7000-8000-000000000002` as `aaa`.
- [ ] Confirm profile summary, level/score fields if present, recent posts, and recent comments.
- [ ] Confirm profile and feed hydration uses `GET /api/users/{userId}`, `POST /api/users/batch-summary`, and visible like/follow status endpoints without blank usernames or missing avatars.
- [ ] Follow `bbb`.
- [ ] Confirm follow count/status changes.
- [ ] Refresh profile and confirm follow state persists.
- [ ] Open `/users/00000000-0000-7000-8000-000000000002/followers`.
- [ ] Open `/users/00000000-0000-7000-8000-000000000002/followees`.
- [ ] Unfollow `bbb` and confirm state changes.
- [ ] Use message action from profile and confirm it opens/creates a conversation.
- [ ] Confirm `GET /api/blocks` is issued after login or social preference initialization, and the current blocked list affects visible profile/message affordances when applicable.
- [ ] If block controls are visible, block then unblock `bbb` via `POST /api/blocks` and `DELETE /api/blocks`; refresh profile and confirm list/state persistence.
- [ ] If category subscription controls are visible, verify `/api/subscriptions/categories` powers the subscribed-only feed filter; if no write UI exists, mark subscription mutation as `NO_UI` and name `subscriptionService.js`.
- [ ] Confirm self-follow, self-message, or self-block controls are absent or safely rejected if visible.
- [ ] Open `/settings`.
- [ ] Confirm profile/account settings render.
- [ ] Update one safe profile field if the UI exposes it, refresh, then restore it or record retained test data.
- [ ] Test avatar upload session/update if the UI exposes file upload. Verify direct `SettingsView.vue` calls to `/api/users/{userId}/avatar/upload-sessions`, shared `uploadSession.js` upload execution, `PUT /api/users/{userId}/avatar`, `/api/oss/**`, and `/files/**` network evidence.

### 6. Private messages and IM realtime

- [ ] Open `/messages` as `aaa`.
- [ ] Confirm `POST /api/im/sessions` succeeds.
- [ ] Confirm WebSocket connects to `/ws/im` or the server-issued `wsUrl`.
- [ ] Confirm conversation list loads from `/api/im/conversations`.
- [ ] Confirm IM unread summary has a disposition: if UI requests `GET /api/im/unread/summary`, verify conversation/room counts; otherwise mark it `NO_UI` and name `UnreadController` owner API/integration coverage.
- [ ] Confirm IM HTTP calls use `imCoreHttp.js` and the resolved `imHttpBaseUrl` gateway origin, and that IM `401` refresh replays at most once.
- [ ] Open or create a conversation with `bbb`.
- [ ] Send text `${runId} IM smoke`.
- [ ] Confirm message appears in the thread.
- [ ] Confirm accepted/committed/rejected state if visible.
- [ ] Refresh thread and confirm history still contains the message.
- [ ] Log in as `bbb` and confirm the message appears in the recipient conversation.
- [ ] Send a reply from `bbb`, log back in as `aaa`, and confirm the reply appears.
- [ ] Mark conversation read by opening the thread or explicit read control.
- [ ] Return to `/messages` and confirm inbox still renders.
- [ ] Open a fabricated or non-participant `/messages/:conversationId` and confirm it denies or shows a safe not-found state.
- [ ] Record WebSocket and HTTP failures separately.
- [ ] Trigger one safe reconnect path when feasible by reload, offline/online, or visibility restore; confirm a fresh `POST /api/im/sessions` occurs and old socket state does not duplicate messages.
- [ ] If room chat controls are not visible, mark `RoomController` APIs `NO_UI` and name the owner API/integration test layer for `POST /api/im/rooms`, `POST /api/im/rooms/{roomId}/join`, `POST /api/im/rooms/{roomId}/leave`, `GET /api/im/rooms/{roomId}/messages`, and `POST /api/im/rooms/{roomId}/read`.

### 7. Notices

- [ ] Open `/notices`.
- [ ] Confirm topic summary loads from `/api/notices/summary`.
- [ ] Confirm unread count loads from `/api/notices/unread-count`.
- [ ] Confirm list loads from `/api/notices`.
- [ ] Open each visible notice topic link or tab.
- [ ] Open `/notices/:topic`.
- [ ] Confirm topic list renders and read state changes after viewing.
- [ ] Refresh notices and confirm read/unread count remains consistent.
- [ ] If the created post/comment/follow/message produced a notice for another user, log in as the recipient and confirm it appears.

### 8. Wallet

- [ ] Open `/wallet` as `aaa`.
- [ ] Confirm summary loads from `/api/wallet/summary`.
- [ ] Confirm balance, frozen/available amounts, ledger/recent transactions if present.
- [ ] Run recharge with unique request note/amount.
- [ ] Confirm `POST /api/wallet/recharges` succeeds and balance/ledger updates.
- [ ] Refresh `/wallet` and confirm the recharge appears in ledger/history.
- [ ] Run withdrawal with a small safe amount.
- [ ] Confirm `POST /api/wallet/withdrawals` succeeds or records validation if insufficient funds.
- [ ] Run transfer from `aaa` to `bbb`.
- [ ] Confirm `POST /api/wallet/transfers` succeeds and ledger updates.
- [ ] Log in as `bbb` and confirm the transfer appears or balance changes according to the product model.
- [ ] Attempt an invalid wallet action such as negative amount, over-balance withdrawal, or missing recipient if visible; confirm validation before submit or safe API error handling.
- [ ] Repeat the same visible operation only if the UI intentionally supports idempotent retry; otherwise avoid duplicate money movement.
- [ ] As `admin`, open `/admin/wallet`.
- [ ] Search/select target user if UI requires it.
- [ ] Freeze/unfreeze or freeze-only only when the UI has a safe reversal path.
- [ ] Reverse a known test transaction only when the UI exposes a safe confirmation and reason field.
- [ ] Confirm admin actions require reason/confirmation.
- [ ] Confirm `aaa` cannot open `/admin/wallet`.

### 9. Market listings, inventory, addresses, orders, and disputes

- [ ] Open `/market` as anonymous and confirm public listings load from `/api/market/listings`.
- [ ] Exercise visible filters/sorts/search on market browse.
- [ ] Open an existing `/market/listings/:listingId` if one exists.
- [ ] Log in as `aaa`.
- [ ] Open `/market/publish`.
- [ ] Publish a virtual listing with unique title `${runId} virtual listing`.
- [ ] Publish a physical listing if the UI exposes physical listing type.
- [ ] Confirm `POST /api/market/listings` succeeds.
- [ ] Refresh or reopen listing detail and confirm seller, price, stock, and status persist.
- [ ] Open `/market/my-listings`.
- [ ] Confirm created listings appear.
- [ ] Test edit/update listing if controls exist.
- [ ] Test pause/resume listing if controls exist and record `POST /api/market/listings/{listingId}/pause` and `POST /api/market/listings/{listingId}/resume`; otherwise record these backend-only transitions as `NO_UI` with owner market integration tests.
- [ ] As `bbb`, confirm seller-only listing controls for `aaa`'s listing are absent or rejected.
- [ ] Test close listing only on a disposable extra listing and record `POST /api/market/listings/{listingId}/close`; if no UI control exists, record `NO_UI`. Do not close the listing needed for the order flow.
- [ ] Open `/market/my-listings/:listingId/inventory`.
- [ ] Add inventory units for virtual listing.
- [ ] Confirm `POST /api/market/listings/{listingId}/inventory` succeeds.
- [ ] Refresh inventory and confirm created inventory units remain listed.
- [ ] Invalidate an inventory unit if the UI exposes the action and enough stock remains; record `POST /api/market/inventory/{inventoryUnitId}/invalidate`.
- [ ] Open `/market/addresses`.
- [ ] Create an address with run-specific receiver/phone/address.
- [ ] Edit that address.
- [ ] Set default address if control exists.
- [ ] Attempt invalid address data if visible and confirm validation.
- [ ] Delete that address after order flows that require it are complete.
- [ ] Log in as `bbb`.
- [ ] Buy the `aaa` listing if the listing is visible and wallet has enough funds.
- [ ] Confirm `POST /api/market/orders` succeeds, carries either a request id or `Idempotency-Key` evidence, and the UI shows escrow/pending state.
- [ ] Open `/market/orders/buying`.
- [ ] Open `/market/orders/:orderId`.
- [ ] Refresh order detail and confirm status remains stable.
- [ ] Cancel a separate disposable order only while current status permits; confirm `POST /api/market/orders/{orderId}/cancel` is hidden or rejected after shipment/delivery. If no UI control exists, record `NO_UI`.
- [ ] Log in as `aaa`.
- [ ] Open `/market/orders/selling`.
- [ ] Open the same order detail.
- [ ] For virtual goods, deliver content and record `POST /api/market/orders/{orderId}/deliver`; verify buyer can view delivery content. If no UI control exists, record `NO_UI`.
- [ ] For physical goods, ship with carrier/tracking and record `POST /api/market/orders/{orderId}/ship`; verify buyer sees shipment fields. If no UI control exists, record `NO_UI`.
- [ ] Log in as `bbb` and confirm delivery/receipt if action is visible; record `POST /api/market/orders/{orderId}/confirm` and escrow/status transition. If no UI control exists, record `NO_UI`.
- [ ] As a non-participant, confirm the order detail is denied or hidden if a safe route can be constructed.
- [ ] Open dispute from buyer if the test order can be safely disputed.
- [ ] Seller accept/reject dispute if visible; record `POST /api/market/disputes/{disputeId}/seller-accept` or `POST /api/market/disputes/{disputeId}/seller-reject`. If no UI control exists, record `NO_UI`.
- [ ] Log in as `admin`.
- [ ] Open `/admin/market/disputes`.
- [ ] Resolve refund/release only for the run-specific dispute; record `POST /api/admin/market/disputes/{disputeId}/resolve-refund` or `POST /api/admin/market/disputes/{disputeId}/resolve-release`.
- [ ] Confirm regular user cannot open `/admin/market/disputes`.

### 10. Drive, file upload, share, download, trash, and permanent delete

- [ ] Open `/drive` as `aaa`.
- [ ] Confirm `/api/drive/space` and `/api/drive/entries` load.
- [ ] Create folder `${runId}-folder`.
- [ ] Rename it to `${runId}-renamed`.
- [ ] Refresh `/drive` and confirm renamed folder remains visible.
- [ ] Create a subfolder if the UI exposes parent navigation.
- [ ] Move entry between folders if move UI exists.
- [ ] Upload a small test file if upload control exists.
- [ ] Confirm drive upload session, OSS object upload session, multipart complete, and drive entry complete calls succeed; expected families include `/api/drive/**`, `/api/oss/objects/upload-sessions`, `/api/oss/objects/{objectId}/complete`, `/api/oss/objects/{objectId}`, and `/files/**`.
- [ ] Confirm shared upload execution through `uploadSession.js` respects upload constraints, multipart field names, headers, and visible failure state for bad file type/size when the UI exposes validation.
- [ ] Refresh after upload and confirm the uploaded file remains visible with expected name/size when shown.
- [ ] Download a file/folder item if download control exists and record `/download-url`, `/api/oss/objects/{objectId}/signed-url`, or `/files/**` status.
- [ ] Search drive for `${runId}`.
- [ ] Generate a share link with password, such as `1234`.
- [ ] Revoke a share only after public-share checks, if revoke UI exists.
- [ ] Open `/drive/s/:shareToken` in a logged-out or clean context.
- [ ] Confirm share metadata loads before password.
- [ ] Enter wrong password and confirm safe error if UI supports it.
- [ ] Enter correct password and confirm unlocked state.
- [ ] Trigger public download-url if visible.
- [ ] Confirm public share access does not expose private owner controls and does not require an authenticated session after password verification.
- [ ] Return to `/drive` as owner.
- [ ] Move folder/file to trash.
- [ ] Confirm `/api/drive/trash` or trash view shows it.
- [ ] Restore from trash.
- [ ] Confirm `bbb` cannot manage `aaa`'s private drive entry unless it is accessed through the public share path.
- [ ] Permanently delete only run-specific disposable files/folders and only if the UI exposes the control.
- [ ] Mark direct OSS grant/revoke/delete APIs as `NO_UI` unless a visible object-permission management UI exists; owner tests must cover `POST /api/oss/objects/{objectId}/grants`, `DELETE /api/oss/objects/{objectId}/grants/{grantId}`, and `DELETE /api/oss/objects/{objectId}`.

### 11. Moderation, reports, and content admin actions

- [ ] Create a report from a post/comment/profile if UI exposes report action.
- [ ] Log in as `admin`.
- [ ] Open `/moderation`.
- [ ] Confirm `/api/moderation/reports` loads.
- [ ] Filter by status/target type/reporter if UI exposes filters.
- [ ] Open or inspect a report detail row if available.
- [ ] Take a moderation action only on a run-specific report and only with a safe reversible action.
- [ ] Confirm `/api/moderation/actions` history loads.
- [ ] In post detail, verify moderator/admin-only actions such as top, wonderful, or delete if visible.
- [ ] Confirm regular user cannot open `/moderation`.
- [ ] Confirm a non-admin cannot see admin/moderation nav entries after refresh and on mobile navigation.
- [ ] If a moderator role is available or safely granted, verify `ROLE_MODERATOR` can open `/moderation` but cannot open admin-only routes such as `/admin/users`, `/admin/wallet`, `/admin/market/disputes`, or `/ops`.
- [ ] If a moderator role was granted for the run, restore the original roles through `/admin/users`, log the user out/in, and verify the rollback.

### 12. Analytics and ops

- [ ] Log in as `admin`.
- [ ] Open `/analytics`.
- [ ] Query UV with a short date range.
- [ ] Query DAU with a short date range.
- [ ] Test invalid date range if UI exposes validation.
- [ ] Refresh `/analytics` and confirm the selected query state or results remain stable enough for reuse.
- [ ] Confirm regular user cannot open `/analytics`.
- [ ] If a moderator role is available or safely granted, verify `ROLE_MODERATOR` can open `/analytics` but cannot open admin-only routes.
- [ ] Open `/ops`.
- [ ] Confirm ops console render includes search reindex controls/status.
- [ ] Trigger search reindex through UI only when the run requires it.
- [ ] Record `409 already_running` as a failure unless UI shows active running state.
- [ ] Confirm regular user cannot open `/ops`.

### 13. Admin user management

- [ ] Log in as `admin`.
- [ ] Open `/admin/users`.
- [ ] Search by username `aaa`.
- [ ] Search by username `bbb`.
- [ ] Search by user id `00000000-0000-7000-8000-000000000001`.
- [ ] Search by email `aaa@example.com`.
- [ ] Confirm not-found or validation state for a nonsense query.
- [ ] Inspect role update UI.
- [ ] Do not change seeded roles unless the UI provides a safe rollback and the test performs that rollback.
- [ ] If role update is tested, verify audit reason is required and the changed user sees the new role after re-login, then the restored role after rollback and another re-login.
- [ ] Confirm regular user cannot open `/admin/users`.

### 14. Preview, system, and navigation shell

- [ ] Open `/preview/editorial`.
- [ ] Confirm it redirects to `/preview/editorial/b`.
- [ ] Open preview A, B, and C.
- [ ] Confirm all preview variants render nonblank content.
- [ ] Open `/dev` as logged out and confirm auth guard.
- [ ] Open `/dev` as logged in and confirm render.
- [ ] Confirm `/dev` direct HTTP calls load: `/api/notices/unread-count`, `/api/follows/{userId}/followees/count`, and `/api/follows/{userId}/followers/count`, or show stable error toast without breaking the page.
- [ ] Open `/403`.
- [ ] Open `/__playwright_missing_${runId}` and confirm wildcard 404 render.
- [ ] Verify sidebar navigation hides admin links for regular users.
- [ ] Verify sidebar navigation shows admin links for admin.
- [ ] Verify mobile navigation at a narrow viewport for auth, primary product routes, and admin-link gating.
- [ ] Verify sidebar navigation groups `community`, `trading`, `personal`, `admin`, and `account` appear only when their visible child items are allowed.
- [ ] Verify `frontend/src/router/navigation.js` nav keys and mobile fallback entries: `posts`, `search`, `market`, `me`, authenticated personal links, moderator links, and admin-only links.
- [ ] Verify active navigation state for posts, market, messages, drive, and one admin route.
- [ ] Capture screenshots for the home/feed shell, post detail, market order detail, drive share, messages, and one admin route in desktop mode.
- [ ] Capture at least one mobile screenshot after navigation opens or collapses; verify text does not overlap or escape controls.
- [ ] If using Chrome DevTools or Playwright accessibility snapshots, run one accessibility/semantic pass on auth, posts, and one admin route and record severe violations.

## API/service coverage map

Use this map to verify that every frontend service has either visible UI coverage or a recorded `NO_UI` reason.

| Frontend service | Main endpoints | Browser coverage expectation |
| --- | --- | --- |
| `authService.js` | `/api/auth/login`, `/api/auth/me`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/register`, `/api/auth/register/code/resend`, `/api/auth/register/code/verify`, `/api/auth/captcha`, `/api/auth/password/reset/request`, `/api/auth/password/reset/confirm` | Auth pages, session restore, logout, validation, captcha/code/reset states if reachable. |
| `postService.js` | `/api/posts`, `/api/posts/{postId}`, comments, replies, update/delete, top/wonderful/moderation delete | Posts and post detail; admin/moderator controls if visible. |
| `postMediaService.js` | `/api/posts/media/upload-sessions` | Attach media to post if UI exposes it; otherwise `NO_UI`. |
| `taxonomyService.js` | `/api/categories`, `/api/tags/hot`, `/api/tags/suggest` | Posts/search category and tag controls. |
| `subscriptionService.js` | `/api/subscriptions/categories` | Subscribed category UI if visible; otherwise `NO_UI`. |
| `bookmarkService.js` | `/api/posts/{postId}/bookmark`, `/api/bookmarks` | Bookmark/unbookmark and `/bookmarks`. |
| `reportService.js` | `/api/reports` | Report post/comment/profile if UI exposes it. |
| `moderationService.js` | `/api/moderation/reports`, `/api/moderation/actions` | `/moderation`. |
| `socialService.js` | `/api/likes`, `/api/follows`, follow lists/counts, like counts/statuses | Like, follow/unfollow, profile lists. |
| `blockService.js` | `/api/blocks` | Block/unblock/list if UI exposes controls. |
| `userService.js` | `/api/users/{userId}`, recent posts/comments, batch summary | Profile, user summaries, recent activity; avatar is a direct Settings UI surface. |
| `adminUserService.js` | `/api/users/admin/search`, `/api/users/admin/role` | `/admin/users`; role update only with rollback. |
| `noticeService.js` | `/api/notices/summary`, `/api/notices`, `/api/notices/unread-count`, `/api/notices/read` | `/notices` and `/notices/:topic`. |
| `searchService.js` | `/api/search/posts`, `/api/ops/search/reindex` | `/search` and `/ops`. |
| `analyticsService.js` | `/api/analytics/uv`, `/api/analytics/dau` | `/analytics`. |
| `walletService.js` | `/api/wallet/summary`, recharge, withdrawal, transfer, admin freeze/reverse | `/wallet` and `/admin/wallet`. |
| `marketService.js` | listings, inventory, addresses, orders, disputes, admin disputes | Market browse, publish, seller, buyer, order, dispute, admin pages. |
| `driveService.js` | space, entries, trash, folders, uploads, search, rename, move, trash/restore/delete, download-url, shares, public share verify/download | `/drive` and `/drive/s/:shareToken`. |
| `imCoreChatService.js` | conversations, messages, read | `/messages` and `/messages/:conversationId`. |
| `imRealtimeClient.js` | `/api/im/sessions`, `/ws/im` | IM session bootstrap and WebSocket send/receive. |

## Direct HTTP surface map

These calls bypass `frontend/src/api/services`. They must either stay accounted for here or be moved behind a service.

| File | Direct calls | Regression expectation |
| --- | --- | --- |
| `HomeView.vue` | `/api/notices/unread-count`, `/api/follows/{userId}/followees/count`, `/api/follows/{userId}/followers/count` | `/dev` logged-in render must verify counts load or stable error handling. |
| `SettingsView.vue` | `/api/users/{userId}/avatar/upload-sessions`, `PUT /api/users/{userId}/avatar`, plus `uploadSession.js` execution | Settings/avatar flow must verify upload session, upload, avatar update, refreshed `me`, and visible errors. |
| `Topbar.vue` | `/api/auth/logout` | Logout must clear session and route to login even if the request fails. |
| `imRealtimeClient.js` | `/api/im/sessions`, WebSocket `wsUrl` | IM realtime flow must verify session bootstrap, reconnect, and WebSocket behavior. |
| `uploadSession.js` | server-provided upload URL | Avatar, post media, and drive upload must verify the generated public upload URL and multipart execution. |

## Client infrastructure coverage map

Use this map for files that are not normal route-level services but still control browser regression behavior.

| Client file | Main behavior | Regression expectation |
| --- | --- | --- |
| `http.js` | `resolveApiBaseUrl`, bearer header injection, `Idempotency-Key` for `POST /api/posts` and `POST /api/posts/{postId}/comments`, refresh-and-retry on `401`, global error toast with trace id | Browser preflight, auth/session refresh, post/comment create, and visible 4xx/5xx error handling. |
| `imCoreHttp.js` | `resolveImHttpBaseUrl`, bearer header injection, best-effort refresh through `/api/auth/refresh` for IM core `401`, IM HTTP error toast | IM conversation list/history/read checks and IM refresh edge case. |
| `uploadSession.js` | normalizes upload session payloads and executes multipart uploads with server-provided URL, fields, headers, and file field | Avatar, post media, and drive upload checks must prove upload sessions are executed through the resolved public URL and bad sessions surface stable errors. |
| `renderRuntimeConfig.mjs` | writes `dist/app-config.js` from `FRONTEND_RUNTIME_API_BASE_URL`, `FRONTEND_RUNTIME_IM_HTTP_BASE_URL`, or `GATEWAY_PUBLIC_BASE_URL` | Deployment preflight must prove runtime config is generated, loaded before app startup, non-empty, and browser-reachable. |
| `endpointResolution.js` | runtime config, Vite env, or local gateway inference for API and IM HTTP base URL | Browser preflight must record selected API and IM origins and prove requests reach `localhost:12880`. |
| `runtimeConfig.js` | reads `globalThis.__COMMUNITY_RUNTIME_CONFIG__` string values | Runtime config mismatch is a regression when local compose uses `localhost:12881` and gateway `localhost:12880`. |
| `imRealtimeClient.js` | IM session bootstrap and WebSocket URL handling | IM checks must record `/api/im/sessions` and `/ws/im` or issued `wsUrl`; failure is separate from HTTP failure. |
| `PostBlockEditor.vue` | paragraph/code/image/video/file block editing and upload states | Post creation/editing must cover paragraph + code; media states require upload controls or component-level coverage. |
| `PostBlockRenderer.vue` | paragraph/code/media/file rendering | Post detail must prove persisted blocks render after refresh. |

## Non-UI and supplemental coverage

Do not let `NO_UI` hide risk. Use these dispositions in the report.

| Surface | Required disposition |
| --- | --- |
| Visible UI exists | Exercise through Playwright and record route/action/network evidence. |
| Component exists but no route | Mark `NO_UI` and name the component or story/test that should cover it. |
| Frontend service exists but no UI | Mark `NO_UI` and name the API/service file plus the needed service or component test. |
| Backend behavior only | Mark `NO_UI` and name the backend integration, contract, or job test that should own it. |
| External dependency failure | Mark `BLOCKED` only after recording the dependency, route, request, status, and user-visible behavior. |

## Indirect or no-UI surfaces

- Growth task/reward/level has no dedicated public page; verify indirectly through post/comment/social/wallet/profile level effects when visible.
- IM rooms have backend APIs, but no route in the current SPA; mark `NO_UI` unless a room UI is added. Owner coverage belongs to IM core API/integration tests for `RoomController`: create room, join, leave, list messages with member/non-member, and mark read.
- OSS object APIs have no standalone page; verify indirectly through avatar, drive upload, post media upload, and public file/download links. Owner coverage for direct object permissions/lifecycle belongs to community-oss API/integration tests for `OssObjectController`: upload session, complete, metadata, signed URL, grant, revoke, delete, and unauthorized actor cases.
- Internal OSS reference APIs `/internal/oss/objects/{objectId}/references` and `/internal/oss/objects/{objectId}/references/{referenceId}` are not browser surfaces; owner coverage belongs to cross-service OSS integration or contract tests that prove drive/post/avatar references retain and release objects correctly.
- Internal IM realtime projection APIs `/internal/im/realtime/projections/room-memberships`, `/internal/im/realtime/projections/user-policies`, and `/internal/im/realtime/projections/block-relations` are not browser surfaces; browser coverage is indirect through IM send/read/block behavior, and owner coverage belongs to IM realtime/core/app integration tests.
- Market backend state-transition endpoints without current frontend service methods or controls, including listing pause/resume/close, order deliver/ship/confirm/cancel, and seller dispute decisions, must be marked `NO_UI` at execution time unless the UI exposes them; owner coverage belongs to market controller/application integration tests until the frontend wires those actions.
- Scheduler/compensation jobs have no general UI except ops/search; record `NO_UI` for other scheduler internals.
- Category subscriptions may only be visible through posts filters; record `NO_UI` if no subscription control exists.
- Email delivery, captcha provider behavior, object-store internals, search index jobs, outbox retries, and compensation jobs need backend or integration coverage when not directly visible in UI.
- Frontend unit/component tests can support targeted diagnosis but do not replace this browser matrix. For targeted follow-ups, run relevant Vitest files such as `router/navigation.test.js`, `http.test.js`, `imRealtimeClient.test.js`, `PostBlockEditor.test.js`, `PostBlockRenderer.test.js`, `SettingsView.test.js`, `Market*View*.test.js`, `Drive*View.test.js`, or matching service tests when the changed area touches those files.

## Backend endpoint families that must be accounted for

The browser matrix does not need to directly call every endpoint, but each family below must have visible UI evidence, indirect UI evidence, or a `NO_UI` owner-test disposition.

| Endpoint family | Primary owner | UI expectation |
| --- | --- | --- |
| `/api/auth` | `authService.js` / `AuthController` | Login, refresh, logout, register, captcha, registration code, password reset. |
| `/api/posts` | `postService.js`, `postMediaService.js` | Feed/detail/create/comment/reply/edit/delete, media upload, batch summary hydration. |
| `/api/categories`, `/api/tags` | `taxonomyService.js` | Feed/search filters and suggestions. |
| `/api/subscriptions/categories` | `subscriptionService.js` | Subscribed filter read path; write path is `NO_UI` unless a control exists. |
| `/api/bookmarks` | `bookmarkService.js` | Bookmark list and post bookmark toggle. |
| `/api/reports`, `/api/moderation` | `reportService.js`, `moderationService.js` | Report creation and moderator/admin queue. |
| `/api/users`, `/api/users/admin` | `userService.js`, `adminUserService.js` | Profile, recent activity, summaries, avatar, admin search/role. |
| `/api/likes`, `/api/follows`, `/api/blocks` | `socialService.js`, `blockService.js` | Like/follow/block plus hydrated counts/status. |
| `/api/search`, `/api/ops` | `searchService.js` | Search and reindex UI. |
| `/api/analytics` | `analyticsService.js` | Admin/moderator analytics. |
| `/api/wallet`, `/api/wallet/admin` | `walletService.js` | User wallet and admin wallet. |
| `/api/market`, `/api/admin/market/disputes` | `marketService.js` | Browse, publish, inventory, addresses, orders, delivery/shipping/confirm/cancel/dispute/admin resolve. |
| `/api/drive`, `/api/drive/shares`, `/files` | `driveService.js` | Drive CRUD, upload, share, download, public access. |
| `/api/im/conversations`, `/api/im/unread`, `/api/im/sessions`, `/ws/im` | `imCoreChatService.js`, `imRealtimeClient.js`, `UnreadController` | Private-message inbox/detail/realtime/read; `/api/im/unread/summary` is `NO_UI` unless the UI issues it. |
| `/api/im/rooms` | `RoomController` | `NO_UI` until a room UI exists; owner API/integration tests must cover create/join/leave/messages/read. |
| `/api/oss/objects` | `OssObjectController` | Indirect through avatar/drive/post media; grant/revoke/delete remain owner API/integration coverage unless UI exists. |
| `/internal/oss/objects` | `InternalOssObjectController` | Browser `NO_UI`; verify indirectly through object retention/release side effects or owner integration tests. |
| `/internal/im/realtime/projections` | `InternalRealtimeProjectionController`, `ImPolicySnapshotController` | Browser `NO_UI`; verify indirectly through IM/block behavior or owner integration tests. |

## Backend controller disposition map

Each controller below must stay aligned with either direct browser evidence, indirect browser evidence, or owner API/integration coverage.

| Controller | Disposition |
| --- | --- |
| `AuthController` | Browser auth, refresh, registration, captcha, and password reset. |
| `PostController` | Browser posts, comments, replies, moderation actions, idempotency, and hydration. |
| `PostMediaController` | Browser post media upload when media controls exist; otherwise component/service coverage plus owner API tests. |
| `CategoryController` | Browser feed/search category filters. |
| `TagController` | Browser hot/suggested tag controls where visible. |
| `SubscriptionController` | Browser subscribed filter read path; mutation is `NO_UI` unless UI exists. |
| `BookmarkController` | Browser bookmark toggle and `/bookmarks`. |
| `ReportController` | Browser report modal where visible. |
| `ModerationController` | Browser `/moderation` queue/actions/history. |
| `LikeController` | Browser post/comment like counts/statuses. |
| `FollowController` | Browser follow/unfollow, follower/followee pages, and `/dev` direct counts. |
| `BlockController` | Browser block/unblock/list where visible; IM policy side effects via integration tests. |
| `UserController` | Browser profile, recent activity, batch summary, and Settings avatar direct flow. |
| `AdminUserController` | Browser `/admin/users` search/role rollback. |
| `NoticeController` | Browser notices, topic detail, unread summary, read state, and `/dev` direct unread count. |
| `SearchController` | Browser `/search`. |
| `OpsController` | Browser `/ops` search reindex. |
| `AnalyticsController` | Browser `/analytics` admin/moderator. |
| `WalletController` | Browser `/wallet`. |
| `AdminWalletController` | Browser `/admin/wallet`. |
| `MarketController` | Browser market browse/publish/inventory/address/order read paths; missing transition controls are `NO_UI` with owner integration tests. |
| `AdminMarketController` | Browser `/admin/market/disputes` when safe dispute data exists. |
| `DriveController` | Browser `/drive` CRUD/upload/trash/download. |
| `DrivePublicShareController` | Browser `/drive/s/:shareToken`. |
| `ConversationController` | Browser `/messages` and `/messages/:conversationId`. |
| `UnreadController` | Browser `NO_UI` unless UI requests `/api/im/unread/summary`; owner IM core API tests required. |
| `RoomController` | Browser `NO_UI`; owner IM core API tests required. |
| `InternalRealtimeProjectionController` | Browser `NO_UI`; owner IM realtime/core integration tests required. |
| `ImPolicySnapshotController` | Browser `NO_UI`; owner IM policy/realtime integration tests required. |
| `OssObjectController` | Indirect browser via avatar/drive/post media; direct grants/lifecycle need owner OSS API tests. |
| `InternalOssObjectController` | Browser `NO_UI`; owner OSS cross-service integration tests required. |
| `PublicFileController` | Browser indirect via `/files/**` avatar/media/drive links and downloads. |

## Final report template

Use this shape in the final answer after executing the skill:

```text
Deployment:
- command:
- health:
- base URL:
- mode:
- route/service freshness:
- runtime endpoint resolution:
- supporting services:

Accounts:
- regular:
- second user:
- admin:
- moderator:
- ownership paths:

Feature results:
- Auth:
- Client infra/session/idempotency:
- Direct HTTP surfaces:
- Posts/content:
- Search:
- Social/profile:
- Messages/IM:
- Notices:
- Wallet:
- Market:
- Drive:
- Moderation:
- Analytics:
- Ops:
- Admin users:
- Settings/avatar:
- Preview/system:
- Accessibility/screenshots:

Failures:
- route/action:
- request:
- status:
- role:
- expected:
- actual:
- console:
- screenshot:
- diagnosis:

Skipped or NO_UI:
- item:
- reason:
- owner test layer:

Artifacts:
- screenshots:
- test data prefix:
- cleanup status:
- supporting Vitest/API tests:
```
