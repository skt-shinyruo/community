# XXL-JOB Distributed Task Integration Design Spec

**Date:** 2026-03-23
**Status:** Approved for planning
**Owner:** Codex

---

## 1. 背景

当前仓库已经出现多类后台任务，但调度方式并不统一：

- `community-app` 内存在基于 Spring `@Scheduled` 的本地定时任务
- 搜索全量重建通过运维接口手工触发，不属于统一调度体系
- `community-im` 与 `community-gateway` 目前主要依赖 Kafka 消费、WebSocket 推送和 HTTP 请求链路，不是典型的离散型调度任务

随着系统继续演进，项目会持续出现以下类型的后台工作：

- 清理类任务
- 重建/补偿类任务
- 对账/回收类任务
- 人工触发的运维批处理任务

如果继续完全依赖应用内 `@Scheduled`，在多实例部署下会带来以下问题：

1. 每个实例都会执行相同任务，无法天然做到单实例调度
2. 任务执行记录、失败记录、重试入口和人工触发能力分散在代码与日志中
3. 需要跨实例、跨环境统一管理任务时，缺少控制面
4. 未来新增更多后台任务时，任务治理成本会越来越高

用户已明确确认本轮要引入 `XXL-JOB`，并将其作为后续分布式定时任务的统一基础设施。

---

## 2. 目标

### 2.1 核心目标

1. 为仓库引入一套可运行的 `XXL-JOB` 分布式任务调度基础设施
2. 第一阶段仅让 `community-app` 接入为 executor
3. 第一阶段迁入两个最合适的任务：
   - `pendingRegistrationUserCleanup`
   - `searchReindex`
4. 保持现有业务服务边界，不把 `XXL-JOB` API 直接侵入各业务域
5. 为后续新增清理、补偿、重建类任务提供统一接入模式

### 2.2 运维目标

- 在 `docker compose` 环境中可以一键启动 `xxl-job-admin`
- 任务可以在 admin 界面中查看、手工触发、查看执行结果
- 多实例部署时由 `XXL-JOB` 负责任务调度，不再依赖每个应用实例都执行本地 `@Scheduled`

### 2.3 非目标

本轮设计不包括以下内容：

- 把所有现有后台工作都迁入 `XXL-JOB`
- 将 Kafka consumer、WebSocket 推送、outbox worker 改造成 `XXL-JOB`
- 让 `community-gateway`、`im-core`、`im-realtime` 在第一阶段接入 executor
- 重写现有业务服务，只为适配调度框架而改变领域边界

---

## 3. 已确认产品与技术方向

以下方向已由用户明确确认：

- 需要引入 `XXL-JOB`
- 目标不是只修补当前一个清理任务，而是为后续分布式任务建立统一能力
- 第一阶段先只覆盖 `community-app`
- 第一阶段优先纳入：
  - 未激活注册用户清理
  - 搜索全量重建
- `outbox worker` 不进入 `XXL-JOB`
- `community-gateway`、`im-core`、`im-realtime` 本轮不接 executor

---

## 4. 现有系统事实与约束

### 4.1 当前已存在的本地定时任务

当前仓库里，实际存在定时任务的后端模块只有 `community-app`。

已存在的本地调度任务包括：

- `PendingRegistrationUserCleanupJob`
  - 基于 `@Scheduled(fixedDelayString = "${auth.registration.pending-user.cleanup-interval-ms:300000}")`
  - 负责删除超过 TTL 的未激活注册用户
- `PostScoreRefresher`
  - 基于 `@Scheduled(fixedDelayString = "${content.score.refresh.delay-ms:30000}")`
  - 负责批量消费帖子热度刷新队列
- `OutboxWorkerScheduler`
  - 基于高频轮询的 outbox worker 调度
  - 负责事件投递和重试链路

### 4.2 当前已存在但尚未纳入统一调度的批处理入口

搜索全量重建目前不是定时任务，而是通过运维接口手工触发：

- `OpsController.reindex()`
- `SearchAdminService.reindex()`
- `ReindexJobService`

