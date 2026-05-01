# Community App DDD Boundary Cleanup Design

**Date:** 2026-05-01
**Status:** Implemented for Phase One
**Owner:** Codex

---

## 1. Goal

在 `backend/community-app` 已经通过严格 DDD Tactical Layering 架构测试的基础上，继续清理仍然存在的边界污染，使代码从“包依赖大体正确”推进到“层职责真正清晰”。

本轮设计的核心目标是先完成后端第一阶段边界纯化：

- `domain` 不依赖 Spring 或其他框架装配工具；
- `application` 不向 controller 返回 HTTP / Spring Web 传输类型；
- controller / web adapter 负责 HTTP cookie、header、media type、response body 表达；
- use-case result 只表达应用语义，不表达 HTTP 响应细节；
- 用 ArchUnit 把这些规则变成可执行守护，避免后续迁移回流。

这是一次 DDD 边界治理，不是业务功能改造，也不是一次全量重构。

---

## 2. Relationship To Existing Architecture Rules

本设计延续以下已批准规则：

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
- `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`

现有规则已经定义：

```text
Controller / Listener / Job
  -> ApplicationService
      -> Domain model / DomainService / Repository interface / Domain event
      -> foreign owner-domain api.query / api.action / api.model
      -> contracts.event
          -> Infrastructure implementation
```

本设计补上当前规则的盲区：

- 包名级规则能阻止 `application -> infrastructure`，但不能阻止 `application.result -> org.springframework.http.ResponseCookie`；
- 包名级规则能阻止 `domain -> infrastructure`，但不能阻止 `domain.service -> org.springframework.stereotype.Service`；
- controller 已经被约束为 HTTP adapter，但部分 HTTP response 细节仍从 application result 传出。

---

## 3. Current Problems

### 3.1 Application Result Leaks HTTP Types

当前 `auth` 应用层 result 暴露 `ResponseCookie`：

- `auth.application.result.LoginResult`
- `auth.application.result.RefreshResult`
- `auth.application.RefreshTokenApplicationService`
- `auth.application.LoginApplicationService`
- `auth.application.AuthApplicationService`

问题不是 refresh cookie 由认证用例签发，而是 `Set-Cookie` 的 HTTP 表达被放进 application result。ApplicationService 应该决定签发、轮换、撤销 refresh token；controller 或 web helper 应该决定如何把该结果写成 HTTP cookie。

### 3.2 User File Result Leaks Spring Web Types

当前 `user.application.result.AvatarFileResult` 暴露：

- `org.springframework.core.io.Resource`
- `org.springframework.http.MediaType`

文件读取能力本身属于应用用例，但 `MediaType` 和 `Resource` 是 Spring HTTP 表达。Application result 应改为应用层文件描述，例如 Java `InputStream`、content type 字符串、content length 或轻量文件句柄；controller 再转换成 `Resource`、`MediaType`、`ResponseEntity` 和 cache headers。

### 3.3 Domain Services Are Spring Beans

多个 `domain.service` 类仍有 `@Service` 或 Spring util import。典型例子：

- `auth.domain.service.CaptchaDomainService`
- `social.domain.service.FollowDomainService`
- `search.domain.service.PostSearchDomainService`
- `content.domain.service.CommentDomainService`

Domain service 是领域概念，不应因 Spring 装配而依赖框架。它们可以由 application service 构造，或通过配置层作为 Bean 暴露，但 domain 类本身应保持普通 Java 类型。

### 3.4 Migration Residue Beyond Phase One

本轮分析还发现更大的迁移残留：

- `content.application.port` 与 `content.application.assembler` 不属于目标包形状；
- `content.infrastructure.persistence.*Service` 仍像旧 service 命名；
- `market.domain.repository` 暴露大量 `select/insert/update` mapper 语义；
- 前端 `PostDetailView.vue`、`PostsView.vue` 文件过大。

这些问题真实存在，但改动面与风险大于第一阶段边界纯化。本设计把它们列为后续阶段，避免一次实现跨越多个独立战线。

---

## 4. Scope And Non-Goals

### 4.1 Phase One In Scope

第一阶段只覆盖后端边界纯化：

