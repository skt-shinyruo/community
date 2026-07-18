# Community Correctness Hardening Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已批准设计关闭 15 个正确性、安全性、并发和前端会话问题，并用只向前迁移、兼容发布顺序和可重复验证把改动安全交付。

**Architecture:** 所有 `backend/community-app` 改动遵守严格 DDD Tactical Layering；入站适配器只调用同域 `*ApplicationService`，同步跨域协作只走 owner-domain `api.query`/`api.action`，异步跨域协作只走 owner-domain `contracts.event`。本文件只编排依赖、迁移和发布，具体 RED/GREEN 步骤在七份子计划中执行。

**Tech Stack:** Java 21、Spring Boot、Spring Security、MyBatis、Flyway、MySQL 8、Redis/Lua、Kafka、Vue 3、Pinia、Axios、Vitest、Maven、Testcontainers、Docker Compose、NGINX。

---

## 交付物索引

| 子计划 | 覆盖问题 | 核心交付物 |
| --- | --- | --- |
| [OSS 身份与授权](./2026-07-18-community-correctness-hardening-oss-identity-and-authorization.md) | 1、2 | 用户身份不可伪造、对象级策略、service JWT、internal typed client |
| [可信客户端 IP](./2026-07-18-community-correctness-hardening-trusted-client-ip.md) | 8 | 纯 IP/CIDR 解析、右向左剥离、Gateway 规范化、NGINX 清洗 |
| [事务型幂等](./2026-07-18-community-correctness-hardening-transactional-idempotency.md) | 3、4 | 每次业务调用新 key、同事务 claim/业务/响应、`INDETERMINATE` 隔离 |
| [Outbox 租约 fencing](./2026-07-18-community-correctness-hardening-outbox-lease-fencing.md) | 7 | `OutboxLease` token CAS、Community `V008`、IM `V002` |
| [业务一致性](./2026-07-18-community-correctness-hardening-business-consistency.md) | 5、6、9、12 | 市场补偿、网盘配额、钱包特权冲正、审核 claim |
| [事件生命周期与服务端事实](./2026-07-18-community-correctness-hardening-event-lifecycle-and-server-facts.md) | 10、11、13 | 点赞 instance、帖子 tombstone、评论回复事实推导、Community `V011` |
| [前端会话与匿名市场](./2026-07-18-community-correctness-hardening-frontend-session-and-anonymous-market.md) | 14、15 | 单 refresh coordinator、token generation、公开 listing 与私有地址解耦 |

设计基线：[Community 正确性加固设计](../specs/2026-07-18-community-correctness-hardening-design.md)。实施中如果发现设计与代码事实冲突，先停止该子计划，在设计文档中记录并批准变更，不能静默改变完成判据。

## 问题到执行步骤映射

| 问题 | 子计划中的验收位置 | 必须成立的不变量 |
| --- | --- | --- |
| 1. OSS 对象级越权 | OSS 计划“用户入口身份”和“领域访问策略” | 用户 DTO 不含 owner/actor；私有读需 owner 或有效 grant；管理操作仅 owner |
| 2. 后台 OSS 无身份 | OSS 计划“service JWT”和“typed internal client” | 无 Servlet context 的后台调用仍携带受限服务身份 |
| 3. 前端错误复用幂等键 | 幂等计划“前端 request config 键” | 两次新调用使用不同 key；同一 config 重试保留 key |
| 4. 提交后幂等窗口 | 幂等计划“事务存储契约”和 `V009` | claim、业务、序列化和 success 同事务；历史 `P` 不重跑 |
| 5. 市场重复补库存 | 业务计划“托管补偿领域事实” | `ESCROW_FAILED -> CANCELLED` 不恢复库存 |
| 6. 网盘重复释放配额 | 业务计划“永久删除 winner 集合” | 并发调用只有 winner 的 `TRASHED -> DELETED` 字节释放配额 |
| 7. Outbox 无 fencing | Outbox 计划“lease token CAS” | 旧 token 不能 success/retry/dead 覆盖新 owner |
| 8. 客户端 IP 不可靠 | IP 计划“信任链规范化” | 不可信直连忽略 Header；可信链从右向左剥离 |
| 9. 钱包冲正失败 | 业务计划“`WalletPostingPolicy`” | NORMAL 不透支；特权纠正可冻结扣款、可负余额，分录仍平衡 |
| 10. 点赞奖励碰撞 | 事件计划“relation instance 全链路” | 每次 unlike/re-like 产生不同持久 UUIDv7 生命周期身份 |
| 11. 删除帖子复活 | 事件计划“永久 tombstone” | tombstone 支配所有低、同、高版本非删除 projection |
| 12. 并发审核 | 业务计划“原子 claim 与唯一 action” | 一个 report 只有一个 action、一组副作用和通知 |
| 13. 伪造回复接收人 | 事件计划“direct parent 锁内推导” | 客户端不提交接收人；root 和 target author 来自锁定评论 |
| 14. Refresh 竞态 | 前端计划“generation-aware coordinator” | HTTP/IM/bootstrap 共用任务；旧失败不清新状态或新 Cookie |
| 15. 匿名实物详情失败 | 前端计划“listing/address 双状态” | 匿名实物和虚拟 listing 均可读；地址失败不覆盖 listing |

