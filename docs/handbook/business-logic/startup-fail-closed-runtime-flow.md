# 启动期 fail-closed 与运行时基础设施装配说明

这篇文档讲的是项目里“不是业务功能，但决定系统能不能安全启动和稳定运行”的那一层。

如果你只读 controller / service，很容易忽略这些代码；但在生产环境里，它们其实属于核心运行时逻辑：

- prod 启动时会校验什么
- 哪些配置缺失会直接拒绝启动
- 哪些基础设施是按条件自动装配的
- fail-closed 思路在代码里是怎么落地的

## 1. 先给结论

当前项目的运行时防线主要分三层：

### 1.1 prod 启动期校验

代表类：

- `StartupValidation`
- `StartupValidator`
- `StartupValidationAutoConfiguration`

职责：

- 生产环境启动时，缺失关键配置直接阻断进程启动

### 1.2 bean 创建期 fail-closed

代表类：

- `ServletInfraSecurityConfig`
- `XxlJobAutoConfiguration`
- `OutboxAutoConfiguration`

职责：

- 某能力被显式开启后，如果依赖配置不完整，就直接抛错，不允许“半启用”

### 1.3 条件自动装配

代表类：

- `SecurityInfraAutoConfiguration`
- `SchedulerInfraAutoConfiguration`
- `XxlJobAutoConfiguration`
- `OutboxAutoConfiguration`

职责：

- 只有满足 classpath / property / bean 条件时才装配基础设施

也就是说，当前系统不是“什么都默认开”，而是：

- 能力按条件接线
- 关键能力一旦开启就尽量 fail-closed

## 2. prod 启动校验是怎么启用的

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/autoconfig/StartupValidationAutoConfiguration.java`

## 2.1 生效条件

这套自动配置带：

- `@Profile("prod")`

因此只有：

- 激活 `prod`

时才会启用。

### 2.1.1 启动时做什么

它会创建：

- `StartupValidation`
- `ApplicationRunner startupValidationRunner`

应用启动后 runner 会立即执行：

- `startupValidation.validateOrThrow(environment)`

如果校验失败：

- 直接抛异常
- 服务拒绝启动

这就是典型的 fail-closed。

## 3. 公共 prod 校验具体查什么

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`

## 3.1 JWT HMAC secret

公共校验首先检查：

- `security.jwt.hmac-secret`

要求：

- 不能为空
- UTF-8 字节长度至少 32

否则直接报错。

这说明 JWT 密钥不是“缺了就用默认值”，而是 prod 下必须显式正确配置。

## 3.2 trusted proxy

如果：

- `gateway.trusted-proxy.enabled = true`

则还会强制校验：

- `gateway.trusted-proxy.cidrs` 非空
- 每个 CIDR 都合法
- 禁止 `0.0.0.0/0`
- 禁止 `::/0`

这条校验的目的很明确：

- 不允许在生产环境里随便信任 `X-Forwarded-For`

否则真实 IP、风控、限流、统计都会被伪造请求头绕过。

## 3.3 服务特有校验扩展点

`StartupValidation` 不会自己塞满所有服务规则，而是额外收集所有：

- `StartupValidator`

逐个执行。

这意味着它是两层结构：

- 公共底座校验
- 服务自定义补充校验

## 3.4 校验失败时返回什么

当前实现会构造一个较完整的错误消息，包含：

- active profiles
- application name
- missing / invalid 项目清单
- fix guide

而不是只抛一个简短异常。

这说明作者在这里的目标不只是“拦住启动”，还包括：

- 降低线上排障成本

## 4. 当前唯一的服务级 StartupValidator

当前生产代码里实现 `StartupValidator` 的类只有：

- `backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`

## 4.1 它什么时候生效

它先检查：

- `spring.application.name`

只有当应用名是：

- `community-app`

时才会继续执行 auth 相关 prod 校验。

也就是说，这个 validator 明确避免误伤其它独立进程。

## 4.2 它具体校验什么

### 4.2.1 refresh cookie 必须安全

要求：

- `security.jwt.refresh-cookie-secure = true`
- `security.jwt.refresh-cookie-same-site` 必须属于 `Lax/Strict/None`

### 4.2.2 找回密码链路必须可用且不能泄漏链接

要求：

- `auth.password-reset.reset-base-url` 非空
- `auth.password-reset.expose-reset-link = false`

### 4.2.3 注册邮件必须可发送

要求：

- `auth.registration.mail.enabled = true`
- `spring.mail.host` 非空

### 4.2.4 固定验证码禁止出现在 prod

如果配置了：

- `auth.captcha.fixed-code`

直接报错。

这是一条非常典型的“开发便捷开关在生产必须 fail-closed”的规则。

## 5. 运行时基础设施是怎么按条件装起来的

## 5.1 SecurityInfraAutoConfiguration

类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/SecurityInfraAutoConfiguration.java`

它本身不做复杂逻辑，只负责开启配置绑定：

- `MetricsBasicAuthProperties`
- `OriginGuardProperties`

意义在于：

- 安全基础设施的属性有统一绑定入口

## 5.2 ServletInfraSecurityConfig

类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`

