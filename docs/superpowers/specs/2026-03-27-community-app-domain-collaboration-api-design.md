# Community App 领域协作 API 收敛设计稿

**Date:** 2026-03-27
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

在 `backend/community-app/` 内部收敛“领域之间如何同步协作”的边界模型，解决当前跨域调用面在窄 service、原始 mapper、foreign entity、巨型 facade 之间摇摆的问题。

本次设计的目标不是做一次表面命名调整，而是把 `community-app` 内部同步协作统一成一套稳定、可验证、可持续执行的规则：

- 每个业务域只对外暴露少量显式协作接口
- 跨域依赖只允许指向 owner 域的 `api.query`、`api.action`、`api.model`
- foreign `entity`、foreign `mapper`、foreign `service` 不再作为默认跨域入口
- controller 不再直接依赖或拼装 foreign domain entity
- 现有巨型 facade 被拆回查询协作、动作协作与装配职责
- 用架构测试和评审规则把新边界长期钉住

本轮接受破坏式重构。功能正确性与边界清晰度优先于保留当前类名、包名或 Bean 注入关系。

---

## 2. Scope And Non-Goals

### 2.1 In Scope

本次设计覆盖 `backend/community-app/` 内全部领域包的**进程内同步协作边界**，重点包括：

- `auth -> user`
- `content -> user`
- `growth -> user`
- `message -> user`
- `social -> content`
- `user -> content`
- `user -> social`
- `user.event -> growth`

当前已明确纳入的代表性问题包括：

- `growth.controller.GrowthController` 直接依赖 `user.entity.User`
- `growth.service.AdminGrowthService` 直接依赖 `user.mapper.UserMapper`
- `content.service.PostFacadeService` 同时承担查询编排、动作前处理、DTO 装配、幂等包装、文本处理
- `message.service.MessageUserQueryService` 在 message 域内部再次定义 user 查询边界语义

### 2.2 Out Of Scope

以下边界不在本轮处理范围内：

- `community-gateway -> community-app` 的 HTTP 边界
- `community-app -> community-im/*` 的真实跨进程 HTTP / WS / Kafka 协议
- 前端 API 路径、页面行为、网关路由规则
- MySQL schema 拆分或 deployable 拆分

### 2.3 Non-Goals

- 不把 `community-app` 内部同步协作重新包装成新的伪 RPC 或内部 client
- 不引入新的“全局领域总线”或统一 `CommandBus`
- 不为了追求抽象完整性，把所有领域都做成重量级六边形架构
- 不把所有 controller DTO 与跨域协作模型混放或复用
- 不保留当前“跨域可直接调 service，偶尔也能直连 mapper”的宽松约定

---

## 3. Confirmed Decisions

本次设计讨论中已确认的决策如下：

- 范围为 `backend/community-app` 全量治理，而不是只修 `growth` / `content` 个别点
- 接受破坏式重构，不要求保留现有类名、包结构和 Bean 依赖关系
- 每个域新增 `api` 包，作为领域对外协作面
- `api` 的含义固定为“领域协作 API”，不表示 HTTP；HTTP 入口仍然是 `controller`
- 协作边界统一采用：
  - `api.query`
  - `api.action`
  - `api.model`
- 只读能力统一命名为 `*QueryApi`
- 有副作用的业务动作统一命名为 `*ActionApi`
- 跨域协作模型统一使用 `*View`、`*Ref`、`*Result`
- 不再使用 `*CommandApi` 命名
- 不再允许继续扩展 `*FacadeService`

---

## 4. Current Problems

### 4.1 Cross-Domain Collaboration Surface Is Not Stable

当前 `community-app` 的文档约束是“跨域协作默认通过聚焦 service 或 domain-owned dto 完成”，但这条规则没有收敛成足够强的编译期与命名约束。

结果是不同领域在实际演化中各自选择了不同路径：

- 有的调用方通过窄 service 读取对方数据
- 有的调用方直接注入 foreign mapper
- 有的 controller 直接拿 foreign entity 拼装 HTTP 响应
- 有的复杂模块通过一个巨型 facade 统一暴露所有读写行为

这使得“什么是稳定调用面”本身没有统一答案。