## 依赖与提交边界

推荐开发依赖如下；没有依赖关系的分支可独立实现，但集成时必须按本图顺序：

```text
trusted proxy config -----> NGINX/Gateway rollout -----> application resolver enablement

OSS service JWT ----------> OSS endpoint split --------> community-oss-client callers

Community V008 + IM V002 -> fenced outbox workers
Community V009 -----------> transactional idempotency writer
Community V010 -----------> moderation unique-action code
Community V011 -----------> social relation-instance producer
                                ^
wallet optional consumer -------|

refresh coordinator -------> anonymous market UI ------> one frontend bundle
```

每个子计划的提交必须保持可编译。迁移、使用该迁移的新代码、运行时 H2 fixture 和迁移断言可以在同一子计划内分成连续提交，但发布时视为一个不可拆分兼容单元。禁止修改以下冻结文件来伪造新 schema：

- `backend/community-db-migrations/src/main/resources/db/migration/community/V001__baseline.sql`
- `backend/community-db-migrations/src/main/resources/db/migration/community/community-schema-manifest.tsv`
- `backend/community-im-db-migrations/src/main/resources/db/migration/im-core/V001__im_core_baseline.sql`
- `backend/community-im-db-migrations/src/main/resources/db/migration/im-core/im-core-schema-manifest.tsv`

## 预实施基线

### 工作区和构建基线

- [ ] 读取根目录 `AGENTS.md`、设计基线和准备执行的子计划，确认未出现新的局部 `AGENTS.md` 约束。
- [ ] 运行 `git status --short`，记录执行前已有改动；不能覆盖或回退非本计划改动。
- [ ] 运行后端基线：

  ```bash
  cd backend
  mvn -pl :community-common-idempotency,:community-common-outbox,:community-common-web,:community-gateway,:community-oss-client,:community-oss,:community-app,:im-core -am test
  ```

  预期：命令退出码为 `0`；如基线已失败，保存具体失败测试名，在本计划提交中只修复由本计划新增或暴露的失败。

- [ ] 在 Docker 可用时运行迁移基线：

  ```bash
  cd backend
  mvn -pl :community-db-migrations,:community-im-db-migrations,:community-oss-db-migrations -am test
  ```

  预期：Community 现有 7 个 migration、IM 现有 1 个 migration、OSS 现有 3 个 migration 均通过空库和升级验证。

- [ ] 运行前端基线：

  ```bash
  cd frontend
  npm test
  npm run build
  ```

  预期：Vitest 全绿，Vite production build 退出码为 `0`。

### 配置和数据预检

- [ ] 在部署环境准备 `service-JWT` issuer、`aud=community-oss`、`scope=oss.internal` 以及当前 Nimbus HS256 链路使用的 HMAC secret；secret 只通过 secret store/environment 注入 Community 与 OSS，不能进入 Git、日志或前端 bundle。
- [ ] 记录生产 NGINX 到 Gateway、Gateway 到 Community/OSS 的实际 IPv4/IPv6 CIDR；配置不得使用 `0.0.0.0/0` 或 `::/0`。
- [ ] 对审核数据执行只读预检：

  ```sql
  select report_id, count(*) as action_count
  from moderation_action
  where report_id is not null
  group by report_id
  having count(*) > 1;
  ```

  预期：返回 `0` 行。若非 `0` 行，停止 `V010` 发布，逐条审查历史 action；迁移不得自动删除或合并审计记录。

- [ ] 统计旧幂等 processing row 和 outbox processing row，作为维护窗口 drain 指标：

  ```sql
  select status, count(*) from http_idempotency group by status;
  select status, count(*) from outbox_event group by status;
  ```

  预期：查询成功并记录结果；此步骤只读，不修改状态。

## 实施批次

### 身份与边缘批次

- [ ] 完整执行 [可信客户端 IP 子计划](./2026-07-18-community-correctness-hardening-trusted-client-ip.md)，先部署 NGINX/Gateway Header 清洗，再启用应用 trusted CIDR。
- [ ] 完整执行 [OSS 身份与授权子计划](./2026-07-18-community-correctness-hardening-oss-identity-and-authorization.md)，先部署可接受用户/服务双身份的 OSS，再部署强制 service token 的后台 client。
- [ ] 验证 OSS 用户路径、服务路径和匿名 `/files/**` 矩阵，确认没有依赖浏览器 token 的后台 handler/job。

