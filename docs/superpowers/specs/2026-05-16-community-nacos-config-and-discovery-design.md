# Community Nacos Config And Discovery Design

Date: 2026-05-16

## Status

Draft for implementation planning. Implementation has not started.

## Context

The backend already uses Nacos for service registration and discovery. The deployed
Java services depend on `spring-cloud-starter-alibaba-nacos-discovery`, configure
`spring.cloud.nacos.discovery.server-addr`, and register these service ids:

- `community-app`
- `community-oss`
- `community-gateway`
- `community-im-gateway`
- `im-core`
- `im-realtime-worker`

`community-gateway` consumes discovery through Spring Cloud Gateway `lb://` and
`lb:ws://` routes. `community-im-gateway` consumes `DiscoveryClient` directly to
find `im-realtime-worker` instances and read worker metadata. `im-realtime` uses a
`@LoadBalanced WebClient.Builder` for internal service-name based calls.

The repository does not currently use Nacos Config. There is no
`spring-cloud-starter-alibaba-nacos-config` dependency, no `spring.config.import`
entry for Nacos, and no baseline config seed into the local Nacos server. Runtime
configuration is split across packaged `application.yml` files and compose
environment variables.

Nacos upstream documentation describes Spring Cloud integration as two separate
capabilities: `spring-cloud-starter-alibaba-nacos-config` for configuration
management and dynamic changes, and `spring-cloud-starter-alibaba-nacos-discovery`
for service registration and discovery. Spring Cloud Alibaba 2023 examples use
`spring.config.import` entries to import Nacos dataIds into the Spring Environment.

## Problem Statement

The project has several configuration families that change by environment or are
operationally tuned after deployment:

- gateway route tables, IM edge route targets, rate limits, and traffic policy,
- CORS and origin-guard allowlists,
- IM session, worker, timeout, and public WebSocket settings,
- runtime observability thresholds and log toggles,
- outbox, analytics, search cleanup, and scheduled workload thresholds,
- OSS public URLs and non-secret object-store routing values.

Keeping these values only in packaged files and compose env vars creates avoidable
friction:

- changing gateway routing or rate-limit policy requires image or environment
  changes rather than a controlled config publication,
- local single and cluster topologies duplicate many values,
- production fail-fast behavior is not tied to an explicit config-source contract,
- Nacos is already running but only partially used,
- service discovery metadata is underused for canary, zone, version, and capability
  aware routing.

The system needs a conservative Nacos Config adoption path that centralizes the
right operational config without moving secrets or unstable deployment wiring into
Nacos.

## Goals

- Add Nacos Config as a first-class configuration source for backend deployables.
- Keep Nacos Discovery registration behavior intact.
- Define a clear taxonomy for what belongs in Nacos Config, what remains in env,
  and what must stay in a secret store.
- Seed local Nacos Config dataIds for `single` and `cluster` topologies so local
  compose remains reproducible.
- Make production config import required and fail-fast; keep local development able
  to start from packaged defaults when Nacos Config is intentionally unavailable.
- Add service discovery metadata for environment, version, zone, traffic group,
  protocol, capabilities, and worker identity.
- Introduce dynamic refresh only for explicitly tested, low-risk configuration
  surfaces.
- Update handbook and deploy docs so Nacos is documented as both discovery and
  config center after this work.

## Non-Goals

- Do not move secrets into ordinary Nacos Config.
- Do not replace deployment platform secrets, `.env` files, or future Secret
  managers with Nacos.
- Do not make every property dynamically refreshable.
- Do not let business domain code call Nacos APIs directly.
- Do not use same-domain `api.*`, domain models, mappers, or DDD business packages
  for config loading.
- Do not change the public API prefixes, WebSocket prefix, service ownership, or
  DDD tactical layering rules.
- Do not introduce a separate configuration service or Spring Cloud Config Server.

## Key Decisions

### Decision 1: Nacos Config Is For Non-Secret Operational Config

Nacos Config will hold non-secret, environment-specific, operator-controlled
properties. Secrets stay in environment variables or a secret store:

- `JWT_HMAC_SECRET`
- database, Redis, Kafka, object-store, and mail credentials
- OSS access key and secret key
- XXL-Job access token
- Nacos credentials and auth tokens

