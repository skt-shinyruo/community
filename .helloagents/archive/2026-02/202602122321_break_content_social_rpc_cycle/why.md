# Change Proposal: 拆除 content-service ↔ social-service 写路径同步依赖环

## Requirement Background

当前项目的服务间依赖在“写路径可信校验/数据补全”场景出现了双向同步调用：

- `social-service` 的点赞写路径（`LikeService.resolveEntityForPayload`）通过 Dubbo 同步调用 `content-service` 的 `ContentEntityRpcService` 来解析 `POST/COMMENT` 的 `ownerUserId/postId/exists`，用于避免信任客户端注入字段。
- `content-service` 的评论写路径在本地投影缺失时，会回源 `social-service` 查询拉黑关系（`SocialBlockClient.isEitherBlocked`）并回填投影。

这会导致依赖图上形成 `content-service ↔ social-service` 的同步环；进一步叠加：

- `content-service -> user-service`（处罚状态投影缺失时 bootstrap 回源）
- `user-service -> social-service`（用户主页统计等聚合展示）

在系统层面放大出更大的潜在依赖环与故障传播路径。

典型风险包括：级联故障（抖动放大）、部署/回滚相互牵制、超时与重试叠加导致雪崩。

## Change Content

1. **social-service 引入本地投影（Content Entity Projection）**  
   以 `POST/COMMENT -> entityUserId/postId/status/updatedAt` 作为写路径可信只读来源，替代实时跨域 resolve。

2. **事件驱动更新投影（最终一致）**  
   social-service 消费 `content-service` 发布的 POST/COMMENT 事件，持续修正投影。

3. **补齐内容“下线/删除/隐藏”事件**  
   content-service 在治理处置（hide/delete）改变帖子/评论状态时，同步发布对应的实体状态变更事件，确保下游投影可感知内容不可用。

4. **点赞写路径改为读投影（拆除同步依赖边）**  
   `LikeService` 写路径优先读取投影；迁移期可提供“缺失回源 + 回填”的受控策略（明确上限、开关、观测），并在投影稳定后关闭回源。

5. **落地架构约束与可观测性**  
   明确并固化约束：跨服务同步调用仅允许读、且不得形成环；为投影命中/缺失/回源、消费延迟等关键指标补齐监控。

## Impact Scope

- **Modules：** `social-service`、`content-service`、`common`（事件契约）
- **APIs：** Kafka topic 不变（`community.event.post.v1` / `community.event.comment.v1`），新增/扩展 event type
- **Data：** social-service 增加投影存储（建议 MySQL 表；也可按现有存储策略选择 Redis，但需持久化基线）

## Core Scenarios

### Requirement: 社交写路径去同步 resolve
**Module:** social

#### Scenario: 点赞 POST/COMMENT
- 写路径不再调用 `content-service` 的 `ContentEntityRpcService`
- 通过本地投影解析 `entityUserId/postId`，并校验实体可用状态
- 投影缺失时按策略处理：
  - 默认 fail-closed（返回 503/400），避免静默绕过可信校验
  - 迁移期可开启受控回源并回填（有明确 QPS/并发/次数上限）

### Requirement: 投影最终一致与纠偏
**Module:** social

#### Scenario: 冷启动/漏消息/滞后
- consumer 通过回放构建基线（受 Kafka retention 约束）
- 提供受控 backfill/rebuild 手段（限速、可观测、可回滚），避免长期依赖“写路径回源”

### Requirement: 内容下线状态同步
**Module:** content/social

#### Scenario: 举报处置 hide/delete
- content-service 在改变帖子/评论状态后发布实体状态变更事件
- social-service 投影及时标记为不可用，阻止后续点赞副作用

## Risk Assessment

- **风险：事件滞后导致短暂不一致**  
  **缓解：** 缺失策略（fail-closed 或受控回源）、监控告警（miss/fallback/lag）、灰度切换。

- **风险：消费失败/重复与乱序**  
  **缓解：** 投影 upsert 幂等、按 `occurredAt` 做单调更新、DLQ/重放能力。

- **风险：上线顺序不当导致写路径不可用**  
  **缓解：** 先部署 consumer+投影（只读），再切换写路径，最后关闭回源与移除 RPC 依赖。