### 4.2 Foreign Entity Leaks Across Domain Boundaries

当前已存在跨域直接依赖 foreign entity 的情况，例如：

- `growth` controller 直接依赖 `user.entity.User`
- `auth` service 直接依赖 `user.entity.User`

这意味着：

- owner 域的实体结构会泄漏到消费者域
- owner 域的字段调整会扩大为跨域改动
- controller 很容易绕过 owner 域自己的协作边界，自己拼装领域语义

### 4.3 Foreign Mapper Bypasses Domain Ownership

当前已存在跨域直接注入 foreign mapper 的情况，例如：

- `growth.service.AdminGrowthService -> user.mapper.UserMapper`

这类依赖会让消费者域直接穿透到 owner 域的持久化层，绕过 owner 域自己的校验、命名与错误语义，最终把“数据 owner”退化成“表 owner”。

### 4.4 Giant Facade Re-Centralizes Responsibilities

当前 `content.service.PostFacadeService` 已经表现出典型的大型 facade 症状：

- 构造器注入协作者过多
- 同时提供读写方法
- 同时承担 DTO 组装、文本清洗、敏感词过滤、幂等包装、查询聚合
- 被多个 controller 和别的领域共同依赖

这意味着原本应由多个清晰边界承载的职责，重新集中进一个“大而全的门面”，只是把问题从“边界缺失”转移成“上帝类膨胀”。

---

## 5. Target Design

### 5.1 Boundary Model

本轮改造后，每个业务域内部只保留以下角色：

1. `controller`
   - 负责 HTTP transport 输入输出
   - 负责认证上下文读取、参数绑定、返回对外 DTO
   - 不直接依赖 foreign `entity`、foreign `service`、foreign `mapper`

2. `api.query`
   - 领域对外只读协作面
   - 供其他领域同步查询使用
   - 返回 `api.model` 中的协作模型

3. `api.action`
   - 领域对外有副作用的业务动作协作面
   - 封装跨域应感知的动作语义与事务边界
   - 返回 `api.model` 中的动作结果模型、基础类型或 `void`

4. `api.model`
   - 跨域协作模型
   - 只表达消费者需要的最小字段集
   - 不承载 HTTP 语义、持久化语义或框架语义

5. `service`
   - 域内实现层
   - 可以依赖本域 `entity`、`mapper`、事件、缓存、assembler
   - 不再默认作为外域协作入口

6. `entity` / `mapper`
   - 严格 owner 域私有
   - 不允许被外域正常依赖

### 5.2 Package Shape

以 `user` 域为例，目标结构如下：

```text
com.nowcoder.community.user
  controller
  api
    query
    action
    model
  service
  dto
  entity
  mapper
  event
  exception
  security
```

`content`、`growth`、`message`、`social` 等域按同一模式组织。

### 5.3 Cross-Domain Dependency Rules

跨域同步调用的允许面收敛为：

- `com.nowcoder.community.<domain>.api.query..`
- `com.nowcoder.community.<domain>.api.action..`
- `com.nowcoder.community.<domain>.api.model..`

明确禁止以下默认跨域依赖路径：

- `com.nowcoder.community.<domain>.mapper..`
- `com.nowcoder.community.<domain>.entity..`
- `com.nowcoder.community.<domain>.service..`

例外策略：

- 同域内部可自由使用本域 `service` / `entity` / `mapper`
- 极少数迁移期白名单必须集中声明在架构测试中，并附带到期说明
- 白名单只作为过渡，不得成为长期制度

### 5.4 Naming Rules

命名规则统一如下：

- 只读协作接口：`*QueryApi`
- 有副作用协作接口：`*ActionApi`
- 只读协作模型：`*View`
- 引用型协作模型：`*Ref`
- 动作返回模型：`*Result`

明确禁止：

- `*FacadeService`
- `*Manager`
- `*UtilService`
- `*WriteApi`
- `*CommandApi`
- 泛化单总口，例如 `UserApi`、`ContentApi`

### 5.5 Internal Semantics

`QueryApi` 与 `ActionApi` 语义必须分离：

- `QueryApi`
  - 只负责读取
  - 不产生业务副作用
  - 不做幂等包装
  - 不负责写路径校验副作用