其中 `ReindexJobService` 已具备单进程 single-flight 语义：

- `tryStart()` 用于抢占当前重建任务
- `finish(jobId)` 用于释放运行状态
- `conflict(jobId)` 用于表达“已有任务在执行”

这说明 reindex 已经具备“业务级并发保护”，适合作为 `XXL-JOB` 的第一批任务，但不应该让 `XXL-JOB` 直接接管其并发语义。

### 4.3 当前多实例保护事实

仓库中已经存在 `SingleFlightTaskGuard`，它是基于 Redis 的分布式 single-flight 保护器，适合：

- cleanup
- reconcile
- 可跳过/可重试的后台任务

它可以解决“多实例重复执行”的问题，但它不是完整的任务平台，缺少：

- 调度中心
- 执行记录
- 页面化任务管理
- 手工触发入口
- 统一重试与运维可见性

因此它更适合作为轻量保护工具，而不是整个项目长期的后台任务治理方案。

### 4.4 当前模块边界事实

当前几个后端模块的职责边界较清晰：

- `community-app`
  - 业务主模块
  - 拥有认证、内容、消息、搜索、运维等大多数后台批任务候选点
- `community-gateway`
  - Edge / API 网关职责
  - 当前没有定时任务
- `community-im/im-core`
  - 主要承接 IM 领域逻辑与 Kafka 消费
  - 当前没有 `@Scheduled`
- `community-im/im-realtime`
  - 主要承接 WebSocket 推送与实时消费
  - 当前没有 `@Scheduled`

因此，如果第一阶段就让所有模块一起接入 executor，会把本来简单的调度基础设施改造扩大成跨模块平台迁移，收益不成比例。

---

## 5. 方案对比

### 5.1 方案 A：继续保留本地 `@Scheduled`，仅在关键任务外层增加 Redis 分布式锁

优点：

- 改动最小
- 可以快速解决多实例重复执行
- 复用已有 `SingleFlightTaskGuard`

缺点：

- 没有统一任务控制面
- 没有任务执行历史和人工触发能力
- 搜索重建这类运维批任务仍然分散
- 不满足“为后续任务建立统一分布式调度基础设施”的目标

### 5.2 方案 B：第一阶段只为 `community-app` 引入 `XXL-JOB`，迁入两类代表性任务

迁入范围：

- `pendingRegistrationUserCleanup`
- `searchReindex`

暂不迁入：

- `PostScoreRefresher`
- `OutboxWorkerScheduler`
- `community-gateway`
- `im-core`
- `im-realtime`

优点：

- 范围清晰，可控
- 先把最典型的“清理类任务”和“运维批处理任务”纳入统一平台
- 不会误伤实时链路和基础设施 worker
- 后续扩展路径清晰，只需新增 handler

缺点：

- 短期内仍会存在“部分任务在 `XXL-JOB`、部分任务在应用内”的混合态
- 需要在 compose 和配置层引入新的基础设施服务

### 5.3 方案 C：一次性让所有后端模块全部接入 `XXL-JOB`

优点：

- 看起来“一次完成平台统一”

缺点：

- 范围过大
- 需要同时定义 gateway、IM、实时链路、Kafka 消费与任务平台之间的边界
- 极易把并不适合调度平台的持续型 worker 误迁进去
- 与当前目标不成比例

### 5.4 推荐方案

采用方案 B。

原因：

- 它能先完成真正有价值的统一调度基础设施落地
- 它不会把 outbox、Kafka consumer、WebSocket 等非调度型工作错误卷入
- 它为后续清理/补偿/重建类任务提供了清晰且可复用的接入模式

---

## 6. 总体设计

### 6.1 组件拓扑

第一阶段系统拓扑调整为：

- 新增 `xxl-job-admin`
  - 作为统一调度控制面
  - 运行在 `deploy/docker-compose.yml`
  - 使用独立 MySQL schema 保存任务定义、执行记录和调度元数据