Nacos may hold non-secret endpoints, service ids, windows, thresholds, TTLs, route
tables, and feature toggles where changing the value does not expose credentials.

### Decision 2: Environment Variables Still Own Bootstrap Wiring

These values remain environment variables because the application needs them before
or while loading config:

- `NACOS_SERVER_ADDR`
- `SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR`
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`
- `NACOS_NAMESPACE`
- `NACOS_CONFIG_GROUP`
- active Spring profiles
- database and broker bootstrap addresses required for process startup
- container ports, image tags, memory limits, and compose-only topology settings

Nacos Config may refine non-secret service endpoints after bootstrap, but it must
not be required to find itself.

### Decision 3: DataIds Are Explicit

Each backend deployable imports one shared dataId and one service dataId. Optional
profile-specific dataIds can be added later, but phase 1 keeps the shape simple:

```text
community-shared.yaml
community-gateway.yaml
community-app.yaml
community-oss.yaml
community-im-gateway.yaml
im-core.yaml
im-realtime.yaml
```

All dataIds use YAML content. The group defaults to `COMMUNITY`. Namespace is
environment-specific and supplied through `NACOS_NAMESPACE`; local compose can use
the public namespace or a documented local namespace id.

### Decision 4: Local Is Optional, Prod Is Required

Local and test profiles use optional imports so developers can still run a single
service from the IDE with packaged defaults:

```text
optional:nacos:community-shared.yaml
optional:nacos:<service>.yaml
```

Production-like profiles use required imports. Missing or unreadable config must
fail startup. `StartupValidation` should be updated so its Nacos guidance matches
the real config import contract.

### Decision 5: Dynamic Refresh Is Narrow

Nacos Config publication does not automatically mean safe runtime behavior. Phase 1
supports dynamic refresh only for these surfaces after tests prove the refresh path:

- gateway HTTP route table,
- gateway IM edge route target,
- gateway rate-limit policies,
- gateway traffic-policy rules,
- runtime observability thresholds and toggles.

All other imported values are startup-time config until an explicit refresh design
and tests are added. This includes auth token TTLs, outbox processing thresholds,
OSS object-store wiring, search cleanup, analytics ingest, and IM session settings.

### Decision 6: Discovery Metadata Carries Runtime Routing Facts

Nacos Discovery metadata should be expanded beyond the existing IM worker fields.
Every backend service registers stable low-cardinality metadata:

```text
version
deployment.environment
zone
traffic.group
protocol
capabilities
management.port
```

`im-realtime-worker` continues to register:

```text
role=ws-worker
workerId
wsPath
wsPort
```

Gateway and IM gateway can later consume metadata for canary or zone-aware routing,
but this spec only requires registration and documentation unless a route rule
explicitly needs it.

## Target Architecture

```text
Nacos server
  -> Config dataIds
      -> Spring Config Data import
          -> backend Environment / ConfigurationProperties
  -> Discovery registry
      -> Spring Cloud LoadBalancer / Gateway lb:// routes
      -> IM WorkerRegistry DiscoveryClient lookup

Environment / secret store
  -> bootstrap addresses, namespace, group, credentials, secrets

Local compose
  -> nacos-db-bootstrap creates schema
  -> nacos-config-bootstrap publishes baseline non-secret dataIds
  -> backend services import config and register discovery metadata
