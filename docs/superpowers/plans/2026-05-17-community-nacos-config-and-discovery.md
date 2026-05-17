# Community Nacos Config And Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Nacos into the repository's configuration center and registration center for all non-secret operational configuration, service metadata, gateway canary routing, frontend runtime config, and IM worker drain/capacity routing.

**Architecture:** Backend services import explicit Nacos Config dataIds through Spring Config Data while retaining environment variables for bootstrap and secrets. Local compose seeds all non-secret dataIds before services start. Gateway and IM gateway consume Nacos Discovery metadata for canary routing, stable fallback, worker drain, and capacity-aware assignment.

**Tech Stack:** Spring Boot 3.2, Spring Cloud 2023.0.1, Spring Cloud Alibaba Nacos Config/Discovery, Spring Cloud Gateway, Spring Cloud LoadBalancer, WebFlux, Servlet MVC, Docker Compose, Bash, JUnit 5, AssertJ, Reactor Test, Maven.

---

## Spec

Implement the approved spec:

`docs/superpowers/specs/2026-05-16-community-nacos-config-and-discovery-design.md`

The implementation must keep secrets out of Nacos Config. Secrets remain in `.env`, environment variables, or a secret manager.

## File Map

### Backend Dependencies And Bootstrap Config

- Modify `backend/community-gateway/pom.xml`: add Nacos Config starter.
- Modify `backend/community-app/pom.xml`: add Nacos Config starter.
- Modify `backend/community-oss/pom.xml`: add Nacos Config starter.
- Modify `backend/community-im-gateway/pom.xml`: add Nacos Config starter.
- Modify `backend/community-im/im-core/pom.xml`: add Nacos Config starter.
- Modify `backend/community-im/im-realtime/pom.xml`: add Nacos Config starter.
- Modify each runtime `application.yml`: add `spring.config.import`, `spring.cloud.nacos.config.*`, and common discovery metadata.

### Nacos Seed And Compose

- Create `deploy/nacos/config/*.yaml`: all seeded non-secret dataIds.
- Create `deploy/nacos/seed-configs.sh`: idempotent Nacos OpenAPI publisher.
- Modify `deploy/compose.infra.nacos.single.yml`: add `nacos-config-bootstrap`.
- Modify `deploy/compose.infra.nacos.cluster.yml`: add `nacos-config-bootstrap`.
- Modify `deploy/compose.runtime.services.single.yml`: depend on `nacos-config-bootstrap`.
- Modify `deploy/compose.runtime.services.cluster.yml`: depend on `nacos-config-bootstrap`.
- Modify `deploy/tests/topology_single_cluster.sh`: assert seed service and dependencies.
- Create `deploy/tests/nacos_config_seed.sh`: assert seed files, dataIds, and secret guard.

### Gateway Runtime Config

- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/GatewayHttpRouteProperties.java`: add validation helpers if needed by refresh code.
- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/GatewayRouteLocatorConfig.java`: move route building behind refresh-aware source.
- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteProperties.java`: keep normalized properties refresh-friendly.
- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteConfig.java`: rebuild routes on refresh.
- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitProperties.java`: add snapshot copy helper.
- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TrafficPolicyProperties.java`: add canary rule model.
- Modify `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TrafficPolicyEvaluator.java`: evaluate canary metadata selectors.
- Create `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayConfigRefreshListener.java`: bind changed config and publish route refresh events.
- Create `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/canary/CanaryRouteProperties.java`: canary configuration model.
- Create `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/canary/CanaryInstanceFilter.java`: metadata selector logic.

### Shared Runtime Policy And Frontend Runtime Config

- Create `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/feature/FeatureFlagProperties.java`.
- Create `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/feature/FeatureFlagDecisions.java`.
- Create `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/degradation/DegradationProperties.java`.
- Create `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/degradation/DegradationDecisions.java`.
- Create `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/config/ConfigRefreshAuditLogger.java`.
- Create `backend/community-app/src/main/java/com/nowcoder/community/runtime/config/RuntimeConfigController.java`.
- Create `backend/community-app/src/main/java/com/nowcoder/community/runtime/config/RuntimeConfigProperties.java`.
- Create `backend/community-app/src/main/java/com/nowcoder/community/runtime/config/RuntimeConfigApplicationService.java`.

### IM Worker Metadata

- Modify `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerDescriptor.java`: add drain and capacity fields.
- Modify `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/DiscoveredWorkerDescriptorFactory.java`: read new metadata.
- Modify `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerRegistry.java`: exclude draining workers from new assignments.
- Modify `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelector.java`: include capacity score.
- Modify `backend/community-im/im-realtime/src/main/resources/application.yml`: publish worker capacity metadata.

### Startup Validation And Docs

- Modify `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`: replace generic Nacos hint with concrete import/secret guidance.
- Create `backend/community-app/src/test/java/com/nowcoder/community/infra/startup/StartupValidationTest.java`.
- Modify `docs/handbook/local-development.md`.
- Modify `docs/handbook/operations.md`.
- Modify `docs/handbook/system-design.md`.
- Modify `deploy/README.md`.
- Modify `deploy/.env.single.example`.
- Modify `deploy/.env.cluster.example`.

## Task 1: Add Nacos Config Dependencies And Bootstrap Imports

**Files:**
- Modify: `backend/community-gateway/pom.xml`
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-oss/pom.xml`
- Modify: `backend/community-im-gateway/pom.xml`
- Modify: `backend/community-im/im-core/pom.xml`
- Modify: `backend/community-im/im-realtime/pom.xml`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-oss/src/main/resources/application.yml`
- Modify: `backend/community-im-gateway/src/main/resources/application.yml`
- Modify: `backend/community-im/im-core/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Test: existing context tests and classpath tests listed below

- [ ] **Step 1: Add failing classpath assertions for Nacos Config**

In `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/GatewayDiscoveryClasspathTest.java`, add this import:

```java
import com.alibaba.cloud.nacos.NacosConfigManager;
```

Then update the test method to include:

```java
assertThat(NacosConfigManager.class).isNotNull();
```

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest=GatewayDiscoveryClasspathTest test
```

Expected: FAIL because `NacosConfigManager` is not on the module classpath.

- [ ] **Step 2: Add Nacos Config starter dependencies**

In each deployable POM listed above, add the dependency immediately after the existing Nacos Discovery dependency:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest=GatewayDiscoveryClasspathTest test
```

Expected: PASS.

- [ ] **Step 3: Add bootstrap-safe Nacos Config imports**

In each runtime `application.yml`, add `spring.config.import` before `spring.application.name`. Use the service dataId matching the module:

```yaml
spring:
  config:
    import:
      - ${NACOS_CONFIG_IMPORT_SHARED:optional:nacos:community-shared.yaml?group=${NACOS_CONFIG_GROUP:COMMUNITY}}
      - ${NACOS_CONFIG_IMPORT_SERVICE:optional:nacos:community-gateway.yaml?group=${NACOS_CONFIG_GROUP:COMMUNITY}}
