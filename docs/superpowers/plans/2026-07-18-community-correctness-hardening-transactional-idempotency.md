# Transactional HTTP Idempotency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让每个新的前端业务调用拥有独立幂等键，并保证后端 claim、业务写入、响应序列化和 `SUCCESS` 状态在同一数据库事务中原子提交。

**Architecture:** Axios 仅在新 request config 没有 key 时生成 UUID；同一 config 的传输重试保留 Header。`IdempotencyGuard.executeRequired` 只接受 `TransactionalIdempotencyStore`，在调用 supplier 前验证当前事务与 JDBC datasource 绑定；JDBC 通过唯一键 claim，并用同 fingerprint 的 `P -> S` guarded update 完成。旧 `PROCESSING` 行由 Community `V009` 一次性转成 `INDETERMINATE`，永不因超时重新执行业务。

**Tech Stack:** Java 21、Spring JDBC/transactions、MySQL 8、H2 test fixture、Flyway、JUnit 5、Testcontainers、Axios、Vitest、Maven。

---

## 前置条件与状态机

本计划的 migration 编号依赖 outbox 子计划已经创建 Community `V008`。如果并行开发，合并时先合入 `V008`，再合入本计划的 `V009`。

```text
new key:        absent --insert P--> supplier --> serialize --> guarded P->S --> COMMIT
business error: absent --insert P--> supplier throws --------------------------> ROLLBACK
serialize err:  absent --insert P--> serialize throws --------------------------> ROLLBACK
replay:         S + same hash -----------------------------------------------> deserialize
conflict:       any + different hash ----------------------------------------> 409
legacy:         P --V009--> I -----------------------------------------------> 409 indeterminate
```

新实现不能提交 `P`：claim 和 `P -> S` 位于同一事务。数据库中任何已提交的 `P` 都属于旧 writer 或异常人工数据，不能自动过期后重跑。

## 前端每次调用的新 key

### 写 request config 语义的 RED 测试

**Files:**

- Modify: `frontend/src/api/http.test.js`
- Delete: `frontend/src/api/idempotencyKeyCache.js`
- Delete: `frontend/src/api/idempotencyKeyCache.test.js`
- Modify: `frontend/src/api/http.js`

- [ ] 在 `http.test.js` 对充值、提现、转账、市场下单、发帖和评论各选一条受保护 URL，连续发出两次 URL/body 完全相同的新调用，断言两个 `Idempotency-Key` 都存在且不同。
- [ ] 复用同一个 Axios config 模拟一次网络失败后的 `http(originalConfig)`，断言第二次发送保留第一次 Header。
- [ ] 调用方显式提供 Header 时断言 interceptor 不覆盖。
- [ ] 非受保护 POST、GET 和 auth refresh 不自动添加 key。
- [ ] 运行 RED：

  ```bash
  cd frontend
  npm test -- --run src/api/http.test.js
  ```

  预期：相同 URL/body 在 10 秒窗口内当前复用 cache key，“两个新调用不同”断言失败。

### 删除指纹缓存并保留 retry key

- [ ] 从 `http.js` 删除 `createIdempotencyKeyCache`、window/size 常量、`safeStringify`、`hashString` 和 fingerprint 逻辑。
- [ ] request interceptor 只执行：

  ```js
  if (shouldAttachIdempotencyKey(config)) {
    config.headers ||= {}
    if (!config.headers[IDEMPOTENCY_HEADER]) {
      config.headers[IDEMPOTENCY_HEADER] = generateIdempotencyKey()
    }
  }
  ```

- [ ] 保留 `crypto.randomUUID()` 优先和现有 fallback；fallback 每次调用也必须生成新值。
- [ ] 删除 cache 源文件和专属测试，运行 `rg -n 'idempotencyKeyCache|createIdempotencyKeyCache' frontend/src`，预期无匹配。
- [ ] 运行 GREEN：

  ```bash
  cd frontend
  npm test -- --run src/api/http.test.js
  ```

  预期：新调用/同 config 重试矩阵全部通过。

- [ ] 提交前端键语义：

  ```bash
  git add frontend/src/api/http.js frontend/src/api/http.test.js \
          frontend/src/api/idempotencyKeyCache.js frontend/src/api/idempotencyKeyCache.test.js
  git commit -m "fix(frontend): scope idempotency keys to one invocation"
  ```

## 事务存储契约

### 写事务绑定 RED 测试

**Files:**

