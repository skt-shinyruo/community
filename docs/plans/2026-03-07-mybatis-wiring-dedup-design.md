# MyBatis Wiring Dedup Design (Single Spring Context)

**Date:** 2026-03-07  
**Status:** Approved (chosen by user)

## Context / Problem

In a modular monolith (one `community-bootstrap` assembling multiple domain packages into a **single** Spring ApplicationContext), MyBatis wiring currently leaks into domain modules:

1. Multiple modules each define `@MapperScan` and (in the original multi-module setup) may include shared infra mapper packages (e.g. outbox) in their scan list.
   - This creates a structural fragility: every module must “remember to scan” infra mappers.
   - It also creates a hidden coupling point: duplicate scanning of shared packages can turn into duplicate bean registration / override / order-dependent behavior during upgrades.

2. MyBatis type alias scanning is hard-coded in the bootstrap `application.yml` as a comma-separated list of domain entity packages.
   - This forces the composition root to know internal persistence details of each domain module.
   - It is easy to forget to update when adding a new domain or moving entities, causing runtime alias resolution failures.

## Goals

1. Make MyBatis wiring a **composition-root concern** (bootstrap owns wiring).
2. Ensure shared infra mappers (e.g. outbox) do not require per-domain scan duplication.
3. Remove bootstrap’s dependency on domain entity package enumeration for MyBatis type aliases.
4. Add a guardrail test that prevents future regressions.

## Non-goals

1. No business logic change.
2. No DB schema changes.
3. No restructuring of domain packages.

## Decision

### 1) Centralize mapper scanning

Create exactly one MyBatis mapper scanner in the bootstrap (composition root) that scans by `@Mapper` annotation:

- `@MapperScan(annotationClass = org.apache.ibatis.annotations.Mapper.class, basePackages = "com.nowcoder.community")`

Remove per-domain `MybatisConfig` classes that exist only for mapper scanning.

### 2) Remove type aliases by switching XML to fully qualified class names

Remove `mybatis.type-aliases-package` from:

- `backend/community-bootstrap/src/main/resources/application.yml`
- `backend/community-bootstrap/src/test/resources/application.yml`

Update MyBatis XML mapper files to use fully qualified class names for:

- `resultType="..."`
- `parameterType="..."`

This avoids any need for alias scanning and prevents alias conflicts.

### 3) Add guardrail tests

Add an architecture/invariant test that enforces:

1. No `type-aliases-package` entry in bootstrap YAML configs.
2. No more than one `@MapperScan` occurrence under main sources (to keep wiring centralized).

## Expected Outcome

- Adding a new domain mapper only requires `@Mapper` annotation on the interface; no bootstrap YAML updates.
- Shared infra mapper packages are not repeatedly scanned by multiple domain modules.
- MyBatis XML mappings remain stable across package refactors (as long as types are updated by IDE refactor).

