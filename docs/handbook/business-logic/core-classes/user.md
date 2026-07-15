# User 核心类细分

本文是 [../user.md](../user.md) 的类级补充。user 域负责用户事实，而不是登录流程本身。

## 先读顺序

1. `UserRegistrationApplicationService`
2. `UserCredentialApplicationService`
3. `UserModerationApplicationService`
4. `UserReadApplicationService`
5. `UserAvatarApplicationService`

## 入口适配器

| 类 | 层 | 角色 |
| --- | --- | --- |
| `user.controller.UserController` | controller | `/api/users/**` 的 HTTP 入口。 |
| `user.controller.AdminUserController` | controller | `/api/admin/users/**` 的管理入口。 |

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `user.application.UserRegistrationApplicationService` | 注册材料准备和 verified active user 创建。 | 看它如何先准备，再插入，最后发布用户存在性。 |
| `user.application.UserCredentialApplicationService` | 密码校验、密码策略和密码更新。 | 看它如何只处理凭据，不碰登录风控。 |
| `user.application.UserReadApplicationService` | 用户资料、摘要、批量读取和跨域 user 查询。 | 看它如何只返回 user 主事实；主页聚合位于 profile 域。 |
| `user.application.UserAvatarApplicationService` | 头像 upload session / confirm。 | 看它如何把 OSS 对象确认后再写 headerUrl 投影。 |
| `user.application.UserModerationApplicationService` | 禁言 / 封禁状态和 policy event 发布。 | 看它如何同时维护用户事实和 IM policy 的下游投影。 |
| `user.application.AdminUserApplicationService` | 管理员搜索用户和修改角色。 | 看 actor / reason / confirm 这类治理约束。 |

## 领域服务

| 类 | 核心规则 |
| --- | --- |
| `user.domain.service.PasswordPolicyDomainService` | 密码复杂度和有效性规则。 |
| `user.domain.service.UserCredentialDomainService` | 密码 trim / bcrypt 判断 / authority 映射。 |
| `user.domain.service.UserModerationDomainService` | 处罚状态和处罚时长的域规则。 |
| `user.domain.service.UserReadDomainService` | 用户读取参数规范化。 |
| `user.domain.service.UserRegistrationDomainService` | 注册材料、active user 草稿和默认头像规则。 |
| `user.domain.service.UserRoleDomainService` | 管理员角色修改的 confirm / reason 约束。 |

## 基础设施和事件

| 类 | 核心职责 |
| --- | --- |
| `user.infrastructure.api.*` | 给 auth / content / social / growth / 其他域提供 user owner 的同步 API 适配。 |
| `user.infrastructure.oss.OssAvatarStorageAdapter` | 把头像上传确认委托给 OSS。 |
| `user.infrastructure.event.OutboxUserPolicyEventPublisher` | user policy contract event 写 `eventbus.user`。 |
| `user.infrastructure.event.UserEventKafkaOutboxHandler` | owner outbox 进入 dispatch application。 |
| `user.infrastructure.event.UserEventKafkaSenderAdapter` | 发布 `user.events`。 |
| `user.infrastructure.audit.Slf4jUserAuditLogAdapter` | 管理动作审计日志。 |

## 关键语义

- user 域是用户事实 owner，不是登录流程 owner。
- 头像的展示 URL 在 user，blob 和 version 在 OSS。
- 处罚状态会同时影响站内行为和 IM policy 投影。
- refresh session 和奖励 projection 都不属于 user；前者在 auth，后者在 wallet。
