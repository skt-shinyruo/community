# Playwright Single IM 链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 修复 im-core 对 YAML CORS 列表的错误绑定，让前端 cursor 会话接口返回 200，并让 /messages 在空会话和有会话时都显示稳定状态。

**Architecture:** CORS 只属于 im-core 的 security adapter 配置；会话 HTTP 仍进入 ConversationApplicationService，再通过 domain repository 读取数据。Gateway 只负责保留 /api/im/conversations/page 路由和 Authorization header，不把 JWT 校验放宽到匿名访问。

**Tech Stack:** Spring Boot ConfigurationProperties、Spring Security OAuth2 Resource Server、MockMvc、Spring Cloud Gateway、JUnit 5、Vue 3/Vitest、Playwright Test。

## Global Constraints

- im.cors.allowed-origins 必须以 List<String> 绑定；不得用 String.split 解析 YAML list。
- 保留 .anyRequest().authenticated()、内部 realtime projection scope 和 Gateway 到 im-core 的 JWT 边界。
- 空会话是 HTTP 200、稳定的 data.items=[]、hasMore=false 响应，不是 403 或 5xx。
- 前端不吞掉请求错误；Playwright 错误审计必须看到所有未预期 4xx/5xx。
- 后端 Controller 继续只调用 ConversationApplicationService，不能直接调用 mapper/repository。

---

### Task 1: Add the Red CORS Binding and Preflight Tests

**Files:**
- Modify: backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/config/NacosImCoreBindingTest.java
- Modify: backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/ImCoreApiControllerTest.java
- Create: backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/security/ImCoreCorsSecurityTest.java
- Read: deploy/nacos/config/im-core.yaml
- Read: backend/community-im/im-core/src/main/resources/application.yml

- [ ] **Step 1: Assert the seed list property**

Extend NacosImCoreBindingTest so it checks both the indexed property and a Binder result:

~~~java
Binder binder = Binder.get(environment);
ImCoreCorsProperties properties = binder.bind(
        "im.cors",
        Bindable.of(ImCoreCorsProperties.class)
).orElseThrow();

assertThat(properties.getAllowedOrigins())
        .contains("http://localhost:12881", "http://127.0.0.1:12881");
~~~

The existing assertion for allowed-origins[2] must remain. Run:

~~~bash
cd backend
mvn -pl :im-core -Dtest=NacosImCoreBindingTest test
~~~

Expected before the implementation: the new properties class is unavailable or the list is not bound.

- [ ] **Step 2: Add the red preflight assertion**

Add to ImCoreApiControllerTest a preflight request with the configured browser origin:

