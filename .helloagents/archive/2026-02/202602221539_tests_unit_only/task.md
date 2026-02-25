# Task List: 全仓测试单元化（仅保留 Unit Tests）

Directory: `.helloagents/archive/2026-02/202602221539_tests_unit_only/`

---

## 1. 基线扫描与约束固化
- [√] 1.1 盘点并标记所有非单元测试形态（`@SpringBootTest/@WebMvcTest/Testcontainers/嵌入式 server`），作为改造清单与验收基线
- [√] 1.2 增加“禁止集成测试形态”的约束校验（在单测中扫描仓库 `src/test/java`），避免后续回归引入 `@SpringBootTest`；depends on task 1.1

## 2. gateway（WebFlux/Gateway）测试单元化
- [√] 2.1 将 `gateway/src/test/java/com/nowcoder/community/gateway/GatewayErrorContractTest.java` 替换为单元测试：直接调用 `GatewayErrorWebExceptionHandler`/`ReactiveSecurityExceptionHandler` 并用 mock exchange/response 断言 `Result` + traceId 协议；verify why.md#关键语义仍可被单元覆盖安全错误协议幂等等
- [√] 2.2 将 `gateway/src/test/java/com/nowcoder/community/gateway/GatewayWebClientConfigTest.java` 替换为单元测试：不启动 `HttpServer.bindNow()`，改为直接验证 `GatewayWebClientConfig` 构建行为；depends on task 2.1
- [-] 2.3 将 `gateway/src/test/java/com/nowcoder/community/gateway/GatewaySecurityConfigTest.java` 改造为单元测试：按 unit-only 策略移除原 Spring 测试形态用例，未保留等价覆盖；depends on task 2.1
- [√] 2.4 将 `gateway/src/test/java/com/nowcoder/community/gateway/GatewayActuatorSecuritySmokeTest.java` 单元化替换：移除随机端口 smoke；改为验证 `infra-security-starter` 关键 fail-closed 逻辑与配置约束；depends on task 2.3
- [-] 2.5 将 `gateway/src/test/java/com/nowcoder/community/gateway/StartupSmokeTest.java` 改造为单元测试：按 unit-only 策略移除原 smoke 用例（不启动 Spring 容器）；depends on task 2.1

## 3. infra-security-starter 测试单元化
- [√] 3.1 将 `infra-security-starter/src/test/java/com/nowcoder/community/infra/security/ActuatorSecurityReactiveTest.java` 单元化替换：移除 WebTestClient/context 形态，聚焦验证关键 fail-closed 行为
- [√] 3.2 将 `infra-security-starter/src/test/java/com/nowcoder/community/infra/security/ActuatorSecurityServletTest.java` 单元化替换：聚焦验证 `prometheusUserDetailsService` 的 fail-closed 行为（缺失/弱口令应抛错）

## 4. auth-service 测试单元化（移除 Testcontainers/Redis 依赖）
- [√] 4.1 将 `auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerWebMvcTest.java` 替换为纯单元测试：直接构造 `AuthController` + Mockito mock 依赖，断言 refresh cookie/header 与返回体
- [√] 4.2 将 `auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerTest.java` 单元化替换：覆盖登录/刷新/登出等核心分支；外部依赖（user-service internal client、CaptchaStore、RefreshTokenStore）使用 mock 或 in-memory；depends on task 4.1
- [√] 4.3 将 `auth-service/src/test/java/com/nowcoder/community/auth/api/AuthActuatorSecuritySmokeTest.java` 单元化替换：聚焦 starter 的 fail-closed 校验（不再跑 MockMvc/容器）
- [√] 4.4 将 `auth-service/src/test/java/com/nowcoder/community/auth/service/RefreshTokenStoreTest.java` 替换为单元测试：使用 `InMemoryRefreshTokenStore` 验证接口契约（不使用 Testcontainers/Docker）；depends on task 4.2

