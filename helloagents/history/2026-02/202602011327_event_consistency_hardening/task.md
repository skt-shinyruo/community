# Task List: 事件一致性与契约治理（Outbox + Like 投影 + API/错误语义）

Directory: `helloagents/plan/202602011327_event_consistency_hardening/`

---

## 1. Outbox 默认安全态（content/social/user）
- [√] 1.1 对齐默认配置：启用 content Outbox（`content-service/src/main/resources/application.yml`），verify why.md#requirement-事件可靠投递outbox-默认开启-kafka-短暂抖动或-broker-重启
- [√] 1.2 对齐默认配置：启用 social Outbox（`social-service/src/main/resources/application.yml`），verify why.md#requirement-事件可靠投递outbox-默认开启-kafka-短暂抖动或-broker-重启
- [√] 1.3 对齐代码默认值：将 Outbox properties 默认值改为 enabled=true（`content-service/src/main/java/com/nowcoder/community/content/outbox/ContentOutboxProperties.java`、`social-service/src/main/java/com/nowcoder/community/social/outbox/SocialOutboxProperties.java`），verify why.md#requirement-事件可靠投递outbox-默认开启
- [√] 1.4 回归：确认 outbox relay 指标可观测（`content-service/src/main/java/com/nowcoder/community/content/outbox/OutboxRelayJob.java`），verify how.md#testing-and-deployment
- [√] 1.5 对齐默认配置：启用 user Outbox（`user-service/src/main/resources/application.yml`），verify why.md#requirement-outbox-relay-健壮性sending-回收--sent-清理
- [√] 1.6 对齐代码默认值：将 UserOutboxProperties 默认 enabled=true（`user-service/src/main/java/com/nowcoder/community/user/outbox/UserOutboxProperties.java`），verify why.md#requirement-outbox-relay-健壮性sending-回收--sent-清理
- [√] 1.7 Outbox relay：增加 SENDING lease 超时回收（content/social/user 的 mapper+service+job），verify why.md#requirement-outbox-relay-健壮性sending-回收--sent-清理
- [√] 1.8 Outbox relay：增加 SENT 保留期清理（默认关闭，可开关）（content/social/user），verify how.md#adr-014-outbox-relay-采用-lease--回收机制避免-sending-永久卡死
- [√] 1.9 Outbox 表索引对齐：为 SENDING 回收/ SENT 清理增加 (status, updated_at, id)/(status, created_at, id) 索引（deploy/mysql-init + tests schema），verify how.md#data-model

## 2. 事件契约：LikeRemoved（可逆投影）
- [√] 2.1 新增事件类型常量 `LikeRemoved`（`common/src/main/java/com/nowcoder/community/common/event/EventTypes.java`），verify why.md#requirement-点赞一致性与热帖分数正确性-用户点赞取消点赞帖子
- [√] 2.2 扩展 social 事件发布接口：增加 publishLikeRemoved（`social-service/src/main/java/com/nowcoder/community/social/event/SocialEventPublisher.java`、`social-service/src/main/java/com/nowcoder/community/social/event/KafkaSocialEventPublisher.java`），verify how.md#api-design
- [√] 2.3 social-service：取消点赞成功时发布 LikeRemoved（`social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`），verify why.md#requirement-点赞一致性与热帖分数正确性

## 3. content-service：点赞 Redis 投影与热度回落
- [√] 3.1 抽取 Redis key 生成器，避免读写两份实现（`content-service/src/main/java/com/nowcoder/community/content/like/RedisLikeQueryService.java`），verify how.md#implementation-key-points
- [√] 3.2 content-service：消费 LikeCreated/LikeRemoved 并更新 Redis Set 投影（`content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`），verify why.md#requirement-点赞一致性与热帖分数正确性
- [√] 3.3 content-service：对 LikeRemoved 同样触发 `postScoreQueue.add(postId)`（`content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`），verify why.md#requirement-点赞一致性与热帖分数正确性
- [√] 3.4 回归：帖子详情点赞展示正确（`content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`），verify why.md#requirement-点赞一致性与热帖分数正确性