~~~java
mockMvc.perform(options("/api/im/conversations/page")
                .header("Origin", "http://localhost:12881")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:12881"));
~~~

Configure the test context with im.cors.allowed-origins[0]=http://localhost:12881 and retain the existing authenticated GET tests. Run:

~~~bash
cd backend
mvn -pl :im-core -Dtest=ImCoreApiControllerTest test
~~~

Expected before the fix: the preflight is rejected as 403 Invalid CORS request when the YAML list is supplied.

- [ ] **Step 3: Add a focused security configuration test**

Create ImCoreCorsSecurityTest that constructs the configuration with two exact origins, obtains CorsConfigurationSource for an OPTIONS request, and asserts:

~~~java
assertThat(configuration.getAllowedOrigins())
        .containsExactly("http://localhost:12881", "http://127.0.0.1:12881");
assertThat(configuration.getAllowedMethods())
        .contains("GET", "POST", "OPTIONS");
assertThat(configuration.getAllowedHeaders())
        .contains("Authorization", "Content-Type", "Idempotency-Key");
~~~

Also assert that an origin not in the list returns no Access-Control-Allow-Origin value. This prevents replacing a list-binding bug with wildcard CORS.

### Task 2: Implement Typed im-core CORS Configuration

**Files:**
- Create: backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security/ImCoreCorsProperties.java
- Modify: backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security/ImCoreSecurityConfig.java
- Read: backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/ImCoreApplication.java
- Read: backend/community-im/im-core/src/main/resources/application.yml

- [ ] **Step 1: Add the typed properties object**

Create a mutable Spring Boot properties class:

~~~java
@ConfigurationProperties(prefix = "im.cors")
public class ImCoreCorsProperties {
    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
~~~

Keep the class in the security package. It must contain no HTTP DTO or domain dependency.

- [ ] **Step 2: Register and consume the properties**

Annotate ImCoreSecurityConfig with EnableConfigurationProperties(ImCoreCorsProperties.class), inject ImCoreCorsProperties into corsConfigurationSource, and set:

~~~java
config.setAllowedOrigins(properties.getAllowedOrigins());
config.setAllowCredentials(true);
config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
config.setExposedHeaders(List.of("traceparent"));
~~~

Delete the Value String parameter, Arrays import, and split-based conversion. Leave securityMatcher, CSRF/session policy, JWT resource server, internal scope, and anyRequest().authenticated() unchanged.

- [ ] **Step 3: Support both Nacos lists and environment comma values**

Keep deploy/nacos/config/im-core.yaml as an indexed YAML list. Verify the existing application property IM_CORS_ALLOWED_ORIGINS remains usable for local overrides; Spring Boot Binder must accept either indexed YAML values or a comma-separated environment value without changing the public origin allowlist.

Run:

~~~bash
cd backend
mvn -pl :im-core -Dtest=NacosImCoreBindingTest,ImCoreCorsSecurityTest,ImCoreApiControllerTest test
~~~

Expected: the typed list is bound, the configured frontend origin passes preflight, and an unlisted origin remains rejected.

### Task 3: Verify Gateway Route, JWT Forwarding, and Cursor Response

**Files:**
- Modify: backend/community-gateway/src/test/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteIntegrationTest.java
- Read: backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java
- Read: deploy/nacos/config/community-gateway.yaml
- Read: backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/ConversationController.java
- Read: backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/ConversationApplicationService.java

- [ ] **Step 1: Add the page route contract**

Extend the existing route capture test with:

~~~java
webTestClient.get()
        .uri("/api/im/conversations/page?cursor=&size=20")
        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
        .exchange()
        .expectStatus().isOk();

assertThat(capture.path()).isEqualTo("/api/im/conversations/page");
assertThat(capture.authorization()).isEqualTo("Bearer test-token");
~~~

Use the test's existing mock upstream and route property registration; do not add a direct im-core URL or bypass the Gateway.

- [ ] **Step 2: Run the route and controller checks**

~~~bash
cd backend
mvn -pl :community-gateway -am -Dtest=GatewayImEdgeRouteIntegrationTest,HttpRoutingIntegrationTest test
mvn -pl :im-core -Dtest=ImCoreApiControllerTest,ConversationApplicationServiceCursorPaginationTest test
~~~

Expected: Gateway retains the path and Authorization header, and the controller maps Page.items, nextCursor, and hasMore through Result.data.

- [ ] **Step 3: Confirm no security weakening**

~~~bash
rg -n "permitAll|anyRequest|oauth2ResourceServer|SCOPE_im\.realtime\.internal|allowed-origins" backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security deploy/nacos/config/im-core.yaml
~~~

Expected: OPTIONS is the only permitted method without authentication; API requests remain authenticated and internal projection remains scope-protected.

### Task 4: Add Frontend Empty-State Coverage

**Files:**
- Modify: frontend/src/views/ConversationsView.test.js
- Modify: frontend/src/api/services/imCoreChatService.test.js
- Read: frontend/src/views/ConversationsView.vue
- Read: frontend/src/api/services/imCoreChatService.js

- [ ] **Step 1: Add the empty page test**

Mock listImConversationPage to resolve:

~~~js
{
  items: [],
  nextCursor: null,
  hasMore: false
}
~~~

Mount ConversationsView, flush promises, assert listImConversationPage was called with { cursor: '', size: 20 }, assert the text 暂无会话, and assert no load-more-conversations control exists.

- [ ] **Step 2: Retain the nonempty and failure semantics**

Keep the existing tests for cursor merge/deduplication, stale responses, and preserving the last successful rows after refresh failure. In imCoreChatService.test.js keep the Result-body unwrap assertion and reject raw array/invalid body responses.

- [ ] **Step 3: Run focused frontend checks**

~~~bash
cd frontend
npm test -- src/api/services/imCoreChatService.test.js src/views/ConversationsView.test.js src/views/ConversationDetailView.test.js
~~~

Expected: empty data is a successful empty state; a rejected request remains a visible error and is not converted to empty data.

### Task 5: Add the Deployed IM Playwright Test

**Files:**
- Create: tests/playwright-single/tests/07-im.spec.ts
- Read: tests/playwright-single/fixtures/test-data.ts
- Read: tests/playwright-single/fixtures/accounts.ts
- Read: tests/playwright-single/fixtures/auth.ts
- Read: tests/playwright-single/fixtures/helpers.ts

- [ ] **Step 1: Add the endpoint-first success assertion**

Use the shared test fixture and write:

~~~ts
test('authenticated user can load the cursor conversation inbox @regression', async ({ page }) => {
  await loginViaUi(page, accounts.bbb)
  const responsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === 'GET'
      && url.pathname === '/api/im/conversations/page'
  })
  await gotoHash(page, '/messages')
  const response = await responsePromise
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(Array.isArray(body.data?.items)).toBe(true)
  expect(typeof body.data?.hasMore).toBe('boolean')
  const items = body.data.items
  if (items.length === 0) {
    await expect(page.getByText('暂无会话')).toBeVisible()
  } else {
    await expect(page.locator('a[href^="/messages/"]').first()).toBeVisible()
  }
})
~~~

