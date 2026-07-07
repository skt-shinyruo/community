# BBS Reliability Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first implementation wave of the high-concurrency BBS reliability platform: outbox governance, admin ops APIs, projection lag visibility, hot-cache degradation signals, and core BBS reliability regressions.

**Architecture:** Add a narrow `ops` governance domain that observes and requeues reliability work without owning business facts. Keep `common-outbox` as the outbox runtime and add query/replay primitives there; expose governance through `ops.application` ports and adapters so controllers only call same-domain application services.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Security, MyBatis/JDBC, Micrometer, JUnit 5, Mockito, Spring MockMvc, ArchUnit, Maven.

## Global Constraints

- `content` remains the owner for posts, comments, and hot-feed source facts.
- `social` remains the owner for likes, follows, and blocks.
- `search`, `notice`, `growth`, and hot feed are projections or derived read models; they must not become facts.
- Controllers, listeners, handlers, bridges, enqueuers, and jobs call only same-domain `*ApplicationService`.
- Cross-domain synchronous collaboration uses foreign owner `api.query` / `api.action` / `api.model`.
- Cross-domain asynchronous collaboration uses owner `contracts.event`.
- `ops` can query, requeue, trigger compensation, audit, and expose metrics; it must not directly mutate business owner facts.
- Outbox replay must requeue the event to the normal worker path; it must not call a handler directly.
- Admin ops APIs use `/api/ops/**`, require `ROLE_ADMIN`, and remain covered by the existing token freshness high-risk prefix.
- Plans and specs live under `docs/superpowers`; handbook docs live under `docs/handbook`.

---

## Scope Check

The spec covers several reliability concerns. This plan keeps them in one implementation wave because the first deliverable is a shared governance platform, not four unrelated features. Tasks are split so each produces independently testable software and can be reviewed on its own.

## File Structure

### Common Outbox Runtime

- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
  Adds governance query, backlog summary, event lookup, and safe `DEAD -> PENDING` replay state transition.
- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventQuery.java`
  Immutable query criteria for outbox governance search.
- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventView.java`
  Governance row view including `createdAt` and `updatedAt`; keeps worker `OutboxEvent` unchanged.
- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxBacklogRow.java`
  Backlog count grouped by topic and status.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreGovernanceTest.java`
  H2 tests for query, backlog, lookup, and replay.

### Ops Domain And Application

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/domain/model/ReplayDecision.java`
  Small domain value describing whether replay is allowed and why.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernancePort.java`
  Application-owned port for outbox governance persistence.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxHandlerCatalog.java`
  Application-owned port for known outbox handler topics.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationService.java`
  Admin use cases for backlog, event search, and replay.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/FindOutboxEventsCommand.java`
  Search command.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/ReplayOutboxEventCommand.java`
  Replay command.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxBacklogResult.java`
  Backlog result.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxEventResult.java`
  Event row result.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxReplayResult.java`
  Replay result.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationServiceTest.java`
  Unit tests for replay decisions and validation.