## 4. 点赞投影 backfill（减少冷启动窗口）
- [√] 4.1 social-service：新增 internal likes 扫描接口（DAO + Controller）（`social-service/src/main/java/com/nowcoder/community/social/like/LikeMapper.java`、`social-service/src/main/java/com/nowcoder/community/social/like/InternalLikeController.java`（新增）），verify how.md#内部回填运维internal
- [√] 4.2 social-service：为扫描补齐索引（`deploy/mysql-init/025_schema_social.sql`），verify how.md#data-model
- [√] 4.3 content-service：新增 backfill job/ops（从 social-service 扫描回填 Redis Set）（`content-service/src/main/java/com/nowcoder/community/content/like/LikeProjectionBackfillJob.java`（新增）），verify how.md#testing-and-deployment

## 5. 互动写路径：拉黑校验消除 fail-open（message/content）
- [√] 5.1 为投影查询增加“缺失可判定”能力（`message-service/src/main/java/com/nowcoder/community/message/projection/UserModerationProjectionRepository.java`），verify why.md#requirement-私信拉黑校验一致性消除-fail-open-冷启动投影缺失情况下发送私信
- [√] 5.2 message-service：投影缺失时回源 social-service internal 并回填（`message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`、`message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`），verify how.md#implementation-key-points
- [√] 5.3 为投影查询增加“缺失可判定”能力（`content-service/src/main/java/com/nowcoder/community/content/projection/UserModerationProjectionRepository.java`），verify why.md#requirement-评论回复互动拉黑校验一致性消除-fail-open-冷启动投影缺失情况下评论回复
- [√] 5.4 content-service：评论/回复写路径在投影缺失时回源 social-service internal 并回填（`content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`、`content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`），verify why.md#requirement-评论回复互动拉黑校验一致性消除-fail-open

## 6. API 契约稳定化（message DTO 化）
- [√] 6.1 为 letters/conversations 输出新增 DTO（`message-service/src/main/java/com/nowcoder/community/message/api/dto/LetterItemResponse.java`（新增）），verify why.md#requirement-api-契约稳定化dto-私信列表与会话详情展示
- [√] 6.2 Controller 返回 DTO 替代实体（`message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`），verify why.md#requirement-api-契约稳定化dto
- [√] 6.3 前端兼容检查（必要时调整）（`frontend/src/api/services/messageService.js`），verify why.md#requirement-api-契约稳定化dto

## 7. 鉴权错误语义统一（401 vs 400）
- [√] 7.1 social-service：认证缺失返回 UNAUTHORIZED（`social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`），verify why.md#requirement-鉴权错误语义统一
- [√] 7.2 user-service：认证缺失返回 UNAUTHORIZED（`user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`），verify why.md#requirement-鉴权错误语义统一
- [√] 7.3 同类 controller 统一（按 rg 扫描逐步补齐），verify why.md#requirement-鉴权错误语义统一
- [√] 7.4 message-service：UserServiceClient 默认 fail-open=false，避免依赖故障伪装成 400（`message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`），verify why.md#客户端降级默认值不安全可能把依赖故障伪装成-400

## 8. 社交写路径契约可信（禁止客户端注入）
- [√] 8.1 content-service：新增 internal entity resolve 接口（POST/COMMENT -> owner/postId/exists）（`content-service/src/main/java/com/nowcoder/community/content/api/InternalContentController.java` 或新增 `InternalEntityController.java` + 对应 DAO），verify why.md#requirement-社交写路径事件契约可信禁止客户端注入
- [√] 8.2 social-service：新增 ContentServiceClient（internal 调用 content resolve）（`social-service/src/main/java/com/nowcoder/community/social/service/ContentServiceClient.java`（新增）），verify how.md#21-社交写路径契约可信服务端解析-entity-元信息禁止客户端注入
- [√] 8.3 social-service：LikeService 写路径改为“先 resolve 再写入/发事件/计数”，忽略客户端 entityUserId/postId（`social-service/src/main/java/com/nowcoder/community/social/like/LikeService.java`），verify why.md#requirement-社交写路径事件契约可信禁止客户端注入
- [√] 8.4 social-service：FollowController/FollowService 收敛 follow 仅支持 USER（或复用 resolve）（`social-service/src/main/java/com/nowcoder/community/social/follow/FollowController.java`、`social-service/src/main/java/com/nowcoder/community/social/follow/FollowService.java`），verify why.md#requirement-社交写路径事件契约可信禁止客户端注入
- [√] 8.5 配置对齐：补齐 social-service -> content-service internal client 配置（`social-service/src/main/resources/application.yml`、`deploy/nacos-config/social-service.yaml`），verify how.md#security-and-performance