- 新增或加强 ArchUnit 规则：
  - `domain` 不依赖 Spring framework 类型；
  - `application.result` 不依赖 Spring Web / servlet / controller DTO 类型；
  - `ApplicationService` 不返回或暴露 HTTP transport result 类型。
- 改造 `auth` refresh cookie 结果边界：
  - application result 返回 token 值和 cookie 属性语义；
  - controller 或 auth web helper 构造 `ResponseCookie`；
  - 对外 HTTP JSON 与 cookie 行为保持不变。
- 改造 `user` avatar file 结果边界：
  - application result 不再暴露 `MediaType`；
  - controller 负责把 content type 字符串转成 `MediaType`；
  - `/files/**` 响应行为保持不变。
- 去除 `domain.service` 中的 Spring 注解与 Spring util 依赖。
- 更新与该阶段边界相关的测试。

### 4.2 Phase One Non-Goals

第一阶段不处理：

- `content.application.port` 包收敛；
- `content.infrastructure.persistence.*Service` 重命名；
- `market` repository 方法语义重塑；
- 前端大文件拆分；
- 数据库 schema、HTTP API 字段、错误码或认证模型变更；
- 将 `community-app` 拆成多个 Maven module。

---

## 5. Target Design

### 5.1 Auth Cookie Boundary

Application layer owns session decisions:

- login 成功后签发 access token 与 refresh token；
- refresh 成功后轮换 refresh token；
- logout 撤销 refresh token family；
- user disabled 或 refresh invalid 时决定是否需要清理 refresh cookie。

HTTP adapter owns cookie expression:

- cookie name；
- `HttpOnly`；
- `Secure`；
- `SameSite`；
- path；
- max age；
- `Set-Cookie` header 写入。

建议引入应用层 result：

```text
LoginResult(accessToken, refreshToken)
RefreshResult(accessToken, refreshToken)
RefreshCookieSpec(name, value, path, secure, sameSite, maxAgeSeconds)
```

其中 `RefreshCookieSpec` 是普通 application result/model，不依赖 `ResponseCookie`。Controller 或 `auth.infrastructure.web` 中的 helper 把它转换成 `ResponseCookie`。

### 5.2 User File Boundary

Application layer owns file lookup semantics:

- 根据 request URI 解析 avatar key；
- 校验 key 格式；
- 调用 avatar storage port 获取文件；
- 对不存在文件返回 null 或明确结果。

HTTP adapter owns file response expression:

- `MediaType.parseMediaType(...)`；
- `ResponseEntity<Resource>`；
- cache headers；
- `X-Content-Type-Options`。

`AvatarFileResult` 不应暴露 Spring `Resource` 或 HTTP media type。推荐字段：

```text
AvatarFileResult(content, contentType, contentLength)
```

其中 `content` 使用 Java 标准类型，例如 `InputStream` 或可关闭的应用层文件句柄；`contentType` 使用字符串，例如 `image/png`。Controller 再把它包装为 Spring `InputStreamResource` 并转换为 `MediaType`。

### 5.3 Pure Domain Services

Domain service 类不标注 Spring stereotype：

```java
public class CaptchaDomainService {
    public void requireCaptcha(String captchaId, String code) {
        if (isBlank(captchaId) || isBlank(code)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
    }
}
```

Application service 可以采用两种方式获得 domain service：

1. 直接 `new` 无状态 domain service；
2. 通过配置类将无状态 domain service 暴露为 Bean。

推荐第一阶段优先使用现有注入方式的最小改动：如果某个 domain service 已被多个 application service 构造注入，可增加集中配置类；如果只在单个 application service 中使用，可直接构造。无论哪种方式，domain 类本身不 import Spring。

### 5.4 Guardrail Tests

新增或加强 `backend/community-app/src/test/java/com/nowcoder/community/app/arch` 下的规则：

- `domain_must_not_depend_on_spring_framework`
  - `..domain..` 不依赖 `org.springframework..`；
  - 可允许 `BusinessException`、Java 标准库和本域/domain/common 错误码类型。
- `application_results_must_not_depend_on_transport_types`
  - `..application.result..` 不依赖 `org.springframework.http..`、`org.springframework.core.io..`、`jakarta.servlet..`、`..controller..`。