- `community-app`
  - 作为第一阶段唯一 executor
  - 向 `xxl-job-admin` 注册可执行任务 handler
  - 继续承载实际业务服务

其他后端模块本轮不接 executor。

### 6.2 代码结构

在 `backend/community-app` 中新增独立调度适配层，例如：

- `com.nowcoder.community.infra.job`
  - `XxlJobProperties`
  - `XxlJobAutoConfiguration`
  - `handlers/`

其中 `handlers/` 下按任务拆分：

- `PendingRegistrationUserCleanupHandler`
- `SearchReindexHandler`

设计原则：

- `XXL-JOB` 相关类只存在于 `infra.job` 适配层
- 业务逻辑仍留在现有 service 中
- handler 只负责接任务参数、调用业务服务、返回执行结果
- 不把 `XXL-JOB` API 直接扩散到 `auth`、`search` 等领域包里

### 6.3 配置设计

新增一组与 `XXL-JOB` 相关的应用配置，并支持通过 `deploy/.env` 注入：

- `XXL_JOB_ENABLED`
- `XXL_JOB_ADMIN_ADDRESSES`
- `XXL_JOB_EXECUTOR_APPNAME`
- `XXL_JOB_EXECUTOR_ADDRESS`
- `XXL_JOB_ACCESS_TOKEN`

第一阶段默认原则：

- `XXL_JOB_ENABLED=false`
- 本地 IDE / 单元测试环境不强依赖 admin
- 只有在 compose / 部署环境显式开启时，executor 才真正向 admin 注册

### 6.4 部署设计

`deploy/docker-compose.yml` 需要新增：

- `xxl-job-admin` 服务
- admin 所需的数据库 schema 初始化脚本，例如 `deploy/mysql-init/020_xxl_job.sql`
- `community-app` 对应的 executor 环境变量透传

第一阶段建议：

- 在 compose 网络内让 admin 与 executor 通过服务名互通
- 对宿主机暴露 admin UI 端口，便于本地联调和任务观察
- admin 的账号、access token、数据库连接通过 `.env` 管理

---

## 7. 第一阶段任务设计

### 7.1 pendingRegistrationUserCleanup

#### 7.1.1 任务职责

该任务负责删除超过 TTL 的未激活注册用户。

它当前已经具备良好的业务语义：

- 只删除 `status=0` 的用户
- 仅针对超过 `pending-user.ttl-seconds` 的记录
- 删除操作天然幂等

因此，它非常适合迁入 `XXL-JOB`。

#### 7.1.2 迁移方式

新增 `PendingRegistrationUserCleanupHandler`，其执行逻辑只做一件事：

1. 从 `RegistrationProperties` 读取 pending user TTL
2. 调用 `InternalUserService.cleanupExpiredPendingUsers(ttl)`
3. 记录删除数量并返回执行结果

不应在 handler 中重复实现删除规则，也不应把 SQL 搬到调度层。

#### 7.1.3 本地调度切换

现有 `PendingRegistrationUserCleanupJob` 是本地 `@Scheduled` 任务。

迁入 `XXL-JOB` 后，推荐引入显式开关，例如：

- `auth.registration.pending-user.local-scheduler-enabled`

行为定义：

- 本地无 admin 的开发环境可以继续启用本地 scheduler
- compose / 部署环境启用 `XXL-JOB` 时，关闭本地 scheduler
- 迁入后的主路径应是 `XXL-JOB`，不是“本地 scheduler + XXL 双跑”

### 7.2 searchReindex

#### 7.2.1 任务职责

该任务负责触发搜索索引全量重建。

它当前的特点是：

- 不是固定频率后台清理，而是典型的运维批处理任务
- 已有 HTTP 运维入口，但缺少统一执行记录和页面化调度入口
- 已通过 `ReindexJobService` 建立单进程 single-flight 保护

这类任务非常适合纳入 `XXL-JOB` 的手工触发或定时触发能力。

#### 7.2.2 迁移方式

新增 `SearchReindexHandler`，其职责是：

