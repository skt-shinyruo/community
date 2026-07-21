# IM Credential Boundary Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让普通 access token 与 IM session ticket 使用相互独立的密钥、issuer、audience 和类型校验，彻底阻断 session ticket 被当作 bearer token。

**Architecture:** `common-security` 的 access decoder 增加凭证混淆 guard，但保持无 `typ` 的旧 access token 兼容。Gateway 与 Realtime 各自持有 `im.session-ticket` 配置和专用 Nimbus codec；Gateway 只签发 ticket，Gateway WebSocket 与 Realtime 都只用专用 decoder 验证 ticket。部署配置在同一维护窗口向两端注入同一 ticket secret。

**Tech Stack:** Java 21、Spring Boot、Spring Security OAuth2 Resource Server、Nimbus JOSE JWT、JUnit 5、Maven、Docker Compose。

## Global Constraints

- 普通 access token 继续使用 `security.jwt.hmac-secret`、`security.jwt.issuer` 和现有 TTL。
- IM ticket 必须使用 `im.session-ticket.hmac-secret`、issuer 默认 `community-im-gateway`、audience 默认 `im-realtime`。
- ticket secret 至少 32 UTF-8 字节，且不得等于 `security.jwt.hmac-secret`；缺失、空白或非法配置必须启动失败。
- 新 ticket 必须包含 `iss`、`aud`、`typ=im-session-ticket`、`sub`、`sid`、`wid`、`iat`、`exp`；不兼容旧 ticket。
- `im-common` 保持纯 DTO 模块，不引入 Spring Security 或 Nimbus。
- 日志和异常不得输出 access token、session ticket 或 secret。
- 不新增通用 credential 平台；Gateway 与 Realtime 使用各自模块内的聚焦配置类。

---

### Task 1: Access Decoder Credential-Confusion Guard

**Files:**

- Modify: `backend/community-common/common-security/src/main/java/com/nowcoder/community/common/security/jwt/JwtCodecs.java`
- Modify: `backend/community-common/common-security/src/test/java/com/nowcoder/community/common/security/jwt/JwtCodecsTest.java`

**Interfaces:**

- Consumes: `JwtProperties` and the existing HS256 secret/issuer validation.
- Produces: `JwtCodecs.jwtDecoder(JwtProperties)` that rejects decoded JWTs whose `typ` claim equals `im-session-ticket` while accepting tokens with no `typ` claim.

- [ ] **Step 1: Write the failing decoder tests**

  Add one token signed by the normal access secret with `typ=im-session-ticket` and one legacy access token without `typ`:

  ```java
  @Test
  void jwtDecoder_shouldRejectImSessionTicketTypeEvenWhenSignatureAndIssuerMatch() {
      JwtProperties properties = properties(ACCESS_SECRET, "community-auth");
      String ticket = encode(properties, "im-session-ticket");

      assertThatThrownBy(() -> JwtCodecs.jwtDecoder(properties).decode(ticket))
              .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void jwtDecoder_shouldContinueAcceptingLegacyAccessTokenWithoutType() {
      JwtProperties properties = properties(ACCESS_SECRET, "community-auth");
      Jwt jwt = JwtCodecs.jwtDecoder(properties).decode(encode(properties, null));

      assertThat(jwt.getSubject()).isEqualTo("123");
  }
  ```

- [ ] **Step 2: Run the test to verify RED**

  Run:

  ```bash
  cd backend
  mvn -pl :community-common-security -Dtest=JwtCodecsTest test
  ```

  Expected: the session-ticket test fails because the current decoder accepts any correctly signed token from the access issuer.

- [ ] **Step 3: Compose issuer and type validators**

  Configure the decoder with an issuer validator and a dedicated OAuth2 token validator:

  ```java
  OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(resolvedIssuer(properties));
  OAuth2TokenValidator<Jwt> accessType = jwt -> "im-session-ticket".equals(jwt.getClaimAsString("typ"))
          ? OAuth2TokenValidatorResult.failure(new OAuth2Error(
                  "invalid_token", "IM session ticket is not an access token", null))
          : OAuth2TokenValidatorResult.success();
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, accessType));
  ```

  Keep the comparison exact and case-sensitive. Do not require a `typ` claim on ordinary access tokens.