- `application_services_must_not_return_transport_types`
  - `..application..*ApplicationService` 的 public 方法不返回 `ResponseCookie`、`ResponseEntity`、`Resource`、`MediaType`、servlet request/response 类型。

这些规则应先红后绿：先写测试确认当前代码失败，再实施最小改造。

---

## 6. Compatibility Rules

第一阶段必须保持：

- `/api/auth/login` response body 仍返回 access token；
- `/api/auth/login` 仍设置 refresh cookie；
- `/api/auth/refresh` 成功时仍轮换 refresh cookie；
- `/api/auth/logout` 仍清理 refresh cookie；
- refresh 失败时是否清 cookie 的业务语义不变；
- `/files/**` 头像读取路径、status、content type、cache header 不变；
- 业务错误码与 HTTP JSON 字段不主动改名。

允许变化：

- application result 类型字段调整；
- controller 内部通过 helper 构造 cookie；
- domain service 装配方式调整；
- ArchUnit 规则更严格。

---

## 7. Testing Strategy

### 7.1 Architecture Tests

运行：

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,DtoBoundaryArchTest test
```

期望：

- 新增规则先能抓住现有泄露；
- 改造后全部通过；
- controller / domain / application 既有边界规则仍通过。

### 7.2 Auth Tests

覆盖：

- login 成功返回 access token 并设置 refresh cookie；
- refresh 成功返回 access token 并设置新 refresh cookie；
- logout 清理 refresh cookie；
- `USER_DISABLED` 失败时按既有规则清理 cookie；
- `REFRESH_TOKEN_INVALID` 失败时保持既有清理规则。

优先复用：

- `AuthControllerUnitTest`
- `LoginApplicationServiceTest`
- `RefreshTokenApplicationServiceTest`

### 7.3 User File Tests

覆盖：

- 合法 avatar URI 返回 `ResponseEntity<Resource>`；
- content type 字符串被 controller 正确转换为 `MediaType`；
- 不合法 file key 仍抛业务异常；
- 不存在文件仍返回 404。

优先复用：

- `FilesControllerStorageRoutingTest`
- `UserFileApplicationServiceTest`

---

## 8. Implementation Phases

### 8.1 Phase One: Backend Boundary Purification

本阶段完成本设计的 in-scope 内容。建议顺序：

1. 写 ArchUnit 失败测试；
2. 改 auth result 和 cookie helper；
3. 改 user avatar file result；
4. 去除 domain Spring 依赖；
5. 跑 focused tests；
6. 跑架构测试。

### 8.2 Phase Two: Content Package Shape Convergence

后续单独设计和实施：

- `content.application.port` 中偏持久化的接口迁入 `content.domain.repository`；
- `content.infrastructure.persistence.*Service` 改为 `MyBatis*Repository` 或明确 adapter；
- `application.assembler` 按职责迁到 application 根或 controller adapter；
- 增加 guardrail 防止新增 `application.port` 滥用。

### 8.3 Phase Three: Market Repository Semantics

后续单独设计和实施：

- repository 方法从 mapper 风格 `select/insert/update` 收敛为领域语义；
- application 字段名从 `*Mapper` 改为 `*Repository`；
- domain repository 不暴露 MyBatis 行为细节；
- 增加命名和方法语义守护。

### 8.4 Phase Four: Frontend Complexity Reduction

后续单独设计和实施：

- 拆分 `PostDetailView.vue` 的数据加载、草稿、点赞关注、治理操作、评论交互；
- 再拆分 `PostsView.vue`；
- 保持 UI 与 API 行为不变；
- 用 Vitest 做回归验证。

---

## 9. Risks And Mitigations

### 9.1 Risk: Moving Cookie Construction Changes Security Flags

风险：

- `HttpOnly`、`Secure`、`SameSite`、path、max age 在迁移中丢失或默认值变化。

缓解：

- 将 cookie 参数作为 application-neutral spec 或 config-backed helper 输入；
- controller 测试断言完整 `Set-Cookie` 关键片段；
- 不改 `JwtProperties` 的语义。

### 9.2 Risk: Removing `@Service` Breaks Bean Injection

风险：

- application service 构造器仍期待 domain service Bean。

缓解：

- 对共享 domain service 增加集中配置；
- 对局部无状态 domain service 直接构造；
- 用 Spring context 相关测试或 focused application tests 验证装配。

### 9.3 Risk: Architecture Rules Block Legitimate Technical Adapters

风险：

- 过宽的规则可能误伤 infrastructure adapter 或 controller。

缓解：

- 规则只约束 `..domain..`、`..application.result..` 和 `*ApplicationService` public 方法；
- 不约束 controller、infrastructure、configuration；
- 如需例外，必须先说明业务原因并收窄到精确类型。

---

## 10. Acceptance Criteria

第一阶段完成时必须满足：

- `domain` 生产代码无 `org.springframework..` import；
- `application.result` 生产代码无 HTTP / servlet / controller 类型依赖；
- public `*ApplicationService` 方法不返回 Spring Web transport 类型；
- `auth` 外部 HTTP 行为不变；
- `/files/**` 外部 HTTP 行为不变；
- focused auth/user tests 通过；
- `DddLayeringArchTest`、`ControllerBoundaryArchTest`、`DomainBoundaryArchTest`、`DtoBoundaryArchTest` 通过；
- `docs/ARCHITECTURE.md` 与 `docs/SYSTEM_DESIGN.md` 在新增规则落地后保持一致。

---

## 11. Implementation Record

第一阶段已完成，实施提交如下：

- `0a2d560b` `test: expose ddd boundary leaks`
  - 新增 `DddLayeringArchTest` 守护：domain 禁 Spring、application 禁 HTTP transport 类型、public `*ApplicationService` 禁返回 Spring Web transport 类型。
- `0d52dabb` `refactor: keep domain services framework-free`
  - 移除 domain service 中的 Spring stereotype / Spring util 依赖，并通过外层 `DomainServiceConfig` 完成 Spring 装配。
- `914c41e2` `refactor: keep avatar file transport at controller`
  - 将 `AvatarFileResult` 改为 application-neutral 结果，由 `FilesController` 负责 `MediaType`、`Resource`、`ResponseEntity` 和 header 表达。
- `75dd897b` `refactor: keep auth cookies at web boundary`
  - 将 `LoginResult` / `RefreshResult` 的 refresh cookie 结果改为 `RefreshCookieSpec`，由 `AuthController` 转换为 `ResponseCookie`。
- `467c84d9` `test: lock down ddd boundary response attributes`
  - 补齐 refresh cookie 属性断言、avatar `Content-Length` 断言，并同步 `docs/ARCHITECTURE.md`、`docs/SYSTEM_DESIGN.md` 与严格 DDD 设计文档。

第一阶段验收结果：

- `domain` 生产代码无 `org.springframework..` / `@Service` / `StringUtils` / `DigestUtils` 残留；
- `application.result` 生产代码无 HTTP / servlet / Spring Web transport 类型残留；
- `DddLayeringArchTest`、`ControllerBoundaryArchTest`、`DomainBoundaryArchTest`、`DtoBoundaryArchTest` 通过；
- focused auth/user tests 通过；
- `mvn -f backend/pom.xml -pl community-app -am test` 通过，`community-app` 719 tests passed；
- `docs/ARCHITECTURE.md`、`docs/SYSTEM_DESIGN.md`、`docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md` 已同步新增边界规则。

Phase Two、Phase Three、Phase Four 仍按本设计原文保持为后续独立设计和实施项，不属于本轮 Phase One 验收范围。

---

## 12. Recommended Conclusion

本轮修正符合 DDD Tactical Layering：它不是把 HTTP 类型机械地从一个类挪到另一个类，而是重新确认职责边界。

- Domain 表达业务规则，不参与 Spring 装配；
- Application 编排用例和业务结果，不表达 HTTP；
- Controller / web adapter 表达 HTTP；
- Infrastructure 表达 MyBatis、Redis、对象存储、outbox 等技术细节；
- ArchUnit 把这些边界固化为可执行规则。

后续应先完成第一阶段，让边界守护变强，再分别推进 `content` 包形状、`market` repository 语义和前端复杂度治理。这样每一阶段都有清晰验收面，回归风险可控。
