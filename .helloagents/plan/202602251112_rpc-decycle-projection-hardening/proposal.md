# 变更提案: rpc-decycle-projection-hardening

## 元信息
```yaml
类型: 架构改造/重构
方案类型: implementation
优先级: P0
状态: 草稿
创建: 2026-02-25
```

---

## 1. 需求

### 背景（问题陈述）

当前仓库已经具备“微服务工程”的基础形态（`gateway` + 多个 `*-service` + Dubbo RPC + Kafka/Outbox），并且通过门禁测试在主动防止回归（例如 `contracts-core/src/test/java/com/nowcoder/community/contracts/arch/NoCrossServicePomDependencyTest.java`）。

但从代码扫描看，系统仍存在两类 P0 风险，且它们在工程演进中**非常容易复发/放大**：

#### P0-1：服务边界容易滑向“分布式单体”

**证据：Dubbo 同步依赖存在且分散**

仓库当前存在 15 处 `@DubboReference`（`rg "@DubboReference"` 统计）。其形成的服务间同步依赖边如下（按模块归类）：

- `auth-service` → `user-service`
  - `auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java`
  - `@DubboReference UserInternalRpcService`（登录/会话/注册/激活/refresh token 相关）
- `user-service` → `social-service`
  - `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`
  - `@DubboReference SocialReadRpcService`（用户主页计数与关注状态聚合）
- `content-service` → `user-service`
  - `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`
  - `@DubboReference UserModerationRpcService`（发言权限/处罚状态）
- `message-service` → `user-service`
  - `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationClient.java`（私信权限/处罚状态）
- `search-service` → `content-service`
  - `search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`
  - `@DubboReference ContentScanRpcService`（reindex 扫描帖子）
- `gateway` → `analytics-service`
  - `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`
  - `@DubboReference InternalAnalyticsRpcService`（UV/DAU 可丢弃采集链路）
- `ops-service` → `search/content/message/social/user`
  - `ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`
  - 通过 Dubbo 聚合高风险运维能力（reindex/outbox replay/backfill）

**历史教训：写路径曾出现双向同步依赖环**

历史方案包记录显示曾出现 `content-service ↔ social-service` 的写路径双向同步依赖，并已通过“事件投影”拆环（参考 `.helloagents/archive/2026-02/202602122321_break_content_social_rpc_cycle/why.md`）。

这说明系统的“边界治理”并非一次性解决：只要后续迭代新增一条同步依赖（尤其在写路径），就可能再次形成环与级联故障传播链路。

#### P0-2：本地投影把一致性/可用性风险前移到写路径

**证据：写路径依赖投影且存在 fail-closed**

- `social-service` 的写路径可信校验依赖本地投影：
  - `social-service/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`
  - `ContentEntityResolver.resolve(...)` 在投影缺失/不完整时直接抛 `SERVICE_UNAVAILABLE`（fail-closed）
  - 投影写入来自 Kafka 消费者：`social-service/src/main/java/com/nowcoder/community/social/kafka/ContentEventConsumer.java`

**对照：同仓库内已存在“投影缺失 → 一次性 bootstrap 修复”的成熟模式**

`content-service` 与 `message-service` 对“用户处罚状态投影”已经采用了更稳健的策略：优先读投影，投影缺失时做一次性 read-repair（向 SSOT 取一次、写回投影、再次校验）：

- `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`
- `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`

这说明：项目内部已经接受“投影缺失不能无脑 503”的现实，并在关键链路中引入了可控的 read-repair。

当前 P0-2 的关键问题在于：
- 各投影的缺失策略不统一（有的 fail-closed，有的 read-repair，有的 fail-open）
- 缺失策略与“写路径/读路径/安全强度/用户体验”的映射尚未被制度化

---

### 目标（要达到什么状态）

> 本方案为“激进改造”路径：允许结构性调整依赖图、引入/补齐事件契约与投影、调整部分接口形态与运维闭环。

