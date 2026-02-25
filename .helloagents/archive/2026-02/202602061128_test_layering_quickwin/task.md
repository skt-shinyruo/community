# Task List: 测试分层 Quick Win（减少 @SpringBootTest 占比）

Directory: `.helloagents/archive/2026-02/202602061128_test_layering_quickwin/`

---

## 1. auth-service
- [-] 1.1 将 `auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerTest.java` 从 `@SpringBootTest` 迁移为 `@WebMvcTest`（或与现有 `AuthControllerWebMvcTest` 合并去重），验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 该用例覆盖登录/刷新/注册/激活/验证码/风控/找回密码等多链路，并依赖 Redis（Testcontainers）+ CaptchaStore 行为，属于“必要集成兜底”，本次 Quick win 保留。

## 2. user-service
- [-] 2.1 将 `user-service/src/test/java/com/nowcoder/community/user/api/UserControllerTest.java` 迁移为 `@WebMvcTest`，使用 `@MockBean` 隔离下游依赖，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 该用例依赖 H2 schema + JWT 鉴权 + 头像存储配置 + Redis mock，属于“HTTP + 业务逻辑 + 配置组合”集成验证，本次 Quick win 保留。
- [-] 2.2 将 `user-service/src/test/java/com/nowcoder/community/user/api/LeaderboardControllerTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 该用例验证 DB 排序语义（score desc, id asc），迁移为切片测试会丢失 SQL/DAO 行为覆盖，本次 Quick win 保留。
- [√] 2.3 将 `user-service/src/test/java/com/nowcoder/community/user/kafka/PointsEventConsumerTest.java` 下沉为纯单元测试（Mockito），验证 why.md#requirement-reduce-springboottest-ratio-scenario-service-event-tests-run-with-mockito
- [√] 2.4 将 `user-service/src/test/java/com/nowcoder/community/user/outbox/OutboxEventServiceTest.java` 下沉为纯单元测试（Mockito），验证 why.md#requirement-reduce-springboottest-ratio-scenario-service-event-tests-run-with-mockito

## 3. content-service
- [-] 3.1 将 `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 该用例通过 DB 构造治理字段，验证序列化输出“不暴露治理字段”，属于必要的端到端输出约束回归，本次 Quick win 保留。
- [-] 3.2 将 `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerPathSemanticsTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 该用例验证跨帖枚举防护（404），依赖 DB 关系与路径语义，本次 Quick win 保留。
- [-] 3.3 将 `content-service/src/test/java/com/nowcoder/community/content/api/PostRenderingCompatibilityEnabledTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 该用例验证配置开关对渲染结果的真实影响，依赖 DB + Controller 输出，本次 Quick win 保留。
- [-] 3.4 将 `content-service/src/test/java/com/nowcoder/community/content/api/PostRenderingCompatibilityDisabledTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 同 3.3，为避免丢失配置/序列化组合覆盖，本次 Quick win 保留。
- [√] 3.5 将 `content-service/src/test/java/com/nowcoder/community/content/kafka/SocialEventConsumerTest.java` 下沉为纯单元测试（Mockito），验证 why.md#requirement-reduce-springboottest-ratio-scenario-service-event-tests-run-with-mockito
- [√] 3.6 将 `content-service/src/test/java/com/nowcoder/community/content/outbox/OutboxEventServiceTest.java` 下沉为纯单元测试（Mockito），验证 why.md#requirement-reduce-springboottest-ratio-scenario-service-event-tests-run-with-mockito
- [√] 3.7 评估 `content-service/src/test/java/com/nowcoder/community/content/service/CommentConcurrencyTest.java` 的测试级别与波动风险（保留集成/改为确定性单测/隔离执行），验证 why.md#requirement-keep-necessary-integration-coverage-scenario-minimal-springboottest-smoke-coverage
  > Note: 该用例为并发 + DB 原子性验证，倾向保留为集成兜底；本次执行 `mvn test` 已通过，后续可再考虑用 profile/Tag 做隔离以避免拖慢默认回归。

## 4. message-service
- [-] 4.1 将 `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 当前用例覆盖鉴权 + Service 写入后读路径返回 200 的组合验证，本次 Quick win 保留。
- [-] 4.2 将 `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerSecurityTest.java` 迁移为 `@WebMvcTest`（保留安全校验语义），验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 用例通过 DB 数据验证“不能修改他人通知”，切片化会丢失数据侧约束覆盖，本次 Quick win 保留。
- [-] 4.3 将 `message-service/src/test/java/com/nowcoder/community/message/api/MessageControllerSecurityTest.java` 迁移为 `@WebMvcTest`（保留安全校验语义），验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 用例通过 DB 数据验证“非成员不能读会话/不能修改他人消息”，保留集成兜底更合适，本次 Quick win 保留。
- [√] 4.4 评估 `message-service/src/test/java/com/nowcoder/community/message/kafka/NoticeEventConsumerIntegrationTest.java`：拆分为“逻辑单测 + 少量集成兜底”或保持现状，验证 why.md#requirement-keep-necessary-integration-coverage-scenario-minimal-springboottest-smoke-coverage
  > Note: 该用例覆盖“消费事件 -> 幂等 -> API 可查询”链路，属于必要集成兜底，保留。

## 5. social-service
- [-] 5.1 将 `social-service/src/test/java/com/nowcoder/community/social/api/SocialControllerTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 当前用例覆盖 JWT 鉴权 + Like 写入/读取链路（memory storage），保留为集成兜底更稳妥，本次 Quick win 保留。
- [-] 5.2 将 `social-service/src/test/java/com/nowcoder/community/social/api/InternalSocialReadControllerTest.java` 迁移为 `@WebMvcTest`，验证 why.md#requirement-reduce-springboottest-ratio-scenario-controller-tests-run-with-webmvctest
  > Note: 当前用例覆盖 internal-token 保护 + follow 行为影响聚合读模型（memory storage），保留为集成兜底更稳妥，本次 Quick win 保留。
- [√] 5.3 将 `social-service/src/test/java/com/nowcoder/community/social/outbox/OutboxEventServiceTest.java` 下沉为纯单元测试（Mockito），验证 why.md#requirement-reduce-springboottest-ratio-scenario-service-event-tests-run-with-mockito

## 6. search-service
- [√] 6.1 将 `search-service/src/test/java/com/nowcoder/community/search/kafka/PostEventConsumerTest.java` 下沉为纯单元测试（Mockito），验证 why.md#requirement-reduce-springboottest-ratio-scenario-service-event-tests-run-with-mockito

## 7. Security Check
- [√] 7.1 执行安全自查（按 G9：敏感信息处理、权限控制、测试配置不引入真实密钥/Token）

## 8. Documentation Update
- [√] 8.1 更新 `.helloagents/project.md` 的“测试与交付/测试分层”段落：补充 Quick win 落地约定与示例
- [-] 8.2 视情况更新相关模块文档（`.helloagents/modules/*.md`）记录测试策略变化与后续分层计划
  > Note: 本次变更为跨模块测试策略与测试实现调整，已在 `.helloagents/project.md` 统一收敛；模块级文档暂不逐个补充，避免重复与维护成本上升。

## 9. Testing
- [√] 9.1 本地执行 `mvn test`（至少连续 2 次）验证稳定性；记录 `@SpringBootTest` 测试数量变化与关键用例覆盖情况
  > Note: 已连续执行 `mvn test` 2 次通过；`@SpringBootTest` 测试文件数由 23 降为 17（仍保留必要集成兜底）。
