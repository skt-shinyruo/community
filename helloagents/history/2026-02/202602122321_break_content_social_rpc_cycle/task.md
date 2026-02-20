# Task List: 拆除 content-service ↔ social-service 写路径同步依赖环

Directory: `helloagents/plan/202602122321_break_content_social_rpc_cycle/`

---

## 1. social-service（投影 + 消费者）
- [√] 1.1 新增 content entity 投影存储与访问层（表结构/mapper/repository），用于 `POST/COMMENT -> (entityUserId, postId, status, updatedAt)`，verify why.md#core-scenarios- [√] 1.2 新增 Kafka consumer：订阅 `EventTopics.POST_EVENTS_V1` / `EventTopics.COMMENT_EVENTS_V1`，消费 `PostPublished/PostUpdated/PostDeleted/CommentCreated/CommentDeleted` 并幂等 upsert 投影，verify why.md#core-scenarios, depends on task 1.1- [√] 1.3 增加投影命中/缺失/更新延迟等关键指标与日志采样（hit/miss/fallback/lag），verify why.md#risk-assessment, depends on task 1.2
## 2. content-service（补齐状态变更事件）
- [√] 2.1 在治理处置（`ModerationService.applyContentAction`）对 POST hide/delete 时发布 `PostDeleted` 事件到 `POST_EVENTS_V1`，保证下游投影可感知内容下线，verify why.md#core-scenarios-2, depends on task 1.2- [√] 2.2 新增 `EventTypes.COMMENT_DELETED`（必要时新增 payload），并在评论 hide/delete 时发布到 `COMMENT_EVENTS_V1`，verify why.md#core-scenarios-2, depends on task 2.1
## 3. social-service（写路径切换与拆环）
- [√] 3.1 修改 `LikeService.resolveEntityForPayload`：优先读投影解析 entity 元信息并校验可用状态；缺失按策略 fail-closed 或受控回源+回填（feature flag + 上限），verify why.md#core-scenarios, depends on task 1.2- [-] 3.2 迁移完成后移除/降级 `ContentServiceClient` 在写路径中的必需性（默认不再同步调用），并补充架构约束（可选 ArchUnit/静态检查），verify how.md#architecture-decision-adr, depends on task 3.1
## 4. Security Check
- [√] 4.1 执行安全检查（输入校验、投影字段最小化、回源限额、防止超时+重试放大、内部 topic/consumer 组隔离）
## 5. Documentation Update
- [√] 5.1 更新 `helloagents/wiki/modules/social.md`：新增“content entity 投影”与“写路径不再同步 resolve”的约束说明- [√] 5.2 更新 `helloagents/wiki/modules/content.md`：补充“moderation 导致的 post/comment 状态变更事件”说明- [√] 5.3 更新 `helloagents/wiki/arch.md`（如存在相关章节）：补充“同步调用只读且不成环”的架构约束与依赖图- [√] 5.4 更新 `helloagents/CHANGELOG.md`：记录本次拆环改造
## 6. Testing
- [√] 6.1 为 social-service consumer 增加单元测试：重复/乱序/未知 type（SKIP/DLQ）场景- [√] 6.2 为 LikeService 增加单元测试：投影命中/缺失/fallback（开关）行为与错误码语义- [√] 6.3 更新 common 事件契约测试：覆盖新增 `CommentDeleted` 事件/载荷可序列化