1) **同步依赖收敛（Bounded Sync）**
- 把“允许的同步依赖边”显式化（允许矩阵 SSOT），并通过门禁测试固化
- 确保**同步依赖图无环**（至少在 Dubbo 层面可自动验证）
- 将同步调用从“业务默认手段”降级为“少数明确场景的工具”：认证、必要聚合、可丢弃采集、运维平面

2) **写路径跨服务依赖去除（Write Path Isolation）**
- 写路径禁止引入跨服务同步依赖（或仅允许“投影缺失时一次性 read-repair”，并且必须在依赖图上保持无环）
- 所有跨域可信校验的所需信息，优先来自本地投影（最终一致）

3) **投影可靠性闭环（Projection SLO + Repair Loop）**
- 对每个“写路径关键投影”定义：来源事件、存储、幂等策略、缺失策略、重建/回放路径、健康/延迟指标与告警
- 将“投影缺失策略”标准化：fail-closed / read-repair / fail-open 三类策略有明确适用边界

---

### 约束条件
```yaml
安全约束: 认证/鉴权相关链路不得引入“静默降级导致越权”的风险（安全能力优先 fail-closed）
可回滚: 改造需可分阶段发布，且每阶段支持快速回滚（尤其是写路径策略与投影依赖）
兼容性: 对外 API 尽量保持兼容；必要的不兼容需提供迁移期双写/双读或明确的版本切换策略
观测约束: 每次引入投影或调整依赖边，必须同时提供可观测性（metrics/log/trace）与运维动作入口
```

---

### 验收标准（Definition of Done）

#### A. 依赖图与门禁（制度化）
- [ ] 形成 1 份“同步依赖允许矩阵（Dubbo）”的 SSOT 文档，并与代码扫描结果一致
- [ ] 新增门禁测试：基于源码扫描构建 Dubbo 同步依赖图，要求 **无环** 且边集合是 allowlist 子集
- [ ] 新增门禁测试：关键写路径 package（如 `social.like`、`content.comment` 等）不得引入新的 `@DubboReference`（或必须显式标注为 read-repair 并具备开关）

#### B. 同步依赖收敛（可量化）
- [ ] 将“请求路径上的跨服务同步调用”从当前状态收敛到**明确白名单场景**（认证/运维/可丢弃采集/少数聚合）
- [ ] `@DubboReference` 总数下降（建议目标：从 15 降到 ≤ 8；运维平面与可丢弃采集不计入“业务耦合边”指标）

#### C. 投影缺失策略统一（可执行）
- [ ] 对写路径关键投影（至少含 `social` 的 `ContentEntityProjection`、`content/message` 的 `UserModerationProjection`、以及 block/like 等投影）输出统一策略表：缺失时采用哪类策略、理由、开关与回滚手段
- [ ] `social-service` 的 `ContentEntityResolver` 不再“无修复能力地 fail-closed”（至少具备一种可控修复路径：read-repair 或 ops backfill）
- [ ] 对每个关键投影提供运维闭环：健康检查 + lag 指标 + 回放/重建入口（建议集中到 `ops-service`）

---

## 2. 方案（多方案对比）

> 说明：以下方案并非互斥。推荐方案会采用“主路径 + 可选增强”的组合方式。

### 方案 A（推荐）：同步依赖白名单化 + 投影化去耦 + 修复闭环制度化

**核心思想：**
- 同步依赖图“先制度化、再削减”：先把允许边/禁止边固化，避免边界继续漂移
- 业务侧逐步用事件投影替代同步 RPC（尤其写路径与高频读路径）
- 对投影缺失引入统一策略：默认 read-repair（严格超时 + 无环约束），安全强约束场景保留 fail-closed
- bootstrap/backfill 类扫描统一上收至 `ops-service`（或统一的 reconcile job），避免每个业务服务都持有扫描 RPC 客户端

**优点：**
- 能在不引入新服务的情况下显著减少同步依赖与故障传播路径
- 与现有工程方向一致（已有 outbox、已有投影拆环、已有 read-repair 的 guard）
- 通过 gate tests 把“架构意图”变成 CI 可验证事实

**缺点/成本：**
- 需要补齐/新增一些事件契约与投影存储（跨多模块）
- 需要对写路径策略做谨慎的发布与回滚设计

