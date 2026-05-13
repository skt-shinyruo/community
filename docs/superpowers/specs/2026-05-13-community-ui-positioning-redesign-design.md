# Community UI Positioning Redesign Design

Date: 2026-05-13

## Status

Draft for implementation planning.

This spec turns the May 13 browser review into concrete UI redesign requirements. It refines the broader May 6 product redesign direction with page-level findings from the running local single topology at `http://localhost:12881`.

## Context

The app is a Vue 3 SPA for a community platform with discussion, search, member identity, social relations, notices, IM, wallet, market, drive, settings, and role-gated admin surfaces.

The current UI is functional and mostly healthy at runtime. During browser review with user `aaa`, API calls for posts, market, wallet, drive, IM conversations, notices, and profile all returned successfully, and no console errors were observed. The remaining problem is product expression:

- The shell reads as a generic workbench instead of a community product.
- The posts homepage opens with filters and empty state before it creates the feeling of an active discussion space.
- Market, wallet, drive, notices, messages, and profile expose technical or implementation copy in normal product surfaces.
- Trading and wallet screens do not yet communicate trust, escrow, fulfillment, ledger status, or risk well enough.
- Mobile navigation gives high priority to market but not to notifications or private messages, which are more central to community retention.
- Several pages use large wrappers, repeated headers, and card-like sections where a denser list/detail product layout would be clearer.

This spec does not replace `2026-05-06-community-frontend-product-redesign-design.md`. It narrows the next redesign pass around project positioning and user-facing details found in the current app.

## Product Positioning

Community should present itself as:

```text
A discussion-centered community where member identity, relationships, notifications, private messages,
trusted trading, wallet escrow, file sharing, and governance all orbit the same community account.
```

The product should not feel like:

- A generic admin dashboard.
- A standalone marketplace.
- A standalone cloud drive.
- A backend demo console.
- A marketing or editorial landing page.

Every major page should answer one of these user questions quickly:

- What is happening in the community?
- Who is this member and why should I trust or follow them?
- What needs my attention?
- What did I buy, sell, send, receive, or store?
- What action is safe to take next?

## Design Thesis

Visual thesis:

Calm, dense, trust-oriented product UI with discussion content at the center, restrained neutral surfaces, small semantic color, and visible member context.

Content thesis:

Use product language for user tasks and state. Remove implementation notes from normal UI. Show uncertainty honestly through state labels such as pending, unavailable, syncing, or development-only.

Interaction thesis:

Prioritize fast scanning and safe action: stable lists, clear primary actions, inline context, mobile-first communication paths, and drawers/modals only for secondary workflows or confirmations.

## Goals

- Reposition the whole frontend around community discussion and member identity.
- Make the posts homepage feel like the primary community surface, not a filter test page.
- Make notifications and private messages first-class community loops.
- Make market and wallet feel trustworthy through escrow, fulfillment, status, and risk signals.
- Make drive feel connected to posts, sharing, and virtual goods instead of an unrelated file manager.
- Remove engineering, version, placeholder, and "future work" copy from product surfaces.
- Align desktop and mobile navigation with high-frequency community behavior.
- Reduce generic card stacking and use lists, detail panes, toolbars, and contextual panels.

## Non-Goals

- Do not change backend business rules, DDD boundaries, ownership, or API contracts solely for visual reasons.
- Do not fake data that the backend cannot provide.
- Do not hide unavailable data as if it were real.
- Do not introduce a new frontend framework.
- Do not build a landing page.
- Do not redesign admin permissions or server-side authorization.

## Information Architecture

### Desktop Navigation

Regroup navigation by user intent:

| Group | Routes | Notes |
| --- | --- | --- |
| Community | posts, search, notices, messages, bookmarks, profile | Discussion, attention, and relationship loops should be first. |
| Trade & Assets | market, buying orders, selling orders, my listings, publish listing, wallet, addresses | Market and wallet are linked by escrow and transactions. |
| Files & Account | drive, settings | Drive is personal utility and sharing support. |
| Admin | moderation, analytics, users, wallet admin, disputes, ops | Render only for roles that can access these routes. |

`notices` and `messages` should move out of a low-priority "personal" cluster because they are core community activity surfaces.

### Mobile Navigation

Mobile bottom navigation should optimize for daily community behavior:

```text
讨论 / 搜索 / 通知 / 私信 / 我
```

Market, wallet, drive, settings, and admin routes should remain reachable from `我` or a menu/drawer. If later product strategy makes marketplace a top-level mobile business goal, that should be a separate decision backed by usage priority.

