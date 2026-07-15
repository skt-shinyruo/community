# 后端架构现状审计

本文记录对 `backend/community-app` 具体代码、调用链、事务边界、跨域依赖和架构守卫的审计结果。审计基线日期为 2026-07-14。

本文是问题清单和整改依据，不替代架构规则 SSOT [architecture.md](../../docs/handbook/architecture.md)、系统协作模型 SSOT [system-design.md](../../docs/handbook/system-design.md) 或可靠性机制 SSOT [reliability.md](../../docs/handbook/reliability.md)。问题修复后，应同步更新本文结论和相应 SSOT 文档。

除非路径显式以 `backend/`、`deploy/` 或 `docs/` 开头，本文中的业务 Java 路径均相对于 `backend/community-app/src/main/java/com/nowcoder/community/`。

## 总体判断

项目已经从传统的 controller-service-mapper 结构迁移为治理力度较强的 package-scoped monolith：controller 基本进入同域 `ApplicationService`，mapper / dataobject 大体收敛在 `infrastructure.persistence`，主事件链采用 outbox，market-wallet 也具备持久化 saga command 和恢复机制。

当前主要完成的是包结构层面的 DDD。领域所有权、事务一致性、跨域依赖、事件契约和领域模型成熟度尚未同步收敛。现有 ArchUnit 测试可以守住大量显式包依赖，但无法识别提交后任务可靠性、同步依赖环、弱类型事件泄漏和跨资源一致性等语义问题。

## 问题判定方法与共同根因

本次审计不以“包名看起来像 DDD”作为通过条件，而是沿真实调用链逐项回答四个问题：业务不变量由谁保护，事务提交点在哪里，提交后的副作用是否可恢复，跨域契约是否在 owner 边界完成转换。只要其中一个问题没有可执行答案，即使依赖方向满足 ArchUnit，也仍然属于架构缺陷。

问题集中在以下五个共同根因：

1. **结构治理先于语义治理**：代码已经进入 `application/domain/infrastructure` 包，但部分业务规则仍由 ApplicationService 分支、SQL 条件、Spring 异常或 after-commit callback 共同决定，领域模型没有成为唯一不变量入口。
2. **把“事务已提交”误当成“用例已完成”**：MySQL commit 之后还依赖 Kafka、Social 或 OSS 完成关键事实，但这些动作没有统一的 durable command、幂等状态、重试、DLQ 和 reconciliation，故障窗口被日志掩盖。
3. **跨域 owner 只在类型层面存在**：同步协作虽使用 `api.*`，但 owner 概念仍可能分裂在两个域，或者通过 infrastructure adapter 隐藏反向调用；异步协作也可能把 producer contract 一直带入 consumer domain。
4. **运行时可选实现改变正确性语义**：Social 的 DB/Redis repository 共用同一领域接口，却拥有不同事务能力，迫使 application 询问底层技术并手写补偿。配置切换因此不只是部署选择，而是在切换一致性模型。
5. **守卫只覆盖已知类名，不覆盖系统不变量**：手工域清单、点名事务入口和单域特例让新增模块、顶层 `infra`、同步依赖环、schema 漂移等问题可以在测试全绿时进入主干。

问题与对应的可观察失败如下：

| 问题族 | 最坏失败 | 当前控制为什么不足 | 必须新增的可执行证明 |
| --- | --- | --- | --- |
| Content 删除 -> Social 清理 | 已删除内容仍残留点赞，且永久不再修复 | after-commit 调用无持久化；现有删除事件没有 Social consumer | commit/宕机、重复投递、并发点赞、DLQ replay 和对账测试 |
| Content <-> OSS | 引用泄漏、重复引用或对象状态与帖子状态永久分叉 | rollback/after-commit 补偿仍是远程 best effort | durable command 状态机、响应丢失、finalize 前宕机和漂移扫描测试 |
| Social DB/Redis 双写模型 | 主记录、计数和 outbox 只成功一部分 | repository 技术能力开关与内存补偿不能提供原子性 | 真实事务回滚集成测试和非 DB 配置 fail-fast |
| 同步跨域环 | 查询扇出、owner 漂移，未来拆分后形成可用性环 | 单 artifact 编译成功不代表域图无环 | foreign `api.*` edge exact set 和 SCC 无环守卫 |
| Refresh token owner 分裂 | 撤销、轮换和 security version 规则分散 | Auth repository implementation 实际跨域调用 User | Auth 单 owner 的 repository/rotation 测试与反向依赖禁令 |
| 弱类型事件 | type/payload 错配到运行时才失败，producer model 泄漏进 consumer | `Object payload` 和 listener 中的零散 `instanceof` 没有编译期约束 | wire golden、集中 decoder 和 consumer ACL ArchUnit |
| Schema 演进 | 环境被错误标记 baseline、漏跑 DDL 或升级丢数据 | 首次建卷 SQL、确认口令和 Flyway history 都不验证真实 schema 等价性 | catalog exact comparison、真实 legacy upgrade 和 reactor/compose contract test |

## 问题清单

### P0：关键跨域副作用可能永久丢失

删除帖子和评论后，点赞清理由事务提交后回调直接同步调用 Social：

- `content/application/PostPublishingApplicationService.java:148-158`
- `content/application/PostModerationApplicationService.java:71-84`
- `content/application/CommentApplicationService.java:236-268`

`AfterCommitExecutor` 明确声明不保证最终执行成功：

- `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java:10-17`

回调没有持久化、重试和恢复机制。进程在 commit 后退出，或者 Social 调用临时失败，都会直接丢失清理任务。帖子删除回调没有捕获异常，还可能形成“数据库已提交，但请求返回失败”的结果。

Comment 路径捕获并吞掉异常，同时注释声称 event / outbox 是下游修复来源。然而 Social 域不存在 `POST_DELETED` 或 `COMMENT_DELETED` 消费者。当前 `POST_DELETED` 由 Search 和 Hot Feed 投影处理，`COMMENT_DELETED` 由 Hot Feed 投影处理，这些消费者都不执行点赞清理。因此该 outbox 并不能补偿点赞清理。

整改方向：让 Social 幂等消费 `PostDeleted` / `CommentDeleted`，或者由 Content 写入专用 durable cleanup command。消费者必须具备重试、死信处理和对账能力，HTTP 同步调用只能作为非关键加速路径。

### P0：MySQL 与 OSS 的一致性依赖易失补偿

发帖和编辑帖子在数据库事务内调用远程 OSS `bindReference`：

- `content/application/PostPublishingApplicationService.java:103-145`
- `content/application/PostPublishingApplicationService.java:242-275`
- `content/infrastructure/oss/OssPostMediaStorageAdapter.java:118-143`

远程调用会延长数据库事务。OSS 绑定成功而后续数据库操作失败时，代码通过 rollback callback 再调用 OSS 释放引用；补偿异常被吞掉。删除旧媒体引用则先提交数据库状态，再通过 `AfterCommitExecutor` best effort 释放 OSS 引用，同样吞掉失败。

这无法原子覆盖 MySQL 和 OSS，可能留下永久引用泄漏。Drive 已经采用显式状态机和恢复任务，Content media 却仍使用临时回调，两套跨资源一致性策略不统一。

整改方向：将媒体绑定和释放建模为持久化状态机或 outbox saga command，在数据库事务内只记录目标状态，由可重试 worker 调用 OSS，并提供超时扫描和对账恢复。

### P1：事务边界的实际所有权与架构声明不一致

基础设施仓储自行开启事务：

- `user/infrastructure/persistence/MyBatisUserRepository.java:110,124,138,202`
- `social/infrastructure/persistence/MyBatisLikeRepository.java:65`

这与“ApplicationService 负责事务边界”的规则不一致。即使这些事务通常会加入外层事务，它们仍使独立调用仓储方法时的原子范围取决于基础设施实现，并掩盖用例真正需要的事务范围。

现有守卫没有覆盖这一不变量：

- `DddLayeringArchTest` 只禁止 `content.infrastructure.persistence` 持有事务。
- `TransactionBoundaryArchTest` 只点名检查 6 个 application entry method，并不验证所有写用例。

Social 的存储差异还泄漏进领域仓储：`LikeRepository.requiresExplicitCompensation()` 告诉 application 当前实现是否参与数据库事务；`LikeApplicationService` 据此手工注册 Spring transaction synchronization。Redis 补偿失败只记录 warning，不能保证收敛。

整改方向：移除 persistence implementation 上的事务声明，将完整用例事务放到同域 `ApplicationService`；将跨存储一致性设计为明确的 application saga / durable command，而不是 domain repository 的技术能力开关。

### P1：跨域同步依赖形成环和查询扇出

扫描 application 对 foreign `api.*` 的依赖后，可见以下同步依赖环：

```text
content <-> social
content <-> user
social  <-> user
user    <-> wallet
growth   -> wallet
```

代表性依赖包括：

- Content 调用 Social 点赞清理、封禁查询和 User 治理查询。
- Social 通过 `ContentEntityResolver` 回查 Content，同时查询 User。
- Wallet 转账和管理操作查询 User。
- User 资料页反向查询 Content、Growth、Social 和 Wallet。

所有业务域都编译在同一个 `community-app` artifact 中，编译器不会阻止这些环。目前调用发生在同 JVM，主要成本是边界耦合、数据库 / Redis 查询扇出和不可独立演进；如果未来拆分服务，这些环会立即变成可用性环。

`GET /api/users/{id}` 是最明显的例子：

- `UserReadApplicationService.getProfile()` 同步查询 Wallet 余额和状态。
- `UserProfileApplicationService.get()` 随后没有使用这两个 Wallet 字段，又调用 Growth 一次、Social 最多四次。

一次资料读取因此包含最多七次 foreign API 调用，其中两次 Wallet 查询完全无效。资料聚合职责也被放进了 User owner domain。

整改方向：删除无用 Wallet 查询，将资料页聚合迁移到独立 query composition / read model 边界；为业务域定义允许的有向依赖图，并用 ArchUnit 检查无环性。

### P1：刷新令牌的领域所有权不清晰

DB refresh token 的实际调用链为：

```text
RefreshTokenApplicationService
  -> auth RefreshTokenRepository
  -> DbRefreshTokenRepository
  -> RefreshTokenSessionPort
  -> RefreshTokenSessionApplicationPortAdapter
  -> user.api.*
  -> user RefreshTokenSessionApplicationService
```

相关代码：

- `auth/application/RefreshTokenApplicationService.java`
- `auth/infrastructure/persistence/DbRefreshTokenRepository.java`
- `auth/infrastructure/api/RefreshTokenSessionApplicationPortAdapter.java`
- `user/application/RefreshTokenSessionApplicationService.java`

这不符合规定的 `caller ApplicationService -> owner api.* -> owner ApplicationService`：真实跨域调用被隐藏在 Auth infrastructure 中。

更深层的问题是概念所有权分裂。表名是 `auth_refresh_token`，token rotation、secret、family 语义和 `RefreshTokenRepository` 位于 Auth；User 又拥有 `RefreshTokenSession` domain model、repository、application service 和 published API。同一概念在两个域之间多次转换，包结构满足形式，但 owner 不明确。

整改方向：优先让 Auth 完整拥有 refresh token session 和持久化。如果必须由 User 托管，则 Auth ApplicationService 应直接调用 User `api.*`，并删除伪装成本域 repository implementation 的跨域适配链。

### P1：异步契约弱类型化，消费域反腐转换不完整

三类事件 envelope 都使用 `Object payload`：

- `content/contracts/event/ContentContractEvent.java`
- `social/contracts/event/SocialContractEvent.java`
- `user/contracts/event/UserContractEvent.java`

listener 因此重复执行 `type string -> JsonNode -> payload class` 转换。类型与 payload 不匹配只能在运行时发现，契约演进也缺少编译期约束。

Notice 的泄漏更深：`ProjectContentNoticeCommand`、`ProjectSocialNoticeCommand` 和 domain model `NoticeProjection` 都继续保存 `Object payload`。`NoticeProjectionApplicationService` 直接 import Content / Social 的 `contracts.event` payload，并用 `instanceof` 分派。listener 没有真正完成反腐转换，producer contract 被带入 consumer application，甚至进入 consumer domain model。

整改方向：listener 必须把 foreign event 映射为 Notice 自己的强类型 command，例如 `ProjectCommentNoticeCommand`、`ProjectLikeNoticeCommand`。Notice application 和 domain 不应 import producer contract，也不应保存无类型 `Object`。

### P1：在途 Community migration 草稿仍可能错误标记生产基线

工作区已经出现 `backend/community-db-migrations` 草稿，这是正确方向，但当前实现还不能作为生产 migration 入口验收：

