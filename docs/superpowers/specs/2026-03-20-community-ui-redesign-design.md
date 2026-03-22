# Community Frontend UI Redesign Spec

**Date:** 2026-03-20
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

Redesign the entire Vue frontend so the product reads as a deep-discussion community instead of a collection of functional pages with inconsistent styling. The new frontend must:

- make discussion content feel primary on public pages
- make moderation and operations pages feel like calm, efficient tools
- unify layout, typography, spacing, navigation, and state treatment across the whole app
- remove placeholder and mock UI that weakens product credibility

This is an intentionally aggressive redesign. Existing page structure, visual language, and component usage can change substantially as long as existing business capabilities remain available.

---

## 2. Confirmed Product Direction

The following decisions were explicitly confirmed during brainstorming:

- Visual direction: aggressive redesign is allowed
- Scope: entire site, including public pages, shell, messaging, settings, and admin pages
- Product mood: deep-discussion community
- Frontstage/backstage split: public pages should feel immersive and reading-oriented; admin pages should feel more restrained and operational
- Placeholder handling: mock and half-finished UI may be removed rather than preserved
- Recommended overall approach: "discussion workspace"

---

## 3. User Experience Problems to Solve

Current UI problems observed from code review:

- Page-level styling bypasses the design tokens and base components through heavy inline styling
- Public pages prioritize tabular metadata over reading flow
- Topbar, sidebar, right panel, auth shell, and admin pages use inconsistent interaction and icon language
- Mobile behavior adapts the shell but not the content patterns
- Several pages still present mock tabs, placeholder charts, fake trending modules, or debugging-style UX
- Messaging, notices (`/notices` route family), settings, and admin pages feel like isolated utilities instead of part of one product

The redesign must solve these problems systemically rather than by touching isolated pages.

---

## 4. Information Architecture

The redesign keeps the existing route map and business capabilities, but changes how those routes are grouped and presented.

### 4.1 Public Product

Public-facing routes should read as one continuous discussion product:

- posts list
- post detail
- search
- profile
- bookmarks
- leaderboard
- follow/follower pages
- notices
- messaging
- settings

### 4.2 Admin Product

Admin-facing routes should feel like one quiet operations desk:

- analytics
- moderation
- ops console
- user management

The public and admin products should share the same technical foundation and token system, but not the same emotional presentation.

### 4.3 Route Scope Classification

To keep planning explicit, every current route family is classified below.

#### Core redesign

These routes are first-class redesign targets and may receive substantial template/layout changes:

- `/posts`
- `/posts/:postId`
- `/search`
- `/messages`
- `/messages/:conversationId`
- `/notices`
- `/notices/:topic`
- `/bookmarks`
- `/leaderboard`
- `/settings`
- `/users/:userId`
- `/users/:userId/followees`
- `/users/:userId/followers`
- `/analytics`
- `/moderation`
- `/ops`
- `/admin/users`

#### Final polish / compatibility pass

These routes must be visually aligned with the new system, but do not drive the core design language:

- `/auth/login`
- `/auth/register`
- `/auth/password/reset`
- `/auth/activation/:userId/:code`
- `/403`
- `/:pathMatch(.*)*`

For these pages, the goal is not bespoke redesign depth. The goal is clear visual compatibility with the new system, good spacing, clean state handling, and removal of obviously outdated styling.

#### Out of scope for product redesign

- `/dev`

`/dev` must remain functional, but does not need the same product-grade redesign treatment as user-facing or admin-facing routes.

---

## 5. Visual Language

### 5.1 Public Pages

Public pages should use a warm, editorial discussion aesthetic:

- warm neutral backgrounds rather than cold SaaS slate
- dark brown or near-ink titles with softer secondary copy
- larger title hierarchy and roomier vertical rhythm
- card surfaces that feel like reading sheets, not dashboard widgets
- tags and controls that support discussion context without visually overwhelming content

Target impression:

- thoughtful
- calm
- text-forward
- discussion-first

Not the target impression:

- generic SaaS dashboard
- fast-scrolling social feed
- hacker-news minimalism
- glossy consumer app chrome

### 5.2 Admin Pages

Admin pages should deliberately step away from the warm editorial look:

- cooler neutral palette
- stronger table/filter/status readability
- clearer danger affordances
- lower decorative weight
- compact but not cramped density

Target impression:

