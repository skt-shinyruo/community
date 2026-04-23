# Community App 应用层单一风格收敛设计稿

**Date:** 2026-04-23
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

为 `backend/community-app/` 内部确立一套唯一、稳定、可执行的应用层风格，解决当前同一仓库内同时并存且互相竞争的多套后端组织方式：

- `controller -> api.query/api.action`
- `controller -> app/query`
- `controller -> use case`
- `controller -> service`
- `controller -> facade`
- `use case -> command service -> action service` 等多层转发

本次设计要解决的不是类名不统一，而是以下根问题：

- 同域内部没有统一入口，导致 application 层迁移长期停留在半成品状态
- 跨域协作与同域编排混在一起，导致 owner-domain `api.*` 被本域自己拿来当主入口
- 复杂页面聚合没有明确 owner，容易出现“依赖已注入，但编排没接完，返回先硬编码”的情况
- 新旧层同时存在时，没有明确“哪层是过渡、哪层是终态”

本轮确认的终态是：

- 同域内部统一通过本域 `ApplicationService` 编排
- 跨域同步协作统一通过 owner-domain `api.query` / `api.action` / `api.model`
- 跨域异步协作统一通过 owner-domain `contracts.event`
- 不再继续扩展 `app/query`、`UseCase + CommandService + ActionService`、`FacadeService` 这类并行风格

---

## 2. Scope And Non-Goals

### 2.1 In Scope

- `backend/community-app/` 内各业务域的同域应用层组织方式
- controller、job、listener、admin service 等同域入口如何调用 owner-domain 能力
- 跨域同步协作边界与同域本地编排的分工
- 对现有 `content`、`user` 等已分叉域的收敛方向
- 架构测试与命名规则需要如何收紧

### 2.2 Out Of Scope

- `community-gateway -> community-app` 的 HTTP 边界
- `community-app -> community-im/*` 的真实跨进程协作协议
- 数据库 schema 拆分
- Maven module 拆分

### 2.3 Non-Goals

- 不把 `community-app` 改造成重量级 CQRS / CommandBus 系统
- 不要求每个写动作都必须有独立 `UseCase`
- 不把跨域 owner-domain `api.*` 删除并退回到“大家直接调实现类”
- 不为了命名统一保留没有持久职责差异的转发层

---

## 3. Decision Summary

唯一推荐风格如下：

### 3.1 同域内部

统一采用：

`controller / job / listener -> 本域 ApplicationService -> 本域 domain/support service -> mapper / repository`

这里的 `ApplicationService` 是**角色**，表示：

- 面向本域入口的应用编排层
- 负责权限上下文转换、页面聚合、事务边界、幂等、文本预处理、跨域协作拼装
- 对 controller 暴露完整用例语义，而不是底层存储语义

### 3.2 跨域同步协作

统一采用：

`foreign domain -> owner-domain api.query / api.action / api.model`

含义固定为：

- `api.query`: 跨域只读协作面
- `api.action`: 跨域有副作用协作面
- `api.model`: 跨域协作模型

### 3.3 跨域异步协作

统一采用：

`contracts.event`

### 3.4 明确结论

- `api.*` 是**跨域协作面**，不是同域 controller 的主入口
- `ApplicationService` 是**同域应用层**，不是给外域直接注入的公共实现
- `service/entity/mapper` 仍然是 owner 域内部实现细节

---

## 4. Why This Style

当前候选方向有三种：

### 4.1 重型 CQRS / UseCase 优先

形态：

`Controller -> QueryHandler / CommandHandler -> UseCase -> DomainService`

问题：

- 对 `community-app` 过重
- 容易衍生出 `UseCase`、`CommandService`、`ActionService`、`FacadeService` 同时存在
- 一旦迁移半途而废，就会形成“抽象已长出、真实编排没闭环”的假完成状态

### 4.2 同域 ApplicationService，跨域 owner-domain API

形态：

- 同域：`Controller -> ApplicationService`
- 跨域：`ForeignDomain -> owner-domain api.*`

优点：

- 与现有 [docs/ARCHITECTURE.md](/home/feng/code/project/community/docs/ARCHITECTURE.md:123) 一致
- 与现有“same-domain caller 不应依赖 same-domain owner api”的收敛计划一致
- 能清楚区分“本地编排”和“跨域协作”
- 足够轻，不需要给每个动作都造一层壳

### 4.3 单体内一律直接调实现类

形态：

`Controller / ForeignDomain -> service`

问题：

- 会重新打穿 owner 边界
- `service` 会再次成为跨域默认入口
- 与当前仓库的领域协作治理方向冲突

本设计选择 **4.2**。

---

## 5. Target Architecture

### 5.1 同域唯一应用层入口

