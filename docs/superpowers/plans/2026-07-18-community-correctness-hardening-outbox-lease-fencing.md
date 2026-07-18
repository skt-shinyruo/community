# Outbox Lease Fencing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 Community 与 IM 共用的 JDBC outbox 增加不透明 lease token fencing，使过期 worker 在事件被重新 claim 后无法写入 success、retry 或 dead 状态。

**Architecture:** claim 生成 `OutboxLease(rowId, token)` 并将 token 与独立 `processing_lease_until` 写入数据库；worker 把 lease 原样传给所有终态更新。每个更新以 `id + PROCESSING + lease_token` compare-and-set，affected rows 为 0 表示所有权已丢失，只记录 warning/counter。恢复仅处理 deadline 到期的 row，并先清除旧 token/deadline。Community 和 IM 使用同一个 `JdbcOutboxEventStore` 与相同列/索引语义。

**Tech Stack:** Java 21、Spring JDBC、MySQL 8、H2、Flyway、Micrometer、JUnit 5、Testcontainers、Maven。

---

## 最终存储契约

```java
public record OutboxLease(UUID rowId, UUID token) {
    public OutboxLease {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(token, "token");
    }
}

Optional<OutboxLease> tryClaimProcessing(UUID rowId, Instant leaseUntil, Instant now);
boolean markSucceeded(OutboxLease lease, Instant now);
boolean markFailedAndScheduleRetry(OutboxLease lease, Instant now, Instant nextRetryAt, String error);
boolean markDead(OutboxLease lease, Instant now, String error);
int recoverExpiredLeases(Instant now);
```

`rowId` 对应 `outbox_event.id`，不能与业务字符串 `event_id` 混淆。`next_retry_at` 只表达 PENDING 重试时间；`processing_lease_until` 只表达 PROCESSING deadline。两者不能复用。

## Community V008 与 IM V002

### 写 migration RED 测试

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V008__add_outbox_lease_fencing.sql`
- Create: `backend/community-im-db-migrations/src/main/resources/db/migration/im-core/V002__add_outbox_lease_fencing.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-im-db-migrations/src/test/java/com/nowcoder/community/im/migration/ImMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-im/im-core/src/test/resources/schema.sql`

- [ ] Community 空库断言 migration count 从 `7` 到 `8`；IM 从 `1` 到 `2`；重复 migrate 仍为 `0`。
- [ ] 两库都断言 `outbox_event` 包含 nullable `lease_token BINARY(16)`、nullable `processing_lease_until TIMESTAMP` 和索引 `idx_outbox_processing_lease(status, processing_lease_until, id)`。
- [ ] V001 upgrade fixture 各插入一条 PENDING 和 PROCESSING 事件；迁移后 payload/event id/status/retry 保持不变，新列为 null。
- [ ] H2 fixture 增加相同列与索引，使 Community/IM runtime repository test 使用迁移后形状。
- [ ] 明确断言以下文件内容不变：Community/IM `V001` 和两个 schema manifest。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-db-migrations,:community-im-db-migrations -am \
    -Dtest='CommunityMigrationTest,ImMigrationTest' test
  ```

  预期：新 migration/列/索引不存在，count 断言失败。

### 实现两库等价迁移

- [ ] 两个 migration 使用相同 DDL 语义：

  ```sql
  alter table outbox_event
    add column lease_token binary(16) null after status,
    add column processing_lease_until timestamp null after lease_token,
    add index idx_outbox_processing_lease(status, processing_lease_until, id);
  ```

  如 MySQL 对列位置/重复索引有限制，以 Testcontainers 实测为准；不得写 vendor-agnostic 测试通过但 MySQL 失败的语法。
- [ ] 不回填随机 token：迁移期间旧 worker 必须已经停止；新 worker 首次 claim 时生成 token。
- [ ] 更新两个 H2 schema 的 `outbox_event`，索引名和列可空性与 MySQL 一致。
- [ ] 运行 GREEN：

  ```bash
  cd backend
  mvn -pl :community-db-migrations,:community-im-db-migrations -am \
    -Dtest='CommunityMigrationTest,ImMigrationTest' test
  ```

  预期：空库、V001 upgrade、重复 migrate、列/索引断言全部通过。

- [ ] 提交 schema：

  ```bash
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V008__add_outbox_lease_fencing.sql \
          backend/community-im-db-migrations/src/main/resources/db/migration/im-core/V002__add_outbox_lease_fencing.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-im-db-migrations/src/test/java/com/nowcoder/community/im/migration/ImMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql \
          backend/community-im/im-core/src/test/resources/schema.sql
  git commit -m "feat(migration): add outbox lease fencing columns"
  ```

## JDBC store fencing

### 写顺序竞争 RED 测试

**Files:**

- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxLease.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxLeaseRecoveryAdapter.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreGovernanceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxLeaseRecoveryAdapterTest.java`

- [ ] 用固定 `UuidV7Generator` 或 captor 取得 token A/B，执行精确顺序：PENDING -> claim A -> A deadline 到期 -> recover -> claim B。
- [ ] 在 B claim 后分别调用 `markSucceeded(A)`、`markFailedAndScheduleRetry(A)`、`markDead(A)`，断言都返回 false，row 仍是 B 拥有的 PROCESSING，retry/error 未改变。
- [ ] 分别用 B 执行 success/retry/dead，断言返回 true且字段正确；每种终态使用独立测试 row。
- [ ] claim 断言 `lease_token=B`、`processing_lease_until=<deadline>`、`next_retry_at=null`。
- [ ] recovery 只选择 `processing_lease_until <= now`；清除 token/deadline，设为 PENDING 且 `next_retry_at=now`，未到期 row 不动。
- [ ] `JdbcOutboxLeaseRecoveryAdapterTest` 固定时钟并覆盖 limit clamp；插入一条 `next_retry_at` 已到期但 `processing_lease_until` 未到期的 PROCESSING row，断言不能恢复，证明 ops 恢复入口不再读取旧字段。
- [ ] 在 adapter 恢复 lease A 后用 shared store claim lease B，再尝试 `markSucceeded(A)`、retry 和 dead；三个旧 lease 更新都必须返回 false，B 的 token、deadline、retry/error 保持不变。
- [ ] manual `requeueDeadForReplay` 清除残留 token/deadline，governance query/view 不返回 lease token，避免把能力凭据暴露给运维 API。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='JdbcOutboxEventStoreTest,JdbcOutboxEventStoreGovernanceTest,JdbcOutboxLeaseRecoveryAdapterTest' test
  ```

  预期：当前 store 无 token，旧 worker A 可无条件覆盖 B，顺序断言失败。

### 实现 token CAS

- [ ] `tryClaimProcessing` 用 `idGenerator.next()` 生成 token，SQL 只从 PENDING claim：

  ```sql
  update outbox_event
  set status = 'PROCESSING', lease_token = ?, processing_lease_until = ?,
      next_retry_at = null, updated_at = ?
  where id = ? and status = 'PENDING'
  ```

- [ ] updated=1 返回 `Optional.of(new OutboxLease(id, token))`，否则 `Optional.empty()`；token 不写日志。
- [ ] 三个终态 SQL 都包含：

  ```sql
  where id = ? and status = 'PROCESSING' and lease_token = ?
  ```

  success/dead 清除 token/deadline；retry 改回 PENDING、增加 retry count、写 next retry，并清除 token/deadline。
- [ ] shared store 和 `JdbcOutboxLeaseRecoveryAdapter` 的恢复条件都使用 `processing_lease_until <= now`，恢复 SQL 同时要求 `status=PROCESSING`，并设置 `status=PENDING`、`next_retry_at=now`、`lease_token=null`、`processing_lease_until=null`；不再把 `next_retry_at` 当 processing lease。
- [ ] `JdbcOutboxLeaseRecoveryAdapter` 构造器注入仓库现有 `TimeConfig` 提供的 UTC `Clock`，每次调用只取一次 `clock.instant()` 并复用同一 timestamp 完成 select/update；禁止在逐行循环中重复读取系统时间。
- [ ] adapter 的逐行 update 必须重新校验当前 deadline；select 后已被其他恢复者处理并由 lease B 重新 claim 的 row affected=0，计入 scanned 但不计入 recovered，不能清除 B 的 token。
- [ ] enqueue 明确让 lease/deadline 为 null；dead replay 也清除它们。
- [ ] `OutboxEvent` 不携带 token；lease 是 worker ownership capability，不能成为 handler payload。
- [ ] 运行 GREEN，预期 store/governance 测试全部通过。
- [ ] 提交 store fencing：

  ```bash
  git add backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox \
          backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxLeaseRecoveryAdapter.java \
          backend/community-app/src/test/java/com/nowcoder/community/common/outbox \
          backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxLeaseRecoveryAdapterTest.java
  git commit -m "fix(outbox): fence terminal updates with lease tokens"
  ```

## Worker 所有权丢失处理

### 写 Worker RED 测试

**Files:**

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/OutboxWorkerRetryTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/OutboxWorkerSchedulerTest.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/autoconfig/OutboxAutoConfiguration.java`
- Modify: `backend/community-common/common-outbox/pom.xml`

- [ ] success、no-handler retry、handler exception retry、max-retry dead 四条路径捕获传给 store 的同一个 `OutboxLease`。
- [ ] 模拟每个 mark 方法返回 false，断言 worker 不调用任何无条件 fallback、不再次 mark，并记录 action `outbox_lease_lost`。
- [ ] 用 `SimpleMeterRegistry` 断言 Micrometer meter `outbox.lease.lost` 增加（Prometheus 导出为 `outbox_lease_lost_total`），tag 只有受控 topic 和 `transition=success|retry|dead`。
- [ ] recovery 后旧 handler 返回的顺序测试证明旧 worker 只产生 lease-lost 信号，不覆盖新 owner。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='OutboxWorkerRetryTest,OutboxWorkerSchedulerTest' test
  ```

  预期：Worker 仍传 UUID 且不检查 affected rows，测试编译或断言失败。