- controlled
- inspectable
- reliable
- operational

---

## 6. Shared Design System Changes

The redesign must replace the current "tokens exist but pages escape them" situation with a stronger shared system.

### 6.1 Tokens

Revise global design tokens to include:

- public palette tokens
- admin palette tokens or contextual variants
- refined typography scale
- spacing rules for editorial layouts and tool layouts
- page width rules for reading pages, workspace pages, and tool pages
- richer surface and border tokens for layered layouts

### 6.2 Component Contract

Core UI primitives must become the default path instead of something pages bypass:

- button
- input
- textarea
- card/sheet
- page header
- badge/tag
- modal
- empty/error/loading states
- inline metadata rows
- list item shells

The redesign should reduce inline style usage drastically. New page structure should prefer named classes and shared component variants.

### 6.3 State Treatment

Loading, empty, success, warning, and error states should be visually coherent across the product. The current mix of ad hoc `div.muted`, inline paddings, and page-specific empty layouts should be replaced by shared state patterns.

---

## 7. Layout and Navigation

### 7.1 Public Shell

Public shell should remain multi-panel on desktop, but with changed emphasis:

- left navigation remains available, but becomes quieter and more structural
- center content becomes the dominant visual area
- right panel becomes contextual support rather than a decorative column
- topbar becomes simpler and less crowded, with search and essential actions only

### 7.2 Admin Shell

Admin pages should reuse the same technical shell but present as a tool desk:

- admin routes should visually read as a sub-product
- navigation emphasis should shift from exploration to workflow
- KPI summary + filter row + table/list + action panel becomes the dominant page pattern

### 7.3 Mobile

Mobile must not simply shrink desktop shapes. It needs dedicated patterns:

- drawer-based primary navigation
- simpler topbar
- bottom action access where appropriate
- single-column discussion cards
- discussion detail optimized for reading and reply flow
- messaging focused on active thread rather than preserving desktop split layout

---

## 8. Page Family Specifications

### 8.1 Posts List

Current problem:

- reads like a fixed metadata table
- titles and summaries are visually subordinate to stats

New structure:

- a discussion stream of content-led cards/sheets
- each item emphasizes title, taxonomy, summary, author, and recent activity context
- likes/replies remain visible but move to secondary metadata or compact action rows
- toolbar remains powerful, but visually subordinate to the feed itself
- publish composer should feel integrated into the discussion flow, not like an admin form pasted into the page

### 8.2 Post Detail

Current problem:

- reads like stacked functional blocks
- reply hierarchy is hard to scan

New structure:

- main post reads like an article or discussion opener
- comments read like a discussion room
- reply nesting remains, but is visually guided rather than noisy
- author, timestamps, engagement, and moderation affordances are clearer
- comment composer should be part of the conversation flow

### 8.3 Search

Current problem:

- feels like a utility page

New structure:

- search page becomes a research/discovery surface
- results use the same content-led language as posts list
- match context, taxonomy, and follow-on filtering are easy to scan
- admin reindex affordance remains available but is visually contained

### 8.4 Profile

Current problem:

- mock tabs and placeholder activity area

New structure:

- profile becomes a real identity page
- hero/profile block plus real activity stream or meaningful empty state
- metadata and social actions become coherent
- fake tabs and placeholder sections are removed

### 8.5 Messaging, Notices, Settings

Messaging:

- desktop should become inbox + thread
- mobile should focus on the current thread and list transitions
- the conversation view should feel like part of the product, not a standalone demo screen

Notices:

- should use a shared message/feed pattern instead of isolated cards

Settings:

- reorganize into grouped sections such as account, preferences, and sensitive actions
- align forms and explanations with the shared system

Bookmarks, leaderboard, and follow/follower pages:

- these pages do not need a bespoke visual language separate from the rest of the public product
- they should inherit the established public list/profile patterns from earlier redesign stages
- their success criterion is consistency with the redesigned public product, not invention of new page grammar

### 8.6 Admin Pages

Analytics:

- remove placeholder chart treatment
- even if charts remain limited, page must still look intentional
- KPI strip + date/filter controls + content region

Moderation:

- standard operational queue layout
- clear state, priority, action affordances, and review flow

Ops Console:

- compact high-risk action surface
- explicit warning framing

User Management:

- same admin structure as moderation and analytics

---

## 9. Placeholder and Fake UI Removal