每个业务域对本域入口只保留少量 `ApplicationService`。

推荐调用关系：

```text
controller / job / listener
  -> owner ApplicationService
      -> owner service / policy / assembler / mapper
      -> foreign owner-domain api.query / api.action
```

约束：

- controller 不直接编排多个 owner service
- controller 不直接调用 same-domain `api.query` / `api.action`
- controller 不直接拼页面级聚合语义
- controller 只保留 transport 绑定、认证上下文提取、HTTP DTO 转换

### 5.2 `ApplicationService` 的职责

`ApplicationService` 应承担以下职责：

- 同域入口对应的完整用例编排
- 认证主体转换为业务 actor / viewer 语义
- 页面级或接口级聚合
- 幂等包装
- 文本输入预处理、敏感词过滤、基础装配
- owner 域事务边界控制
- 调用 foreign owner-domain `api.*`
- 明确降级策略

`ApplicationService` 不应承担以下职责：

- 直接暴露数据库语义，如 `insert/update/deleteById`
- 直接充当跨域公共入口
- 只做一层 rename / forward
- 把所有 unrelated use case 塞进同一个 God Service

### 5.3 owner-domain `api.*` 的职责

`api.*` 只服务于**外域调用者**。

约束：

- `api.query` 不返回 HTTP DTO
- `api.action` 不暴露内部 mapper 操作语义
- `api.model` 只承载跨域需要的最小字段集
- same-domain controller / job / listener 默认禁止依赖 same-domain `api.*`

### 5.4 本域内部 `service` 的职责

本域内部普通 `service` 负责：

- 规则计算
- 持久化装配
- 单一资源读写
- 子能力封装

它们可以依赖：

- 本域 `mapper`
- 本域 `entity`
- 本域 `repository`
- 本域 `assembler`

它们不应成为：

- 跨域默认入口
- 页面级聚合 owner
- controller 乱拼的临时积木

---

## 6. Package And Naming Rules

### 6.1 Package Shape

统一推荐包形态：

```text
com.nowcoder.community.<domain>
  controller
  service
  api
    query
    action
    model
  dto
  entity
  mapper
  contracts
    event
```

说明：

- `ApplicationService` 放在 `service` 包，不再单独扩出 `app/query`、`app/command`
- `service` 包里允许有不同角色，但 controller-facing 的入口类必须清晰命名
- 不新增新的 `application/query` 或 `application/command` 森林

### 6.2 Naming Rules

同域入口类：

- `*ApplicationService`
- 例如：`UserProfileApplicationService`、`PostApplicationService`

跨域协作接口：

- `*QueryApi`
- `*ActionApi`

跨域协作模型：

- `*View`
- `*Result`
- `*Ref`

明确禁止新增：

- `*FacadeService`
- `*CommandService`
- `*ActionService`（若只是同域入口转发）
- `app/query/*`
- `app/command/*`
- 无边界差异的 `*UseCase` 大量铺开

### 6.3 `UseCase` 的保留规则

`UseCase` 不是默认层。

只有在以下场景才允许保留：

- 某个复杂写事务有明确、稳定、可复用的事务脚本职责
- 该类被 `ApplicationService` 作为内部 helper 使用
- 它不是 controller 或 foreign domain 的直接注入入口

换句话说：

- `UseCase` 可以作为内部实现细节存在
- 但不能再与 `ApplicationService`、`CommandService`、`ActionService` 并列成为多套主入口

---

## 7. Enforcement Rules

### 7.1 Same-Domain Rules

同域内：

- `controller` 不得依赖 same-domain `api.query`
- `controller` 不得依赖 same-domain `api.action`
- `controller` 不得直接依赖过多底层 `service`
- `controller` 不得直接依赖 `mapper`
- `controller` 不得直接依赖 `entity`

推荐上限：

- 一个 controller 方法只调用一个 owner `ApplicationService` 方法
- 若确需多个调用，也应是同一个 `ApplicationService` 内部拆分，不在 controller 层拼装

### 7.2 Cross-Domain Rules

外域只允许依赖：

- `owner.api.query`
- `owner.api.action`
- `owner.api.model`
- `owner.contracts.event`

外域默认禁止依赖：

- `owner.service`
- `owner.entity`
- `owner.mapper`

### 7.3 Degradation Rules

本地同 JVM 聚合默认不是“远程降级”问题。

因此：

- 不允许“依赖已经存在但暂时不编排，先硬编码默认值”长期存在
- 不允许用单测把占位返回固化成正确行为
- 如果确需降级，必须同时满足：
  - 降级字段对外显式可见
  - 降级原因有明确产品或运行时语义
  - 降级与真实 0 值 / 空值可区分
  - 有明确移除计划

---

## 8. How Existing Domains Should Converge

### 8.1 `user` 域