- `CommunityMigrationRunner.java:68-74` 只比较固定确认口令，然后直接调用 `flyway.baseline()`。口令证明操作者授权了动作，却不证明现有库与 V001 的表、列、索引、约束和默认值等价。任意非空库都可以被标记为 version 1，V001 随后会被永久跳过。
- `CommunityMigrationTest.java:94-117` 的“升级”只创建 `migration_upgrade_probe`，baseline 后再执行测试专用 V002。它没有构造当前受支持的 Community legacy schema，也没有证明 user、wallet、content、outbox 等真实业务数据在升级后保留。
- `CommunityMigrationApplication.java:17-23` 允许通过 `COMMUNITY_MIGRATION_LOCATIONS` 选择任意 classpath location，而开发 seed 与生产 migration 一起打进 shaded JAR。一次错误的生产环境变量就可以执行 `db/dev-seed/community/R__development_seed.sql`。
- `CommunityMigrationTest.java:22` 使用 `disabledWithoutDocker = true`。如果 CI 没有 Docker，所有真正执行 MySQL SQL 的测试都会被标记 skipped，纯资源布局测试仍可能让 module 看起来通过。
- 空库测试只抽查少量表名，不检查完整列、索引、约束、默认值和 reference data。`V001__baseline.sql:666-675,751-755` 的 `category.id` 没有默认值，但 seed 没有提供 `id` 且使用 `insert ignore`，MySQL 只产生 warning，三个分类实际不会被创建；当前测试无法发现。
- 工作区中的 `backend/pom.xml`、single/cluster compose、bootstrap script 和 `community_migration_contract.sh` 已有接入草稿，但尚未经过 wave barrier。特别是 `community-dev-seed` 只检查 `COMMUNITY_DEV_SEED_ENABLED`，没有校验 `DEPLOYMENT_ENVIRONMENT=development`，并且直接用 application DML 账号执行挂载的 seed SQL；这仍允许生产误开。compose contract 当前也只验证服务和字符串存在，没有启动 runner 后验证账号权限与 history。

已经确认的正向约束也必须保留：V001 与 010、011、020、031、032、033、040、050、060、080、090 Community schema 文件在去除注释和 `USE community` 后逐语句一致；它排除了 IM、OSS、Nacos、XXL-JOB 和开发用户 seed；runner 保持 `baselineOnMigrate(false)`、`cleanDisabled(true)`，且不提供 `repair` 动作。

整改方向：baseline 必须先把 `information_schema` catalog 与随 V001 发布的 canonical manifest 做 exact comparison；生产 CLI 固定 production location，开发 seed 使用显式 development-only action；升级测试从真实 legacy SQL 建库并写入真实业务数据；Docker 不可用必须让要求 SQL 验证的 CI job 失败。module、compose、最小权限账号和 development gate 全部通过 barrier 之前，MIG-01 状态只能是 `IN_PROGRESS`。

### P2：领域模型整体仍偏贫血，规则散落在 application 和 SQL

当前 94 个 domain model 中，27 个仍包含公开 setter。典型例子：

- `user/domain/model/UserAccount` 是没有行为的 record。
- `wallet/domain/model/WalletAccount` 几乎只有持久化字段和 setter。
- `content/domain/model/Comment` 的状态和删除字段均可任意修改。
- `MarketOrder` 已有状态检查和 transition model，但公开 setter 仍允许绕过不变量。

仓储接口也暴露大量 SQL-shaped 状态操作，例如：

- `content/domain/repository/PostRepository.markDeletedByAuthor/markTop/markWonderful`
- `market/domain/repository/MarketOrderRepository.markDelivered/markEscrowSucceeded/markReleaseSucceeded/...`

这意味着 aggregate 尚未稳定成为一致性边界。规则同时存在于 ApplicationService、DomainService、实体断言、repository 方法名和 SQL 条件中。相应地，application orchestration 持续膨胀：`DriveUploadApplicationService` 649 行、`MarketOrderApplicationService` 443 行、`PostReadApplicationService` 368 行、`PostPublishingApplicationService` 365 行。

整改方向：按高风险写模型逐步收敛，不做一次性重写。优先让 Wallet、Comment、Post 和 MarketOrder 的状态变化通过 aggregate behavior 产生明确 transition / domain event，repository 负责持久化 aggregate 或 transition，不再独立表达业务动作。

### P2：基础设施和 HTTP 语义仍向内泄漏

`common.exception.ErrorCode` 强制所有错误码实现 `getHttpStatus()`。30 个 domain 类直接使用 `BusinessException`，共约 136 处引用，因此 domain 虽未 import Spring HTTP 类型，却间接依赖 HTTP status 语义。`LikeApplicationService.isContentNotFound()` 甚至使用 `errorCode.getHttpStatus() == 404` 判断业务错误。

application 还存在两类技术泄漏：

- 8 个 application 类直接 import 顶层 `com.nowcoder.community.infra.*`，包括 idempotency helper 和 pagination。
- 15 个 application 类 import `DataIntegrityViolationException` 或 `DuplicateKeyException`，把数据库唯一约束实现语义带入用例层。

`DddLayeringArchTest.application_must_not_depend_on_transport_or_infrastructure` 只匹配 `..infrastructure..`，不会命中顶层 `com.nowcoder.community.infra.*`。

整改方向：领域错误只表达稳定的业务错误标识或类别，由 controller advice 映射 HTTP status；repository / technical port 将 duplicate-key 等异常翻译成 domain/application semantic result；将纯 application helper 移出 `infra`，或通过 application-owned port 使用真正的基础设施能力。

### P2：架构守卫存在系统性盲区

`ArchitectureRulesSupport.CORE_DOMAINS` 是手工维护的集合，包含当前不存在的 `message`，且没有把实际一级业务包与清单自动对账。测试只断言集合必须包含若干已知名称；新增业务域如果没有加入清单，多项跨域规则会自动跳过。

其他盲区包括：

- application infrastructure 规则漏掉顶层 `infra`。
- transaction ownership 规则只覆盖 Content persistence 和少量点名入口。
- 多项规则以 Content、Auth、Market 等单域特例表达，而不是统一层次不变量。
- 单个 Maven artifact 不提供业务域之间的编译期依赖约束。

整改方向：从实际包含 `domain` / `application` 的一级包自动发现业务域，与显式分类做 exact reconciliation；把单域规则提升为全业务域规则；加入 domain dependency cycle 检查。中期可考虑用 Maven module、JPMS 或 Spring Modulith verification 增加编译期 / 模块级约束。

### P2：数据库缺少可演进的 migration 机制

项目没有 Flyway 或 Liquibase。`deploy/mysql/community` 中的 SQL 通过 `/docker-entrypoint-initdb.d` 只在 MySQL 数据卷首次创建时执行。

`data-and-storage.md` 明确说明 baseline SQL 只描述当前最终结构，不包含历史迁移；开发环境结构不匹配时直接重建数据卷。这适合本地演示，但无法支撑共享环境或生产数据库的版本审计、滚动升级和可控回滚。

整改方向：引入版本化 migration，baseline 只用于全新环境；所有结构变更以不可变 migration 文件前进，并在 CI 验证从受支持基线升级到当前版本。

## 修改方案

本节把前面的审计结论转换为可执行计划。计划修订日期为 2026-07-15。所有生产代码修改都使用 TDD：先建立能够稳定复现问题或表达目标边界的失败测试，再做最小实现，最后清理旧路径和增加防回归守卫。

### 目标状态

完成全部任务后，系统应满足以下可验证结果：

1. Content 删除事实提交后，即使进程退出、Kafka 重试或消费者重复投递，Social 点赞最终都会清理，并且删除与并发点赞之间不存在残留窗口。
2. Content 与 OSS 之间的上传、引用绑定和引用释放均通过可重放的状态机和 durable command 收敛；数据库事务内不执行远程 OSS 调用。
3. 事务只由同域 `*ApplicationService` 建立；domain repository 不暴露事务或补偿能力，application 不依赖 Spring DAO 和 transaction support。
4. Refresh token session 完整归 Auth；用户资料和点赞目标解析由独立组合域承担，核心同步依赖图无环。
5. foreign event 在 listener 边界完成反腐转换；application/domain 不保存 `Object payload`，也不依赖 foreign `contracts.event`。
6. MarketOrder、WalletAccount、Comment 三个高风险写模型先形成可保护的一致性边界，不进行全仓一次性重写。
7. 业务包清单、层次依赖、事务所有权和同步依赖图由 ArchUnit 自动守卫。
8. Community、OSS 和 IM 的 schema 通过不可变版本化 migration 演进，不再依赖重建数据卷。

### 强制设计决策

以下决策在实施过程中不再由各子任务自行改变；若代码事实证明决策不可行，应先暂停对应任务并更新本计划：

- Social 的正确性写模型只支持 MySQL SSOT。`social.storage=redis` 写仓储模式退出正确性支持矩阵；Redis 以后只能作为 cache/projection port。
- Refresh token session 由 Auth 完整拥有。Session 保存签发时的 `securityVersion`，刷新时与 User 当前版本比较，不引入 `user -> auth` 同步撤销依赖。
- 新建 `profile` 查询组合域承接用户资料页；新建 `interaction` 组合域承接点赞写入前的目标解析。组合域可以依赖 owner API，但 owner domain 不反向依赖组合域。
- 混合 topic 的 wire envelope 使用 `JsonNode payload`，而不是无约束 `Object` 或无法解决类型擦除的 `ContractEvent<T>`。listener 解码后立即转换为消费域 command。
- 数据库 migration 使用独立 runner 和专用 DDL 账户；application replica 不拥有 DDL 权限。
- 领域模型按 Market、Wallet、Comment 三个切片渐进迁移。未迁移模型不通过大规模机械改名或一次性删除 setter 处理。

## TDD 执行协议

每个任务严格执行以下循环：

1. **Characterize**：先固定当前外部契约、事务结果、事件 ID、JSON 或 SQL 并发语义，避免改造时无意改变行为。
2. **Red**：新增最小失败测试，并单独运行。必须确认失败原因是缺少目标行为，而不是 fixture、环境或旧 SNAPSHOT。
3. **Green**：只实现让当前测试通过的最小生产代码，不同时做无关重构。
4. **Refactor**：在测试保持通过的前提下删除旧入口、重复模型、兼容分支和错误注释。
5. **Focused verify**：运行该任务列出的单元、持久化、集成和 ArchUnit 测试。
6. **Boundary verify**：涉及包边界、事务、事件或 schema 时，运行全部 `*ArchTest` 和相关 migration test。
7. **Regression verify**：一个 wave 合并后运行 `mvn test -pl :community-app -am`；最终运行后端 reactor 全量测试。

Red 阶段不得为了让测试先通过而加入长期白名单、`@Disabled`、宽松 mock 或双写兜底。Green 阶段不得删除失败测试。每个任务的完成定义包括生产实现、失败路径测试、架构守卫、迁移/回滚说明和文档同步。

## Subagent 协作规则

主 agent 负责依赖调度、共享文件、最终合并和全量验证；最多同时运行三个 implementation subagent。每个 subagent 一次只领取一个有明确文件所有权的任务。

分发要求：

- 下发消息必须包含任务 ID、允许修改的目录/文件、禁止修改的共享文件、Red/Green 命令和完成定义。
- 两个 subagent 不得同时修改同一个 Java、SQL、POM 或 handbook 文件。
- `ArchitectureRulesSupport.java`、parent `pom.xml`、compose 文件和本计划由主 agent 统一整合；subagent 只提交建议或修改其独占切片。
- subagent 返回时必须列出修改文件、首次 Red 的实际失败、最终测试结果、剩余风险和未执行命令。
- 一个 wave 内可并行开发，但必须在 wave barrier 统一合并并跑回归；失败时只回退对应任务，不跨 wave 堆叠修复。
- 共享工作区内禁止并发构建同一 Maven module：不同目录可并行编辑，独立 module 可并行测试；`community-app` 等共享 `target` 的测试由主 agent 在 barrier 串行执行，避免 clean/compile 与半写文件制造假失败。
- 不允许 subagent 擅自扩大到下一任务、创建长期兼容层或更改本节的强制设计决策。

### TDD 证据账本

每个任务必须在合并前补齐下表中的六类证据。仅新增测试文件、仅看到最终 Green，或者只运行包含旧 SNAPSHOT 的单 module 命令，都不能证明执行了 TDD。

| 字段 | 必须记录的内容 |
| --- | --- |
| Characterize | 修改前外部行为、wire/SQL 样本、事务结果和对应测试名 |
| First Red | 精确命令、失败测试、关键失败消息；说明为什么是目标行为缺失而非 fixture 错误 |
| Minimal Green | 为让该 Red 通过而修改的最小生产文件；不得混入下一任务 |
| Refactor | Green 后删除的旧入口、兼容分支、重复模型和白名单 |
| Focused verify | 精确命令、tests/failures/errors/skipped；Testcontainers 测试必须记录实际启动的数据库版本 |
| Barrier verify | `*ArchTest`、相关 module 回归、schema/compose contract 和未执行命令 |

主 agent 在每个 wave barrier 更新以下状态值：`PENDING -> RED_CONFIRMED -> GREEN_FOCUSED -> VERIFIED -> DONE`。存在跳过的关键集成测试、没有首次 Red 记录或仍保留旧正确性路径时，最多只能标为 `GREEN_FOCUSED`。

### 当前 subagent 分发

