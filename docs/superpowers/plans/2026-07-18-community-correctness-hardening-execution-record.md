# Community Correctness Hardening Execution Record

**计划基线：** `930bcc21` (`docs: add correctness hardening implementation plans`)

**实现范围：** `930bcc21..45278d48`，共 70 个实现提交，执行日期为 2026-07-18 至 2026-07-21。

**本地结论：** 15 个问题的代码实现、自动化测试、真实 MySQL migration 回归、架构守卫、前端全量测试、production build 和静态扫描均已完成。生产 secret、真实 trusted CIDR、生产数据预检、维护窗口、部署顺序、consumer gate、发布后观察等外部门禁未在本 worktree 执行，不能以本地绿测替代。

## 问题完成证据

| 问题 | 本地状态 | 主要证据 |
| --- | --- | --- |
| 1. OSS 对象级越权 | 完成 | 对象访问策略、subject 绑定、grant/version 关系隐藏与授权测试 |
| 2. 后台 OSS 无身份 | 完成 | 用户/service JWT 双入口、scoped service token client、全局调用方切换 |
| 3. 前端错误复用幂等键 | 完成 | 每次 invocation 新 key，同一 config retry 保留 key，header 大小写兼容 |
| 4. 提交后幂等窗口 | 完成 | transactional JDBC store、业务与响应同事务、V009 隔离历史 processing row |
| 5. 市场重复补库存 | 完成 | escrow compensation 状态事实与 lease replay 测试 |
| 6. 网盘重复释放配额 | 完成 | 永久删除 winner 集合及仅 winner 释放 quota |
| 7. Outbox 无 fencing | 完成 | Community V008、IM V002、lease token CAS、旧 worker lease loss 停止 |
| 8. 客户端 IP 不可靠 | 完成 | 纯 IP/CIDR 信任链、Gateway 规范化、NGINX header 清洗与固定拓扑 |
| 9. 钱包冲正失败 | 完成 | privileged correction posting、审计纠正允许负余额且分录平衡 |
| 10. 点赞奖励碰撞 | 完成 | Community V011、持久 relation instance、兼容事件 producer/consumer |
| 11. 删除帖子复活 | 完成 | 永久 tombstone、过期清理有界、终态删除推进 version |
| 12. 并发审核 | 完成 | Community V010 唯一约束、原子 report claim、冲突 wire contract |
| 13. 伪造回复接收人 | 完成 | 客户端字段移除、direct parent 锁内推导 root/target、前端只传 parent |
| 14. Refresh 竞态 | 完成 | token generation、单 coordinator、HTTP/IM/bootstrap 共享、stale Cookie 保护 |
| 15. 匿名实物详情失败 | 完成 | listing/address 双状态、匿名不取地址、登录回跳、晚响应丢弃 |

## 提交清单

### 可信客户端 IP

- `e09f11a3` feat(web): add trusted proxy chain resolver
- `90f040a0` test(web): harden trusted proxy address boundaries
- `eba6c27f` fix(web): resolve servlet client ip from trusted chain
- `5d7b87e9` test(web): bound forwarded header adaptation
- `79c572e1` fix(gateway): canonicalize forwarded client address
- `00d2447f` fix(gateway): harden rate limit order and forwarded headers
- `aa322ba6` fix(content): use canonical client ip for view identity
- `ad27892a` test(content): cover null client ip resolution
- `0d24e10f` fix(deploy): sanitize forwarded client headers
- `821d7726` fix(deploy): pin trusted proxy topology
- `67744dd1` fix(security): log trusted proxy startup config
- `6ac8cb8c` fix(deploy): reserve trusted proxy peers
- `a31cd829` fix(web): isolate trusted proxy configuration ownership
- `9f02222a` fix(web): validate trusted proxy cidr lists consistently
- `63b8d539` fix(web): reject equivalent universal proxy cidrs
- `57c5862c` fix(deploy): preserve trusted topology compatibility

### OSS 身份与授权