### 方案 B：引入 Query Side（读模型服务），以 CQRS 方式消除跨域读 RPC

**核心思想：**
- 新增一个只读的 `query-service`（或按领域拆分多个 query 服务）
- 由事件流物化出页面级/接口级的读模型（例如用户主页、会话列表、帖子详情等）
- 前端/网关对“读”只依赖 query-side；写仍落在各 domain service

**优点：**
- 可以把大多数跨服务 read fan-out 从运行时彻底搬走（同步依赖大幅减少）
- 读模型天然适配“页面需要什么就给什么”，也更容易做缓存

**缺点/成本：**
- 引入新服务与新的数据存储/物化链路，复杂度显著上升
- 需要更强的事件治理能力（schema/version、重放、幂等、数据一致性观测）

### 方案 C：保持当前同步调用形态，仅加强门禁与文档

**优点：**
- 最低改造成本

**缺点：**
- 不能实质性解决“分布式单体”趋势；最多只能减缓回归
- 对 P0-2（写路径投影缺失导致 503）基本无解

---

### 推荐决策

选择 **方案 A** 作为近期主路径，并预留 **方案 B 的局部落地窗口**：
- 短期（1~2 个迭代）：以 A 为主，先把依赖图固化，并把最危险/最常见的同步依赖迁移为投影或集中化 reconcile
- 中期（2~4 个迭代）：对“页面级聚合读”尝试 B 的局部实践（例如“用户主页/会话列表”等 1~2 个高价值读模型），验证收益后再决定是否扩大

---

## 3. 分阶段路线图（建议）

### Phase 0：制度化（先刹车）
- 输出 allowlist 依赖矩阵（Dubbo）与无环门禁
- 定义投影缺失策略表（fail-closed/read-repair/fail-open）与适用边界

### Phase 1：削减同步依赖（先把请求路径变窄）
- 对 message/content 等服务：把“高频读 + 权限守卫”类的同步依赖迁移为投影（或严格 read-repair）
- 对 social 写路径关键投影：补齐修复闭环，避免“正常用户操作”被投影滞后频繁 503

### Phase 2：运维闭环与读模型增强（让系统可持续）
- 统一把 bootstrap/backfill/replay 能力集中到 ops 平面
- 为关键读路径引入局部 CQRS read model（可选）

---

## 4. 风险评估与回滚策略

| 风险 | 等级 | 描述 | 应对 | 回滚 |
|------|------|------|------|------|
| 依赖图门禁误伤 | 中 | 新门禁可能阻断合理依赖 | allowlist 明确 + 例外流程 | 临时放宽门禁/回退规则 |
| 投影缺失策略调整导致行为变化 | 高 | 写路径从 503 变为 read-repair/异步可能改变延迟/一致性 | 先灰度到少数接口 + 指标观测 | 开关回退到旧策略 |
| read-repair 引入新的同步依赖边 | 中 | miss path 可能在高峰期放大下游压力 | 严格超时 + 限流 + 仅 miss path + 熔断 | 关闭 repair 开关 |
| 事件契约演进带来兼容性问题 | 高 | 新旧 consumer 不兼容 | versioning + unknown handling（已有约定） | 回滚 producer / 双写窗口 |

---

## 5. 技术决策（ADR）

### rpc-decycle-projection-hardening#D001：同步依赖 allowlist + 无环门禁是强约束
**状态**：拟采纳  
**理由**：没有制度化门禁，所有“去耦努力”都会被后续迭代悄悄反向侵蚀。  

### rpc-decycle-projection-hardening#D002：投影缺失默认采用 read-repair（严格超时 + 无环约束）
**状态**：拟采纳  
**理由**：完全 fail-closed 会把最终一致系统的短暂滞后放大为用户可见故障；项目内已有成熟实践（`UserModerationGuard`）。  

### rpc-decycle-projection-hardening#D003：bootstrap/backfill/reconcile 统一走 ops 平面（或统一 reconcile job）
**状态**：拟采纳  
**理由**：扫描 RPC 客户端分散在多个服务会形成隐式耦合网，并使“依赖图”难以被人脑理解与运维。  