### 贯穿 lease 并增加信号

- [ ] `pollOnce` 从 claim Optional 取得 lease；handler 仍接收原 `OutboxEvent`，mark 调用接收 lease。
- [ ] 将 `handleFailure` 签名改为：

  ```java
  private void handleFailure(OutboxEvent event, OutboxLease lease, Instant now, RuntimeException error)
  ```

- [ ] 每次 mark 返回 false 时调用统一 `recordLeaseLost(event, transition)` 后结束当前 event；禁止使用 event id 再写一次。
- [ ] `common-outbox` 增加 Micrometer core 依赖；`OutboxWorkerScheduler` 通过 `ObjectProvider<MeterRegistry>` 可选取得 registry 并传给 `OutboxWorker`，未配置 registry 时 worker 仍正常工作。保留一个兼容测试构造器，避免各 deployable 自行复制 meter wiring。
- [ ] warning 包含 eventId/topic/transition/retry count，不包含 lease token/payload/last error 原文；error class 可保留。
- [ ] `OutboxAutoConfiguration` 继续创建唯一 shared `JdbcOutboxEventStore` 和 scheduler，IM `ImOutboxConfiguration` 只提供 handlers，不复制 store 实现。
- [ ] 运行 GREEN，预期 Worker/Scheduler 测试通过。
- [ ] 提交 Worker fencing：

  ```bash
  git add backend/community-common/common-outbox \
          backend/community-app/src/test/java/com/nowcoder/community/common/outbox
  git commit -m "fix(outbox): stop stale workers after lease loss"
  ```

## Community 与 IM 运行时契约

### 写两 deployable 的共享实现测试

**Files:**

- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/outbox/ImMessageOutboxEnqueuerTest.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/outbox/ImKafkaOutboxHandlerTest.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/infrastructure/persistence/ImCoreMySqlMigrationRepositoryContractTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreTest.java`

- [ ] IM MySQL contract 断言 autoconfig bean 类型就是 shared `JdbcOutboxEventStore`，并对 IM datasource 完成 claim/recover/reclaim/old-token-CAS-miss 顺序。
- [ ] Community 同样顺序在 Community datasource 运行，证明不是只更新 migration 而漏掉 runtime fixture。
- [ ] enqueue/handler payload contract 不变；token 不进入 Kafka message或 `OutboxEventView`。
- [ ] 运行：

  ```bash
  cd backend
  mvn -pl :community-app,:im-core -am \
    -Dtest='JdbcOutboxEventStoreTest,ImCoreMySqlMigrationRepositoryContractTest,ImMessageOutboxEnqueuerTest,ImKafkaOutboxHandlerTest' test
  ```

  预期：全部通过；Testcontainers 测试需要 Docker。

- [ ] 提交 deployable 契约测试：

  ```bash
  git add backend/community-app/src/test/java/com/nowcoder/community/common/outbox \
          backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core
  git commit -m "test(outbox): verify fencing in community and im"
  ```

## 综合验证与维护窗口

- [ ] 运行共享实现和两个 deployable：

  ```bash
  cd backend
  mvn -pl :community-common-outbox,:community-app,:im-core -am \
    -Dtest='*Outbox*Test,ImCoreMySqlMigrationRepositoryContractTest' test
  ```

  预期：`BUILD SUCCESS`。

- [ ] 运行 migration modules：

  ```bash
  cd backend
  mvn -pl :community-db-migrations,:community-im-db-migrations -am test
  ```

  预期：本计划合入点 Community 8 个 migration、IM 2 个 migration；全部计划合并后 Community 由总控验收到 11 个。

- [ ] 运行架构守卫：

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：业务 ApplicationService/handler 未直接访问 outbox mapper/dataobject；shared infrastructure 边界保持不变。

- [ ] 发布前停止并排空所有旧 Community 与 IM outbox worker；确认进程列表和 worker poll metric 均为 0 后才执行 V008/V002。
- [ ] migration 后查询两库：

  ```sql
  select status,
         sum(lease_token is not null) as token_rows,
         sum(processing_lease_until is not null) as deadline_rows
  from outbox_event
  group by status;
  ```

  预期：恢复 worker 前旧 row 的 token/deadline 都为 0；恢复后只有 PROCESSING row 可同时拥有 token/deadline。
- [ ] 只能在确认没有旧 binary 后启动 fenced worker；旧/新 worker 混跑会让旧 worker 的无条件 update 绕过 fencing，属于发布阻断条件。
- [ ] 观察 `outbox_lease_lost_total` 和 `outbox_lease_recovery`；少量 lease loss 可由长 handler 引起，持续增长需要检查 lease 时长/handler latency，不能用无条件更新消除告警。
- [ ] `git diff --check`，预期无 whitespace 错误。