Mobile must not expose the desktop sidebar as a persistent semantic navigation landmark when it is visually closed. Closed mobile navigation should not create focus or screen-reader clutter.

### Topbar

The topbar should not always say `DISCUSSION WORKSPACE`. Route scope labels should reflect the current area:

| Area | Scope Label |
| --- | --- |
| Posts/Search/Profile | Community |
| Notices/Messages | Inbox |
| Market/Orders/Wallet | Trade & Assets |
| Drive | Files |
| Settings | Account |
| Admin | Operations |

Shell search should render only where it has useful behavior. Search placeholder copy should state the scope, such as `搜索讨论、标签或成员`.

## Global UI Rules

### Copy

Remove or rewrite product-facing implementation copy, including:

- `第一版`
- `后续`
- `暂未返回`
- `待同步`
- `钱包页为准`
- `前台只按...`
- `当前主页还未接入...`
- raw `ACTIVE` status labels
- raw member UUIDs as primary identifiers

Acceptable replacements:

- `暂无记录`
- `同步中`
- `处理中`
- `可用`
- `私有`
- `可分享`
- `托管中`
- `等待履约`
- `需要处理`
- `仅自己可见`

Development-only state may still exist locally, but it must be explicitly marked and separated from ordinary product content.

### Layout

- Use a single page title layer. Avoid topbar title plus page header plus card header repeating the same noun.
- Use object lists for posts, notifications, conversations, orders, files, and transactions.
- Use right-side detail/context panels where the user selects an object from a list.
- Keep cards for objects and interaction containers only.
- Reduce large radii on routine work surfaces to the 8px to 12px range.
- Avoid nested cards and decorative shadows on routine sections.

### Visual Language

- Keep the neutral product palette but use semantic colors deliberately:
  - unread/new: blue
  - success/available: green
  - warning/pending/risk: amber
  - danger/destructive: red
  - neutral/private/archived: gray
- Typography should be sans-first for product UI.
- Serif/display typography should not be used for routine list titles or controls.
- Iconography should be unified; prefer the existing app icon pattern or a single icon source in implementation.

## Page Requirements

### Posts Homepage

Current issue:

The first viewport is dominated by title copy, toolbar controls, counters, compose strip, and empty state. It does not immediately communicate active community discussion.

Required redesign:

- Make the discussion feed the primary surface.
- Use a scan-friendly topic list by default: title, author, avatar, category, tags, created/last activity time, reply count, like count, unread/new status.
- Keep only high-value filters visible: latest, hot, following/subscribed, unread.
- Move category and tag filters into a compact toolbar or disclosure when screen width is tight.
- Keep the composer as a lightweight affordance that does not dominate the first viewport.
- Add a right context column on desktop for hot tags, active categories, unread summary, or member activity when data is available.
- Empty state should offer useful next actions: start a discussion, view hot topics/tags, or reset filters.

Acceptance details:

- The first desktop viewport should show the feed area even when the list is empty.
- Mobile should show feed controls without horizontal overflow.
- Empty copy should not explain implementation constraints.

### Post Detail

Required redesign:

- Use a reading column for the post and a reply thread below or beside it depending on width.
- Keep author identity, category, tags, status, and actions close to the post title.
- Put moderation/report/bookmark/like actions in predictable locations.
- Reply composer should be easy to find but not visually heavier than the thread.
- Media and block content should preserve reading rhythm and not cause layout shifts.

### Search

Required redesign:

- Treat search as a utility workbench.
- Show query input, filters, result count/freshness, and results in one clear flow.
- Search results should reuse topic row patterns.
- Empty state should distinguish no results from no query.

### Member Profile

Current issue:

The profile has the right concepts but surfaces raw UUID and implementation caveats such as wallet uncertainty.

Required redesign:

- Make profile an identity center: avatar, display name, role/badge, join date, intro, relationship state.
- Hide full user ID behind copy/details action or show shortened ID.
- Use tabs or sections for activity, posts, comments, relationships, and trust/contribution signals.
- Remove wallet balance caveats from public profile. Private financial facts should not be shown publicly unless explicitly supported by product rules.
- Convert `钱包页为准` and similar copy into either hidden data or a clear unavailable/private state.

### Notices

Current issue:

Topic grouping is sound, but the page behaves like category cards rather than an actionable inbox.

Required redesign:

