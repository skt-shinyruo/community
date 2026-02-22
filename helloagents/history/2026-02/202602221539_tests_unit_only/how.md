# Technical Design: 全仓测试单元化（仅保留 Unit Tests）

## Technical Solution

### Core Technologies
- JUnit 5
- AssertJ
- Mockito（或等价 mock/stub）
-（可选）Spring Test 的 *mock* 类型（例如 `MockHttpServletRequest/Response`、`MockServerWebExchange`）：允许作为“测试辅助对象”，但**禁止启动 Spring 容器**与**禁止随机端口 web server**

### Implementation Key Points
1. **识别并移除“非单元测试形态”**
   - 禁止：`@SpringBootTest`、`@WebMvcTest/@WebFluxTest/@DataJpaTest`、`@AutoConfigureMockMvc/@AutoConfigureWebTestClient`、Testcontainers、嵌入式 server（Reactor Netty `HttpServer.bindNow()` 等）
2. **将 HTTP 级集成断言下沉到可单测组件**
   - gateway：
     - 由 `GatewayErrorWebExceptionHandler` / `ReactiveSecurityExceptionHandler` 单测输出结构（status/header/body/traceId）
     - 由 `TraceIdSupport` 等工具类单测 traceId 解析、规范化与 traceparent 生成
   - servlet 服务（auth/content/message/...）：
     - controller：直接构造 controller，使用 Mockito mock `HttpServletRequest/HttpServletResponse`，断言 header/cookie 写出与返回 `Result<T>` 结构
     - service：mock 外部 client/repository，断言异常映射与幂等逻辑
3. **把“依赖中间件”的测试替换为 in-memory 或 mock**
   - Redis/DB/Kafka：用 fake store（内存 Map）或 Mockito stub 替代
   - 原本用于验证跨进程/跨实例一致性的用例，改为验证“接口契约 + 关键调用行为”（例如 key 前缀、TTL 计算、幂等等）
4. **保证可维护性：以意图为中心重写用例**
   - 原集成测试往往同时验证多条链路，单元化后拆为多个更聚焦的小测试：
     - 参数校验（null/blank/边界）
     - 关键分支（401/403/404/429/500 映射）
     - 幂等/去重（eventId/token family）

## Security and Performance
- **Security:** 禁止在测试中引入真实凭据、真实外部服务地址；所有敏感配置使用测试常量或 mock。
- **Performance:** 单元化后默认回归显著加速；避免随机端口/容器启动可降低 CI 抖动。

## Testing and Deployment
- **Testing:**
  1. `mvn test`（默认应全绿，且无需任何外部依赖）
  2. 额外一致性检查（建议作为 CI 步骤或本地检查项）：
     - `rg -n \"@SpringBootTest|@WebMvcTest|@WebFluxTest|@DataJpaTest|Testcontainers\" */src/test/java` 应为空
- **Deployment:** 无部署变更（仅测试代码与知识库更新）