| Task | Owner | 独占修改面 | 当前状态 | Barrier 前剩余工作 |
| --- | --- | --- | --- | --- |
| `MIG-01` | `/root/reliability_plan` | `backend/community-db-migrations/**` | `DONE` | 11 tests、MySQL 8.0 catalog/upgrade、reactor 与 compose contract 均通过 |
| `SOC-01` | `/root/boundaries_plan` | Social 生产/测试切片 | `DONE` | DB-only 写模型和真实事务回滚已通过 Wave 1 barrier |
| `EVT-01` | `/root/contracts_model_plan` | Notice command/listener/application/domain 测试切片 | `DONE` | Notice ACL、wire/relation key、撤销和 malformed/unknown 语义均通过 |
| `ARCH-01` / shared integration | 主 agent | `ArchitectureRulesSupport.java`、parent POM、compose、本计划 | `DONE` | 自动 inventory 与 132 ArchTests 已通过；`profile`、`interaction` 均已登记，分类集合 exact reconciliation 通过 |
| `LEAK-01` | 主 agent | common helper、调用方 import、`DddLayeringArchTest` | `DONE` | 22 条依赖违规 Red 后迁移到 common，focused 与全量回归通过 |
| `REL-DEL-01/02` | `/root/boundaries_plan` | Content deletion outbox、Social listener/command 测试切片 | `DONE` | 旧同步 callback 按计划保留到 `REL-DEL-04`，不作为当前任务未完成项 |
| `REL-MEDIA-01` / `MIG-02A` | `/root/reliability_plan` | OSS client/service/reference 与 OSS migration module | `DONE` | deterministic reference 与独立 `oss_schema_history` 已通过 Wave 2 barrier |
| `DEP-01` / `DEP-03` | `/root/contracts_model_plan` | Profile 查询组合域、Wallet reward ownership | `DONE` | Profile 已登记 inventory；Reward 旧 User 路径已删除 |
| `REL-DEL-03/04/05` | `/root/boundaries_plan` | Social deletion fence/reconciliation、Content 旧同步清理退役 | `DONE` | fence、分页清理、对账 Job/指标与旧同步 callback 退役均通过 Wave 3 barrier |
| `REL-MEDIA-02/03` | `/root/reliability_plan` | Content media desired state、repository/outbox/processor | `DONE` | desired-state CAS、事务性 outbox、可重放 handler 与远程无事务边界均通过 Wave 3 barrier |
| `AUTH-01` | `/root/contracts_model_plan` | Auth refresh session model/repository/MyBatis/Redis 与 User 旧面退休 | `DONE` | ownership、security version、失配撤销和公开入口收敛均通过 Wave 3 barrier；更强的 revoke 原子性另列后续任务 |
| `Wave 3 shared schema` | 主 agent | Community V002-V005、H2 current schema、migration 测试 | `DONE` | 完整 12/12、0 skipped，真实 MySQL 8.0；四个 deployment contract 与 `git diff --check` 通过 |
| `DEP-02` / `LEAK-02A` | Wave 4 subagent、主 agent 整合 | Interaction、Social like 写入口与 User create outcome | `DONE` | Interaction 写组合域、User 语义化创建结果及同步 exact baseline 已通过 Wave 4 barrier |
| `REL-MEDIA-04/05` | Wave 4 subagent、主 agent 整合 | Content media 调度/对账、OSS canonical reference query | `DONE` | create/update/delete durable 调度、历史对账、指标及 OSS 查询均通过 Wave 4 barrier |
| `LEAK-02B` | `/root/boundaries_plan`、`/root/contracts_model_plan` | Wallet 与 Market create outcome 独占切片 | `DONE` | 首次 37 项中 24 项失败；Minimal Green 与删除旧入口后均为 37/37 |
| `LEAK-02C` / `ERR-01` | 主 agent、Wave 4 subagent | Drive/Growth create outcome；common error/web/webflux | `DONE` | DAO 异常只在 adapter 翻译；ErrorKind 到 Servlet/WebFlux 状态映射保持 wire 兼容 |
| `TX-01` / `ARCH-02` | `/root/contracts_model_plan`、主 agent整合 | transaction port/adapter、全域技术泄漏守卫 | `DONE` | Spring transaction implementation 已退出 application/domain；全域 infrastructure transaction 守卫通过 |
| `EVT-02` | Wave 5 subagent、主 agent整合 | Content/Social/User event envelope、codec 与 consumer ACL | `DONE` | 三类 envelope 均为 `JsonNode`；typed codec、wire golden 和 malformed/unknown 语义通过 |
| `DM-MARKET-01` | Wave 5 Market subagent | Market aggregate/transition/repository/MyBatis 独占切片 | `DONE` | 状态迁移统一由 aggregate 产生并经单一 CAS apply；旧 SQL-shaped 写入口已删除 |
| `DM-WALLET-01` | Wave 5 Wallet subagent | Wallet aggregate/change/repository/MyBatis 独占切片 | `DONE` | balance/status/version 不变量统一由 aggregate change 表达；repository outcome 稳定分类 |
| `ARCH-03` | 主 agent | 同步 edge exact set、SCC 守卫和 Content/Social 反向边退休 | `DONE` | exact set 从 61 收紧到 59，Tarjan/DFS 无 SCC cycle |
| `DM-COMMENT-01` | `/root/boundaries_plan` | Comment aggregate/transition、repository/MyBatis 与 application 测试切片 | `DONE` | Comment 直接契约 35/35；与 Content upload 联合独立复验 60/60；应用 barrier 通过 |
| `REL-MEDIA-06` | `/root/reliability_plan` | Content/OSS upload 状态机、事务边界、恢复 Job 与故障注入 | `DONE` | OSS focused 21/21、MySQL 并发 1/1、service 80/80、client 13/13；Nacos 6/6 |
| `MIG-02B` | `/root/contracts_model_plan` | `community-im-db-migrations/**`、IM Core schema parity/repository contract | `DONE` | migration 13/13、IM Core 66/66，真实 MySQL 8.0、0 skipped；IM deployment contract 通过 |
| `Wave 6 independent verify` | `/root/content_comment_verify`、`/root/deployment_barrier` | 只读 focused 与部署契约复验 | `DONE` | Content/Comment 10 suites、60/60；五个 deployment contracts 5/5，未并发触碰 Maven target |

### 已完成 TDD 证据（Wave 0/1/2 与 LEAK-01）

| Task | Characterize / First Red | Minimal Green / Refactor | Focused / Barrier verify |
| --- | --- | --- | --- |
| `BASE-00` | 固定 58 条 foreign `api.*` 类型依赖，并保存 Content/Social/User 三类生产事件 JSON golden | 只增加 characterization 与 golden fixture，不改生产行为 | 4 个基线测试通过；后续命令统一使用 `-am` |
| `ARCH-01` | 真实 class scan 与手工 root 分类不具备 exact reconciliation | 当前模块 `target/classes` 自动发现 tactical roots；显式区分 core、adapter、platform、technical | `ArchitectureModuleInventoryTest` 3/3；Wave 1 ArchTests 120/120 |
| `SOC-01` | 新守卫命中 repository compensation 与 transaction support；发布失败场景暴露原子性要求 | 删除 Redis 写仓储、技术能力开关、手工回滚和 persistence 事务；MyBatis 为唯一写实现 | focused 51/51；`SocialWriteTransactionIntegrationTest` 3/3；全量回归通过 |
| `EVT-01` | Notice command/domain 持有 foreign contract 和 `Object payload` | listener 转换为 Notice sealed command，domain 保存 `NoticeProjectionContent`，删除旧 command | focused 31/31；Notice boundary 包含在 120 个 ArchTests 中 |
| `MIG-01` | 空库、schema drift、错误 baseline、任意 location、dev seed 和 legacy upgrade 测试先失败 | 增加 Flyway runner、59 表 manifest exact verifier、不可变 V001、受 profile 保护的 seed、DDL/DML 分权和 compose gate | `mvn test -pl :community-db-migrations -am`：11/11，0 skipped，6 个 MySQL 8.0 Testcontainers；migration/topology/OSS contract 全部退出 0 |
| `LEAK-01` | `mvn test -pl :community-app -am` 首次失败：`DddLayeringArchTest` 报告 22 条 application -> root `infra` 违规 | idempotency key/resolver/fingerprint 移入 common-idempotency；pagination 移入 common-core；删除四个 root helper 并更新全部调用方 | helper 4/4、focused 152/152、`community-app` 1546/1546、0 skipped；`git diff --check` 通过 |
| `REL-DEL-01/02` | 新增测试后 `testCompile` 缺少 `CleanupDeletedContentLikesCommand`、`SocialContentDeletionKafkaListener` 和 application command 入口；同一轮还包含并行 Profile 缺失符号，账本不把它们误记为本任务 Red | 增加 Content deletion outbox 集成证明、Social listener 和 Social-owned command；`LikeApplicationService` 增加事务 command 入口并把 public self-call 收敛到 private core | Wave 2 focused 集合 49/49；Content commit/outbox、listener 映射与 recognized malformed 失败语义通过 |
| `REL-MEDIA-01` | OSS application/client/controller/persistence 测试首先因 caller `referenceId` accessor/constructor 不存在而失败 | caller-supplied deterministic ID 贯穿 client/controller/application/domain/MyBatis；replay 比较完整语义指纹，冲突为 409；plain insert 竞争后 current-read winner；重复 release 保留首次时间 | OSS client 8/8、service 54/54，0 skipped |
| `MIG-02A` | deploy contract 首先报告 `community-oss-db-migrations is not part of the backend reactor`；module 测试首先缺 runner/application/catalog verifier | 新增独立 module、固定 `classpath:db/migration/community-oss`、不可变 V001/canonical manifest/shaded runner；history 固定为 `oss_schema_history`；compose 改为 DDL runner + DML runtime | migration 9/9，真实 `mysql:8.0`、0 skipped；shaded JAR 与四个 deploy contract 通过 |
| `DEP-01` | Red 编译失败缺少 Profile application/controller/result，且 `UserReadApplicationService` 构造仍要求无效 Wallet collaborator | 新建 `profile` controller/application/result/dto；User 只发布基础 profile API/model，删除 Wallet 字段和调用；Profile 纳入 architecture inventory | Profile/User focused 测试包含在 49/49；Wave 2 全量回归 1547/1547 |
| `DEP-03` | 首轮测试自身遗漏 `UUID` import，不计 First Red；修复 fixture 后再次运行，仅剩 18 个 Wallet projection/listener/command 缺失符号 | reward listener/application/command 迁入 Wallet，删除 User reward listener/application/API；command 独立放入 `wallet.application.command` | Wallet ownership 修复后 related focused 23/23；Reward ArchUnit 与全量回归通过 |

Wave 2 barrier 还捕获了三类集成缺陷。第一次 121 项 ArchUnit 有 4 项失败，分别来自同步 API exact baseline 变化和 Wallet listener 使用 ApplicationService 嵌套 command；修复后完整重跑又由 Profile exact inventory 的两处旧期望产生 2 项失败，补齐文档化集合后为 121/121。第一次 `community-app` 全量运行有 20 个 Context error：`UserReadQueryApiAdapter` 同时发布两个 API，`@MockBean UserLookupQueryApi` 会连带移除 `UserProfileQueryApi`；拆成两个单契约 adapter 后 focused 20/20、全量 1547/1547。以上均为 barrier 发现并修复的真实缺陷，不归写成预先设计的 First Red。

### Wave 3 已完成 TDD 证据

| Task | Characterize / First Red | Minimal Green / Refactor | Focused / Barrier verify |
| --- | --- | --- | --- |
| `Wave 3 shared schema` | 从仅执行 V001 的真实 MySQL 写入 ACTIVE/PENDING refresh session 与既有 media row；首次运行 expected 3 migrations、actual 0。补充失败路径后又分别复现 MySQL 单表 `UPDATE` 左到右赋值导致 pending 未清（expected 2、actual 1），以及 70 字符 deterministic media event ID 写入 `varchar(64)` 报 `Data too long` | 只追加 V002 Social fence、V003 media desired state、V004 refresh security version 与 V005 outbox ID 扩列；V001/manifest 不改。V004 先清 pending/revoked time 再改 state；历史无可信版本 session 全部 revoke family | V001 -> V005 focused：1/1、0 skipped、真实 `mysql:8.0`；空库 current schema focused：1/1；最终 module 12/12、0 skipped，真实 `mysql:8.0` |
| `REL-DEL-03/04/05` | 联合 focused 命令停在 `community-app:testCompile`：缺 `LikeTargetState`、target repository/MyBatis、reconciliation command/result/service/metrics/job、scan API 和新 application constructor；这些均为测试声明的目标生产面，不是 fixture 错误 | 增加 Social target tombstone、行锁/CAS、分页清理、对账 ApplicationService/Job/metrics；Content 三个 ApplicationService 删除旧同步 callback，并删除 `SocialLikeCleanupActionApi` 与 adapter | Wave 3 focused 合集 268/268；同步 exact baseline 首次收紧时精确报告 3 条 missing 旧边和 2 条 unexpected 对账边，更新后 1/1；最终 ArchUnit 124/124、应用 1580/1580，均 0 skipped |
| `REL-MEDIA-02/03` | 同一 First Red 明确缺 reference state/status/operation/command/publisher/ApplicationService/outbox handler、repository 5 个 CAS API、asset desired-state 字段与 caller-supplied reference ID bind | 增加 desired-state/version 状态机、repository CAS、事务性 outbox publisher/handler 和短 claim/finalize 事务；OSS bind/release 保持在数据库事务之外；删除把意图与远程完成混为一体的 repository API | Wave 3 focused 合集 268/268；持久化 focused 9/9；最终 ArchUnit 124/124、应用 1580/1580，覆盖 OSS 响应丢失、DB finalize 失败与 stale replay |
| `AUTH-01` | 同一 First Red 明确报 `issue/store/finishRotation`、`StoredRefreshToken` 的 `securityVersionAtIssue` 参数/accessor 不存在；所有权 ArchUnit 同时冻结 User 旧类型退役目标。Green 审计新增公开面反射测试，首次 13 项中 1 项失败并精确列出 `rotate/consume/issueInFamily` 三个绕过当前 security version 的入口 | session model/repository/MyBatis/Redis 完整迁入 Auth；签发保存版本，刷新读取 User 当前版本，失配时 revoke family 且不 replacement/finish/access-token；删除 User 旧面及三个未使用公开绕过入口；ownership guard 扩到整个 `user.infrastructure..` | Auth 公开面/事务 focused 15/15；Wave 3 focused 合集 268/268；最终 ArchUnit 124/124、应用 1580/1580，均 0 skipped |

