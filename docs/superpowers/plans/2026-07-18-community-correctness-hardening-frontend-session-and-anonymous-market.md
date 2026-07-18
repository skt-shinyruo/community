# Frontend Session And Anonymous Market Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让普通 HTTP、IM HTTP 和启动恢复共用一个 generation-aware refresh coordinator，并让匿名用户可靠浏览实物/虚拟市场详情而不请求私有地址。

**Architecture:** Pinia auth store 维护单调 `tokenGeneration`；每个认证请求记录发送时 generation。模块级 `refreshCoordinator` 持有唯一 in-flight Promise，通过无拦截器的 auth transport 刷新 token并加载一次 profile；stale 401 直接使用更新 token，旧 refresh 失败不能清除更新状态。市场详情将 public listing 与 private address 拆成独立状态机，用 listing/auth generation sequence 丢弃过期响应。

**Tech Stack:** Vue 3、Pinia、Axios、Vue Router、Vitest、Java 21、Spring MVC、MockMvc、Maven、Vite。

---

## Auth store generation

### 写 store RED 测试

**Files:**

- Modify: `frontend/src/stores/auth.js`
- Create: `frontend/src/stores/auth.test.js`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/views/LoginView.test.js`
- Modify: `frontend/src/views/RegisterView.vue`
- Modify: `frontend/src/views/RegisterView.test.js`

- [ ] 初始 `tokenGeneration=0`；安装不同 token 单调增加；重复安装同 token 不倒退。
- [ ] `setAccessToken('new')` 保留已有 `me`，避免 refresh 窗口把 UI 清空。
- [ ] 新增原子 action：

  ```js
  installSession({ accessToken, me })
  ```

  一次更新 token/profile，更新 session hint；profile 为显式 `undefined` 时保留旧 profile，显式 null 时清空。
- [ ] `clear()` 清 token/profile并推进 generation，使旧异步任务能检测状态已变化；重复 clear 不得降低 generation。
- [ ] 运行 RED：

  ```bash
  cd frontend
  npm test -- --run src/stores/auth.test.js src/views/LoginView.test.js src/views/RegisterView.test.js
  ```

  预期：当前 store 无 generation，且 `setAccessToken` 清空 profile，断言失败。

### 实现单调认证状态

- [ ] state 增加 `tokenGeneration: 0`；只通过 actions 修改 token。
- [ ] token 值变化或 clear 有效状态时 generation `+= 1`；同 token 的 profile 更新不推进 generation。generation 只表示当前页面进程内的认证先后关系，不持久化，也不引入会破坏 Pinia/devtools 序列化的 `BigInt`。
- [ ] `setAccessToken(nonEmptyToken)` 只更新 token/session hint，不清 `me`；空 token 必须委托 `clear()`，不能留下“无 token 但有旧 profile”的状态。
- [ ] `installSession` 在一个 action 中更新 token和 profile；coordinator 成功路径只调用一次。
- [ ] 显式 login/register 不能调用“保留 profile”的 `setAccessToken`。它们在拿到新 token 后调用 `installSession({ accessToken: token, me: null })` 清掉上一账号资料，再用新 token加载当前 profile；对应 view test 固定账号切换期间不会展示旧 `me`。
- [ ] 运行 GREEN，预期 store/login/register 测试通过。
- [ ] 提交 store：

  ```bash
  git add frontend/src/stores/auth.js frontend/src/stores/auth.test.js \
          frontend/src/views/LoginView.vue frontend/src/views/LoginView.test.js \
          frontend/src/views/RegisterView.vue frontend/src/views/RegisterView.test.js
  git commit -m "feat(frontend): track access token generations"
  ```

## 唯一 refresh coordinator

### 写 coordinator RED 测试

**Files:**

- Create: `frontend/src/auth/refreshTransport.js`
- Create: `frontend/src/auth/refreshCoordinator.js`
- Create: `frontend/src/auth/refreshCoordinator.test.js`
- Modify: `frontend/src/api/http.test.js`
- Modify: `frontend/src/api/imCoreHttp.test.js`
- Modify: `frontend/src/auth/session.test.js`

- [ ] `refreshCoordinator.test.js` 用 deferred Promise 同时发起 HTTP、IM、bootstrap 三个 join，断言 transport refresh 调用一次、profile loader 调用一次、三方收到同 token。
- [ ] stale 401：请求记录 generation 1；store 已是 generation 2/new token，调用恢复时不得发 refresh，只返回 new token。
- [ ] refresh 开始于 generation 1，期间登录/另一个成功任务安装 generation 2；旧 refresh reject 后断言不 clear generation 2，并让等待者使用当前 token继续一次 retry。
- [ ] 同样覆盖旧 refresh 成功晚到：generation 已变化且当前有新 token 时，丢弃旧 refresh token/profile，不调用 `installSession`；generation 已变化但当前 token 为空（期间 logout）时，返回 session-changed failure，不能用旧 refresh 响应重新登录。
- [ ] 真正终态失败且 generation 未变化时 clear 一次，所有 joiner reject 同一 failure；不能每个 caller 重复 clear/redirect。
- [ ] refresh 成功先通过新 token加载一次 profile，再 `installSession`；加载期间旧 profile 保持可见，完成后 token/profile 一起替换。
- [ ] profile 暂时失败时安装有效新 token并保留旧 profile；bootstrap 无旧 profile 时返回 `{ state: 'error' }`，不能把有效 token清掉。
- [ ] 每个 retry 最多一次；第二个 401 原样 reject，不形成 refresh loop。
- [ ] 运行 RED：

  ```bash
  cd frontend
  npm test -- --run \
    src/auth/refreshCoordinator.test.js \
    src/api/http.test.js \
    src/api/imCoreHttp.test.js \
    src/auth/session.test.js
  ```

  预期：coordinator 不存在；当前 HTTP/IM/bootstrap 分别发 refresh，join/旧失败断言失败。

### 实现无循环依赖的 transport/coordinator

- [ ] `refreshTransport.js` 用独立 `axios.create`，base URL 使用 `resolveApiBaseUrl()`，`withCredentials=true`；不得 import `http.js`，否则形成 interceptor 循环。
- [ ] transport 暴露两个小函数：

  ```js
  requestRefreshToken()
  requestCurrentUser(accessToken)
  ```

  两者解析现有 `Result` envelope；profile 请求显式写 `Authorization: Bearer <new token>`。
- [ ] `refreshCoordinator.js` 模块内只保留一个 `inFlightRefresh`；公开：

  ```js
  refreshSession({ auth, expectedGeneration, requireProfile = true })
  recoverUnauthorized({ auth, requestGeneration })
  ```

- [ ] `recoverUnauthorized` 先比较 generation：已变化且有 token直接返回；否则 join/start `refreshSession`。
- [ ] `recoverUnauthorized` 发现 generation 已变化但当前 token 为空时直接抛 session-changed failure；不能发起 refresh，也不能把空 token写回原请求。
- [ ] refresh response 返回后、profile response 返回后、最终 `installSession` 前都重新比较 start generation。generation 已变化且有当前 token时丢弃旧响应并返回当前 token；已变化且无 token时丢弃旧响应并抛 session-changed failure。
- [ ] refresh catch 同样再次比较 generation；generation 已变化且有 token时返回当前 token，已变化且无 token时只抛错不 clear；只有仍等于 start generation 时调用 `auth.clear()` 并抛终态 refresh failure。
- [ ] refresh 成功但 profile 的 `5xx`/网络请求失败时 `installSession({ accessToken: token, me: undefined })`；返回标志 `profileLoaded=false`，供 bootstrap 决定 ready/error。新 token 的 profile 请求返回 `401` 时视为终态认证失败，并仅在 generation 仍匹配时 clear。
- [ ] coordinator 不负责 router hard redirect；调用者可在确认终态失败后统一导航，避免 IM/HTTP 各自跳转。
- [ ] 运行 coordinator tests，预期全部通过。
- [ ] 提交 coordinator：

  ```bash
  git add frontend/src/auth/refreshTransport.js \
          frontend/src/auth/refreshCoordinator.js \
          frontend/src/auth/refreshCoordinator.test.js
  git commit -m "feat(frontend): coordinate refresh across clients"
  ```

## 接入 HTTP、IM 和 bootstrap

### 普通 HTTP 记录 generation

**Files:**

- Modify: `frontend/src/api/http.js`
- Modify: `frontend/src/api/http.test.js`

- [ ] request interceptor 在附加 Bearer token时同时写内部字段 `_authTokenGeneration=auth.tokenGeneration`；该字段只存在 config，不发送 Header。
- [ ] 删除模块内 `refreshingPromise` 和直接 `/api/auth/refresh` 调用。
- [ ] 401 且非 auth endpoint、未 `_retry` 时调用 `recoverUnauthorized`，取得 token后显式覆盖 `original.headers.Authorization` 再 `http(original)`。
- [ ] 终态 failure 可保留一次登录导航，但只有 generation 未变化且 store 已 anonymous 时执行；stale failure 不跳转。
- [ ] 测试两个并发 401 只 refresh/profile一次；旧 request 401 在新 token已安装时零 refresh；第二次 401 不循环。
- [ ] 运行：

  ```bash
  cd frontend
  npm test -- --run src/api/http.test.js src/auth/refreshCoordinator.test.js
  ```

  预期：全部通过。

### IM HTTP 使用同一 coordinator

**Files:**

- Modify: `frontend/src/api/imCoreHttp.js`
- Modify: `frontend/src/api/imCoreHttp.test.js`

- [ ] IM request 同样记录 `_authTokenGeneration`；response interceptor 删除对 `http.post('/api/auth/refresh')` 的依赖和 `http` import。
- [ ] IM 401 调用同一个 `recoverUnauthorized`，更新 IM original Authorization 后重试一次。
- [ ] 测试一个 community HTTP 401 和一个 IM 401 并发，只产生一次 auth refresh/profile；两条原请求都用新 token成功。
- [ ] stale failure 不 clear新 token；终态 failure clear一次。
- [ ] 运行：

  ```bash
  cd frontend
  npm test -- --run src/api/imCoreHttp.test.js src/api/http.test.js
  ```

  预期：全部通过。

### Bootstrap 复用 coordinator

**Files:**

- Modify: `frontend/src/auth/session.js`
- Modify: `frontend/src/auth/session.test.js`
- Modify: `frontend/src/api/services/authService.js`
- Modify: `frontend/src/api/services/authService.test.js`

- [ ] `ensureSessionReady` 无 access token但有 session hint 时调用 `refreshSession`，不直接调用 auth service `refresh()`。
- [ ] coordinator 已加载 profile时 bootstrap 不再调用 `me()`。token 已存在但 profile 为空时用无拦截器的 `requestCurrentUser(token)` 请求一次；若该请求 `401`，调用 `recoverUnauthorized`，由 coordinator 完成 refresh + 一次新 token profile load，不能再重放原 `/me` 形成第二次 profile 请求。
- [ ] HTTP 恢复和 bootstrap 同时开始时共享 in-flight Promise；`pendingSessionPromise` 可保留只用于整段 bootstrap，但不能创建第二个 refresh。
- [ ] 删除 `authService.refresh` export 及其测试调用；当前生产代码只有 `session.js` 使用它，改由 coordinator/transport 后不保留可绕过协调器的第二入口。`authService.me` 可保留给非恢复场景，但 bootstrap 不通过带 401 interceptor 的 `me()` 重放链路加载 profile。
- [ ] 运行 session/auth service 测试，预期 restore/anonymous/profile error/join 全部通过。
- [ ] 静态扫描：

  ```bash
  rg -n "post\('/api/auth/refresh|/api/auth/refresh" frontend/src \
    --glob '!auth/refreshTransport.js' --glob '!**/*.test.js'
  ```

  预期：无匹配；唯一实际 refresh HTTP 位于 `refreshTransport.js`。
- [ ] 提交三个入口接入：

  ```bash
  git add frontend/src/api/http.js frontend/src/api/http.test.js \
          frontend/src/api/imCoreHttp.js frontend/src/api/imCoreHttp.test.js \
          frontend/src/auth/session.js frontend/src/auth/session.test.js \
          frontend/src/api/services/authService.js frontend/src/api/services/authService.test.js
  git commit -m "fix(frontend): share generation aware session refresh"
  ```

## 后端 refresh Cookie 竞态

### 写失败响应 RED 测试

**Files:**

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`

