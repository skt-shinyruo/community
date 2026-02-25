# Technical Design: 后端架构升级（五类系统性问题治理）

## Technical Solution

### Core Technologies
- Spring Boot 3 / Java 17
- Spring Cloud Gateway（WebFlux）
- Spring Security（Resource Server JWT）
- MyBatis + MySQL（业务存储）
- Redis（缓存/限流/部分业务存储）
- Kafka（异步事件，DLQ）
- Spring Data Elasticsearch（search storage = es）

### Implementation Key Points
1. **计数原子化：** 对 `comment_count` 等计数字段改为 `UPDATE ... SET x = x ± 1`（或等价原子写）策略；仅在校准/修复场景使用全量 COUNT。
2. **网关 traceId 统一写入：** 在 gateway 所有写响应路径（security exception / rate limit / origin guard 等）统一从请求头 `X-Trace-Id` 回填 `Result.traceId`，避免 reactive 下 ThreadLocal 为空。
3. **traceId 规范化：** 对外输入仅接受 32 位 hex（或从 traceparent 提取），避免日志污染与高基数指标；并统一生成/透传 `traceparent`。
4. **可信代理边界：** 增加 `gateway.trusted-proxy.*` 配置；默认不信任 `X-Forwarded-For`，仅在请求来自可信代理网段时解析。
5. **内部调用治理：** 抽取 `common` 层的内部调用标准（headers 注入、超时、Result 解包、错误映射、指标与日志），并逐步迁移各服务的 `*Client`。
6. **幂等治理统一：** 消费端统一 insert-first 幂等锁（唯一约束为准），补齐按 `consumed_at` 的清理任务与索引，避免无限增长。
7. **Outbox（P1）：** 对 DB 事务型事件生产者（至少 content-service）引入 outbox 表，事务内写 outbox，事务后由 relay worker 可靠投递 Kafka（含重试、退避、指标）。
8. **ES 重建索引无停机：** reindex 构建新索引（带时间戳/版本号），回填完成后切换 alias；失败不影响线上查询；保留旧索引作为回滚窗口。

## Architecture Design
```mermaid
flowchart TD
    A[Client] -->|HTTP| G[gateway]
    G -->|/api/**| S1[content-service]
    G -->|/api/**| S2[search-service]
    G -->|/api/**| S3[message-service]

    subgraph Trace & Security Baseline
      G --> T1[TraceIdGlobalFilter\nnormalize + traceparent]
      G --> T2[TrustedProxyIpResolver\ntrust boundary]
      G --> T3[UnifiedErrorWriter\nResult.traceId]
    end

    subgraph Async Event Governance
      S1 -->|tx commit| O1[outbox_event\n(DB)]
      O1 --> R1[outbox relay worker]
      R1 -->|Kafka| K[(Kafka topics + DLQ)]
      K --> C1[message-service consumer\nidempotent]
      K --> C2[search-service consumer\nidempotent]
    end
```

## Architecture Decision ADR

### ADR-001: 采用 Outbox 作为 DB 事务型事件生产者的可靠投递方案
**Context:** 当前生产端使用 after-commit 发送 Kafka，解决了“回滚但事件已发出”，但仍可能出现“已提交但事件丢失”（Kafka 不可用/发送失败）。
**Decision:** 对 DB 事务型生产者（至少 content-service）引入 outbox_event：事务内写 outbox，异步 relay 可靠投递 Kafka，并具备重试与可观测。
**Rationale:** Outbox 是业界成熟的最终一致可靠投递方案，避免引入分布式事务；可分阶段灰度落地。
**Alternatives:** 仅 after-commit + 重试 → 仍存在丢事件窗口；使用事务消息/Exactly-once → 复杂度与成本更高。
**Impact:** 增加 DB 写入与运维复杂度；需要表结构、relay worker、监控告警与演练脚本。

### ADR-002: 建立 “trusted proxies” 模型来解析 Forwarded/XFF
**Context:** 直接信任 `X-Forwarded-For` 会被客户端伪造绕过限流/UV；但在真实部署中可能存在反向代理需要解析。
**Decision:** 默认不信任 XFF；仅当 remoteAddress 属于配置的可信代理网段时解析，并提供审计日志/指标。
**Rationale:** 明确安全边界，避免默认不安全；配置化适配不同部署拓扑。
**Alternatives:** 永远信任 XFF → 安全风险高；永远不信任 → 在多级代理场景丢失真实 IP（可通过配置解决）。
**Impact:** 需要新增配置与灰度策略；配置错误可能误限流，需要可观测与回滚。