Wave 3 barrier 额外捕获并修复了三类集成缺陷：第一次 focused 回归发现 `RefreshTokenApplicationService.revokeFamilyByToken()` 通过 public self-call 绕过 Spring 事务代理，改为共享 private core；第一次全量应用回归为 1580 项中 3 个 error，原因是 H2 无法推断 `coalesce(?, ?, ?)` 参数类型，改为 Java 默认值与显式 JDBC 类型后持久化 focused 9/9；Auth 公开面审计则用真实 First Red 删除了三个可绕过 security-version 查询的未使用 public 方法。同步 foreign API exact set 从初始 58 条删除 3 条 Content -> Social cleanup 边，同时因 Social 对账增加 2 条 Content query/model 边，当前精确基线为 57 条，而不是 55 条。

### Wave 4 已完成 TDD 证据

| Task | Characterize / First Red | Minimal Green / Refactor | Focused / Barrier verify |
| --- | --- | --- | --- |
| `DEP-02` | 固定现有点赞 URL、USER/POST/COMMENT 解析和 Social 写语义；First Red 缺 Interaction controller/application、Social action contract，并证明解析失败时不能进入 Social | 新建 `interaction` 组合域接管写入口；Social 接受已解析的 owner/post 语义，删除 `ContentEntityResolver`、Social 写 controller/DTO 和 foreign query collaborator；查询入口仍归 Social | Interaction/Social focused 包含在联合 106/106；`LikeInteractionBoundaryArchTest` 2/2；同步 exact set 收敛并更新为 61 条；Wave 4 ArchUnit 132/132 |
| `LEAK-02A` | User duplicate/replay 测试固定唯一键回载、规范聚合校验和未知完整性错误传播；旧实现仍由 application 捕获 Spring DAO 异常 | `MyBatisUserRepository` 在 adapter 内翻译 duplicate，返回 User-owned insert outcome；ApplicationService 只处理语义结果，删除异常 message/constraint 解析 | 与 DEP/ERR 联合 application focused 106/106；User DAO boundary 1/1；common-core 3/3，均 0 skipped |
| `LEAK-02B` | Wallet/Market 37 项 First Red 有 24 项失败，精确指出八个 repository 缺少 `create(ExactAggregateType)` 语义入口 | Wallet 使用 `CreationOutcome<T>`，Market 使用 owner-owned `CreateResult`；八个 MyBatis adapter 负责 duplicate 翻译和唯一键回载；删除 Wallet 六个无调用方 `insert/insertTxn` 创建入口 | Minimal Green 37/37；Refactor 后复验 37/37；`PersistenceExceptionBoundaryArchTest` 与应用全量回归通过 |
| `LEAK-02C` | Drive/Growth repository/application tests 固定 duplicate replay、规范聚合比对、冲突和未知完整性错误；First Red 命中 Spring DAO 泄漏和缺少 owner outcome | 在 Drive/Growth persistence adapter 翻译 duplicate，repository 返回领域拥有的创建结果；ApplicationService 删除 Spring DAO catch/import | focused 12/12；`DriveGrowthDaoExceptionBoundaryArchTest` 1/1；Wave 4 ArchUnit 与应用回归通过 |
| `ERR-01` | golden table 固定既有业务 code/message/HTTP status/body `httpStatus`；反射 Red 要求 `ErrorCode` 不再暴露 HTTP，Servlet/WebFlux 参数化测试覆盖全部 `ErrorKind` | common-core 增加 `ErrorKind`；Servlet/WebFlux 各自在 adapter 映射状态；迁移全部 error enum 和 Like 判断后删除 `getHttpStatus`、status 字段及旧构造 | common-core 3/3、Servlet 8/8、WebFlux 8/8；`ErrorSemanticsBoundaryArchTest` 2/2；对外 golden 兼容 |
| `REL-MEDIA-04/05` | create/update/delete、kept/repeated edit、rollback、各删除入口和五类历史漂移测试先声明 durable desired-state 语义；First Red 缺删除调度、canonical query、对账 service/job/metrics 与 scan API | 发帖和编辑只 request + enqueue；删除 BEFORE_COMMIT bridge 与事件 outbox 同事务调度 release；新增 OSS canonical reference query、分页对账、失败隔离和 pending/drift 指标；删除 direct storage、rollback synchronization 和吞异常补偿 | Content media focused 58/58，修正两个旧 fixture 后独立复验 6/6；OSS client focused 7/7、service focused 13/13；最终 OSS client 8/8、service 54/54 |
| `ARCH-01` completion | Interaction 落地后 exact inventory Red 报新 tactical root 未分类；Wave 4 首轮全 ArchUnit 又由两组 documented-domain exact set 漏 `interaction` 产生 2 个失败 | 将 `interaction` 同时纳入 core/business 分类和两组 exact expected set，不增加例外或白名单 | `DomainBoundaryArchTest` 16/16；全部 ArchUnit 132/132；应用 reactor 1737/1737；`git diff --check` 退出 0 |

Wave 4 首轮架构 barrier 为 132 项中 2 项失败，均来自 `DomainBoundaryArchTest` 的文档化域精确集合仍停留在 Interaction 落地前；补齐两组 `interaction` 期望后 focused 16/16、全量 132/132。最终 `mvn test -pl :community-app -am` 为 1737/1737，OSS client 为 8/8、OSS service 为 54/54，全部 0 failures、0 errors、0 skipped。同步 foreign API exact set 当前为 61 条；原 Social 写用例的 foreign 解析已移出，但 Social 对账仍同步查询 Content，真实 Content/Social SCC 留给 Wave 5 `ARCH-03` 的 Red/Green 消除，不能把 edge 总数变化误写成“已经无环”。

### Wave 5 已完成 TDD 证据

| Task | Characterize / First Red | Minimal Green / Refactor | Focused / Barrier verify |
| --- | --- | --- | --- |
| `TX-01` | `TransactionBoundaryArchTest` First Red 精确命中 `MyBatisUserRepository` 的 role/status/password 三个无 version overload 与 `nextUserSecurityVersion` 共 4 个 persistence transaction；回滚集成测试固定 audit/publisher 异常时 user row、policy/security counter 与 outbox 同回滚 | 删除三个无 version overload、四个 infrastructure transaction 和 runtime counter upsert；AdminUser/UserCredential/UserModeration/Like cleanup 保持 public ApplicationService 事务入口，counter 初始化只归 migration | TransactionBoundary 3 + User transaction 2 + repository 8 + 三组 application 28 = 41/41；最终 ArchUnit 135/135 |
| `ARCH-02` | `SharedArchitectureInvariantArchTest` 首次有效 Red 为 4 项中 1 项失败，精确列出 Drive upload/trash 与 HotFeed projection 的 11 条 Spring transaction implementation 依赖；同一守卫还冻结三类 envelope、foreign event contract 与 migrated aggregate setter 不变量 | Drive 增加 application-owned `DriveTransactionOperations`，HotFeed 增加 `HotFeedProjectionCompletion`，Spring 实现下沉 infrastructure；删除 Content persistence 事务特例，统一由全域规则承担 | Shared 4 + Sync graph 2 + TransactionBoundary 3 = 9/9；Drive/transaction focused 51/51，HotFeed/event focused 105/105 |
| `EVT-02` | BASE-00 三类 golden 固定现有 JSON；First Red 在 testCompile 明确缺三个 owner codec 与 sealed typed event，共享守卫同时报告三个 envelope 的 `Object payload`；codec 参数化测试覆盖已知 type、type/payload 错配、malformed 和 unknown | Content/Social/User envelope 改为 `JsonNode`，各 owner 增加集中 codec 与 sealed typed event；producer/dispatcher/consumer 逐条迁移并删除零散 payload normalize/`instanceof` 分派 | Content codec 30 + Social 22 + User 8 + adoption 13 + wire golden 5 = 78/78；0 skipped |
| `DM-MARKET-01` | repository/persistence Red 缺统一 `apply/ApplyStatus` 与完整 transition DO/SQL；边界 Red 报 26 个 public setter 和全部 `markXxx/changeStatus`；状态机参数化测试固定合法/非法边及 txn/auto-confirm 原子字段 | `MarketOrder.place/reconstitute` 产生带 expected status 与字段操作的不可变 transition；repository 收敛 `create + apply(APPLIED/STALE)`，MyBatis 单 CAS；迁移 order/saga/auto-confirm/dispute 后删除 setter 与 SQL-shaped 写入口，foreground stale 映射已发布 18002 conflict | 核心状态机/持久化/边界/application 212/212；扩展 Market 回归 249/249；完整 reactor 通过 |
| `DM-WALLET-01` | First Red 在 testCompile 明确缺 `WalletAccountChange`、工厂/行为与 repository semantic apply；setter guard 命中 9 个 public setter；persistence/application 固定 missing/version conflict/insufficient 的 fail-closed 分类 | aggregate 封装非负余额、冻结出账、overflow、合法状态边和 version+1；repository `apply` 在 adapter 内执行 CAS/分类，application 删除 `setStatus` 与 reload-and-classify，保留 Wave 4 create replay | 核心 domain/repository/application/replay 20/20；扩展 Wallet 写回归 55/55；既有 ledger/回滚测试全部通过 |
| `ARCH-03` | characterization 先锁定 61 条 `core application -> foreign api.query/action/model` 精确依赖，并证明 Content/Social 位于同一 SCC；新增边输出 origin class 与具体 target | Social 删除点赞对账改为以自身 durable tombstone/like state 为 SSOT，删除对 Content 的同步反查；exact set 随真实删除收紧为 59 条，SCC 由 Tarjan/DFS 直接失败，不设置 cycle whitelist | `SynchronousCollaborationBaselineArchTest` 2/2；Wave 5 ArchUnit 135/135；当前图 59 条且无环 |

Wave 5 首轮完整 ArchUnit 为 136 项中 1 项失败：`ErrorSemanticsBoundaryArchTest` 仍要求已经退休 Content 同步反查的 Social 对账路径调用 `ErrorCode.getKind()`；删除该过期正向要求、保留“不得读取 HTTP status”后为 135/135。首轮 reactor 为 2053 项中 2 个 failure、2 个 error：错误码 golden 漏登记已发布的 `ORDER_TRANSITION_CONFLICT`，两个 HotFeed fixture 缺 Social producer 必填 `actorUserId`，普通 duplicate/stale deletion event 还会重复扫描点赞。三组独立修复的 focused 回归为 109/109；最终 `mvn test -pl :community-app -am` 为 2055/2055，全部 0 failures、0 errors、0 skipped。后一个问题不是测试过期：生产逻辑改为只有显式 reconciliation command 才能在 fence 不推进时扫描，普通旧事件严格 no-op。

### Wave 6 已完成 TDD 证据

| Task | Characterize / First Red | Minimal Green / Refactor | Focused / Barrier verify |
| --- | --- | --- | --- |
| `MIG-02B` | deployment contract 首先报告 `community-im-db-migrations is not part of the backend reactor`；module 测试随后明确缺 runner、catalog verifier 与固定 migration location。H2/MySQL exact parity Red 还暴露索引、列类型、`on update` 和 counter seed 漂移 | 新增独立 migration module、固定 `classpath:db/migration/im-core`、不可变 V001、12 表 exact manifest、`im_core_schema_history`、shaded runner 与 DDL/DML 账户；移除 `070_schema_im_core.sql` init/helper 回放，IM Core 启动等待 runner；H2 schema 仅保留在 test scope | migration 13/13、IM Core 66/66，真实 `mysql:8.0`、0 failures/errors/skipped；IM deployment contract exit 0 |
| `REL-MEDIA-06` | Content tests 先声明 upload 状态机、versioned CAS、canonical query、事务外 OSS 与 stale/poison-row recovery；OSS tests 先声明 request replay、响应丢失、finalize rollback、stale claimant 和并发 fingerprint。补强的 wildcard HEAD 精确匹配 Red 为 18 项中 1 项失败，消息 `Expecting code to raise a throwable` | Content 落地 `PREPARED -> COMPLETING -> OBJECT_COMPLETED -> COMPLETED/FAILED`、短事务 claim/finalize、事务外 OSS、UNKNOWN reset、候选轮转与失败隔离；OSS 增加 READY renewal、`claimVersion` fencing、`.claim-<version>` attempt key、CAS-first finalize、V002/V003 和 recovery job。主上传严格比较本次 submitted content，历史 wildcard recovery 保留兼容；删除 best-effort cleanup | Content/Comment 独立联合复验 10 suites、60/60；OSS focused 21/21、真实 MySQL prepare 并发 1/1、client 13/13、service 80/80、migration 9/9；Nacos binding 6/6，均 0 skipped |
| `DM-COMMENT-01` | aggregate/transition/repository tests 先因缺 `CommentSnapshot.version`、`CommentDataObject`、`CommentEdit`、`CommentDeletion`、`CommentThreadDeletion`、稳定 apply result 和 repository `apply` 而 Red；并发测试固定 root delete 与 reply insert 的线程锁语义 | aggregate 统一 15 分钟编辑边界、作者/管理员权限、post/active 校验和 root/reply 删除规则；V006 增加单调 version；repository 使用 CAS、线程行锁、root-first 固定集合原子 apply，reply create 先锁 root。删除 14 个 persistence setter、SQL-shaped repository/mapper 写入口和 DomainService 重复规则；事件与 counter 只依据实际 apply 结果 | Comment 直接契约 35/35；Content/Comment 独立联合复验 10 suites、60/60；`CommentSideEffectBoundaryArchTest` 与应用全量 barrier 通过，0 failures/errors/skipped |