当前问题：

- [GetUserProfilePageQuery.java](/home/feng/code/project/community/backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java) 已注入多路依赖，但未完成编排
- [UserQueryService.java](/home/feng/code/project/community/backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java) 注入了 `WalletAccountQueryApi` 却返回硬编码钱包值
- [UserController.java](/home/feng/code/project/community/backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java) 已把这些字段正式对外暴露

目标结构：

```text
UserController
  -> UserProfileApplicationService
      -> UserQueryService
      -> UserSocialProfileService
      -> WalletAccountQueryApi
      -> UserLevelQueryApi
      -> PostReadQueryApi
```

规则：

- `UserProfileApplicationService` 负责页面级编排与 viewer 语义
- `UserQueryService` 退回到 owner-domain 基础查询实现
- `GetUserProfilePageQuery` 要么升级并重命名为 `UserProfileApplicationService`，要么删除
- `UserProfileQueryApi` 只保留跨域真正需要的 owner-domain 协作字段，不承载页面聚合

### 8.2 `content` 域

当前问题：

- [PostController.java](/home/feng/code/project/community/backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java:80) 实际走 `PostPublishingActionApi`
- [PostPublishingActionService.java](/home/feng/code/project/community/backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java#L17) 与 [PostCommandService.java](/home/feng/code/project/community/backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java#L24) 并存
- `CreatePostUseCase` 等类已经承担部分事务脚本职责，但主入口并不唯一

目标结构：

- `PostController`、`CommentController`、管理端入口统一依赖少量 `*ApplicationService`
- `PostPublishingActionService` 与 `PostCommandService` 收敛为同一 owner 应用入口
- `CreatePostUseCase`、`UpdatePostUseCase` 等若保留，只作为内部事务 helper
- `content.api.*` 只保留给 foreign domain 使用

### 8.3 `growth`、`wallet`、`market`

这些域当前多数更接近“本域 service 直接承接应用编排”的轻量模式。

收敛原则不是强行再造层，而是：

- 把 controller-facing owner 入口明确命名成 `*ApplicationService`
- 把跨域调用继续约束到 `api.*`
- 不再引入新一轮 `app/query`、`command service`、`facade` 风格

---

## 9. Migration Rules

### 9.1 Sequence

每个域按以下顺序收敛：

1. 先确定同域 owner `ApplicationService`
2. 把 controller / job / listener 改为只依赖它
3. 把原有 same-domain `api.*` 依赖替换掉
4. 把重复的 `CommandService` / `ActionService` / `app/query` 入口删掉或内联
5. 最后收紧 ArchUnit 规则

### 9.2 Compatibility Rule

迁移期间允许短期 coexist，但必须满足：

- 新入口已明确
- 旧入口标记为迁移目标
- 旧入口不再新增新调用方
- 架构白名单只允许收缩，不允许扩散

### 9.3 Test Rule

测试必须跟随终态，而不是保护占位实现。

明确要求：

- 不再把“migration in progress”“temporarily disabled”写成长期正确行为
- 对外已暴露字段必须断言真实编排语义
- 如果暂时占位，测试名称和断言必须表明这是待移除过渡，而不是稳定契约

---

## 10. Architecture Test Changes

建议新增或收紧以下规则：

- controller 不得依赖 same-domain `api.query` / `api.action`
- controller 不得依赖 foreign `service` / `mapper` / `entity`
- 不允许新增 `..app.query..`
- 不允许新增 `*FacadeService`
- 不允许新增 `*CommandService` 作为 controller-facing 入口
- 对已确认收敛的域，逐步禁止 `UseCase` 被 controller 直接依赖

这些规则的目的不是限制命名自由，而是防止仓库重新长回多套主入口。

---

## 11. Recommended End State

`community-app` 的应用层应收敛为两条清晰规则：

1. 同域内部：
   `controller / job / listener -> owner ApplicationService -> owner implementation`

2. 跨域同步：
   `foreign domain -> owner-domain api.query / api.action / api.model`

进一步展开就是：

- `ApplicationService` 是同域唯一应用层入口
- `api.*` 是跨域唯一同步协作入口
- `UseCase` 只作为少量内部 helper，而不是主风格
- `service -> mapper` 仍是默认 owner-domain 实现路径
- repository / adapter 只在确有多后端语义时保留

这套规则的目标不是多造一层，而是消灭“同一个 use case 同时有三四个入口类”的摇摆状态。

如果不把“同域入口”和“跨域入口”分开定义，当前 `user profile` 这类问题会继续重复出现：

- abstraction 先长出来
- 真实编排没接完
- controller 已经把字段公开
- 单测再把占位值固化为正确行为

本设计的核心价值，就是让这种半迁移状态在结构上不再容易发生。