```

Use these service dataIds:

```text
community-gateway.yaml
community-app.yaml
community-oss.yaml
community-im-gateway.yaml
im-core.yaml
im-realtime.yaml
```

Under existing `spring.cloud.nacos`, add `config` and metadata-aware `discovery` blocks:

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR:${NACOS_SERVER_ADDR:localhost:8848}}
        namespace: ${NACOS_NAMESPACE:}
        group: ${NACOS_CONFIG_GROUP:COMMUNITY}
        file-extension: yaml
      discovery:
        server-addr: ${SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR:${NACOS_SERVER_ADDR:localhost:8848}}
        metadata:
          version: ${SERVICE_VERSION:@project.version@}
          deployment.environment: ${DEPLOYMENT_ENVIRONMENT:local}
          zone: ${SERVICE_ZONE:local}
          traffic.group: ${SERVICE_TRAFFIC_GROUP:baseline}
          release.track: ${SERVICE_RELEASE_TRACK:stable}
          weight: ${SERVICE_WEIGHT:100}
          protocol: http
          capabilities: ${SERVICE_CAPABILITIES:}
          management.port: ${MANAGEMENT_SERVER_PORT:${SERVER_PORT}}
          draining: ${SERVICE_DRAINING:false}
```

For `im-realtime`, keep the existing custom `spring.cloud.nacos.discovery.service` and merge this metadata with the worker fields:

```yaml
          role: ws-worker
          wsPath: ${IM_WS_PATH:/internal/ws/im}
          wsPort: ${SERVER_PORT:18081}
          workerId: ${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}
          maxConnections: ${IM_REALTIME_MAX_CONNECTIONS:10000}
          activeConnectionHint: ${IM_REALTIME_ACTIVE_CONNECTION_HINT:0}
          shardGroup: ${IM_REALTIME_SHARD_GROUP:default}
```

- [ ] **Step 4: Keep tests offline**

Where a Spring Boot test currently disables discovery, also disable config.

Add this dynamic property beside existing discovery disables:

```java
registry.add("spring.cloud.nacos.config.enabled", () -> "false");
```

For annotation property arrays, add:

```java
"spring.cloud.nacos.config.enabled=false"
```

Update these known files:

```text
backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java
backend/community-gateway/src/test/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteIntegrationTest.java
backend/community-gateway/src/test/java/com/nowcoder/community/gateway/security/GatewayDefaultSecurityIntegrationTest.java
backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplicationTest.java
backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/ImSessionApiIntegrationTest.java
backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/ws/ImEdgeWebSocketBridgeIntegrationTest.java
backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java
backend/community-oss/src/test/java/com/nowcoder/community/oss/OssApplicationSmokeTest.java
```

In `backend/community-im/im-realtime/src/test/resources/application-test.yml`, add:

```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: false
```

- [ ] **Step 5: Run bootstrap/import tests**

Run:

```bash
cd backend
mvn -pl :community-gateway,:community-im-gateway,:im-realtime,:community-oss -am test -Dtest='GatewayDiscoveryClasspathTest,CommunityImGatewayApplicationTest,ImRealtimeWebSocketIntegrationTest,OssApplicationSmokeTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "feat: add nacos config imports"
```

## Task 2: Add Nacos Seed DataIds And Secret Guard

**Files:**
- Create: `deploy/nacos/config/community-shared.yaml`
- Create: `deploy/nacos/config/community-feature-flags.yaml`
- Create: `deploy/nacos/config/community-degradation.yaml`
- Create: `deploy/nacos/config/community-canary-routing.yaml`
- Create: `deploy/nacos/config/community-frontend-runtime.yaml`
- Create: `deploy/nacos/config/community-cache-policy.yaml`
- Create: `deploy/nacos/config/community-search-policy.yaml`
- Create: `deploy/nacos/config/community-upload-policy.yaml`
- Create: `deploy/nacos/config/community-notification-policy.yaml`
- Create: `deploy/nacos/config/community-kafka-policy.yaml`
- Create: `deploy/nacos/config/community-work-processing.yaml`
- Create: `deploy/nacos/config/community-gateway.yaml`
- Create: `deploy/nacos/config/community-app.yaml`
- Create: `deploy/nacos/config/community-oss.yaml`
- Create: `deploy/nacos/config/community-im-gateway.yaml`
- Create: `deploy/nacos/config/im-core.yaml`
- Create: `deploy/nacos/config/im-realtime.yaml`
- Create: `deploy/nacos/seed-configs.sh`
- Create: `deploy/tests/nacos_config_seed.sh`

- [ ] **Step 1: Write the seed guard test**

Create `deploy/tests/nacos_config_seed.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
CONFIG_DIR="${REPO_ROOT}/deploy/nacos/config"
SEED_SCRIPT="${REPO_ROOT}/deploy/nacos/seed-configs.sh"

required_data_ids=(
  community-shared.yaml
  community-feature-flags.yaml
  community-degradation.yaml
  community-canary-routing.yaml
  community-frontend-runtime.yaml
  community-cache-policy.yaml
  community-search-policy.yaml
  community-upload-policy.yaml
  community-notification-policy.yaml
  community-kafka-policy.yaml
  community-work-processing.yaml
  community-gateway.yaml
  community-app.yaml
  community-oss.yaml
  community-im-gateway.yaml
  im-core.yaml
  im-realtime.yaml
)

test -x "${SEED_SCRIPT}"

for data_id in "${required_data_ids[@]}"; do
  test -s "${CONFIG_DIR}/${data_id}"
  grep -F "${data_id}" "${SEED_SCRIPT}"
done

if rg -n -i '(password|secret|access[_-]?key|hmac|token):[[:space:]]*[^$[:space:]]+' "${CONFIG_DIR}"; then
  echo "seed configs must not contain literal secret-like values" >&2
  exit 1
fi

if rg -n -i '(change-me|changeme|dummy|example-secret|example-password)' "${CONFIG_DIR}"; then
  echo "seed configs must not contain fake secret values" >&2
  exit 1
fi
```

Run:

```bash
bash deploy/tests/nacos_config_seed.sh
```

Expected: FAIL because seed files and script do not exist.

- [ ] **Step 2: Create shared seed files**

Create `deploy/nacos/config/community-shared.yaml`:

```yaml
security:
  jwt:
    issuer: community-auth

community:
  observability:
    runtime-logging:
      enabled: true
      startup-summary-enabled: true
      periodic-summary-enabled: true
      periodic-summary-interval: 60s
      jvm:
        enabled: true
        memory-threshold-percent: 85
        gc-pause-threshold-ms: 200
        direct-memory-threshold-percent: 85
      executors:
        enabled: true
        saturation-threshold-percent: 85
      http-client:
        enabled: true
        slow-request-threshold-ms: 1000
      logging-system:
        enabled: true
        queue-pressure-threshold-percent: 80
      system:
        enabled: true
        fd-usage-threshold-percent: 80
        disk-usage-threshold-percent: 90
        cpu-load-threshold-percent: 85
```

Create `deploy/nacos/config/community-feature-flags.yaml`:

```yaml
community:
  features:
    global-read-only: false
    registration: true
    login: true
    post-publishing: true
    comment-publishing: true
    private-message: true
    file-upload: true
    market-trading: true
    report-moderation: true
    analytics-ingest: false
    search: true
```

Create `deploy/nacos/config/community-degradation.yaml`:

```yaml
community:
  degradation:
    search: strict
    analytics: best-effort
    online-im-push: best-effort
    media-processing: strict
    background-projections: best-effort
```

Create `deploy/nacos/config/community-frontend-runtime.yaml`:

```yaml
frontend:
  runtime:
    api-base-path: /api
    public-gateway-origin: http://localhost:12880
    websocket-url: ws://localhost:12880/ws/im
    analytics-enabled: false
    analytics-sample-rate: 0.0
    release-channel: local
    features:
      posts: true
      comments: true
      private-message: true
      file-upload: true
      market: true
```

Create `deploy/nacos/config/community-canary-routing.yaml`:

```yaml
gateway:
  http:
    canary:
      rules: []
```

