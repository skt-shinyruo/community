# Community Observability (OpenTelemetry + Elastic) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a production-oriented `OpenTelemetry + Elastic` observability foundation for the backend services, including EDOT collector deployment, trace header bridging, structured logging, and the first batch of high-value business/security/async logs.

**Architecture:** Keep the current service boundaries and evolve observability in layers. Phase 1 uses `OTel Java agent` for traces/metrics and `backend structured JSON file appender -> shared named volume -> EDOT filelog receiver` for logs, so the repo can migrate from the current Promtail/Loki setup without requiring every service to emit OTLP logs on day one or bind host log directories. The implementation then aligns `traceparent` and `X-Trace-Id`, adds module-local JSON/text logback configs, and fills the highest-value event logs in auth, content, async, and IM flows before adding Kibana assets and runbooks.

**Tech Stack:** Docker Compose, Elastic / Kibana, EDOT Collector, OpenTelemetry Java agent, Spring Boot 3.2, Logback, SLF4J, Maven, JUnit 5, OutputCaptureExtension.

**Spec:** `docs/superpowers/specs/2026-03-24-community-observability-otel-elastic-design.md`

---

## File Map

### Deploy / Runtime

- `deploy/docker-compose.yml`
  - Add an `observability-elastic` profile alongside the existing `observability` profile.
  - Wire Elasticsearch, Kibana, and the EDOT gateway collector without breaking the current Loki/Grafana stack.
- `deploy/.env.example`
  - Add env vars for Elastic ports, EDOT OTLP endpoint, OTel enable flags, and backend service metadata defaults.
- `deploy/README.md`
  - Document how to start the new profile and how it coexists with the current Promtail/Loki setup.
- `deploy/Dockerfile.backend-service`
  - Add the Java agent and entrypoint support for `OTEL_*`, `SERVICE_VERSION`, and shared Java options.
- `deploy/scripts/run-backend-service.sh`
  - Centralize backend runtime startup so all four backend services get the same OTel and service metadata behavior.
- `deploy/observability-elastic/edot-collector.yml`
  - Define OTLP receivers, filelog receiver, processors, and Elastic exporters.
- `deploy/observability-elastic/kibana/`
  - Store saved-object exports or operator-facing import instructions for the first dashboards/searches.

### Trace Bridge / Logging Foundation

- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdWebFilter.java`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdCodec.java`
- `backend/community-app/src/main/java/com/nowcoder/community/common/trace/TraceIdCodec.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/trace/TraceIdCodec.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/trace/TraceContext.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/web/TraceIdFilter.java`
  - Align `traceparent`/`X-Trace-Id` precedence and guarantee MDC-backed trace/log correlation.

- `backend/community-gateway/pom.xml`
- `backend/community-app/pom.xml`
- `backend/community-im/im-core/pom.xml`
- `backend/community-im/im-realtime/pom.xml`
  - Add JSON logging dependency support for runtime modules.

- `backend/community-gateway/src/main/resources/application.yml`
- `backend/community-app/src/main/resources/application.yml`
- `backend/community-im/im-core/src/main/resources/application.yml`
- `backend/community-im/im-realtime/src/main/resources/application.yml`
- `backend/community-gateway/src/main/resources/logback-spring.xml`
- `backend/community-app/src/main/resources/logback-spring.xml`
- `backend/community-im/im-core/src/main/resources/logback-spring.xml`
- `backend/community-im/im-realtime/src/main/resources/logback-spring.xml`
  - Add production JSON / local text logging behavior and service version defaults.

### First-Batch Business Logs

- Auth/security:
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/web/AuthOriginGuardFilter.java`
- Content / async:
  - `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorker.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorkerScheduler.java`
- User / IM:
  - `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
  - `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaConfig.java`
  - `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/CommandConsumers.java`
  - `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`

### Test Coverage Targets

- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/TraceIdWebFilterTest.java`
- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerRetryTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandlerTest.java`
- `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/web/ResultTraceIdAdviceTest.java`
- `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`

---

### Task 1: Add the Elastic Observability Compose Profile

