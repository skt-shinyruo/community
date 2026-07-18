# Community 正确性加固设计

- 日期：2026-07-18
- 状态：设计已批准
- 范围：2026 年 7 月代码审计发现的 15 个正确性、安全性、并发和前端会话问题
- 选定方案：在现有 owner-domain 边界内进行针对性加固

## 1. 背景

本次审计发现了十五个实现缺陷。这些缺陷可能泄露私有 OSS 对象、破坏后台 OSS 任务、把两个独立用户操作错误合并为一次幂等重放、重复执行业务副作用、在并发下破坏配额或库存正确性、接受客户端伪造的服务端事实，以及让公开前端路由依赖私有认证数据。

修复范围涉及 `community-app`、`community-oss`、`community-oss-client`、`community-gateway`、共享幂等/outbox/web 模块、IM outbox 存储、前端、部署配置以及只向前的 Flyway 迁移。所有修复必须维持仓库的严格 DDD Tactical Layering，不能引入第二套应用入口风格，也不能让适配器直接编排其他 owner domain。

## 2. 目标

1. 关闭所有已报告的授权和身份漏洞。
2. 保证 HTTP 幂等、outbox 租约、库存补偿、网盘配额释放和审核决策在重试与并发下正确。
3. 让钱包冲正和点赞奖励生命周期使用真实业务身份，而不是偶然的请求键或关系键。
4. 保证内容终态删除和评论回复目标来自服务端权威状态。
5. 统一前端刷新行为，并保持市场详情可以匿名浏览。
6. 通过只向前迁移、明确的兼容发布顺序、确定性测试和运维可观测性安全交付。

## 3. 非目标

- 重写 deployable 或替换 package-scoped monolith。
- 引入分布式事务或新的工作流引擎。
- 改变市场列表和详情路由的匿名公开属性。
- 改变钱包的有符号 `BIGINT` 存储，或禁止特权冲正产生负余额。
- 修改冻结的 Flyway 基线以描述基线之后的变化。
- 重构与本次问题无关的遗留包。

## 4. 架构约束

所有触及的 `community-app` 业务路径遵循：

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> same-domain *ApplicationService
      -> domain model / policy / repository interface
      -> 需要同步跨域协作时调用 foreign owner api.query / api.action
      -> 需要异步跨域协作时发布 owner contracts.event
          -> infrastructure implementation