```

## Configuration Taxonomy

### Move To Nacos Config In Phase 1

Gateway:

- `gateway.cors.allowed-origins`
- `gateway.im-edge.service-id`
- `gateway.im-edge.session-path`
- `gateway.im-edge.ws-path`
- `gateway.http.routes`
- `gateway.http.rate-limit`
- `gateway.http.traffic-policy`

IM:

- `im.gateway.public-ws-url`
- `im.gateway.session.ticket-ttl`
- `im.gateway.worker.service-id`
- `im.gateway.worker.*-metadata-key`
- `im.gateway.ws.path`
- `im.gateway.ws.first-frame-timeout-ms`
- `im.gateway.ws.max-inbound-chars`
- `im.clients.community-service-id`
- `im.clients.im-core-service-id`
- `im.clients.membership-snapshot-service-id`
- `im.clients.policy-snapshot-service-id`
- `im.clients.snapshot-timeout-ms`
- `im.ws.path`
- `im.ws.outbound-buffer-size`
- `im.room-flush-interval-ms`

Security and edge policy:

- `security.jwt.issuer`
- `gateway.origin-guard.allowed-origins`
- `gateway.origin-guard.fail-open-when-allowlist-empty`
- `security.jwt.access-token-ttl-seconds`
- `security.jwt.refresh-token-ttl-seconds`
- `security.jwt.refresh-cookie-name`
- `security.jwt.refresh-cookie-path`
- `security.jwt.refresh-cookie-same-site`
- `security.jwt.refresh-cookie-secure`
- auth captcha, registration, password-reset, and login-rate-limit windows and
  thresholds.

Observability:

- `community.observability.runtime-logging.*`
- HTTP access log enablement, slow thresholds, and exclude paths.
- low-cardinality deployment labels that are not already resource attributes.

Work processing:

- `events.outbox.enabled`
- `events.outbox.batch-size`
- `events.outbox.processing-lease`
- `events.outbox.max-retries`
- `events.outbox.base-backoff`
- `events.outbox.max-backoff`
- `events.outbox.worker-fixed-delay-ms`
- `content.score.refresh.enabled`
- `content.score.refresh.batch-size`
- `search.idempotency.*`
- `analytics.max-days-range`
- `analytics.ingest.*`

OSS non-secret routing:

- `oss.public-base-url`
- `oss.client.base-url`
- `oss.object-store.mode`
- `oss.object-store.endpoint`
- `oss.object-store.bucket`
- `oss.object-store.region`
- `oss.object-store.path-style`
- `oss.object-store.local-root`

### Keep In Environment Variables

- `NACOS_SERVER_ADDR` and Nacos namespace/group settings.
- active profiles and service-specific JVM options.
- DB, Redis, Kafka, Elasticsearch, MailHog, Garage, and XXL bootstrap endpoints
  where local compose needs deterministic wiring.
- compose-only ports, memory limits, image tags, and healthcheck settings.
- `OTEL_EXPORTER_OTLP_ENDPOINT` and collector bootstrap endpoint.

### Keep In Secret Store Or `.env`

- `JWT_HMAC_SECRET`
- DB passwords
- Redis passwords when enabled
- Kafka credentials when enabled
- OSS access key and secret key
- mail username and password
- `XXL_JOB_ACCESS_TOKEN`
- Nacos auth credentials

## Component Design

### Maven Dependencies

Add `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config` to each backend
deployable that imports Nacos Config:

- `community-gateway`
- `community-app`
- `community-oss`
- `community-im-gateway`
- `im-core`
- `im-realtime`

Keep the existing `spring-cloud-starter-alibaba-nacos-discovery` dependencies.

If Spring Cloud Alibaba `2023.0.1.0` lacks required Config Data behavior for the
chosen syntax, the implementation plan must include a controlled Spring Cloud
Alibaba patch-level upgrade and compatibility test pass.

### Application Config Import

Each service gets a small bootstrap-safe config block in packaged `application.yml`:

```yaml
spring:
  config:
    import:
      - ${NACOS_CONFIG_IMPORT_SHARED:optional:nacos:community-shared.yaml?group=${NACOS_CONFIG_GROUP:COMMUNITY}}
      - ${NACOS_CONFIG_IMPORT_SERVICE:optional:nacos:<service>.yaml?group=${NACOS_CONFIG_GROUP:COMMUNITY}}
  cloud:
    nacos:
      config:
        server-addr: ${SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR:${NACOS_SERVER_ADDR:localhost:8848}}
        namespace: ${NACOS_NAMESPACE:}
        group: ${NACOS_CONFIG_GROUP:COMMUNITY}
        file-extension: yaml
      discovery:
        server-addr: ${SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR:${NACOS_SERVER_ADDR:localhost:8848}}