- `ActionApi`
  - 表达业务动作
  - 负责 owner 域内必要的事务边界与业务校验
  - 不泄漏底层 `mapper.update(...)` 这类持久化语义

错误与异常规则：

- owner 域继续定义自己的业务错误码
- 跨域调用方只依赖稳定业务语义，不依赖内部实现细节
- 不把本地协作重新包装成“下游服务不可用”

---

## 6. `api.model` Design Rules

`api.model` 的职责必须非常克制。

### 6.1 Rules

- 只放跨域协作模型
- 不放 HTTP request / response DTO
- 不放 JPA / MyBatis entity
- 不放 `Result<T>`、分页插件类型、Spring Web 类型
- 按消费者实际需要定义最小字段集，不做万能超集
- 优先使用不可变模型，例如 `record` 或只读属性类

### 6.2 Examples

- `user.api.model.UserSummaryView`
- `user.api.model.UserCredentialView`
- `user.api.model.UserGrowthProfileView`
- `user.api.model.UserModerationStateView`
- `content.api.model.PostSummaryView`
- `content.api.model.PostDetailView`
- `content.api.model.ResolvedContentRef`
- `growth.api.model.GrowthSummaryView`

### 6.3 Explicit Separation From HTTP DTO

以下两类模型必须分开：

- controller 对外返回的 HTTP DTO：继续放在各域 `dto`
- 跨域同步协作模型：只放在各域 `api.model`

不得为了减少类数量，把 controller DTO 与跨域协作模型混成同一对象。

---

## 7. Domain API Inventory

### 7.1 User Domain

`user` 是当前最高扇出的 owner 域，需要最先收敛。

建议对外暴露：

- `user.api.query.UserLookupQueryApi`
  - 按 `id / username / email` 查询基础身份摘要
- `user.api.query.UserProfileQueryApi`
  - 查询用户资料和用户主页所需视图
- `user.api.query.UserCredentialQueryApi`
  - 查询登录、刷新、密码重置所需认证材料
- `user.api.query.UserModerationQueryApi`
  - 查询禁言、封禁等治理状态
- `user.api.action.UserPointsActionApi`
  - 跨域触发积分动作

典型消费者：

- `growth`
- `auth`
- `message`
- `content`

### 7.2 Content Domain

`content` 既是依赖源，又包含明显结构债，需要第二阶段收敛。

建议对外暴露：

- `content.api.query.PostReadQueryApi`
  - 帖子详情、批量帖子摘要、按用户查最近帖子
- `content.api.query.CommentReadQueryApi`
  - 评论列表、回复列表、按用户查最近评论
- `content.api.query.ContentEntityQueryApi`
  - 解析 `entityType + entityId` 到稳定引用
- `content.api.query.BookmarkQueryApi`
  - 收藏状态、收藏列表等只读能力
- `content.api.query.SubscriptionQueryApi`
  - 订阅分类列表等只读能力
- `content.api.action.PostPublishingActionApi`
  - 发帖、改帖、删帖
- `content.api.action.PostModerationActionApi`
  - 置顶、加精、后台删除等治理动作
- `content.api.action.CommentActionApi`
  - 发评论、改评论

其中：

- `PostFacadeService` 不是目标形态，最终必须删除
- 读写路径应分别落到 query/action API
- DTO 组装逻辑应进入专门 assembler，而不是继续堆进门面类

### 7.3 Growth Domain

建议对外暴露：

- `growth.api.query.GrowthProfileQueryApi`
  - 查询用户成长值、余额、冻结额、等级快照
- `growth.api.query.RewardCatalogQueryApi`
  - 查询奖励目录
- `growth.api.query.TaskCenterQueryApi`
  - 查询任务中心快照
- `growth.api.action.RewardRedemptionActionApi`
  - 兑换动作
- `growth.api.action.GrowthGrantActionApi`
  - 外域触发的增长发放动作

### 7.4 Message Domain

建议对外暴露：

- `message.api.query.ConversationQueryApi`
  - 会话列表、会话统计、消息分页读取
- `message.api.query.NoticeQueryApi`
  - 通知读取与未读计数
- `message.api.action.MessageActionApi`
  - 发私信、标记已读等动作

说明：

