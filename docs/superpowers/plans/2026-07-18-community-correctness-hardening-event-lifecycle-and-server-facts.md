# Event Lifecycle And Server Facts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每次点赞生命周期提供持久身份，保证帖子终态删除永久压制 projection，并让评论回复的 root/接收人完全由锁定的服务端评论事实推导。

**Architecture:** Social domain 在创建点赞关系时生成 UUIDv7 `relationInstanceId`，持久化并通过 domain event/`contracts.event` 传播；钱包 consumer 优先用 instance 构造幂等 source id。Content hot-feed command 显式携带 terminal deletion，Redis guard 用无 TTL tombstone 支配全部非删除版本。评论命令只携带 direct parent id，ApplicationService 在事务中通过 domain repository 锁 direct parent/root，再由 `CommentDomainService` 推导 thread 和 target author。

**Tech Stack:** Java 21、Spring transactions、MyBatis、MySQL 8、Flyway、Redis Lua、Kafka contracts、JUnit 5、Testcontainers、Vue 3、Vitest、Maven。

---

## Community V011：点赞关系实例

### 写 migration RED 测试

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V011__add_social_like_relation_instance.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`

- [ ] 在 V010 schema 插入至少三条 `social_like`，包括同 actor 不同 target 和同 target 不同 actor；V011 后断言每行 `relation_instance_id` 非空且互不相同，原主键/owner/created_at 不变。
- [ ] 断言列为 `BINARY(16) NOT NULL`，唯一索引名固定 `uk_social_like_relation_instance`。
- [ ] 空库 migration count 从 `10` 更新到 `11`；重复 migrate 为 0；V001 upgrade 后回填同样成立。
- [ ] H2 `schema.sql` 增加 non-null binary UUID 和唯一索引；冻结 `V001__baseline.sql`/manifest 不改。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest=CommunityMigrationTest test
  ```

  预期：V011/列/索引不存在，断言失败。

### 实现唯一回填和约束

- [ ] V011 按四步执行：add nullable column；逐行写不同 UUID binary；add unique index；modify column non-null。历史值只需唯一稳定，不要求 UUIDv7 排序。
- [ ] 在真实 MySQL 验证 `UUID_TO_BIN(UUID())` 的 update 对每行产生不同值；若优化器导致 statement-level 复用，改用基于主键哈希的确定性 16-byte 回填，且碰撞测试覆盖全部 fixture。
- [ ] 不修改 `(user_id, entity_type, entity_id)` 主键；relation instance 是生命周期身份，不替代关系唯一性。
- [ ] 运行 migration GREEN：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest=CommunityMigrationTest test
  ```

  预期：Community 11 个 migration 全部通过空库、upgrade、重复执行和回填断言。
- [ ] 提交 V011：

  ```bash
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V011__add_social_like_relation_instance.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql
  git commit -m "feat(migration): identify each social like lifecycle"
  ```

## 点赞 relation instance 全链路

### 写 domain/repository RED 测试

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/LikeRelation.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/LikeChangedDomainEvent.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeCleanupFenceApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeCleanupTransactionIntegrationTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialWriteTransactionIntegrationTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepositoryTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/LikeDomainServiceTest.java`

- [ ] `LikeRelation` 新签名固定为：

  ```java
  public record LikeRelation(
      UUID relationInstanceId,
      UUID actorUserId,
      int entityType,
      UUID entityId,
      UUID entityUserId
  ) {}
  ```

- [ ] 测试首次 like 的 created event instance 与持久 row 一致；unlike 的 removed event 使用同一个 instance。
- [ ] 测试 unlike 后 re-like 产生新 instance；第二生命周期 created/removed 使用新值，且新旧值不相等。
- [ ] 重复 like/unlike 是 no-op，不发布额外 event；remove CAS 使用错误/旧 instance 返回 false，不删新关系。
- [ ] 内容删除批量扫描/移除每一条原 relation instance，并在 removed event 中原样传播。
- [ ] 更新 cleanup fence 单测中的全部 `LikeRelation` 构造和 repository stub，使每条 fixture 有独立 instance；断言分页清理用完整 relation 做 CAS，而不是退回旧 `setLike(..., false)` 签名。
- [ ] 更新两个事务集成测试的原始 `social_like` INSERT，显式写入不同的 `relation_instance_id`；发布失败回滚后同时断言 relation instance row 与 outbox 均不存在，重试后二者一致。
- [ ] MyBatis test 断言 insert/select/scan 均映射 `relation_instance_id`；`deleteLike` SQL 条件包含 stable relation key 和 expected instance。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='LikeApplicationServiceTest,LikeCleanupFenceApplicationServiceTest,LikeCleanupTransactionIntegrationTest,SocialWriteTransactionIntegrationTest,MyBatisLikeRepositoryTest,LikeDomainServiceTest' test
  ```

  预期：现有模型/表访问没有 instance，编译或断言失败。

### 实现持久生命周期身份

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/LikeScanDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/LikeMapper.java`