### ADR-003: search-service ES 重建索引采用 alias/蓝绿切换
**Context:** 当前 `clear()` 通过删除/重建索引实现，存在查询不可用窗口，且失败时难回滚。
**Decision:** 固定 alias（如 `community_posts`）作为线上读写入口；reindex 构建新索引（`community_posts_vYYYYMMDDHHMM`），完成后切换 alias。
**Rationale:** 可用性更高、失败可回滚、支持保留旧索引用于演练/回放。
**Alternatives:** clear-and-rebuild → 有窗口；双写+回填 → 复杂度更高（可作为后续优化）。
**Impact:** 需要额外索引管理代码与运维约定；需监控索引数量与磁盘占用。

### ADR-004: 内部同步调用治理优先“统一规范 + 渐进迁移”，不一次性替换所有 client
**Context:** 各服务内部 client 实现风格不一致，若一次性重构改动面过大，风险高。
**Decision:** 在 common 抽取规范与基础能力（headers 注入、Result 解包、错误映射、指标），分模块迁移；保留旧配置兼容并逐步弃用。
**Rationale:** 降低一次性改造风险；让每次迁移都有独立可验证的收益。
**Alternatives:** 统一切换到 Feign/Resilience4j 等 → 引入新依赖与学习成本，且改动面更大。
**Impact:** 需要维护一段时间的双配置/双实现，并在知识库与部署配置中清晰标注。

## API Design
- 以不破坏现有对外 API 为原则：
  - 对外接口路径与参数不变（/api/**）
  - 行为增强：错误响应体补齐 traceId、限流更准确、计数更正确
- internal 接口可能新增运维能力（可选）：
  - Outbox replay / health / metrics（默认关闭或仅 internal-token 访问）
  - search reindex 支持无停机模式（保持旧入口兼容）

## Data Model
```sql
-- 1) Outbox（示例：content-service 所在 schema）
create table if not exists outbox_event (
  id bigint auto_increment primary key,
  event_id varchar(64) not null,
  topic varchar(128) not null,
  message_key varchar(128) null,
  payload_json text not null,
  status varchar(32) not null default 'NEW',
  retry_count int not null default 0,
  next_retry_at timestamp null,
  published_at timestamp null,
  created_at timestamp null default current_timestamp,
  unique key uk_outbox_event_id (event_id),
  key idx_outbox_status_next (status, next_retry_at),
  key idx_outbox_created_at (created_at)
);

-- 2) 幂等表索引（示例：search-service）
create index if not exists idx_search_consumed_event_time on search_consumed_event(consumed_at);

-- 3) 幂等表索引（示例：message-service）
create index if not exists idx_consumed_event_time on consumed_event(consumed_at);
```

## Security and Performance
- **Security:**
  - 默认不信任 XFF，trusted proxies 白名单化
  - traceId 规范化，避免日志注入/高基数
  - internal API 继续使用 `X-Internal-Token`，并避免外部可伪造 header 影响内部语义
- **Performance:**
  - 计数原子化减少 count 查询与锁竞争
  - 幂等 insert-first 降低 DB 读放大
  - ES alias reindex 避免线上查询中断
  - Outbox relay 需控制扫描批次与重试退避，避免对 DB/Kafka 造成尖峰

## Testing and Deployment
- **Testing:**
  - content-service：并发评论计数正确性测试（并发/交错场景）
  - gateway：401/403/429 错误响应体 traceId 回归测试
  - gateway：XFF 伪造绕过限流的回归测试（trusted proxies 开关）
  - search-service：幂等 insert-first 与清理任务测试；ES alias reindex 的演练测试（可选集成）
  - outbox：Kafka 宕机/恢复后补投递演练（集成测试或演练脚本）
- **Deployment:**
  1. 先落地 traceId/IP/计数原子化（低风险、收益直接）
  2. 再统一幂等 insert-first + 清理策略（可灰度）
  3. 最后引入 Outbox 与 ES alias reindex（需 DB schema + 运维配合 + 演练）
