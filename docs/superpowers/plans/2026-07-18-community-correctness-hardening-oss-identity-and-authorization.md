# OSS Identity And Authorization Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 关闭 OSS 用户接口的对象级越权，并让 Community 后台调用在没有 Servlet 请求上下文时使用受 audience/scope 限制的短期 service JWT。

**Architecture:** `/api/oss/**` 只接受用户 JWT，Controller 从 `Authentication` 提取 subject 并调用 OSS ApplicationService；ApplicationService 加载对象和 grant 后调用纯领域 `OssObjectAccessPolicy`。`/internal/oss/**` 只接受 `aud=community-oss`、`SCOPE_oss.internal` 的服务 JWT，后台 `CommunityOssClient` 只调用 internal capability endpoint，不再转发浏览器 token；内部 ApplicationService 入口还校验 service subject 与对象/session 的 `ownerService` 一致。`/files/**` 保持匿名且只读取 `PUBLIC + ACTIVE` 对象及 active version。

**Tech Stack:** Java 21、Spring Boot、Spring Security OAuth2 Resource Server、Nimbus JWT、MyBatis、MockMvc、JUnit 5、AssertJ、Mockito、Maven。

---

## 调用契约

最终入口必须清晰区分三种身份：

```text
browser user JWT
  -> /api/oss/**
  -> OssObjectController
  -> same-domain OSS ApplicationService
  -> OssObjectAccessPolicy

community-app service JWT
  -> /internal/oss/**
  -> InternalOssObjectController
  -> same-domain OSS ApplicationService
  -> capability-scoped internal operation

anonymous
  -> /files/**
  -> PublicFileController
  -> ObjectQueryApplicationService.resolvePublicFile(...)
```

用户操作不得从 request body/query 读取 `ownerId` 或 `actorId`。internal 请求中的 owner/subject 是待管理的业务事实，认证 principal 始终是 service subject；审计中必须分开记录，不能把 service subject 替换成终端用户。

`CommunityOssClient` 是服务端 typed client，不是用户 API client，最终能力面固定如下：

| client 方法 | 路由 | 认证 |
| --- | --- | --- |
| prepare/complete proxy upload | `/internal/oss/upload-sessions/**` | service JWT |
| metadata/signed download/delete | `/internal/oss/objects/**` | service JWT |
| bind/get/release reference | `/internal/oss/objects/**/references/**` | service JWT |
| public bytes | `/files/**` | anonymous |

从 `CommunityOssClient` 删除未被后台 owner domain 使用的 `grantObjectAccess`/`revokeObjectAccess`；grant/revoke 只属于用户 JWT 下的 `/api/oss/**`。浏览器 multipart complete 仍属于用户 API，不复用 internal route。

## 用户身份和领域策略

### 建立对象访问策略的 RED 测试

**Files:**

- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/domain/service/OssObjectAccessPolicy.java`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/domain/service/OssObjectAccessPolicyTest.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/domain/repository/OssAccessGrantRepository.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/domain/model/OssAccessGrantTest.java`

- [ ] 在 `OssObjectAccessPolicyTest` 写 owner、有效 USER/READ grant、已撤销 grant、已过期 grant、其他 principal、非 READ permission、version-specific grant 的参数化测试。
- [ ] 锁定策略签名，策略只接收领域事实：

  ```java
  public final class OssObjectAccessPolicy {
      public boolean canRead(
              OssObject object,
              UUID requestedVersionId,
              String actorId,
              List<OssAccessGrant> grants,
              Instant now
      );

      public boolean canManage(OssObject object, String actorId);
  }
  ```