**Files:**
- Create: `deploy/observability-elastic/edot-collector.yml`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`
- Modify: `deploy/README.md`
- Modify: `docs/OBSERVABILITY.md`

- [ ] **Step 1: Add the new compose profile and collector config**
  - Add `observability-elastic` services to `deploy/docker-compose.yml` for:
    - `elasticsearch`
    - `kibana`
    - `observability-gateway-edot-collector`
  - Keep the current `observability` profile intact so Promtail/Loki/Grafana remain available during migration.
  - Create `deploy/observability-elastic/edot-collector.yml` with:
    - OTLP receiver for traces/metrics
    - filelog receiver for the shared backend log volume
    - batch / memory limiter processors
    - resource processor for `service.namespace=community`
    - Elastic exporter path chosen by the spec

- [ ] **Step 2: Add environment defaults and docs**
  - Extend `deploy/.env.example` with:
    - `ELASTICSEARCH_PORT`
    - `KIBANA_PORT`
    - `OTEL_EXPORTER_OTLP_ENDPOINT`
    - `OTEL_ENABLED`
    - `OTEL_JAVA_AGENT_VERSION`
    - `SERVICE_VERSION`
  - Update `deploy/README.md` and `docs/OBSERVABILITY.md` to describe:
    - old `observability` profile
    - new `observability-elastic` profile
    - migration expectation and coexistence

- [ ] **Step 3: Validate compose wiring**

Run:
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile observability-elastic config
```
Expected: PASS with the new Elastic/collector services rendered and no missing-file errors.

- [ ] **Step 4: Commit**

```bash
git add deploy/observability-elastic/edot-collector.yml \
        deploy/docker-compose.yml \
        deploy/.env.example \
        deploy/README.md \
        docs/OBSERVABILITY.md
git commit -m "chore(observability): add elastic compose profile and edot collector"
```

---

### Task 2: Wire OTel Java Agent and Shared Backend Runtime Metadata

**Files:**
- Create: `deploy/scripts/run-backend-service.sh`
- Modify: `deploy/Dockerfile.backend-service`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`

- [ ] **Step 1: Add the shared backend entrypoint**
  - Create `deploy/scripts/run-backend-service.sh` to:
    - compose `JAVA_OPTS`
    - enable `-javaagent:/otel/opentelemetry-javaagent.jar` when `OTEL_ENABLED=true`
    - export `OTEL_SERVICE_NAME`
    - export `OTEL_RESOURCE_ATTRIBUTES`
    - export `SERVICE_VERSION`
    - start `java -jar /app/app.jar`

- [ ] **Step 2: Update the backend image**
  - In `deploy/Dockerfile.backend-service`:
    - download or copy the Java agent in a reproducible way using `OTEL_JAVA_AGENT_VERSION`
    - copy `deploy/scripts/run-backend-service.sh`
    - replace the current inline `java ${JAVA_OPTS}` entrypoint with the script

- [ ] **Step 3: Wire compose env per service**
  - In `deploy/docker-compose.yml`, add `OTEL_*` and `SERVICE_VERSION` env vars to:
    - `community-gateway`
    - `community-app`
    - `im-core`
    - `im-realtime`
  - Use service-specific `OTEL_SERVICE_NAME`, shared OTLP endpoint, and a consistent `deployment.environment=local-compose`.

- [ ] **Step 4: Validate image build path**

Run:
```bash
docker build -f deploy/Dockerfile.backend-service --build-arg MODULE=community-app backend
```
Expected: PASS and produce a runnable image with the Java agent and startup script present.

- [ ] **Step 5: Commit**

```bash
git add deploy/scripts/run-backend-service.sh \
        deploy/Dockerfile.backend-service \
        deploy/docker-compose.yml \
        deploy/.env.example
git commit -m "chore(observability): wire backend runtime for otel java agent"
```

---

### Task 3: Implement the `traceparent` / `X-Trace-Id` Bridge Rules

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdCodec.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdWebFilter.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/TraceIdWebFilterTest.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/common/trace/TraceIdCodec.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/common/trace/TraceIdCodecTest.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/trace/TraceIdCodec.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/trace/TraceIdCodecTest.java`