Wave 6 barrier 还捕获并修复了四类集成问题。真实 MySQL 暴露 `updateConversationInbox` 的 `SET` 从左到右求值：过早写 `last_seq` 会让 unread delta 使用新值，移动该赋值到末尾后 migration 13/13、IM Core 66/66。应用全量测试暴露两个旧 Comment fixture 以 `postId=null` 构造已不合法聚合；修正后，评论引用的帖子行缺失稳定传播 `POST_NOT_FOUND`，reply 使用自身 canonical `postId`，不通过缺失父评论反推。Nacos 配置经历两轮 Red/Green：第一轮分别发现 `content.media.upload-recovery.enabled` 和 `community.oss.upload-recovery.enabled` 缺失；补齐两个 upload recovery 开关后，后续 6 项 binding barrier 又有 1 项因 `reference-reconciliation.enabled=null` 失败。补齐 `enabled: true`、`batch-size: 50`、`delay-ms: 300000` 后最终 6/6。一次并行运行产生 26 个 `NoClassDefFoundError`，根因是两个 Maven 进程同时写 `community-app/target/test-classes`；该结果不计业务 Red，清理该 module 后由主 agent 串行重跑为 2094/2094。

Community schema 以 forward-only V006/V007 分别承载 Comment 单调 version 与 Content upload recovery 字段/索引；`community-db-migrations` 从 V001 到 V007 的空库、升级、checksum 和 exact catalog 测试最终为 12/12，真实 `mysql:8.0`、0 skipped。V001 未被改写，应用回滚仍只允许 forward-fix。

OSS MySQL 并发测试第一次报告 `Could not find a valid Docker environment`，也不计业务 Red。有效依赖树混用了 Testcontainers core 1.19.8/docker-java 3.3.6 与 2.0.5 模块；统一 Testcontainers 2.0.5 后实际加载 docker-java 3.7.1、Unix socket 和 `mysql:8.0`，并将 fixture 收敛为专用 `MySQLContainer`，最终并发测试 1/1、0 skipped。

#### Wave 6 可复现 TDD 命令

以下命令在生产实现前分别确认目标缺失的 Red，Minimal Green/Refactor 后用同一选择器复验。多 module focused 命令显式关闭“未包含指定测试”的依赖 module 误报，不跳过目标 module 中的任何测试。

```bash
cd backend

# MIG-02B Red/Green：runner、catalog、H2 parity、真实 MySQL repository
mvn test -pl :community-im-db-migrations,:im-core -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='ImMigrationTest,ImMigrationLayoutTest,ImMigrationApplicationTest,ImCoreMySqlMigrationRepositoryContractTest'

# REL-MEDIA-06 Red/Green：Content 状态机/边界、OSS fencing/recovery 和 schema
mvn test -pl :community-app -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='PostMediaUploadReliabilityContractTest,PostMediaUploadTransactionBoundaryTest,PostMediaAssetMapperPersistenceTest,OssPostMediaStorageAdapterTest,NacosPolicyBindingTest'
mvn test -pl :community-oss -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='ObjectUploadReliabilityContractTest,ObjectUploadTransactionOperationsIntegrationTest,ObjectUploadPrepareMySqlConcurrencyContractTest'
mvn test -pl :community-oss-db-migrations,:community-oss -am

# wildcard HEAD 精确匹配的补强 First Red
mvn test -pl :community-oss -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='ObjectUploadReliabilityContractTest#directCompletionMustRejectHeadMetadataThatDiffersFromSubmittedContentForWildcardClaim'

# DM-COMMENT-01 Red/Green：aggregate、row boundary、CAS/锁和事件基数
mvn test -pl :community-app -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='CommentAggregateContractTest,CommentThreadTransitionContractTest,MyBatisCommentRepositoryTest,CommentThreadLockingPersistenceIntegrationTest,CommentPersistenceBoundaryContractTest,CommentMapperPersistenceTest,CommentDeletionCardinalityContractTest,CommentApplicationServiceTest,CommentSideEffectBoundaryArchTest'

mvn test -pl :community-db-migrations,:community-app -am
cd ..
deploy/tests/im_migration_contract.sh
```

`AUTH-01` 的所有权迁移已经完成，但以下可靠性增强不包含在其 DONE 语义内，必须在后续 Red 中单独证明：MyBatis family revocation marker 的无引用清理；Redis `revokeFamily()` 的原子性、marker 写失败传播及 revoke-vs-finish 并发；密码、角色、有效封禁推进 security version 时的真实数据库回滚。已发布 V001 中过时的 owner 注释不为文案重写 checksum，后续只修正可变文档或新 migration 注释。

后续 wave 只在前一 barrier 通过后分发。完成的 subagent 优先复用原 agent 上下文；新任务必须重新声明文件所有权，防止“并行”变成共享文件上的最后写入者覆盖。

### 任务依赖图

```text
BASE-00
  |
  +-- MIG-01 --------------------+--> AUTH-01
  |                              +--> REL-DEL-03
  |                              +--> REL-MEDIA-02/03
  +-- MIG-02A --------------------------> REL-MEDIA-06
  +-- MIG-02B
  |
  +-- SOC-01 --> REL-DEL-02/03/04/05 --> DEP-02
  |
  +-- REL-MEDIA-01 --> REL-MEDIA-03 --> REL-MEDIA-04 --> REL-MEDIA-05/06
  |
  +-- DEP-01 ----+
  +-- DEP-03 ----+-------------------------------> ARCH-03
  +-- AUTH-01 ---+
  +-- DEP-02 ----+
  |
  +-- EVT-01 --> EVT-02 --> ARCH-02
  +-- ERR-01 -------------> ARCH-02
  +-- LEAK-01/02 ---------> ARCH-02
  +-- DM-MARKET-01 --+
  +-- DM-WALLET-01 --+--> DM-COMMENT-01

ARCH-01 可提前建立 inventory；ARCH-03 必须在同步反向边全部删除后才能 Green。
```

### 并行波次

| Wave | 主 agent | Subagent A | Subagent B | Subagent C | Barrier |
| --- | --- | --- | --- | --- | --- |
| 0 | `BASE-00`、共享测试基线 | 只读复核 | 只读复核 | 只读复核 | 基线测试稳定 |
| 1 | `ARCH-01` 协调 | `MIG-01` | `SOC-01` | `EVT-01` | migration、Social DB-only、Notice ACL 分别通过 |
| 2 | `LEAK-01` | `REL-DEL-01/02` | `REL-MEDIA-01 + MIG-02A` | `DEP-01 + DEP-03` | 不删除旧可靠性路径；OSS migration runner 已就绪 |
| 3 | 共享 schema/配置整合 | `REL-DEL-03/04/05` | `REL-MEDIA-02/03` | `AUTH-01` | P0 durable path 可故障重放 |
| 4 | `LEAK-02C` | `DEP-02 + LEAK-02A` | `REL-MEDIA-04/05` | `LEAK-02B + ERR-01` | 同步图和技术泄漏收敛 |
| 5 | `ARCH-02/03` | `EVT-02` | `DM-MARKET-01` | `DM-WALLET-01` | 全部架构守卫 Green |
| 6 | 全量验证与文档 | `DM-COMMENT-01` | `REL-MEDIA-06` | `MIG-02B` | reactor、故障注入和升级测试通过 |

`REL-DEL-04` 与 `REL-MEDIA-04` 都会修改 `PostPublishingApplicationService`；必须由同一 owner 串行完成，或由主 agent 在 Wave 3/4 barrier 统一修改。

## 任务明细

### BASE-00：建立可信基线

**问题**：单模块测试可能读取本机旧 SNAPSHOT；现有 ArchUnit 全绿但没有保存同步依赖图、事件 wire 样本和跨资源失败窗口。没有可信基线就无法判断 Red 是否由新测试触发。

**Red/Green/Refactor**

- [x] Red：新增当前同步 foreign API edge 的 characterization test，输出 origin class、target domain 和 API 类型；测试暂时断言当前已知 edge 集合。
- [x] Red：为 Content/Social/User contract event 保存当前生产 JSON golden samples。
- [x] Green：不改生产行为，只修复 fixture，使基线在 `-am` 构建下稳定。
- [x] Refactor：统一测试命令禁止直接使用可能命中旧本地 SNAPSHOT 的 `mvn -pl :community-app`。

**验证**

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
mvn test -pl :community-app -am
```

完成标准：记录测试数量、当前同步 edge 和 golden sample checksum；工作区除计划文件外无未知改动。

### MIG-01：建立 Community 版本化 migration

**问题**：`deploy/mysql/community` 只描述最终态，并且只在建卷时执行；没有版本历史、checksum、部署锁或升级测试。工作区虽已有 runner 草稿，但固定口令 baseline、probe-only upgrade、任意 location override、可静默跳过的 Testcontainers 和未接入 reactor/compose 等缺口仍会产生“测试绿、生产漏 DDL”的假象。后续点赞 fence、媒体状态和 refresh security version 都依赖可靠 DDL，因此本任务是所有新增表/列任务的硬前置。

**目标设计**

- 新建 `backend/community-db-migrations` Maven module，使用 Flyway 和专用 migration runner。
- `V001__baseline.sql` 固化当前 Community schema，并随包发布 canonical catalog manifest；开发 seed 使用固定的 development-only action，不进入生产 history。
- production `migrate/validate` 的 location 和 history table 由代码固定，环境变量不能改到 dev/test resource；不暴露 `repair` 或 `clean`。
- single/cluster compose 只初始化 database 与专用账号，由一次性 migration service 使用 DDL 账号执行；application replica 等待 migration 成功并且只持有 DML 权限。
- 已有环境只有在真实 `information_schema` 与 V001 manifest exact equality 后才能显式 `baselineVersion=1`；额外 owner 表、缺表、错列、错索引、错约束或错默认值都拒绝 baseline，禁止 `baselineOnMigrate`。

**TDD**

- [x] Red：空 MySQL Testcontainer 执行 V001 后，把表、列、类型/nullability/default、主键/唯一键/普通索引和 check constraint 的 canonical catalog 与 manifest exact compare；不能只抽查表名。
- [x] Red：reference data test 断言 task template、category 和 version counter 实际写入；禁止用 `INSERT IGNORE` 吞掉缺失主键、约束或类型错误。
- [x] Red：重复 migrate 为 no-op；修改已应用脚本后 `validate` 必须失败。
- [x] Red：分别构造“任意非空库、缺表、额外 owner 表、错列、错索引、错默认值”并调用 baseline，全部必须在写 history 前失败；完全等价 legacy schema 才能 baseline。
- [x] Red：从 `deploy/mysql/community` 当前支持的 010-090 Community SQL 建完整 legacy schema，写入 user、wallet、content、social、outbox 等代表数据，执行 `baseline at 1 -> test V002` 后验证逐行保留。
- [x] Red：CLI test 证明 production action 忽略/拒绝 location override，dev seed 缺少显式 development environment 时失败；shaded JAR resource test 固定生产与开发入口。
- [x] Red：移除 `disabledWithoutDocker`；要求 SQL 验证的 CI job 无 Docker 时必须失败，并断言 testsuite 的 skipped 为 0。
- [x] Red：reactor test 证明 `:community-db-migrations` 可被 parent 选中；compose contract test 断言不再挂载 Community 最终态 schema、runtime 等待 migration service、DDL/DML 凭据不同。
- [x] Green：完成 runner、V001、manifest verifier、固定 action/location、history table、parent module 和 single/cluster compose migration service。
- [x] Green：修复 reference seed 主键；`DeployCommunitySchema` 及需要真实 schema 的 persistence test 改读 V001/runner，不再拼接 retired init SQL。
- [x] Refactor：移除 Community schema mounts和重复 schema fixture；保留 database/user bootstrap，dev seed 只用于显式开发流程。

**迁移与回滚**

- migration service 获取数据库 advisory lock；同一环境只能有一个 runner 执行，application 在 runner 成功前不得启动。
- 先备份并以只读账号导出 catalog；runner 自行做 exact comparison，只有完全等同 V001 manifest 的环境才能写 baseline history，人工确认口令不能替代比较。
- 发布后的 migration 永不修改，只追加新版本。
- `validate` 失败立即阻断发布；禁止用 `repair` 改写 checksum 来绕过失败。
- 回滚应用版本时不回滚 DDL；先证明旧应用可容忍 additive schema，再回滚二进制。DDL 错误只使用 forward-fix migration，并保留备份恢复演练记录。

**验证**

```bash
cd backend
mvn test -pl :community-db-migrations -am
mvn test -pl :community-app -am -Dtest='*Schema*Test,*ArchTest'
../deploy/tests/development_clean_break_contract.sh
docker compose -f ../deploy/compose.yml config
```

完成标准：空库、完全等价 legacy 库和至少六类 schema drift 都有真实 MySQL 结果；upgrade fixture 含真实业务行；所有 SQL integration tests 为 0 skipped；parent reactor、single/cluster compose、shaded JAR 和最小权限账号均通过验证。仅 module 内 7 个测试通过不代表 MIG-01 完成。

### MIG-02：为 OSS 与 IM 建立独立 migration history

**问题**：`community_oss` 的 DDL 当前与 Community DDL 一起挂载到 MySQL 首次建卷流程，`im_core` 的生产 DDL 则混在 `deploy/mysql/community/070_schema_im_core.sql`，测试还单独维护 H2 `schema.sql`。三个所有权不同的数据库没有独立 history、checksum、部署锁和升级入口：OSS 或 IM 单独发布时不能证明目标 schema 已升级；修改旧 SQL 不会被发现；重建测试库与升级生产库走的是不同路径。Nacos 与 XXL-JOB 的 schema 是第三方版本资产，不应伪装成本项目业务 migration。

**目标设计**

- `community_oss` 与 `im_core` 各自拥有 Flyway location、history table、最小权限 DDL 账号和一次性 runner；任何 runner 都不能跨库执行脚本。
- `community_oss` 基线只包含 OSS 所有的 object、upload session、reference 等表；`im_core` 基线只包含 IM Core 的 room、message、member/read watermark 等表。
- Community、OSS、IM 三套 history 和发布节奏彼此独立；服务账号只保留 DML 权限，运行时服务不得自动执行 migration。
- Nacos、XXL-JOB 继续使用固定上游版本的 vendored bootstrap schema；升级它们必须跟随对应第三方版本说明，不写入 Community/OSS/IM Flyway history。

#### MIG-02A：Community OSS migration

- [x] Red：空 MySQL Testcontainer 执行 OSS migration 后，现有 OSS MyBatis/JDBC schema contract tests 全部通过。
- [x] Red：重复 migrate 为 no-op；篡改已应用脚本后 `validate` 失败；从受支持的 `010_schema.sql` 结构 baseline 后升级不丢 object、upload session 和 reference 数据。
- [x] Red：compose contract test 证明 MySQL init 不再挂载 `community_oss/010_schema.sql`，OSS runtime service 等待独立 migration service 成功。
- [x] Green：建立 `community-oss-db-migrations` module/runner、`V001__oss_baseline.sql`、独立 history table `oss_schema_history` 与 DDL/DML credential 配置。
- [x] Green：OSS 集成测试和部署 smoke test 统一从 runner 建库；移除首次建卷的 OSS 最终态 schema mount。
- [x] Refactor：REL-MEDIA 后续 OSS 字段只能追加 `V002+`，不得修改 `V001`；开发 fixture 与生产 migration 分离。

#### MIG-02B：IM Core migration

- [x] Red：空 MySQL Testcontainer 执行 IM migration 后，IM Core repository contract tests 全部通过。
- [x] Red：以当前 `070_schema_im_core.sql` 对象清单为受支持基线，验证 `baseline -> migrate`、数据保留、重复执行和 checksum 失败。
- [x] Red：测试证明生产 profile 不再依赖 H2 `schema.sql` 建表；H2 fixture 若保留，必须由测试断言与 Flyway schema 等价。
- [x] Green：建立 `community-im-db-migrations` module/runner、`V001__im_core_baseline.sql`、独立 history table 与 DDL/DML credential 配置。
- [x] Green：从 Community init mounts 移除 `070_schema_im_core.sql`；IM Core runtime 等待 IM migration service 成功。
- [x] Refactor：明确 IM Realtime/Gateway 不拥有关系库 DDL；后续 IM schema 只追加版本化脚本。

**迁移与发布顺序**

1. 对现有 `community_oss`/`im_core` 分别生成对象清单、约束和索引 checksum；只有与对应 V001 等价的库才能标记 baseline。
2. 先部署 migration runner 和 DDL credential，再执行 baseline/validate；成功后才部署依赖新列的应用。
3. 删除 init mount 只影响新卷；已有卷以 Flyway history 为准，不重放 V001。
4. 应用回滚不删除新列或 history；失败使用 forward-fix migration。

**验证**

```bash
cd backend
mvn test -pl :community-oss-db-migrations,:community-oss -am
mvn test -pl :community-im-db-migrations,:im-core -am
../deploy/tests/im_migration_contract.sh
```

依赖：`MIG-02A` 必须在 `REL-MEDIA-06` 引入 OSS 新状态字段前完成；`MIG-02B` 与 Community/Social/Notice 改造无代码依赖，可独立并行。

### SOC-01：Social 写模型收敛为 MySQL SSOT

**问题**：Like/Follow/Block repository 暴露 `requiresExplicitCompensation()`，ApplicationService 直接注册 Spring transaction callback。Redis 写成功、DB outbox 失败后的补偿仍可能失败，只记录 warning，无法保证收敛。

**TDD**

- [x] Red：新增 `SocialRepositoryBoundaryArchTest`，禁止 domain repository 声明 compensation/transaction 能力，禁止 `social.application` 依赖 `org.springframework.transaction.support`。
- [x] Red：真实 MyBatis 集成测试让事件发布失败，断言 Like/Follow/Block 主记录、计数和 outbox 一起回滚。
- [x] Red：startup test 证明非 `db` 的 `social.storage` 配置 fail-fast。
- [x] Green：删除 `RedisLikeRepository`、`RedisFollowRepository`、`RedisBlockRepository` 及专属写仓储测试。
- [x] Green：删除三个 repository 的技术开关和三个 ApplicationService 的手工 rollback callback。
- [x] Green：MyBatis repository 成为唯一写实现，删除 `MyBatisLikeRepository.deleteLikesByEntity()` 上的事务。
- [x] Refactor：以后 Redis 只能通过独立 cache/projection port 引入。

**验证**

```bash
cd backend
mvn test -pl :community-app -am \
  -Dtest='LikeApplicationServiceTest,FollowApplicationServiceTest,BlockApplicationServiceTest,*TransactionTest,StartupValidationTest,*ArchTest'
