# Task List: content Domain Event + BEFORE_COMMIT Outbox 自动入队

Directory: `helloagents/plan/202602212345_content_domain_event_outbox/`

---

## 1. content-service（Domain Event 基础设施）
- [√] 1.1 新增 posts 领域事件类型（PostPublished/PostUpdated/PostDeleted）与发布器（无事务时 fail-fast），verify why.md#change-content
- [√] 1.2 新增 `PostPayloadAssembler`：从 `DiscussPost + tags + ContentTextCodec` 构造完整 `PostPayload`（字段完整性 SSOT），verify why.md#core-scenarios
- [√] 1.3 新增 Domain Event → Outbox 桥接：`@TransactionalEventListener(BEFORE_COMMIT)` 将领域事件映射为 `ContentEventPublisher` 调用，verify how.md#technical-solution
- [-] 1.4（可选但推荐）在 Outbox enabled 场景对“无事务 publish”增加护栏（日志+异常策略），避免遗留入口形成隐性不一致窗口，verify why.md#risk-assessment

## 2. content-service（迁移帖子管理动作：top/wonderful/delete）
- [√] 2.1 扩展 `PostCommandService`（或新增专用命令服务）：实现 `top/wonderful/delete` 等管理命令为 `@Transactional`，并发布对应 Domain Event；将非 DB 副作用（如 `postScoreQueue.add`）放到 after-commit，verify why.md#core-scenarios
- [√] 2.2 重构 `PostController`：移除 `ContentEventPublisher` 与 `PostPayload` 手工拼装，Controller 仅做鉴权/校验/审计并委托命令服务，verify why.md#core-scenarios

## 3. content-service（修复旁路写路径：PostScoreRefresher）
- [√] 3.1 将 `updateScore + PostUpdated` 事件发布改为“事务内更新 + 发布 Domain Event + BEFORE_COMMIT 入队 outbox”，并复用 `PostPayloadAssembler`，verify why.md#core-scenarios-1
- [-] 3.2 增加观测：score 刷新链路的 outbox queued/publish 指标与失败告警点位，verify why.md#risk-assessment

## 4. content-service（迁移存量写路径 publish 点位）
- [√] 4.1 将 `PostCommandService.createPost/updatePost/deletePostByAuthor` 从“手工拼装 payload + publish”迁移为“发布 Domain Event + assembler 统一构造 payload”，verify why.md#change-content
- [-] 4.2（评估后落地）将 `ModerationService` 的 post/comment 删除事件拼装收敛到对应 assembler（避免字段缺失与重复拼装），verify why.md#change-content

## 5. Security Check
- [√] 5.1 执行安全检查（输入校验、鉴权边界、事件 payload 最小化、避免把治理原因等敏感字段写入事件、outbox 失败回滚策略）

## 6. Documentation Update
- [√] 6.1 更新 `helloagents/wiki/modules/content.md`：写路径分层（Controller thin + transactional command service）与 Domain Event/Outbox 桥接说明
- [√] 6.2 更新 `helloagents/wiki/arch.md`：补充 ADR-020 索引（迁移到 history 后挂链接）
- [√] 6.3 更新 `helloagents/CHANGELOG.md`：记录本次一致性与分层收敛改造

## 7. Testing
- [√] 7.1 单元测试：`PostPayloadAssembler` 字段完整性（特别是 `categoryId/tags/title/content`），并覆盖 deleted/score 更新场景
- [√] 7.2 集成测试：Outbox enabled 时模拟 enqueue 失败必须触发事务回滚（业务状态不变 + outbox 无记录）
- [√] 7.3 回归测试：管理动作（top/wonderful/delete）对外 API 行为不变，且下游（search/message/user）消费无破坏性变化