```

Implementation must verify the exact optional import syntax supported by the
project's Spring Cloud Alibaba version before rollout. The final code should avoid
duplicating long import strings across modules if a small shared convention is
possible without hiding boot-time behavior.

### Required Imports In Production

Compose and production-like profiles set imports to required:

```text
NACOS_CONFIG_IMPORT_SHARED=nacos:community-shared.yaml?group=COMMUNITY
NACOS_CONFIG_IMPORT_SERVICE=nacos:community-gateway.yaml?group=COMMUNITY
```

Local IDE and tests can keep optional imports or disable Nacos Config explicitly.

### Nacos Config Seed

Add a local seed path:

```text
deploy/nacos/config/
  community-shared.yaml
  community-gateway.yaml
  community-app.yaml
  community-oss.yaml
  community-im-gateway.yaml
  im-core.yaml
  im-realtime.yaml
deploy/nacos/seed-configs.sh
```

Add a `nacos-config-bootstrap` compose service that:

1. waits for Nacos health,
2. publishes or updates local baseline dataIds through the Nacos OpenAPI,
3. fails fast on publish errors,
4. is idempotent,
5. runs before backend services.

The seed files contain only non-secret values. Any placeholder secret in seed files
is a bug.

### Configuration Classes And Refresh

Properties already represented by `@ConfigurationProperties` should remain that way.
The implementation should prefer Spring Environment binding over direct Nacos SDK
usage.

Refreshable gateway surfaces need explicit runtime behavior:

- route changes publish a `RefreshRoutesEvent` or otherwise rebuild the route
  locator safely,
- rate-limit and traffic-policy changes update the in-memory policy source without
  requiring process restart,
- invalid refreshed config is rejected or fails closed,
- refresh events are logged with dataId, group, changed property prefix, outcome,
  and trace-free process context.

Non-refreshable surfaces may still come from Nacos at startup.

### Discovery Metadata

Each service adds metadata under `spring.cloud.nacos.discovery.metadata`:

```yaml
version: ${SERVICE_VERSION:@project.version@}
deployment.environment: ${DEPLOYMENT_ENVIRONMENT:local}
zone: ${SERVICE_ZONE:local}
traffic.group: ${SERVICE_TRAFFIC_GROUP:baseline}
protocol: http
capabilities: ${SERVICE_CAPABILITIES:}
management.port: ${MANAGEMENT_SERVER_PORT:${SERVER_PORT}}
```

`im-realtime` keeps and extends the existing worker metadata:

```yaml
role: ws-worker
wsPath: ${IM_WS_PATH:/internal/ws/im}
wsPort: ${SERVER_PORT:18081}
workerId: ${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}
```

Metadata values must be low-cardinality and must not include secrets, host-specific
tokens, user ids, raw URLs with credentials, or arbitrary business state.

### Startup Validation

Update startup validation guidance so it distinguishes:

- missing required Nacos Config import,
- missing environment bootstrap values,
- missing secrets.

The current generic "check Nacos config" hint should become specific and only appear
when Nacos Config is actually enabled or required.

## Failure Handling

- In local optional mode, missing Nacos Config logs a clear warning and uses packaged
  defaults.
- In production required mode, missing Nacos Config fails startup before serving
  traffic.
- Invalid gateway route or policy refresh fails closed and keeps the last valid
  config.
- Invalid CORS or origin allowlist config fails closed in production-sensitive
  paths.
- Nacos Discovery outage after startup should not erase the last in-process gateway
  route config, but service instance lookup follows Spring Cloud LoadBalancer and
  existing IM gateway fail-closed behavior.
- Seed failure in compose stops backend service startup so local failures are
  obvious.

## Security

- Do not put secrets in seed files, Nacos Config dataIds, docs examples, or tests.
- Local compose can keep `NACOS_AUTH_ENABLE=false`, but production documentation must
  require Nacos auth, transport protection where available, and restricted namespace
  permissions.
- Nacos config history should be treated as sensitive operational metadata even
  without secrets.
- Runtime logs for config refresh must log dataId and prefix, not full config values.
- CORS and origin guard defaults remain fail-closed for production.

## Testing Strategy

### Unit And Context Tests

- Verify all services can start with Nacos discovery and config disabled for tests.
- Verify optional imports do not require a live Nacos server in local/test profiles.
- Add binding tests for moved `@ConfigurationProperties` groups where behavior is
  security-sensitive or route-sensitive.
- Add gateway tests for refreshed route, rate-limit, and traffic-policy config.
- Add tests that invalid refreshed gateway config keeps the last valid state.
- Add IM worker metadata tests for `workerId`, `wsPath`, `wsPort`, and new common
  metadata.

### Integration And Compose Tests

- Extend deploy topology tests to assert `nacos-config-bootstrap` exists and backend
  services depend on it.
- Add a local smoke script that starts `single`, queries Nacos config dataIds, and
  verifies service instance metadata.
- Verify `community-gateway` routes `/api`, `/api/oss`, `/files`, `/api/im`, and
  `/ws/im` through service discovery after config import.
- Verify `community-im-gateway` can discover `im-realtime-worker` metadata from
  Nacos after config seed.
- Verify production-like required imports fail startup when the service dataId is
  absent.

### Suggested Verification Commands

```bash
cd backend
mvn test -pl :community-gateway,:community-im-gateway,:im-realtime -am
```

```bash
./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example
./deploy/tests/topology_single_cluster.sh
```

```bash
./deploy/deployment.sh up --topology single
curl -fsS "http://localhost:18848/nacos/v1/cs/configs?dataId=community-gateway.yaml&group=COMMUNITY"
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