## 9. 热度刷新链路可靠性（post:score）
- [√] 9.1 content-service：PostScoreRefresher 增加 per-post try/catch 与失败回补（可重试异常 re-enqueue）（`content-service/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`、`content-service/src/main/java/com/nowcoder/community/content/score/PostScoreQueue.java`），verify why.md#requirement-热度刷新队列可靠性至少一次--可观测
- [√] 9.2 content-service：增加 refresh 成功/失败/回补指标（`content-service/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`），verify why.md#requirement-热度刷新队列可靠性至少一次--可观测

## 10. Security Check
- [√] 10.1 安全检查：Outbox/ops/internal 权限、token 管理、fail-open/fail-closed 策略核对（参考 `common/src/main/java/com/nowcoder/community/common/internal/InternalTokenFilter.java`、`common/src/main/java/com/nowcoder/community/common/internal/InternalOpsGuardFilter.java`）
- [√] 10.2 安全检查：internal client 的 fail-open 默认值核对（特别是 message/social/user/content 之间调用），verify why.md#客户端降级默认值不安全可能把依赖故障伪装成-400

## 11. Documentation Update
- [√] 11.1 更新架构 ADR 索引与模块说明（`helloagents/wiki/arch.md`、`helloagents/wiki/api.md`）
- [√] 11.2 更新对应模块文档（`helloagents/wiki/modules/content.md`、`helloagents/wiki/modules/social.md`、`helloagents/wiki/modules/message.md`）
- [√] 11.3 更新 `helloagents/CHANGELOG.md`（记录 outbox 默认、LikeRemoved、投影与 API 变更、社交写路径契约可信、post:score 回补）

## 12. Testing
- [√] 12.1 social-service：LikeRemoved 发布测试（`social-service/src/test/java/...`）
- [√] 12.2 social-service：entity resolve 校验测试（伪造 entityUserId/postId 无效；entity 不存在返回 404）（`social-service/src/test/java/...`）
- [√] 12.3 content-service：点赞投影消费测试（`content-service/src/test/java/...`）
- [√] 12.4 content-service：post:score 刷新失败回补测试（`content-service/src/test/java/...`）
- [√] 12.5 message-service：投影缺失回源测试（`message-service/src/test/java/...`）
- [-] 12.6 端到端冒烟：login → 发帖 → 点赞/取消点赞 → 热帖刷新 → 私信拉黑（`deploy/README.md` 或脚本演练；需要 Kafka/Redis/MySQL 真实环境）
- [√] 12.7 outbox：SENDING lease 超时回收 + SENT 清理策略测试（`content-service/src/test/java/...`、`social-service/src/test/java/...`、`user-service/src/test/java/...`）

## 13. Kafka 消费端契约一致性（search-service）
- [√] 13.1 search-service：`PostEventConsumer` 统一使用 `EventEnvelopeParser` + `UnknownEventAction`（unknown type/version 可配置 + 降噪），verify docs/SYSTEM_DESIGN.md#版本治理与-unknown-handling

## 14. 积分链路对齐（user-service）
- [√] 14.1 user-service：支持 LikeRemoved 触发积分回退（`user-service/src/main/java/com/nowcoder/community/user/kafka/PointsEventConsumer.java`）
- [√] 14.2 user-service：积分非负保护（SQL clamp）+ 单测补齐（`user-service/src/main/resources/mapper/user_mapper.xml`、`user-service/src/test/java/com/nowcoder/community/user/kafka/PointsEventConsumerTest.java`）

## 15. Ops Guard 对齐（internal backfill）
- [√] 15.1 common：将 `/internal/*/likes/backfill` 纳入 ops-guard（`common/src/main/java/com/nowcoder/community/common/internal/InternalOpsGuardFilter.java`）
