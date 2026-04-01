# Community App Persistence Strategy Alignment Design

## Context

`community-app` currently mixes two domain-internal persistence styles:

- `content` / `user` / `growth` / `notice` mostly use `Service -> MyBatis Mapper -> XML`.
- `social` uses `Service -> Repository -> {Db, Redis, InMemory}`.

This is not a database-technology split. `social` DB storage still uses MyBatis, but its service layer depends on a storage abstraction while most other domains depend directly on mappers.

## Problem

The real inconsistency is the missing rule for when owner-domain services should introduce a persistence abstraction.

Today that creates two concrete problems:

1. The codebase does not state when direct mapper access is acceptable versus when a repository/port is required.
2. `social` still leaks storage choice into service code (`LikeService` / `FollowService` inspect `social.storage`), so its abstraction boundary is only partial.

## Decision

Adopt the following persistence strategy for `community-app`:

1. Cross-domain collaboration remains unified through owner-domain `api.query`, `api.action`, `api.model`, and `contracts`.
2. Inside an owner domain, direct mapper access remains the default for single-SSOT MyBatis-backed reads and straightforward writes.
3. Introduce a repository/port only when at least one of these conditions is true:
   - the domain supports multiple backend implementations;
   - the write path needs backend-specific atomicity or compensation semantics;
   - tests benefit from an in-memory implementation that exercises real business behavior.
4. When a repository exists, storage selection and compensation policy must stay inside the repository abstraction or storage-specific adapter layer, not in the service layer.

## Scope For This Change

This change does not convert `content`, `user`, `growth`, or `notice` to repositories.

This change only:

- removes `social.storage` branching from `LikeService` and `FollowService`;
- moves compensating-write policy behind repository abstractions;
- adds targeted regression tests;
- records the rule in architecture documentation and a focused guard test.

## Expected Outcome

- `social` becomes the canonical example of "write-side repository because storage semantics differ".
- `content` / `user` / `growth` / `notice` remain valid examples of "owner-domain service directly uses mapper when a second persistence abstraction adds no value".
- future changes have a written rule instead of style drift.