- [ ] **Step 3: Create policy seed files**

Create `deploy/nacos/config/community-cache-policy.yaml`:

```yaml
community:
  cache:
    null-ttl: 30s
    default-ttl: 300s
    hotspot-threshold: 1000
    prewarm-enabled: false
    diagnostic-bypass-enabled: false
```

Create `deploy/nacos/config/community-search-policy.yaml`:

```yaml
search:
  storage: es
  index:
    prefix: community_posts_v
    initialize: true
  query:
    default-sort: relevance
    max-page-size: 50
    max-result-window: 10000
    timeout-ms: 1500
  idempotency:
    cleanup-enabled: true
    retention-days: 7
    cleanup-interval-ms: 21600000
  degradation:
    enabled: false
```

Create `deploy/nacos/config/community-upload-policy.yaml`:

```yaml
community:
  upload:
    max-file-size: 10GB
    max-request-size: 10GB
    allowed-mime-types:
      - image/jpeg
      - image/png
      - image/webp
      - video/mp4
      - application/pdf
    allowed-extensions:
      - jpg
      - jpeg
      - png
      - webp
      - mp4
      - pdf
    avatar-upload-enabled: true
    media-upload-enabled: true
```

Create `deploy/nacos/config/community-notification-policy.yaml`:

```yaml
notice:
  channels:
    email-enabled: true
    in-app-enabled: true
  templates:
    sender-display-name: Community
    default-title: Community notification
    default-link-path: /notifications
  digest:
    enabled: false
    window: PT1H
```

Create `deploy/nacos/config/community-kafka-policy.yaml`:

```yaml
community:
  kafka-policy:
    retry:
      max-attempts: 3
      base-backoff: 1s
      max-backoff: 30s
    dlq:
      enabled: true
    producer:
      enable-idempotence: true
      max-in-flight-requests: 5
```

Create `deploy/nacos/config/community-work-processing.yaml`:

```yaml
events:
  outbox:
    enabled: true
    batch-size: 50
    processing-lease: 30s
    max-retries: 50
    base-backoff: 5s
    max-backoff: 10m
    worker-fixed-delay-ms: 1000

content:
  score:
    refresh:
      enabled: true
      batch-size: 200

analytics:
  max-days-range: 31
  ingest:
    enabled: false
    record-uv: true
    record-dau: true
```

- [ ] **Step 4: Create service seed files**

Create `deploy/nacos/config/community-gateway.yaml`:

```yaml
gateway:
  cors:
    allowed-origins: []
  im-edge:
    service-id: community-im-gateway
    session-path: /api/im/sessions
    ws-path: /ws/im
  http:
    routes:
      - id: oss-api
        path-prefix: /api/oss
        service-id: community-oss
      - id: im-core
        path-prefix: /api/im
        service-id: im-core
      - id: bootstrap-api
        path-prefix: /api
        service-id: community-app
      - id: oss-files
        path-prefix: /files
        service-id: community-oss
    rate-limit:
      enabled: true
      fail-open-on-error: false
      policies: {}
    traffic-policy:
      default-policy-id: baseline
      default-tags:
        plane: http
      rules: []
```

Create `deploy/nacos/config/community-app.yaml`:

```yaml
gateway:
  origin-guard:
    enabled: true
    allowed-origins: []
    fail-open-when-allowlist-empty: false

security:
  jwt:
    access-token-ttl-seconds: 900
    refresh-token-ttl-seconds: 604800
    refresh-cookie-name: refresh_token
    refresh-cookie-path: /api/auth
    refresh-cookie-same-site: Lax
    refresh-cookie-secure: false

auth:
  captcha:
    ttl-seconds: 60
    max-failures: 3
  registration:
    code:
      ttl-seconds: 600
      max-failures: 3
      resend-cooldown-seconds: 60
      expose-code: false
    draft:
      ttl-seconds: 1800
    mail:
      enabled: true
  password-reset:
    ttl-seconds: 600
    request-window-seconds: 3600
    max-requests-per-email: 3
    max-requests-per-ip: 20
  login-rate-limit:
    enabled: true
    window-seconds: 60
    max-failures-per-ip: 20
    max-failures-per-user: 5
    captcha-required-failures-per-ip: 5
    captcha-required-failures-per-user: 2

oss:
  client:
    base-url: http://community-oss:18090
```

Create `deploy/nacos/config/community-oss.yaml`:

```yaml
oss:
  public-base-url: http://localhost:12880
  object-store:
    mode: garage
    endpoint: http://garage:3900
    bucket: community-oss
    region: garage
    path-style: true
    local-root: /tmp/community-oss
```

Create `deploy/nacos/config/community-im-gateway.yaml`:

```yaml
im:
  gateway:
    cors:
      allowed-origins: []
    public-ws-url: ws://localhost:12880/ws/im
    session:
      ticket-ttl: PT2M
    worker:
      service-id: im-realtime-worker
      worker-id-metadata-key: workerId
      ws-path-metadata-key: wsPath
      ws-port-metadata-key: wsPort
    ws:
      path: /ws/im
      first-frame-timeout-ms: 5000
      max-inbound-chars: 10000
```

Create `deploy/nacos/config/im-core.yaml`:

```yaml
events:
  outbox:
    enabled: true
    batch-size: 50
    processing-lease: 30s
    max-retries: 50
    base-backoff: 5s
    max-backoff: 10m
    worker-fixed-delay-ms: 1000
```

Create `deploy/nacos/config/im-realtime.yaml`:

```yaml
im:
  clients:
    community-service-id: community-app
    im-core-service-id: im-core
    membership-snapshot-service-id: im-core
    policy-snapshot-service-id: community-app
    snapshot-timeout-ms: 3000
    internal-scope: im.realtime.internal
  session:
    worker-service-id: im-realtime-worker
    worker-id: local
    ticket-ttl: PT2M
    worker-id-metadata-key: workerId
    ws-path-metadata-key: wsPath
    ws-port-metadata-key: wsPort
  ws:
    path: /internal/ws/im
    outbound-buffer-size: 256
    kafka-send-timeout-ms: 5000
  realtime:
    worker:
      max-connections: 10000
      drain-enabled: false
      shard-group: default
      capacity-weight: 100
```

- [ ] **Step 5: Create the seed publisher script**

Create `deploy/nacos/seed-configs.sh`:

```bash
#!/usr/bin/env sh
set -eu

NACOS_ADDR="${NACOS_ADDR:-http://nacos:8848}"
NACOS_GROUP="${NACOS_CONFIG_GROUP:-COMMUNITY}"
CONFIG_DIR="${CONFIG_DIR:-/nacos/config}"

data_ids="
community-shared.yaml
community-feature-flags.yaml
community-degradation.yaml
community-canary-routing.yaml
community-frontend-runtime.yaml
community-cache-policy.yaml
community-search-policy.yaml
community-upload-policy.yaml
community-notification-policy.yaml
community-kafka-policy.yaml
community-work-processing.yaml
community-gateway.yaml
community-app.yaml
community-oss.yaml
community-im-gateway.yaml
im-core.yaml
im-realtime.yaml
"

echo "[nacos-config-bootstrap] waiting for ${NACOS_ADDR}"
for i in $(seq 1 120); do
  if curl -fsS "${NACOS_ADDR}/nacos/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if [ "$i" -eq 120 ]; then
    echo "[nacos-config-bootstrap] nacos did not become healthy" >&2
    exit 1
  fi
  sleep 1
done

for data_id in ${data_ids}; do
  file="${CONFIG_DIR}/${data_id}"
  test -s "${file}"
  echo "[nacos-config-bootstrap] publishing ${data_id}"
  curl -fsS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${NACOS_GROUP}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@${file}" >/dev/null
done

echo "[nacos-config-bootstrap] done"
```