- [ ] 给 `LikeApplicationService` 注入共享 `UuidV7Generator`；只有从不存在到 like 的 candidate 使用 `idGenerator.next()`，replay/no-op 不生成发布身份。
- [ ] repository API 接收/返回完整 `LikeRelation`，至少提供：

  ```java
  boolean addLike(LikeRelation relation);
  boolean removeLike(LikeRelation expectedRelation);
  Optional<LikeRelation> findLike(UUID actorUserId, int entityType, UUID entityId);
  ```

- [ ] ApplicationService 删除“先 `isLiked` 再构造无实例 relation”的路径；unlike 先 `findLike`，然后用完整 relation CAS delete。
- [ ] entity owner like count 与 relation insert/delete 保持同一 `@Transactional`；CAS loser 不调整 count、不发 event。
- [ ] `LikeDomainService.likeChangedEvent(...)` 显式接收 `LikeRelation`，event 同时保留 stable `relationKey` 与 `relationInstanceId`。
- [ ] Mapper insert/select/scan 增加 binary UUID；delete：

  ```sql
  delete from social_like
  where user_id = ? and entity_type = ? and entity_id = ?
    and relation_instance_id = ?
  ```

- [ ] 批量清理不能先执行 `deleteLikesByEntity` 后猜 instance；必须 scan relation -> CAS remove -> publish，直到空页。
- [ ] 运行 GREEN，预期 domain/repository/application 测试通过。
- [ ] 提交持久链路：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/social \
          backend/community-app/src/test/java/com/nowcoder/community/social
  git commit -m "feat(social): persist like relation lifecycle identity"
  ```

### 写 contract 与钱包兼容 RED 测试

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/LikePayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/event/WalletRewardKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardProjectionApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/SocialContractEventCodecTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/infrastructure/event/WalletRewardKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRewardProjectionApplicationServiceTest.java`

- [ ] contract codec test 断言新 payload JSON 可选字段 `relationInstanceId` 正确 round-trip；缺字段的 legacy JSON 仍 decode 为 null。
- [ ] publisher test 断言 gate enabled 时 created/removed 都写入 instance；event id 仍是每次 event 的唯一 id，stable relationKey 保持通知/成长系统兼容。
- [ ] wallet listener 新 payload source id 精确为 `<relationInstanceId>:created` / `:removed`；legacy payload source id 回退 `<relationKey>:created` / `:removed`。
- [ ] 重复投递 created/removed 只产生一次各自 wallet request；unlike/re-like 的第二 instance 产生新的 reward/revoke 生命周期。
- [ ] remove-before-create 测试保留 idempotent 行为，不能因到账顺序把两个 instance 合并。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='SocialContractEventCodecTest,OutboxSocialDomainEventPublisherTest,WalletRewardKafkaListenerTest,WalletRewardProjectionApplicationServiceTest' test
  ```

  预期：payload 无字段，wallet 仍用 relationKey，断言失败。

### 实现加法 contract 与 producer gate

- [ ] `LikePayload` 增加 nullable UUID getter/setter；不得让 `api.*` import 此 `contracts.event` 类型。
- [ ] `OutboxSocialDomainEventPublisher` 增加配置 `social.events.relation-instance-publishing-enabled`，默认 false；false 时仍发布 legacy-compatible payload，true 时写 instance。
- [ ] wallet `validateLikePayload` 继续要求 legacy relationKey，以支持旧/新事件；`likeSourceId` 优先 instance：

  ```java
  String base = payload.getRelationInstanceId() == null
      ? payload.getRelationKey().trim()
      : payload.getRelationInstanceId().toString();
  return base + ":" + action;
  ```

- [ ] 其他 consumer（notice、growth、hot-feed）继续使用 stable relationKey；新增字段是加法，不能迫使它们改变分组语义。
- [ ] application/Nacos 配置加入 gate，第一轮部署为 false；所有钱包 consumer 升级后才改 true。
- [ ] 运行 GREEN，预期 codec/publisher/wallet 测试通过。
- [ ] 提交 event/consumer：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/social/contracts \
          backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event \
          backend/community-app/src/main/java/com/nowcoder/community/wallet \
          backend/community-app/src/test/java/com/nowcoder/community/social \
          backend/community-app/src/test/java/com/nowcoder/community/wallet \
          backend/community-app/src/main/resources/application.yml \
          deploy/nacos/config/community-app.yaml
  git commit -m "feat(events): publish like lifecycle identity compatibly"
  ```

