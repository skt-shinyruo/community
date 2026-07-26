# Playwright Single 管理后台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 保留普通用户访问 /admin/users 的精确 403，同时让管理员看到用户搜索、争议列表空状态/主体内容和开发检查台主体，不再把页面 shell 当成成功。

**Architecture:** 前端路由负责角色拦截，管理员页面通过现有 adminUserService、marketService 和 HomeView API 读取内容；后端 AdminUserController/AdminMarketController 只进入各自 ApplicationService，角色授权由现有 Security/MarketSecurityRules 保持。

**Tech Stack:** Vue 3、Vitest、Spring MVC MockMvc、Spring Security test、JUnit 5、Playwright Test。

## Global Constraints

- 普通用户访问 /admin/users 必须保持前端 #/403 和 无权限，不能通过前端隐藏菜单或放宽 ROLE_ADMIN。
- 管理员成功页面必须区分 loading、API error、成功空列表和成功有数据；失败不能被转为空列表。
- 后端 Controller 不得直接调用 repository/mapper，也不得为方便 E2E 删除角色校验。
- Playwright admin 流程使用真实 accounts.admin/accounts.aaa，API 错误由共享审计 fixture 失败。

---

### Task 1: Add Red Tests for the Admin Empty and Body States

**Files:**
- Modify: frontend/src/views/AdminMarketDisputesView.test.js
- Modify: frontend/src/views/UserManagementView.test.js
- Read: frontend/src/views/AdminMarketDisputesView.vue
- Read: frontend/src/views/UserManagementView.vue

- [ ] **Step 1: Add the empty dispute case**

Add a test that sets:

~~~js
listAdminMarketDisputes.mockResolvedValue({
  data: [],
  traceId: ''
})
~~~

Mount the view, flush promises, assert loading is gone, assert wrapper.text() contains 暂无待处理争议, and assert wrapper.findAll('.market-admin-row') has length 0.

- [ ] **Step 2: Add the populated body case**

Retain or extend the existing populated fixture and assert all of these user-visible labels:

~~~js
expect(wrapper.text()).toContain('实物商品')
expect(wrapper.text()).toContain('待管理员裁定')
expect(wrapper.text()).toContain('需要管理员裁定')
expect(wrapper.text()).toContain('收到的商品与描述不符')
expect(wrapper.text()).toContain('不同意退款')
~~~

Trigger the refund button and retain the exact call:

~~~js
expect(adminResolveMarketDispute).toHaveBeenCalledWith(
  disputeId,
  'refund',
  { note: 'refund' }
)
~~~

- [ ] **Step 3: Lock the user-management body contract**

In UserManagementView.test.js, after the successful search, assert 搜索用户, 用户信息, alice, and USER are visible. Keep the existing exact request payload with userId, username, and email fields.

- [ ] **Step 4: Run the red frontend tests**

~~~bash
cd frontend
npm test -- src/views/AdminMarketDisputesView.test.js src/views/UserManagementView.test.js
~~~

Expected before implementation: the empty dispute test fails because the template renders an empty market-admin-list without an empty-state message.

### Task 2: Implement Explicit Admin Success and Empty States

**Files:**
- Modify: frontend/src/views/AdminMarketDisputesView.vue
- Read: frontend/src/views/marketState.js
- Read: frontend/src/components/ui/UiState.vue

- [ ] **Step 1: Separate error, loading, empty, and populated branches**

Keep the existing error and loading branches. Replace the unconditional successful list container with this behavior:

~~~vue
<UiState v-else-if="state.disputes.length === 0">
  暂无待处理争议
  <template #description>当前没有等待管理员裁定的市场争议。</template>
</UiState>
<div v-else class="market-admin-list">
  <article v-for="item in state.disputes" :key="item.disputeId" class="market-admin-row">
    <div>
      <strong>争议 #{{ item.disputeId }}</strong>
      <p>{{ item.goodsTypeLabel }} · {{ item.reason }} · {{ item.statusLabel }}</p>
      <p>{{ item.nextActionLabel }}</p>
      <p v-if="item.buyerNote || item.sellerNote">买家说明：{{ item.buyerNote || '未填写' }} · 卖家说明：{{ item.sellerNote || '未填写' }}</p>
    </div>
    <div class="market-inline-actions">
      <UiButton variant="secondary" :disabled="submittingId === item.disputeId" @click="resolve(item.disputeId, 'refund')">
        退回买家
      </UiButton>
      <UiButton :disabled="submittingId === item.disputeId" @click="resolve(item.disputeId, 'release')">
        放款卖家
      </UiButton>
    </div>
  </article>
</div>
~~~

Keep reload assigning Array.isArray(data) ? data : [] only after the service has returned a successful Result body. The catch branch must set error and leave the request failure visible.

- [ ] **Step 2: Preserve resolution behavior**

Keep adminResolveMarketDispute(disputeId, action, payload), the exact resolve-refund/resolve-release endpoint mapping in marketService.js, and reload after a successful action. The empty-state branch must not render resolution buttons.

- [ ] **Step 3: Run the focused tests**