- Create: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/TransactionalIdempotencyStore.java`
- Create: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyErrorCode.java`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyStore.java`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/JdbcIdempotencyStore.java`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/RedisIdempotencyStore.java`
- Create: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/TransactionalIdempotencyStoreTest.java`
- Modify: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/JdbcIdempotencyStoreTest.java`

- [ ] 固定事务契约：

  ```java
  public interface TransactionalIdempotencyStore extends IdempotencyStore {
      boolean isEnlistedInCurrentTransaction();
  }
  ```

- [ ] 把 `IdempotencyStore.saveSuccess(...)` 返回值改为 `boolean`，语义是且仅是同一 key、同一 request hash、`PROCESSING` 状态的 row 被更新时返回 true。
- [ ] 用 H2/JDBC + `DataSourceTransactionManager` 测试事务外 `false`、同 datasource 事务内 `true`、另一个 datasource 事务内 `false`。
- [ ] 测试 `saveSuccess` 对 wrong hash、`SUCCESS`、`INDETERMINATE`、missing row 返回 false且不覆盖数据。
- [ ] 测试过期 `P` 不能被 `tryAcquireProcessing` 更新、读取时不能删除；状态映射增加 `INDETERMINATE`。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-common-idempotency -am \
    -Dtest='TransactionalIdempotencyStoreTest,JdbcIdempotencyStoreTest' test
  ```

  预期：事务接口和状态不存在；当前 JDBC 会回收过期 `P` 并执行 upsert success，测试失败。

### 实现 datasource enlistment 与 guarded SQL

- [ ] `JdbcIdempotencyStore implements TransactionalIdempotencyStore`，保存 `JdbcTemplate.getDataSource()` 并实现：

  ```java
  return TransactionSynchronizationManager.isActualTransactionActive()
      && dataSource != null
      && TransactionSynchronizationManager.hasResource(dataSource);
  ```

- [ ] `tryAcquireProcessing` 的 duplicate 分支只能回收过期 `S`；删除 `(status=P and processing_expires_at < now)` 的重新 claim 条件。
- [ ] `get` 对 `P` 始终返回 `PROCESSING`，不删除；对 `I` 返回 `INDETERMINATE`；只有过期 `S` 可按既有 retention 语义清理。
- [ ] `saveSuccess` 改为单条 guarded update：

  ```sql
  update http_idempotency
  set status = 'S', response_json = ?, success_expires_at = ?,
      processing_expires_at = null, updated_at = now()
  where operation = ? and user_id = ? and idem_key = ?
    and request_hash = ? and status = 'P'
  ```

- [ ] 禁止 insert/upsert success；affected rows 不是 `1` 时返回 false。
- [ ] `RedisIdempotencyStore` 更新返回签名以保持编译，但它不实现 `TransactionalIdempotencyStore`，因此不能用于 `executeRequired`。
- [ ] 状态和错误码至少区分：`IDEMPOTENCY_IN_PROGRESS`、`IDEMPOTENCY_OUTCOME_INDETERMINATE`、`IDEMPOTENCY_STORE_UNAVAILABLE`；`ErrorKind.CONFLICT` 映射 `409`，store unavailable 映射 `503`。
- [ ] 运行 GREEN，预期两组 store 测试通过。
- [ ] 提交事务存储契约：

  ```bash
  git add backend/community-common/common-idempotency/src/main \
          backend/community-common/common-idempotency/src/test
  git commit -m "feat(idempotency): add transactional jdbc store contract"
  ```

## Guard 原子执行

### 写 Guard 和业务事务 RED 测试

**Files:**

- Modify: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardStoreFailureTest.java`
- Modify: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardFingerprintTest.java`
- Create: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardTransactionTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardSerializationFailureTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardTtlTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyBusinessAtomicitySpringTest.java`

- [ ] 测试非 transactional store、无活动事务、活动事务绑定其他 datasource 时 supplier 调用次数为 `0`，返回 `503`。
- [ ] 在事务中让 supplier 插入业务 probe row，然后让 JSON codec 抛出 `JsonCodecException`；断言业务 row 和幂等 row 都不存在。
- [ ] 让 `saveSuccess` 返回 false，断言事务回滚且不返回业务结果。
- [ ] 正常路径断言业务 row 与 status `S` 同时可见，response JSON 非空且不等于字符串 `null`。
- [ ] 相同 key/hash 的成功 replay 不再次调用 supplier；损坏/空/`null` response JSON 返回 `503`，不能返回 Java null。
- [ ] `INDETERMINATE` 返回稳定 `409`，message 明确“结果不确定，请查询业务状态”，不能建议换 key。
- [ ] fingerprint 不同仍使用调用方传入的 replay conflict code；同 hash 的 `PROCESSING` 返回 `IDEMPOTENCY_IN_PROGRESS`。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-common-idempotency,:community-app -am \
    -Dtest='IdempotencyGuard*Test,IdempotencyBusinessAtomicitySpringTest' test
  ```

  预期：当前 Guard 允许事务外执行、序列化失败保存 `"null"`、success 使用 `afterCommit`，多项断言失败。

### 把 claim、supplier、序列化、success 放入当前事务

**Files:**

- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/autoconfig/IdempotencyGuardAutoConfiguration.java`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/autoconfig/JdbcIdempotencyAutoConfiguration.java`

- [ ] `executeRequired` 在 key/hash 校验后、调用 store/supplier 前执行：

  ```java
  if (!(store instanceof TransactionalIdempotencyStore transactional)
          || !transactional.isEnlistedInCurrentTransaction()) {
      throw new BusinessException(IdempotencyErrorCode.STORE_UNAVAILABLE);
  }
  ```

