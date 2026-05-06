# Community Frontend Product Redesign Design

Date: 2026-05-06

## Status

Accepted for planning.

The user chose a full-platform redesign and prioritized final product quality over a short delivery cycle. This spec defines the product direction, UI system, page scope, and implementation boundaries for turning the current Vue SPA into a mature product surface.

## Context

The current frontend is a Vue 3 + Vite SPA with routes for discussion, search, member profiles, social lists, bookmarks, market, wallet, orders, messages, notices, settings, analytics, moderation, admin user management, wallet admin, market disputes, and ops.

The existing UI has useful functional coverage, but the product surface still reads like a collection of separate demo pages:

- Public pages use an editorial shell with dark sidebar, large paper-like cards, heavy shadows, uppercase labels, and repeated explanatory copy.
- Work surfaces often stack page containers, hero panels, cards, and subcards even when a simpler list/detail layout would be clearer.
- Mobile navigation has a known shell conflict where the sidebar and bottom navigation can both be visible.
- Real data, empty data, and development/debug data are not visually separated enough.
- Admin and workflow pages are functionally serious but still look like generic cards rather than an operations-grade console.

The redesign should preserve the existing business capabilities while replacing the UI direction, information architecture, and cross-page interaction model.

## Product Thesis

Community should feel like a mature integrated platform:

```text
Discussion + member identity + marketplace + wallet + governance
```

The product should use a professional SaaS foundation, with community content density where discussion surfaces need it.

Visual direction:

- Primary influence: Linear / Stripe style restraint, clarity, density, and operational polish.
- Secondary influence: GitHub / Reddit style scan-friendly discussion and identity surfaces.

This is not a marketing site, not an editorial magazine, and not a playful consumer marketplace. It is a durable community operating product.

## Goals

- Establish a unified product shell across public, personal, trading, and admin surfaces.
- Replace decorative editorial styling with a restrained design system suitable for repeated work.
- Reorganize navigation around product workspaces instead of a long flat page list.
- Make every major route look like part of one product.
- Make mobile and desktop layouts both first-class.
- Normalize loading, empty, error, permission, pending, and debug states.
- Keep discussion readable, marketplace trustworthy, wallet safe, and admin pages operational.
- Keep existing frontend technical constraints: Vue 3, Vue Router, Pinia, CSS files, and Vitest.

## Non-Goals

- Do not change backend DDD package rules or backend API ownership.
- Do not redesign business rules for posting, trading, wallet ledger, moderation, or IM persistence.
- Do not introduce a new frontend framework or large UI library unless a later implementation plan justifies it.
- Do not turn the product into a landing page.
- Do not hide unfinished or pending backend semantics behind fake completed states.

## Information Architecture

The top-level product model should be four workspaces:

| Workspace | Purpose | Primary Routes |
| --- | --- | --- |
| Community | Read, publish, search, save, and inspect members | posts, post detail, search, bookmarks, user profile, followees, followers |
| Trading | Browse, publish, buy, sell, and resolve marketplace orders | market, listing detail, publish, my listings, inventory, buying orders, selling orders, order detail, addresses |
| Personal | Account-level operations and communication | wallet, messages, message detail, notices, notice detail, settings |
| Admin | Governance, metrics, risk, and operations | analytics, moderation, user management, wallet admin, market disputes, ops |

Navigation should make the current workspace obvious and keep secondary items grouped. The sidebar can show all available workspaces on desktop, while mobile should use a small bottom navigation plus an explicit menu or account entry. Mobile must not show the desktop sidebar and bottom navigation at the same time.

### In-Scope Route Inventory

The redesign scope includes all user-facing routes currently registered in `frontend/src/router/index.js`:

| Area | Routes |
| --- | --- |
| Account | login, register, password reset |
| Community | posts, post detail, search, bookmarks, user profile, followees, followers |
| Trading | market list, listing detail, publish listing, my listings, inventory, buying orders, selling orders, order detail, addresses |
| Personal | wallet, conversations, conversation detail, notices, notice detail, settings |
| Admin | analytics, moderation, user management, wallet admin, market disputes, ops console |
| System | forbidden, not found |

Editorial preview routes and the development route should not drive the product design. If retained, they should be visually marked as preview/development surfaces and kept out of normal user navigation.

## Product Shell

The shell should become a product workspace instead of an editorial frame.

Desktop requirements:

- Sidebar groups routes by workspace and permission.
- Topbar contains page title, concise scope text, account controls, state indicators, and search only on routes where the search action can produce useful results. Routes without search behavior should not render inert search controls.
- Main content uses predictable max widths and optional secondary context columns.
- Admin routes may use denser tables and filters but should still share the same product tokens.

Mobile requirements:

- One navigation model is visible at a time.
- Bottom navigation contains only high-frequency entries.
- Secondary navigation opens as a drawer or menu.
- Page actions collapse into explicit menus when horizontal space is limited.
- Content must remain readable at 390px width without overlapping controls.

