# Fix Gateway Health And OpenAI Failures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the current unrelated red tests in `community-gateway` and `tools/mock-data-studio` without changing UUIDv7 migration behavior.

**Architecture:** Keep each fix scoped to its own subsystem. For gateway, isolate the security test from external Redis health so the test verifies public access instead of incidental component liveness. For mock-data-studio, align the OpenAI client wrapper with the SDK/test contract and preserve per-run budget semantics through the existing enhancer.

**Tech Stack:** Spring Boot WebFlux + Actuator, Maven Surefire, Node.js test runner, OpenAI Node SDK

---

### Task 1: Isolate gateway health security test from Redis liveness

**Files:**
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/security/GatewayDefaultSecurityIntegrationTest.java`

- [ ] **Step 1: Reproduce the failing test**

Run: `cd backend && mvn -pl community-gateway -am -Dtest=GatewayDefaultSecurityIntegrationTest#shouldKeepHealthEndpointPublic -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL with `503 SERVICE_UNAVAILABLE` because `/actuator/health` reports Redis `DOWN`.

- [ ] **Step 2: Add the minimal test-scoped isolation**

Add a dynamic property in `registerProperties`:

```java
registry.add("management.health.redis.enabled", () -> "false");
```

- [ ] **Step 3: Re-run the gateway test**

Run: `cd backend && mvn -pl community-gateway -am -Dtest=GatewayDefaultSecurityIntegrationTest#shouldKeepHealthEndpointPublic -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with `/actuator/health` reachable as `200 OK`.

### Task 2: Align mock-data-studio OpenAI client with current wrapper contract

**Files:**
- Modify: `tools/mock-data-studio/src/ai/openaiClient.mjs`
- Modify: `tools/mock-data-studio/src/batches/aiConfigRepository.mjs`

- [ ] **Step 1: Reproduce the failing OpenAI tests**

Run: `cd tools/mock-data-studio && node --test test/openai-client.test.mjs`
Expected: FAIL from undefined `chat.completions`, wrong missing-key error message, and enhancer budget fallback.

- [ ] **Step 2: Implement the minimal SDK-compatible request path**

Use `responses.create` and parse `output_text`, while preserving fallback parsing and provider metadata:

```js
const response = await openaiClient.responses.create({
  model,
  input: prompt
})
const responseText = String(response?.output_text ?? '')
```

For missing credentials, raise a provider-specific error:

```js
const error = new Error('OpenAI API key is not configured')
error.code = 'AI_NOT_CONFIGURED'
throw error
```

- [ ] **Step 3: Keep the DB connection probe consistent**

Update `aiConfigRepository.mjs` to use the same `responses.create` contract for its connectivity smoke test.

- [ ] **Step 4: Re-run the OpenAI tests**

Run: `cd tools/mock-data-studio && node --test test/openai-client.test.mjs`
Expected: PASS.

### Task 3: Verify both subsystems stay green

**Files:**
- Verify only

- [ ] **Step 1: Run the gateway focused suite**

Run: `cd backend && mvn -pl community-gateway -am -Dtest=GatewayDefaultSecurityIntegrationTest,GatewayRoomFlowCompatibilityTest,InternalWorkerBridgeIntegrationTest,WsTransparentProxyIntegrationTest,HttpRoutingIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS.

- [ ] **Step 2: Run the mock-data-studio focused suite**

Run: `cd tools/mock-data-studio && node --test test/openai-client.test.mjs test/batch-repository.test.mjs test/delete-batch-service.test.mjs test/job-runner.test.mjs test/ui-api-contract.test.mjs`
Expected: PASS.

- [ ] **Step 3: Report remaining unrelated reds if any**

If any test outside these touched subsystems still fails, report it explicitly with the exact command and failing case.