## 帖子终态 deletion tombstone

### 写 command/guard RED 测试

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedProjectionGuard.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotFeedProjectionGuardTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`

- [ ] command 增加最后字段 `boolean terminalDeletion`；listener 只对 `PostDeleted` 设 true，published/updated/comment/like 均 false。
- [ ] guard RED 顺序：普通 v10 commit -> deletion v5 仍 accepted/commit tombstone -> 普通 v4/v5/v11 全 rejected -> 重复同 deletion event rejected为幂等。
- [ ] deletion 测试证明 event/version 较旧也不能被 stale-version 规则挡住；tombstone key 无 TTL。
- [ ] ApplicationService 测试在数据库 post 暂时仍显示 active 时收到 terminal deletion，仍先删除 feed/summary/detail projection并 commit tombstone；不能依赖异步 DB 可见顺序。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='PostHotFeedProjectionKafkaListenerTest,RedisHotFeedProjectionGuardTest,PostHotFeedProjectionApplicationServiceTest' test
  ```

  预期：command/guard 无 deletion 语义，高版本普通 event 可再次被接受，测试失败。

### 实现永久 tombstone Lua 状态机

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotFeedProjectionGuard.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/PassthroughHotFeedProjectionGuard.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java`

- [ ] `HotFeedProjectionGuard.tryBegin` 增加 deletion 参数，`ProjectionAttempt` 持有 `terminalDeletion`；所有实现/测试 helper 同步签名。
- [ ] Redis 增加 `post:feed:hot:projection:tombstone:<postId>`。BEGIN/CURRENT 对非删除先检查 tombstone并拒绝；删除跳过 stale version比较但仍需要 event idempotency和 lock token。
- [ ] COMMIT Lua 在持有 token 时原子执行：写 event identity TTL、把 max version 更新为 `max(current, incoming)`、删除时 `SET tombstone 1` 不带 EX/PX、释放 lock。
- [ ] 删除事件重放看到 event key/tombstone后 no-op；abort 只释放自己 token 的 lock，不能删除 tombstone。
- [ ] `PostHotFeedProjectionApplicationService.project` 对 `terminalDeletion` 不查询 like count/重算 score，直接 evict read models并在 transaction completion commit attempt。
- [ ] 非删除路径即使读取 active post，也会在 BEGIN/CURRENT 被 tombstone 拒绝，不能 upsert hot feed。
- [ ] 运行 GREEN，预期 listener/Redis/application 测试通过。
- [ ] 提交 tombstone：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/content/application \
          backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure \
          backend/community-app/src/test/java/com/nowcoder/community/content
  git commit -m "fix(content): make post deletion dominate hot feed projection"
  ```

### 对齐帖子删除版本事实

**Files:**

- Modify: `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepositoryTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java`

- [ ] persistence test 删除帖子后断言 `update_time == deleted_time`；published `PostDeleted` 的 version 基于该删除时间且 event id 保持 `content:PostDeleted:<postId>`。
- [ ] `updateModerationDeleteMeta` 同一 update 加：

  ```sql
  update_time = #{deletedTime}
  ```