- `MessageUserQueryService` 不是 user 域协作边界
- 它要么退回 message 域内部缓存型适配器，要么在迁移后进一步收缩

### 7.5 Social Domain

建议对外暴露：

- `social.api.query.FollowRelationQueryApi`
  - 是否关注、粉丝/关注计数
- `social.api.query.LikeStateQueryApi`
  - 点赞状态和点赞计数
- `social.api.query.BlockRelationQueryApi`
  - 拉黑关系读取
- `social.api.action.SocialInteractionActionApi`
  - 关注、取关、点赞、取消点赞、拉黑、取消拉黑

### 7.6 Search / Analytics / Ops

这些域不应重新成为同步依赖中心。

原则如下：

- `search`
  - 只保留极窄的后台或运维动作 API
  - 普通业务协作优先继续通过事件驱动
- `analytics`
  - 若必须对外读取，仅允许极窄只读 query API
- `ops`
  - 不作为业务域同步依赖源

---

## 8. Migration Strategy

迁移必须分阶段进行，保证仓库始终可构建、可测试、可回滚。

### 8.1 Phase 0: Establish Guardrails First

先新增架构测试与命名护栏，但初始允许少量迁移期白名单。

第一批规则：

- 非本域不得依赖 foreign `mapper`
- 非本域不得依赖 foreign `entity`
- controller 不得直接依赖 foreign `entity` / `service` / `mapper`
- 禁止新增 `*FacadeService`

目标：

- 在不一次性打爆全仓库的前提下，阻止问题继续扩散

### 8.2 Phase 1: Introduce `user.api`

先引入：

- `user.api.query.*`
- `user.api.action.*`
- `user.api.model.*`

并让 `user` 域内部用现有实现承接这些新接口。

第一批迁移调用方：

- `growth`
- `auth`
- `message`
- `content` 中依赖 user 治理状态的部分

这一阶段完成后，代表性目标包括：

- `GrowthController` 不再依赖 `user.entity.User`
- `AdminGrowthService` 不再依赖 `UserMapper`
- `AuthService` 不再依赖 `user.entity.User`
- `MessageUserQueryService` 改为依赖 `UserLookupQueryApi`

### 8.3 Phase 2: Introduce `content.api`

在 `user` 边界稳定后，引入：

- `content.api.query.*`
- `content.api.action.*`
- `content.api.model.*`

处理重点：

- 先把 `PostFacadeService` 的读能力拆到 `query`
- 再把写能力拆到 `action`
- 把 DTO 装配逻辑提成 assembler

迁移期允许 `PostFacadeService` 作为过渡外壳，内部转调新 API，但它最终必须删除。

### 8.4 Phase 3: Migrate Consumer Domains To Official APIs

在 `user.api` 与 `content.api` 都稳定后，统一清理消费者域中的自建 adapter 和灰区依赖。

重点包括：

- `social.service.ContentEntityResolver -> content.api.query.ContentEntityQueryApi`
- `message.service.MessageUserQueryService -> user.api.query.UserLookupQueryApi`
- `growth` 内全部对 `user/content` 的跨域依赖切换到 `api`
- `auth` 内全部对 `user` 的跨域读取切换到 `api`

### 8.5 Phase 4: Remove Transitional Service Entrypoints

当所有外域都不再依赖旧入口后，再回头清理 owner 域内部结构。

主要工作：

- 收缩或删除只承担对外转发的壳 service
- 删除只为兼容旧跨域依赖保留的过渡方法
- 删除 `PostFacadeService`
- 清理误导性命名

### 8.6 Phase 5: Tighten Guardrails To Full Enforcement

当主要迁移完成后，把 Phase 0 的白名单式护栏切换为全量强约束：

- 外域只允许依赖 `api.query`、`api.action`、`api.model`
- `service`、`entity`、`mapper` 只允许域内访问
- controller 不得依赖 foreign domain 的非 `api.model` 类型
- 不允许新增 facade 风格类名

---

## 9. Validation And Governance

### 9.1 ArchUnit Rules

建议新增 `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`，至少覆盖以下规则：

1. 硬禁止规则
   - 非本域类不得依赖 foreign `mapper`
   - 非本域类不得依赖 foreign `entity`
   - 非本域类不得依赖 foreign `service`