1. 调用 `ReindexJobService.tryStart()`
2. 如果未获取执行权，则返回“已有任务运行，跳过本次执行”
3. 如果获取执行权，则调用现有搜索重建业务服务
4. 完成后调用 `finish(jobId)` 释放运行状态

这里的关键点是：

- `XXL-JOB` 只负责“触发”
- single-flight 仍由业务层负责
- 不能把“是否允许并发重建”这类业务保护逻辑下沉到调度平台

#### 7.2.3 与现有 HTTP 入口的关系

现有 `OpsController.reindex()` 不需要因为 `XXL-JOB` 而立刻删除。

第一阶段推荐：

- 保留 HTTP 入口作为运维兼容触发方式
- 让 HTTP 入口与 `XXL-JOB` handler 复用同一组业务服务
- 依赖 `ReindexJobService` 保证不同入口之间不会并发跑出多个全量重建

---

## 8. 明确不迁入 `XXL-JOB` 的任务

### 8.1 PostScoreRefresher

`PostScoreRefresher` 当前更像“高频、持续、低延迟的队列消费补偿器”，而不是典型后台批任务。

它的特征是：

- 频率高
- 每次只处理小批量队列元素
- 本质上更接近应用内异步 worker

第一阶段不建议迁入 `XXL-JOB`。

后续如果真的要迁，也应先重新评估其队列模型，而不是简单把 `@Scheduled` 换成 job handler。

### 8.2 OutboxWorkerScheduler

`OutboxWorkerScheduler` 不应迁入 `XXL-JOB`。

原因：

- 它是应用内最终一致性基础设施的一部分
- 需要持续轮询、低延迟、至少一次投递
- 与页面化的“离散任务执行”模型天然不匹配

如果强行迁入，反而会引入：

- 更高延迟
- 更复杂的故障语义
- 更模糊的事件投递边界

### 8.3 gateway / IM 模块

`community-gateway`、`im-core`、`im-realtime` 本轮不接 executor。

原因：

- 当前没有现成的离散型调度任务
- 主要工作负载是 HTTP、Kafka、WebSocket
- 让这些模块一起接入 executor，只会扩大基础设施改造范围

---

## 9. 运行流转与失败语义

### 9.1 cleanup 任务

`pendingRegistrationUserCleanup` 的运行流转为：

1. `xxl-job-admin` 按配置调度任务
2. `community-app` executor 收到调度请求
3. `PendingRegistrationUserCleanupHandler` 调用 `InternalUserService.cleanupExpiredPendingUsers(ttl)`
4. 返回本轮删除数量与执行状态

失败语义：

- 该任务天然幂等
- 同一窗口内重复执行只是多扫一遍，不会产生错误副作用
- 因此适合由 `XXL-JOB` 做失败重试

### 9.2 reindex 任务

`searchReindex` 的运行流转为：

1. `xxl-job-admin` 手工或定时触发任务
2. `SearchReindexHandler` 调用 `ReindexJobService.tryStart()`
3. 若未抢到执行权，则返回 skip，而不是把它当成真正失败
4. 若抢到执行权，则执行当前搜索重建服务
5. 执行完成后 `finish(jobId)`

失败语义必须区分两类情况：

- 已有任务在执行
  - 这不是异常故障
  - 应作为 `skipped / conflict` 结果返回
  - 不应触发无意义的自动重试
- 真正执行失败
  - 应返回失败
  - 由 `XXL-JOB` 记录、告警或人工重试

### 9.3 开发环境与部署环境的行为区别

需要明确以下约束：

- 本地开发环境
  - 可以不开启 `XXL-JOB`
  - 可以继续保留被迁移任务的本地 scheduler 兜底
- compose / 部署环境
  - 开启 `XXL-JOB`
  - 关闭已迁移任务的本地 scheduler
  - 避免本地调度和平台调度双跑

---

## 10. 安全、配置与运维要求

### 10.1 Admin 安全

`xxl-job-admin` 至少需要具备以下最小安全要求：