- [ ] 重复删除 affected=0，不改变已记录删除时间/event identity。
- [ ] 运行：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='MyBatisPostContentRepositoryTest,OutboxContentEventPublisherTest' test
  ```

  预期：删除时间/version/event id 断言通过。
- [ ] 提交版本事实：

  ```bash
  git add backend/community-app/src/main/resources/mapper/discusspost-mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/content
  git commit -m "fix(content): advance post version at terminal deletion"
  ```

## 评论 direct parent 与服务端接收人

### 删除 transport 伪造字段的 RED 测试

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CreateCommentRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/api/action/CommentActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapter.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapterTest.java`
- Modify: `frontend/src/api/services/postService.js`
- Modify: `frontend/src/api/services/postService.test.js`

- [ ] Controller JSON 测试发送 `replyToUserId`/`targetId`，断言 DTO/command 不存在这些属性，捕获 command 只含 `userId, postId, parentCommentId, content`。
- [ ] 同步 `CommentActionApi.addComment` 改为：

  ```java
  UUID addComment(
      UUID userId,
      String idempotencyKey,
      UUID postId,
      UUID parentCommentId,
      String content
  );
  ```

- [ ] frontend service test 断言 addComment payload 最多只有 `content` 和 `parentCommentId`，调用方传入额外 reply user 也不会发送。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='PostControllerUnitTest,CommentActionApiAdapterTest' test
  cd ../frontend
  npm test -- --run src/api/services/postService.test.js
  ```

  预期：当前 DTO/command/API/service 均暴露 reply user，断言失败。

### 收窄 Controller、command、API

- [ ] 删除 `CreateCommentRequest.replyToUserId`、`CreateCommentCommand.replyToUserId` 和 legacy `CommentApplicationService.create/addComment(... targetId ...)` overload。
- [ ] `PostController.addComment` 只从认证 subject、path post id、body parent/content 组 command；不做 parent lookup。
- [ ] `CommentActionApiAdapter` 只把 foreign owner-domain caller 的 direct parent 交给同域 ApplicationService；不自行查评论。
- [ ] `CommentApplicationService.createCommentRequestHash` 删除 reply user，只哈希 post/direct parent/content。
- [ ] frontend `addComment` 删除 `replyToUserId` payload 逻辑；展示响应模型里的服务端 `replyToUserId` 保留。
- [ ] 再运行 transport/API 测试，预期通过。
- [ ] 提交传输收窄：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/content/controller \
          backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java \
          backend/community-app/src/main/java/com/nowcoder/community/content/api/action/CommentActionApi.java \
          backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapter.java \
          backend/community-app/src/test/java/com/nowcoder/community/content \
          frontend/src/api/services/postService.js frontend/src/api/services/postService.test.js
  git commit -m "fix(comments): remove client supplied reply recipient"
  ```

### 写锁内推导 RED 测试

**Files:**

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentReplyContext.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/domain/service/CommentDomainServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCreateConcurrencySpringTest.java`

- [ ] 顶级评论：无 parent，root/parent/reply user 为 null，notification target 为 post author。
- [ ] 回复 root：direct parent=root，root id=root.id，stored parent=root.id，reply user/target=root.userId。
- [ ] 回复 nested：direct parent=reply.id，root id=reply.rootCommentId，stored parent=reply.id，reply user/target=reply.userId。
- [ ] inactive parent、post mismatch、direct parent 指向不存在/inactive root、root thread mismatch 均返回隐藏式 NOT_FOUND。
- [ ] concurrency test 覆盖 direct reply 删除与整棵 root thread 删除两种竞争；create transaction 的 `FOR UPDATE` 重读必须拒绝 stale parent，不能插入 comment或发送通知，并且 root 删除与 nested reply 创建不能因反向锁顺序死锁。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='CommentDomainServiceTest,CommentApplicationServiceTest,CommentCreateConcurrencySpringTest' test
  ```

  预期：当前只允许 root parent且信任 raw reply user，nested/竞态断言失败。