- `9bb321e5` feat(oss): define object access policy
- `cd0a7bcf` fix(oss): bind user operations to authenticated subject
- `532ec987` fix(oss): enforce object capability authorization
- `7aa91293` fix(oss): hide cross-object capability relationships
- `ff98b1a3` fix(oss): validate canonical object version relationships
- `56325898` feat(oss): isolate user and service jwt entrypoints
- `9fbac899` fix(oss): secure actuator and fallback routes
- `3b9c5480` feat(oss): authenticate internal client with service tokens
- `ccb2de1f` fix(oss): isolate internal capability cores
- `d26f6ee4` fix(oss): harden internal capability boundaries
- `d098e92d` fix(oss): stream authenticated proxy uploads
- `ce0e00cd` refactor(oss): wire scoped internal client globally

### Outbox 租约 Fencing

- `79d7cdb5` feat(migration): add outbox lease fencing columns
- `cfd8a5a5` fix(outbox): fence terminal updates with lease tokens
- `88766b19` fix(outbox): fence lease recovery by token
- `25c738ff` fix(outbox): stop stale workers after lease loss
- `56725811` fix(outbox): bound lease loss metric topics
- `698758e2` test(outbox): verify fencing in community and im

### 事务型幂等

- `1a065702` fix(frontend): scope idempotency keys to one invocation
- `57fe0325` fix(frontend): preserve case-insensitive idempotency headers
- `8cc111e0` feat(idempotency): add transactional jdbc store contract
- `addce3d1` fix(idempotency): guard expired success cleanup
- `0e6a19cf` fix(idempotency): recover success rows without expiry
- `1481e780` fix(idempotency): commit response with business transaction
- `cb9f6441` fix(idempotency): fail closed on replay codec errors
- `1118f9ea` feat(migration): quarantine legacy idempotency processing rows
- `2a9786b1` fix(test): preserve idempotency schema compatibility

### 业务一致性

- `f8d602bc` fix(market): avoid duplicate escrow inventory compensation
- `8ff22f25` test(market): prove escrow recovery lease replay
- `779c3f8a` feat(drive): conditionally win permanent deletion
- `28cdda80` fix(drive): release quota only for deletion winners
- `ac63fa63` fix(drive): retry earlier deleted child cleanup
- `d7b4bf7e` feat(wallet): model privileged correction postings
- `faacf115` fix(wallet): allow audited corrections to create debt
- `8b9e6960` feat(migration): enforce one action per moderation report
- `9a489996` test(migration): harden moderation uniqueness failure recovery
- `6580e4e5` fix(content): serialize moderation decisions by report claim
- `a7bd9e06` test(content): isolate moderation outbox scheduler
- `45278d48` test(content): cover moderation conflict wire contract

### 事件生命周期与服务端事实

- `e1061f11` feat(migration): identify each social like lifecycle
- `e03b174c` test(migration): avoid positional social like column rebuild
- `7f20b0b8` feat(social): persist like relation lifecycle identity
- `62ee8aeb` feat(events): publish like lifecycle identity compatibly
- `9d4bc163` fix(content): make post deletion dominate hot feed projection
- `2537bf9d` perf(content): bound projection expiry cleanup
- `c0ca8d20` fix(content): advance post version at terminal deletion
- `57e698ab` fix(comments): remove client supplied reply recipient
- `376c9830` fix(comments): derive reply thread from locked parent
- `8f655c19` fix(frontend): send direct parent for comment replies

### 前端会话与匿名市场

- `6ff0e40e` fix(auth): preserve rotated cookie on stale refresh failure
- `4166886f` feat(frontend): track access token generations
- `9cddc1ea` feat(frontend): coordinate refresh across clients
- `57925499` fix(frontend): share generation aware session refresh
- `e95f7cb0` fix(frontend): keep market details public without address data

## 最终验证

所有下列命令均在 `community-correctness-hardening` worktree 执行。

| 验证 | 结果 |
| --- | --- |
| 后端综合 reactor：`mvn -pl :community-common-idempotency,:community-common-outbox,:community-common-web,:community-gateway,:community-oss-client,:community-oss,:community-app,:im-core -am test` | 退出码 0；20 个 reactor module 全部 `SUCCESS` |
| migration reactor：`mvn -pl :community-db-migrations,:community-im-db-migrations,:community-oss-db-migrations -am test` | 退出码 0；IM `14/14`、Community `22/22`、OSS `9/9` |
| DDD 架构守卫：`mvn test -pl :community-app -Dtest='*ArchTest'` | 退出码 0；`135/135` |
| 前端全量：`npm test` | 退出码 0；84 个 test file，`402/402` |
| 前端 production build：`npm run build` | 退出码 0；Vite 转换 254 个模块并完成产物构建 |
| `git diff --check` | 退出码 0 |