### Ops Infrastructure And Metrics

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapter.java`
  Maps `common-outbox` store rows into ops application results.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/SpringOutboxHandlerCatalog.java`
  Reads registered `OutboxHandler` beans and exposes known topics.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetrics.java`
  Records replay counters with bounded dimensions.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapterTest.java`
  Adapter mapping and replay test.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetricsTest.java`
  Micrometer `SimpleMeterRegistry` test.

### Ops Web And Security

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/OutboxOpsController.java`
  Admin endpoints under `/api/ops/outbox`.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxEventResponse.java`
  Web response DTO.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxBacklogResponse.java`
  Web response DTO.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxReplayRequest.java`
  Web request DTO.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxReplayResponse.java`
  Web response DTO.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`
  Requires `ROLE_ADMIN` for `/api/ops/**`.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java`
  MockMvc tests for admin enforcement and service delegation.

### Architecture Guardrails

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
  Add an explicit `ops.controller` rule so governance controllers cannot depend on domain, infrastructure, common outbox, or business owner packages.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
  Ensure `ops.domain` does not depend on Spring, MyBatis, Redis, HTTP DTOs, mapper, or dataobject packages.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
  Ensure `ops.infrastructure` does not leak mapper/dataobject types into application/domain.

### Projection Lag And Hot Cache Visibility

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationService.java`
  Reports projection lag by reading outbox health through an application-owned port.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionLagPort.java`
  Port returning projection lag rows.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/ProjectionLagResult.java`
  Result row.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapter.java`
  Computes lag for projection topics from `outbox_event`.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/ProjectionOpsController.java`
  Admin endpoint `/api/ops/projections/lag`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
  Record hot-feed cache hit, fallback, and degraded counters.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java`
  Bounded Micrometer metrics for hot feed reads.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java`

### Business Slice Regression Tests

- Modify or extend: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
  Search replay must not revive deleted posts.
- Modify or extend: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
  Replaying the same `sourceEventId` must not duplicate notices.
- Modify or extend: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java`
  Replaying the same `sourceEventId` must not duplicate task progress.
- Modify or extend: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`
  Out-of-order hot-feed projection must not regress rank/source version.

### Documentation

- Modify: `docs/handbook/reliability.md`
  Add ops governance API and replay semantics after implementation.
- Modify: `docs/handbook/operations.md`
  Add runbook steps for backlog, `DEAD`, replay, and cache degradation.
- Modify: `docs/handbook/observability.md`
  Document the exact new metric names and labels.

---

### Task 1: Common Outbox Governance Primitives

**Files:**
- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventQuery.java`
- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventView.java`
- Create: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxBacklogRow.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreGovernanceTest.java`

**Interfaces:**
- Produces: `OutboxEventQuery`, `OutboxEventView`, `OutboxBacklogRow`.
- Produces: `JdbcOutboxEventStore.findEvents(OutboxEventQuery query): List<OutboxEventView>`.
- Produces: `JdbcOutboxEventStore.findEventById(UUID id): Optional<OutboxEventView>`.
- Produces: `JdbcOutboxEventStore.countBacklogByTopicAndStatus(): List<OutboxBacklogRow>`.
- Produces: `JdbcOutboxEventStore.requeueDeadForReplay(UUID id, Instant now, String reason): boolean`.
- Consumed by: Task 3 ops infrastructure adapter.

- [ ] **Step 1: Write the failing governance store test**

Create `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreGovernanceTest.java`:

```java
package com.nowcoder.community.common.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxEventStoreGovernanceTest {

    @Test
    void governanceQueriesShouldFilterSummarizeAndRequeueDeadEvents() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);
            Instant now = Instant.parse("2026-07-07T00:00:00Z");
            UUID deadId = UUID.fromString("0197e6f0-0000-7000-8000-000000000001");
            UUID pendingId = UUID.fromString("0197e6f0-0000-7000-8000-000000000002");
            insertEvent(jdbcTemplate, deadId, "e-dead:search", "projection.search.post", "post-1",
                    OutboxEventStatus.DEAD, 4, now.minusSeconds(120), now.minusSeconds(60));
            insertEvent(jdbcTemplate, pendingId, "e-pending:growth", "projection.growth.task.post", "post-1",
                    OutboxEventStatus.PENDING, 1, now.minusSeconds(30), now.minusSeconds(20));

            List<OutboxEventView> deadRows = store.findEvents(new OutboxEventQuery(
                    OutboxEventStatus.DEAD,
                    "projection.search.post",
                    null,
                    now.minusSeconds(300),
                    now,
                    20
            ));

            assertThat(deadRows).hasSize(1);
            assertThat(deadRows.get(0).id()).isEqualTo(deadId);
            assertThat(deadRows.get(0).createdAt()).isEqualTo(now.minusSeconds(120));
            assertThat(deadRows.get(0).updatedAt()).isEqualTo(now.minusSeconds(60));

            assertThat(store.findEventById(deadId)).isPresent();
            assertThat(store.countBacklogByTopicAndStatus())
                    .extracting(row -> row.topic() + "|" + row.status() + "|" + row.count())
                    .contains("projection.search.post|DEAD|1", "projection.growth.task.post|PENDING|1");

            boolean requeued = store.requeueDeadForReplay(deadId, now, "fixed es mapping");

            assertThat(requeued).isTrue();
            OutboxEventView replayed = store.findEventById(deadId).orElseThrow();
            assertThat(replayed.status()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(replayed.retryCount()).isEqualTo(0);
            assertThat(replayed.nextRetryAt()).isEqualTo(now);
            assertThat(replayed.lastError()).isEqualTo("replay requested: fixed es mapping");
        } finally {
            db.shutdown();
        }
    }

    private static void insertEvent(
            JdbcTemplate jdbcTemplate,
            UUID id,
            String eventId,
            String topic,
            String eventKey,
            String status,
            int retryCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, retry_count,
                          next_retry_at, last_error, trace_id, traceparent, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?)
                        """,
                BinaryUuidCodec.toBytes(id),
                eventId,
                topic,
                eventKey,
                "{\"postId\":\"post-1\"}",
                status,
                retryCount,
                "boom",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
        );
    }

    private static void createOutboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "create table if not exists outbox_event (\n" +
                        "  id binary(16) primary key,\n" +
                        "  event_id varchar(64) not null,\n" +
                        "  topic varchar(255) not null,\n" +
                        "  event_key varchar(255) not null,\n" +
                        "  payload clob not null,\n" +
                        "  status varchar(32) not null,\n" +
                        "  retry_count int not null default 0,\n" +
                        "  next_retry_at timestamp,\n" +
                        "  last_error varchar(512),\n" +
                        "  trace_id varchar(32) null,\n" +
                        "  traceparent varchar(128) null,\n" +
                        "  created_at timestamp default current_timestamp,\n" +
                        "  updated_at timestamp default current_timestamp,\n" +
                        "  constraint uk_outbox_event_id unique (event_id)\n" +
                        ")"
        );
        jdbcTemplate.execute("create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id)");
        jdbcTemplate.execute("delete from outbox_event");
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=JdbcOutboxEventStoreGovernanceTest
```

Expected: compilation fails because `OutboxEventQuery`, `OutboxEventView`, `OutboxBacklogRow`, and governance methods on `JdbcOutboxEventStore` do not exist.

- [ ] **Step 3: Add outbox governance records**

Create `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventQuery.java`:

```java
package com.nowcoder.community.common.outbox;

import org.springframework.util.StringUtils;

import java.time.Instant;

public record OutboxEventQuery(
        String status,
        String topic,
        String eventId,
        Instant createdFrom,
        Instant createdTo,
        int limit
) {

    public int safeLimit() {
        return Math.min(500, Math.max(1, limit <= 0 ? 50 : limit));
    }

    public String normalizedStatus() {
        return StringUtils.hasText(status) ? status.trim() : null;
    }

    public String normalizedTopic() {
        return StringUtils.hasText(topic) ? topic.trim() : null;
    }

    public String normalizedEventId() {
        return StringUtils.hasText(eventId) ? eventId.trim() : null;
    }
}
```

Create `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventView.java`:

```java
package com.nowcoder.community.common.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventView(
        UUID id,
        String eventId,
        String topic,
        String eventKey,
        String payload,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String traceId,
        String traceparent,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Create `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxBacklogRow.java`:

```java
package com.nowcoder.community.common.outbox;

public record OutboxBacklogRow(String topic, String status, long count) {
}
```

- [ ] **Step 4: Add governance methods to `JdbcOutboxEventStore`**

Modify `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java` by adding these public methods before `normalize(...)`:

```java
public List<OutboxEventView> findEvents(OutboxEventQuery query) {
    OutboxEventQuery q = query == null ? new OutboxEventQuery(null, null, null, null, null, 50) : query;
    StringBuilder sql = new StringBuilder(
            "select id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, " +
                    "last_error, trace_id, traceparent, created_at, updated_at from outbox_event where 1 = 1"
    );
    List<Object> args = new java.util.ArrayList<>();
    if (q.normalizedStatus() != null) {
        sql.append(" and status = ?");
        args.add(q.normalizedStatus());
    }
    if (q.normalizedTopic() != null) {
        sql.append(" and topic = ?");
        args.add(q.normalizedTopic());
    }
    if (q.normalizedEventId() != null) {
        sql.append(" and event_id = ?");
        args.add(q.normalizedEventId());
    }
    if (q.createdFrom() != null) {
        sql.append(" and created_at >= ?");
        args.add(Timestamp.from(q.createdFrom()));
    }
    if (q.createdTo() != null) {
        sql.append(" and created_at <= ?");
        args.add(Timestamp.from(q.createdTo()));
    }
    sql.append(" order by id asc limit ?");
    args.add(q.safeLimit());
    return jdbcTemplate.query(sql.toString(), governanceRowMapper(), args.toArray());
}

public Optional<OutboxEventView> findEventById(UUID id) {
    if (id == null) {
        return Optional.empty();
    }
    List<OutboxEventView> rows = jdbcTemplate.query(
            "select id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, " +
                    "last_error, trace_id, traceparent, created_at, updated_at from outbox_event where id = ?",
            governanceRowMapper(),
            BinaryUuidCodec.toBytes(id)
    );
    return rows.stream().findFirst();
}

public List<OutboxBacklogRow> countBacklogByTopicAndStatus() {
    return jdbcTemplate.query(
            "select topic, status, count(*) as row_count from outbox_event " +
                    "where status in (?, ?, ?) group by topic, status order by topic asc, status asc",
            (rs, rowNum) -> new OutboxBacklogRow(
                    rs.getString("topic"),
                    rs.getString("status"),
                    rs.getLong("row_count")
            ),
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING,
            OutboxEventStatus.DEAD
    );
}

public boolean requeueDeadForReplay(UUID id, Instant now, String reason) {
    if (id == null) {
        return false;
    }
    Timestamp nowTs = Timestamp.from(now == null ? Instant.now() : now);
    int updated = jdbcTemplate.update(
            "update outbox_event set status = ?, retry_count = 0, next_retry_at = ?, last_error = ?, updated_at = ? " +
                    "where id = ? and status = ?",
            OutboxEventStatus.PENDING,
            nowTs,
            truncateError("replay requested: " + (StringUtils.hasText(reason) ? reason.trim() : "manual")),
            nowTs,
            BinaryUuidCodec.toBytes(id),
            OutboxEventStatus.DEAD
    );
    return updated > 0;
}
```

Add this private mapper before the existing `rowMapper()`:

```java
private static RowMapper<OutboxEventView> governanceRowMapper() {
    return (rs, rowNum) -> {
        Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new OutboxEventView(
                BinaryUuidCodec.fromBytes(rs.getBytes("id")),
                rs.getString("event_id"),
                rs.getString("topic"),
                rs.getString("event_key"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                nextRetryAt == null ? null : nextRetryAt.toInstant(),
                rs.getString("last_error"),
                rs.getString("trace_id"),
                rs.getString("traceparent"),
                createdAt == null ? null : createdAt.toInstant(),
                updatedAt == null ? null : updatedAt.toInstant()
        );
    };
}
```

Also add imports:

```java
import java.util.Optional;
```

- [ ] **Step 5: Run the task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=JdbcOutboxEventStoreGovernanceTest,JdbcOutboxEventStoreTest,OutboxWorkerRetryTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventQuery.java \
        backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventView.java \
        backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxBacklogRow.java \
        backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java \
        backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreGovernanceTest.java
git commit -m "feat: add outbox governance primitives"
```

### Task 2: Ops Outbox Governance Application

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/domain/model/ReplayDecision.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernancePort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxHandlerCatalog.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/FindOutboxEventsCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/ReplayOutboxEventCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxBacklogResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxEventResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxReplayResult.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationServiceTest.java`

**Interfaces:**
- Consumes: Task 1 outbox concepts through `OutboxGovernancePort`.
- Produces: `OutboxGovernanceApplicationService.listBacklog()`.
- Produces: `OutboxGovernanceApplicationService.findEvents(FindOutboxEventsCommand command)`.
- Produces: `OutboxGovernanceApplicationService.replay(ReplayOutboxEventCommand command)`.
- Consumed by: Task 3 infrastructure and Task 4 controller.

- [ ] **Step 1: Write the failing application service test**

Create `backend/community-app/src/test/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationServiceTest.java`:

```java
package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxGovernanceApplicationServiceTest {

    private OutboxGovernancePort port;
    private OutboxHandlerCatalog handlerCatalog;
    private OutboxGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        port = mock(OutboxGovernancePort.class);
        handlerCatalog = mock(OutboxHandlerCatalog.class);
        service = new OutboxGovernanceApplicationService(port, handlerCatalog);
    }

    @Test
    void listBacklogShouldDelegateToPort() {
        when(port.listBacklog()).thenReturn(List.of(new OutboxBacklogResult("projection.search.post", "DEAD", 2L)));

        List<OutboxBacklogResult> result = service.listBacklog();

        assertThat(result).containsExactly(new OutboxBacklogResult("projection.search.post", "DEAD", 2L));
    }

    @Test
    void findEventsShouldNormalizeLimitAndDelegate() {
        FindOutboxEventsCommand command = new FindOutboxEventsCommand(
                OutboxEventStatus.DEAD,
                " projection.search.post ",
                null,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-08T00:00:00Z"),
                1000
        );

        service.findEvents(command);

        verify(port).findEvents(new FindOutboxEventsCommand(
                OutboxEventStatus.DEAD,
                "projection.search.post",
                null,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-08T00:00:00Z"),
                500
        ));
    }

    @Test
    void replayShouldRejectNonDeadEvent() {
        UUID outboxId = uuid(1);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.PENDING, "{}")));

        assertThatThrownBy(() -> service.replay(new ReplayOutboxEventCommand(uuid(99), outboxId, "retry after fix")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only DEAD outbox events can be replayed");
    }

    @Test
    void replayShouldRejectMissingHandler() {
        UUID outboxId = uuid(1);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.DEAD, "{}")));
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(false);

        assertThatThrownBy(() -> service.replay(new ReplayOutboxEventCommand(uuid(99), outboxId, "retry after fix")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no outbox handler registered");
    }

    @Test
    void replayShouldRequeueDeadEvent() {
        UUID outboxId = uuid(1);
        UUID actorId = uuid(99);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.DEAD, "{\"postId\":\"p1\"}")));
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(true);
        when(port.requeueDead(outboxId, "retry after fix")).thenReturn(true);

        var result = service.replay(new ReplayOutboxEventCommand(actorId, outboxId, "retry after fix"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.beforeStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(result.afterStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(result.topic()).isEqualTo("projection.search.post");
        verify(port).requeueDead(outboxId, "retry after fix");
    }

    private static OutboxEventResult event(UUID id, String status, String payload) {
        return new OutboxEventResult(
                id,
                "event-1",
                "projection.search.post",
                "post-1",
                payload,
                status,
                3,
                null,
                "boom",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:01:00Z")
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxGovernanceApplicationServiceTest
```

Expected: compilation fails because the `ops` application types do not exist.

- [ ] **Step 3: Add commands and results**

Create the following records:

`backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/FindOutboxEventsCommand.java`

```java
package com.nowcoder.community.ops.application.command;

import java.time.Instant;

public record FindOutboxEventsCommand(
        String status,
        String topic,
        String eventId,
        Instant createdFrom,
        Instant createdTo,
        int limit
) {
    public FindOutboxEventsCommand normalized() {
        return new FindOutboxEventsCommand(
                trim(status),
                trim(topic),
                trim(eventId),
                createdFrom,
                createdTo,
                Math.min(500, Math.max(1, limit <= 0 ? 50 : limit))
        );
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

`backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/ReplayOutboxEventCommand.java`

```java
package com.nowcoder.community.ops.application.command;

import java.util.UUID;

public record ReplayOutboxEventCommand(UUID actorUserId, UUID outboxId, String reason) {
    public String normalizedReason() {
        return reason == null || reason.isBlank() ? "" : reason.trim();
    }
}
```

`backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxBacklogResult.java`

```java
package com.nowcoder.community.ops.application.result;

public record OutboxBacklogResult(String topic, String status, long count) {
}
```

`backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxEventResult.java`

```java
package com.nowcoder.community.ops.application.result;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventResult(
        UUID id,
        String eventId,
        String topic,
        String eventKey,
        String payload,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String traceId,
        String traceparent,
        Instant createdAt,
        Instant updatedAt
) {
}
```

`backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxReplayResult.java`

```java
package com.nowcoder.community.ops.application.result;

import java.util.UUID;

public record OutboxReplayResult(
        UUID outboxId,
        String eventId,
        String topic,
        String beforeStatus,
        String afterStatus,
        boolean replayed,
        String result
) {
}
```

- [ ] **Step 4: Add domain decision and application ports**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/domain/model/ReplayDecision.java`:

```java
package com.nowcoder.community.ops.domain.model;

public record ReplayDecision(boolean allowed, String result, String reason) {

    public static ReplayDecision allow() {
        return new ReplayDecision(true, "REPLAYED", "allowed");
    }

    public static ReplayDecision reject(String reason) {
        return new ReplayDecision(false, "MANUAL_REPAIR_REQUIRED", reason);
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernancePort.java`:

```java
package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxGovernancePort {

    List<OutboxBacklogResult> listBacklog();

    List<OutboxEventResult> findEvents(FindOutboxEventsCommand command);

    Optional<OutboxEventResult> findById(UUID id);

    boolean requeueDead(UUID id, String reason);
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxHandlerCatalog.java`:

```java
package com.nowcoder.community.ops.application;

import java.util.Set;

public interface OutboxHandlerCatalog {

    boolean hasHandler(String topic);

    Set<String> topics();
}
```

- [ ] **Step 5: Add application service**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationService.java`:

```java
package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.domain.model.ReplayDecision;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class OutboxGovernanceApplicationService {

    private final OutboxGovernancePort outboxGovernancePort;
    private final OutboxHandlerCatalog outboxHandlerCatalog;

    public OutboxGovernanceApplicationService(
            OutboxGovernancePort outboxGovernancePort,
            OutboxHandlerCatalog outboxHandlerCatalog
    ) {
        this.outboxGovernancePort = Objects.requireNonNull(outboxGovernancePort, "outboxGovernancePort must not be null");
        this.outboxHandlerCatalog = Objects.requireNonNull(outboxHandlerCatalog, "outboxHandlerCatalog must not be null");
    }

    public List<OutboxBacklogResult> listBacklog() {
        return outboxGovernancePort.listBacklog();
    }

    public List<OutboxEventResult> findEvents(FindOutboxEventsCommand command) {
        FindOutboxEventsCommand normalized = command == null
                ? new FindOutboxEventsCommand(null, null, null, null, null, 50)
                : command.normalized();
        return outboxGovernancePort.findEvents(normalized);
    }

    public OutboxReplayResult replay(ReplayOutboxEventCommand command) {
        if (command == null || command.actorUserId() == null || command.outboxId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId and outboxId are required");
        }
        if (command.normalizedReason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "replay reason is required");
        }
        OutboxEventResult event = outboxGovernancePort.findById(command.outboxId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "outbox event not found"));
        ReplayDecision decision = decideReplay(event);
        if (!decision.allowed()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, decision.reason());
        }
        boolean requeued = outboxGovernancePort.requeueDead(command.outboxId(), command.normalizedReason());
        return new OutboxReplayResult(
                event.id(),
                event.eventId(),
                event.topic(),
                event.status(),
                requeued ? OutboxEventStatus.PENDING : event.status(),
                requeued,
                requeued ? decision.result() : "NOT_REQUEUED"
        );
    }

    private ReplayDecision decideReplay(OutboxEventResult event) {
        if (event == null) {
            return ReplayDecision.reject("outbox event not found");
        }
        if (!OutboxEventStatus.DEAD.equals(event.status())) {
            return ReplayDecision.reject("only DEAD outbox events can be replayed");
        }
        if (event.topic() == null || event.topic().isBlank() || !outboxHandlerCatalog.hasHandler(event.topic())) {
            return ReplayDecision.reject("no outbox handler registered for topic=" + event.topic());
        }
        if (event.payload() == null || event.payload().isBlank()) {
            return ReplayDecision.reject("outbox payload is blank");
        }
        return ReplayDecision.allow();
    }
}
```

- [ ] **Step 6: Run the task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxGovernanceApplicationServiceTest
```

Expected: test passes.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/ops \
        backend/community-app/src/test/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationServiceTest.java
git commit -m "feat: add outbox governance application service"
```

### Task 3: Ops Outbox Infrastructure And Metrics

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/SpringOutboxHandlerCatalog.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetrics.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetricsTest.java`

**Interfaces:**
- Consumes: Task 1 `JdbcOutboxEventStore`.
- Consumes: Task 2 `OutboxGovernancePort`, `OutboxHandlerCatalog`.
- Produces: Spring beans implementing ops application ports.

- [ ] **Step 1: Write failing metrics test**

Create `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetricsTest.java`:

```java
package com.nowcoder.community.ops.infrastructure.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityGovernanceMetricsTest {

    @Test
    void recordReplayShouldUseBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliabilityGovernanceMetrics metrics = new ReliabilityGovernanceMetrics(registry);

        metrics.recordReplay("projection.search.post", "REPLAYED");
        metrics.recordReplay("projection.search.post", "REPLAYED");
        metrics.recordReplay("projection.growth.task.post", "MANUAL_REPAIR_REQUIRED");

        assertThat(registry.counter(
                "community_outbox_replay_total",
                "topic", "projection.search.post",
                "result", "REPLAYED"
        ).count()).isEqualTo(2.0);
        assertThat(registry.counter(
                "community_outbox_replay_total",
                "topic", "projection.growth.task.post",
                "result", "MANUAL_REPAIR_REQUIRED"
        ).count()).isEqualTo(1.0);
    }
}
```

- [ ] **Step 2: Run the failing metrics test**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=ReliabilityGovernanceMetricsTest
```

Expected: compilation fails because `ReliabilityGovernanceMetrics` does not exist.

- [ ] **Step 3: Add metrics component**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetrics.java`:

```java
package com.nowcoder.community.ops.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReliabilityGovernanceMetrics {

    private final MeterRegistry meterRegistry;

    public ReliabilityGovernanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordReplay(String topic, String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "community_outbox_replay_total",
                Tags.of(
                        "topic", bounded(topic),
                        "result", bounded(result)
                )
        ).increment();
    }

    private String bounded(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
```

- [ ] **Step 4: Write failing infrastructure adapter test**

Create `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapterTest.java`:

```java
package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxGovernanceAdapterTest {

    @Test
    void adapterShouldMapStoreRowsAndReplayDeadEvents() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            UUID outboxId = UUID.fromString("0197e6f0-0000-7000-8000-000000000111");
            insertEvent(jdbcTemplate, outboxId);
            JdbcOutboxGovernanceAdapter adapter = new JdbcOutboxGovernanceAdapter(new JdbcOutboxEventStore(jdbcTemplate));

            var rows = adapter.findEvents(new FindOutboxEventsCommand(
                    OutboxEventStatus.DEAD,
                    "projection.search.post",
                    null,
                    null,
                    null,
                    10
            ));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).id()).isEqualTo(outboxId);
            assertThat(adapter.findById(outboxId)).isPresent();
            assertThat(adapter.listBacklog()).extracting(row -> row.topic() + "|" + row.status() + "|" + row.count())
                    .contains("projection.search.post|DEAD|1");

            assertThat(adapter.requeueDead(outboxId, "operator replay")).isTrue();
            assertThat(adapter.findById(outboxId).orElseThrow().status()).isEqualTo(OutboxEventStatus.PENDING);
        } finally {
            db.shutdown();
        }
    }

    private static void insertEvent(JdbcTemplate jdbcTemplate, UUID id) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, retry_count,
                          next_retry_at, last_error, trace_id, traceparent, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?)
                        """,
                BinaryUuidCodec.toBytes(id),
                "event-1",
                "projection.search.post",
                "post-1",
                "{\"postId\":\"post-1\"}",
                OutboxEventStatus.DEAD,
                2,
                "boom",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                Timestamp.from(Instant.parse("2026-07-07T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-07-07T00:01:00Z"))
        );
    }

    private static void createOutboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "create table if not exists outbox_event (\n" +
                        "  id binary(16) primary key,\n" +
                        "  event_id varchar(64) not null,\n" +
                        "  topic varchar(255) not null,\n" +
                        "  event_key varchar(255) not null,\n" +
                        "  payload clob not null,\n" +
                        "  status varchar(32) not null,\n" +
                        "  retry_count int not null default 0,\n" +
                        "  next_retry_at timestamp,\n" +
                        "  last_error varchar(512),\n" +
                        "  trace_id varchar(32) null,\n" +
                        "  traceparent varchar(128) null,\n" +
                        "  created_at timestamp default current_timestamp,\n" +
                        "  updated_at timestamp default current_timestamp,\n" +
                        "  constraint uk_outbox_event_id unique (event_id)\n" +
                        ")"
        );
        jdbcTemplate.execute("delete from outbox_event");
    }
}
```

- [ ] **Step 5: Add infrastructure adapters**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapter.java`:

```java
package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEventQuery;
import com.nowcoder.community.common.outbox.OutboxEventView;
import com.nowcoder.community.ops.application.OutboxGovernancePort;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOutboxGovernanceAdapter implements OutboxGovernancePort {

    private final JdbcOutboxEventStore store;

    public JdbcOutboxGovernanceAdapter(JdbcOutboxEventStore store) {
        this.store = store;
    }

    @Override
    public List<OutboxBacklogResult> listBacklog() {
        return store.countBacklogByTopicAndStatus().stream()
                .map(row -> new OutboxBacklogResult(row.topic(), row.status(), row.count()))
                .toList();
    }

    @Override
    public List<OutboxEventResult> findEvents(FindOutboxEventsCommand command) {
        FindOutboxEventsCommand c = command == null
                ? new FindOutboxEventsCommand(null, null, null, null, null, 50)
                : command.normalized();
        return store.findEvents(new OutboxEventQuery(
                        c.status(),
                        c.topic(),
                        c.eventId(),
                        c.createdFrom(),
                        c.createdTo(),
                        c.limit()
                )).stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    public Optional<OutboxEventResult> findById(UUID id) {
        return store.findEventById(id).map(this::toResult);
    }

    @Override
    public boolean requeueDead(UUID id, String reason) {
        return store.requeueDeadForReplay(id, Instant.now(), reason);
    }

    private OutboxEventResult toResult(OutboxEventView row) {
        return new OutboxEventResult(
                row.id(),
                row.eventId(),
                row.topic(),
                row.eventKey(),
                row.payload(),
                row.status(),
                row.retryCount(),
                row.nextRetryAt(),
                row.lastError(),
                row.traceId(),
                row.traceparent(),
                row.createdAt(),
                row.updatedAt()
        );
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/SpringOutboxHandlerCatalog.java`:

```java
package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.ops.application.OutboxHandlerCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpringOutboxHandlerCatalog implements OutboxHandlerCatalog {

    private final ObjectProvider<List<OutboxHandler>> handlersProvider;

    public SpringOutboxHandlerCatalog(ObjectProvider<List<OutboxHandler>> handlersProvider) {
        this.handlersProvider = handlersProvider;
    }

    @Override
    public boolean hasHandler(String topic) {
        return StringUtils.hasText(topic) && topics().contains(topic.trim());
    }

    @Override
    public Set<String> topics() {
        List<OutboxHandler> handlers = handlersProvider == null ? null : handlersProvider.getIfAvailable();
        if (handlers == null || handlers.isEmpty()) {
            return Set.of();
        }
        return handlers.stream()
                .filter(handler -> handler != null && StringUtils.hasText(handler.topic()))
                .map(handler -> handler.topic().trim())
                .collect(Collectors.toUnmodifiableSet());
    }
}
```

- [ ] **Step 6: Run the task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=JdbcOutboxGovernanceAdapterTest,ReliabilityGovernanceMetricsTest
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure \
        backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure
git commit -m "feat: wire outbox governance infrastructure"
```

### Task 4: Ops Admin API And Security

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/OutboxOpsController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxEventResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxBacklogResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxReplayRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxReplayResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java`

**Interfaces:**
- Consumes: Task 2 `OutboxGovernanceApplicationService`.
- Produces: `GET /api/ops/outbox/backlog`.
- Produces: `GET /api/ops/outbox/events`.
- Produces: `POST /api/ops/outbox/events/{outboxId}/replay`.

- [ ] **Step 1: Write the failing MockMvc test**

Create `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java`:

```java
package com.nowcoder.community.ops.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.exception.GlobalExceptionHandler;
import com.nowcoder.community.common.web.security.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.security.OpsSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutboxOpsController.class)
@Import({
        OutboxOpsController.class,
        OpsSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class OutboxOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OutboxGovernanceApplicationService outboxGovernanceApplicationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/ops/outbox/backlog")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(2).toString())).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldQueryBacklogAndEventsAndReplay() throws Exception {
        UUID adminUserId = uuid(99);
        UUID outboxId = uuid(1);
        when(outboxGovernanceApplicationService.listBacklog())
                .thenReturn(List.of(new OutboxBacklogResult("projection.search.post", "DEAD", 2L)));
        when(outboxGovernanceApplicationService.findEvents(any()))
                .thenReturn(List.of(new OutboxEventResult(
                        outboxId,
                        "event-1",
                        "projection.search.post",
                        "post-1",
                        "{\"postId\":\"post-1\"}",
                        "DEAD",
                        3,
                        null,
                        "boom",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                        Instant.parse("2026-07-07T00:00:00Z"),
                        Instant.parse("2026-07-07T00:01:00Z")
                )));
        when(outboxGovernanceApplicationService.replay(any()))
                .thenReturn(new OutboxReplayResult(outboxId, "event-1", "projection.search.post", "DEAD", "PENDING", true, "REPLAYED"));

        mockMvc.perform(get("/api/ops/outbox/backlog")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topic").value("projection.search.post"))
                .andExpect(jsonPath("$.data[0].status").value("DEAD"))
                .andExpect(jsonPath("$.data[0].count").value(2));

        mockMvc.perform(get("/api/ops/outbox/events?status=DEAD&topic=projection.search.post&limit=10")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outboxId").value(outboxId.toString()))
                .andExpect(jsonPath("$.data[0].eventId").value("event-1"));

        mockMvc.perform(post("/api/ops/outbox/events/" + outboxId + "/replay")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"fixed es mapping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.afterStatus").value("PENDING"));

        verify(outboxGovernanceApplicationService).listBacklog();
        verify(outboxGovernanceApplicationService).findEvents(any());
        verify(outboxGovernanceApplicationService).replay(any());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

- [ ] **Step 2: Run the failing MockMvc test**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
```

Expected: compilation fails because controller, DTOs, and security rules do not exist.

- [ ] **Step 3: Add DTOs**

Create the DTO classes under `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/`:

```java
package com.nowcoder.community.ops.controller.dto;

public record OutboxBacklogResponse(String topic, String status, long count) {
}
```

```java
package com.nowcoder.community.ops.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventResponse(
        UUID outboxId,
        String eventId,
        String topic,
        String eventKey,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String traceId,
        Instant createdAt,
        Instant updatedAt
) {
}
```

```java
package com.nowcoder.community.ops.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class OutboxReplayRequest {

    @NotBlank
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
```

```java
package com.nowcoder.community.ops.controller.dto;

import java.util.UUID;

public record OutboxReplayResponse(
        UUID outboxId,
        String eventId,
        String topic,
        String beforeStatus,
        String afterStatus,
        boolean replayed,
        String result
) {
}
```

- [ ] **Step 4: Add security rules**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`:

```java
package com.nowcoder.community.ops.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
public class OpsSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/api/ops/**").hasRole("ADMIN");
    }
}
```

- [ ] **Step 5: Add controller**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/OutboxOpsController.java`:

```java
package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.result.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.controller.dto.OutboxBacklogResponse;
import com.nowcoder.community.ops.controller.dto.OutboxEventResponse;
import com.nowcoder.community.ops.controller.dto.OutboxReplayRequest;
import com.nowcoder.community.ops.controller.dto.OutboxReplayResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ops/outbox")
public class OutboxOpsController {

    private final OutboxGovernanceApplicationService outboxGovernanceApplicationService;

    public OutboxOpsController(OutboxGovernanceApplicationService outboxGovernanceApplicationService) {
        this.outboxGovernanceApplicationService = outboxGovernanceApplicationService;
    }

    @GetMapping("/backlog")
    public Result<List<OutboxBacklogResponse>> backlog() {
        return Result.ok(outboxGovernanceApplicationService.listBacklog().stream()
                .map(this::toBacklogResponse)
                .toList());
    }

    @GetMapping("/events")
    public Result<List<OutboxEventResponse>> events(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        return Result.ok(outboxGovernanceApplicationService.findEvents(new FindOutboxEventsCommand(
                        status,
                        topic,
                        eventId,
                        createdFrom,
                        createdTo,
                        limit
                )).stream()
                .map(this::toEventResponse)
                .toList());
    }

    @PostMapping("/events/{outboxId}/replay")
    public Result<OutboxReplayResponse> replay(
            Authentication authentication,
            @PathVariable UUID outboxId,
            @RequestBody @Valid OutboxReplayRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toReplayResponse(outboxGovernanceApplicationService.replay(new ReplayOutboxEventCommand(
                actorUserId,
                outboxId,
                request.getReason()
        ))));
    }

    private OutboxBacklogResponse toBacklogResponse(OutboxBacklogResult result) {
        return new OutboxBacklogResponse(result.topic(), result.status(), result.count());
    }

    private OutboxEventResponse toEventResponse(OutboxEventResult result) {
        return new OutboxEventResponse(
                result.id(),
                result.eventId(),
                result.topic(),
                result.eventKey(),
                result.status(),
                result.retryCount(),
                result.nextRetryAt(),
                result.lastError(),
                result.traceId(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    private OutboxReplayResponse toReplayResponse(OutboxReplayResult result) {
        return new OutboxReplayResponse(
                result.outboxId(),
                result.eventId(),
                result.topic(),
                result.beforeStatus(),
                result.afterStatus(),
                result.replayed(),
                result.result()
        );
    }
}
```

- [ ] **Step 6: Run the task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
```

Expected: test passes.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/ops/controller \
        backend/community-app/src/main/java/com/nowcoder/community/ops/security \
        backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java
git commit -m "feat: expose outbox ops admin api"
```

### Task 5: Ops Architecture Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`

**Interfaces:**
- Consumes: Tasks 2-4 `ops` packages.
- Produces: ArchUnit rules that prevent ops from becoming a business fact owner or bypassing application boundaries.

- [ ] **Step 1: Add failing ArchUnit rules**

Add this rule to `DomainBoundaryArchTest`:

```java
@ArchTest
static final ArchRule ops_domain_must_not_depend_on_framework_or_persistence =
        noClasses()
                .that().resideInAnyPackage("..ops.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "org.mybatis..",
                        "..mapper..",
                        "..dataobject..",
                        "..controller.dto..",
                        "..infrastructure.."
                )
                .because("ops domain expresses governance decisions only");
```

Add this rule to `ControllerBoundaryArchTest`:

```java
@ArchTest
static final ArchRule ops_controllers_should_enter_ops_application_only =
        noClasses()
                .that().resideInAnyPackage("..ops.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..ops.domain..",
                        "..ops.infrastructure..",
                        "..common.outbox..",
                        "..content..",
                        "..social..",
                        "..search..",
                        "..notice..",
                        "..growth.."
                )
                .because("ops controllers must enter through ops application services only");
```

Add this rule to `InfraBoundaryArchTest`:

```java
@ArchTest
static final ArchRule ops_application_must_not_depend_on_ops_infrastructure =
        noClasses()
                .that().resideInAnyPackage("..ops.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..ops.infrastructure..")
                .because("ops application owns ports and infrastructure implements them");
```

- [ ] **Step 2: Run ArchUnit**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: if any new rule fails, adjust imports/dependencies in the ops implementation instead of weakening the rule.

- [ ] **Step 3: Commit**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
git commit -m "test: guard ops reliability boundaries"
```

### Task 6: Projection Lag Visibility

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionLagPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/ProjectionLagResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/ProjectionOpsController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationServiceTest.java`

**Interfaces:**
- Consumes: `outbox_event` projection topics through infrastructure.
- Produces: `ProjectionGovernanceApplicationService.listProjectionLag(): List<ProjectionLagResult>`.
- Produces: `GET /api/ops/projections/lag`.

- [ ] **Step 1: Write the failing projection governance test**

Create `backend/community-app/src/test/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationServiceTest.java`:

```java
package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectionGovernanceApplicationServiceTest {

    @Test
    void listProjectionLagShouldReturnPortRows() {
        ProjectionLagPort port = mock(ProjectionLagPort.class);
        when(port.listProjectionLag()).thenReturn(List.of(new ProjectionLagResult(
                "projection.search.post",
                "PENDING",
                3L,
                Duration.ofSeconds(42)
        )));
        ProjectionGovernanceApplicationService service = new ProjectionGovernanceApplicationService(port);

        List<ProjectionLagResult> result = service.listProjectionLag();

        assertThat(result).containsExactly(new ProjectionLagResult(
                "projection.search.post",
                "PENDING",
                3L,
                Duration.ofSeconds(42)
        ));
    }
}
```

- [ ] **Step 2: Add projection application types**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/ProjectionLagResult.java`:

```java
package com.nowcoder.community.ops.application.result;

import java.time.Duration;

public record ProjectionLagResult(String projection, String status, long count, Duration oldestAge) {
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionLagPort.java`:

```java
package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.ProjectionLagResult;

import java.util.List;

public interface ProjectionLagPort {

    List<ProjectionLagResult> listProjectionLag();
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationService.java`:

```java
package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProjectionGovernanceApplicationService {

    private final ProjectionLagPort projectionLagPort;

    public ProjectionGovernanceApplicationService(ProjectionLagPort projectionLagPort) {
        this.projectionLagPort = Objects.requireNonNull(projectionLagPort, "projectionLagPort must not be null");
    }

    public List<ProjectionLagResult> listProjectionLag() {
        return projectionLagPort.listProjectionLag();
    }
}
```

- [ ] **Step 3: Add outbox-backed lag adapter**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapter.java`:

```java
package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.ops.application.ProjectionLagPort;
import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Repository
public class OutboxProjectionLagAdapter implements ProjectionLagPort {

    private final JdbcTemplate jdbcTemplate;

    public OutboxProjectionLagAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ProjectionLagResult> listProjectionLag() {
        Instant now = Instant.now();
        return jdbcTemplate.query(
                """
                        select topic, status, count(*) as row_count, min(created_at) as oldest_created_at
                        from outbox_event
                        where topic like 'projection.%'
                          and status in ('PENDING', 'PROCESSING', 'DEAD')
                        group by topic, status
                        order by topic asc, status asc
                        """,
                (rs, rowNum) -> {
                    Timestamp oldest = rs.getTimestamp("oldest_created_at");
                    Duration age = oldest == null ? Duration.ZERO : Duration.between(oldest.toInstant(), now);
                    return new ProjectionLagResult(
                            rs.getString("topic"),
                            rs.getString("status"),
                            rs.getLong("row_count"),
                            age.isNegative() ? Duration.ZERO : age
                    );
                }
        );
    }
}
```

- [ ] **Step 4: Add projection ops controller**

Create `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/ProjectionOpsController.java`:

```java
package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.result.Result;
import com.nowcoder.community.ops.application.ProjectionGovernanceApplicationService;
import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ops/projections")
public class ProjectionOpsController {

    private final ProjectionGovernanceApplicationService projectionGovernanceApplicationService;

    public ProjectionOpsController(ProjectionGovernanceApplicationService projectionGovernanceApplicationService) {
        this.projectionGovernanceApplicationService = projectionGovernanceApplicationService;
    }

    @GetMapping("/lag")
    public Result<List<ProjectionLagResult>> lag() {
        return Result.ok(projectionGovernanceApplicationService.listProjectionLag());
    }
}
```

- [ ] **Step 5: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=ProjectionGovernanceApplicationServiceTest,OutboxOpsControllerTest
```

Expected: projection application test passes; existing ops security test still passes because `/api/ops/**` covers projection routes.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionLagPort.java \
        backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/ProjectionLagResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapter.java \
        backend/community-app/src/main/java/com/nowcoder/community/ops/controller/ProjectionOpsController.java \
        backend/community-app/src/test/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationServiceTest.java
git commit -m "feat: expose projection lag governance"
```

### Task 7: Hot Feed Cache Degradation Metrics

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java`

**Interfaces:**
- Produces: `HotFeedReadMetrics.record(String result, String scope)`.
- Produces metrics: `community_cache_requests_total{cache="hot_feed",result,scope}`.
- Keeps feed API response shape unchanged.

- [ ] **Step 1: Write the failing metrics test**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java` with a focused metrics test:

```java
package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedReadApplicationServiceReliabilityTest {

    @Test
    void hotFeedReadMetricsShouldUseBoundedCacheTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.record("hit", "global");
        metrics.record("fallback", "board");
        metrics.record("degraded", "global");

        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "hit",
                "scope", "global"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "fallback",
                "scope", "board"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "degraded",
                "scope", "global"
        ).count()).isEqualTo(1.0);
    }
}
```

- [ ] **Step 2: Add `HotFeedReadMetrics`**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java`:

```java
package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class HotFeedReadMetrics {

    private final MeterRegistry meterRegistry;

    public HotFeedReadMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    HotFeedReadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String result, String scope) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "community_cache_requests_total",
                Tags.of(
                        "cache", "hot_feed",
                        "result", bounded(result),
                        "scope", bounded(scope)
                )
        ).increment();
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40);
    }
}
```

- [ ] **Step 3: Wire metrics into `FeedReadApplicationService`**

Modify constructors in `FeedReadApplicationService` to accept `HotFeedReadMetrics`; keep existing test constructors by adding overload defaults:

```java
private final HotFeedReadMetrics hotFeedReadMetrics;
```

In the main `@Autowired` constructor add `HotFeedReadMetrics hotFeedReadMetrics` and assign:

```java
this.hotFeedReadMetrics = hotFeedReadMetrics == null ? new HotFeedReadMetrics((io.micrometer.core.instrument.MeterRegistry) null) : hotFeedReadMetrics;
```

In `loadHotPage(...)`, record outcomes:

```java
String scope = boardId == null ? "global" : "board";
try {
    List<UUID> ids = readFeedIds(page == 0 ? cursor : encodedCursor, limit, boardId);
    if (!ids.isEmpty()) {
        hotFeedReadMetrics.record("hit", scope);
        List<PostSummaryResult> items = filterBoardItems(postFeedSummaryLoader.readSummaries(ids), boardId);
        return new LoadedFeedPage(items, hasNextCachedPage(page, limit, boardId), postFeedCache.readRankVersion());
    }
} catch (RuntimeException ex) {
    hotFeedReadMetrics.record("degraded", scope);
}
if (!policyProperties.isLatestFallbackEnabled()) {
    return new LoadedFeedPage(List.of(), false, safeRankVersion());
}
List<DiscussPost> fallbackPosts = listFallbackPosts(page, limit, boardId);
if (fallbackPosts.isEmpty()) {
    hotFeedReadMetrics.record("empty", scope);
    return new LoadedFeedPage(List.of(), false, safeRankVersion());
}
hotFeedReadMetrics.record("fallback", scope);
```

Add helper:

```java
private String safeRankVersion() {
    try {
        return postFeedCache.readRankVersion();
    } catch (RuntimeException ex) {
        hotFeedReadMetrics.record("degraded", "rank_version");
        return policyProperties.getHotRankVersion();
    }
}
```

Replace direct `postFeedCache.readRankVersion()` calls inside `loadHotPage` with `safeRankVersion()`.

- [ ] **Step 4: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FeedReadApplicationServiceReliabilityTest,PostHotFeedProjectionApplicationServiceTest
```

Expected: selected tests pass. If constructor changes break existing `FeedReadApplicationService` tests, add overloads that delegate with `new HotFeedReadMetrics((MeterRegistry) null)`.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java
git commit -m "feat: record hot feed cache reliability metrics"
```

### Task 8: Core Business Slice Regression Tests

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`

**Interfaces:**
- Consumes existing search, notice, growth, and hot-feed projection services.
- Produces regression coverage for replay and duplicate/old event behavior.

- [ ] **Step 1: Add search replay regression**

In `SearchPostProjectionApplicationServiceTest`, add this test:

```java
@Test
void replayedOldEventShouldNotReviveDeletedPost() throws Exception {
    PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
    SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
    SearchPostProjectionApplicationService service =
            new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);
    UUID postId = uuid(205);

    when(postScanQueryApi.getPostProjectionAllowDeleted(postId)).thenReturn(null);

    service.projectPostFromOutbox(new ProjectPostOutboxCommand(postId, "evt-old-create-replayed", 1L));

    verify(searchApplicationService).deletePost(new DeleteIndexedPostCommand(postId));
    verify(searchApplicationService, never()).syncPostProjection(org.mockito.ArgumentMatchers.any());
}
```

- [ ] **Step 2: Add notice duplicate replay regression**

In `NoticeProjectionApplicationServiceTest`, add this test:

```java
@Test
void replayedSourceEventShouldNotCreateDuplicateNotice() {
    NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
    NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
    when(eventRecorder.tryRecord("event-1")).thenReturn(true, false);
    NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);
    ProjectContentNoticeCommand command = commentCommand("event-1");

    projectionService.projectContentEventReliably(command);
    projectionService.projectContentEventReliably(command);

    verify(eventRecorder, times(2)).tryRecord("event-1");
    verify(noticeService, times(1)).createNotice(any(CreateNoticeCommand.class));
}
```

- [ ] **Step 3: Add growth duplicate replay regression**

In `TaskProgressApplicationServiceTest`, add this test:

```java
@Test
void replayedSourceEventShouldNotIncrementTaskTwice() {
    service.processEvent(USER_ID, "PostPublished", "post-evt-replayed", LocalDate.of(2026, 3, 22));
    service.processEvent(USER_ID, "PostPublished", "post-evt-replayed", LocalDate.of(2026, 3, 22));

    assertThat(countProgressRows("DAILY_POST")).isEqualTo(1);
    assertThat(progressValue("DAILY_POST")).isEqualTo(1);
    assertThat(eventLogCount("DAILY_POST", "post-evt-replayed")).isEqualTo(1);
    assertThat(walletTxnCountFor("task:" + USER_ID + ":DAILY_POST:2026-03-22")).isEqualTo(1);
}
```

- [ ] **Step 4: Add hot feed stale event regression**

In `PostHotFeedProjectionApplicationServiceTest`, add this test:

```java
@Test
void outOfOrderProjectionShouldNotRegressVersion() {
    PostContentRepository postContentRepository = mock(PostContentRepository.class);
    LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
    PostFeedCache postFeedCache = mock(PostFeedCache.class);
    PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
    PostDetailCache postDetailCache = mock(PostDetailCache.class);
    PostCounterCache postCounterCache = mock(PostCounterCache.class);
    PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
    HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
    PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
            postContentRepository,
            likeQueryPort,
            postFeedCache,
            postSummaryCache,
            postDetailCache,
            postCounterCache,
            postHotnessDomainService,
            policyProperties(),
            projectionGuard
    );
    HotFeedProjectionGuard.ProjectionAttempt accepted = HotFeedProjectionGuard.ProjectionAttempt.accepted(
            uuid(230),
            "evt-new",
            20L,
            "token-new"
    );
    HotFeedProjectionGuard.ProjectionAttempt stale = HotFeedProjectionGuard.ProjectionAttempt.rejected(
            uuid(230),
            "evt-old",
            10L
    );
    DiscussPost post = post(uuid(230), uuid(30), 0, 10.0);
    when(projectionGuard.tryBegin(uuid(230), "evt-new", 20L)).thenReturn(accepted);
    when(projectionGuard.tryBegin(uuid(230), "evt-old", 10L)).thenReturn(stale);
    when(postContentRepository.getByIdAllowDeleted(uuid(230))).thenReturn(post);
    when(likeQueryPort.countPostLikes(uuid(230))).thenReturn(2L);
    when(postHotnessDomainService.recomputeScore(post, 2L, 1.0)).thenReturn(14.0);
    when(projectionGuard.isCurrent(accepted)).thenReturn(true);

    service.project(new ProjectPostHotFeedCommand(uuid(230), uuid(30), 1.0, "evt-new", 20L));
    service.project(new ProjectPostHotFeedCommand(uuid(230), uuid(30), 1.0, "evt-old", 10L));

    verify(postFeedCache).upsertGlobalHot(uuid(230), 14.0, "hot-v2");
    verify(postHotnessDomainService, times(1)).recomputeScore(post, 2L, 1.0);
    verify(projectionGuard).commit(accepted);
    verify(projectionGuard, never()).commit(stale);
}
```

- [ ] **Step 5: Run business slice tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,NoticeProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,PostHotFeedProjectionApplicationServiceTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java
git commit -m "test: cover bbs reliability replay slices"
```

### Task 9: Documentation And Final Verification

**Files:**
- Modify: `docs/handbook/reliability.md`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/observability.md`
- Modify: `docs/superpowers/specs/2026-07-07-bbs-reliability-platform-design.md`

**Interfaces:**
- Consumes implemented routes and metrics from Tasks 1-8.
- Produces operator-facing docs and verifies the implementation as a coherent platform.

- [ ] **Step 1: Update reliability handbook**

In `docs/handbook/reliability.md`, add a section after `DB Outbox`:

```markdown
## Outbox Governance

Admin reliability governance APIs live under `/api/ops/outbox/**` and require `ROLE_ADMIN`.

- `GET /api/ops/outbox/backlog` returns `PENDING`, `PROCESSING`, and `DEAD` counts grouped by topic.
- `GET /api/ops/outbox/events` filters by `status`, `topic`, `eventId`, `createdFrom`, `createdTo`, and `limit`.
- `POST /api/ops/outbox/events/{outboxId}/replay` only requeues `DEAD` rows to `PENDING`.

Replay never calls handlers directly. It resets retry count, sets `next_retry_at` to now, records the operator reason in `last_error`, and lets the normal `OutboxWorker` claim and process the row.

Replay is rejected when the row is not `DEAD`, the topic has no registered handler, the payload is blank, or the operator did not provide a reason.
```

- [ ] **Step 2: Update operations runbook**

In `docs/handbook/operations.md`, add a runbook section:

```markdown
## Outbox DEAD Triage

1. Query backlog with `GET /api/ops/outbox/backlog`.
2. List terminal rows with `GET /api/ops/outbox/events?status=DEAD&topic=<topic>&limit=50`.
3. Inspect `eventId`, `topic`, `eventKey`, `lastError`, `traceId`, `createdAt`, and `updatedAt`.
4. Fix the dependency or handler issue before replay.
5. Requeue a single row with `POST /api/ops/outbox/events/{outboxId}/replay` and a non-empty `reason`.
6. Confirm the row moves to `SUCCEEDED` or returns to `DEAD` with a new `lastError`.

Prefer owner-current-state rebuilds for search and hot-feed read models when event payloads are stale or incompatible.
```

- [ ] **Step 3: Update observability handbook**

In `docs/handbook/observability.md`, add metric contracts:

```markdown
New reliability governance metrics:

- `community_outbox_replay_total{topic,result}`
- `community_cache_requests_total{cache="hot_feed",result,scope}`

Allowed `community_outbox_replay_total.result` values are `REPLAYED`, `MANUAL_REPAIR_REQUIRED`, and `NOT_REQUEUED`.

Allowed `community_cache_requests_total.result` values are `hit`, `fallback`, `empty`, and `degraded`.
```

- [ ] **Step 4: Update spec status**

At the bottom of `docs/superpowers/specs/2026-07-07-bbs-reliability-platform-design.md`, add:

```markdown
## Implementation Status

The first implementation wave covers:

- outbox governance query and replay primitives
- `/api/ops/outbox/**` admin governance APIs
- projection lag visibility
- hot-feed cache reliability metrics
- replay regression tests for search, notice, growth, and hot feed
```

- [ ] **Step 5: Run final verification**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest,JdbcOutboxEventStoreGovernanceTest,OutboxGovernanceApplicationServiceTest,JdbcOutboxGovernanceAdapterTest,ReliabilityGovernanceMetricsTest,OutboxOpsControllerTest,ProjectionGovernanceApplicationServiceTest,FeedReadApplicationServiceReliabilityTest,SearchPostProjectionApplicationServiceTest,NoticeProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,PostHotFeedProjectionApplicationServiceTest'
```

Expected: all selected tests pass.

Then run:

```bash
git status --short
```

Expected: only intentional documentation updates remain unstaged before the final commit.

- [ ] **Step 6: Commit**

```bash
git add docs/handbook/reliability.md \
        docs/handbook/operations.md \
        docs/handbook/observability.md \
        docs/superpowers/specs/2026-07-07-bbs-reliability-platform-design.md
git commit -m "docs: document reliability governance operations"
```

## Self-Review

Spec coverage:

- Design and roadmap are covered by Tasks 1-9 and the existing approved spec.
- Backend governance is covered by Tasks 1-6.
- Ops governance APIs, replay, metrics, and runbook are covered by Tasks 3, 4, 6, and 9.
- High-concurrency read/write reliability is covered by Tasks 7 and 8.
- DDD owner boundaries are covered by Tasks 2, 4, and 5.

Placeholder scan:

- The plan avoids unresolved placeholder markers and unbounded "handle edge cases" instructions.
- Task 8 provides concrete test bodies and uses helper methods that are already present in the target test classes.

Type consistency:

- `OutboxEventQuery`, `OutboxEventView`, and `OutboxBacklogRow` are produced in Task 1 and consumed in Task 3.
- `FindOutboxEventsCommand`, `ReplayOutboxEventCommand`, and result records are produced in Task 2 and consumed in Tasks 3 and 4.
- `/api/ops/**` is produced in Task 4 and protected by `OpsSecurityRules`.