### 实现 direct parent/root 锁与领域推导

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/comment-mapper.xml`

- [ ] `CommentRepository` 增加：

  ```java
  Optional<CommentReplyContext> lockReplyContext(UUID postId, UUID directParentCommentId);
  ```

- [ ] MyBatis repository 先非锁定读取 direct parent 以发现 `rootCommentId`，随后固定按“root first、direct parent second（两者不同时）”调用 `selectByIdForUpdate`，最后重新校验 direct parent 的 id/root/post/status。不能先锁 nested direct parent 再锁 root，否则会与 root thread 删除形成反向锁顺序。
- [ ] `CommentReplyContext` 包含 `CommentSnapshot directParent` 和 `CommentSnapshot root`；两者相同表示直接回复 root。
- [ ] `CommentDomainService.resolveCreateTarget(postId, postAuthorId, context)` 只使用锁定事实，验证 active/post/thread 并推导 root/direct parent/target author。
- [ ] `CommentApplicationService.createInsideTransaction` 在 sanitize/insert 前获取 context；block query 使用推导 target author；`CommentDraft.replyToUserId` 和 `CommentCreatedDomainEvent.targetUserId` 都使用同一结果。
- [ ] 从 `MyBatisCommentRepository.create` 删除当前只允许 `parentCommentId == rootCommentId` 的 `lockRootForReply`；锁与重校验统一由 `lockReplyContext` 完成，`create` 只映射已经过领域校验的 draft 并插入。ApplicationService 的同一事务保证锁持续到 insert/commit。
- [ ] 插入与锁保持在 `@Transactional create` 的同一事务；repository 不提交独立事务。
- [ ] 再运行 domain/application/concurrency 测试，预期全部通过。
- [ ] 提交服务端事实：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
          backend/community-app/src/main/java/com/nowcoder/community/content/domain \
          backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence \
          backend/community-app/src/main/resources/mapper/comment-mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/content
  git commit -m "fix(comments): derive reply thread from locked parent"
  ```

### 修正前端 direct parent

**Files:**

- Modify: `frontend/src/views/post-detail/usePostDetailLoader.js`
- Modify: `frontend/src/views/PostDetailView.test.js`

- [ ] 测试点击 root 回复后提交 root id；点击 nested reply 后提交该 reply id，而不是 root id；payload 不含 reply user。
- [ ] `startReply(c, reply)` 保存 `_replyParentCommentId = reply?.id || c.id`；可保留显示用的 reply username，但不能保存/发送其 user id 作为权威参数。
- [ ] `submitReply(c)` 调用：

  ```js
  addComment(postId, {
    content: c._replyContent,
    parentCommentId: c._replyParentCommentId
  })
  ```

- [ ] 成功/cancel 后清空 `_replyParentCommentId`；列表响应中的 `replyToUserId` 继续用于“回复 @用户”展示。
- [ ] 运行：

  ```bash
  cd frontend
  npm test -- --run src/views/PostDetailView.test.js src/api/services/postService.test.js
  ```

  预期：root/nested/payload 测试全部通过。
- [ ] 提交前端 parent 语义：

  ```bash
  git add frontend/src/views/post-detail/usePostDetailLoader.js frontend/src/views/PostDetailView.test.js
  git commit -m "fix(frontend): send direct parent for comment replies"
  ```

## 综合验证与兼容发布

- [ ] 运行 backend 聚焦测试：

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='*Like*Test,*HotFeed*Test,*Comment*Test,CommunityMigrationTest' test
  ```

  预期：点赞 lifecycle、tombstone 和评论服务端推导测试全部通过。

- [ ] 运行 migration 和架构守卫：

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am test
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：Community 11 个 migration；DDD 守卫全绿。

- [ ] 运行前端：

  ```bash
  cd frontend
  npm test -- --run src/views/PostDetailView.test.js src/api/services/postService.test.js
  npm run build
  ```

  预期：测试/build 退出码为 `0`。

- [ ] 静态扫描输入边界：

  ```bash
  rg -n 'replyToUserId|targetId' \
    backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CreateCommentRequest.java \
    backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java \
    backend/community-app/src/main/java/com/nowcoder/community/content/api/action/CommentActionApi.java \
    frontend/src/api/services/postService.js
  ```

  预期：无匹配；domain draft和响应展示模型可保留服务端推导字段。

- [ ] 兼容顺序：维护窗口停止旧 social writer -> 执行 V011 -> 部署 gate=false 的新 producer和 optional consumer -> 确认全部钱包 consumer 兼容 -> gate=true 开启 instance payload。
- [ ] Redis tombstone 不设 TTL；发布后抽查删除 post 的 tombstone存在，并重放更高 version like/comment event，确认 feed/cache 不复活。
- [ ] `git diff --check`，预期无 whitespace 错误。