- Keep topic grouping for comments, likes, follows, and moderation.
- Add latest context per topic when data exists: actor, target title, time, unread count.
- Provide clear actions: mark all read, refresh, open topic.
- Detail pages should show notification rows with actor, event, target, time, and read state.
- Empty state should explain that notifications appear when members interact or governance status changes.

### Messages

Current issue:

The page copy is directionally correct, but the UI is not yet a true inbox.

Required redesign:

- Desktop layout: conversation list on the left, selected conversation on the right.
- Optional member/context panel on wide screens when useful data exists.
- Mobile layout: conversation list and conversation detail as separate navigation states.
- Conversation rows should show avatar, display name, last message, time, unread count, and sync state.
- Do not use `成员 #UUID` as the main participant label when a username can be resolved.
- Empty state should offer a path back to community profiles or discussion contexts.

### Market

Current issue:

The market reads as an empty listing page and does not communicate community trust or wallet escrow.

Required redesign:

- Market list should show trust and transaction signals: seller identity, price, stock, fulfillment type, escrow status, listing status, and dispute/guarantee copy.
- Add compact filters for goods type, status, price, and search when supported.
- Place `我的购买`, `我的出售`, and pending order counts near the top because buyer/seller follow-up is high priority.
- Empty state should explain how community trading works: list an item, buy through wallet escrow, resolve disputes through governance.
- Listing detail should prioritize price, seller, fulfillment, stock, purchase action, escrow/risk note, and description.
- Publish flow should read as a workflow form, not a promotional page.

### Orders And Addresses

Required redesign:

- Buying and selling order lists should be status-first.
- Order rows should show counterparty, listing title, amount, escrow/funds state, fulfillment state, and next action.
- Order detail should show lifecycle timeline: created, paid/escrowed, fulfilled/shipped, received/completed, disputed/resolved.
- Address management should be compact with default address, edit, delete, and validation states.

### Wallet

Current issue:

The page reads as a form demo and exposes "current session" and future-work copy.

Required redesign:

- Top summary should show available balance, escrow/frozen amount, pending amount, and recent ledger activity where data exists.
- If backend only returns balance, show unavailable secondary metrics as unavailable, not as future work.
- Transaction actions should use focused panels/drawers or a segmented action area instead of three equal cards.
- Recent transactions should be the main body: type, amount, counterparty, status, time, and trace/details when relevant.
- Transfer should support member lookup or make clear that an exact member ID is required as a temporary local constraint.
- Risk copy should appear next to irreversible or pending operations.

### Drive

Current issue:

Drive layout is close to a real tool, but it feels disconnected from the community.

Required redesign:

- Keep list + detail structure.
- Replace technical labels:
  - `ACTIVE` -> `可用`
  - `仅内部` -> `私有`
  - `可分享` can remain if paired with clear sharing controls.
- Add product context where supported: file can be used for post media, share links, or virtual goods fulfillment.
- Detail panel should show share state, download/share/delete actions, and references when available.
- Upload/new folder/search controls should remain compact and aligned.

### Settings

Required redesign:

- Separate public profile, avatar, account identity, and preferences.
- Make avatar upload state clear.
- Avoid showing raw technical identity as the main account content unless needed for support/debug copy.

### Forbidden And System States

Current behavior:

Unauthorized access to moderation with `aaa` correctly routes to 403.

Required redesign:

- Keep server authority clear.
- 403 copy should be concise and should not over-explain implementation.
- Include safe actions: return to discussion, switch account/login, or go to profile depending on auth state.

### Admin Surfaces

Required redesign:

- Admin routes should use an operations console style: dense, auditable, risk-aware.
- Analytics should not include future-work copy as normal UI. Use data freshness and limitations instead.
- Moderation should show queue, evidence, decision action, status, and audit trail.
- User management should make role changes and audit reason explicit.
- Wallet admin and disputes should emphasize funds state, risk, constraints, and trace/audit context.
- Ops console should remain visually distinct as high risk.

## Responsive Requirements

Desktop:

- `1440x900` should show workspace navigation, page body, and any secondary context without excessive vertical chrome.
- Feed/list pages should make the list visible in the first viewport.

Mobile:

- `390x844` must have no overlapping text or controls.
- Bottom navigation should use the final high-frequency entries.
- Closed side navigation should not remain a semantic/focus burden.
- Toolbars should wrap or collapse into menus cleanly.
- Object rows should keep primary text readable without pushing actions off-screen.

## Accessibility Requirements

- Navigation, menus, drawers, forms, list rows, and modals must be keyboard reachable.
- Focus states must be visible.
- Icon-only buttons need labels/tooltips.
- Form errors should be associated with relevant fields.
- Status labels should not rely on color alone.
- Empty/error/loading/forbidden states should use live region behavior only where it helps.

