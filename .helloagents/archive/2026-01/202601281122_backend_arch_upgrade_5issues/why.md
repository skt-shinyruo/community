# Change Proposal: 后端架构升级（修复 5 类系统性问题：并发一致性 / traceId / IP 信任 / 内部调用治理 / 事件治理）

## Requirement Background
当前后端为 Spring Boot 3 微服务架构（gateway + 多业务服务），已具备统一 Result、错误码、internal-token 与 Kafka 基线能力，但在“并发正确性、安全边界、可观测性一致性、跨服务韧性、事件治理”上仍存在系统性缺口：

1. **并发一致性（content-service）**：评论数通过 `count -> set` 回写，存在并发丢更新风险，线上流量越大偏差越明显。
2. **可观测性（gateway）**：网关在 401/403 等错误响应时，Result.body 中 traceId 可能缺失（reactive 下 ThreadLocal 不可用），导致前端与排障链路断裂。
3. **安全边界（gateway）**：限流/统计等依赖 IP 的逻辑直接信任 `X-Forwarded-For`，在缺少“可信代理边界”时容易被伪造绕过。
4. **稳定性（内部同步调用）**：各服务内部 HTTP client 实现风格与错误映射不一致，缺少统一的降级语义与可观测指标，易引发级联失败与排障困难。
5. **事件治理（Kafka / search-service）**：消费者幂等表实现存在非最优模式（count→insert / 无清理策略），search reindex 对 ES 采用 clear-and-rebuild 存在可用性窗口；生产端仅有 after-commit，仍可能出现“已提交但事件丢失”的问题（需 P1 Outbox 补齐）。

## Change Content
1. **并发一致性升级：** 评论数/计数类字段改为“原子增量/减量”策略，避免 `count->set` 并发丢更新。
2. **traceId 一致性升级：** gateway 侧所有错误响应（401/403/429/5xx 等）保证 `Result.traceId` 与 `X-Trace-Id` 一致；并统一 traceId 规范化与注入策略。
3. **可信代理边界：** 建立 `X-Forwarded-For` 信任模型（trusted proxies），默认不信任外部伪造头，避免绕过限流/UV。
4. **内部调用治理：** 抽取统一的 internal HTTP client 规范（超时/错误映射/指标/traceId/internal-token 注入/降级语义），并逐步收敛各服务实现。
5. **事件治理升级：**
   - 生产端（DB 事务型）：引入 Outbox（P1）确保可靠投递（Kafka 宕机/抖动不丢事件）。
   - 消费端：统一幂等为 insert-first，并补齐清理策略（TTL/归档）避免无限增长。
   - search reindex：ES 场景采用 alias/蓝绿索引切换，消除 clear 导致的可用性窗口；失败可回滚到旧索引。

## Impact Scope
- **Modules:**
  - gateway
  - content-service
  - search-service
  - common
  - message-service（对齐幂等/清理策略）
  - deploy（DB schema / nacos-config / 运维脚本）
- **Files:**
  - gateway: `GatewayRateLimitGlobalFilter` / `ReactiveSecurityExceptionHandler` / 新增 trusted-proxy 支撑
  - content-service: `CommentService` / mapper XML 或 mapper 接口 / 相关测试
  - search-service: `SearchConsumedEventStore` / ES reindex 逻辑 / 相关测试与配置
  - common: traceId/Result/内部调用基础组件（如需）
  - deploy: `deploy/mysql-init/*.sql`、`deploy/nacos-config/*.yaml`
