# Technical Design: 拆除 content-service ↔ social-service 写路径同步依赖环

## Technical Solution

### Core Technologies
- Kafka 事件（`EventEnvelope` + `EventTopics` + `EventTypes`）
- social-service 本地投影（建议 MySQL 表 + 幂等 upsert）
- Spring Kafka consumer（与现有 `content-service/message-service/user-service` 的 consumer 形态一致）

### Implementation Key Points

#### 1) 建立 social-service 的 Content Entity Projection（SSOT=投影；源=content 事件）
目标：为社交写路径提供“可信的 entity 元信息解析”，替代实时跨域同步 resolve。

建议数据模型（示意）：
- 主键：`(entity_type, entity_id)`
- 字段：`entity_user_id`、`post_id`、`status`、`updated_at`（来自 envelope.occurredAt）
- 语义：
  - 对 POST：`post_id = entity_id`
  - 对 COMMENT：`post_id = root postId`（由 content-service 在事件中提供）

更新策略（关键）：
- 事件重复投递：upsert 幂等
- 事件乱序：仅当 `occurredAt >= current.updated_at` 时覆盖，避免旧事件回滚状态

#### 2) social-service 消费 content 事件并更新投影
订阅 topic（建议）：
- `EventTopics.POST_EVENTS_V1`：`PostPublished` / `PostUpdated` / `PostDeleted`
- `EventTopics.COMMENT_EVENTS_V1`：`CommentCreated` + 新增的 `CommentDeleted`（见下文）

处理逻辑（概要）：
- 解析 `EventEnvelope`（仅支持 v1，未知类型按配置 SKIP 或 DLQ）
- 从 payload 提取最小必要字段，更新投影（含 `updated_at=occurredAt`）

#### 3) content-service 补齐“处置导致的内容下线”事件
当前治理处置（`ModerationService.applyContentAction`）会更新 DB 状态，但不会发布 POST/COMMENT 的状态变更事件。
为保证下游投影准确，需要补齐：
- 对帖子 hide/delete：发布 `PostDeleted` 到 `POST_EVENTS_V1`（复用 `PostPayload`）
- 对评论 hide/delete：新增 `EventTypes.COMMENT_DELETED`，并发布到 `COMMENT_EVENTS_V1`（建议新增专用 payload 或复用最小字段载体）

这样 social-service 无需订阅 moderation topic，也能完整感知“内容是否可点赞”。

#### 4) LikeService 写路径切换为读投影（拆环）
改造点：`LikeService.resolveEntityForPayload`。

目标行为：
- 对 POST/COMMENT：从投影读取 `entityUserId/postId`，并校验可用状态
- 投影缺失：默认 fail-closed（避免绕过可信校验）
- 迁移期：可选“受控回源 + 回填”开关（限额、限速、可观测），用于兜底投影冷启动或历史数据缺口

迁移期回源策略建议：
- Feature flag：`social.entity-resolve.fallback.enabled=false`（默认关闭）
- 上限：每实例 QPS/并发/每日最大次数（防止在下游抖动时放大为雪崩）
- 回填：成功回源后写入投影，减少后续回源
- 观测：hit/miss/fallback/outcome 指标 + 采样日志

#### 5) 架构约束落地（同步调用只读且不成环）
建议在两处固化：
- 文档：`.helloagents/modules/*` 明确“写路径禁止跨域同步依赖”的边界与例外（仅允许读、且不得形成环）
- 代码：增加轻量静态约束（例如 ArchUnit/自定义检查）阻止 `social-service` 再引入 `content-service` 的写路径同步客户端

## Architecture Decision ADR

### ADR-01: 社交写路径可信校验采用事件投影替代跨域同步 resolve
**Context:** `social-service` 点赞写路径依赖 `content-service` 同步 resolve，导致与 `content-service -> social-service` 的写路径校验形成同步环，放大级联故障与发布牵制风险。  
**Decision:** 在 social-service 引入内容实体元信息投影，使用 Kafka 事件最终一致更新；点赞写路径改为读投影。  
**Rationale:** 通过数据就近与最终一致，将跨域依赖从“用户写路径”迁移到“异步投影更新”，从结构上拆环并降低故障传播。  
**Alternatives:**  
- 保留 RPC + 强隔离治理：无法从依赖图上消除环，风险仅被延后。  
- 抽离独立只读服务：结构最干净但改动与成本最高。  
**Impact:** 写路径稳定性提升；需要投影/回放/监控等工程化能力与灰度上线策略。

## Security and Performance
- **Security：** 投影只存最小必要字段（id/owner/postId/status），不引入敏感信息；写路径回源（若开启）必须限额，避免在下游异常时放大为攻击面。
- **Performance：** 写路径从远程 RPC 变为本地读（DB/缓存），降低 P99 延迟与尾部抖动；consumer 处理需限流并监控 lag。

## Testing and Deployment
- **Testing：** consumer 单元测试（乱序/重复/未知 type）、LikeService 投影命中/缺失/降级策略测试、事件契约测试（新增 type/payload）。
- **Deployment：** 分阶段上线：先上投影+consumer（只读），观测覆盖率与 lag；再灰度切换 LikeService 读投影；最后关闭回源并移除同步 client 依赖。