```

入站适配器只绑定传输层输入、提取已认证身份并转换 DTO。ApplicationService 负责事务边界、命令、幂等、权威数据查询、跨域调用和结果装配。Domain 代码保持独立，不依赖 Spring、HTTP、持久化对象、infrastructure 或发布的 owner API。MyBatis 和 Redis 必须位于 domain repository 或聚焦的 application-owned technical port 之后。

任何修复都不得引入 Controller 到 Repository、入站适配器到 foreign API、同域 `api.*` 绕行、ApplicationService 到 Mapper，或 application command/result 到 HTTP 类型的依赖。

## 5. 身份与访问控制

### 5.1 OSS 对象授权

OSS 服务分离三个安全入口：

| 入口 | 身份 | 规则 |
| --- | --- | --- |
| `/api/oss/**` | 用户 JWT | actor 和 owner 只能来自认证上下文 |
| `/internal/oss/**` | 服务 JWT | token 必须具有 `aud=community-oss` 和 `scope=oss.internal` |
| `/files/**` | 匿名 | 只允许读取 `PUBLIC + ACTIVE` 对象 |

用户接口 DTO 删除 `ownerId` 和 `actorId`。这些字段不能通过兼容别名或回退逻辑进入 application command。

每个用户操作都先进入 OSS ApplicationService。ApplicationService 加载对象和调用者所需的有效 grant，再调用纯领域访问策略。策略至少执行以下规则：

- 创建对象时把 owner 绑定为已认证用户；
- 私有元数据和签名下载需要 owner 身份或适用的有效 grant；
- 创建/撤销 grant 和删除对象只允许 owner；
- 公共字节只通过 `/files/**` 暴露，且对象必须同时为 `PUBLIC` 和 `ACTIVE`；
- 对不存在对象和需要隐藏存在性的未授权对象，对外统一返回 `404`。

策略只接收身份、对象事实、grant 事实和请求能力，不读取安全上下文、HTTP 请求、Mapper 或存储适配器。

### 5.2 后台 OSS 服务身份

`community-oss-client` 为内部操作增加 service token provider。后台 handler、job 和 ApplicationService 使用 typed internal client 调用 `/internal/oss/**`，并携带短期服务 JWT。它们不依赖 Servlet 请求，也不转发任意浏览器 Bearer Token。

服务 JWT 在 `sub` 中标识调用服务，并使用配置的服务凭据签名。OSS 只有在 audience 和 scope 匹配时才接受它，并把服务 subject 写入审计上下文。内部接口只暴露引用管理、恢复和清理所需的能力；服务身份不能伪装成终端用户。

启动时校验密钥以及 issuer/audience 配置。生产环境缺少凭据时 fail closed。

### 5.3 规范化客户端 IP

边缘层和应用层采用唯一的信任模型：

1. 对外 NGINX 删除客户端提供的 `Forwarded`、`X-Forwarded-For` 和 `X-Real-IP`；设置 `X-Forwarded-For` 时使用 `$remote_addr`，不再使用 `$proxy_add_x_forwarded_for`。
2. Gateway 只有在直接对端属于已配置的可信 NGINX 地址时才接受该 Header。它从右向左遍历直接对端和转发链，丢弃可信代理跳，选择第一个不可信跳作为规范客户端地址。
3. 如果 Gateway 的直接对端不可信，则忽略全部转发 Header，使用 socket 对端地址。
4. Gateway 转发前删除所有传入的转发 Header，并重新生成只包含规范客户端地址的 `X-Forwarded-For`。
5. 应用只有在直接对端属于已配置的 Gateway 地址时才接受该规范 Header，否则使用自己的 socket 对端地址。

包括帖子浏览采集在内的所有消费者都使用 shared web boundary 提供的规范地址。Controller 不再自行解析转发 Header。部署默认配置包含真实的 NGINX/Gateway 网络，避免正常流量全部退化为同一个容器 IP。

## 6. 可靠性基础设施

### 6.1 前端幂等身份

前端删除全局的 10 秒 URL/请求体指纹缓存。每次新的业务调用都生成新的幂等键，即使 URL 和请求体与最近一次调用完全相同。

幂等键保存在 Axios request config 中。同一个 config 的传输重试保留原 Header；新的 API 方法调用创建新键。测试明确区分：

- 两次连续且内容相同的充值、下单、发帖或评论使用不同键；
- 同一个失败 request config 的重试继续使用原键。

### 6.2 事务型 HTTP 幂等

`executeRequired` 是受保护写接口的正确性入口。它要求 JDBC-backed idempotency store，并要求当前存在与同一 datasource 同步的活动事务。缺少事务或 store 不兼容时，必须在业务执行前失败。

同一个数据库事务按顺序执行：

1. 使用请求指纹占用 `(operation, userId, idempotencyKey)`；
2. 执行业务函数；
3. 序列化成功结果；
4. 写入序列化响应和 `SUCCESS` 状态；
5. 原子提交幂等记录和业务修改。

成功状态不再通过 `afterCommit` 写入。序列化失败会抛出异常并回滚整个事务，绝不能转换为字符串 `"null"`。成功重放必须反序列化出有效响应，否则按基础设施故障处理。

已有记录按以下规则处理：

- 同键、同指纹且状态为 `SUCCESS`：重放已保存结果；
- 同键但指纹不同：`409 IDEMPOTENCY_REPLAY_CONFLICT`；
- 当前仍在并发处理中：`409 IDEMPOTENCY_IN_PROGRESS`；
- 被隔离的历史处理中记录：`409 IDEMPOTENCY_OUTCOME_INDETERMINATE`。

发布前停止并排空旧 writer。任何残留的旧 `PROCESSING` 记录都具有无法判断的结果，迁移为 `INDETERMINATE`；它绝不能因超时而获得重新执行业务的许可。客户端遇到不确定结果时，不能被建议更换新键。

### 6.3 Outbox 租约 fencing

每次 claim 生成新的不透明 lease token，并与独立的 processing deadline 一起保存。被 claim 的事件把 token 从 worker 一直携带到 store。

所有 `SUCCEEDED`、retry 和 `DEAD` 更新都使用 compare-and-set，条件至少包含事件身份、`PROCESSING` 状态和 lease token。过期记录恢复时也必须先使旧 token 失效，之后事件才能被重新 claim。因此旧 worker 不能覆盖新 owner 已写入的结果。

终态更新影响 0 行表示当前 worker 已失去所有权。Worker 停止处理该结果，记录结构化 warning 和 counter，且不得执行无条件兜底更新。Outbox 仍然是至少一次投递，所以 handler 继续保持幂等。

Community 和 IM 的 `outbox_event` 表使用同一份共享实现和 schema 语义。旧 worker 与 fenced worker 绝不能混跑。

### 6.4 唯一的前端刷新协调器

普通 HTTP、IM HTTP 和认证 bootstrap 共用一个 refresh coordinator 和一个 in-flight Promise，不再分别发起 refresh。

每个认证请求记录自己使用的 access-token generation。收到 `401` 时：

- 如果 auth store 已有更新 token，则直接用新 token 重试一次，不发 refresh；
- 否则加入或发起共享 refresh；
- 标记已重试，避免第二次 `401` 形成循环。

刷新成功后，store 安装新 token，并且只重新加载一次当前用户和角色。替换数据返回前可以继续展示原 profile，不能清空后又不重新加载。终态刷新失败只有在没有出现更新 token generation 时才清空前端认证状态。

后端在无效 refresh 请求的响应中不附带删除 Cookie 的 Header，因为并发成功轮换可能已经安装了更新 Cookie。显式 logout 和显式 session revoke 仍负责清除 refresh Cookie。

## 7. 业务与资金一致性

### 7.1 市场托管补偿

市场订单状态机把 `ESCROW_FAILED` 定义为“预留库存已经完成补偿”。从该状态恢复时只能转为 `CANCELLED`，不能再次增加库存。

库存恢复只绑定在第一次记录托管失败的 guarded transition 上。Action 状态写入失败后重放 `completeEscrowNoop`，只执行订单终态转换。Repository 更新继续使用条件更新；测试在订单补偿和 action 完成之间注入故障，证明库存永远不会超过下单前数值。

### 7.2 网盘永久删除与配额

每次永久删除使用一个 `REQUIRES_NEW` 事务。ApplicationService 锁定 owner 的 space row，重新读取目标 entry 和子树，并把每个符合条件的行从 `TRASHED -> DELETED`。Repository 返回本事务实际赢得转换的行；配额只减去该返回集合中文件字节数的总和。

Space 更新和 entry 转换一起提交。并发 loser 释放 0 字节并返回既有终态。即使用户还有其他文件，配额不变量也保持成立。

OSS 字节删除只在数据库事务提交后执行，因此网络调用不会持有数据库锁。如果 OSS 删除失败，请求返回存储不可用；再次执行永久删除时会选择已经 `DELETED` 的文件重试 OSS 清理，但不会再次释放配额。

### 7.3 钱包特权冲正

钱包领域行为区分普通扣款和特权冲正：

| 操作 | 冻结/最低余额检查 | 是否可产生负余额 |
| --- | --- | --- |
| 普通扣款、消费、转账 | 执行 | 否 |
| 特权撤奖或管理员冲正 | 由显式策略绕过 | 是 |
| 普通入账 | 允许并增加余额 | 优先偿还已有负余额 |

特权模式不是客户端可控 Flag。只有专用且已授权的应用用例可以携带可审计的冲正原因和来源身份请求冲正。Domain model 执行策略；ApplicationService 继续负责 actor 授权和幂等。

双分录不变量保持不变。现有有符号 `BIGINT` balance 已支持负债，不需要类型迁移。

### 7.4 审核 claim 与决策

审核处理使用原子的 `PENDING -> PROCESSING` claim。未赢得 claim 的请求不能执行处罚、通知或 action 写入。

对 winner 而言，决策校验、owner-domain 副作用、唯一 `moderation_action`、举报终态和通知 outbox row 在一个事务中提交。事务中不放置外部网络副作用。回滚会把全部持久状态恢复到 claim 之前。

非空 `moderation_action.report_id` 唯一。如果重放的规范化决策字段与已有 action 相同，则返回已有 action，不重复副作用。同一举报上的不同决策，或其他决策已获胜后的过期尝试，返回 `409 MODERATION_DECISION_CONFLICT`。不关联举报、`report_id` 为空的直接管理 action 继续允许存在多条。

## 8. 事件生命周期与服务端事实

### 8.1 点赞关系实例与钱包奖励

稳定关系键 `(userId, entityType, entityId)` 描述当前关系对，但不能标识两次独立的点赞生命周期。因此，每次新点赞成功时生成 UUIDv7 `relationInstanceId`，持久化到 `social_like`，并贯穿：

- `LikeRelation` 和 Repository scan；
- 点赞创建/移除 domain event；
- 发布的 `LikePayload` contract；
- 批量移除点赞的内容删除清理。

取消点赞使用包含 relation instance 的 compare-and-set 删除，并发布完全相同的 instance。对同一目标再次点赞会创建新 instance。稳定 relation key 继续用于通知分组和现有通知语义。

钱包幂等来源使用 `<relationInstanceId>:created` 和 `<relationInstanceId>:removed`。任一生命周期事件重复投递都无害，后续重新点赞则属于新的奖励生命周期。Consumer 只有在读取不含 `relationInstanceId` 的旧事件时才回退到 legacy relation key。该加法 contract 要求兼容的钱包 consumer 先于新 social producer 发布。

### 8.2 帖子终态删除 tombstone

删除帖子时同时写入终态和 `update_time = deleted_time`。Hot-feed projection command 显式标识终态删除，不能把它表达成普通版本更新。

Redis projection 按以下规则应用删除：

1. 删除绕过普通 stale-version 拒绝；
2. 删除 hot-feed 和相关缓存 projection；
3. 用一个原子操作写入已处理 event identity、最大已观察 version 和永久 deletion tombstone；
4. tombstone 不设置 TTL，并拒绝任何后续非删除 projection，不考虑其 version；
5. 重复删除 event 保持幂等。

Contract 保持稳定 event ID：`content:PostDeleted:<postId>`。这样，更高版本的评论/点赞 projection 也不能保留或复活已删除帖子。

### 8.3 评论回复目标

客户端不再提交 `replyToUserId` 或 `targetId` 作为可信事实。这些字段从 HTTP request、application command、幂等指纹和发布的同步 action API 中删除。

`parentCommentId` 表示直接回复的评论：

- 顶级评论没有 parent；
- 回复 root comment 时发送 root comment ID；
- 回复嵌套 reply 时发送该 reply 的 ID。

Application 加载 `ACTIVE` direct parent。Domain 根据已存储事实推导 root comment、direct parent、target author 和通知接收人。Repository 插入前锁定并重新校验 root 和 direct parent，包括 post 归属和 active 状态。是否抑制给自己的通知继续由服务端策略决定。

前端只改变提交的 parent ID。已有扁平回复展示保持兼容，因为存储中的 root/thread 字段仍由服务端推导并保留。

## 9. 匿名市场详情

市场详情把公开 listing state 与私有 address state 分开。加载 listing 永远不依赖地址请求。

只有已认证且正在查看实物 listing 的用户才请求私有地址簿。地址请求返回 `401`、空数据或暂时故障时，不能用页面级错误替换已经加载成功的公开 listing。已认证的空状态和错误状态继续提供已有地址管理和重试操作。

Address loader 同时观察 listing identity/type 和 authentication-token generation。它取消旧请求或使用 sequence guard，并丢弃属于旧 listing 或旧认证状态的响应。

购买流程：

1. 先检查认证；
2. 匿名用户跳转到登录页，并以 `route.fullPath` 作为返回目标；
3. 只有已认证的实物买家才必须选择有效地址；
4. 虚拟商品购买不依赖地址状态。

Refresh coordinator 和匿名市场改动作为同一个前端 bundle 发布。

## 10. 错误语义

| 条件 | HTTP 状态 | 对外行为 |
| --- | --- | --- |
| 缺少认证或认证无效 | `401` | 要求认证 |
| 已认证但全局无权限 | `403` | 拒绝访问 |
| 需要隐藏对象存在性的对象级拒绝 | `404` | 与对象不存在不可区分 |
| 参数校验失败 | `400` | 返回稳定校验 code/detail |
| 幂等请求指纹冲突 | `409` | 不执行业务操作 |
| 历史幂等结果不确定 | `409` | 不建议更换新键 |
| 审核决策冲突或已过期 | `409` | 相同决策返回既有结果 |
| 暂时性依赖/基础设施故障 | `5xx`，通常为 `503` | 按接口策略允许重试 |
| 丢失 outbox lease | 不映射 HTTP | 作为正常 CAS miss 记录 metric/log |

所有 HTTP 失败继续使用仓库统一的 `Result<T>` envelope 和 trace 传播。

## 11. 只向前数据库迁移

### 11.1 Community schema

当前 `V007` 之后增加：

- `V008__add_outbox_lease_fencing.sql`：为 `outbox_event` 增加 nullable `lease_token BINARY(16)`、nullable `processing_lease_until` 和 processing-lease scan index。
- `V009__quarantine_indeterminate_http_idempotency.sql`：完成强制 writer drain 后，把残留 legacy processing row 转为显式 indeterminate 状态，并清理已经失去意义的 processing expiry。
- `V010__enforce_unique_moderation_action_report.sql`：发现重复的非空 report ID 时失败；否则把非唯一 report index 替换为唯一约束，同时继续允许多个 null report ID。
- `V011__add_social_like_relation_instance.sql`：增加 nullable binary UUID 列，为每个已有 row 回填不同值，添加唯一索引，最后把列改为 non-null。

应用新建的 relation instance 使用 UUIDv7。历史回填值只需要在迁移后保持唯一和稳定，不需要伪装为带创建时间排序的信息。

### 11.2 IM schema

当前 IM `V001` 之后增加：

- `V002__add_outbox_lease_fencing.sql`：给 IM `outbox_event` 增加同样的 lease token、processing deadline 和 scan index。

OSS 保持在 `V003`，因为本轮修复不需要 OSS schema 变化。

### 11.3 迁移规则

- 不修改 `V001` 或任何已执行 migration。
- Schema manifest 描述用于校验 legacy baseline 的冻结 version-one 基线；不能仅为表示后续 Flyway migration 而修改它。
- 更新 migration count 断言、迁移后 column/index 断言、upgrade fixture，以及模拟当前运行时表的 H2/test schema。
- 使用 Testcontainers 在真实 MySQL 上测试空 schema 和 version-one upgrade。
- 审核 migration 先检查重复数据；发现冲突时中止发布，不删除或合并审计历史。

## 12. 兼容与发布顺序

以下两个 drain 是强制正确性门禁：

1. 在隔离 legacy `PROCESSING` row 前停止并排空旧 HTTP idempotency writer，其业务结果无法被安全推断。
2. 在 Community 和 IM 启用 token fencing 前停止并排空所有旧 outbox worker。旧/新 worker 混跑不安全，因为旧终态更新没有 fencing。

完整顺序如下：

1. 配置 service-JWT 签名/验证密钥以及预期 issuer/audience/scope。
2. 部署 Gateway/NGINX 转发 Header 清洗，再在应用中启用匹配的 trusted-proxy CIDR。
3. 部署分离用户/内部身份及 internal endpoint 的 OSS。
4. 部署后台 OSS caller 和具备服务身份的 `community-oss-client`。
5. 执行只读的审核重复数据预检；发现重复时，在维护窗口之前中止发布并进行显式数据审查。
6. 停止并排空所有旧 Community application instance，包括受保护的 HTTP writer 和 outbox worker；同时停止并排空 IM outbox worker。这样同时满足幂等与 fencing 门禁，也防止旧 social writer 插入缺少 relation instance 的 row。
7. 执行 Community `V008` 到 `V011` 以及 IM `V002`；审核 migration 在 Flyway 内再次执行重复断言。
8. 部署事务型幂等实现、全部 fenced-outbox worker 和 schema-compatible Community/IM application。确认不存在旧 worker 或旧 Community writer 后，恢复流量和 worker。
9. 第一次 Community rollout 中，钱包 consumer 接受 optional `relationInstanceId`，新 relation-instance event 的发送由 rollout gate 保持关闭。
10. 所有钱包 consumer instance 兼容后，开启 social producer 和内容删除清理的 relation-instance event。兼容窗口结束后的后续版本可以删除临时 gate。
11. 把 refresh coordinator 和匿名市场行为作为同一个前端 bundle 发布。

回滚采用 application-forward，而不是 migration-down：停用或重新部署仍兼容加法 schema 的 application code，修复问题，并在需要修复 schema 时发布新的 migration。不能撤销已经执行的 Flyway 文件。

## 13. 可观测性

增加或保留以下结构化信号：

- OSS 授权拒绝，按入口、capability 和 caller type 分类，但不暴露对象秘密；
- service-JWT 校验失败原因；
- indeterminate 幂等响应和 replay conflict；
- outbox lease-loss CAS miss，按 topic/handler 分类；
- 市场补偿尝试和 affected-row count；
- 网盘删除 winner/loser 和 released bytes；
- 审核 claim conflict 和 replay 分类；
- refresh 请求加入共享任务、stale-token retry、成功和终态失败；
- 被丢弃的过期 address response。

日志保留 trace context，但不得记录 JWT、Cookie、签名 URL、地址或原始私有 payload。

## 14. 验证策略

每个缺陷都按 red-green-refactor 实施。最低验证矩阵如下：

| 区域 | 必须证明 |
| --- | --- |
| OSS | owner、有效 grant、无关用户、public-active、public-deleted/private、用户 JWT 与服务 JWT、无 Servlet context 的后台调用 |
| 客户端 IP | 不可信直连、可信单跳/多跳、伪造左侧前缀、畸形地址、帖子浏览路径使用规范 resolver |
| 前端幂等 | 相同的新操作使用不同键；同一 config 重试保持同一键 |
| 后端幂等 | 业务与成功状态原子提交、序列化回滚、指纹冲突、并发同键、indeterminate legacy row、缺少事务/store |
| 市场 | Action 完成写入故障后恢复，库存也绝不二次增加 |
| 网盘 | 两个并发永久删除只产生一次状态转换和一次配额释放；OSS 失败不会再次释放 |
| Outbox | 新 token claim 后，过期 worker 不能写 success/retry/dead；Community 和 IM schema 都支持 fencing |
| 钱包 | 普通透支拒绝、正常执行冻结/最低余额规则、特权冲正可产生负债、后续入账偿债、总账平衡 |
| 点赞 | create/remove 重复投递、remove-before-create、unlike/re-like instance、legacy payload fallback、内容删除批量移除 |
| 帖子删除 | tombstone 后，低/相同/高版本 projection 都不能残留或复活；重复删除幂等 |
| 审核 | 两个并发管理员只产生一个 action/副作用/通知；相同重放返回已有结果；冲突重放返回 `409` |
| 评论 | 伪造字段消失、direct parent 决定接收人、嵌套回复推导 root、锁内拒绝 inactive/mismatched parent |
| Refresh | HTTP/IM/bootstrap 共用一个请求、stale `401` 使用新 token 重试、旧 refresh 失败不清除新 token/Cookie、重新加载 profile |
| 市场详情 | 匿名实物/虚拟详情、认证地址成功/空/失败、认证或 listing 切换丢弃旧响应、登录返回路径 |
| Migration | 空 MySQL、V001 upgrade、重复执行、审核重复预检失败、relation 唯一回填、冻结 baseline 校验 |

后端边界修改后必须通过架构守卫：

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

后端综合验证目标：

```bash
cd backend
mvn -pl :community-common-idempotency,:community-common-outbox,:community-common-web,:community-gateway,:community-oss-client,:community-oss,:community-app,:im-core -am test
mvn -pl :community-db-migrations,:community-im-db-migrations,:community-oss-db-migrations -am test
```

Migration 测试需要 Docker 来运行 MySQL Testcontainers。前端验证：

```bash
cd frontend
npm test
npm run build
```

## 15. 问题追踪

| 问题 | 设计章节 | 完成判据 |
| --- | --- | --- |
| 1. OSS 对象级越权 | 5.1 | 不存在用户控制身份；策略覆盖元数据、签名访问、grant、删除和公共字节 |
| 2. 后台 OSS 无身份 | 5.2 | 内部 job 在无 request context 时也能使用 scoped service identity 成功调用 |
| 3. 前端错误复用幂等键 | 6.1 | 内容相同的独立操作使用不同键 |
| 4. 幂等事务提交后故障窗口 | 6.2 | 业务结果、序列化响应和成功状态原子提交 |
| 5. 市场重复库存补偿 | 7.1 | 从 `ESCROW_FAILED` 恢复不能增加库存 |
| 6. 网盘重复释放配额 | 7.2 | 并发删除中只有一个请求释放字节 |
| 7. Outbox 租约无 fencing | 6.3 | 所有 processing 终态更新都受 token CAS 保护 |
| 8. 客户端 IP 不可靠 | 5.3 | 所有路径使用经过清洗、从右向左解析的信任链 |
| 9. 撤奖/管理员冲正失败 | 7.3 | 特权冲正绕过消费限制并可产生负债 |
| 10. 点赞生命周期奖励碰撞 | 8.1 | 每个点赞生命周期具有持久 relation instance |
| 11. 已删除帖子仍留在 projection | 8.2 | 永久 tombstone 支配所有非删除 version |
| 12. 并发审核 | 7.4 | 一个举报最多产生一个持久 action 和一组副作用 |
| 13. 伪造回复接收人 | 8.3 | 接收人和 thread 事实只由已锁定的存储评论推导 |
| 14. Refresh 竞态 | 6.4 | 所有 client 协调 refresh，过期失败不能清除更新认证 |
| 15. 实物详情不能匿名 | 9 | 公开详情不依赖私有地址状态也能成功 |

只有全部十五项判据和验证矩阵通过，且未削弱 DDD 架构测试时，本设计才算完整实现。
