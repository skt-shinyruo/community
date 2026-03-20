# Project Gateway Unified Edge Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new `community-gateway` service that becomes the unified project edge for `community-app`, `im-core`, and `im-realtime`, including HTTP routing, gateway-level policies, WebSocket first-frame auth, stable `userId`/shard routing, and internal worker bridging.

**Architecture:** Add a new `backend/community-gateway` Spring Boot WebFlux module that owns the external HTTP and WebSocket edge. Keep `community-app` and `im-core` as business owners, and keep `im-realtime` as the internal IM session worker. Roll out in phases: HTTP edge first, then external WebSocket termination, then shard routing and worker bridge, then cutover and hardening.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring WebFlux, Reactor Netty WebSocket client/server, Spring Security, Micrometer, Maven multi-module build, Docker Compose local stack, Vue frontend.

> **Human approval note:** Do not create git commits during execution unless the human explicitly asks for a commit.

---

## Parallel Execution Map

These waves are the intended execution order once implementation begins. Inside a wave, tasks with disjoint write-sets can be assigned to subagents in parallel.

### Wave 0: Foundation

- Task 1: Create `community-gateway` module skeleton and local runtime wiring

This wave is serial and should stay with the main thread because it establishes module layout, build wiring, and the initial file structure.

### Wave 1: HTTP Edge

- Task 2: Implement HTTP upstream routing
- Task 3: Implement HTTP edge policies and observability baseline

These tasks can run in parallel once Task 1 lands, because they can work on disjoint files under `gateway/http` and `gateway/edge`.

### Wave 2: WebSocket Edge

- Task 4: Add WebSocket edge skeleton and transparent proxy baseline
- Task 5: Add first-frame auth state machine and JWT validation

Task 4 should start first. Task 5 can start once the external WebSocket handler contract is fixed.

### Wave 3: Routing And Bridge

- Task 6: Implement shard router and worker registry
- Task 7: Implement internal worker bridge and `im-realtime` worker-mode cutover

Task 6 and Task 7 can proceed in parallel only after the bridge interfaces and worker descriptor shape are agreed. The main thread should own the shared contracts.

### Wave 4: Product Cutover

- Task 8: Switch frontend and local stack to gateway-first traffic
- Task 9: Add end-to-end IM flow coverage, rollout controls, and docs

These can run in parallel after the gateway path is functionally complete.

---

## Task 1: Create `community-gateway` Module Skeleton And Local Runtime Wiring

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/community-gateway/pom.xml`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/CommunityGatewayApplication.java`
- Create: `backend/community-gateway/src/main/resources/application.yml`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/CommunityGatewayApplicationTest.java`
- Modify: `deploy/docker-compose.yml`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DEPLOYMENT.md`

- [ ] **Step 1: Add the new Maven module and empty application skeleton**

Create the new module directory and wire it into `backend/pom.xml`.

The new module should use:
- `spring-boot-starter-webflux`
- `spring-boot-starter-security`
- `spring-boot-starter-actuator`
- `spring-boot-starter-validation`
- `micrometer-registry-prometheus`
- `spring-boot-starter-test`
- `reactor-test`

- [ ] **Step 2: Write the failing context-load test**

Create `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/CommunityGatewayApplicationTest.java` with a minimal `@SpringBootTest` context-load test.

Expected initial failure reason:
- module not yet compiled correctly, or
- application wiring incomplete

- [ ] **Step 3: Run the module test to confirm the module is not wired yet**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=CommunityGatewayApplicationTest test
```

Expected:
- FAIL due to missing module sources or incomplete application wiring

- [ ] **Step 4: Implement the minimal bootable gateway app**

Add:
- `CommunityGatewayApplication`
- minimal `application.yml`
- health actuator exposure

Use port `8080` internally. In local compose, expose the gateway on host port `12880` so it can coexist with the current `community-app` port layout during rollout.

- [ ] **Step 5: Add local compose wiring**

Update `deploy/docker-compose.yml`:
- add `community-gateway` service
- route it to `community-app`, `im-core`, and `im-realtime` through internal Docker DNS
- keep existing direct ports for `community-app`, `im-core`, and `im-realtime` during transition

- [ ] **Step 6: Re-run the module test**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=CommunityGatewayApplicationTest test
```

Expected:
- PASS

- [ ] **Step 7: Update architecture and deployment docs with the transitional port layout**

