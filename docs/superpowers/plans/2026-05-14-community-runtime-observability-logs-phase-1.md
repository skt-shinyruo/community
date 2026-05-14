# Community Runtime Observability Logs Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add business-agnostic structured runtime logs for the backend application startup, JVM/GC pressure, executor pressure, Hikari connection pool pressure, and slow servlet requests.

**Architecture:** Create `community-common-observability` as a shared Spring Boot auto-configuration module. Runtime loggers write through SLF4J and MDC into the existing Logback JSON pipeline, and `community-app` consumes the module without putting operational logging inside business domains.

**Tech Stack:** Java 17, Spring Boot 3.2, Logback MDC, Micrometer where already present, JUnit 5, AssertJ, Spring Boot test utilities.

---

### Task 1: Shared Runtime Log Writer

**Files:**
- Create: `backend/community-common/common-observability/pom.xml`
- Modify: `backend/community-common/pom.xml`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/logging/RuntimeLogFields.java`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/logging/RuntimeLogEvent.java`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/logging/RuntimeLogWriter.java`
- Test: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/logging/RuntimeLogWriterTest.java`

- [ ] **Step 1: Write failing tests for MDC fields and restoration**

Add tests that create a `RuntimeLogWriter`, emit an event, and assert:
- MDC fields are present while logging.
- Existing MDC values are restored afterward.
- Message text includes stable key-value pairs but does not duplicate `community.category`, `community.action`, or `community.outcome`.

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -pl community-common/common-observability test -Dtest=RuntimeLogWriterTest`

Expected: FAIL because the module/classes do not exist yet.

- [ ] **Step 3: Create module and minimal writer implementation**

Add a Maven module with `spring-boot-autoconfigure`, `slf4j-api`, and test dependencies. Implement `RuntimeLogWriter` as a small wrapper around a supplied `Logger`.

- [ ] **Step 4: Run the writer tests**

Run: `mvn -pl community-common/common-observability test -Dtest=RuntimeLogWriterTest`

Expected: PASS.
### Task 2: Configuration Properties and Auto-Configuration

**Files:**
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/logging/RuntimeLoggingProperties.java`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/autoconfig/RuntimeObservabilityAutoConfiguration.java`
- Create: `backend/community-common/common-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/autoconfig/RuntimeObservabilityAutoConfigurationTest.java`

- [ ] **Step 1: Write failing auto-configuration tests**

Use `ApplicationContextRunner` to assert:
- Runtime logging is enabled by default.
- `community.observability.runtime-logging.enabled=false` disables all beans.
- Startup/JVM, executor, datasource, and servlet access components can be independently disabled.

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -pl community-common/common-observability test -Dtest=RuntimeObservabilityAutoConfigurationTest`

Expected: FAIL because properties and auto-configuration are not implemented.

- [ ] **Step 3: Implement properties and auto-configuration**

Bind `community.observability.runtime-logging` with nested `jvm`, `executors`, `datasource`, and `http` settings. Register beans conditionally and safely with optional bean providers.

- [ ] **Step 4: Run tests**

Run: `mvn -pl community-common/common-observability test -Dtest=RuntimeObservabilityAutoConfigurationTest`

Expected: PASS.

### Task 3: JVM, GC, Executor, and DataSource Runtime Loggers

**Files:**
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/jvm/JvmRuntimeLogger.java`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/jvm/GcPauseThresholdLogger.java`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/executor/ExecutorRuntimeLogger.java`
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/data/DataSourceRuntimeLogger.java`
- Test: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/jvm/JvmRuntimeLoggerTest.java`
- Test: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/executor/ExecutorRuntimeLoggerTest.java`
- Test: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/data/DataSourceRuntimeLoggerTest.java`

- [ ] **Step 1: Write failing threshold tests**

Assert the loggers emit only threshold or startup events:
- JVM startup summary contains Java version, processors, heap max, timezone, and charset.
- JVM memory pressure logs only when used/max exceeds the threshold.
- Executor pressure logs only when active/max exceeds threshold or queue has pending work.
- Hikari pool pressure logs active/idle/total/waiting values.

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -pl community-common/common-observability test -Dtest='JvmRuntimeLoggerTest,ExecutorRuntimeLoggerTest,DataSourceRuntimeLoggerTest'`

Expected: FAIL because logger implementations do not exist.

- [ ] **Step 3: Implement minimal loggers**

Implement best-effort loggers with no hard dependency on business code. Catch inspection exceptions and emit at most one skipped instrumentation event.

- [ ] **Step 4: Run tests**

Run: `mvn -pl community-common/common-observability test -Dtest='JvmRuntimeLoggerTest,ExecutorRuntimeLoggerTest,DataSourceRuntimeLoggerTest'`

Expected: PASS.

### Task 4: Servlet Slow Access Runtime Log

**Files:**
- Create: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/http/ServletAccessRuntimeLogFilter.java`
- Test: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/http/ServletAccessRuntimeLogFilterTest.java`

- [ ] **Step 1: Write failing servlet filter tests**

Assert the filter:
- Excludes configured paths such as `/actuator/health`.
- Logs only requests at or above `slow-request-threshold`.
- Includes method, sanitized path without query string, status, duration, and threshold.
- Does not log request body, cookies, Authorization header, or query string.

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -pl community-common/common-observability test -Dtest=ServletAccessRuntimeLogFilterTest`

Expected: FAIL because filter implementation does not exist.

- [ ] **Step 3: Implement minimal servlet filter**

Use `OncePerRequestFilter`, `AntPathMatcher`, and `FilterRegistrationBean` through auto-configuration.

- [ ] **Step 4: Run tests**

Run: `mvn -pl community-common/common-observability test -Dtest=ServletAccessRuntimeLogFilterTest`

Expected: PASS.

### Task 5: Wire community-app and Logback Fields

**Files:**
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/main/resources/logback-spring.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/observability/RuntimeObservabilityIntegrationTest.java`

- [ ] **Step 1: Write failing integration test**

Start an application context slice with the new dependency and assert a startup event can be emitted as JSON with `event.category`, `event.action`, `event.outcome`, and backward-compatible `community.*` fields.

- [ ] **Step 2: Run test and verify failure**

Run: `mvn -pl community-app test -Dtest=RuntimeObservabilityIntegrationTest`

Expected: FAIL because `community-app` does not depend on or expose the new module yet.

- [ ] **Step 3: Add dependency, defaults, and Logback MDC includes**

Add `community-common-observability` to `community-app`, add default runtime logging config, and include the new MDC keys in all JSON appenders.

- [ ] **Step 4: Run integration test**

Run: `mvn -pl community-app test -Dtest=RuntimeObservabilityIntegrationTest`

Expected: PASS.

### Task 6: Documentation and Verification

**Files:**
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/system-design.md`

- [ ] **Step 1: Update docs**

Document the Phase 1 runtime log categories, key fields, configuration toggles, and example Kibana queries.

- [ ] **Step 2: Run focused tests**

Run: `mvn -pl community-common/common-observability,community-app test -Dtest='*Runtime*Test,*Observability*Test,ServletAccessRuntimeLogFilterTest'`

Expected: PASS.

- [ ] **Step 3: Run architecture tests**

Run: `mvn test -pl :community-app -Dtest='*ArchTest'`

Expected: PASS.