- [ ] 明确断言：owner 可读可管理；有效 `principalType=USER`、`principalValue=actorId`、`permission=READ` 且 object/version 匹配的 grant 只赋予读；grant 用户不能 grant/revoke/delete；空 actor 一律无权。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-oss -am -Dtest=OssObjectAccessPolicyTest test
  ```

  预期：编译因 `OssObjectAccessPolicy` 不存在而失败；创建类壳后，策略断言失败。

### 实现纯领域策略

- [ ] 实现 `OssObjectAccessPolicy`，不引入 Spring、HTTP、repository 或 infrastructure import。
- [ ] owner 比较必须同时要求对象 `ownerType=USER` 和标准化后的 `ownerId == actorId`；grant 只调用 `OssAccessGrant.activeAt(now)` 并校验 object/version/principal/permission。
- [ ] 在 `OssAccessGrantRepository` 增加聚焦查询，避免 ApplicationService 自己拼持久化条件：

  ```java
  List<OssAccessGrant> findReadGrants(UUID objectId, UUID versionId, String principalValue);
  ```

  MyBatis 实现仍返回领域 `OssAccessGrant`，不得把 mapper/dataobject 暴露给 application/domain。
- [ ] 运行 GREEN：

  ```bash
  cd backend
  mvn -pl :community-oss -am -Dtest='OssObjectAccessPolicyTest,OssAccessGrantTest' test
  ```

  预期：上述测试全部通过。

- [ ] 提交领域策略：

  ```bash
  git add backend/community-oss/src/main/java/com/nowcoder/community/oss/domain \
          backend/community-oss/src/test/java/com/nowcoder/community/oss/domain
  git commit -m "feat(oss): define object access policy"
  ```

### 删除用户可控身份的 RED 测试

**Files:**

- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/OssObjectControllerTest.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/controller/dto/PrepareUploadSessionRequest.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/controller/dto/GrantObjectAccessRequest.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/controller/OssObjectController.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/command/CompleteObjectUploadCommand.java`

- [ ] 在 `OssObjectControllerTest` 发送 body 中的 `ownerType`/`ownerId`/`actorId` 和 query 中的 `actorId`，断言这些字段不再绑定；捕获 application command，断言 actor/owner 来自 `Authentication.getName()`。
- [ ] 覆盖 `prepareUpload`、multipart `completeUpload`、`getMetadata`、`createSignedUrl`、`grantAccess`、`revokeAccess`、`deleteObject` 七条用户路径。
- [ ] 给用户 multipart complete 增加 command 身份：

  ```java
  public record CompleteObjectUploadCommand(
          UUID sessionId,
          UUID objectId,
          UUID versionId,
          ObjectUploadContent content,
          String actorId
  ) {}
  ```

- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-oss -am -Dtest=OssObjectControllerTest test
  ```

  预期：现有 Controller 仍使用 request `ownerId`/`actorId`，捕获断言失败。

### 让 Controller 只适配认证身份

- [ ] 从 `PrepareUploadSessionRequest` 删除 `ownerType`、`ownerId`、`actorId`，从 `GrantObjectAccessRequest` 删除 `actorId`；删除兼容构造器和 Jackson alias，不能保留 fallback。用户对象的 owner type 固定为 `USER`，不能保留一个被 Controller 静默忽略的 owner type 输入。
- [ ] 给所有用户方法增加 `Authentication authentication`，用一个 controller-local helper 严格提取非空 subject；Controller 固定 user owner type，并把同一个 subject 写入 command 的 owner/actor：

  ```java
  String actorId = requireUserSubject(authentication);
  new PrepareObjectUploadCommand(
      request.requestId(), request.usage(), request.ownerService(), request.ownerDomain(),
      "USER", actorId, request.visibility(), request.fileName(), request.contentType(),
      request.contentLength(), request.checksumSha256(), actorId
  );
  ```

- [ ] `revokeAccess` 和 `deleteObject` 删除 `@RequestParam actorId`；`createSignedUrl` 不再传空字符串。
- [ ] `getMetadata` 改为调用 `ObjectQueryApplicationService.getMetadata(objectId, actorId)`；不要在 Controller 查询 grant/repository。
- [ ] `completeUpload` 把 actor 写进 `CompleteObjectUploadCommand`，ApplicationService 必须校验 upload session/object owner 与 actor 一致。
- [ ] 再运行 `OssObjectControllerTest`，预期全部通过。
- [ ] 提交用户身份绑定：

  ```bash
  git add backend/community-oss/src/main/java/com/nowcoder/community/oss/controller \
          backend/community-oss/src/main/java/com/nowcoder/community/oss/application/command \
          backend/community-oss/src/test/java/com/nowcoder/community/oss/controller
  git commit -m "fix(oss): bind user operations to authenticated subject"
  ```

## 对象级授权应用

### 为每个能力写 RED 测试

**Files:**

- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectQueryApplicationServiceTest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectAccessApplicationServiceTest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectPermissionApplicationServiceTest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectLifecycleApplicationServiceTest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectUploadApplicationServiceTest.java`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/PublicFileControllerTest.java`

- [ ] 元数据和签名 URL 分别测试 owner、有效 grant、无关用户、过期 grant、已撤销 grant、对象不存在。
- [ ] grant/revoke/delete 分别测试 owner 成功、grant 用户和无关用户被拒绝。
- [ ] 对“对象不存在”和“需要隐藏存在性的未授权”断言相同稳定 `404` error code/message；不要用 `403` 暴露私有对象存在性。
- [ ] 上传完成测试 actor 与 `OssUploadSession.createdBy` 一致成功，不一致返回隐藏式 `404`，且不写 object/version。不能比较 `ownerType/ownerId`：由 Community 内部 prepare 的 avatar、drive、post-media 对象使用各 owner domain 的业务 owner，而浏览器上传者保存在 session `createdBy`。
- [ ] `/files/**` 回归覆盖 `PUBLIC + ACTIVE + active version` 成功，以及 private、delete pending、purged、inactive version 均为 `404`。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-oss -am \
    -Dtest='ObjectQueryApplicationServiceTest,ObjectAccessApplicationServiceTest,ObjectPermissionApplicationServiceTest,ObjectLifecycleApplicationServiceTest,ObjectUploadApplicationServiceTest,PublicFileControllerTest' test
  ```

  预期：无关用户当前能读取/管理，至少授权断言失败。

### 在 ApplicationService 内统一执行策略

**Files:**

- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/ObjectQueryApplicationService.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/ObjectAccessApplicationService.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/ObjectPermissionApplicationService.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/ObjectLifecycleApplicationService.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/ObjectUploadApplicationService.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/persistence/MyBatisOssAccessGrantRepository.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/persistence/mapper/OssAccessGrantMapper.java`
- Modify: `backend/community-oss/src/main/resources/mapper/oss_access_grant_mapper.xml`

- [ ] 注入 `OssObjectAccessPolicy` 和 `Clock`；metadata/signed URL 在访问 `ObjectStore` 前完成授权。
- [ ] read capability 只加载当前 actor 的候选 READ grants，随后仍由 domain policy 判定，不能把 SQL 命中当作最终授权。
- [ ] manage capability 调用 `canManage`，不把“存在 active grant”误当成 owner。
- [ ] 将隐藏式拒绝集中为 application helper，例如：

  ```java
  private BusinessException objectNotFound() {
      return new BusinessException(CommonErrorCode.NOT_FOUND, "OSS object not found");
  }
  ```

  所有私有对象的 missing/denied 分支必须走同一个错误；日志可记录 capability/caller type，但不得记录签名 URL或私有路径。
- [ ] 用户 complete upload 在 claim/finalize 之前验证 `session.createdBy == actorId`，并继续校验 session/object/version 对应关系；拒绝时事务内不改变 session lifecycle。internal complete 使用独立 service 入口并校验 `session.ownerService == serviceSubject`，不能把 service subject 与业务 actor 混为一个字段。
- [ ] 保留 `resolvePublicFile` 的独立公开判定，不让有效私有 grant 扩大 `/files/**` 的匿名能力。
- [ ] 运行 GREEN 命令，预期六组测试全部通过。
- [ ] 提交对象级授权：

  ```bash
  git add backend/community-oss/src/main/java/com/nowcoder/community/oss/application \
          backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/persistence \
          backend/community-oss/src/main/resources/mapper/oss_access_grant_mapper.xml \
          backend/community-oss/src/test/java/com/nowcoder/community/oss/application \
          backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/PublicFileControllerTest.java
  git commit -m "fix(oss): enforce object capability authorization"
  ```

## 用户 JWT 与 service JWT 隔离

### 写安全链 RED 测试

**Files:**

- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/infrastructure/security/OssSecurityConfigTest.java`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/security/OssServiceJwtProperties.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/security/OssSecurityConfig.java`
- Modify: `backend/community-oss/src/main/resources/application.yml`
- Modify: `deploy/nacos/config/community-oss.yaml`

- [ ] 使用 MockMvc/测试 JwtDecoder 覆盖：匿名 user/internal 为 `401`；user JWT 可访问 `/api/oss/**` 但不能访问 internal；service JWT 只有 issuer、audience、scope 全匹配时可访问 internal，且不能访问 user API；`GET /files/**` 匿名允许。
- [ ] 对错误 audience、缺 scope、错误 issuer、过期 token、用户 token 带偶然 scope 分别断言 `401` 或 `403`，并确认 Result envelope/trace header 保持现有格式。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-oss -am -Dtest=OssSecurityConfigTest test
  ```

  预期：当前单一 filter chain 会接受任意已认证 JWT 进入 internal，隔离断言失败。

### 实现两条有序 SecurityFilterChain

- [ ] 把 `OssSecurityConfig` 拆为显式 `@Order` 的 internal chain 与 public/user chain；internal chain 的 matcher 必须先于 `/api/**`/`/files/**` chain。
- [ ] internal chain 在 chain 构造时用 `JwtCodecs.jwtDecoder(JwtProperties)` 创建专用 decoder，并组合默认 issuer/expiry validator 与 `aud` 包含 `community-oss` 的 validator；不要把该 decoder 注册成第二个无 qualifier 的全局 bean，否则会抑制 common-security 提供的默认用户 `JwtDecoder`。authorization rule 再要求 `SCOPE_oss.internal`。
- [ ] service subject 必须非空，并由 Controller 作为显式参数传入 ApplicationService 审计；不能只放 MDC，更不能接受 request 中的 actor 代替 subject。
- [ ] 用户 chain 使用 common-security 的默认用户 decoder，并增加 user-principal authorization manager：subject 必须是合法用户 UUID，且 audience 不得包含 service audience。这样同一 HS256 key 下的 service token 也不能进入 `/api/oss/**`。
- [ ] `OssServiceJwtProperties` 校验 internal token 的 expected issuer、audience、scope；签名与验签继续复用仓库现有 `security.jwt` Nimbus HS256 配置，由 `JwtProperties`/`JwtConfigurationValidator` 校验 HMAC secret 和 issuer。internal decoder 在默认 issuer/expiry validator 之外叠加 audience validator，scope 在 internal authorization rule 中校验。
- [ ] 配置键固定为：

  ```yaml
  oss:
    security:
      service-jwt:
        issuer: ${OSS_SERVICE_JWT_ISSUER:community-auth}
        audience: ${OSS_SERVICE_JWT_AUDIENCE:community-oss}
        scope: ${OSS_SERVICE_JWT_SCOPE:oss.internal}
  ```

- [ ] 再运行 `OssSecurityConfigTest`，预期身份矩阵全部通过。
- [ ] 提交安全链：

  ```bash
  git add backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/security \
          backend/community-oss/src/main/resources/application.yml \
          backend/community-oss/src/test/java/com/nowcoder/community/oss/infrastructure/security \
          deploy/nacos/config/community-oss.yaml
  git commit -m "feat(oss): isolate user and service jwt entrypoints"
  ```

## typed internal client

### 写无 Servlet context 的 RED 测试

**Files:**

- Create: `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/OssServiceTokenProvider.java`
- Modify: `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/CommunityOssClient.java`
- Modify: `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/HttpCommunityOssClient.java`
- Modify: `backend/community-oss-client/src/test/java/com/nowcoder/community/oss/client/HttpCommunityOssClientTest.java`
- Modify: `backend/community-oss-client/src/test/java/com/nowcoder/community/oss/client/CommunityOssClientContractTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/observability/ObservedCommunityOssClient.java`
- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/controller/InternalOssObjectController.java`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/controller/dto/InternalPrepareUploadSessionRequest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/InternalOssObjectControllerTest.java`

- [ ] 在 `HttpCommunityOssClientTest` 清空 `RequestContextHolder`，注入返回 `service-token-1` 的 provider，断言所有非公开后台请求携带 `Authorization: Bearer service-token-1`。
- [ ] 在 Servlet request 中放入 `Bearer browser-token`，断言 client 仍只发送 service token。
- [ ] 断言 prepare、complete proxy upload、metadata、signed download、reference 和 cleanup/delete 全部使用 `/internal/oss/**`；`loadPublicFile` 仍走 `/files/**` 且不添加 Authorization。
- [ ] contract test 断言 `CommunityOssClient` 不再暴露 `grantObjectAccess`/`revokeObjectAccess`，防止后台 service token 获得用户授权管理能力。
- [ ] internal controller/application 测试使用两个 service subject，断言 `community-app` 只能操作 `ownerService=community-app` 的 object/session/reference；subject 不匹配时隐藏式 `404`，且 repository/storage 无写入。
- [ ] 断言 provider 返回空 token 或抛错时，在发送 HTTP 前 fail closed；异常消息不得包含 token。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-oss-client,:community-oss -am \
    -Dtest='HttpCommunityOssClientTest,CommunityOssClientContractTest,HttpCommunityOssClientFailureContractTest,InternalOssObjectControllerTest' test
  ```

  预期：无 request context 时当前 client 不发送 token，且存在 `RequestContextHolder` 转发代码，测试失败。

### 实现 service token provider 与 internal routes

- [ ] 定义最小契约：

  ```java
  @FunctionalInterface
  public interface OssServiceTokenProvider {
      String tokenValue();
  }
  ```

- [ ] `HttpCommunityOssClient` 构造器必须显式接收 provider；移除 `RequestContextHolder`、`ServletRequestAttributes` 和 `currentBearerAuthorization()`。
- [ ] 每次请求时取短期 token，不能在 client 构造时永久缓存；让轮换后的 token 自动生效。
- [ ] 从 `CommunityOssClient`、`HttpCommunityOssClient` 和 `ObservedCommunityOssClient` 一起删除 grant/revoke；其余非公开方法逐一切换到上方能力表中的 internal route。
- [ ] internal prepare 使用独立 DTO；用户 DTO `PrepareUploadSessionRequest` 只保留用户可提交字段。两个 DTO 不继承、不互相嵌套，避免以后给 internal body 加字段时重新扩大 `/api/oss/**` 的绑定面：

  ```java
  public record InternalPrepareUploadSessionRequest(
      UUID requestId,
      String usage,
      String ownerService,
      String ownerDomain,
      String ownerType,
      String ownerId,
      String visibility,
      String fileName,
      String contentType,
      long contentLength,
      String checksumSha256,
      String actorId
  ) {}
  ```

  `ownerType`、`ownerId`、`actorId` 是 owner-domain 业务事实；`ownerService` 必须与认证得到的 `serviceSubject` 相等，不能把这些字段当成认证 principal。
- [ ] 按 internal capability 扩展 `InternalOssObjectController`，每个 endpoint 仍只调用同域 `ObjectUploadApplicationService`、`ObjectQueryApplicationService`、`ObjectAccessApplicationService`、`ObjectReferenceApplicationService` 或 `ObjectLifecycleApplicationService`，Controller 不直接访问 repository/storage。
- [ ] `InternalOssObjectController` 从 `Authentication.getName()` 提取非空 `serviceSubject`，禁止从 body/query 接受该值。精确增加/保留以下 ApplicationService 入口，并让它们委托现有私有核心逻辑：

  ```java
  ObjectUploadSessionResult prepareInternalUpload(
      String serviceSubject, PrepareObjectUploadCommand command);
  ObjectMetadataResult completeInternalUpload(
      String serviceSubject, CompleteObjectUploadCommand command);
  ObjectMetadataResult getInternalMetadata(UUID objectId, String serviceSubject);
  ObjectSignedUrlResult createInternalSignedDownloadUrl(
      CreateSignedUrlCommand command, String serviceSubject);
  ObjectLifecycleResult deleteInternalObject(
      DeleteObjectCommand command, String serviceSubject);
  ```

  reference bind/get/release 同样增加 service subject 参数或 internal command；不能让 Controller 调用用户授权入口。
- [ ] internal prepare 将 `ownerService` 绑定为 `serviceSubject`（或拒绝不相等值），并拒绝用户入口保留的 `ownerType=USER`；后续 metadata/signed URL/delete 同时校验 `object.ownerService == serviceSubject && ownerType != USER`，complete 对 session 做同样校验，reference 校验 object/reference owner service。这样用户不能在 `/api/oss/**` 伪造 `ownerService=community-app` 后混入 internal 能力。业务 `actorId` 仅作为 owner-domain 审计事实，不参与 service principal 判定。
- [ ] browser multipart complete 继续使用 `/api/oss/objects/{objectId}/complete` 和用户 JWT；frontend `src/api/uploadSession.js` 的 URL 不改成 internal。
- [ ] 再运行 client/internal controller 测试，预期全部通过。
- [ ] 提交 typed client：

  ```bash
  git add backend/community-oss-client \
          backend/community-oss/src/main/java/com/nowcoder/community/oss/controller \
          backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/InternalOssObjectControllerTest.java
  git commit -m "feat(oss): authenticate internal client with service tokens"
  ```

## Community app wiring 与调用者回归

### 写配置和 adapter RED 测试

**Files:**

- Move: `backend/community-app/src/main/java/com/nowcoder/community/user/config/OssClientConfiguration.java` -> `backend/community-app/src/main/java/com/nowcoder/community/infra/oss/OssClientConfiguration.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/oss/OssClientProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/oss/JwtOssServiceTokenProvider.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/infra/oss/OssClientConfigurationTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/observability/ObservedCommunityOssClient.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/oss/OssDriveObjectStorageAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapterTest.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `deploy/nacos/config/community-app.yaml`

- [ ] 测试 token claims 精确包含 `iss`、`sub=community-app`、`aud=[community-oss]`、`scope=oss.internal`、`iat`、最长 5 分钟 `exp`；使用可控 `Clock` 验证过期时间。`iss` 和 HMAC secret 来自现有 `JwtProperties`/`JwtEncoder`，不能在 OSS client 下复制第二份 secret 配置。
- [ ] 测试配置缺 service subject/audience/scope/token TTL 或全局 issuer/HMAC secret 时 fail closed，不创建匿名 client bean。
- [ ] adapter 测试在无 HTTP request 的 job/handler 场景调用 prepare/metadata/reference/delete，断言不会因缺浏览器 token 失败。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app,:community-oss-client -am \
    -Dtest='*Oss*AdapterTest,*OssClientConfigurationTest' test
  ```

  预期：旧配置不提供 service token，至少 bean/claim 断言失败。

### 实现全局 infrastructure 配置

- [ ] 将 OSS client bean 移出 user owner domain，包名改为 `com.nowcoder.community.infra.oss`；全局技术 bean 不归 `user.config` 所有。
- [ ] `OssClientProperties` 使用 `@ConfigurationProperties("oss.client")` 并在配置 bean 创建前校验 base URL、service subject、audience、scope 和 `0 < tokenTtl <= PT5M`；不要继续散落多个 `@Value` 默认值。
- [ ] `JwtOssServiceTokenProvider` 实现 client 模块的 `com.nowcoder.community.oss.client.OssServiceTokenProvider`；参考 `im-realtime` 的 `PolicySnapshotClient`，用共享 `JwtProperties` 和 `JwtEncoder` 生成 HS256 短期 token，并显式写 audience。
- [ ] `ObservedCommunityOssClient` 同步新 client 契约；观测标签记录 capability/status/latency，不记录 Bearer token、签名 URL 或私有 payload。
- [ ] 在 `application.yml` 和 Nacos 增加：

  ```yaml
  oss:
    client:
      base-url: ${OSS_CLIENT_BASE_URL:http://community-oss:18090}
      service-subject: ${OSS_CLIENT_SERVICE_SUBJECT:community-app}
      audience: ${OSS_CLIENT_AUDIENCE:community-oss}
      scope: ${OSS_CLIENT_SCOPE:oss.internal}
      token-ttl: ${OSS_CLIENT_TOKEN_TTL:PT5M}
  ```

- [ ] 确认 `OssDriveObjectStorageAdapter`、`OssPostMediaStorageAdapter`、`OssPostMediaReferenceQueryAdapter`、`OssAvatarStorageAdapter` 只依赖 client contract，并仍位于各 owner-domain infrastructure；不要把 foreign client 注入 Controller/Listener/Job。
- [ ] 再运行 adapter/config 测试，预期全部通过。
- [ ] 提交 Community wiring：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/infra \
          backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/oss \
          backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/oss \
          backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/oss \
          backend/community-app/src/main/resources/application.yml \
          backend/community-app/src/test/java/com/nowcoder/community \
          deploy/nacos/config/community-app.yaml
  git commit -m "refactor(oss): wire scoped internal client globally"
  ```

## 完整验证与发布门禁

- [ ] 运行 OSS/client 回归：

  ```bash
  cd backend
  mvn -pl :community-oss-client,:community-oss,:community-app -am \
    -Dtest='*Oss*Test,Object*ApplicationServiceTest,*Oss*AdapterTest' test
  ```

  预期：owner/grant/unauthorized/public/service identity 矩阵全部通过。

- [ ] 运行架构守卫：

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：`BUILD SUCCESS`；没有入站 adapter 到 `CommunityOssClient` 或 foreign owner-domain API 的新调用。

- [ ] 运行静态扫描：

  ```bash
  rg -n 'RequestContextHolder|ServletRequestAttributes|currentBearerAuthorization|@RequestParam\(.*actorId|String actorId\)' \
    backend/community-oss-client/src/main \
    backend/community-oss/src/main/java/com/nowcoder/community/oss/controller
  ```

  预期：client 中无 Servlet token 转发；用户 Controller DTO/query 中无可控 actor。internal 审计模型可保留明确命名的业务 actor 字段，但不能参与认证 principal 判定。

- [ ] 在集成环境用四个 token 验收：owner user、grant user、unrelated user、service；确认 private metadata/signed URL、grant/delete、internal reference 和 public file 的预期状态码。
- [ ] 发布顺序固定为：先配置签名/验证材料，再部署兼容双入口的 `community-oss`，最后部署强制 service token 的 `community-app` client；不能先部署不再转发用户 token 的 client。
- [ ] `git diff --check`，预期无 whitespace 错误。