Document that:
- `community-gateway` is the new unified edge
- local rollout initially exposes it on `12880`
- direct service ports remain temporarily available during cutover

---

## Task 2: Implement HTTP Upstream Routing

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/UpstreamRouteProperties.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/ProxyHttpHandler.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/HttpProxyRouter.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing HTTP routing integration test**

Create `HttpRoutingIntegrationTest` that boots the gateway against two in-process stub upstreams and verifies:
- `/api/posts` proxies to the `community-app` upstream
- `/api/im/conversations` proxies to the `im-core` upstream
- unknown paths still fail closed

- [ ] **Step 2: Run the routing test and confirm it fails**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=HttpRoutingIntegrationTest test
```

Expected:
- FAIL because no routing layer exists yet

- [ ] **Step 3: Implement route properties and proxy handler**

Define explicit route ownership:
- `community-app` owns general `/api/**`, `/files/**`, `/actuator/**` only if explicitly allowed
- `im-core` owns `/api/im/**`
- internal paths such as `/internal/**` must not be exposed publicly by the gateway

The proxy handler must forward:
- method
- path
- query string
- headers needed for auth and tracing
- body

- [ ] **Step 4: Implement the functional HTTP router**

Add `HttpProxyRouter` so that route ownership is explicit and testable. Do not use a catch-all “proxy everything” handler.

- [ ] **Step 5: Re-run the routing test**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=HttpRoutingIntegrationTest test
```

Expected:
- PASS

---

## Task 3: Implement HTTP Edge Policies And Observability Baseline

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TraceIdWebFilter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/AccessLogWebFilter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitProperties.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/InMemoryRateLimiter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitWebFilter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TrafficPolicyProperties.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/TrafficPolicyEvaluator.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/RateLimitWebFilterTest.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/TrafficPolicyEvaluatorTest.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing rate-limit and traffic-policy tests**

Cover:
- rate limit decisions for a path + principal/IP key
- disabled policy paths pass through
- traffic policy evaluator returns stable decisions for allow/default/canary buckets

- [ ] **Step 2: Run the focused policy tests to confirm failure**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=RateLimitWebFilterTest,TrafficPolicyEvaluatorTest test
```

Expected:
- FAIL because the filters and evaluators do not exist yet

- [ ] **Step 3: Implement trace and access-log filters**

Requirements:
- inject or preserve `X-Trace-Id`
- add timing and upstream outcome fields
- do not duplicate business audit semantics from upstreams

- [ ] **Step 4: Implement the initial in-memory HTTP rate limiter**

Keep first cut intentionally simple:
- per-path policy
- principal/IP key
- explicit fail-closed/fail-open toggle in config

- [ ] **Step 5: Implement the traffic policy evaluator scaffold**

This is the minimal framework for future canary routing. First cut only needs:
- disabled
- stable
- percentage-based canary

Do not implement dynamic control-plane storage yet.

- [ ] **Step 6: Re-run the focused tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=RateLimitWebFilterTest,TrafficPolicyEvaluatorTest test
```

Expected:
- PASS

---

## Task 4: Add WebSocket Edge Skeleton And Transparent Proxy Baseline

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/GatewayWebSocketConfig.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalImWebSocketHandler.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/WsProxyProperties.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsTransparentProxyIntegrationTest.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing transparent WebSocket proxy integration test**

The test should:
- start a stub internal worker WebSocket server
- connect to the gateway `/ws/im`
- verify that frames are proxied bidirectionally

This test should not include first-frame auth parsing yet. It is only establishing the bridgeable WebSocket edge.

- [ ] **Step 2: Run the transparent proxy test and confirm it fails**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=WsTransparentProxyIntegrationTest test
```

Expected:
- FAIL because no WebSocket edge exists yet

- [ ] **Step 3: Implement external WS mapping and minimal bidirectional proxy**

Add the external `/ws/im` mapping and a minimal Reactor Netty bridge that can connect to one configured internal worker URL.

- [ ] **Step 4: Re-run the transparent proxy test**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=WsTransparentProxyIntegrationTest test
```

Expected:
- PASS

---

## Task 5: Add First-Frame Auth State Machine And JWT Validation

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/security/GatewayJwtDecoderConfig.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/AuthFrameParser.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalWsSessionState.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsAuthStateMachineIntegrationTest.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalImWebSocketHandler.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing auth state machine integration test**

Cover:
- first frame must be `auth`
- invalid JWT returns `auth_error` and closes
- valid JWT extracts `userId`
- non-auth frames before auth are rejected

- [ ] **Step 2: Run the auth state machine test and confirm it fails**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=WsAuthStateMachineIntegrationTest test
```

Expected:
- FAIL because the gateway does not yet parse or validate the first frame

- [ ] **Step 3: Implement JWT decoder and first-frame parser**

Requirements:
- reuse the same JWT secret source pattern as IM services
- parse the JSON auth frame safely
- capture `userId` and preserve the original auth frame for later forwarding to the worker in the first bridge version

- [ ] **Step 4: Implement the external session state machine**

States:
- connected_unauthed
- auth_validating
- authed_ready
- closing

Do not combine these states with shard routing logic yet.

- [ ] **Step 5: Re-run the auth state machine test**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=WsAuthStateMachineIntegrationTest test
```

Expected:
- PASS

---

## Task 6: Implement Shard Router And Worker Registry

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/WorkerDescriptor.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/WorkerRegistryProperties.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/WorkerRegistry.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ShardRouter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouter.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouterTest.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/shard/WorkerRegistryTest.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing shard router and worker registry tests**

Cover:
- stable `userId -> worker` decisions when worker set is unchanged
- minimal remapping when a worker is added/removed
- unhealthy workers are excluded
- empty registry fails fast

- [ ] **Step 2: Run the shard tests and confirm they fail**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=ConsistentHashShardRouterTest,WorkerRegistryTest test
```

Expected:
- FAIL because no registry or router exists yet

- [ ] **Step 3: Implement worker descriptor and static registry**

First cut:
- config-driven worker list
- health flag
- internal WebSocket URL
- version / pool labels for later gray routing

- [ ] **Step 4: Implement consistent-hash routing**

Use `userId` as the routing key. The router must not depend on IP or cookie affinity.

- [ ] **Step 5: Re-run the shard tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=ConsistentHashShardRouterTest,WorkerRegistryTest test
```

Expected:
- PASS

---

## Task 7: Implement Internal Worker Bridge And `im-realtime` Worker-Mode Cutover

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/InternalWorkerBridge.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/InternalWorkerBridgeFactory.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/InternalWorkerBridgeIntegrationTest.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayPrivateFlowCompatibilityTest.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayRoomFlowCompatibilityTest.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalImWebSocketHandler.java`
- Modify: `deploy/docker-compose.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`

- [ ] **Step 1: Write the failing bridge integration tests**

Cover:
- gateway picks a worker using `userId`
- gateway opens an internal WebSocket to that worker
- gateway forwards the original `auth` frame first, then subsequent frames
- worker responses flow back to the external client

- [ ] **Step 2: Run the bridge tests and confirm they fail**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=InternalWorkerBridgeIntegrationTest,GatewayPrivateFlowCompatibilityTest,GatewayRoomFlowCompatibilityTest test
```

Expected:
- FAIL because shard routing is not connected to bridge creation yet

- [ ] **Step 3: Implement the first bridge version by forwarding the original auth frame**

Do not invent a new internal IM protocol in the first cut. Keep the worker contract compatible by:
- selecting a worker after gateway JWT validation
- opening an internal WebSocket to that worker
- forwarding the original auth frame as the first upstream message
- then proxying all subsequent frames

- [ ] **Step 4: Add worker-mode rollout toggles**

In `im-realtime`, add explicit configuration that distinguishes:
- direct public edge mode
- internal worker mode behind gateway

First cut can keep the same handler path and message protocol, but the config must make the intended deployment role explicit.

- [ ] **Step 5: Re-run the bridge compatibility tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=InternalWorkerBridgeIntegrationTest,GatewayPrivateFlowCompatibilityTest,GatewayRoomFlowCompatibilityTest test
```

Expected:
- PASS

---

## Task 8: Switch Frontend And Local Stack To Gateway-First Traffic

**Files:**
- Modify: `frontend/src/api/http.js`
- Modify: `frontend/src/api/imCoreHttp.js`
- Modify: `frontend/src/im/imRealtimeClient.js`
- Modify: `deploy/docker-compose.yml`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/business-logic/im-private-message-flow.md`
- Modify: `docs/business-logic/im-room-message-flow.md`

- [ ] **Step 1: Write the failing frontend gateway-resolution tests or smoke checks**

If the frontend test harness is absent, add a lightweight smoke script or document the exact manual verification matrix. At minimum, verify resolution rules for:
- main HTTP API base URL
- IM HTTP base URL
- IM WebSocket URL

- [ ] **Step 2: Update frontend URL resolution to prefer the project gateway**

Local default behavior should become:
- API -> `http://localhost:12880`
- IM HTTP -> `http://localhost:12880`
- IM WebSocket -> `ws://localhost:12880/ws/im`

Keep env overrides for non-local environments.

- [ ] **Step 3: Update compose to expose gateway-first traffic**

Expose:
- `community-gateway` on `12880`

Keep direct service ports available temporarily for rollback and debugging.

- [ ] **Step 4: Run local smoke verification**

Run:

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build community-gateway community-app im-core im-realtime frontend
curl -i http://localhost:12880/actuator/health
```

Expected:
- gateway health returns success
- frontend can authenticate, open IM WebSocket via gateway, and reach HTTP APIs via gateway

- [ ] **Step 5: Update the business-logic docs to show gateway-first paths**

Adjust both IM flow docs so they describe:
- external client -> `community-gateway`
- internal worker selection / forwarding

---

## Task 9: Add End-To-End Validation, Rollout Controls, And Gateway Docs

**Files:**
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/e2e/GatewayEndToEndSmokeTest.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RolloutModeProperties.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RolloutModeResolver.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `docs/SECURITY.md`
- Modify: `docs/LOAD_TESTING.md`
- Modify: `docs/README.md`
- Modify: `deploy/docker-compose.yml`

- [ ] **Step 1: Write the failing rollout-mode and smoke tests**

Cover rollout modes:
- HTTP transparent proxy only
- WS transparent proxy only
- WS first-frame auth without shard routing
- full shard routing mode

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=GatewayEndToEndSmokeTest test
```

Expected:
- FAIL because rollout controls do not exist yet

- [ ] **Step 3: Implement rollout mode properties and resolver**

Add explicit config flags so operators can degrade safely:
- HTTP transparent mode
- WS transparent mode
- WS auth-only mode
- full WS shard-routing mode

- [ ] **Step 4: Update security, load-testing, and docs index**

Document:
- gateway as the new unified edge
- direct service ports only for internal/debug use during transition
- IM correctness still depends on backfill from `im-core`

- [ ] **Step 5: Re-run the gateway module test suite**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am test
```

Expected:
- PASS

- [ ] **Step 6: Run focused existing IM tests to catch regressions**

Run:

```bash
mvn -f backend/pom.xml -pl :im-realtime -am -Dtest=ImRealtimeWebSocketIntegrationTest test
mvn -f backend/pom.xml -pl :im-core -am -Dtest=PrivateMessageServiceTest,RoomMessageServiceTest,ImCoreApiControllerTest test
```

Expected:
- PASS

---

## Execution Notes For Subagent-Driven Development

Use fresh workers with disjoint write sets.

### Recommended worker ownership

- Worker A:
  - `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/**`
  - `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/**`

- Worker B:
  - `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/**`
  - `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/**`

- Worker C:
  - `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/**`
  - `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/**`

- Worker D:
  - `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/**`
  - `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/shard/**`

- Worker E:
  - `frontend/src/api/http.js`
  - `frontend/src/api/imCoreHttp.js`
  - `frontend/src/im/imRealtimeClient.js`
  - IM business-logic docs

- Main thread only:
  - `backend/pom.xml`
  - `backend/community-gateway/pom.xml`
  - `deploy/docker-compose.yml`
  - gateway app skeleton
  - cross-cutting WS bridge contracts
  - rollout flags

### Mandatory serial checkpoints

- After Task 1
- After Task 4
- After Task 5
- After Task 7
- Before Task 8 cutover

---

## Final Verification Checklist

- [ ] `community-gateway` module builds and passes its test suite
- [ ] `community-app` routes still work via gateway
- [ ] `im-core` HTTP read routes still work via gateway
- [ ] private-message flow works via gateway
- [ ] room-message flow works via gateway
- [ ] gateway can reject invalid first-frame auth cleanly
- [ ] gateway can route by `userId` to a stable worker
- [ ] rollout flags can disable shard routing and revert to simpler modes
- [ ] docs reflect the new gateway-first external topology

---

Plan complete and saved to `docs/superpowers/plans/2026-03-19-community-gateway-unified-edge.md`. After review approval, this is ready for execution.