综合 reactor 首次运行发现 `MODERATION_DECISION_CONFLICT` 未进入 error-code golden table，`ErrorCodeCompatibilityGoldenTest` 按预期失败。补齐 wire contract 后，聚焦测试 `75/75` 通过并提交为 `45278d48`，随后综合 reactor 全量重跑通过。

实施期间保留的聚焦证据：事件生命周期后端 `222/222`、事件前端 `9/9`、前端会话聚焦 `78/78`、`AuthControllerUnitTest` `17/17`、市场相关回归 `37/37`。

### Migration History

| 数据库 | 空库 migration history | 本次新增 |
| --- | ---: | --- |
| Community | 11 | V008 outbox fencing、V009 idempotency quarantine、V010 moderation unique action、V011 relation instance |
| IM | 2 | V002 outbox fencing |
| OSS | 3 | 无新增 migration，回归既有 V001-V003 |

真实 MySQL 回归覆盖空库、从 V001/旧 catalog 升级、重复 migrate、checksum/layout、V009 回填、V010 重复数据阻断及恢复、V011 relation instance 回填。

### 静态扫描

- `RequestContextHolder|proxy_add_x_forwarded_for` 在 OSS client production source 与 `deploy/nginx` 中无匹配。
- `replyToUserId|targetId` 在评论 inbound DTO、application command、published action API 和前端 post service 请求代码中无匹配。
- 生产前端中旧 `refreshingPromise`、普通 `http.post(.../api/auth/refresh)` 和旧 session clear 模式无匹配。
- 生产前端 `/api/auth/refresh` 唯一调用位于 `frontend/src/auth/refreshTransport.js`。

## 外部发布门禁

以下步骤依赖真实环境、生产数据、secret store、维护窗口或发布权限，本地执行记录不将其标为已完成：

1. 在 secret store/environment 配置 service JWT issuer、`aud=community-oss`、`scope=oss.internal` 和 HMAC secret；不得写入 Git、日志或前端 bundle。
2. 记录生产 NGINX 到 Gateway、Gateway 到 Community/OSS 的真实 IPv4/IPv6 CIDR；不得使用 `0.0.0.0/0`、`::/0` 或等价 universal CIDR。
3. 在生产只读预检 `moderation_action` 是否存在重复 `report_id`，并记录 `http_idempotency`、Community/IM `outbox_event` 的迁移前状态计数。
4. 在维护窗口阻断新写流量，停止旧 Community writer 与 Community/IM outbox worker，等待活动请求和 handler 完成；保留残留 `PROCESSING` 行，禁止手工删除。
5. 使用官方 migration module 按 Community V008、V009、V010、V011 和 IM V002 顺序迁移；迁移后、worker 恢复前验证 lease token/deadline 为零，V009 后验证历史 `P` 全部隔离为 `I`。
6. 先发布 NGINX/Gateway header 清洗，再启用应用 trusted CIDR；用真实拓扑验证 access log、rate-limit key 和 post fingerprint 的 client IP 一致。
7. 先配置签验材料并发布兼容 user/service 双入口的 OSS，再发布强制 service token 的 Community client；用 owner、grant、unrelated user 和 service token 验证权限矩阵。
8. 保持 social producer gate 关闭，先升级所有可读取 optional `relationInstanceId` 的钱包 consumer；全部确认后才开启 producer gate。
9. 在浏览器验收 HTTP/IM/bootstrap 并发 refresh 只有一个 refresh 与一个 profile 请求，验证 stale failure 不清新 session；验收匿名实物/虚拟详情、地址隔离和登录回跳。
10. 发布后观察 OSS deny/service JWT failure、idempotency indeterminate、outbox lease loss、market/drive/wallet/moderation、refresh/stale address 指标，并确认日志不含 JWT、Cookie、signed URL、地址或私有 payload。
11. 将实际上线时间窗、生产预检结果、migration history 和观察结果附到发布工单。兼容窗口后的 producer gate 清理属于单独后续计划，不属于本次 15 项实现完成条件。

## Git 状态约束

执行期间未 reset、未丢弃既有改动、未新建 worktree、未 push、未 merge。最终文档提交前，代码与测试提交后的 worktree 为 clean；前端 `dist` 为 ignored build output。