## Documentation Updates

Implementation must update:

- `docs/handbook/local-development.md`
- `docs/handbook/operations.md`
- `docs/handbook/system-design.md`
- `docs/handbook/architecture.md` if deployable or shared-infrastructure boundaries
  change
- `deploy/README.md`
- `deploy/.env.single.example`
- `deploy/.env.cluster.example`
- backend module READMEs if they document service-local configuration

The docs must describe Nacos as both discovery and config center after this work,
while still stating that secrets stay outside Nacos Config.

## Rollout Plan

1. Add Nacos Config dependencies and optional import blocks with no behavioral
   config moved yet.
2. Add local Nacos config seed files that mirror current packaged defaults for the
   selected non-secret properties.
3. Wire `nacos-config-bootstrap` into single and cluster compose.
4. Move gateway config groups first and verify gateway startup and routing.
5. Add gateway refresh support for routes, rate limits, and traffic policy.
6. Move IM gateway and IM realtime non-secret routing and timeout config.
7. Move observability thresholds and low-risk feature toggles.
8. Move work-processing thresholds as startup-time config only.
9. Add discovery metadata to all backend services.
10. Make production-like config imports required and update startup validation.
11. Update handbook, deploy docs, and local verification scripts.

## Acceptance Criteria

- Every backend deployable imports `community-shared.yaml` and its own service
  dataId from Nacos Config in non-test runtime profiles.
- Tests can still run without a live Nacos server.
- Local `single` and `cluster` topologies seed Nacos Config before backend services
  start.
- Gateway routes, rate limits, traffic policy, CORS, IM gateway settings,
  observability thresholds, and selected work-processing thresholds are present in
  Nacos seed dataIds.
- Secrets are absent from Nacos seed files and config examples.
- Production-like startup fails when required Nacos Config dataIds are missing.
- Dynamic refresh is implemented only for the approved gateway and observability
  surfaces and has tests.
- All backend services register the standard discovery metadata.
- `im-realtime-worker` discovery still exposes valid `workerId`, `wsPath`, and
  `wsPort` metadata.
- Documentation explains the config taxonomy, local seed flow, verification commands,
  and secret-handling rules.

## Implementation Defaults

Phase 1 uses these defaults unless implementation proves they are incompatible with
the selected Spring Cloud Alibaba version:

- Local compose uses the public Nacos namespace to avoid namespace bootstrap
  coupling. Production environments provide `NACOS_NAMESPACE`.
- Gateway refresh uses Spring Cloud refresh infrastructure when available. Direct
  Nacos listener callbacks are allowed only if the refresh infrastructure cannot
  provide deterministic route rebuild behavior.
- Environment separation uses namespace and group. Profile-specific dataIds are not
  introduced in phase 1.
- `community-shared.yaml` contains only cross-service observability defaults,
  `security.jwt.issuer`, Nacos/discovery metadata defaults, and other truly shared
  non-secret defaults. Service-specific auth, route, rate-limit, and processing
  settings stay in the service dataId.