2. 允许面规则
   - 跨域依赖只允许指向：
     - `api.query`
     - `api.action`
     - `api.model`

3. Controller 规则
   - controller 不得依赖 foreign `entity`
   - controller 不得依赖 foreign `mapper`
   - controller 不得把 foreign entity 作为输入输出模型

4. 命名规则
   - `api.query` 下类名必须以 `QueryApi` 结尾
   - `api.action` 下类名必须以 `ActionApi` 结尾
   - 禁止新增 `*FacadeService`

### 9.2 Facade Risk Heuristics

为避免新边界再次退化成大门面类，以下启发式应作为评审红线：

一个类若满足任意 3 条，即视为高风险 facade：

- 构造器依赖数超过 6 个
- 同时包含读写方法
- 同时处理 DTO 装配、文本处理、鉴权上下文、业务编排
- 被多个 controller 和多个外域共同依赖
- 方法名覆盖 `list/detail/create/update/delete/top/wonderful/...` 等多种职责

### 9.3 Documentation And Review Checklist

后续落地时需要同步更新：

- `docs/ARCHITECTURE.md`
- 相关业务逻辑文档
- 代码评审 checklist

其中 `docs/ARCHITECTURE.md` 当前“跨域协作默认通过聚焦 service 或 domain-owned dto 完成”的表述，需要收敛为更强的正式规范：

- 进程内跨域同步协作统一通过 owner 域 `api.query` / `api.action` / `api.model`

---

## 10. Acceptance Criteria

本轮治理完成后，至少需要满足以下验收条件：

- `growth/auth/message/social/user/content` 之间不再出现 foreign `mapper` 依赖
- 外域不再 import foreign `entity`
- controller 不再直接拼装 foreign entity
- `PostFacadeService` 被删除
- `UserService` 不再承担对外跨域入口角色
- ArchUnit 边界测试默认绿色
- 迁移期白名单被收敛到零或接近零，且没有长期开放项

---

## 11. Risks And Mitigations

### 11.1 Risk: API Proliferation Becomes New Indirection

风险：

- 如果按现有 service 机械平移，`api` 包会只是旧问题换皮

缓解：

- 严格按跨域用例切接口，而不是按原类名平移
- 禁止 `UserApi`、`ContentApi` 这类单总口
- `api.model` 按最小字段集设计

### 11.2 Risk: Transitional Dual Boundaries Last Too Long

风险：

- 迁移期若长期同时保留 `service + api + mapper` 三套对外入口，会让边界再次模糊

缓解：

- 迁移按 owner 域分阶段推进
- 每一阶段完成后立即迁消费者
- 迁移完成后删除过渡入口，而不是长期保留

### 11.3 Risk: Over-Splitting Action APIs

风险：

- 如果动作边界过细，会造成 API 数量激增、命名碎裂

缓解：

- 按稳定业务动作簇划分 action API，而不是一方法一接口
- 以“消费者如何理解该能力”为标准，而不是以 mapper 操作为标准

### 11.4 Risk: Hidden Callers In Existing Codebase

风险：

- 历史调用点可能比当前已知范围更多，尤其是 listener、job、admin controller

缓解：

- 计划阶段先枚举所有 foreign `entity/service/mapper` 引用点
- 删除旧入口前确保全仓库无引用
- 用架构测试防止迁移过程中回流

---

## 12. Recommended Conclusion

`community-app` 内部同步协作应从“聚焦 service / dto 的宽松约定”升级为“owner 域显式协作 API”的强规则：

- 域对外只暴露 `api.query`、`api.action`、`api.model`
- 外域不再依赖 foreign `service`、`entity`、`mapper`
- controller 只做 HTTP 输入输出，不再直接拼 foreign domain 语义
- 巨型 facade 通过 query/action/assembler 三类职责拆回清晰边界
- 最终通过 ArchUnit、文档和评审规则把边界收敛长期制度化

这是一次边界与协作协议治理，不是简单的重命名工程。只有把“谁可以被跨域调用、跨域传什么、哪些依赖被硬禁止”同时钉住，当前这类摇摆问题才不会在下一轮演化中重新出现。
