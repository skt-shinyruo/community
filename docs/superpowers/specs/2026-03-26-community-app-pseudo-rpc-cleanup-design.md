# Community App 内部伪 RPC 清理设计稿

**Date:** 2026-03-26
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

在 `backend/community-app/` 内部清理“同 JVM 服务内调用被写成跨服务调用”的伪 RPC 模式，把本地协作重新收敛为普通 Spring service 调用。

本次设计的目标不是把 `community-app` 拆成更多 deployable，也不是修改现有跨服务协议，而是完成以下收敛：

- 明确 `community-app` 内部各领域包之间的同步协作属于服务内调用
- 删除本地调用中的 timeout/unavailable/degraded/remote_error 语义
- 删除把本地异常包装成“下游服务不可用”的适配代码
- 把 `InternalUserService` 这类大而全的隐性公共内核拆成窄能力服务
- 把重复的治理编排从 controller / service 中收口到共享应用服务

这是一次边界与语义清理，不是一次分布式改造项目。

---

## 2. Scope And Non-Goals

### 2.1 In Scope

仅处理 `community-app` 进程内的同步协作，重点覆盖：

- `auth -> user`
- `message -> user`
- `content -> user`
- `user -> social`
- `im controller -> message/user/social` 的本地编排

当前明确纳入的典型类包括：

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/InternalUserService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/UserLookupService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserSocialProfileService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`

### 2.2 Out Of Scope

以下内容明确不在本轮范围内：

- `community-gateway -> community-app` 的 HTTP 边界
- `im-realtime -> community-app` 的 HTTP 调用
- `im-realtime -> im-core` 的 HTTP 调用
- `community-gateway -> im-realtime` 的 WS bridge
- Kafka command / event 协议
- 任何外部 API 路径、HTTP JSON shape、Kafka topic 名称变更

换句话说：真正的跨服务适配层全部保持不动。

### 2.3 Non-Goals

- 不把 `community-app` 内部协作重新包装成新的“内部 client”或“内部网关”
- 不引入新的通用降级框架来替代旧的伪 RPC 包装
- 不为了“零重复”而把所有治理、查询、聚合都抽成过度泛化的基础设施
- 不在本轮推进 `community-app` 之外的代码迁移

---

## 3. Confirmed Decisions

在本次设计讨论中已经确认的决策如下：

- 只处理 `community-app` 内部这些同 JVM 调用
- 真正跨服务的 HTTP / WS / Kafka 链路保持不动
- 接受对误导性类名和包装层做重命名或拆分，而不是只删几行指标
- 推荐采用“能力端口 + 共享应用服务”的改法，而不是只做语义打补丁
- 私信发送治理需要从 `PrivateMessageService` 与 `ImGovernanceController` 中抽成共享应用服务

明确不做的事情：

- 不把 `community-app` 重新拆成独立 user 服务 / social 服务 / message 服务
- 不给进程内调用保留伪下游 metrics 或伪远程错误码
- 不继续容忍 `Internal*` 大类作为外部领域的默认入口

---

## 4. Problem Statement

当前 `community-app` 的主要问题不是“存在跨领域调用”，而是“跨领域调用的边界表达错误”。

### 4.1 Same-Process Calls Are Modeled Like Remote Calls

典型表现：

- `UserLookupService` 本质上只是在同进程内调用 `UserService` 与 `InternalUserService`
- `UserSocialProfileService` 本质上只是在同进程内调用 `LikeService` 与 `FollowService`
- 这些类却引入了网络异常分类、timeout/unavailable/degraded 结果码、`SERVICE_UNAVAILABLE` 包装和 `internal_call_*` 指标

这会让调用方产生错误心智：

- 好像本地领域协作是“调用下游服务”
- 好像本地编程错误也应该按远程故障处理
- 好像本地服务可以天然地 fail-open / fail-closed

### 4.2 User Domain Exposes A Catch-All Internal Kernel

`InternalUserService` 当前同时承担：

- 认证
- 密码更新
- 权限映射
- 注册 / 待激活用户 / 激活 / 清理
- 按 id / 用户名 / 邮箱查询
- 批量用户摘要
- 处罚状态查询 / 扫描 / 应用

这导致其他领域并不是依赖“少量清晰能力”，而是在依赖一个隐性的公共内核。

### 4.3 Governance Rules Are Duplicated Across Adapter And Domain Service

私信发送治理当前同时存在于：

- `ImGovernanceController`
- `PrivateMessageService.send(...)`

两处都在做“用户存在校验 + 禁言封禁校验 + 拉黑校验”的组合，只是入口不同。

这意味着治理规则没有被定义成一套共享应用策略，而是散落在 transport adapter 和业务 service 中。

---

## 5. Target Design

### 5.1 Boundary Model

本轮改造后，`community-app` 内部只保留三种角色：

1. 领域能力服务
   - 负责提供窄而清晰的业务能力
   - 例如用户查询、处罚状态查询、注册、认证、社交统计查询

2. 共享应用编排服务
   - 负责跨领域的业务规则组合
   - 例如“私信发送前治理校验”

3. HTTP controller
   - 只负责处理 transport 输入输出
   - 不再自己拼装多领域规则

禁止继续存在的角色：

- 仅为了模仿内部 RPC 而存在的 wrapper service
- 把本地 Bean 调用包装成“内部下游调用”的通用框架
- 通过 `Internal*` catch-all 服务暴露整个领域

### 5.2 User Domain Capability Split

`InternalUserService` 将被拆为以下窄服务：

- `UserCredentialService`
  - 认证
  - 密码更新
  - authority 映射

- `UserRegistrationService`
  - 注册
  - 查询待激活用户
  - 激活用户
  - 清理过期待激活用户

- `UserQueryService`
  - 按 id 查询
  - 按用户名查询
  - 按邮箱查询
  - 批量用户摘要查询

- `UserModerationService`
  - 查询处罚状态
  - 扫描处罚状态
  - 应用处罚

拆分原则：

- `auth` 只依赖认证 / 注册 / 用户查询相关能力
- `message` / `content` 只依赖用户查询与处罚能力
- job / event listener 只依赖自己真正需要的能力

### 5.3 Message User Read Model Service

`message.service.UserLookupService` 会被改造成一个普通的 message 域本地读模型服务，负责：

- 将 user 域查询结果转换为 message 域需要的 `UserSummary`
- 提供用户名解析、按 id 查询、批量摘要查询
- 维护局部 TTL 缓存以降低重复读放大

它不再承担：

- 内部调用框架
- 伪远程错误分类
- 降级开关
- 伪下游 metrics

推荐命名：

- `MessageUserQueryService`

命名目标是表达“message 域用它查询用户”，而不是表达“message 在调用内部 user RPC”。

### 5.4 Social Aggregate Service In User Domain

`UserSocialProfileService` 保留“在 user 域聚合 social 统计”的职责，但改成普通本地聚合服务。

允许保留：

- 聚合返回对象
- 面向用户主页场景的只读聚合方法

不再允许：

- 用通用 `call(...)` 包装器包裹本地服务调用
- 用 timeout/unavailable/degraded 来描述本地调用结果
- 把本地异常改写成 `SERVICE_UNAVAILABLE`

如果未来确实要对用户主页做“局部字段降级”，也应写在用户主页聚合场景内，而不是沉到一个伪 RPC 基础设施层。

### 5.5 Shared Private Message Governance Service

新增共享应用服务：

- `PrivateMessageGovernanceService`

它负责收口以下规则：

- 发送方必须存在
- 接收方必须存在
- 不能给自己发送私信
- 发送方不能处于禁言 / 封禁状态
- 双方不存在拉黑关系

契约约定：

- 校验通过时返回 `void`
- 规则不满足时抛出 `BusinessException`
- 不返回“allowed / denied”结果对象，也不引入新的验证结果封装

调用方改成：

- `PrivateMessageService.send(...)` -> `PrivateMessageGovernanceService`
- `ImGovernanceController` -> `PrivateMessageGovernanceService`

这样 controller 不再是治理规则 owner，只是对 `im-realtime` 暴露的 HTTP adapter。

---

## 6. Semantic Rules After Refactor

### 6.1 In-Process Service Collaboration Rules

`community-app` 内部服务调用一律遵循以下规则：

- 通过 Spring Bean 直接注入
- 返回普通对象、集合、基础类型或 `void`
- 业务可预期失败通过 `BusinessException` 表达
- 非业务异常直接冒泡，由统一异常处理转为 500

明确禁止：

- 在本地 service 中主动识别网络异常类型
- 在本地 service 中把异常包装为“下游服务不可用”
- 在本地 service 中定义 remote outcome 枚举

### 6.2 Result Semantics To Remove

以下伪 RPC 语义必须从 `community-app` 内部调用层删除：

- `OUTCOME_TIMEOUT`
- `OUTCOME_UNAVAILABLE`
- `OUTCOME_REMOTE_ERROR`
- `OUTCOME_DEGRADED`
- `wrapUnexpectedException(...)` 中对 `SERVICE_UNAVAILABLE` 的本地包装
- `internal_call_requests_total`
- `internal_call_latency`

### 6.3 Query Semantics That May Remain

以下语义是正常的本地业务查询语义，可以保留，但要改成直接、清晰的本地命名：

- 查不到用户返回 `null`
- 查不到用户名返回 `null`
- 批量摘要查不到部分 id 时跳过缺失项
- 使用局部缓存避免重复查同一用户名

推荐命名方式：

- `findUserIdByUsernameOrNull`
- `findUserSummaryByIdOrNull`
- `getUserSummariesByIds`

这里的 `OrNull` 表达的是业务查询结果，不是降级。

---

## 7. Configuration And Metrics Changes

### 7.1 Config To Remove Or Deprecate

以下配置仅服务于伪 RPC 语义，本轮应删除或废弃：

- `message.user-lookup.fail-open`
- `user.social-profile.degrade-on-error`

如果短期为了兼容配置绑定需要保留字段，也应：

- 标记为 deprecated
- 让其不再影响本地服务行为
- 在后续清理中彻底删除

### 7.2 Metrics To Keep

真正跨服务的适配层 metrics 保持不动，例如：

- `im-realtime -> community-app`
- `im-realtime -> im-core`
- `community-gateway -> upstream`

### 7.3 Metrics To Replace

若本轮仍希望保留可观测性，应把指标上提到真实业务场景，而不是伪下游调用：

- 私信发送治理校验次数 / 耗时
- 批量用户摘要查询耗时
- 用户主页社交聚合耗时

不再统计：

- `message -> user` 成功 / 超时 / 不可用
- `user -> social` 成功 / 超时 / 不可用

---

## 8. Migration Strategy

### 8.1 Step 1: Introduce Shared Governance Service

先新增 `PrivateMessageGovernanceService`，把重复治理规则收口。

第一阶段完成后：

- `PrivateMessageService.send(...)` 不再自己拼用户存在 / 禁言 / 拉黑规则
- `ImGovernanceController` 不再自己拼用户存在 / 禁言 / 拉黑规则

这一步优先做，因为它先消除规则重复，再给后续 user service 拆分提供稳定调用面。

### 8.2 Step 2: Split `InternalUserService`

按能力引入新窄服务，并逐步迁移调用方：

- auth 侧迁移到 `UserCredentialService` / `UserRegistrationService` / `UserQueryService`
- message / content 侧迁移到 `UserQueryService` / `UserModerationService`
- job / listener 迁移到相应窄服务

迁移期间允许新旧类短暂并存，但旧类仅作为过渡，最终必须删除。

### 8.3 Step 3: Remove Pseudo-RPC Wrappers

清理：

- `UserLookupService` 的伪 RPC 包装
- `UserSocialProfileService` 的伪 RPC 包装
- 所有本地 `internal-call` metrics / outcome / timeout 分类

保留真正需要的本地缓存与 DTO 转换。

### 8.4 Step 4: Rename And Clean Up

在行为稳定后完成收尾：

- 删除误导性类名
- 删除废弃配置
- 更新架构和业务文档
- 删除无用测试、无用 imports 和无用注释

---

## 9. Compatibility Rules

本轮改造必须满足以下兼容约束：

- 外部 HTTP API 不变
- `im-realtime` 依赖的治理 HTTP 接口不变
- 真正跨服务链路不变
- 业务错误码尽量保持不变

允许变化的部分：

- 先前被错误包装成 `SERVICE_UNAVAILABLE` 的本地异常，将回归为原始业务异常或统一 500
- `community-app` 内部类名、注入关系、service 划分会调整
- 面向伪 RPC 的配置与 metrics 会删除

---

## 10. Testing Strategy

### 10.1 Governance Tests

为 `PrivateMessageGovernanceService` 新增测试，覆盖：

- fromUserId 非法
- toUserId 非法
- 自己给自己发私信
- 发送方不存在
- 接收方不存在
- 发送方被禁言
- 发送方被封禁
- 双方存在拉黑关系
- 正常允许发送

同时验证：

- `PrivateMessageService.send(...)` 与 `ImGovernanceController` 使用同一套治理规则

### 10.2 User Capability Service Tests

为拆分后的 user 窄服务补齐或迁移测试，覆盖：

- 认证成功 / 失败
- authority 映射
- 注册 / 激活 / 过期待激活用户
- 通过 id / 用户名 / 邮箱查询用户
- 批量用户摘要查询
- 处罚状态查询 / 扫描 / 应用

### 10.3 Local Query Service Tests

为 message 域本地用户查询服务覆盖：

- 用户名解析成功 / 失败
- 按 id 查询成功 / 查无此人
- 批量摘要转换
- TTL 缓存命中与过期

### 10.4 Social Aggregate Tests

为 user 域社交聚合服务覆盖：

- 正常聚合 like / follow 数据
- 去掉伪 RPC 包装后，业务返回结构不变
- 不再断言 timeout/unavailable/degraded 指标或结果码

### 10.5 Regression Focus

重点回归以下行为：

- 登录 / 刷新 / 注册 / 注册验证码 / 密码重置
- 私信发送与私信治理校验
- 用户主页社交统计展示
- 所有依赖 `InternalUserService` 的 job / listener

---

## 11. Risks And Mitigations

### 11.1 Risk: Hidden Callers Of `InternalUserService`

风险：

- `InternalUserService` 当前像隐性公共内核，调用点可能比已知范围更多

缓解：

- 在计划阶段枚举全部调用点
- 迁移完成前不删除旧类
- 删除旧类前确保全仓库无引用

### 11.2 Risk: Behavior Drift During Service Split

风险：

- 把一个大类拆成多个窄服务时，容易引入校验差异或事务边界差异

缓解：

- 迁移时优先复制现有行为
- 先补测试再删除旧实现
- 事务边界只在必要处调整，不做顺手优化

### 11.3 Risk: Over-Abstracting Governance

风险：

- 共享治理服务如果设计成通用“治理引擎”，会再次走向过度抽象

缓解：

- 明确只服务“私信发送前校验”
- 不引入配置驱动规则引擎
- 只组合现有显式业务规则

---

## 12. End State

改造完成后，`community-app` 内部边界应满足以下判断标准：

- 同进程跨领域调用能直接看成“本地业务协作”，而不是“调用内部下游服务”
- 不再有 service 因为本地 Bean 调用而声明 timeout/unavailable/degraded 语义
- `user` 域暴露的是少量清晰能力服务，而不是一个大而全的 `Internal*` 内核
- 私信发送治理规则只存在一处 owner
- 真正的远程失败语义只保留在真实跨服务适配层

这才是 `community-app` 作为包级单体应有的边界表达。