Run:

```bash
chmod +x deploy/nacos/seed-configs.sh
bash deploy/tests/nacos_config_seed.sh
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add deploy/nacos deploy/tests/nacos_config_seed.sh
git commit -m "feat: seed nacos config dataids"
```

## Task 3: Wire Nacos Config Bootstrap Into Compose

**Files:**
- Modify: `deploy/compose.infra.nacos.single.yml`
- Modify: `deploy/compose.infra.nacos.cluster.yml`
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/tests/topology_single_cluster.sh`

- [ ] **Step 1: Extend topology test first**

In `deploy/tests/topology_single_cluster.sh`, after the existing Nacos healthcheck assertions, add:

```bash
grep -E '^  nacos-config-bootstrap:$' "${single_infra}"
grep -A8 -E '^  nacos-config-bootstrap:$' "${single_infra}" | grep -F './nacos:/nacos:ro'
grep -A6 -E '^      nacos-config-bootstrap:$' "${single_full}" | grep -F 'condition: service_completed_successfully'

grep -E '^  nacos-config-bootstrap:$' "${cluster_infra}"
grep -A8 -E '^  nacos-config-bootstrap:$' "${cluster_infra}" | grep -F './nacos:/nacos:ro'
grep -A6 -E '^      nacos-config-bootstrap:$' "${cluster_full}" | grep -F 'condition: service_completed_successfully'
```

Run:

```bash
bash deploy/tests/topology_single_cluster.sh
```

Expected: FAIL because compose files do not define `nacos-config-bootstrap`.

- [ ] **Step 2: Add compose bootstrap service**

In both Nacos infra compose files, add this service after Nacos server services:

```yaml
  nacos-config-bootstrap:
    image: curlimages/curl:8.11.1
    entrypoint:
    - /bin/sh
    - /nacos/seed-configs.sh
    environment:
    - NACOS_ADDR=${NACOS_CONFIG_BOOTSTRAP_ADDR:-http://nacos:8848}
    - NACOS_CONFIG_GROUP=${NACOS_CONFIG_GROUP:-COMMUNITY}
    - CONFIG_DIR=/nacos/config
    volumes:
    - ./nacos:/nacos:ro
    depends_on:
      nacos:
        condition: service_healthy
    restart: "no"
```

For cluster, set the default address to `http://nacos-1:8848` and depend on `nacos-1`:

```yaml
    - NACOS_ADDR=${NACOS_CONFIG_BOOTSTRAP_ADDR:-http://nacos-1:8848}
    depends_on:
      nacos-1:
        condition: service_healthy
```

- [ ] **Step 3: Make runtime services depend on config bootstrap**

For every backend service in `deploy/compose.runtime.services.single.yml`, add:

```yaml
      nacos-config-bootstrap:
        condition: service_completed_successfully
```

Add it beside the existing `nacos-db-bootstrap` dependency for:

```text
community-app
community-oss
community-gateway
community-im-gateway
im-core
im-realtime
```

In `deploy/compose.runtime.services.cluster.yml`, add the same dependency for every backend replica service.

- [ ] **Step 4: Add config import env vars to compose**

For each backend service environment block, add:

```yaml
- SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
- NACOS_CONFIG_GROUP=${NACOS_CONFIG_GROUP:-COMMUNITY}
```

For cluster, use:

```yaml
- SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos-1:8848,nacos-2:8848,nacos-3:8848}
- NACOS_CONFIG_GROUP=${NACOS_CONFIG_GROUP:-COMMUNITY}
```

For production-like required mode, keep optional imports out of local defaults. Required imports are covered in Task 10.

- [ ] **Step 5: Run compose topology checks**

Run:

```bash
bash deploy/tests/nacos_config_seed.sh
bash deploy/tests/topology_single_cluster.sh
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add deploy
git commit -m "feat: bootstrap nacos configs in compose"
```

## Task 4: Add Gateway Dynamic Config Refresh

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayConfigRefreshListener.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/config/GatewayConfigRefreshListenerTest.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/GatewayRouteLocatorConfig.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteConfig.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitProperties.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TrafficPolicyProperties.java`

- [ ] **Step 1: Write failing refresh listener test**

Create `GatewayConfigRefreshListenerTest.java`:

```java
package com.nowcoder.community.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigRefreshListenerTest {

    @Test
    void publishesRefreshRoutesEventWhenGatewayKeysChange() {
        AtomicReference<Object> event = new AtomicReference<>();
        ApplicationEventPublisher publisher = event::set;
        GatewayConfigRefreshListener listener = new GatewayConfigRefreshListener(publisher);

        listener.onEnvironmentChange(Set.of("gateway.http.routes[0].service-id"));

        assertThat(event.get()).isInstanceOf(RefreshRoutesEvent.class);
    }

    @Test
    void ignoresUnrelatedKeys() {
        AtomicReference<Object> event = new AtomicReference<>();
        ApplicationEventPublisher publisher = event::set;
        GatewayConfigRefreshListener listener = new GatewayConfigRefreshListener(publisher);

        listener.onEnvironmentChange(Set.of("security.jwt.issuer"));

        assertThat(event.get()).isNull();
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest=GatewayConfigRefreshListenerTest test
```

Expected: FAIL because `GatewayConfigRefreshListener` does not exist.

- [ ] **Step 2: Implement refresh listener**

Create `GatewayConfigRefreshListener.java`:

```java
package com.nowcoder.community.gateway.config;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class GatewayConfigRefreshListener {

    private final ApplicationEventPublisher publisher;

    public GatewayConfigRefreshListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        onEnvironmentChange(event.getKeys());
    }

    void onEnvironmentChange(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (keys.stream().anyMatch(GatewayConfigRefreshListener::isGatewayRouteKey)) {
            publisher.publishEvent(new RefreshRoutesEvent(this));
        }
    }

    private static boolean isGatewayRouteKey(String key) {
        return key != null && (
                key.startsWith("gateway.http.routes")
                        || key.startsWith("gateway.im-edge")
                        || key.startsWith("gateway.http.canary")
        );
    }
}
```

- [ ] **Step 3: Run refresh listener test**

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest=GatewayConfigRefreshListenerTest test
```

Expected: PASS.

- [ ] **Step 4: Add property snapshot helpers**

In `RateLimitProperties.Policy`, add:

```java
public Policy copy() {
    Policy copy = new Policy();
    copy.setEnabled(enabled);
    copy.setLimit(limit);
    copy.setWindow(window);
    return copy;
}
```

In `TrafficPolicyProperties.Rule`, add setter/copy support for list/map based refresh:

```java
public Rule copy() {
    Rule copy = new Rule();
    copy.setEnabled(enabled);
    copy.setPolicyId(policyId);
    copy.getPathPrefixes().addAll(pathPrefixes);
    copy.getMethods().addAll(methods);
    copy.getTags().putAll(tags);
    return copy;
}
```

- [ ] **Step 5: Run gateway edge tests**

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest='GatewayConfigRefreshListenerTest,RateLimitWebFilterTest,TrafficPolicyEvaluatorTest,HttpRoutingIntegrationTest,GatewayImEdgeRouteIntegrationTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-gateway
git commit -m "feat: refresh gateway nacos config"
```

## Task 5: Add Canary Routing Model And Metadata Filtering

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/canary/CanaryRouteProperties.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/canary/CanaryInstanceFilter.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/canary/CanaryInstanceFilterTest.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TrafficPolicyProperties.java`

- [ ] **Step 1: Write failing metadata selector test**

Create `CanaryInstanceFilterTest.java`:

```java
package com.nowcoder.community.gateway.canary;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanaryInstanceFilterTest {

    @Test
    void selectsInstancesByReleaseTrackAndTrafficGroup() {
        CanaryRouteProperties.Selector selector = new CanaryRouteProperties.Selector();
        selector.getMetadata().put("release.track", "canary");
        selector.getMetadata().put("traffic.group", "beta");

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("stable-1", Map.of("release.track", "stable", "traffic.group", "baseline")),
                instance("canary-1", Map.of("release.track", "canary", "traffic.group", "beta"))
        ), selector);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("canary-1");
    }

    @Test
    void excludesDrainingInstances() {
        CanaryRouteProperties.Selector selector = new CanaryRouteProperties.Selector();
        selector.getMetadata().put("release.track", "canary");

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("canary-draining", Map.of("release.track", "canary", "draining", "true")),
                instance("canary-ready", Map.of("release.track", "canary", "draining", "false"))
        ), selector);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("canary-ready");
    }

    private static ServiceInstance instance(String id, Map<String, String> metadata) {
        DefaultServiceInstance instance = new DefaultServiceInstance(id, "community-app", "127.0.0.1", 8080, false);
        instance.getMetadata().putAll(metadata);
        return instance;
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest=CanaryInstanceFilterTest test
```

Expected: FAIL because canary classes do not exist.

- [ ] **Step 2: Add canary properties**

Create `CanaryRouteProperties.java`:

```java
package com.nowcoder.community.gateway.canary;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.http.canary")
public class CanaryRouteProperties {

    private final List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return rules;
    }

    public static class Rule {
        private boolean enabled = true;
        private String serviceId;
        private String pathPrefix;
        private String method;
        private String fallback = "stable";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private final Selector selector = new Selector();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public Map<String, String> getCookies() {
            return cookies;
        }

        public Selector getSelector() {
            return selector;
        }
    }

    public static class Selector {
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Map<String, String> getMetadata() {
            return metadata;
        }
    }
}
```

- [ ] **Step 3: Add metadata filter**

Create `CanaryInstanceFilter.java`:

```java
package com.nowcoder.community.gateway.canary;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CanaryInstanceFilter {

    public List<ServiceInstance> filter(List<ServiceInstance> instances, CanaryRouteProperties.Selector selector) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        Map<String, String> required = selector == null ? Map.of() : selector.getMetadata();
        return instances.stream()
                .filter(instance -> instance != null)
                .filter(instance -> !"true".equalsIgnoreCase(instance.getMetadata().getOrDefault("draining", "false")))
                .filter(instance -> matches(instance, required))
                .toList();
    }

    private static boolean matches(ServiceInstance instance, Map<String, String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Map<String, String> metadata = instance.getMetadata();
        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (!entry.getValue().equals(metadata.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 4: Enable canary properties**

In `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/EdgeConfig.java`, add `CanaryRouteProperties.class` to `@EnableConfigurationProperties`.

The annotation should include:

```java
@EnableConfigurationProperties({
        RateLimitProperties.class,
        TrafficPolicyProperties.class,
        CanaryRouteProperties.class
})
```

Add import:

```java
import com.nowcoder.community.gateway.canary.CanaryRouteProperties;
```

- [ ] **Step 5: Run canary tests**

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest='CanaryInstanceFilterTest,TrafficPolicyEvaluatorTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-gateway
git commit -m "feat: add gateway canary metadata filtering"
```

## Task 6: Add Feature Flag And Degradation Accessors

**Files:**
- Create: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/feature/FeatureFlagProperties.java`
- Create: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/feature/FeatureFlagDecisions.java`
- Create: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/degradation/DegradationProperties.java`
- Create: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/degradation/DegradationDecisions.java`
- Create: `backend/community-common/common-spring/src/test/java/com/nowcoder/community/common/spring/feature/FeatureFlagDecisionsTest.java`
- Create: `backend/community-common/common-spring/src/test/java/com/nowcoder/community/common/spring/degradation/DegradationDecisionsTest.java`

- [ ] **Step 1: Write failing feature flag tests**

Create `FeatureFlagDecisionsTest.java`:

```java
package com.nowcoder.community.common.spring.feature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagDecisionsTest {

    @Test
    void returnsConfiguredFlagValue() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.getFlags().put("post-publishing", true);

        assertThat(new FeatureFlagDecisions(properties).enabled("post-publishing")).isTrue();
    }

    @Test
    void unknownFlagsDefaultToFalse() {
        assertThat(new FeatureFlagDecisions(new FeatureFlagProperties()).enabled("missing")).isFalse();
    }
}
```

Run:

```bash
cd backend
mvn -pl community-common/common-spring -Dtest=FeatureFlagDecisionsTest test
```

Expected: FAIL because feature classes do not exist.

- [ ] **Step 2: Implement feature flag classes**

Create `FeatureFlagProperties.java`:

```java
package com.nowcoder.community.common.spring.feature;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "community.features")
public class FeatureFlagProperties {

    private final Map<String, Boolean> flags = new LinkedHashMap<>();

    public Map<String, Boolean> getFlags() {
        return flags;
    }
}
```

Create `FeatureFlagDecisions.java`:

```java
package com.nowcoder.community.common.spring.feature;

public class FeatureFlagDecisions {

    private final FeatureFlagProperties properties;

    public FeatureFlagDecisions(FeatureFlagProperties properties) {
        this.properties = properties;
    }

    public boolean enabled(String key) {
        if (key == null || key.isBlank() || properties == null) {
            return false;
        }
        return Boolean.TRUE.equals(properties.getFlags().get(key));
    }
}
```

- [ ] **Step 3: Write and implement degradation tests/classes**

Create `DegradationDecisionsTest.java`:

```java
package com.nowcoder.community.common.spring.degradation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DegradationDecisionsTest {

    @Test
    void returnsConfiguredMode() {
        DegradationProperties properties = new DegradationProperties();
        properties.getModes().put("search", "best-effort");

        assertThat(new DegradationDecisions(properties).mode("search")).isEqualTo("best-effort");
    }

    @Test
    void unknownModeDefaultsToStrict() {
        assertThat(new DegradationDecisions(new DegradationProperties()).mode("missing")).isEqualTo("strict");
    }
}
```

Create `DegradationProperties.java`:

```java
package com.nowcoder.community.common.spring.degradation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "community.degradation")
public class DegradationProperties {

    private final Map<String, String> modes = new LinkedHashMap<>();

    public Map<String, String> getModes() {
        return modes;
    }
}
```

Create `DegradationDecisions.java`:

```java
package com.nowcoder.community.common.spring.degradation;

import java.util.Set;

public class DegradationDecisions {

    private static final Set<String> ALLOWED = Set.of("off", "read-only", "best-effort", "strict");
    private final DegradationProperties properties;

    public DegradationDecisions(DegradationProperties properties) {
        this.properties = properties;
    }

    public String mode(String key) {
        if (key == null || key.isBlank() || properties == null) {
            return "strict";
        }
        String mode = properties.getModes().getOrDefault(key, "strict");
        return ALLOWED.contains(mode) ? mode : "strict";
    }
}
```

- [ ] **Step 4: Register common properties**

Create or update a common-spring auto configuration class to expose:

```java
@EnableConfigurationProperties({
        FeatureFlagProperties.class,
        DegradationProperties.class
})
```

If `common-spring` has no existing auto configuration for these properties, create:

`backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/spring/autoconfig/RuntimePolicyAutoConfiguration.java`

```java
package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.degradation.DegradationDecisions;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties({FeatureFlagProperties.class, DegradationProperties.class})
public class RuntimePolicyAutoConfiguration {

    @Bean
    FeatureFlagDecisions featureFlagDecisions(FeatureFlagProperties properties) {
        return new FeatureFlagDecisions(properties);
    }

    @Bean
    DegradationDecisions degradationDecisions(DegradationProperties properties) {
        return new DegradationDecisions(properties);
    }
}
```

Add this line to `backend/community-common/common-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```text
com.nowcoder.community.common.spring.autoconfig.RuntimePolicyAutoConfiguration
```

- [ ] **Step 5: Run common-spring tests**

Run:

```bash
cd backend
mvn -pl community-common/common-spring test -Dtest='FeatureFlagDecisionsTest,DegradationDecisionsTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-common/common-spring
git commit -m "feat: add runtime policy decisions"
```

## Task 7: Add Browser Runtime Config Endpoint

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/runtime/config/RuntimeConfigProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/runtime/config/RuntimeConfigApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/runtime/config/RuntimeConfigController.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/runtime/config/RuntimeConfigControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Create `RuntimeConfigControllerTest.java`:

```java
package com.nowcoder.community.runtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigControllerTest {

    @Test
    void exposesOnlyBrowserSafeFields() {
        RuntimeConfigProperties properties = new RuntimeConfigProperties();
        properties.setApiBasePath("/api");
        properties.setWebsocketUrl("ws://localhost:12880/ws/im");
        properties.getFeatures().put("file-upload", true);

        ResponseEntity<RuntimeConfigApplicationService.RuntimeConfigResult> response =
                new RuntimeConfigController(new RuntimeConfigApplicationService(properties)).runtimeConfig();

        RuntimeConfigApplicationService.RuntimeConfigResult body = response.getBody();
        assertThat(body.apiBasePath()).isEqualTo("/api");
        assertThat(body.websocketUrl()).isEqualTo("ws://localhost:12880/ws/im");
        assertThat(body.features()).containsEntry("file-upload", true);
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=RuntimeConfigControllerTest test
```

Expected: FAIL because runtime config classes do not exist.

- [ ] **Step 2: Implement properties and service**

Create `RuntimeConfigProperties.java`:

```java
package com.nowcoder.community.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "frontend.runtime")
public class RuntimeConfigProperties {

    private String apiBasePath = "/api";
    private String publicGatewayOrigin = "";
    private String websocketUrl = "";
    private boolean analyticsEnabled = false;
    private double analyticsSampleRate = 0.0;
    private String releaseChannel = "local";
    private final Map<String, Boolean> features = new LinkedHashMap<>();

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public String getPublicGatewayOrigin() {
        return publicGatewayOrigin;
    }

    public void setPublicGatewayOrigin(String publicGatewayOrigin) {
        this.publicGatewayOrigin = publicGatewayOrigin;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    public void setAnalyticsEnabled(boolean analyticsEnabled) {
        this.analyticsEnabled = analyticsEnabled;
    }

    public double getAnalyticsSampleRate() {
        return analyticsSampleRate;
    }

    public void setAnalyticsSampleRate(double analyticsSampleRate) {
        this.analyticsSampleRate = analyticsSampleRate;
    }

    public String getReleaseChannel() {
        return releaseChannel;
    }

    public void setReleaseChannel(String releaseChannel) {
        this.releaseChannel = releaseChannel;
    }

    public Map<String, Boolean> getFeatures() {
        return features;
    }
}
```

Create `RuntimeConfigApplicationService.java`:

```java
package com.nowcoder.community.runtime.config;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RuntimeConfigApplicationService {

    private final RuntimeConfigProperties properties;

    public RuntimeConfigApplicationService(RuntimeConfigProperties properties) {
        this.properties = properties;
    }

    public RuntimeConfigResult current() {
        return new RuntimeConfigResult(
                properties.getApiBasePath(),
                properties.getPublicGatewayOrigin(),
                properties.getWebsocketUrl(),
                properties.isAnalyticsEnabled(),
                properties.getAnalyticsSampleRate(),
                properties.getReleaseChannel(),
                Map.copyOf(properties.getFeatures())
        );
    }

    public record RuntimeConfigResult(
            String apiBasePath,
            String publicGatewayOrigin,
            String websocketUrl,
            boolean analyticsEnabled,
            double analyticsSampleRate,
            String releaseChannel,
            Map<String, Boolean> features
    ) {
    }
}
```

- [ ] **Step 3: Implement controller through application service**

Create `RuntimeConfigController.java`:

```java
package com.nowcoder.community.runtime.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-config")
public class RuntimeConfigController {

    private final RuntimeConfigApplicationService applicationService;

    public RuntimeConfigController(RuntimeConfigApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<RuntimeConfigApplicationService.RuntimeConfigResult> runtimeConfig() {
        return ResponseEntity.ok(applicationService.current());
    }
}
```

Because this is a controller, it must call only the same-area application service and must not call Nacos, repositories, mappers, or foreign APIs directly.

- [ ] **Step 4: Run runtime config tests**

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=RuntimeConfigControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/runtime backend/community-app/src/test/java/com/nowcoder/community/runtime
git commit -m "feat: expose browser runtime config"
```

## Task 8: Add IM Worker Drain And Capacity Metadata

**Files:**
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerDescriptor.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/DiscoveredWorkerDescriptorFactory.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerRegistry.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelector.java`
- Modify: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/shard/DiscoveredWorkerDescriptorFactoryTest.java`
- Modify: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelectorTest.java`

- [ ] **Step 1: Write failing metadata tests**

In `DiscoveredWorkerDescriptorFactoryTest`, update `shouldBuildWorkerDescriptorFromServiceInstanceMetadata`:

```java
assertThat(descriptor.isDraining()).isFalse();
assertThat(descriptor.getMaxConnections()).isEqualTo(100);
assertThat(descriptor.getActiveConnectionHint()).isEqualTo(25);
assertThat(descriptor.getShardGroup()).isEqualTo("local-a");
```

Update `instanceWithPort` to add:

```java
instance.getMetadata().put("draining", "false");
instance.getMetadata().put("maxConnections", "100");
instance.getMetadata().put("activeConnectionHint", "25");
instance.getMetadata().put("shardGroup", "local-a");
```

Add a new test:

```java
@Test
void shouldIgnoreDrainingWorkersForHealthySelection() {
    WorkerRegistry registry = new WorkerRegistry(List.of(
            new WorkerDescriptor("worker-a", URI.create("ws://127.0.0.1:18081/internal/ws/im"), true, 100, 0, "local"),
            new WorkerDescriptor("worker-b", URI.create("ws://127.0.0.1:18082/internal/ws/im"), false, 100, 0, "local")
    ));

    assertThat(registry.healthyWorkers()).extracting(WorkerDescriptor::getId).containsExactly("worker-b");
}
```

Run:

```bash
cd backend
mvn -pl :community-im-gateway -Dtest='DiscoveredWorkerDescriptorFactoryTest,RendezvousWorkerSelectorTest' test
```

Expected: FAIL because `WorkerDescriptor` does not expose the new fields.

- [ ] **Step 2: Extend WorkerDescriptor**

Replace `WorkerDescriptor` with:

```java
package com.nowcoder.community.im.gateway.shard;

import java.net.URI;
import java.util.Objects;

public class WorkerDescriptor {

    private final String id;
    private final URI uri;
    private final boolean draining;
    private final int maxConnections;
    private final int activeConnectionHint;
    private final String shardGroup;

    public WorkerDescriptor(String id, URI uri) {
        this(id, uri, false, 0, 0, "default");
    }

    public WorkerDescriptor(String id, URI uri, boolean draining, int maxConnections, int activeConnectionHint, String shardGroup) {
        this.id = id;
        this.uri = uri;
        this.draining = draining;
        this.maxConnections = Math.max(maxConnections, 0);
        this.activeConnectionHint = Math.max(activeConnectionHint, 0);
        this.shardGroup = shardGroup == null || shardGroup.isBlank() ? "default" : shardGroup.trim();
    }

    public String getId() {
        return id;
    }

    public URI getUri() {
        return uri;
    }

    public boolean isDraining() {
        return draining;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getActiveConnectionHint() {
        return activeConnectionHint;
    }

    public String getShardGroup() {
        return shardGroup;
    }

    public int availableCapacity() {
        if (maxConnections <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxConnections - activeConnectionHint, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkerDescriptor that)) {
            return false;
        }
        return draining == that.draining
                && maxConnections == that.maxConnections
                && activeConnectionHint == that.activeConnectionHint
                && Objects.equals(id, that.id)
                && Objects.equals(uri, that.uri)
                && Objects.equals(shardGroup, that.shardGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uri, draining, maxConnections, activeConnectionHint, shardGroup);
    }
}
```

- [ ] **Step 3: Parse metadata in factory**

In `DiscoveredWorkerDescriptorFactory`, add parsing helpers:

```java
private static boolean parseBoolean(String value) {
    return "true".equalsIgnoreCase(value == null ? "" : value.trim());
}

private static int parseNonNegativeInt(String value, int defaultValue) {
    if (!StringUtils.hasText(value)) {
        return defaultValue;
    }
    int parsed = 0;
    String trimmed = value.trim();
    for (int index = 0; index < trimmed.length(); index++) {
        char ch = trimmed.charAt(index);
        if (ch < '0' || ch > '9') {
            return defaultValue;
        }
        parsed = parsed * 10 + ch - '0';
        if (parsed < 0) {
            return defaultValue;
        }
    }
    return parsed;
}
```

When returning the descriptor, use:

```java
boolean draining = parseBoolean(metadata.get("draining"));
int maxConnections = parseNonNegativeInt(metadata.get("maxConnections"), 0);
int activeConnectionHint = parseNonNegativeInt(metadata.get("activeConnectionHint"), 0);
String shardGroup = metadata.getOrDefault("shardGroup", "default");
return Optional.of(new WorkerDescriptor(workerId.trim(), uri, draining, maxConnections, activeConnectionHint, shardGroup));
```

- [ ] **Step 4: Exclude draining workers**

In `WorkerRegistry.isValid`, require non-draining:

```java
private static boolean isValid(WorkerDescriptor worker) {
    return worker != null
            && !worker.isDraining()
            && StringUtils.hasText(worker.getId())
            && worker.getUri() != null;
}
```

- [ ] **Step 5: Run IM gateway tests**

Run:

```bash
cd backend
mvn -pl :community-im-gateway -Dtest='DiscoveredWorkerDescriptorFactoryTest,RendezvousWorkerSelectorTest,ImSessionApiIntegrationTest,ImEdgeWebSocketBridgeIntegrationTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-im-gateway
git commit -m "feat: honor im worker drain metadata"
```

## Task 9: Add Policy Binding Tests For Config-Center DataIds

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/config/NacosPolicyBindingTest.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/config/NacosGatewayBindingTest.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/config/NacosImGatewayBindingTest.java`
- Create: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/config/NacosImRealtimeBindingTest.java`

- [ ] **Step 1: Write binding test for gateway seed**

Create `NacosGatewayBindingTest.java`:

```java
package com.nowcoder.community.gateway.config;

import com.nowcoder.community.gateway.edge.RateLimitProperties;
import com.nowcoder.community.gateway.edge.TrafficPolicyProperties;
import com.nowcoder.community.gateway.http.GatewayHttpRouteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class NacosGatewayBindingTest {

    @Test
    void bindsGatewaySeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("deploy/nacos/config/community-gateway.yaml");
        Binder binder = Binder.get(environment);

        GatewayHttpRouteProperties routes = binder.bind("gateway.http", GatewayHttpRouteProperties.class).orElseThrow();
        RateLimitProperties rateLimit = binder.bind("gateway.http.rate-limit", RateLimitProperties.class).orElseThrow();
        TrafficPolicyProperties traffic = binder.bind("gateway.http.traffic-policy", TrafficPolicyProperties.class).orElseThrow();

        assertThat(routes.getRoutes()).extracting(GatewayHttpRouteProperties.Route::getServiceId)
                .contains("community-app", "community-oss", "im-core");
        assertThat(rateLimit.isEnabled()).isTrue();
        assertThat(traffic.getDefaultPolicyId()).isEqualTo("baseline");
    }

    private static StandardEnvironment environmentFrom(String path) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).get(0));
        return environment;
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-gateway -Dtest=NacosGatewayBindingTest test
```

Expected: PASS after Task 2 seed files exist.

- [ ] **Step 2: Add app policy binding test**

Create `NacosPolicyBindingTest.java`:

```java
package com.nowcoder.community.config;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class NacosPolicyBindingTest {

    @Test
    void bindsCommunityAppSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("deploy/nacos/config/community-app.yaml");
        Binder binder = Binder.get(environment);

        OriginGuardProperties originGuard = binder.bind("gateway.origin-guard", OriginGuardProperties.class).orElseThrow();
        LoginRateLimitProperties loginRateLimit = binder.bind("auth.login-rate-limit", LoginRateLimitProperties.class).orElseThrow();

        assertThat(originGuard.isEnabled()).isTrue();
        assertThat(loginRateLimit.isEnabled()).isTrue();
    }

    private static StandardEnvironment environmentFrom(String path) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).get(0));
        return environment;
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=NacosPolicyBindingTest test
```

Expected: PASS.

- [ ] **Step 3: Add IM binding tests**

Create `NacosImGatewayBindingTest.java`:

```java
package com.nowcoder.community.im.gateway.config;

import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class NacosImGatewayBindingTest {

    @Test
    void bindsImGatewaySeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("deploy/nacos/config/community-im-gateway.yaml");
        ImGatewaySessionProperties properties = Binder.get(environment)
                .bind("im.gateway", ImGatewaySessionProperties.class)
                .orElseThrow();

        assertThat(properties.getWorker().getServiceId()).isEqualTo("im-realtime-worker");
        assertThat(properties.getWs().getPath()).isEqualTo("/ws/im");
    }

    private static StandardEnvironment environmentFrom(String path) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).get(0));
        return environment;
    }
}
```

Create `NacosImRealtimeBindingTest.java`:

```java
package com.nowcoder.community.im.realtime.config;

