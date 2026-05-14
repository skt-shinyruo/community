# Community Runtime Observability Logs Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend business-agnostic runtime logs to application lifecycle, Redis, Kafka, SQL, OSS, HTTP client, JVM direct-memory/classloading, logging system, scheduled jobs, cache, security, and process resources.

**Architecture:** Keep runtime logging in `community-common-observability` with optional Spring auto-configuration. Use threshold-triggered events, low-cardinality fields, and explicit logger APIs for places that cannot be safely auto-intercepted without business payload risk.

**Tech Stack:** Java 17, Spring Boot 3.2, Logback MDC, Spring Data Redis/Lettuce, Spring Kafka, MyBatis interceptor, RestClient/WebClient interceptor, JUnit 5, AssertJ.

---

### Task 1: Add Phase 2 Logger Tests

**Files:**
- Create tests under `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability`

- [x] Write failing tests for each new logger category:
  - lifecycle: `app_startup`, `app_ready`, `app_shutdown`, `graceful_shutdown_timeout`
  - cache/Redis: `redis_connection_pressure`, `redis_command_slow`, `cache_hit_ratio_low`
  - messaging/Kafka: `kafka_producer_error`, `kafka_consumer_lag_threshold`, `kafka_rebalance`
  - database: `sql_slow_query`
  - OSS/HTTP client: `oss_upload_slow`, `oss_download_slow`, `oss_client_error`, `http_client_slow`, `http_client_error`
  - runtime/system: `jvm_direct_memory_pressure`, `jvm_class_loading_summary`, `logging_appender_error`, `logging_queue_pressure`, `process_fd_pressure`, `disk_space_pressure`, `cpu_load_threshold`
  - job/security: `scheduled_job_slow`, `scheduled_job_skipped`, `scheduled_job_error`, `rate_limit_triggered`, `auth_filter_error`

- [x] Run focused tests and verify they fail because classes/fields are missing:

```bash
mvn -pl community-common/common-observability test -Dtest='*RuntimeLoggerTest,*ObservabilityAutoConfigurationTest,*InterceptorTest'
```

### Task 2: Implement Shared Logger APIs and Properties

**Files:**
- Modify `RuntimeLoggingProperties`
- Modify `RuntimeObservabilityAutoConfiguration`
- Create focused logger classes under `app`, `redis`, `kafka`, `data`, `oss`, `http`, `jvm`, `logging`, `job`, `cache`, `security`, `system`

- [x] Add nested property groups with enabled flags and thresholds.
- [x] Implement each logger as a small class that writes a single family of events.
- [x] Keep skipped instrumentation warnings best-effort and bounded.
- [x] Run the logger tests until green.

### Task 3: Add Safe Auto Instrumentation

**Files:**
- Create MyBatis SQL slow query interceptor.
- Create HTTP client slow/error interceptors for `RestClient.Builder` and `WebClient.Builder`.
- Extend periodic scheduler to call JVM direct memory/class loading and process resource snapshots when beans are present.
- Register Kafka producer/rebalance/record interceptors when Spring Kafka is present.

- [x] Auto-register only when dependency classes are present.
- [x] Never log SQL bind values, Redis keys, Kafka payloads, object keys, request bodies, cookies, or Authorization headers.
- [x] Run auto-configuration tests.

### Task 4: Wire deployables and OSS/Kafka Touchpoints

**Files:**
- Modify `community-app` config and Logback MDC include fields.
- Modify `community-oss`, `community-im`, and gateway deployables to depend on observability and expose runtime MDC fields.
- Modify OSS client wiring or client implementation to emit operation slow/error events.
- Modify `community-oss` `ObjectStore` wiring to emit bucket/size slow/error events.
- Modify Kafka send helper to expose producer failure events without payload.
- Modify security/rate-limit and scheduler helpers only where the change remains infrastructure-level.

- [x] Add default environment-variable-backed properties.
- [x] Add app integration test for new JSON MDC fields.
- [x] Add WebClient customizer and OSS `ObjectStore` wrapper tests.
- [x] Run `community-app` focused tests with `-am`.

### Task 5: Docs and Verification

**Files:**
- Modify `docs/handbook/operations.md`
- Modify `docs/handbook/system-design.md`

- [x] Document new event actions, key fields, and redaction rules.
- [x] Run:

```bash
mvn -pl community-common/common-observability test
mvn test -pl :community-app -am -Dtest='RuntimeObservabilityIntegrationTest,*ArchTest'
mvn -pl community-oss -am test -Dtest='ObservedObjectStoreTest,OssInfrastructureConfigurationTest,OssApplicationSmokeTest'
mvn -pl community-im/im-realtime -am test -Dtest='LoadBalancedWebClientConfigTest,KafkaConfigTest'
mvn -pl community-im/im-core -am test -Dtest=CommandConsumersLoggingTest
mvn -pl community-gateway -am test -Dtest='GatewayDiscoveryClasspathTest,GatewayDefaultSecurityIntegrationTest'
mvn -pl community-im-gateway -am test -Dtest=CommunityImGatewayApplicationTest
git diff --check -- docs/handbook docs/superpowers/plans backend/community-common backend/community-app backend/community-oss backend/community-oss-client backend/community-im backend/community-gateway backend/community-im-gateway
```