## Visual System

The UI system should be calm, product-grade, and data-forward.

Typography:

- Use the sans font as the default across the app.
- Reserve display/serif typography for long-form content moments only, not routine page titles or cards.
- Avoid letter-spaced uppercase labels except for rare status or metadata.
- Use a small, repeatable type scale for page titles, section titles, list titles, body text, metadata, and captions.

Color:

- Use neutral surfaces and one main accent.
- Use semantic colors only for status: success, warning, danger, pending, disabled, unread.
- Avoid decorative gradients behind routine work surfaces.
- Preserve strong contrast in both light and dark themes.

Shape and elevation:

- Standard radius should be modest, usually 6px to 12px.
- Cards should not be the default layout primitive.
- Shadows should be rare and used for floating elements such as menus, dialogs, drawers, and toasts.
- Borders, spacing, and hierarchy should do most of the layout work.

Density:

- Default product density should be compact but readable.
- Lists, tables, toolbars, filters, and detail pages should use stable row heights and predictable alignment.
- Existing density settings in the UI store may be retained if they become consistently applied.

## Component Rules

Shared components should support the redesign instead of each page inventing its own shell.

Required shared patterns:

- Page header: title, scope text, breadcrumbs on detail/nested workflow pages, primary action, secondary actions.
- Toolbar: filters, sort, search, view mode, and batch actions.
- Object list: posts, users, listings, orders, notices, messages, moderation items.
- Detail layout: primary body plus optional right-side context.
- Status badge: semantic status with consistent labels.
- Empty state: compact, honest, and action-oriented.
- Loading state: skeletons only where they preserve layout; otherwise simple progress.
- Error state: message, trace id when available, retry action.
- Permission state: clear reason and safe next action.
- Modal/drawer: for confirmation, secondary forms, and mobile navigation.

Cards are allowed only when the card itself represents an object or interaction:

- Post preview card.
- Listing card.
- Order summary.
- User card.
- Notification row/card.
- Admin review item.

Cards should not be used as generic wrappers around every section.

## Content Rules

Copy should sound like product UI, not documentation or marketing.

Rules:

- Page subtitles should explain scope or freshness, not restate the route name.
- Remove repeated explanatory paragraphs when labels and layout already communicate the behavior.
- Use concrete action labels: "Publish", "Review", "Transfer", "Resolve", "Retry", "View order".
- Keep development/debug helpers out of normal user flows.
- If a debug artifact must exist locally, it should be visually marked as development-only and hidden from production-like surfaces.
- Real values, empty values, pending values, and unavailable values must be visually distinct.

## Data Truth And State Semantics

The frontend must not present uncertain data as completed fact.

Required user-visible semantics:

- Marketplace wallet actions with pending backend processing should be shown as processing, not completed.
- Search and notice projections may lag and should not promise immediate consistency.
- IM push is best effort; conversation history remains the source for recovery.
- Like/follow counts may update optimistically but should reconcile with owner API reads.
- Wallet ledger is the source of truth for balances and transactions.

The redesign must include a consistent state language:

| State | UI Meaning |
| --- | --- |
| Empty | No records exist for the current scope or filter |
| Loading | The app is fetching data |
| Pending | User action accepted but backend processing is not final |
| Failed | User action or fetch failed and can be retried or inspected |
| Forbidden | User lacks permission |
| Unavailable | Capability exists but required data or integration is absent |
| Development-only | Helper visible only in local/dev contexts |

## Page Requirements

### Community

Posts should become the main discussion workspace:

- Feed layout should prioritize scan speed: title, author, time, category/tag, reply/like stats, unread/new indicators.
- Composer should be available without dominating the whole first viewport.
- Filters and sort should live in a toolbar, not scattered across cards.
- Post detail should use a reading column plus thread context and reply composer.
- Search should feel like a utility workbench: query, filters, results, and empty/error states.
- Member profiles should show identity, relationship, activity, and trust signals without fake snapshot copy.
- Followers, followees, and bookmarks should reuse object list patterns.

### Trading

Trading surfaces should feel trustworthy and transaction-oriented:

- Market list should open with filters, categories, listing rows/cards, and clear price/stock/status signals.
- Listing detail should prioritize price, delivery mode, seller, stock, fulfillment rules, and primary action.
- Publish and inventory pages should behave like forms/workflows, not hero pages.
- Buying and selling orders should use status-first lists with clear next action.
- Order detail should show lifecycle, payment/escrow status, delivery, dispute, and audit context.
- Addresses should be a compact management surface with default address and edit/delete actions.

### Personal

Personal surfaces should be quiet and task-focused:

- Wallet should show balance, pending amounts, available actions, and ledger history with clear risk copy.
- Messages should use an inbox + conversation detail pattern.
- Notices should use grouped notification lists and topic detail.
- Settings should separate public profile, avatar, account identity, and preferences.