- [ ] `loginApplicationService.refresh` 抛 `RefreshFailure(clearRefreshCookie=true)` 时，断言 response 不含任何清除 refresh Cookie 的 `Set-Cookie`。
- [ ] 抛 `REFRESH_TOKEN_INVALID`/`USER_DISABLED` BusinessException 时同样无清 Cookie Header。
- [ ] refresh 成功仍写 rotated Cookie；logout 仍写 `Max-Age=0` 清 Cookie；显式 revoke endpoint 如存在也保留清除行为。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=AuthControllerUnitTest test
  ```

  预期：当前 catch 分支会调用 `clearRefreshCookie()`，失败响应断言失败。

### 删除失败分支的 Cookie 清除

- [ ] `AuthController.refresh` 只处理成功结果并添加 rotated Cookie；删除 `catch (RefreshFailure)`、`catch (BusinessException)` 和 `shouldClearRefreshCookie`。
- [ ] 让异常交给全局错误处理器；Controller 不改变 Cookie。不要修改 refresh rotation domain logic。
- [ ] 清理不再使用的 `RefreshFailure`/`AuthErrorCode` import；logout 行为不变。
- [ ] 运行 GREEN，预期 unit test 全部通过。
- [ ] 提交后端 Cookie 修复：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
          backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java
  git commit -m "fix(auth): preserve rotated cookie on stale refresh failure"
  ```