~~~bash
cd frontend
npm test -- src/views/AdminMarketDisputesView.test.js src/views/UserManagementView.test.js src/api/services/marketService.test.js
~~~

Expected: both populated and empty dispute states pass, the role-search body passes, and a failed API mock still renders the error state.

### Task 3: Verify Backend Admin Authorization and Empty Responses

**Files:**
- Modify: backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java
- Modify: backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketDisputeApplicationService.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java

- [ ] **Step 1: Add the empty list API assertion**

In AdminMarketControllerTest, mock listOpenDisputes() to return List.of(), call GET /api/admin/market/disputes with ROLE_ADMIN, assert HTTP 200 and JSON data array size 0.

~~~java
when(marketDisputeService.listOpenDisputes()).thenReturn(List.of());

mockMvc.perform(get("/api/admin/market/disputes")
                .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString()))
                        .authorities(() -> "ROLE_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
~~~

- [ ] **Step 2: Keep the forbidden path exact**

Retain a non-admin request to an admin endpoint and assert status().isForbidden(). Add the same precise assertion for GET /api/admin/market/disputes if it is not already covered; do not accept 401/200 or a redirect as an equivalent result.

- [ ] **Step 3: Verify user search response semantics**

In AdminUserControllerUnitTest, retain the successful AdminUserResult mapping and add a null service result test that asserts Result.getCode() is 0 and Result.getData() is null. This is the successful “not found” body used by the frontend, not an HTTP error.

- [ ] **Step 4: Run the backend contract and architecture tests**

~~~bash
cd backend
mvn -pl :community-app -Dtest=AdminMarketControllerTest,AdminUserControllerUnitTest test
mvn test -pl :community-app -Dtest='*ArchTest'
~~~

Expected: admin list/empty/search responses are 200, non-admin authorization is 403, and Controller dependencies remain ApplicationService-only.

### Task 4: Add Real Admin Playwright Body Assertions

**Files:**
- Modify: tests/playwright-single/tests/06-admin.spec.ts
- Read: tests/playwright-single/fixtures/accounts.ts
- Read: frontend/src/api/services/adminUserService.js
- Read: frontend/src/api/services/marketService.js

- [ ] **Step 1: Keep the ordinary-user role guard**

Use accounts.aaa, navigate to /admin/users, and assert:

~~~ts
await expect(page).toHaveURL(/#\/403/)
await expect(page.getByText('无权限').first()).toBeVisible()
~~~

This test is a successful assertion of a legal authorization result. It must not be implemented as an expected generic API 403.

- [ ] **Step 2: Assert the administrator user-management body**

As accounts.admin, navigate to /admin/users, assert 搜索用户 and the input name user-search-username, fill accounts.aaa.username, click 搜索, and assert 用户信息 plus the returned aaa username. Wait for the exact GET /api/users/admin/search response and assert status 200.

~~~ts
const searchResponse = page.waitForResponse((response) => {
  const url = new URL(response.url())
  return response.request().method() === 'GET'
    && url.pathname === '/api/users/admin/search'
})
await page.getByRole('button', { name: '搜索' }).click()
expect((await searchResponse).status()).toBe(200)
await expect(page.getByText(accounts.aaa.username).first()).toBeVisible()
~~~

- [ ] **Step 3: Assert dispute and development body states**

Navigate to /admin/market/disputes and assert either 暂无待处理争议 or a visible .market-admin-row. Navigate to /dev and assert 开发检查台 and 本地联调数据. Keep existing checks for 治理后台, 统计, 用户管理, 钱包后台, and 争议裁定.

- [ ] **Step 4: Run the focused admin E2E**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/06-admin.spec.ts
~~~

Expected: ordinary user reaches exact #/403, administrator sees real page body content, and shared audit reports no unapproved API error.

### Task 5: Verify Admin Documentation and Error Semantics

**Files:**
- Modify: tests/playwright-single/README.md
- Modify: docs/handbook/testing.md
- Verify: tests/playwright-single/tests/99-known-issues.spec.ts is absent

- [ ] **Step 1: Document both legal outcomes**

Describe /admin/users as two separate checks: ordinary user gets exact 403/无权限; administrator gets a successful search body. Describe market disputes as a successful list or explicit empty state, and /dev as a successful development-check body.

- [ ] **Step 2: Scan stale shell-only semantics**

~~~bash
rg -n "shell|只渲染|搜索用户.*Count\(0\)|争议 #.*Count\(0\)|开发检查台.*Count\(0\)|admin.*503|admin.*403" tests/playwright-single frontend/src docs/handbook
~~~

Expected: no known-issue assertion remains. Negative security assertions may mention exact 403, but no test may describe 403 as a generally expected application failure.

- [ ] **Step 3: Run the complete focused matrix**

~~~bash
cd frontend
npm test -- src/views/AdminMarketDisputesView.test.js src/views/UserManagementView.test.js
cd ../backend
mvn -pl :community-app -Dtest=AdminMarketControllerTest,AdminUserControllerUnitTest test
cd ..
npm --prefix tests/playwright-single run test:regression -- tests/06-admin.spec.ts
~~~

Expected: UI, backend, and deployed admin checks all exit zero.