import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class NacosImRealtimeBindingTest {

    @Test
    void bindsImRealtimeSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("deploy/nacos/config/im-realtime.yaml");
        ImServiceClientProperties properties = Binder.get(environment)
                .bind("im.clients", ImServiceClientProperties.class)
                .orElseThrow();

        assertThat(properties.getCommunityServiceId()).isEqualTo("community-app");
        assertThat(properties.getImCoreServiceId()).isEqualTo("im-core");
    }

    private static StandardEnvironment environmentFrom(String path) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).get(0));
        return environment;
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-im-gateway,:im-realtime -Dtest='NacosImGatewayBindingTest,NacosImRealtimeBindingTest' test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend
git commit -m "test: verify nacos seed binding"
```

## Task 10: Update Startup Validation For Required Config Imports

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/infra/startup/StartupValidationTest.java`

- [ ] **Step 1: Write failing startup validation tests**

Create `StartupValidationTest.java`:

```java
package com.nowcoder.community.infra.startup;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupValidationTest {

    @Test
    void prodShouldRejectMissingRequiredNacosImportsWhenEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.application.name", "community-app")
                .withProperty("security.jwt.hmac-secret", "01234567890123456789012345678901")
                .withProperty("community.nacos.config.required", "true");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NACOS_CONFIG_IMPORT_SHARED")
                .hasMessageContaining("NACOS_CONFIG_IMPORT_SERVICE");
    }

    @Test
    void prodShouldAcceptRequiredNacosImportsWhenConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.application.name", "community-app")
                .withProperty("security.jwt.hmac-secret", "01234567890123456789012345678901")
                .withProperty("community.nacos.config.required", "true")
                .withProperty("NACOS_CONFIG_IMPORT_SHARED", "nacos:community-shared.yaml?group=COMMUNITY")
                .withProperty("NACOS_CONFIG_IMPORT_SERVICE", "nacos:community-app.yaml?group=COMMUNITY");
        environment.setActiveProfiles("prod");

        new StartupValidation().validateOrThrow(environment);
    }
}
```

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=StartupValidationTest test
```

Expected: FAIL because startup validation does not check Nacos import variables.

- [ ] **Step 2: Implement Nacos import validation**

In `StartupValidation.validateOrThrow`, after JWT validation, add:

```java
validateNacosConfig(environment, errors);
```

Add this method:

```java
private void validateNacosConfig(Environment environment, List<String> errors) {
    Boolean required = environment.getProperty("community.nacos.config.required", Boolean.class, Boolean.FALSE);
    if (!Boolean.TRUE.equals(required)) {
        return;
    }
    requireNonBlank(environment, errors, "NACOS_CONFIG_IMPORT_SHARED",
            "生产环境启用 Nacos Config required 模式时必须导入 community-shared.yaml");
    requireNonBlank(environment, errors, "NACOS_CONFIG_IMPORT_SERVICE",
            "生产环境启用 Nacos Config required 模式时必须导入当前服务 dataId");
}
```

Replace the old fix guide line:

```java
sb.append(" - 检查 Nacos 配置是否已发布（prod profile 下应为 required/fail-fast）").append('\n');
```

with:

```java
sb.append(" - 如果 community.nacos.config.required=true，检查 NACOS_CONFIG_IMPORT_SHARED / NACOS_CONFIG_IMPORT_SERVICE 是否使用 required nacos: dataId").append('\n');
sb.append(" - 检查 Nacos dataId 是否已发布到正确 namespace/group").append('\n');
```

- [ ] **Step 3: Run startup validation tests**

Run:

```bash
cd backend
mvn -pl :community-app -Dtest='StartupValidationTest,AuthStartupValidatorTest' test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/infra/startup backend/community-app/src/test/java/com/nowcoder/community/infra/startup
git commit -m "feat: validate required nacos imports"
```

## Task 11: Document Nacos Config And Discovery Operations

**Files:**
- Modify: `docs/handbook/local-development.md`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `deploy/README.md`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`