The branch handles both seed states: an empty `items` array must render `暂无会话`, while a nonempty array must render a conversation link under `/messages/`. Do not require a pre-existing conversation.

- [ ] **Step 2: Assert the rendered error-free state**

After the response promise resolves, assert either page.getByText('暂无会话') or page.locator('a[href^="/messages/"]').first() is visible. Assert page.locator('.conversations-empty.error') has count zero and let the shared audit fail any API 4xx/5xx, pageerror, or console.error.

- [ ] **Step 3: Run the IM E2E**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/07-im.spec.ts
~~~

Expected: GET /api/im/conversations/page returns 200 from the frontend origin, no CORS 403 is emitted, and the page displays either its explicit empty state or a conversation.

### Task 6: Verify the IM Slice and Documentation Contract

**Files:**
- Verify: all files above
- Modify: tests/playwright-single/README.md
- Modify: docs/handbook/testing.md

- [ ] **Step 1: Run backend and frontend checks together**

~~~bash
cd backend
mvn -pl :im-core -Dtest=NacosImCoreBindingTest,ImCoreCorsSecurityTest,ImCoreApiControllerTest,ConversationApplicationServiceCursorPaginationTest test
mvn -pl :community-gateway -am -Dtest=GatewayImEdgeRouteIntegrationTest test
cd ../frontend
npm test -- src/api/services/imCoreChatService.test.js src/views/ConversationsView.test.js
~~~

- [ ] **Step 2: Scan for the old failure semantic**

~~~bash
rg -n "conversations.*403|Invalid CORS request|toBe\(403\)|test:known|known-issues" tests/playwright-single frontend/src backend/community-im backend/community-gateway --glob '!**/node_modules/**'
~~~

Expected: no old known-issue test or expected IM 403 remains. Security tests may mention rejection as a negative assertion only when the path, origin, and status are exact.

- [ ] **Step 3: Run the final deployed check**

~~~bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:regression -- tests/07-im.spec.ts
~~~

Expected: the bounded health probe and IM Playwright test both exit zero.