- [ ] **Step 4: Run GREEN and commit**

  Run the command from Step 2; expected `BUILD SUCCESS`.

  ```bash
  git add backend/community-common/common-security/src/main/java/com/nowcoder/community/common/security/jwt/JwtCodecs.java \
          backend/community-common/common-security/src/test/java/com/nowcoder/community/common/security/jwt/JwtCodecsTest.java
  git commit -m "fix(security): reject IM tickets as access tokens"
  ```

### Task 2: Gateway Dedicated Ticket Codec

**Files:**

- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImSessionTicketProperties.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewaySecurityConfig.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/SessionTicketCodec.java`
- Modify: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/SessionTicketCodecTest.java`
- Modify: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/ImSessionApiIntegrationTest.java`

**Interfaces:**

- Consumes: `JwtProperties` only to prove the ticket secret differs from the access secret.
- Produces: `@ConfigurationProperties(prefix = "im.session-ticket") ImSessionTicketProperties` with `hmacSecret`, `issuer`, and `audience`; `SessionTicketCodec.encode(...)` and `decode(...)` use only its dedicated encoder/decoder.

- [ ] **Step 1: Write failing codec and open-session tests**

  Cover round trip plus wrong secret, issuer, audience, type, expiry, missing/short/equal secret. Decode the raw ticket with a test-only decoder and assert:

  ```java
  assertThat(raw.getIssuer().toString()).isEqualTo("community-im-gateway");
  assertThat(raw.getAudience()).containsExactly("im-realtime");
  assertThat(raw.getClaimAsString("typ")).isEqualTo("im-session-ticket");
  ```

  Extend the integration test to call `POST /api/im/session` with the newly issued ticket as `Authorization: Bearer ...` and assert HTTP `401`.

- [ ] **Step 2: Run tests to verify RED**

  ```bash
  cd backend
  mvn -pl :community-im-gateway -am -Dtest='SessionTicketCodecTest,ImSessionApiIntegrationTest' test
  ```

  Expected: current tickets use the access issuer/secret, omit `aud`, and can pass the bearer signature/issuer checks.

- [ ] **Step 3: Implement validated ticket properties and codec**

  The properties class exposes normalized values and a validation method:

  ```java
  @ConfigurationProperties(prefix = "im.session-ticket")
  public class ImSessionTicketProperties {
      private String hmacSecret;
      private String issuer = "community-im-gateway";
      private String audience = "im-realtime";

      SecretKey secretKeyOrThrow(JwtProperties accessProperties) { /* validate and return HmacSHA256 */ }
      String requiredIssuer() { /* trim or throw */ }
      String requiredAudience() { /* trim or throw */ }
  }
  ```

  `SessionTicketCodec` constructs an `ImmutableSecret` encoder and `NimbusJwtDecoder`; its validator composes default timestamp checks, exact issuer, audience membership, and exact type. Add `.audience(List.of(audience))` when building claims. Register `ImSessionTicketProperties` through the existing `@EnableConfigurationProperties` annotation in `ImGatewaySecurityConfig`.

- [ ] **Step 4: Run GREEN and commit**

  Run the command from Step 2; expected both tests pass.

  ```bash
  git add backend/community-im-gateway/src/main backend/community-im-gateway/src/test
  git commit -m "fix(im-gateway): isolate session ticket credentials"
  ```

### Task 3: Realtime Dedicated Ticket Verification

**Files:**

- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionTicketProperties.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/LoadBalancedWebClientConfig.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/SessionTicketCodec.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/SessionTicketCodecTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`

**Interfaces:**

- Consumes: the same external `im.session-ticket.*` values used by Gateway.
- Produces: Realtime verification requiring dedicated signature, issuer, audience, type, and expiry; no fallback to `JwtDecoder` for access tokens.