- [ ] **Step 1: Update local development docs**

In `docs/handbook/local-development.md`, add a section after the Nacos default port table:

```markdown
## Nacos Config And Discovery

Nacos is both the local service registry and non-secret configuration center.
`nacos-db-bootstrap` initializes the Nacos MySQL schema, then
`nacos-config-bootstrap` publishes YAML dataIds from `deploy/nacos/config`.

Local services import config with optional `nacos:` imports so IDE startup can still
fall back to packaged defaults. Production-like runs set required imports through
`NACOS_CONFIG_IMPORT_SHARED` and `NACOS_CONFIG_IMPORT_SERVICE`.

Secrets do not live in Nacos Config. Keep JWT HMAC secrets, database passwords,
object-store access keys, XXL-JOB tokens, and Nacos credentials in `.env` or a
secret manager.
```

- [ ] **Step 2: Update operations docs**

In `docs/handbook/operations.md`, add verification commands:

```markdown
### Nacos Config Verification

List a seeded config:

```bash
curl -fsS "http://localhost:18848/nacos/v1/cs/configs?dataId=community-gateway.yaml&group=COMMUNITY"
```

List IM worker registration metadata:

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

If a required config import is missing in production-like mode, the service must
fail startup before serving traffic. Check `NACOS_CONFIG_IMPORT_SHARED`,
`NACOS_CONFIG_IMPORT_SERVICE`, `NACOS_NAMESPACE`, and `NACOS_CONFIG_GROUP`.
```
```

- [ ] **Step 3: Update deploy README**

In `deploy/README.md`, update the Nacos bullet:

```markdown
- Nacos：`http://localhost:18848/nacos`，作为服务注册中心和非密钥配置中心。
```

Add:

```markdown
`nacos-config-bootstrap` 会把 `deploy/nacos/config/*.yaml` 发布到 Nacos group
`COMMUNITY`。这些 seed 文件不得包含密码、token、access key、JWT HMAC secret 或
其他密钥。
```

- [ ] **Step 4: Update env examples**

In both env examples, add:

```dotenv
# Nacos Config / Discovery
NACOS_CONFIG_GROUP=COMMUNITY
NACOS_NAMESPACE=
COMMUNITY_NACOS_CONFIG_REQUIRED=false
```

Do not add secret values.

- [ ] **Step 5: Run docs checks**

Run:

```bash
git diff --check -- docs/handbook/local-development.md docs/handbook/operations.md docs/handbook/system-design.md deploy/README.md deploy/.env.single.example deploy/.env.cluster.example
bash deploy/tests/nacos_config_seed.sh
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add docs/handbook deploy/README.md deploy/.env.single.example deploy/.env.cluster.example
git commit -m "docs: document nacos config center"
```

## Task 12: Final Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run backend focused tests**

Run:

```bash
cd backend
mvn test -pl :community-gateway,:community-im-gateway,:im-realtime,:community-app,:community-oss -am
```

Expected: PASS.

- [ ] **Step 2: Run deploy verification**

Run:

```bash
bash deploy/tests/nacos_config_seed.sh
bash deploy/tests/topology_single_cluster.sh
./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example >/tmp/community-single-nacos-config.yml
./deploy/deployment.sh config --topology cluster --env-file deploy/.env.cluster.example >/tmp/community-cluster-nacos-config.yml
```

Expected: PASS. The rendered compose files contain `nacos-config-bootstrap`.

- [ ] **Step 3: Run secret scan on seed files**

Run:

```bash
if rg -n -i '(password|secret|access[_-]?key|hmac|token):[[:space:]]*[^$[:space:]]+' deploy/nacos/config; then
  exit 1
fi
```

Expected: exit 0 with no matches.

- [ ] **Step 4: Run diff check**

Run:

```bash
git diff --check
```

Expected: PASS.

- [ ] **Step 5: Commit any final fixes**

If the final verification required fixes, commit them:

```bash
git add backend deploy docs
git commit -m "chore: finalize nacos config integration"
```

If no files changed, do not create an empty commit.