```

### REL-DEL：删除内容后的点赞最终清理

#### 问题与目标

当前 Content 删除事务通过 `AfterCommitExecutor` 调 Social；commit 后宕机或调用失败会永久丢任务。仅补 Kafka listener 仍不完整：点赞请求可能先完成 Content resolve、后于 cleanup 提交，从而在删除后写入孤儿点赞。

目标链路：

```text
Content delete transaction
  -> PostDeleted / CommentDeleted owner outbox
  -> content.events Kafka retry/DLQ
  -> SocialContentDeletionKafkaListener
  -> LikeApplicationService.cleanupDeletedContentLikes(command)
  -> target tombstone + target row lock
  -> idempotent cleanup + existing LikeRemoved outbox

SocialLikeCleanupReconciliationJob
  -> LikeCleanupReconciliationApplicationService
  -> bounded orphan scan and repair
```

#### REL-DEL-01：锁定删除事件事实

- [x] Red：`CommentApplicationServiceTest` 证明级联删除的每个 comment 都发布独立 deletion event。
- [x] Red：Post author/admin/moderation 删除只有在状态实际变化时才发布事件。
- [x] Red：新增 `ContentDeletionOutboxIntegrationTest`，验证稳定 event ID、commit 同写和 rollback 同回滚。
- [x] Green：只补齐缺失契约，不改 wire 格式。
- [x] Refactor：删除依赖手工触发 `afterCommit()` 的可靠性测试。

#### REL-DEL-02：新增 Social listener

- [x] Red：`SocialContentDeletionKafkaListenerTest` 覆盖 POST/COMMENT 映射、Map/JsonNode payload、unknown type 忽略、recognized malformed event 抛错。
- [x] Green：新增 `CleanupDeletedContentLikesCommand` 和 `SocialContentDeletionKafkaListener`；listener 只调用同域 `LikeApplicationService`。
- [x] Refactor：foreign payload 只能存在于 listener，不进入 Social application/domain。

#### REL-DEL-03：删除 fence 与幂等重放

- [x] Red：`LikeTargetStateTest` 覆盖 ACTIVE 到 DELETED 单向迁移、source version 单调和重复删除。
- [x] Red：`LikeApplicationServiceTest` 覆盖重复事件 no-op、超过 200 条分页、中途事件发布失败后重试、deleted target 禁止新点赞。
- [x] Red：`MyBatisLikeTargetStateRepositoryTest` 覆盖 insert-if-absent、`select for update` 和并发 CAS。
- [x] Green：新增 `LikeTargetState`、`LikeTargetStateRepository` 及 MyBatis 实现。
- [x] Green：新增 migration 表 `social_like_target_state(entity_type, entity_id, status, source_event_id, source_version, deleted_at, updated_at)`。
- [x] Green：点赞和 cleanup 锁同一个 target row；cleanup 写 tombstone 后分页删除并发布 LikeRemoved。
- [x] Refactor：将旧 `cleanupEntityLikes(int, UUID)` 的核心收敛到 command use case。

#### REL-DEL-04：移除易失同步路径

- [x] Red：ArchUnit 禁止 `content.application` 依赖 `SocialLikeCleanupActionApi`。
- [x] Green：从 `PostPublishingApplicationService`、`PostModerationApplicationService`、`CommentApplicationService` 删除 cleanup callback。
- [x] Green：无调用者后删除 `SocialLikeCleanupActionApi`、adapter 和测试。
- [x] Refactor：可重建缓存 eviction 可以继续 after-commit；关键业务清理不得使用它。

#### REL-DEL-05：DLQ 后对账

- [x] Red：`LikeCleanupReconciliationApplicationServiceTest` 覆盖 batch、cursor、Content NOT_FOUND、Content unavailable 和单项失败隔离。
- [x] Red：Job test 断言 inbound job 只调用同域 ApplicationService。
- [x] Green：增加 bounded scan、reconciliation job 和指标 `social_like_cleanup_total`、`social_like_orphan_targets`、`social_like_cleanup_lag_seconds`。
- [x] Refactor：数据量增长后通过 Content batch query 优化，不允许 Job 直接调用 foreign API。

**验收**

```bash
cd backend
mvn test -pl :community-app -am \
  -Dtest='*ContentDeletionOutboxIntegrationTest,SocialContentDeletionKafkaListenerTest,LikeApplicationServiceTest,*LikeCleanup*Test,*ArchTest'
```

故障注入必须覆盖 Content commit 后立即停进程、重复 Kafka 投递、consumer 进入 DLQ 后人工 replay，以及删除与点赞并发。

### REL-MEDIA：Post media 与 OSS 最终一致

#### 问题与目标

当前至少存在五个窗口：bind 成功而 Content DB/补偿失败；OSS 成功但响应丢失导致重复 reference；Content 先标 RELEASED 而远程释放失败；帖子删除完全没有释放 media reference；prepare/complete 同样在本地事务中调用 OSS 并吞补偿异常。

目标链路：

```text
Content transaction
  -> asset desired state BIND_PENDING / RELEASE_PENDING
  -> command.content.post-media-reference outbox
  -> commit

Outbox handler
  -> PostMediaReferenceApplicationService
  -> short DB claim
  -> OSS call outside DB transaction
  -> short DB finalize