- [ ] acquired 路径顺序固定为 supplier -> serialize -> validate JSON -> guarded `saveSuccess` -> return；删除 `TransactionSynchronization`、`afterCommit`、`safeDelete`、`safeExtendProcessing` 和 `pendingConfirmation()`。
- [ ] supplier/serialization/store 异常原样触发外层 Spring transaction rollback；Guard 不做补偿删除，因为删除也会扩大非原子窗口。
- [ ] `toJson` 对 null result、空 JSON、字符串 `null` 抛基础设施异常；`fromJson` 对这些值和反序列化失败转换成 `503`。
- [ ] `saveSuccess == false` 抛 `IllegalStateException` 或 `BusinessException(STORE_UNAVAILABLE)`，确保 `@Transactional` 默认回滚；不能继续返回 result。
- [ ] `JdbcIdempotencyAutoConfiguration` 返回类型改为 `TransactionalIdempotencyStore`；应用配置 `http.idempotency.store=REDIS` 时启动可保留通用 store，但调用 `executeRequired` 必须在 supplier 前失败。
- [ ] 运行 GREEN 命令，预期 Guard、serialization、atomicity 测试全部通过。
- [ ] 逐一确认以下 use case 的 `@Transactional` 包住 `executeRequired`：

  - `PostPublishingApplicationService.create(...)`
  - `CommentApplicationService.create(...)`
  - `WalletRechargeApplicationService.recharge(CreateRechargeCommand)`
  - `WalletWithdrawApplicationService.withdraw(CreateWithdrawCommand)`
  - `WalletTransferApplicationService.transfer(CreateTransferCommand)`
  - `MarketOrderApplicationService.createOrder(CreateMarketOrderCommand)`

  若某方法缺事务，只在该 same-domain ApplicationService 上补 `@Transactional`，不要把事务放到 Controller。
- [ ] 提交 Guard 原子执行：

  ```bash
  git add backend/community-common/common-idempotency \
          backend/community-app/src/main/java/com/nowcoder/community/content/application \
          backend/community-app/src/main/java/com/nowcoder/community/wallet/application \
          backend/community-app/src/main/java/com/nowcoder/community/market/application \
          backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency
  git commit -m "fix(idempotency): commit response with business transaction"
  ```

## Community V009 隔离旧结果

### 写 migration RED 测试

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V009__quarantine_indeterminate_http_idempotency.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`

- [ ] 在 `CommunityMigrationTest` 的 V008 状态库插入三行：legacy `P`、`S`、已存在非 processing 数据；迁移后断言只有 `P -> I`，`processing_expires_at` 为 null，request hash/key/response 和业务表不变。
- [ ] 空库 migration count 从 `8` 更新为 `9`；重复 migrate 仍执行 `0`；history success count 为 `9`。
- [ ] upgrade fixture 覆盖残留 `P`，证明 baseline 后执行 V002-V009 会隔离而不是删除。
- [ ] 在 H2 `schema.sql` 允许/表达 `I` 状态；不要修改 `V001__baseline.sql` 或 `community-schema-manifest.tsv`。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest=CommunityMigrationTest test
  ```

  预期：V009 不存在或 migration count/状态断言失败。

### 实现只向前隔离 migration

- [ ] V009 只执行可审计的状态转换：

  ```sql
  update http_idempotency
  set status = 'I', processing_expires_at = null, updated_at = current_timestamp
  where status = 'P';
  ```

- [ ] 不根据 TTL 猜测业务成功/失败，不删除 row，不写伪造 response JSON。
- [ ] 运行 migration GREEN：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest=CommunityMigrationTest test
  ```

  预期：空库、V001 upgrade、重复 migrate、状态保留断言全部通过。

- [ ] 提交 V009：

  ```bash
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V009__quarantine_indeterminate_http_idempotency.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql
  git commit -m "feat(migration): quarantine legacy idempotency processing rows"
  ```

## 综合验证与维护窗口

- [ ] 运行前端幂等测试：

  ```bash
  cd frontend
  npm test -- --run src/api/http.test.js
  npm run build
  ```

  预期：测试/build 退出码为 `0`，无 cache import。

- [ ] 运行后端聚焦回归：

  ```bash
  cd backend
  mvn -pl :community-common-idempotency,:community-app -am \
    -Dtest='Idempotency*Test,*ApplicationServiceTest' test
  ```

  预期：所有受保护 use case 在事务中通过，serialization rollback 证据成立。

- [ ] 运行真实 MySQL migration 测试：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am test
  ```

  预期：在本计划合入点执行 9 个 migration；全部计划合并后总控验收更新为 11 个。

- [ ] 运行架构守卫：

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：事务仍由 ApplicationService 拥有，Controller 不直接调用 Guard/store/mapper。

- [ ] 维护窗口先停止所有旧 Community writer 并等待活动请求结束，再执行 V009；迁移前后记录：

  ```sql
  select status, count(*) from http_idempotency group by status order by status;
  ```

  预期：迁移后 `P=0`，原残留数量全部计入 `I`；新 writer 启动后不会产生已提交 `P`。
- [ ] 发布后监控 `replay_conflict`、`indeterminate`、`store_unavailable`、`serialize_error`；日志不得记录 request body、response JSON 或 idempotency key 全值。
- [ ] `git diff --check`，预期无 whitespace 错误。