## 匿名市场 listing/address 双状态

### 写页面 RED 测试

**Files:**

- Modify: `frontend/src/views/MarketViews.test.js`
- Modify: `frontend/src/views/MarketDetailView.vue`

- [ ] 匿名实物 listing：detail 成功渲染，`listMarketAddresses` 调用 0 次；页面不显示 address error。
- [ ] 匿名虚拟 listing：同样不请求地址，可查看详情。
- [ ] 已认证实物：分别覆盖地址成功、空列表、401、503；四种情况下 listing 始终保留，地址错误只显示在地址区域。
- [ ] 已认证虚拟：不请求地址。
- [ ] listing A/address A 请求未完成时切换到 listing B或 token generation变化；A 的晚响应不得覆盖 B 地址/错误/selected id。
- [ ] 匿名点击“安全下单”不调用 `createMarketOrder`，而是：

  ```js
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  ```

- [ ] 已认证实物无地址阻止下单并显示 address-specific error；已认证虚拟不依赖地址。
- [ ] 运行 RED：

  ```bash
  cd frontend
  npm test -- --run src/views/MarketViews.test.js
  ```

  预期：当前实物 detail 无条件请求地址，匿名/地址失败进入 page error，断言失败。

### 分离公开 listing loader

- [ ] `MarketDetailView.vue` 注入 `useAuthStore()`，增加独立状态：

  ```js
  const addressLoading = ref(false)
  const addressError = ref('')
  let listingSequence = 0
  let addressSequence = 0
  ```