- [ ] **Step 1: Write the failing trace precedence tests**
  - In gateway:
    - add test: valid `traceparent` wins over valid `X-Trace-Id`
    - add test: missing `traceparent` falls back to valid `X-Trace-Id`
    - add test: invalid `X-Trace-Id` gets replaced by a generated 32-hex trace id
  - In app and im-core:
    - add codec tests for the same precedence and normalization rules

- [ ] **Step 2: Run targeted tests (RED)**

Run:
```bash
cd backend && mvn -q -pl community-gateway test -Dtest=TraceIdWebFilterTest,HttpRoutingIntegrationTest
```
Expected: FAIL on the new precedence assertions.

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=TraceIdCodecTest
```
Expected: FAIL because `TraceIdCodec.resolveTraceId(...)` still prefers `X-Trace-Id`.

Run:
```bash
cd backend && mvn -q -pl community-im/im-core test -Dtest=TraceIdCodecTest
```
Expected: FAIL for the same reason.

- [ ] **Step 3: Implement the bridge logic**
  - Add a gateway-local `TraceIdCodec` so the gateway no longer accepts arbitrary `X-Trace-Id` values as-is.
  - Change app and im-core codecs so:
    - valid `traceparent` has priority
    - valid `X-Trace-Id` is the fallback
    - invalid legacy headers never become the standard `trace.id`
  - Make the gateway response header always return the final chosen 32-hex trace id.

- [ ] **Step 4: Re-run the targeted tests (GREEN)**

Run:
```bash
cd backend && mvn -q -pl community-gateway test -Dtest=TraceIdWebFilterTest,HttpRoutingIntegrationTest
```

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=TraceIdCodecTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-core test -Dtest=TraceIdCodecTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdCodec.java \
        backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdWebFilter.java \
        backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/TraceIdWebFilterTest.java \
        backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java \
        backend/community-app/src/main/java/com/nowcoder/community/common/trace/TraceIdCodec.java \
        backend/community-app/src/test/java/com/nowcoder/community/common/trace/TraceIdCodecTest.java \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/trace/TraceIdCodec.java \
        backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/trace/TraceIdCodecTest.java
git commit -m "feat(observability): align traceparent and legacy trace id bridging"
```

---

### Task 4: Add the Structured Logging Foundation for the Runtime Modules

**Files:**
- Modify: `backend/community-gateway/pom.xml`
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-im/im-core/pom.xml`
- Modify: `backend/community-im/im-realtime/pom.xml`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-im/im-core/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Create: `backend/community-gateway/src/main/resources/logback-spring.xml`
- Create: `backend/community-app/src/main/resources/logback-spring.xml`
- Create: `backend/community-im/im-core/src/main/resources/logback-spring.xml`
- Create: `backend/community-im/im-realtime/src/main/resources/logback-spring.xml`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/AccessLogWebFilterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/web/GlobalExceptionHandlerTest.java`
- Create: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/trace/TraceContext.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/web/TraceIdFilter.java`

- [ ] **Step 1: Add the logging dependencies and service-version properties**
  - Add `logstash-logback-encoder` (or the chosen JSON encoder dependency) to the four runtime module poms.
  - Add an application property in each runtime module for service version with an env override and a filtered Maven-version fallback.

- [ ] **Step 2: Add the logback configs**
  - In each runtime module, add `logback-spring.xml` that provides:
    - JSON console output for production-like profiles
    - human-readable text output for local/dev/test
    - stable fields for `service.name`, `service.version`, `trace.id`, level, logger, and message

- [ ] **Step 3: Align `im-core` MDC support**
  - Introduce `TraceContext` in `im-core` mirroring the existing `community-app` MDC behavior.
  - Update `im-core` `TraceIdFilter` to set/clear MDC-backed trace context, not just ThreadLocal.

- [ ] **Step 4: Write and run focused logging tests (RED -> GREEN)**
  - Add/modify tests so that:
    - gateway access logging emits the expected structured fields
    - `community-app` exception logging still includes `trace.id`
    - `im-core` exception logging works with the new MDC-backed trace context

Run:
```bash
cd backend && mvn -q -pl community-gateway test -Dtest=AccessLogWebFilterTest
```

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=GlobalExceptionHandlerTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-core test -Dtest=GlobalExceptionHandlerTest
```
Expected: tests fail before the logging foundation is complete, then pass once the encoder/config/MDC wiring is in place.

