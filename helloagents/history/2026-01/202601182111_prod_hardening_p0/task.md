# Task List: 生产可用 P0 加固（事件一致性/幂等/DLQ/同步调用韧性/MySQL 多 Schema）

Directory: `helloagents/history/2026-01/202601182111_prod_hardening_p0/`

---

## 1. message-service（R1：消费端幂等 + 事务 + ack）

- [√] 1.1 拆分 Kafka Listener 与事务处理器：新增 `message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventProcessor.java`（`@Service` + `@Transactional`），并将 `NoticeEventConsumer` 改为仅调用 processor + 成功后 `ack.acknowledge()`；移除/避免 `@Transactional` 仅标注在同类内部方法，验证 why.md#r1-message-consume-atomicity（重点：R1S2）  
  - 验证：`mvn -pl message-service -Dtest=NoticeEventConsumerIntegrationTest test`
- [√] 1.2 幂等实现改为“insert-first（唯一约束为准）”：调整 `message-service/src/main/java/com/nowcoder/community/message/dao/ConsumedEventMapper.java`，在 processor 中改为先插入 `consumed_event(event_id)` 作为幂等锁，捕获 DuplicateKey/唯一冲突后直接返回；删除/弃用“count→insert”路径，验证 why.md#r1s1-duplicate-eventid-no-duplicate-notice  
  - 验证：本地构造同 eventId 重复消费两次，notice 仅 1 条（可复用 controller 查询）
- [√] 1.3 补齐测试：扩展/新增 `message-service/src/test/java/com/nowcoder/community/message/kafka/NoticeEventConsumerIntegrationTest.java`，增加“同 eventId 重复消费不重复通知”的断言；（可选）用 `@MockBean NoticeService` 注入一次性失败以覆盖 why.md#r1s2-fail-between-idempotency-and-side-effect-no-data-loss  
  - 验证：`mvn -pl message-service test`

## 2. common + content-service + social-service（R2：After-Commit 发送）

- [√] 2.1 新增通用 After-Commit 工具：创建 `common/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java`，提供 `runAfterCommit(Runnable)`，在事务活跃时注册 `afterCommit()`，无事务时直接执行；验证 why.md#r2-producer-after-commit（准备项）  
  - 验证：增加最小单元测试或在服务内通过手工日志验证回调时机
- [√] 2.2 content-service 生产端改造：更新 `content-service/src/main/java/com/nowcoder/community/content/event/KafkaContentEventPublisher.java`，在事务活跃时使用 `AfterCommitExecutor` 发送 Kafka；发送失败时不再以异常“同步失败”影响已提交事务，而改为记录错误日志 + 指标（P0 仅要求可观测），验证 why.md#r2s2-db-commit-then-send  
  - 验证：发帖/评论写接口回归；模拟 Kafka 不可用时检查日志/指标
- [√] 2.3 将“发帖写入 + 发布”收敛到事务域：新增 `content-service/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`（或同等命名）实现 `@Transactional createPost(...)`，把 `PostController` 中写入 + `postScoreQueue.add` + `eventPublisher.publishPostPublished` 移入 service；`PostController` 只做鉴权与参数清洗后调用 service，验证 why.md#r2s1-db-rollback-no-event、why.md#r2s2-db-commit-then-send  
  - 验证：构造异常导致事务回滚时 Kafka 不应收到事件（可通过消费端日志/测试桩验证）
- [√] 2.4 （可选但建议）把“非 DB 副作用”也延后到提交后：在 `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java` 中将 `postScoreQueue.add(postId)` 延后到 after-commit 执行，避免事务回滚仍触发热度刷新；验证 why.md#r2s1-db-rollback-no-event（副作用一致性）
- [√] 2.5 social-service 生产端统一策略：更新 `social-service/src/main/java/com/nowcoder/community/social/event/KafkaSocialEventPublisher.java`，同样在事务活跃时使用 `AfterCommitExecutor`；补齐发送失败日志 + 指标（即便目前主要是 Redis 写，仍统一约定），验证 why.md#r2-producer-after-commit  

## 3. user-service（R3：同步调用限时 + 降级 + 可观测）

- [√] 3.1 RestTemplate 强制超时：新增 `user-service/src/main/java/com/nowcoder/community/user/config/SocialServiceClientProperties.java`（`@ConfigurationProperties`，含 connect/read timeout 默认值）并更新 `user-service/src/main/java/com/nowcoder/community/user/config/UserRestClientConfig.java:14` 使用 `RestTemplateBuilder` 设置超时；（可选）在 `user-service/src/main/resources/application.yml` 增加可调默认值示例，验证 why.md#r3s1-social-down-fast-degrade  
  - 验证：手工停掉 social-service，访问 user 主页接口应在超时阈值内返回（不被挂死）
- [√] 3.2 增加可观测指标：更新 `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`，对 `userLikeCount/followeeCount/followerCount/hasFollowed` 增加 Timer/Counter（success/error/degraded），并在 catch 降级路径增加一次计数，验证 why.md#r3-sync-call-resilience  
  - 验证：`curl http://localhost:12882/api/users/{id}` 在 social-service up/down 两种情况下，`/actuator/prometheus` 中指标增量符合预期
- [√] 3.3 补齐最小自动化测试：新增或扩展 `user-service` 单测，模拟 restTemplate 抛 `RestClientException` 时 `safe*` 方法返回默认值，并断言降级计数被触发（可使用 `SimpleMeterRegistry`），验证 why.md#r3s1-social-down-fast-degrade  
  - 验证：`mvn -pl user-service test`