它提供了三个运行时关键能力：

- Prometheus basic auth 用户
- JWT decoder
- actuator 专用 filter chain

### 5.2.1 Prometheus 密码 fail-closed

当创建 `prometheusUserDetailsService` 时：

- 密码为空直接抛
- 密码字节长度不足 12 也直接抛

这属于 bean 创建期的 fail-closed，不依赖 prod profile。

### 5.2.2 JWT decoder

如果没有自定义 bean，就会根据：

- `JwtProperties`

创建默认 `JwtDecoder`。

### 5.2.3 Actuator filter chain

这条链把 actuator 面和业务面隔离开，并默认：

- 只公开 health/info
- Prometheus 单独要 role
- 其余 deny all

## 5.3 SchedulerInfraAutoConfiguration

类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/autoconfig/SchedulerInfraAutoConfiguration.java`

只有当容器里存在：

- `StringRedisTemplate`

时，才会创建：

- `SingleFlightTaskGuard`

这意味着：

- Redis 不在场，就不会硬塞一个半残的 scheduler 锁实现

## 5.4 XxlJobAutoConfiguration

类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobProperties.java`

### 5.4.1 生效条件

需要同时满足：

- classpath 上有 `XxlJobSpringExecutor`
- `xxl.job.enabled = true`

### 5.4.2 一旦启用，就要严格验配置

当前会强制检查：

- `xxl.job.admin.addresses`
- `xxl.job.admin.accessToken`
- `xxl.job.executor.appname`

只要缺一个，就直接抛 `IllegalStateException`。

### 5.4.3 executor address 也会严格校验

如果配置了：

- `xxl.job.executor.address`

那么它必须是：

- 合法 URI
- 有明确 host
- 有正数 port

否则也直接拒绝启动。

所以 XXL-Job 当前不是“先起起来再说”，而是：

- 显式开启后必须配置完整

## 5.5 OutboxAutoConfiguration

类：

- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/autoconfig/OutboxAutoConfiguration.java`

这块虽然更偏异步投递，但本质上也属于运行时基础设施装配。

### 5.5.1 store 装配条件

只要有 `JdbcTemplate`，就能装：

- `JdbcOutboxEventStore`

如果拿不到 `JdbcTemplate`：

- 直接抛 `events.outbox requires JdbcTemplate`

### 5.5.2 scheduler 装配条件

只有 `events.outbox.enabled = true` 时，才装：

- `Clock`
- `OutboxWorkerScheduler`

这也是一种典型的条件装配。

## 6. 这些机制共同体现了什么运行时设计哲学

## 6.1 关键密钥和安全开关不允许 silent fallback

例子：

- JWT secret 长度不足直接阻断 prod 启动
- Prometheus 密码为空或太短直接抛
- 固定验证码在 prod 禁止存在

## 6.2 某功能如果被显式开启，就必须配置完整

例子：

- 开启 trusted proxy 后必须给 CIDR allowlist
- 开启 XXL-Job 后必须给 admin / executor 关键配置
- 开启 outbox 后必须能拿到 JDBC store

## 6.3 基础设施尽量按条件接线，而不是默认全装

例子：

- 没 Redis 就不装 `SingleFlightTaskGuard`
- 没开 outbox 就不装 outbox worker
- 没开 XXL-Job 就不装 executor

## 6.4 启动期和运行期防线并存

这个项目的 fail-closed 不是只靠一处做完，而是分两段：

### 6.4.1 启动期

通过：

- `StartupValidation`
- `StartupValidator`

拦住显式的 prod 错配。

### 6.4.2 运行时 bean 装配期

通过：

- `ServletInfraSecurityConfig`
- `XxlJobAutoConfiguration`
- `OutboxAutoConfiguration`

拦住“功能已经被启用但关键配置不完整”的状态。

## 7. 当前还没有单独业务文档，但其实属于核心逻辑的点

这也是这篇文档存在的理由。

下面这些逻辑不直接处理业务对象，但它们都属于核心运行时逻辑：

- prod 启动期校验
- Prometheus basic auth fail-closed
- trusted proxy 的安全校验
- XXL-Job 的条件装配与强校验
- Redis single-flight 的条件接线

如果这些逻辑出问题，系统可能：

- 启不来
- 鉴权失守
- 指标面裸奔
- 任务调度半接线

所以它们不该被视为“无关紧要的基础设施细节”。

## 8. 与其他文档的关系

如果你想继续沿着安全和异步链路往下读，可以配合：

- `security-authz-boundary-flow.md`
- `shared-outbox-delivery-guarantee-flow.md`
- `ops-scheduler-compensation-flow.md`
- `docs/handbook/SECURITY.md`

这篇文档的目标不是解释业务状态机，而是解释：

- 为什么系统在某些错误配置下宁可不启动，也不接受风险运行

## 9. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidator.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/autoconfig/StartupValidationAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/SecurityInfraAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobProperties.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/autoconfig/SchedulerInfraAutoConfiguration.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/autoconfig/OutboxAutoConfiguration.java`