```

命令只保存 `assetId`、`operation`、`operationVersion`、`actorUserId`；执行时重新加载 asset。event ID 使用 `content-media-reference:<assetId>:<version>:<operation>`。

#### REL-MEDIA-01：OSS deterministic reference ID

- [x] Red：OSS application/controller/client/persistence tests 覆盖相同 requested ID 的幂等 replay、同 ID 不同语义冲突、响应丢失重试、RELEASED 后迟到 bind、重复 release。
- [x] Green：`OssBindReferenceRequest`、command 和 OSS aggregate 接受 caller-supplied `referenceId`；旧客户端暂时允许为空。
- [x] Green：按 ID 查询已有记录并比较完整 semantic fingerprint，不依赖 blind upsert。
- [x] Refactor：冲突映射稳定 409，release time 不因重放漂移。

#### REL-MEDIA-02：Content desired state

- [x] Red：domain test 固定 `UNBOUND -> BIND_PENDING -> BOUND -> RELEASE_PENDING -> RELEASED` 和 operation version。
- [x] Red：persistence test 证明 stale version 不能 finalize 新操作。
- [x] Green：增加 `reference_status`、`reference_operation_version`、`reference_updated_at` migration。
- [x] Green：repository 提供 `requestBind/markBound/requestRelease/markReleased/listPending`；release 完成前保留 reference ID。
- [x] Refactor：删除用单条 `releaseRemovedFromPost()` 同时表达业务意图和远程完成事实的接口。

#### REL-MEDIA-03：durable command 与处理器

- [x] Red：publisher test 证明 enqueue 与主事务同 commit/rollback。
- [x] Red：processor test 覆盖 OSS 失败重试、OSS 成功后 DB finalize 失败、handler 完成前崩溃、stale version。
- [x] Red：Spring test 断言 bind/release 远程调用时没有活动数据库事务。
- [x] Green：新增 application-owned command publisher、outbox adapter、handler 和 `PostMediaReferenceApplicationService`。
- [x] Green：使用短 claim/finalize 事务包围远程调用，不让 handler 直接访问 repository/OSS。

#### REL-MEDIA-04：改造发帖、编辑和删除

- [x] Red：重写 `PostPublishingApplicationServiceTest`，要求 create/update 只写 desired state 和 command，不直接调用 storage port。
- [x] Red：覆盖 kept asset、重复编辑、主事务回滚和所有帖子删除入口的 release 调度。
- [x] Green：`bindMediaAssets/releaseRemovedMediaAssets` 改为 request + enqueue。
- [x] Green：删除 rollback synchronization 和吞异常补偿。
- [x] Green：新增 BEFORE_COMMIT bridge，统一为 author/admin/moderation 删除调度所有 media release。
- [x] Refactor：删除事件 outbox 与 media command 必须和帖子删除同事务提交。

#### REL-MEDIA-05：恢复与历史对账

- [x] Red：覆盖 pending 丢 outbox、deleted post 遗留 BOUND、RELEASED/OSS active 漂移、BOUND/reference missing、批处理失败隔离。
- [x] Green：增加 reconciliation application/job、OSS reference query 和 pending/drift 指标。
- [x] Refactor：worker feature flag 只控制消费，不删除 pending row 或回滚 DDL。

#### REL-MEDIA-06：收敛 prepare/complete

- [x] Red：覆盖 request replay、response lost、DB finalize failed、重复 complete、metadata UNKNOWN、stale completing recovery。
- [x] Green：Content 使用 `PREPARED -> COMPLETING -> OBJECT_COMPLETED -> COMPLETED/FAILED`；事务外调用 OSS，恢复任务查询 canonical OSS metadata。
- [x] Green：OSS completed session 重放返回既有 metadata；OSS 自身增加 ObjectStore 成功而 OSS DB finalize 失败的 recovery。
- [x] Refactor：删除 prepare/complete 的 best-effort delete/cleanup。

**部署顺序**

1. 先部署 OSS 可选 deterministic reference ID。
2. 前向增加字段、索引和 migration。
3. 部署 producer，worker 暂时关闭。
4. 执行历史漂移扫描并观察。
5. 小批量开启 handler/reconciliation。
6. 删除旧 direct/after-commit 路径。

**验收**

```bash
cd backend
mvn test -pl :community-oss -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='ObjectUploadReliabilityContractTest,ObjectUploadTransactionOperationsIntegrationTest,ObjectUploadPrepareMySqlConcurrencyContractTest'
mvn test -pl :community-oss-client,:community-oss -am
mvn test -pl :community-oss-db-migrations,:community-oss -am
mvn test -pl :community-app -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='PostMediaUploadReliabilityContractTest,PostMediaUploadTransactionBoundaryTest,PostMediaAssetMapperPersistenceTest,OssPostMediaStorageAdapterTest,NacosPolicyBindingTest,*ArchTest'
```

### AUTH-01：Refresh token session 完整归 Auth

**问题**：Auth repository 经 Auth infrastructure port 转调 User API；同一 session 在 Auth port、User API 和 User domain 三次建模。直接让 User 同步调用 Auth revoke 又会制造新环。

**目标**：Auth 持有 session aggregate、repository、mapper、rotation/family/reuse 规则。Session 保存 `securityVersionAtIssue`；角色、密码或有效封禁使 User security version 递增，刷新时版本不一致即撤销 family。

**TDD**

- [x] Red：`LoginTokenIssuerTest` 要求签发 refresh token 时保存当前 security version。
- [x] Red：`LoginApplicationServiceTest` 覆盖 pending version 不一致时 revoke family、不生成 replacement、不 finish rotation。
- [x] Red：DB/Redis repository tests 覆盖版本字段贯穿 store/begin/finish。
- [x] Red：ArchUnit 禁止 Auth infrastructure 依赖 `user.api.*`，并要求 User 中不存在 refresh-session application/domain/api/persistence 类型。
- [x] Green：扩展 `RefreshTokenRepository` record 和方法；刷新前比较当前 `UserCredentialView.securityVersion`。
- [x] Green：把 User 的 model/repository/mapper/dataobject 迁至 Auth infrastructure，命名 `MyBatisRefreshTokenRepository`。
- [x] Green：删除 `RefreshTokenSessionPort`、adapter 及全部 User refresh-session API/实现。
- [x] Green：从 User role/password/moderation use case 删除 session revoke 依赖；事务放到 Auth ApplicationService。
- [x] Refactor：MyBatis repository 统一 token hash；Redis record/tombstone 同步保存 version。

**数据迁移**：新增 `auth_refresh_token.security_version`；部署前撤销全部旧 session，不能以默认 0 继续刷新。

**验证**

```bash
cd backend
mvn test -pl :community-app -am \
  -Dtest='RefreshTokenApplicationServiceTest,LoginApplicationServiceTest,LoginTokenIssuerTest,*RefreshTokenRepositoryTest,RefreshTokenCleanupJobTest,*ArchTest'
```

### TX-01：事务边界收回 ApplicationService

**问题**：User/MyBatis Like repository 自行开启事务，隐藏了“版本分配 + 业务更新 + audit/event”的真实原子范围。

**TDD**

- [x] Red：全域 ArchUnit 禁止 `..infrastructure..` 和 root `..infra..` 的类/方法标注 `@Transactional`。
- [x] Red：Admin role update 中 audit 抛错时，用户行、security counter 和角色全部回滚。
- [x] Red：Moderation event publisher 抛错时，处罚状态、policy/security counter 全部回滚。
- [x] Green：删除 User repository 无版本 update overload 和全部 persistence `@Transactional`。
- [x] Green：保留 AdminUser、UserCredential、UserModeration、Like cleanup 的 public ApplicationService 事务入口。
- [x] Refactor：初始化 counter 由 migration 完成；repository test 只测试单步映射。

依赖：`AUTH-01` 完成后执行，避免重复修改 User security use case。

### DEP-01：资料页迁入 Profile 查询组合域

**问题**：User 基础资料查询固定调用 Wallet 两次但响应不使用；随后又同步调用 Growth/Social/Content，User 反向依赖多个 owner。

**TDD**

- [x] Red：`UserReadApplicationServiceTest` 要求基础资料只访问 UserRepository，无 Wallet collaborator。
- [x] Red：新增 `UserProfileQueryApplicationServiceTest`，覆盖匿名/本人不查 hasFollowed、recent list 先校验用户。
- [x] Red：`UserProfileControllerTest` 固定三个现有 URL 和响应 JSON。
- [x] Green：新建 `profile.controller/application/result/dto`，接管三个 profile endpoint。
- [x] Green：User 发布基础 `UserProfileQueryApi/UserProfileView`；删除 wallet 字段和旧 `UserProfileApplicationService`。
- [x] Refactor：Profile 是 read composition，不创建伪 aggregate；按发布契约拆分 User lookup/profile adapters，避免一个 contract mock 移除另一个 contract bean。

### DEP-02：点赞写入迁入 Interaction 组合域

**问题**：Social 通过 `ContentEntityResolver` 反查 Content，而 Content 又依赖 Social，形成同步环。Social 不应负责解释 Content 聚合。

**TDD**

- [x] Red：`LikeInteractionApplicationServiceTest` 覆盖 USER/POST/COMMENT 目标解析、失败不调用 Social action、foreign model 转换。
- [x] Red：`LikeApplicationServiceTest` 要求构造器不含 Content/User query API。
- [x] Green：新建 `interaction` controller/application，接管 `POST /api/likes`。
- [x] Green：发布 `SocialLikeActionApi` 和 Social 自有 `ResolvedLikeTargetView`；`SetLikeCommand` 接收已解析 owner/post 信息。
- [x] Green：删除 `ContentEntityResolver`，Social 只执行点赞、block、仓储和事件规则。
- [x] Refactor：查询 endpoints 可留在 Social controller；组合域不泄漏 `ResolvedContentRef`。

依赖：`SOC-01`、`REL-DEL-04`。

### DEP-03：奖励投影迁入 Wallet

**问题**：`UserRewardKafkaListener -> UserRewardApplicationService -> WalletRewardActionApi` 只改变 Wallet 余额，却让 User 反向依赖 Wallet。

**TDD**

- [x] Red：`WalletRewardKafkaListenerTest` 固定 Content/Social event 映射、幂等 key、自赞跳过和坏事件拒绝。
- [x] Red：ArchUnit 禁止 `user.application` 依赖 `wallet.api.*`。
- [x] Green：移动为 `wallet.infrastructure.event.WalletRewardKafkaListener` 和 `WalletRewardProjectionApplicationService`。
- [x] Green：删除 User reward listener/application 和无调用方 owner API。
- [x] Refactor：Kafka topic 保持兼容；listener 只依赖独立的 `wallet.application.command.RewardProjectionCommand`，不依赖 ApplicationService 嵌套 helper。

### EVT-01：Notice listener 完成反腐转换

**问题**：Notice listener 虽反序列化 foreign payload，却继续把它作为 `Object` 传进 application/domain；幂等、LikeRemoved 撤销和输出 JSON 又必须保持不变。

**TDD**

- [x] Red：listener test 要求 Comment/Moderation/Like/Follow 映射为 Notice 自有 sealed command。
- [x] Red：application test 只构造 Notice command；golden test 固定现有 `contentJson`、topic 和 relation key。
- [x] Red：ArchUnit 禁止 `notice.application/domain` 依赖 Content/Social contracts，禁止 Notice model 声明 `Object payload`。
- [x] Green：新增 `ProjectNoticeCommand` sealed variants；所有 foreign 分派停在 listener。
- [x] Green：`NoticeProjection` 保存 Notice 自有 `NoticeContent` 或明确稳定字段。
- [x] Refactor：删除 `ProjectContentNoticeCommand`、`ProjectSocialNoticeCommand`，保留 `tryRecord` 顺序和重复语义。

### EVT-02：三类事件 envelope 去 Object 化

**问题**：`Object payload` 使 type/payload 错配只能运行期发现；仅换成泛型不能解决 Kafka 反序列化的类型擦除。

**TDD**

- [x] Red：用 BASE-00 golden samples 固定现有 JSON wire。
- [x] Red：owner decoder 参数化测试覆盖所有已知 type、malformed payload 和 unknown type。
- [x] Red：ArchUnit 禁止 contracts 声明 `Object payload`，禁止 application/domain 依赖 foreign envelope/`JsonNode`。
- [x] Green：envelope 改用 `JsonNode`；每个 owner 提供集中 encoder/decoder 和 sealed typed event。
- [x] Green：按 Content、Social、User 顺序迁移 producer、dispatcher 和全部 consumer。
- [x] Refactor：删除 listener 的重复 `normalizePayload`；滚动发布 dual reader 必须有明确删除测试。

依赖：`EVT-01` 先完成，避免 Notice application 与 listener 同时大改。

### LEAK-01：移除 application 对 root infra 的依赖

- [x] Red：ArchUnit 禁止 `..application..` 依赖 `com.nowcoder.community.infra..`。
- [x] Red：补齐 idempotency key、fingerprint 和 pagination overflow 的行为测试。
- [x] Green：把 `EffectiveIdempotencyKey`、resolver、fingerprint 移入 common-idempotency；`Pagination` 移入 common-core。
- [x] Green：更新 Content/Market/Wallet/Notice/Social imports，删除 root helper。
- [x] Refactor：root `infra` 只保留真正 adapter，不保存纯 application utility。

### LEAK-02：DAO 异常在 persistence 边界翻译

**问题**：15 个 ApplicationService 捕获 Spring DAO 异常；User domain 甚至解析 constraint name。用例层因此依赖具体数据库错误。

**公共 TDD 规则**

- [x] Red：ArchUnit 禁止 application/domain 依赖 `org.springframework.dao..`。
- [x] Red：每个用例测试 repository 的 duplicate outcome 会 reload 并校验 replay；未知完整性失败不能伪装成重复成功。
- [x] Green：只有 infrastructure 捕获 `DuplicateKeyException`，repository 返回 `CREATED/ALREADY_EXISTS/CONFLICT` 等语义结果。
- [x] Refactor：删除 domain/application 的 exception cause/message 解析。

并行切片：

- `LEAK-02A`：User。
- `LEAK-02B`：Wallet + Market。
- `LEAK-02C`：Drive + Growth。

### ERR-01：错误语义去 HTTP 化

**目标模型**

```text
ErrorCode = code + message + ErrorKind
ErrorKind = INVALID_INPUT / UNAUTHENTICATED / FORBIDDEN /
            NOT_FOUND / CONFLICT / THROTTLED / UNAVAILABLE / INTERNAL