The following types of UI should be removed or replaced as part of the redesign:

- fake trending topic blocks with hard-coded content
- fake tabs with no behavior
- placeholder charts presented as finished modules
- dead footer/legal links that pretend to be real product surfaces
- half-debug, half-product interaction patterns such as user IDs shown where real identity presentation should exist

When real data is unavailable, use honest empty or pending states rather than decorative fake data.

---

## 10. Interaction Principles

The redesign should follow these principles:

- reading first, metrics second
- fewer competing highlights per screen
- one obvious primary action per major region
- consistent hover, focus, active, and selected treatment
- clear distinction between public exploration and admin operations
- reduced visual noise in repeated controls

---

## 11. Implementation Constraints

The redesign should preserve:

- existing frontend stack: Vue 3, Vue Router, Pinia, existing API layer
- existing routes and business flows unless a route is explicitly merged or removed because it is placeholder-only
- compatibility with the current backend and API contracts

The redesign may change:

- templates
- class structure
- token values
- shared components
- page composition
- iconography
- responsive rules

Route-level removals or merges are not a primary goal of this redesign. The implementation may remove fake widgets, placeholder sections, dead links, and mock content inside existing routes. Actual route removal or route merging should only happen if a route is demonstrably placeholder-only and has no meaningful user workflow value. Planning should assume route continuity unless a specific route is called out explicitly.

---

## 12. Migration Strategy Expectations

Implementation planning should assume a staged migration, not one giant blind rewrite.

Recommended migration order:

1. token system and shell foundations
2. public core path: posts, post detail, search, profile
3. supporting social pages: messaging, notices, settings, follow pages, leaderboard, bookmarks
4. admin product pages
5. final consistency pass and responsive cleanup

This order matters because public core path and shell decisions define the visual grammar for the rest of the app.

Routes mapped to each stage:

1. token system and shell foundations
   - all shared shell/layout primitives
   - public/admin visual split
2. public core path
   - `/posts`
   - `/posts/:postId`
   - `/search`
   - `/users/:userId`
3. supporting social pages
   - `/messages`
   - `/messages/:conversationId`
   - `/notices`
   - `/notices/:topic`
   - `/settings`
   - `/users/:userId/followees`
   - `/users/:userId/followers`
   - `/bookmarks`
   - `/leaderboard`
4. admin product pages
   - `/analytics`
   - `/moderation`
   - `/ops`
   - `/admin/users`
5. final consistency pass
   - `/auth/login`
   - `/auth/register`
   - `/auth/password/reset`
   - `/auth/activation/:userId/:code`
   - `/403`
   - `/:pathMatch(.*)*`

---

## 13. Success Criteria

The redesign is successful when:

- the app reads as one coherent product rather than many page-specific styles
- public pages clearly emphasize reading and discussion
- admin pages clearly emphasize control and workflow
- inline page styling is substantially reduced in favor of shared classes/components
- placeholder and fake UI are removed
- desktop and mobile both feel intentionally designed

---

## 14. Out of Scope

This redesign does not require:

- backend API redesign
- new business features unrelated to the UI cleanup
- introducing a new frontend framework or component library
- implementing full charting depth if backend data/product scope is not ready

If a feature is currently placeholder-only, the redesign can replace it with an honest intermediate state rather than inventing new product scope.

---

## 15. Planning Notes

Implementation planning should specifically cover:

- design token rewrite
- shell and navigation redesign
- page family migration strategy
- shared component refactor
- responsive rules
- removal of placeholder/mock UI
- validation and regression checks on major routes

The verification matrix for planning should treat the following as major routes:

- `/posts`
- `/posts/:postId`
- `/search`
- `/users/:userId`
- `/messages`
- `/messages/:conversationId`
- `/notices`
- `/settings`
- `/analytics`
- `/moderation`
- `/ops`
- `/admin/users`

Secondary compatibility routes that still require a final regression check:

- `/auth/login`
- `/auth/register`
- `/auth/password/reset`
- `/auth/activation/:userId/:code`
- `/403`
- `/:pathMatch(.*)*`

Additional in-scope routes that must still be covered during regression verification even if they inherit an existing page pattern:

- `/bookmarks`
- `/leaderboard`
- `/users/:userId/followees`
- `/users/:userId/followers`
- `/notices`
- `/notices/:topic`