### Admin

Admin must feel like an operations console:

- Analytics should use filters, KPI groups, trend modules, freshness, and known-data limitations.
- Moderation should show queue, evidence, status, decision actions, and audit trail.
- User management should make role changes, risks, and audit reason explicit.
- Wallet admin should emphasize dangerous actions, constraints, auditability, and trace ids.
- Market disputes should show buyer/seller claims, order state, funds state, evidence, and final decision.
- Ops console should be visually distinct as a high-risk surface with confirmation and scope controls.

## Accessibility And Interaction

The redesign must preserve basic accessibility:

- Keyboard reachable navigation, menus, dialogs, forms, and list actions.
- Visible focus states.
- Clear labels for icon-only buttons.
- Form errors associated with fields.
- Touch targets large enough on mobile.
- No overlapping text or controls at common mobile widths.
- Reduced motion should be respected if motion is added.

Motion should be restrained:

- Use transitions for drawer/modal open/close, route fade, hover affordance, and async state changes.
- Avoid ornamental motion that distracts from reading or operations.

## Technical Design

The implementation should stay within the existing frontend stack.

Expected work areas:

- `frontend/src/router/navigation.js`: regroup navigation around the new workspaces.
- `frontend/src/components/layout/*`: redesign shell, sidebar, topbar, mobile navigation, and auth shell.
- `frontend/src/components/ui/*`: normalize page header, empty state, buttons, badges, inputs, cards, modal, pagination, select, avatar, file input, and scroll affordances.
- `frontend/src/styles/*`: replace editorial defaults with product tokens, layout primitives, component rules, and page-specific exceptions.
- `frontend/src/views/*.vue`: migrate pages to the new shared patterns.
- `frontend/src/views/*State.js`: keep non-trivial state transformations testable outside Vue templates.
- `frontend/src/api/services/*.js`: no redesign-driven API shape changes unless a page exposes existing service semantics incorrectly.

Implementation should avoid mixing broad visual rewrites with unrelated backend behavior changes.

## Testing And Verification

The implementation plan should include verification at several levels:

- Unit tests for changed pure state helpers and navigation helpers.
- Component or view tests for critical route states where existing tests already cover behavior.
- `npm test` for frontend after broad changes.
- `npm run build` for production bundle validation.
- Browser checks for desktop and mobile routes.
- Explicit mobile verification at 390px width for shell/nav overlap.
- Manual review of representative pages from each workspace.

Representative browser routes:

- `/#/posts`
- `/#/posts/:postId`
- `/#/search`
- `/#/market`
- `/#/market/listings/:listingId`
- `/#/wallet`
- `/#/messages`
- `/#/notices`
- `/#/settings`
- `/#/analytics`
- `/#/moderation`
- `/#/admin/users`

Some routes require authentication or seeded data. The implementation plan should identify which routes can be visually checked anonymously, which need a dev account, and which need seed fixtures.

## Rollout Plan

The user chose full-platform scope, but implementation should still be staged to control risk:

1. Product foundation: shell, navigation, tokens, shared UI rules, state components, mobile fix.
2. Community workspace: posts, post detail, search, profiles, social lists, bookmarks.
3. Trading workspace: market list/detail, publish, inventory, buying/selling orders, order detail, addresses.
4. Personal workspace: wallet, messages, notices, settings.
5. Admin workspace: analytics, moderation, user management, wallet admin, disputes, ops.
6. Polish pass: copy reduction, state consistency, dark theme, responsive audit, accessibility pass.

Each stage should leave the app shippable and avoid half-migrated shared components that break untouched pages.

## Acceptance Criteria

The redesign is complete when:

- All in-scope routes listed in this spec use the new shell family: workspace navigation for Community/Trading/Personal/Admin routes, auth shell for Account routes, and system-state shell for System routes.
- Desktop and mobile have one coherent navigation model without sidebar/bottom-nav conflict.
- Public, personal, trading, and admin surfaces share the same design system.
- Page hierarchy is based on headers, toolbars, lists, details, and contextual panels rather than nested decorative cards.
- Debug/development artifacts are not shown as normal product content.
- Empty, loading, error, forbidden, pending, and unavailable states are consistent.
- Discussion pages remain content-dense and readable.
- Trading pages communicate trust, price, status, inventory, and next action.
- Wallet and admin pages communicate risk and auditability.
- Frontend tests and build pass for the changed frontend.
- Browser checks cover at least one representative route per workspace on desktop and mobile.

## Documentation Follow-Up

After implementation changes are made, update `docs/handbook/frontend.md` if route grouping, navigation semantics, shell behavior, UI state contracts, or page-state helper responsibilities change.

This spec is a planning artifact. It does not replace the handbook as the long-term SSOT for implemented frontend behavior.