- [ ] **Step 5: Commit**

```bash
git add backend/community-gateway/pom.xml \
        backend/community-app/pom.xml \
        backend/community-im/im-core/pom.xml \
        backend/community-im/im-realtime/pom.xml \
        backend/community-gateway/src/main/resources/application.yml \
        backend/community-app/src/main/resources/application.yml \
        backend/community-im/im-core/src/main/resources/application.yml \
        backend/community-im/im-realtime/src/main/resources/application.yml \
        backend/community-gateway/src/main/resources/logback-spring.xml \
        backend/community-app/src/main/resources/logback-spring.xml \
        backend/community-im/im-core/src/main/resources/logback-spring.xml \
        backend/community-im/im-realtime/src/main/resources/logback-spring.xml \
        backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/AccessLogWebFilterTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java \
        backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/web/GlobalExceptionHandlerTest.java \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/trace/TraceContext.java \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/web/TraceIdFilter.java
git commit -m "feat(logging): add structured runtime logging foundation"
```

---

### Task 5: Add the First Security and Audit Event Logs in `community-app`

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/web/AuthOriginGuardFilter.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/web/AuthOriginGuardFilterTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/PasswordResetServiceTest.java`

- [ ] **Step 1: Add failing log assertions for the security flows**
  - Cover at least:
    - login success
    - login denied / invalid credentials
    - captcha required
    - registration code issued
    - registration verify success
    - password reset request issued / hidden-noop
    - password reset confirm success / invalid token
    - origin guard degrade or deny

- [ ] **Step 2: Run the targeted auth/security tests (RED)**

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=AuthServiceLoginTest,RegistrationServiceTest,RegistrationVerificationServiceTest,PasswordResetServiceTest,AuthOriginGuardFilterTest
```
Expected: FAIL because the new structured event logs do not exist yet.

- [ ] **Step 3: Implement the structured security logs**
  - Add logger fields matching the spec:
    - `community.category=security`
    - stable `community.action`
    - stable `community.outcome`
  - Log identifiers and outcomes, never secrets:
    - no password
    - no captcha value
    - no token/cookie/reset-link raw value

- [ ] **Step 4: Re-run the targeted tests (GREEN)**

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=AuthServiceLoginTest,RegistrationServiceTest,RegistrationVerificationServiceTest,PasswordResetServiceTest,AuthOriginGuardFilterTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/web/AuthOriginGuardFilter.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/web/AuthOriginGuardFilterTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/PasswordResetServiceTest.java
git commit -m "feat(observability): add auth security event logs"
```

---

### Task 6: Add Content and Async Event Logs in `community-app`

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorker.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorkerScheduler.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerRetryTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerSchedulerTest.java`

- [ ] **Step 1: Write the failing content/async logging tests**
  - Cover at least:
    - post create/update/delete/top/wonderful audit/business events
    - search reindex start / skipped / success / failure
    - outbox retry / dead / no-handler
    - outbox scheduler poll failure

- [ ] **Step 2: Run targeted tests (RED)**

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=PostCommandServiceLoggingTest,SearchReindexExecutionServiceTest,OutboxWorkerRetryTest,OutboxWorkerSchedulerTest
```
Expected: FAIL because the new event logs and/or test classes are missing.

- [ ] **Step 3: Implement the business and async logs**
  - Use `community.category=business` or `community.category=async` consistently.
  - Keep high-frequency success detail out of `info`; use summary or state-change logs instead.
  - Preserve existing retry/dead behavior and enrich the log fields rather than rewriting control flow.

- [ ] **Step 4: Re-run targeted tests (GREEN)**

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=PostCommandServiceLoggingTest,SearchReindexExecutionServiceTest,OutboxWorkerRetryTest,OutboxWorkerSchedulerTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java \
        backend/community-app/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorker.java \
        backend/community-app/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorkerScheduler.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerRetryTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerSchedulerTest.java
git commit -m "feat(observability): add content and async event logs"
```

---