Web/WebFlux adapter: ErrorKind -> HTTP status
```

**TDD**

- [x] Red：golden table 固定所有现有 error code 对外 HTTP status、Result.code/message/httpStatus。
- [x] Red：Servlet/WebFlux handler 参数化测试；Like 按 `ErrorKind.NOT_FOUND` 判断。
- [x] Red：ArchUnit/反射测试要求最终 `ErrorCode` 不声明 `getHttpStatus()`。
- [x] Green 1：增加 `ErrorKind` 和 mapper，handler/Like 改用 kind，旧 status 暂时 deprecated。
- [x] Green 2：所有调用迁移后删除 `getHttpStatus`、枚举 status 字段和旧 `SimpleErrorCode` 构造。
- [x] Refactor：禁止按数字 code 区间推断 HTTP status。

兼容要求：对外 HTTP status、业务 code、message 和响应体 `httpStatus` 全部不变。

### DM-MARKET-01：MarketOrder 作为富模型样板

**问题**：`MarketOrder` 虽已有少量状态断言和 transition 类型，但公开 setter 仍允许 application、test fixture 或 persistence 绕过状态机；`MarketOrderRepository` 同时暴露 `markEscrowSucceeded/markReleaseSucceeded/markRefundSucceeded/changeStatus` 等 SQL-shaped 动作，业务迁移图被复制在多个 ApplicationService、repository 方法名和 XML `where status = ...` 中。调用方往往先判断状态、再选择某个 mapper 动作，状态在两步之间变化时只能得到 `0 rows`，却无法知道是幂等 replay、非法迁移还是并发冲突。txn ID、自动确认时间和退款/释放状态也可能由不同方法分开写入，无法证明一次迁移是完整原子的。

**目标设计**：`MarketOrder` 通过 `place/reconstitute` 建立合法快照，所有写行为只从 aggregate 产生包含 `orderId + expectedStatus + nextStatus + side-effect fields` 的 `MarketOrderTransition`。repository 只提供语义创建、查询和单一 `apply(transition)`；MyBatis adapter 把 transition 原子映射为带 expected status 的 CAS，mapper/dataobject 不进入 domain/application。非法迁移在进入 repository 前失败，并发失败由 repository owner-owned result 明确表达，不由 application 解析行数或数据库异常。

**TDD**

- [x] Red：domain test 穷举 escrow 成功/失败、取消、交付、确认、release、refund、dispute 的全部合法边，并对每个非法源状态断言失败；同时固定 escrow/release/refund txn ID 和 auto-confirm 字段只能随对应迁移写入。
- [x] Red：persistence test 对每类 transition 验证 `expectedStatus` 命中时完整字段一次写入、stale status 时零修改、无关字段不被覆盖；边界测试禁止 `MarketOrder` 公共 setter，禁止 repository 重新增加 `markXxx/changeStatus`。
- [x] Green：补全 `MarketOrderSnapshot`、`place/reconstitute` 和完整 `MarketOrderTransition`；repository 收敛为 `apply(transition)` 及稳定 apply result。
- [x] Green：mapper 只接收 `MarketOrderDataObject/MarketOrderTransitionDataObject`，repository 负责 aggregate 与 row/transition 的双向转换；迁移全部 Market ApplicationService。
- [x] Refactor：删除旧 `markXxx/changeStatus`、重复 application 状态分支和 aggregate 公共 setter；保留既有 HTTP、事件、saga command 与 txn ID 兼容。

完成标准：不存在绕过 aggregate 的 MarketOrder 状态写入口；状态图的每条边只有一个领域构造点和一个 repository apply 通道；并发失败、非法迁移与幂等 replay 在测试中可区分。

### DM-WALLET-01：WalletAccount 并发不变量

**问题**：`WalletAccount` 当前是可任意改写余额、状态和 version 的 JavaBean。`WalletAccountApplicationService` 直接 `setStatus`，再调用 repository 的 `updateBalanceWithVersion(accountId, expectedVersion, delta, nextStatus)`；余额不得为负主要依赖 XML 条件，版本推进只存在于 SQL。更新返回 0 后 application 再查询一次并自行推断 `NOT_FOUND/VERSION_CONFLICT/INSUFFICIENT_FUNDS`，这次查询与失败更新不是同一判断瞬间，既有 TOCTOU 窗口，也迫使 application 理解 SQL 拒绝原因。冻结账户能否入账/出账、合法 freeze/unfreeze 边和“一次操作只推进一个版本”因此没有唯一领域入口。

**目标设计**：`WalletAccount` 只能通过 `openUser/openSystem/reconstitute` 建立，公开行为 `post/freeze/unfreeze` 先校验冻结、非负余额、合法状态边和 version overflow，再返回不可变 `WalletAccountChange(accountId, expectedVersion, delta, nextBalance, nextStatus, nextVersion)`。repository 只暴露 `apply(change)`，MyBatis adapter 执行 version + balance 条件 CAS，并把失败翻译成 `APPLIED/NOT_FOUND/VERSION_CONFLICT/INSUFFICIENT_FUNDS`；ApplicationService 只编排和映射稳定错误码，不再二次实现余额或并发判断。

**TDD**

- [x] Red：domain test 覆盖 ACTIVE/FROZEN 入账与出账、余额下溢/数值溢出、版本恰好推进一次、freeze/unfreeze 合法与非法源状态；反射守卫证明 aggregate 无 public setter。
- [x] Red：真实 persistence test 覆盖 apply 后 balance/status/version 同步落库、账户不存在、stale version、数据库当前余额不足和零行的 fail-closed 分类；application 参数化测试固定四种 repository outcome 到既有 Wallet error code 的映射。
- [x] Green：实现 `openUser/openSystem/reconstitute` 与 `post/freeze/unfreeze`，所有 change 在构造时校验账户 ID、合法状态、非负前后余额和 `nextVersion == expectedVersion + 1`。
- [x] Green：repository 收敛为 `apply(change)`；保留 mapper 内部 CAS 作为技术细节，删除 domain repository 的 SQL-shaped `updateBalanceWithVersion`。
- [x] Refactor：迁移 `ensureAccount/apply/setStatus` 后删除 reload-and-classify、aggregate setter 和重复规则；保留 Wave 4 `CreationOutcome` duplicate/replay 语义及现有 ledger 事务边界。

完成标准：application/domain 不再出现 SQL-shaped balance/status 更新；任何出入账或冻结变更都携带 expected version；持久化与应用测试能稳定区分不存在、版本冲突和余额不足，既有钱包账本回滚测试保持通过。

`DM-MARKET-01` 与 `DM-WALLET-01` 文件独立，可以并行。

### DM-COMMENT-01：Comment row 与领域规则分离

**问题**：Comment 同时充当 MyBatis row、controller/application 数据载体和领域对象，公开 setter 可修改 post、author、root、status 与删除时间。编辑窗口、作者权限、帖子归属、root/reply 删除差异分别散落在 ApplicationService 分支和 repository SQL；线程级联删除又返回独立 `CommentDeletionResult`。因此同一个删除事实存在“领域判断、SQL 批量修改、事件逐条发布”三套表达，重复删除、并发编辑和 root/reply 竞态容易造成数据库结果与 deletion event 数量不一致。

**目标设计**：mapper 只装配 `CommentDataObject`，repository 通过 `CommentSnapshot` 还原 aggregate。aggregate 负责编辑资格、帖子归属、删除状态和 root/reply 规则；单条与线程删除分别产生明确 transition，其中线程 transition 固定被影响 comment ID/expected version 集合。Comment 使用显式单调 `version`，不把可能被数据库自动更新时间语义影响的 `update_time` 当作并发 token。repository 原子应用 transition 并返回稳定语义结果，ApplicationService 只按已持久化结果逐条发布既有 deletion event。

**TDD**

- [x] Red：domain test 覆盖编辑窗口边界、作者/管理员权限、已删除状态、post 不匹配、root 级联和 reply 单删；并发或重复命令必须有明确 no-op/conflict 结果。
- [x] Red：repository test 固定线程受影响集合、重复删除幂等、stale state 失败和现有 `CommentDeletionResult` 顺序/字段；golden test 保持 deletion event 数量与 payload 不变。
- [x] Green：引入 `CommentDataObject`、`CommentSnapshot` 和 aggregate 构造/行为；mapper/dataobject 不再作为 domain 类型返回。
- [x] Green：使用 `CommentDeletion/CommentThreadDeletion` transition 原子持久化单条或线程删除，ApplicationService 从结果发布事件。
- [x] Refactor：删除 domain persistence setter、重复 SQL-shaped 业务方法和 application 规则分支，不改变现有 URL、权限和事件 wire。

完成标准：Comment 的编辑/删除规则只有 aggregate 一个入口；row object 与 domain model 类型分离；重复、并发和线程删除均有可执行证明，事件数量严格等于实际首次删除的 comment 数量。

依赖：`REL-DEL` 全部完成后执行，避免同时修改 `CommentApplicationService`。

**验证**

```bash
cd backend
mvn test -pl :community-app -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='CommentAggregateContractTest,CommentThreadTransitionContractTest,MyBatisCommentRepositoryTest,CommentThreadLockingPersistenceIntegrationTest,CommentPersistenceBoundaryContractTest,CommentMapperPersistenceTest,CommentDeletionCardinalityContractTest,CommentApplicationServiceTest,CommentSideEffectBoundaryArchTest'
mvn test -pl :community-db-migrations,:community-app -am
```

### ARCH-01：自动发现模块清单

- [x] Red：synthetic package tests 固定一级包发现算法；真实 class scan 与当前手工集合 exact equality 失败。
- [x] Green：自动发现含 tactical layer 的 root，并显式分类 core、adapter、platform、technical。
- [x] Green：`profile` 生产 root 落地时纳入显式分类，并同步 exact inventory 断言。
- [x] Green：`interaction` 生产 root 落地时纳入显式分类；已删除不存在的 `message`，分类集合不得重叠。
- [x] Refactor：新业务 root 未分类时立即失败，不再使用 `contains`。

### ARCH-02：统一层次和技术泄漏规则

在对应生产任务完成时逐条启用，禁止长期白名单：

- [x] 所有 infrastructure persistence 不得拥有事务。
- [x] application 不得依赖 root infra、Spring DAO/transaction support。
- [x] application/domain 不得依赖 foreign `contracts.event`。
- [x] contracts envelope 不得声明 `Object payload`。
- [x] 已迁移 aggregate 不得公开 setter。

### ARCH-03：同步依赖图无环守卫

- [x] Red：只采集 `core application -> foreign api.query/action/model`，输出具体 edge 和 origin class。
- [x] Red：先用 characterization 锁定当前图，每删除一条边就同步收紧 expected edge set。
- [x] Green：DEP、AUTH、REL-DEL 完成后运行 Tarjan/DFS，要求无 SCC cycle。
- [x] Green：维护允许 edge exact set；新增同步边必须显式评审。
- [x] Refactor：不得用 cycle whitelist 伪装无环。

## 发布与回滚原则

- 所有 schema 变化只做 forward migration；应用回滚不回滚 DDL。
- durable producer、consumer 和 reconciliation 分阶段启用。先部署可读状态和幂等 consumer，再开启 producer，最后删除旧路径。
- 删除旧同步路径前必须完成历史数据扫描和故障重放测试。
- feature flag 只控制新 worker/producer 是否运行，不删除 pending/outbox/history 数据。
- wire contract 保持 JSON 字段兼容；需要 dual reader 时必须设置一个发布窗口和 retirement test。
- correctness runtime 不保留 Redis Social 写模式作为回滚选项。

## Wave 验收与最终验证

每个 wave 结束运行：

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
mvn test -pl :community-app -am
```

涉及 OSS 时追加：

```bash
cd backend
mvn test -pl :community-oss-client,:community-oss -am
```

涉及 migration 时追加：

```bash
cd backend
mvn test -pl :community-db-migrations,:community-app -am
```

全部任务完成后的最终命令：

```bash
cd backend
mvn test
```

最终故障注入至少包括：

- Content commit 后、Kafka publish 前停进程。
- Kafka consumer 连续失败进入 DLT，再 replay 同一事件。
- OSS bind/release 成功但响应丢失。
- OSS 成功后、Content DB finalize 前停进程。
- ObjectStore PUT 成功后、OSS session/version/object DB finalize 失败；恢复必须认领既有 attempt，不能再次 PUT 或让旧 claimant 覆盖 winner。
- OSS stale claimant 在新 claim 获胜后迟到 finalize；同一 request ID 以不同 fingerprint 并发 prepare。
- Content upload recovery 遇到 poison row、UNKNOWN metadata 和 stale `COMPLETING` 时继续处理后续候选。
- migration 从空库和受支持基线升级。
- refresh token 在 security version 变化前后并发刷新。
- 点赞与帖子/评论删除并发。
- Comment root 删除与 reply 创建并发；重复 root/reply 删除的事件数量必须等于首次实际修改行数。

完成标准不是“测试命令退出 0”，而是恢复后 Content、Social、Auth、OSS 和数据库 history 最终收敛，且不存在重复 reference、孤儿点赞、旧 token 可刷新、永久 pending 或新增同步依赖环。

### 已知残余风险：OSS attempt object GC

`claimVersion` fencing 已阻止旧 writer 覆盖 winner，canonical correctness 已由并发测试证明；但 loser 写入的 `.claim-*` attempt object 可能成为 orphan。当前 winner 的 canonical key 也使用 `.claim-*`，因此不能直接对该命名模式配置 bucket lifecycle，否则可能删除仍被引用的 winner。这不影响上传结果正确性，但会影响长期存储成本和删除合规。

后续实现必须另起 TDD 任务并二选一：

1. 只有 session CAS winner 可进入持久化的 `PROMOTING -> PROMOTED` 流程；以可幂等恢复的 copy/promote 写入稳定 canonical key，原子记录 source/target 和 winner claim version，再只对已脱离 canonical 引用的 attempt namespace 配置 lifecycle。普通的一次性 copy + DB key 更新不满足该方案。
2. 持久化 attempt 记录；grace period 后再次核对 canonical key 与 claim version，再幂等删除 loser，并暴露扫描、删除、失败和积压指标。

在上述任务完成前，不得把 attempt object GC 标记为已解决，也不得用不校验 winner 的直接删除脚本代替。

## 当前验证基线

为确保使用工作区内最新 common module 源码、真实 migration 和最终部署配置，Wave 6 barrier 已执行：

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
mvn test -pl :community-app -am
mvn test

cd ..
deploy/tests/community_migration_contract.sh
deploy/tests/oss_migration_contract.sh
deploy/tests/im_migration_contract.sh
deploy/tests/development_clean_break_contract.sh
deploy/tests/nacos_config_seed.sh
git diff --check
```

当前结果（2026-07-15 Wave 6 final barrier）：135 个架构测试全部通过；`community-app` 为 2094/2094。完整 `backend` reactor 的 25 个 module 全部 `SUCCESS`，总耗时 06:06；其中 `community-im-db-migrations` 13/13、IM Core 66/66、`community-db-migrations` 12/12、`community-oss-db-migrations` 9/9、OSS client 13/13、OSS service 80/80，真实 MySQL 测试均运行且所有集合都是 0 failures、0 errors、0 skipped。Content/Comment 独立复验为 10 suites、60/60，Comment 直接契约 35/35；OSS reliability focused 21/21、prepare MySQL 并发 1/1；Nacos binding 6/6。

五个 deployment contract 全部 exit 0：Community、OSS、IM migration topology，development clean-break 和 Nacos seed 均通过。同步 foreign API exact set 保持 59 条，Tarjan/DFS 为 0 个 SCC cycle；`git diff --check` exit 0。至此本计划列出的 Wave 0-6 实现任务全部达到 `DONE`，没有未勾选 checklist；已知但未冒充完成项的是上节 OSS loser attempt object GC，它不破坏 canonical correctness，但仍需独立 TDD 后续任务处理存储成本与删除合规。