- [ ] `loadDetail` 只调用 `getMarketListingDetail`，成功立即写 listing/page state；用 local sequence 判断 response 仍属于当前 route。
- [ ] listing 成功后调用 `loadAddressesFor({ listingId, goodsType, authGeneration })`；该函数首先判断 `auth.authed && goodsType==='PHYSICAL'`，否则清地址状态并返回。
- [ ] address response 写入前同时比较 address sequence、listing id 和 `auth.tokenGeneration`；不匹配时丢弃并记录低基数 `stale_address_response` signal。
- [ ] address catch 只写 `addressError`，不写 page-level `error`；401 可由 coordinator恢复，终态失败仍不隐藏 listing。
- [ ] watch key 使用 `[route.params.listingId, auth.tokenGeneration]`，listing id变化重载公开 detail；仅 generation变化时可复用当前 listing并重载/清空私有地址，避免不必要公开请求。
- [ ] 地址区域为 loading/empty/error/select 提供独立 UI；不要把说明性功能文案放进页面级错误。
- [ ] submit 首先检查 auth；匿名导航登录并保留 `route.fullPath`。认证后才验证 listing/address和调用 order API。
- [ ] 运行 GREEN，预期 market tests 全部通过。
- [ ] 提交市场页面：

  ```bash
  git add frontend/src/views/MarketDetailView.vue frontend/src/views/MarketViews.test.js
  git commit -m "fix(frontend): keep market details public without address data"
  ```

## 综合验证与单 bundle 发布

- [ ] 运行前端聚焦测试：

  ```bash
  cd frontend
  npm test -- --run \
    src/stores/auth.test.js \
    src/auth/refreshCoordinator.test.js \
    src/auth/session.test.js \
    src/api/http.test.js \
    src/api/imCoreHttp.test.js \
    src/api/services/authService.test.js \
    src/views/LoginView.test.js \
    src/views/RegisterView.test.js \
    src/views/MarketViews.test.js
  ```

  预期：所有并发、stale generation、匿名/认证市场矩阵通过。

- [ ] 运行全量 frontend：

  ```bash
  cd frontend
  npm test
  npm run build
  ```

  预期：Vitest 全绿，Vite build 退出码为 `0`。

- [ ] 运行后端 auth 和架构：

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=AuthControllerUnitTest test
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：Controller 只处理 HTTP Cookie适配和调用 `LoginApplicationService`，没有 application command/result 暴露 HTTP 类型。

- [ ] 静态扫描：

  ```bash
  rg -n 'refreshingPromise|http\.post\(.*/api/auth/refresh|setAccessToken\([^)]*\).*me = null' frontend/src
  ```

  预期：旧独立 refresh 状态和 profile 清空模式无匹配；唯一 in-flight 变量位于 coordinator并使用新名称。

- [ ] 浏览器人工并发验收：同时打开普通 API、IM API和 session bootstrap，让旧 token过期，确认 Network 中只有一个 refresh和一个 me；再快速登录/刷新，确认旧失败不清新状态。
- [ ] 匿名打开实物/虚拟详情，确认 Network 无 address 请求；点击下单进入 login并带 redirect，登录完成返回原 listing。
- [ ] refresh coordinator 和匿名 market 必须在同一个 frontend build/bundle 发布，避免页面依赖旧 auth store generation。
- [ ] `git diff --check`，预期无 whitespace 错误。