- 管理员账号不使用默认弱口令
- `access token` 外置到环境变量
- compose 暴露端口仅用于本地或受控环境

### 10.2 Executor 注册要求

`community-app` executor 需要具备以下要求：

- 只有在 `XXL_JOB_ENABLED=true` 时才初始化
- 在配置缺失时应 fail fast，而不是半启动
- 任务 handler 注册应可观测，便于排查“admin 已启动但 executor 未注册”的问题

### 10.3 观测性

需要至少保留以下观测信息：

- admin 上的任务执行历史
- 应用日志中的 handler 开始/结束/失败日志
- cleanup 删除数量
- reindex 任务的 jobId、冲突跳过、执行耗时和失败原因

---

## 11. 实施边界与迁移顺序

### 11.1 Phase 1

第一阶段仅包含：

1. `xxl-job-admin` compose 接入
2. `community-app` executor 接入
3. `PendingRegistrationUserCleanupHandler`
4. `SearchReindexHandler`
5. 已迁移 cleanup 任务的本地 scheduler 开关化

### 11.2 明确延后项

以下内容明确延后，不进入本轮实现：

- `PostScoreRefresher` 迁移
- `OutboxWorkerScheduler` 迁移
- gateway / IM 模块接 executor
- 统一任务平台 SDK 抽到多模块共享
- 复杂分片执行与广播任务设计

---

## 12. 测试与验收要求

### 12.1 配置与装配测试

需要覆盖：

- `XXL_JOB_ENABLED=false` 时应用可正常启动且不依赖 admin
- `XXL_JOB_ENABLED=true` 时 executor 装配成功
- handler Bean 注册符合预期

### 12.2 cleanup 任务测试

需要覆盖：

- handler 调用现有清理 service 并返回删除数量
- TTL 从配置读取而不是硬编码
- 本地 scheduler 开关关闭后不会重复执行本地 `@Scheduled`

### 12.3 reindex 任务测试

需要覆盖：

- 已有 reindex 执行中时，handler 返回 skip/conflict 语义
- 获取执行权后能正常触发重建
- 执行结束后释放 `jobId`
- 业务异常时正确返回失败结果

### 12.4 compose 验收

本地 compose 验收必须满足：

1. `xxl-job-admin` 能启动并可访问
2. `community-app` 能注册为 executor
3. admin 中能看到两个第一阶段任务
4. `pendingRegistrationUserCleanup` 能成功执行
5. `searchReindex` 能从 admin 触发，并保持 single-flight 语义

---

## 13. 风险与缓解

### 13.1 任务平台引入复杂度

风险：

- 新增 admin、schema、配置和 compose 编排

缓解：

- 第一阶段只接一个 executor 和两个任务
- 不同时改造其它模块

### 13.2 本地 scheduler 与 XXL 双跑

风险：

- 若切换策略不清晰，迁移后的任务可能被本地 `@Scheduled` 和 `XXL-JOB` 同时执行

缓解：

- 为已迁移任务引入显式本地 scheduler 开关
- compose 环境强制关闭本地 scheduler

### 13.3 reindex 任务被误判为失败

风险：

- “已有任务正在运行”如果被当作普通失败，会导致 admin 无意义重试

缓解：

- 将冲突场景设计为 skip，而不是 fail
- 保留业务层 single-flight 语义

---

## 14. 最终结论

本轮采用以下设计：

1. 在 `docker compose` 中引入 `xxl-job-admin`
2. 第一阶段只让 `community-app` 接入 executor
3. 第一阶段仅迁入：
   - `pendingRegistrationUserCleanup`
   - `searchReindex`
4. `PostScoreRefresher`、`OutboxWorkerScheduler`、gateway / IM 模块明确延后
5. `XXL-JOB` 只作为任务编排与触发层，业务并发保护和实际业务逻辑继续保留在现有 service 中

这个方案既能满足“建立统一分布式任务基础设施”的目标，又能把本轮改造控制在合理边界内，为后续更多清理、补偿、重建类任务提供稳定接入路径。
