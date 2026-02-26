# 任务清单: remove-projections-rpc-only

```yaml
@feature: remove-projections-rpc-only
@created: 2026-02-25
@status: completed
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 22/22 (100%) | 更新: 2026-02-25 18:08:05
当前: 全部任务完成（已切换为 RPC 回源）
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 22 | 0 | 0 | 22 |

---

## 任务列表

### 1. social-service：移除内容实体投影

- [√] 1.1 `social/social-service/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java` 改为 Dubbo 调用 `ContentEntityRpcService`（不再读取本地投影）
- [√] 1.2 删除 `social/social-service/src/main/java/com/nowcoder/community/social/projection/` 包并更新 MyBatis 扫描配置
- [√] 1.3 删除 `social/social-service/src/main/java/com/nowcoder/community/social/kafka/ContentEventConsumer.java`（不再消费内容事件写投影）
- [√] 1.4 更新 social-service 单测与 `social/social-service/src/test/resources/schema.sql`（不再建表/依赖投影）

### 2. social-api：补齐 likes 读 RPC（替代 content 的 Redis 投影）

- [√] 2.1 扩展 `social/social-api/src/main/java/com/nowcoder/community/social/api/rpc/SocialReadRpcService.java`：新增 `entityLikeCount/hasLiked`
- [√] 2.2 实现 `social/social-service/src/main/java/com/nowcoder/community/social/rpc/SocialReadRpcServiceImpl.java` 对应方法（调用 LikeService）

### 3. content-service：移除 moderation/block 投影，写路径改 RPC

- [√] 3.1 简化 `content/content-service/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`：直接调用 `UserModerationClient.getStatus` 判断禁言/封禁（移除 read-repair/PROJECTION_MISSING 语义）
- [√] 3.2 新增 `content/content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`（Dubbo 调 `SocialBlockRpcService`），替换 `content/content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java` 的拉黑校验
- [√] 3.3 精简 `content/content-service/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`：移除对投影仓库的无效依赖
- [√] 3.4 删除 `content/content-service/src/main/java/com/nowcoder/community/content/projection/` 与 `content/content-service/src/main/java/com/nowcoder/community/content/kafka/ModerationEventConsumer.java`

### 4. content-service：移除 likes Redis 投影，改为 social RPC

- [√] 4.1 删除 `content/content-service/src/main/java/com/nowcoder/community/content/like/` 现有 Redis 投影实现，新增 RPC 版 `LikeQueryService`
- [√] 4.2 修改 `content/content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`：不再写 Redis Set，仅用于 postScoreQueue 触发

### 5. message-service：移除 moderation/block/userSummary 投影，改 RPC

- [√] 5.1 简化 `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`：直接调用 `UserModerationClient.getStatus` 判断禁言/封禁
- [√] 5.2 修改 `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`：改为 Dubbo 调用 `UserReadRpcService`（移除 user_summary_projection）
- [√] 5.3 新增 `message-service/src/main/java/com/nowcoder/community/message/service/SocialBlockClient.java` 并替换 `message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java` 的拉黑校验
- [√] 5.4 删除 `message-service/src/main/java/com/nowcoder/community/message/projection/` 与相关 Kafka 消费者、ops projection RPC provider

### 6. ops-service 与 *-api：移除投影回填能力

- [√] 6.1 删除 `content/content-api/src/main/java/com/nowcoder/community/content/api/rpc/*ProjectionOpsRpcService.java` 与 `content/content-api/src/main/java/com/nowcoder/community/content/api/rpc/dto/*`（投影写入 DTO），并清理 content-service 对应实现
- [√] 6.2 确认仓库当前无独立 `message-api/` 模块（无需移除；message 领域契约位于 `message-service` 内）
- [√] 6.3 修改 `ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`：移除所有 backfill endpoints 与 Dubbo 引用

### 7. deploy/docs/KB 同步与验证

- [√] 7.1 更新 `deploy/mysql-init/020_schema_content.sql`、`deploy/mysql-init/030_schema_message.sql`、`deploy/mysql-init/025_schema_social.sql`：移除 `*_projection` 建表（代码不再依赖）
- [√] 7.2 更新 `docs/SYSTEM_DESIGN.md` 与 `.helloagents/*.md`：删除/改写“本地投影/回填/PROJECTION_MISSING”相关描述
- [√] 7.3 运行 `mvn test`（按模块分批）确保编译与单测通过

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等