## 4. DLQ（R4：监控告警 + 回放流程）

- [√] 4.1 DLQ 发布指标：更新 `message-service/src/main/java/com/nowcoder/community/message/kafka/KafkaErrorHandlerConfig.java` 与 `search-service/src/main/java/com/nowcoder/community/search/kafka/KafkaErrorHandlerConfig.java`，在 recoverer 中注入 `MeterRegistry`，每次 DLQ publish 增加 Counter（tag：originalTopic/errorType），验证 why.md#r4s1-dlq-publish-alert  
  - 验证：制造一次消费失败进入 DLQ，检查对应服务 `/actuator/prometheus` 指标增长
- [√] 4.2 告警规则：更新 `deploy/observability/alerts.yml`，新增 DLQ publish 告警（例如 `increase(kafka_dlq_published_total[5m]) > 0`），并按 topic/服务打标签便于定位，验证 why.md#r4s1-dlq-publish-alert  
  - 验证：`docker compose` 启动 Prometheus 后可看到规则加载（可在 Grafana Explore/Prometheus UI 查询）
- [√] 4.3 回放脚本与 Runbook：新增 `scripts/kafka-replay-dlq.sh`（支持 topic 白名单、限量、限速、dry-run），并更新 `docs/OBSERVABILITY.md` 或 `deploy/backups/README.md` 补充 DLQ 回放操作流程、风险与回滚措施，验证 why.md#r4s2-dlq-replay-runbook  
  - 验证：在本地演练环境回放 1 条 DLQ 消息到原 topic（确认不会无限回放）

## 5. MySQL 同实例多 Schema（R5：先拆非身份域）

- [√] 5.1 初始化脚本拆分：重构 `deploy/mysql-init/001_schema.sql` 为多 schema 脚本（使用 `deploy/mysql-init/001_create_databases.sh` + `010/020/030/040_schema_*.sql`），将表迁移到 `community_content/community_message/community_search`；保留 `community.user`（身份域暂留），验证 why.md#r5s1-compose-boot-with-schemas  
  - 验证：清空 mysql 数据卷后启动 compose，检查 schema 与表自动创建
- [√] 5.2 Compose 与环境变量：更新 `deploy/.env.example` 增加 per-service DB 账号/密码与 schema 名称（例如 `CONTENT_DB_*`/`MESSAGE_DB_*`/`SEARCH_DB_*`），并更新 `deploy/docker-compose.yml` 为 content/message/search 注入各自 `*_DB_URL` + `*_DB_USERNAME` + `*_DB_PASSWORD`（不再复用 `MYSQL_USER` 作为业务账号），验证 why.md#r5-mysql-multi-schema-split  
  - 验证：`docker compose up -d --build` 后三服务能正常连库启动
- [√] 5.3 多 schema 备份/恢复：更新 `scripts/mysql-backup.sh` 与 `scripts/mysql-restore.sh` 支持多库（新增 `MYSQL_DATABASES`，默认建议 `community,community_content,community_message,community_search`），并更新 `deploy/backups/README.md` 说明演练步骤，验证 why.md#r5s2-backup-restore-multi-db  
  - 验证：执行一次完整备份与恢复演练（本地）
- [√] 5.4 回归验证：在多 schema 模式下执行最小烟测（登录/发帖/评论/点赞/通知/搜索），并记录验证步骤到 `deploy/backups/README.md`（或新增 runbook 文档），验证 why.md#r5s1-compose-boot-with-schemas  
  - 验证：已在演练窗口执行“备份→清卷重启→多 schema 重建”，并完成最小烟测清单；详细步骤见 `deploy/backups/README.md`

## 6. Security Check（G9）

- [√] 6.1 执行仓库内安全检查脚本：`scripts/security-check.sh`、`scripts/secret-scan.sh`，确认无新增明文密钥与危险命令；并对“DLQ 回放脚本/DB 初始化脚本”做 EHRB 风险复核（限速/白名单/防误操作）
  > Note: `npm audit` 报告存在中等漏洞（未阻断脚本）；建议后续按依赖升级策略处理。

## 7. Documentation Update（知识库与 docs）

- [√] 7.1 更新系统设计文档：在 `docs/SYSTEM_DESIGN.md` 补充 P0 的一致性边界说明（After-Commit 的能力与局限、P1 Outbox 路线），并在 `docs/DATA_MODEL.md` 更新多 schema 归属与最小权限约定
- [√] 7.2 更新可观测文档：在 `docs/OBSERVABILITY.md` 增加 DLQ 指标/告警/回放说明、user-service 同步调用降级指标与建议查询语句

## 8. Testing（持续验证）

- [√] 8.1 单模块测试：`mvn -pl message-service test`、`mvn -pl user-service test`、`mvn -pl content-service test`、`mvn -pl search-service test`
  > Note: 已通过 `scripts/security-check.sh` 执行 `mvn test`（全模块）+ `frontend` 单测与构建。
- [√] 8.2 Compose 集成验证：使用推荐 compose 组合启动（`deploy/docker-compose.yml` + `deploy/docker-compose.frontend-direct.yml`），完成 5.4 的最小烟测清单
  - 验证：已按推荐 compose 组合启动并完成 5.4 最小烟测清单（登录/发帖/评论/点赞/通知/搜索）