### 维护窗口与 schema 批次

- [ ] 阻断新写流量，停止所有旧 Community application instance；等待受保护 HTTP 请求结束，并确认旧幂等 writer 不再写入。
- [ ] 停止 Community 和 IM 的全部旧 outbox worker；等待当前 handler 完成，确认没有旧 worker 进程。
- [ ] 再次查询 `http_idempotency` 和两库 `outbox_event` 状态；保留残留 `PROCESSING` 行供迁移隔离/恢复，禁止手工删除。
- [ ] 按版本顺序执行 Community `V008`、`V009`、`V010`、`V011` 和 IM `V002`；Flyway 必须从官方 migration module 运行。
- [ ] 部署同时兼容新 schema 的 transactional idempotency writer 和 fenced outbox worker；确认旧 binary 已全部退出后再恢复流量和 worker。

### 业务、事件和前端批次

- [ ] 完整执行 [业务一致性子计划](./2026-07-18-community-correctness-hardening-business-consistency.md)。
- [ ] 先部署能读取 nullable/optional `relationInstanceId` 的钱包 consumer，并保持 social producer gate 关闭。
- [ ] 确认所有钱包 consumer instance 已升级后，完整执行并开启 [事件生命周期与服务端事实子计划](./2026-07-18-community-correctness-hardening-event-lifecycle-and-server-facts.md) 的 social producer。
- [ ] 将 [前端会话与匿名市场子计划](./2026-07-18-community-correctness-hardening-frontend-session-and-anonymous-market.md) 的 refresh 与 market 改动构建成同一个 bundle 发布。

## 最终验证

### 聚焦测试

- [ ] 按七份子计划逐一运行其聚焦测试命令，确认每个 RED 测试都曾以预期原因失败并在实现后通过。
- [ ] 运行严格 DDD 架构守卫：

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：所有 `*ArchTest` 通过；没有 Controller/Listener/Handler 到 foreign API、repository、mapper 或 dataobject 的新依赖。

### 后端和迁移回归

- [ ] 运行综合后端回归：

  ```bash
  cd backend
  mvn -pl :community-common-idempotency,:community-common-outbox,:community-common-web,:community-gateway,:community-oss-client,:community-oss,:community-app,:im-core -am test
  ```

  预期：`BUILD SUCCESS`，退出码为 `0`。

- [ ] 在 Docker 可用时运行真实 MySQL migration 回归：

  ```bash
  cd backend
  mvn -pl :community-db-migrations,:community-im-db-migrations,:community-oss-db-migrations -am test
  ```

  预期：Community 空库执行 11 个 migration，IM 空库执行 2 个 migration，OSS 仍执行 3 个 migration；V001 upgrade、重复 migrate、索引/列断言均通过。

### 前端回归

- [ ] 运行前端测试和 production build：

  ```bash
  cd frontend
  npm test
  npm run build
  ```

  预期：HTTP、IM、session、market、post service 测试通过；build 不包含已删除的 `idempotencyKeyCache` import。

### 静态与人工验收

- [ ] 运行禁止模式扫描：

  ```bash
  rg -n 'RequestContextHolder|proxy_add_x_forwarded_for|replyToUserId|targetId' \
    backend/community-oss-client/src/main \
    deploy/nginx \
    backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CreateCommentRequest.java \
    frontend/src/api/services/postService.js
  ```

  预期：目标范围内无匹配；展示响应模型中的服务端 `replyToUserId` 不在本扫描范围。

- [ ] 运行 `git diff --check`，预期无 trailing whitespace 或冲突标记。
- [ ] 对照本文件“问题到执行步骤映射”逐行勾选 15 项，任何一项缺少自动测试证据都不得宣告完成。

## 发布观察与回滚

- [ ] 发布后观察 OSS 授权拒绝、service JWT 校验失败、幂等 indeterminate、outbox lease loss、市场补偿、网盘 released bytes、审核 claim conflict、refresh join/stale retry 和 stale address response 指标。
- [ ] 确认日志不包含 JWT、Cookie、签名 URL、地址详情或原始私有 payload。
- [ ] 如 application 出现回归，停止受影响流量并前向部署仍兼容加法 schema 的修复版本；不得执行 migration down，也不得修改已执行的 `V008-V011`/`V002` 文件。
- [ ] 兼容窗口结束且所有旧 social event 已消费后，单独创建后续清理计划移除 producer gate；该清理不属于本次 15 项完成条件。

## 总控提交

- [ ] 每个子计划按其中列出的提交边界提交；最后只提交跨子计划的发布说明或配置清单，不把未通过测试的多域改动压成一个提交。
- [ ] 记录最终 commit 列表、测试命令退出码、migration history count 和上线时间窗，附到发布工单。