## 5. content-service 测试单元化（移除 DB 依赖）
- [√] 5.1 移除 `content-service/src/test/java/com/nowcoder/community/content/api/ErrorContractTest.java`（原用例依赖集成形态，不纳入 unit-only 回归）
- [√] 5.2 移除 `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerPathSemanticsTest.java`（原用例依赖集成形态，不纳入 unit-only 回归）
- [√] 5.3 移除 `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`（原用例依赖集成形态，不纳入 unit-only 回归）
- [√] 5.4 移除 `content-service/src/test/java/com/nowcoder/community/content/api/PostRenderingCompatibilityEnabledTest.java` 与 `content-service/src/test/java/com/nowcoder/community/content/api/PostRenderingCompatibilityDisabledTest.java`（原用例依赖集成形态，不纳入 unit-only 回归）
- [√] 5.5 移除 `content-service/src/test/java/com/nowcoder/community/content/api/ContentSecurityContractTest.java`（原用例依赖 Spring 容器/契约回归，不纳入 unit-only 回归）
- [√] 5.6 移除 `content-service/src/test/java/com/nowcoder/community/content/domain/assembler/PostPayloadAssemblerTest.java`（原用例不纳入 unit-only 回归）
- [√] 5.7 移除 `content-service/src/test/java/com/nowcoder/community/content/domain/event/DomainEventOutboxAtomicityTest.java`（原用例依赖 DB/事务语义，不纳入 unit-only 回归）
- [√] 5.8 移除 `content-service/src/test/java/com/nowcoder/community/content/service/CommentConcurrencyTest.java`（原用例依赖并发/外部组件，不纳入 unit-only 回归）

## 6. message-service 测试单元化（移除 Kafka/DB/HTTP 集成）
- [√] 6.1 移除 `message-service/src/test/java/com/nowcoder/community/message/kafka/NoticeEventConsumerIntegrationTest.java`（原用例依赖 Kafka/外部组件，不纳入 unit-only 回归）
- [√] 6.2 移除 `message-service/src/test/java/com/nowcoder/community/message/kafka/NoticeEventProcessorTxTest.java`（原用例依赖事务/集成形态，不纳入 unit-only 回归）
- [√] 6.3 移除 `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerTest.java`（原用例依赖 Spring Web 层测试，不纳入 unit-only 回归）
- [√] 6.4 移除 `message-service/src/test/java/com/nowcoder/community/message/api/MessageControllerSecurityTest.java` 与 `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerSecurityTest.java`（原用例依赖集成/契约形态，不纳入 unit-only 回归）

## 7. 其它服务的安全/契约类测试单元化
- [√] 7.1 移除 `ops-service/src/test/java/com/nowcoder/community/ops/api/OpsSecurityContractTest.java`（原用例依赖 SpringBootTest/MockMvc，不纳入 unit-only 回归）
- [√] 7.2 移除 `search-service/src/test/java/com/nowcoder/community/search/api/SearchSecurityContractTest.java`（原用例依赖 Spring 容器/契约回归，不纳入 unit-only 回归）
- [√] 7.3 移除 `analytics-service/src/test/java/com/nowcoder/community/analytics/config/AnalyticsSecurityContractTest.java`（原用例依赖 Spring 容器/契约回归，不纳入 unit-only 回归）
- [√] 7.4 移除 `social-service/src/test/java/com/nowcoder/community/social/api/SocialControllerTest.java` 与 `social-service/src/test/java/com/nowcoder/community/social/api/SocialSecurityContractTest.java`（原用例依赖 Spring 容器/契约回归，不纳入 unit-only 回归）
- [√] 7.5 移除 `social-service/src/test/java/com/nowcoder/community/social/storage/RedisStorageAtomicityTest.java`（原用例依赖 Redis/Testcontainers，不纳入 unit-only 回归）
- [√] 7.6 移除 `user-service/src/test/java/com/nowcoder/community/user/api/UserControllerTest.java`、`user-service/src/test/java/com/nowcoder/community/user/api/LeaderboardControllerTest.java`、`user-service/src/test/java/com/nowcoder/community/user/api/UserSecurityContractTest.java`（原用例依赖 Spring Web 层测试/契约回归，不纳入 unit-only 回归）

## 8. Security Check
- [√] 8.1 执行安全检查（G9）：确认测试中无明文真实密钥/外部服务地址；确认所有外部依赖均为 mock/stub

## 9. Documentation Update
- [√] 9.1 更新 `.helloagents/project.md`：将“测试分层（切片/集成/Testcontainers）”调整为“仅单元测试”，并写明禁止项与示例

## 10. Verification
- [√] 10.1 `mvn test` 全量通过（无外部依赖）
- [√] 10.2 `rg -n \"@SpringBootTest|@WebMvcTest|@WebFluxTest|@DataJpaTest|Testcontainers\" */src/test/java` 结果为空