- [ ] **Step 1: Add failing verification matrix**

  Mirror the Gateway codec matrix. In the WebSocket integration test, prove a normal access token cannot authenticate the first ticket frame and a valid dedicated ticket can.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :im-realtime -am -Dtest='SessionTicketCodecTest,ImRealtimeWebSocketIntegrationTest' test
  ```

  Expected: current codec accepts tickets from the access credential domain and has no audience validation.

- [ ] **Step 3: Implement the Realtime properties and decoder**

  Use the same property names and validation rules as Gateway, but keep the class in the Realtime module. Register it beside `ImSessionProperties`; remove `JwtProperties` and the application `JwtDecoder` from the session-ticket codec constructor. Keep access JWT use in snapshot clients unchanged.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :im-realtime -am -Dtest='SessionTicketCodecTest,ImRealtimeWebSocketIntegrationTest' test
  git add backend/community-im/im-realtime/src/main backend/community-im/im-realtime/src/test
  git commit -m "fix(im-realtime): verify dedicated session tickets"
  ```

### Task 4: Runtime Configuration and Deployment Contract

**Files:**

- Modify: `backend/community-im-gateway/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/test/resources/application-test.yml`
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`
- Modify: `deploy/tests/topology_single_cluster.sh`

**Interfaces:**

- Consumes: environment variables `IM_SESSION_TICKET_HMAC_SECRET`, `IM_SESSION_TICKET_ISSUER`, `IM_SESSION_TICKET_AUDIENCE`.
- Produces: identical Gateway/Realtime runtime settings, with secret required and issuer/audience defaults explicit.

- [ ] **Step 1: Add failing topology assertions**

  Assert every Gateway and Realtime service receives all three environment variables and both example env files define distinct sample values for `JWT_HMAC_SECRET` and `IM_SESSION_TICKET_HMAC_SECRET`.

- [ ] **Step 2: Run RED**

  ```bash
  bash deploy/tests/topology_single_cluster.sh
  ```

  Expected: ticket configuration is absent from the compose services.

- [ ] **Step 3: Wire configuration**

  Add to both application YAML files:

  ```yaml
  im:
    session-ticket:
      hmac-secret: ${IM_SESSION_TICKET_HMAC_SECRET:}
      issuer: ${IM_SESSION_TICKET_ISSUER:community-im-gateway}
      audience: ${IM_SESSION_TICKET_AUDIENCE:im-realtime}
  ```

  Add the three environment variables to the single Gateway/Realtime services and all cluster Gateway/Realtime replicas. The examples use a development-only ticket secret different from their access secret and at least 32 bytes.

- [ ] **Step 4: Verify configuration and commit**

  ```bash
  bash deploy/tests/topology_single_cluster.sh
  cd backend
  mvn -pl :community-im-gateway,:im-realtime -am test
  git add backend/community-im-gateway/src/main/resources/application.yml \
          backend/community-im/im-realtime/src/main/resources/application.yml \
          backend/community-im/im-realtime/src/test/resources/application-test.yml \
          deploy/compose.runtime.services.single.yml deploy/compose.runtime.services.cluster.yml \
          deploy/.env.single.example deploy/.env.cluster.example deploy/tests/topology_single_cluster.sh
  git commit -m "chore(im): configure dedicated session ticket credentials"
  ```

### Task 5: Credential Boundary Regression

**Files:**

- Test: all files changed in Tasks 1-4.

**Interfaces:**

- Consumes: the completed access and session-ticket credential boundaries.
- Produces: evidence that legacy access tokens remain valid while every ticket misuse path fails closed.

- [ ] **Step 1: Run the focused backend suite**

  ```bash
  cd backend
  mvn test -pl :community-common-security,:community-im-gateway,:im-realtime -am
  ```

  Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run secret and token leakage scans**

  ```bash
  rg -n 'SessionTicketCodec\(JwtProperties|SessionTicketCodec\([^)]*JwtDecoder' \
    backend/community-im-gateway/src/main backend/community-im/im-realtime/src/main
  ```

  Expected: no matches.

- [ ] **Step 3: Confirm a clean diff and commit only test corrections if needed**

  ```bash
  git diff --check
  ```

  Expected: no whitespace errors or conflict markers.