## Implementation Boundaries

Likely frontend files:

- `frontend/src/router/navigation.js`
- `frontend/src/components/layout/AppShell.vue`
- `frontend/src/components/layout/SidebarNav.vue`
- `frontend/src/components/layout/Topbar.vue`
- `frontend/src/components/layout/MobileNav.vue`
- `frontend/src/components/ui/*`
- `frontend/src/styles/variables.css`
- `frontend/src/styles/layout.css`
- `frontend/src/styles/components.css`
- `frontend/src/styles/pages.css`
- `frontend/src/views/PostsView.vue`
- `frontend/src/views/PostDetailView.vue`
- `frontend/src/views/SearchView.vue`
- `frontend/src/views/UserProfileView.vue`
- `frontend/src/views/NoticesView.vue`
- `frontend/src/views/NoticeDetailView.vue`
- `frontend/src/views/ConversationsView.vue`
- `frontend/src/views/ConversationDetailView.vue`
- `frontend/src/views/MarketListView.vue`
- `frontend/src/views/MarketDetailView.vue`
- `frontend/src/views/Market*View.vue`
- `frontend/src/views/WalletView.vue`
- `frontend/src/views/DriveView.vue`
- `frontend/src/views/SettingsView.vue`
- admin views under `frontend/src/views/*Admin*.vue`, `ModerationView.vue`, `AnalyticsView.vue`, `OpsConsoleView.vue`
- related `frontend/src/views/*State.js` helpers and tests

Backend work is out of scope unless an existing UI requirement cannot be represented honestly with available API data. If that happens, the implementation plan should either mark the field unavailable or create a separate backend/API spec.

## Testing And Verification

Implementation should verify:

- Navigation helper tests for regrouped desktop/mobile entries and role visibility.
- View/state tests for changed pure helpers.
- Existing route/auth guard tests still pass.
- `npm test` from `frontend/`.
- `npm run build` from `frontend/`.
- Browser checks on desktop `1440x900` and mobile `390x844`.
- Representative logged-in checks with `aaa`:
  - `/#/posts`
  - `/#/market`
  - `/#/wallet`
  - `/#/drive`
  - `/#/messages`
  - `/#/notices`
  - `/#/users/00000000-0000-7000-8000-000000000001`
  - `/#/403`
- Admin checks should use an account with `ROLE_ADMIN` or `ROLE_MODERATOR` if available; otherwise verify permission states only.

Screenshots should be captured after implementation for the representative routes and reviewed for:

- no overlapping controls
- no engineering copy in product surfaces
- correct mobile nav priority
- feed/list visibility in first viewport
- trust/risk/status signals on market and wallet

## Rollout Plan

Use staged implementation to keep the app shippable:

1. Navigation and shell positioning: route groups, topbar scope labels, mobile nav, sidebar semantics.
2. Global copy and token cleanup: remove engineering copy, normalize status labels, reduce card/radius excess.
3. Community loop: posts, post detail, search, profile.
4. Attention loop: notices and messages.
5. Trust loop: market, orders, wallet.
6. Utility loop: drive and settings.
7. Admin and system states.
8. Responsive, accessibility, dark theme, and screenshot polish.

## Acceptance Criteria

The redesign pass is complete when:

- The UI clearly presents Community as a discussion-centered platform with member identity and trust systems.
- Desktop navigation groups routes by user intent, not just technical modules.
- Mobile bottom navigation prioritizes discussion, search, notices, messages, and profile/account.
- Posts homepage shows the discussion surface as the main event.
- Market communicates seller identity, fulfillment, escrow/trust, price, stock, status, and next action.
- Wallet communicates available balance, pending/unavailable states, transaction actions, and ledger history without demo copy.
- Drive uses product labels and connects file management to sharing/community use.
- Notices and messages behave like actionable inboxes.
- Profile no longer exposes raw UUID or implementation caveats as primary content.
- Product-facing pages do not contain future-work or backend implementation notes.
- Routine UI uses lists, toolbars, detail panes, and contextual panels instead of nested decorative cards.
- Desktop and mobile layouts pass browser review at the specified sizes.
- Frontend tests and build pass.

## Documentation Follow-Up

After implementation, update `docs/handbook/frontend.md` if route grouping, shell semantics, mobile navigation entries, state language, or shared UI responsibilities change.

This spec is a planning artifact. The handbook remains the long-term SSOT for implemented frontend behavior.
