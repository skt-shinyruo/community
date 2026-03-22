# Remove `community-app` WebFlux Footprint Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove WebFlux/WebReactive dependencies and reactive-only infra auto-config from `backend/community-app` while keeping the module running as a Servlet (Spring MVC) application.

**Architecture:** `community-app` is intended to be the single deployable backend (`community-app`) with synchronous MVC controllers. WebFlux support inside this module is currently only “scaffolding” (conditional reactive auto-config + reactive exception/trace filters). We will delete that reactive scaffolding and remove the `spring-boot-starter-webflux` dependency to reduce confusion and classpath surface area.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring MVC (Servlet), Spring Security (Servlet), Maven multi-module build.

---

### Task 1: Remove WebFlux dependency from `community-app`

**Files:**
- Modify: `backend/community-app/pom.xml`

- [x] **Step 1: Remove `spring-boot-starter-webflux` dependency**
  - Delete the `<dependency>` block for `org.springframework.boot:spring-boot-starter-webflux`.
  - Keep `spring-boot-starter-web` as the web stack.

- [x] **Step 2: Ensure no code references `WebClient`/reactive controller types**
  - Run: `rg -n "org\\.springframework\\.web\\.reactive|WebClient|Mono<|Flux<" backend/community-app/src/main/java`
  - Expected: no hits outside intentionally deleted files.

---

### Task 2: Delete reactive-only infra code from `community-app`

**Files:**
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/infra/web/autoconfig/ReactiveWebInfraAutoConfiguration.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/infra/web/reactive/TraceIdWebFilter.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/infra/web/reactive/ReactiveSecurityExceptionHandler.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ReactiveSecurityInfraAutoConfiguration.java`
- Modify: `backend/community-app/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [x] **Step 1: Remove reactive auto-configuration import entries**
  - Remove:
    - `com.nowcoder.community.infra.security.autoconfig.ReactiveSecurityInfraAutoConfiguration`
    - `com.nowcoder.community.infra.web.autoconfig.ReactiveWebInfraAutoConfiguration`

- [x] **Step 2: Delete the reactive auto-config classes and reactive infra classes**
  - Delete the files listed above.
  - Keep the servlet equivalents:
    - `ServletInfraSecurityConfig`
    - `ServletWebInfraAutoConfiguration`

- [x] **Step 3: Sanity search for leftover references**
  - Run: `rg -n "ReactiveWebInfraAutoConfiguration|TraceIdWebFilter|ReactiveSecurityExceptionHandler|ReactiveSecurityInfraAutoConfiguration" backend/community-app`
  - Expected: no matches.

---

### Task 3: Verify build/test

**Files:**
- Test: Maven build output

- [x] **Step 1: Compile/test `community-app`**
  - From repo root:
    - Run: `cd backend && mvn -pl :community-app -am test`
  - Expected: BUILD SUCCESS

- [x] **Step 2: Optional full backend test sweep**
  - Run: `cd backend && mvn test`
  - Expected: BUILD SUCCESS
