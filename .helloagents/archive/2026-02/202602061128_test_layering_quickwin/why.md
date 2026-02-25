# Change Proposal: 测试分层 Quick Win（减少 @SpringBootTest 占比）

## Requirement Background
当前多服务测试分布不均，且 `@SpringBootTest` 占比偏高，导致：
1. **测试慢**：频繁启动 Spring 容器，单次执行成本高；
2. **环境依赖强**：更容易受外部依赖/配置影响（DB/Kafka/Nacos 等），本地与 CI 差异更明显；
3. **波动大**：集成类测试更容易产生偶发失败（并发/时序/资源竞争）。

基于代码扫描的现状（用于制定目标与优先级）：
- `src/test`：53 个测试文件，其中 23 个使用 `@SpringBootTest`（约 43%）
- `auth-service`、`user-service` 的 test/main 文件比约 0.08（相对偏低）
- `user-service`（4/5）、`content-service`（7/10）、`message-service`（5/8）中 `@SpringBootTest` 占比较高

本次选择 **Solution 2（Quick win，不改构建流程）**：优先把“可切片/可单元化”的测试从 `@SpringBootTest` 下沉到更轻量的测试形态，同时保留必要的集成测试。

## Change Content
1. 将 Controller 层测试由 `@SpringBootTest + @AutoConfigureMockMvc` 优先迁移为 `@WebMvcTest`（MVC 切片），通过 `@MockBean` 隔离下游依赖。
2. 将 Service / Kafka Consumer / Outbox 等逻辑测试优先迁移为纯单元测试（JUnit5 + Mockito），避免无必要的 Spring 容器启动。
3. 仅保留少量“确有必要”的 `@SpringBootTest`：用于覆盖 wiring/事务边界/序列化与配置组合等问题；对明显属于集成测试的用例（例如 `*IntegrationTest`）保持明确定位。
4. 对测试配置做最小化与隔离（优先使用 `src/test/resources/application.yml`），避免在测试中触发外部环境依赖（例如 Nacos/Kafka 真实连接）。

## Impact Scope
- **Modules：**
  - `auth-service`
  - `user-service`
  - `content-service`
  - `message-service`
  - `social-service`
  - `search-service`
  - （可选）`gateway`
- **Files（优先改造目标，按现状扫描列出）：**
  - `auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerTest.java`
  - `user-service/src/test/java/com/nowcoder/community/user/api/UserControllerTest.java`
  - `user-service/src/test/java/com/nowcoder/community/user/api/LeaderboardControllerTest.java`
  - `user-service/src/test/java/com/nowcoder/community/user/kafka/PointsEventConsumerTest.java`
  - `user-service/src/test/java/com/nowcoder/community/user/outbox/OutboxEventServiceTest.java`
  - `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`
  - `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerPathSemanticsTest.java`
  - `content-service/src/test/java/com/nowcoder/community/content/api/PostRenderingCompatibilityEnabledTest.java`
  - `content-service/src/test/java/com/nowcoder/community/content/api/PostRenderingCompatibilityDisabledTest.java`
  - `content-service/src/test/java/com/nowcoder/community/content/kafka/SocialEventConsumerTest.java`
  - `content-service/src/test/java/com/nowcoder/community/content/outbox/OutboxEventServiceTest.java`
  - `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerTest.java`
  - `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerSecurityTest.java`
  - `message-service/src/test/java/com/nowcoder/community/message/api/MessageControllerSecurityTest.java`
  - `message-service/src/test/java/com/nowcoder/community/message/kafka/NoticeEventConsumerIntegrationTest.java`（评估是否可拆分单元/集成）
  - `social-service/src/test/java/com/nowcoder/community/social/api/SocialControllerTest.java`
  - `social-service/src/test/java/com/nowcoder/community/social/api/InternalSocialReadControllerTest.java`
  - `search-service/src/test/java/com/nowcoder/community/search/kafka/PostEventConsumerTest.java`
- **APIs：** 无对外 API 变更（仅测试形态调整）
- **Data：** 无业务数据模型变更（可能涉及测试用 schema.sql/fixture 的小幅调整）

## Core Scenarios

### Requirement: Reduce @SpringBootTest Ratio
**Module:** backend-tests（跨服务）
将可切片/可单元化的测试从 `@SpringBootTest` 下沉，降低上下文启动成本与环境耦合。

#### Scenario: Controller tests run with @WebMvcTest
在不启动全量 Spring Boot 容器的前提下，验证：
- 路由/参数绑定/校验/返回结构
- 关键的安全行为（认证/授权/匿名访问）在切片测试中仍可验证

#### Scenario: Service/Event tests run with Mockito
对纯业务逻辑（计算、转换、分支、幂等、异常映射）使用 Mockito 隔离外部依赖，验证：
- 行为与边界条件（输入校验、空值、异常路径）
- 与下游组件的交互契约（repository/producer/client 的调用与参数）

### Requirement: Keep Necessary Integration Coverage
**Module:** backend-tests（跨服务）
保留少量必要集成测试用于兜底“配置 + wiring + 事务边界 + 序列化组合”问题，避免因为下沉过度导致漏检。

#### Scenario: Minimal SpringBootTest smoke coverage
对每个服务保留少量 smoke/关键链路测试：
- 启动成功、关键 bean 装配成功
- 关键链路（例如 outbox -> producer）在最小环境下可跑通（不追求全依赖）

## Risk Assessment
- **Risk：** 过度 mock 可能掩盖 wiring/配置问题，或降低端到端覆盖。
  - **Mitigation：** 保留少量 `@SpringBootTest` smoke 用例；对关键链路保留集成级验证。
- **Risk：** 切片测试引入安全配置差异（例如过滤器链与真实启动不一致）。
  - **Mitigation：** 安全相关测试优先用切片 + 显式导入安全配置/使用 `@WithMockUser` 等方式保持一致性；必要时保留 1-2 个集成级安全回归。
- **Risk：** 并发/时序类测试（如并发评论）本身容易波动。
  - **Mitigation：** 明确其测试级别（集成/压力），在实现阶段评估是否需要降级为确定性单测或隔离执行。