- **APIs:**
  - 不新增/不破坏主要对外 API（/api/**），以“行为增强”为主（traceId、限流、正确性）。
  - internal API 可能新增/增强（例如 Outbox 运维接口、search reindex 的无停机模式参数化），需保持默认兼容。
- **Data:**
  - 新增 outbox 表（DB 事务型生产者侧，至少覆盖 content-service）
  - 幂等表（consumed_event/search_consumed_event）增加清理策略所需索引/任务
  - ES：引入 alias/蓝绿索引命名约定（community_posts -> community_posts_v*）

## Core Scenarios

### Requirement: R1-comment-count-atomicity
<a id="r1-comment-count-atomicity"></a>
**Module:** content-service

计数类字段（以 `post.comment_count` 为代表）在并发写入下保持正确性，不因并发导致长期偏差。

#### Scenario: R1S1-add-comment-concurrent
<a id="r1s1-add-comment-concurrent"></a>
当多个用户并发对同一帖子发表评论：
- `comment_count` 最终值与实际评论数一致（至少满足单调递增与无丢更新）。
- 写路径不依赖 `count->set` 回写。

#### Scenario: R1S2-delete-comment-concurrent
<a id="r1s2-delete-comment-concurrent"></a>
当并发删除/撤回评论与新增评论交错发生：
- `comment_count` 最终值正确或可被一致性校准任务纠偏（若引入）。
- 不出现负数/明显偏差。

---

### Requirement: R2-gateway-traceid-in-error-body
<a id="r2-gateway-traceid-in-error-body"></a>
**Module:** gateway / common / frontend

网关在 reactive 环境下，所有错误响应体中的 `Result.traceId` 必须可用，且与 `X-Trace-Id` 一致，保证前端与排障闭环。

#### Scenario: R2S1-401-403-body-has-traceid
<a id="r2s1-401-403-body-has-traceid"></a>
当请求触发 401/403（未登录/无权限）：
- 响应 header 必含 `X-Trace-Id` / `traceparent`
- 响应 body `Result.traceId` 与 header 中 `X-Trace-Id` 一致

#### Scenario: R2S2-rate-limit-429-body-has-traceid
<a id="r2s2-rate-limit-429-body-has-traceid"></a>
当请求触发限流（429）或限流组件 fail-closed（503）：
- 响应 body 仍携带 traceId（用于定位 ruleId、来源 IP、用户等）

---

### Requirement: R3-trusted-proxy-forwarded-headers
<a id="r3-trusted-proxy-forwarded-headers"></a>
**Module:** gateway

建立可信代理边界：只有来自可信代理的 `X-Forwarded-For/Forwarded` 才参与“真实客户端 IP”判定，否则以 remoteAddress 为准，避免 IP 伪造绕过限流/统计。

#### Scenario: R3S1-prevent-xff-spoof-bypass-rate-limit
<a id="r3s1-prevent-xff-spoof-bypass-rate-limit"></a>
当客户端伪造 `X-Forwarded-For`：
- 在未配置 trusted proxies 时，网关不信任该头，不可绕过 IP 限流
- 在配置 trusted proxies 时，仅当请求来自可信代理网段才解析 XFF

#### Scenario: R3S2-analytics-uv-uses-trusted-ip
<a id="r3s2-analytics-uv-uses-trusted-ip"></a>
UV 采集使用与限流一致的“可信 IP 提取”逻辑：
- 避免伪造 XFF 刷 UV
- 避免异常高基数导致内存去重集合膨胀

---

### Requirement: R4-internal-http-client-governance
<a id="r4-internal-http-client-governance"></a>
**Module:** common / auth-service / content-service / message-service / user-service / search-service

跨服务同步 HTTP 调用统一具备：确定性超时、统一错误映射、必要的 traceId/internal-token 注入、指标与日志；并明确哪些调用可降级、哪些必须 fail-fast/fail-closed。

#### Scenario: R4S1-standard-timeout-metrics-error-mapping
<a id="r4s1-standard-timeout-metrics-error-mapping"></a>
当下游抖动/不可用：
- 调用方在超时阈值内返回（不被挂死）
- 错误码/信息一致（便于网关与前端统一处理）
- 指标可观测（成功/失败/超时/降级次数与耗时）

#### Scenario: R4S2-explicit-fallback-policy-per-call
<a id="r4s2-explicit-fallback-policy-per-call"></a>
对于写路径中的跨服务校验（例如禁言/拉黑等）：
- 明确“安全优先 vs 可用性优先”的默认策略与可配置开关
- 降级行为可观测且可演练

---

### Requirement: R5-event-governance-outbox-idempotency-reindex
<a id="r5-event-governance-outbox-idempotency-reindex"></a>
**Module:** content-service / search-service / message-service / common / deploy

事件链路具备“可靠投递 + 幂等消费 + 可回放/可重建 + 无停机重建（ES）”能力，降低最终一致链路的系统性风险。

#### Scenario: R5S1-outbox-reliable-delivery-for-db-producers
<a id="r5s1-outbox-reliable-delivery-for-db-producers"></a>
当 DB 事务已提交但 Kafka 不可用/发送失败：
- 事件不会丢失（落库 outbox）
- Kafka 恢复后由 relay worker 补投递
- 下游幂等保证不会产生重复副作用

#### Scenario: R5S2-insert-first-idempotency-with-ttl
<a id="r5s2-insert-first-idempotency-with-ttl"></a>
消费者侧幂等：
- 使用 insert-first（唯一约束为准）避免 count→insert 竞态与额外读
- 有清理策略（TTL/归档/按时间分区）避免表无限增长

#### Scenario: R5S3-search-reindex-zero-downtime-with-alias
<a id="r5s3-search-reindex-zero-downtime-with-alias"></a>
search-service 在 ES 存储模式下重建索引：
- 构建新索引并回填数据，完成后切换 alias
- 重建失败不影响线上查询（仍走旧 alias）
- 重建完成后可选择清理旧索引（保留回滚窗口）

## Risk Assessment
- **Risk:** Outbox + alias 重建引入数据与运维复杂度，若无灰度/开关可能影响线上稳定性。
  - **Mitigation:** 分阶段落地（先 traceId/IP/并发正确性，再幂等治理，再 Outbox/alias）；提供开关与回滚路径；补齐监控告警与演练脚本。
- **Risk:** trusted proxy 配置错误导致真实 IP 获取异常（误限流/误统计）。
  - **Mitigation:** 默认 fail-safe（不信任 XFF）；仅在明确部署拓扑下启用；提供灰度与日志观测。
- **Risk:** 幂等表清理策略不当导致误删与重复消费副作用。
  - **Mitigation:** 以“足够长 TTL + 业务幂等”双保险；清理任务先 dry-run/指标观测再启用删除。
