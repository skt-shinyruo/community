# 任务清单: rpc-decycle-projection-hardening

```yaml
@feature: rpc-decycle-projection-hardening
@created: 2026-02-25
@status: in_progress
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: in_progress | 进度: 9/24 (38%) | 更新: 2026-02-25 16:12:15
当前: 完成 3.5（message-service 扫描能力上收 ops 平面）
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 9 | 0 | 6 | 24 |

---

## 任务列表

### 1. 依赖图 SSOT（先把“允许什么”说清楚）

- [√] 1.1 输出 Dubbo 同步依赖图（基于 `@DubboReference` 扫描）并归档为 SSOT 文档（建议：`docs/DEPENDENCY_POLICY.md` 或 `.helloagents/wiki/dependency-policy.md`）
- [√] 1.2 定义同步依赖 allowlist（服务→服务边集合）与禁令（例如：写路径禁止新增同步依赖；禁止形成环）
- [√] 1.3 把“投影缺失策略”制度化为一张表（fail-closed / read-repair / fail-open），并为每个关键投影标注策略与理由

### 2. 门禁测试（把架构意图变成 CI 可验证事实）

- [-] 2.1 新增 gate test：从源码扫描构建 Dubbo 同步依赖图，要求“无环 + 边属于 allowlist”
  - 建议位置：`contracts-core/src/test/java/com/nowcoder/community/contracts/arch/`
- [-] 2.2 新增 gate test：关键写路径 package 禁止出现 `@DubboReference`（或必须被显式标记为 read-repair 且带开关）
  - 示例目标：`social-service/.../like/**`、`content-service/.../comment/**`、`message-service/.../service/**`（按实际写路径范围细化）
- [-] 2.3 新增 gate test：所有 `@DubboReference` 必须显式 `retries=0` 且设置合理 `timeout`（避免默认重试放大故障）
- [-] 2.4 新增 gate test：bootstrap/backfill 类型扫描 RPC 客户端不得被请求处理链路直接调用（需通过 job/ops 触发）
- [-] 2.5 新增 gate test：禁止在 `common` / `infra-*` / `*-api` 中出现 `@DubboService`（RPC provider 只能存在于 `*-service`）

### 3. 同步依赖削减（按服务拆解）

> 原则：优先削减“请求路径上的同步依赖”；运维平面（`ops-service`）与可丢弃链路（`gateway` analytics）可保留。

- [√] 3.1 `message-service`：将 `UserServiceClient` 的用户摘要/用户名解析从“RPC 依赖”迁移为“用户摘要投影”（事件驱动）
  - 当前证据：`message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`
- [√] 3.2 `message-service`：将处罚状态校验的 read-repair 机制标准化（限流/超时/熔断策略明确），并与“投影缺失策略表”对齐
  - 当前证据：`message-service/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`
- [√] 3.3 `content-service`：将处罚状态校验的 read-repair 机制标准化（同 3.2）
  - 当前证据：`content/content-service/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`
- [√] 3.4 `content-service`：将 `SocialBlockScanClient` / `SocialLikeScanClient` 的扫描能力上收至 ops 平面（避免业务服务持有扫描依赖）
  - 当前证据：`ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`、`content/content-service/src/main/java/com/nowcoder/community/content/rpc/ContentLikeProjectionOpsRpcServiceImpl.java`、`content/content-service/src/main/java/com/nowcoder/community/content/rpc/ContentBlockProjectionOpsRpcServiceImpl.java`
- [√] 3.5 `message-service`：将 `SocialBlockScanClient` 的扫描能力上收至 ops 平面（同 3.4）
  - 当前证据：`ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`、`message-service/src/main/java/com/nowcoder/community/message/rpc/MessageBlockProjectionOpsRpcServiceImpl.java`
- [ ] 3.6 `user-service`：评估并实施“用户主页聚合读”去耦路径（两选一或组合）
  - A: 保留 `user-service -> social-service` 单一聚合 RPC，但固化 fail-open 与观测（现状）
  - B: user-service 消费 social 事件物化计数投影，减少或移除该 RPC（更激进）
  - 当前证据：`user/user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`
- [ ] 3.7 `search-service`：明确 `reindex` 的同步依赖边界（仅 ops 触发、禁止请求路径触发 full scan），并评估将扫描迁移为事件回放/离线任务的可行性
  - 当前证据：`search/search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`

### 4. 写路径关键投影（可用性风险前移的系统性治理）

- [ ] 4.1 `social-service`：为 `ContentEntityProjection` 建立“投影缺失修复闭环”（至少一种：read-repair / ops backfill / 受控 fallback）
  - 当前证据：`social/social-service/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`（当前 fail-closed）
- [ ] 4.2 `social-service`：补齐 `ContentEntityProjection` 的健康/延迟指标与告警（miss/incomplete/lag 分维度）
  - 当前证据：`social/social-service/src/main/java/com/nowcoder/community/social/kafka/ContentEventConsumer.java`、`ContentEntityProjectionRepository`
- [ ] 4.3 对所有“写路径关键投影”统一补齐：replay/backfill/runbook（建议集中到 `ops-service`）
  - 现有基础：`ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`（outbox replay、reindex、backfill）
- [ ] 4.4 抽取并统一“read-repair 基础能力”（限流/超时/metrics/开关），减少各服务自行实现导致的漂移
  - 参考现有实现：`content/content-service/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`、`message-service/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`

### 5. 运维平面收敛（把扫描/重建/纠偏集中化）

- [ ] 5.1 `ops-service`：扩展运维能力，覆盖关键投影的 backfill/rebuild（不仅限 outbox replay）
- [ ] 5.2 为每个投影提供“健康检查 + 修复动作”的统一入口（避免到处 ssh/临时脚本）

### 6. 验证与演练（避免方案只停留在文档）

- [-] 6.1 增加“依赖环回归”演练用例：人为引入一条禁止边时 CI 必须失败（按需求变更：不做门禁演练）
- [ ] 6.2 增加“投影滞后/缺失”演练用例：关键写路径不应无限 503，且具备可观测与可修复路径
- [√] 6.3 跑通 `mvn test`（根目录）与关键模块单测，确保变更不引入外部依赖（保持 unit-only 测试约定）

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-02-25 11:50:38 | 1.1 | [√] | 新增 `docs/DEPENDENCY_POLICY.md`（依赖图 + 证据清单） |
| 2026-02-25 11:50:38 | 1.2 | [√] | 在 SSOT 文档中固化 allowlist + 禁令，并与门禁测试对齐 |
| 2026-02-25 11:50:38 | 1.3 | [√] | 在 SSOT 文档中补齐投影缺失策略表（fail-closed/read-repair/fail-open） |
| 2026-02-25 11:50:38 | 2.1 | [√] | 新增 `DubboSyncDependencyGateTest`（allowlist + 无环） |
| 2026-02-25 11:50:38 | 2.2 | [√] | 新增 `NoDubboReferenceInCriticalWritePathTest`（关键写路径包防回潮） |
| 2026-02-25 11:50:38 | 2.3 | [√] | 新增 `DubboReferenceConfigGateTest`（retries=0 + timeout） |
| 2026-02-25 11:53:53 | 2.5 | [√] | 新增 `NoDubboServiceOutsideServiceModulesTest`，并补充 SSOT 文档禁令说明 |
| 2026-02-25 12:07:53 | 1.1 | [√] | 按需求变更：依赖政策 SSOT 迁移到 `.helloagents/project.md`，撤销 repo 内 `docs/DEPENDENCY_POLICY.md` |
| 2026-02-25 12:07:53 | 2.1 | [-] | 按需求变更：移除 Dubbo 同步依赖门禁测试，仅保留 KB 说明 |
| 2026-02-25 12:07:53 | 2.2 | [-] | 按需求变更：移除关键写路径 `@DubboReference` 门禁测试，仅保留 KB 说明 |
| 2026-02-25 12:07:53 | 2.3 | [-] | 按需求变更：移除 `@DubboReference` 参数强制门禁测试，仅保留 KB 说明 |
| 2026-02-25 12:07:53 | 2.5 | [-] | 按需求变更：移除 `@DubboService` 位置门禁测试，仅保留 KB 说明 |
| 2026-02-25 12:37:26 | 2.4 | [-] | 按需求变更：不再新增/保留任何门禁测试，仅保留 KB 说明 |
| 2026-02-25 12:37:26 | 3.1 | [√] | `message-service` 用户摘要投影：新增 user topic/type/payload + consumer + projection repo + schema；移除用户摘要/用户名解析 RPC；同步更新 `docs/SYSTEM_DESIGN.md` 与 `scripts/kafka-reset-topics.sh` |
| 2026-02-25 12:45:15 | 6.3 | [√] | `mvn test`（根目录）通过：BUILD SUCCESS |
| 2026-02-25 14:34:21 | 3.2 | [√] | 标准化 message 私信处罚状态 read-repair：加入 per-user 冷却、全局限流、熔断与 inflight single-flight；`UserModerationClient` 固化 fail-closed |
| 2026-02-25 14:56:08 | 3.3 | [√] | 标准化 content 发言处罚状态 read-repair：加入 per-user 冷却、全局限流、熔断与 inflight single-flight；`UserModerationClient` 固化 fail-closed |
| 2026-02-25 15:52:07 | 3.4 | [√] | content likes/blocks 扫描能力上收 ops-service：content-service 移除 scan Dubbo 依赖与本地 bootstrap/backfill；新增投影写入型 ops RPC 与 ops orchestrate endpoints |
| 2026-02-25 16:12:15 | 3.5 | [√] | message blocks 扫描能力上收 ops-service：message-service 移除 scan Dubbo 依赖与本地 bootstrap；新增 message-api + 投影写入型 ops RPC 与 ops orchestrate endpoint |

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等

- 修复 `contracts-core` 既有门禁测试 `NoContractsRuntimeLeakTest` 的 repo root 识别逻辑，使其在 `mvn test -pl contracts-core -am` 下可稳定运行。
- 用户明确要求“不需要门禁”，因此本方案中的 Dubbo 门禁类任务已撤销（标记为 [-]），依赖政策与投影缺失策略仅写入知识库 SSOT。
- 按需求变更：移除仓库内现存架构/质量门禁测试（`contracts-core/common/gateway/content-service/message-service`），避免“门禁漂移”与误伤；相关约束仅在 KB 记录与 code review 约定中执行。