### Task 7: Add User-Sensitive and IM Event Logs

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaConfig.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/CommandConsumers.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/CommandConsumersLoggingTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`

- [ ] **Step 1: Write the failing user/IM log assertions**
  - User:
    - avatar upload token request
    - avatar upload
    - avatar update
  - IM:
    - command persisted summary
    - DLQ / kafka send fail / bootstrap fail
    - WS auth deny / disconnect summary

- [ ] **Step 2: Run the targeted tests (RED)**

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=UserControllerLoggingTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-core test -Dtest=CommandConsumersLoggingTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-realtime test -Dtest=ImRealtimeWebSocketIntegrationTest
```
Expected: FAIL because the structured event logs or test fixtures are missing.

- [ ] **Step 3: Implement the logs**
  - In `UserController`, log only operation metadata and outcome; do not log file content or credential material.
  - In `im-core`, keep message-success logs as summaries, not per-step chatter.
  - In `im-realtime`, add structured failure/deny/disconnect events without logging every successful send.

- [ ] **Step 4: Re-run the targeted tests (GREEN)**

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=UserControllerLoggingTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-core test -Dtest=CommandConsumersLoggingTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-realtime test -Dtest=ImRealtimeWebSocketIntegrationTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaConfig.java \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/CommandConsumers.java \
        backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java \
        backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/CommandConsumersLoggingTest.java \
        backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java
git commit -m "feat(observability): add user and im event logs"
```

---

### Task 8: Add Kibana Assets, Alerting Docs, and Final Runbooks

**Files:**
- Create: `deploy/observability-elastic/kibana/README.md`
- Create: `deploy/observability-elastic/kibana/saved-objects.ndjson`
- Modify: `docs/OBSERVABILITY.md`
- Modify: `docs/README.md`
- Modify: `deploy/README.md`

- [ ] **Step 1: Add repository-backed Kibana assets**
  - Export and store the first set of saved objects for:
    - trace-by-service exploration
    - auth/security event search
    - async retry/dead events
    - service health overview

- [ ] **Step 2: Document alerting and operator usage**
  - Update docs to explain:
    - which alerts remain in Prometheus during Phase 1
    - which searches/views are in Kibana
    - how to inspect one `trace.id`
    - how to inspect one `community.job_id` or `community.event_id`

- [ ] **Step 3: Validate docs and compose references**

Run:
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile observability-elastic config
```

Run:
```bash
rg -n "observability-elastic|EDOT Collector|trace.id|community.category" docs deploy
```
Expected: PASS and the docs consistently reference the new profile and field model.

- [ ] **Step 4: Commit**

```bash
git add deploy/observability-elastic/kibana/README.md \
        deploy/observability-elastic/kibana/saved-objects.ndjson \
        docs/OBSERVABILITY.md \
        docs/README.md \
        deploy/README.md
git commit -m "docs(observability): add elastic dashboards and operator runbooks"
```

---

## Final Verification

- [ ] **Step 1: Targeted backend regression runs**

Run:
```bash
cd backend && mvn -q -pl community-gateway test -Dtest=TraceIdWebFilterTest,HttpRoutingIntegrationTest,AccessLogWebFilterTest
```

Run:
```bash
cd backend && mvn -q -pl community-app test -Dtest=GlobalExceptionHandlerTest,AuthServiceLoginTest,RegistrationServiceTest,RegistrationVerificationServiceTest,PasswordResetServiceTest,SearchReindexExecutionServiceTest,OutboxWorkerRetryTest,OutboxWorkerSchedulerTest,PostCommandServiceLoggingTest,UserControllerLoggingTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-core test -Dtest=TraceIdCodecTest,GlobalExceptionHandlerTest,CommandConsumersLoggingTest
```

Run:
```bash
cd backend && mvn -q -pl community-im/im-realtime test -Dtest=ImRealtimeWebSocketIntegrationTest
```
Expected: PASS.

- [ ] **Step 2: Compose validation**

Run:
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile observability-elastic config
```
Expected: PASS.

- [ ] **Step 3: Optional local smoke-up**

Run:
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile observability-elastic up -d --build observability-gateway-edot-collector elasticsearch kibana community-app community-gateway im-core im-realtime
```
Expected: PASS, services become healthy, and logs/traces begin flowing to the Elastic profile stack.
