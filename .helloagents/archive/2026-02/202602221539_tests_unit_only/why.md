# Change Proposal: 全仓测试单元化（仅保留 Unit Tests）

## Requirement Background
当前仓库存在较多基于 Spring 容器/网络端口/外部中间件（Redis/DB/Kafka/Nacos/Docker）的测试形态（例如 `@SpringBootTest`、`@WebMvcTest`、Testcontainers 等）。这类测试在 CI 或本地开发环境缺少依赖组件时，容易出现与业务无关的波动失败（如 503/超时/连接拒绝），并显著拉长默认回归耗时。

本次变更目标明确：
- **所有测试只保留单元测试**（Unit Tests）
- **不保留集成测试**（Integration Tests）
- **不允许任何依赖其他组件的测试**（包括但不限于：真实 Redis/DB/Kafka/Nacos、Docker/Testcontainers、真实网络端口监听与 HTTP 调用）

## Change Content
1. 将现有 `@SpringBootTest` / 切片测试 / Testcontainers / 本地 server 等用例，改造为 **纯 JUnit5 + Mockito（或等价 mock/stub）** 的单元测试：
   - 不启动 Spring 容器（不启动 web server、不注入 ApplicationContext）
   - 不做真实网络请求（包括本地 `HttpServer.bindNow()` / `MockMvc` 依赖容器 / `WebTestClient` 随机端口）
   - 外部依赖（DB/Redis/Kafka/HTTP/RPC）全部使用 mock/stub/in-memory 实现替代
2. 对原本通过“端到端 HTTP”验证的安全/错误协议/traceId 规则，调整为对底层组件的单元验证：
   - 例如：异常映射器、Security 异常处理器、traceId 解析/回填、cookie/header 写出、幂等/去重逻辑等
3. 同步更新知识库（SSOT）中的测试约定：明确禁止项与推荐单元测试写法，避免后续新增集成测试导致回归不稳定。

## Impact Scope
- **Modules:**
  - gateway, infra-security-starter
  - auth-service, user-service, content-service, social-service, message-service, search-service, ops-service, analytics-service
- **Files:**
  - 主要影响各模块 `src/test/java/**` 下的测试文件（尤其是 `@SpringBootTest` / `@WebMvcTest` / Testcontainers）
  - 可能少量补充测试用的 stub/fake（位于各模块 `src/test/java/**`）
  - 知识库：`.helloagents/project.md`（测试约定部分）
- **APIs:** 无（不涉及对外接口变更）
- **Data:** 无（不新增/修改生产数据模型；仅移除测试对外部 DB/Redis 的依赖）

## Core Scenarios

### Requirement: 单元测试不依赖外部组件
**Module:** 全部

#### Scenario: 在“无 Redis/DB/Kafka/Nacos/Docker”环境运行默认回归
- 运行命令：`mvn test`
- 预期结果：
  - 测试全部通过
  - 不启动任何外部组件容器（无 Testcontainers）
  - 不产生真实网络监听端口/HTTP 调用（含本地 loopback）

### Requirement: 关键语义仍可被单元覆盖（安全/错误协议/幂等等）
**Module:** gateway/auth/content/message（及相关 starter）

#### Scenario: 错误响应协议（Result + traceId + timestamp）
- 预期结果：关键错误映射逻辑在单元层可验证（不依赖 web server/MockMvc/WebTestClient 随机端口）

#### Scenario: 安全兜底语义（例如 prometheus basic-auth 配置校验）
- 预期结果：缺失/弱口令等 fail-closed 校验在单元层可验证

## Risk Assessment
- **Risk:** 覆盖面下降（失去端到端 wiring/配置组合验证）
  - **Mitigation:** 将“端到端验证”转为对底层组件的可重复单元验证；同时在知识库中明确“联调/演练”应使用 docker compose（但不作为 `mvn test` 的默认测试形态）。
- **Risk:** 测试改造工作量较大（跨模块、多类测试形态）
  - **Mitigation:** 以“先消除外部依赖与 SpringBootTest”为第一优先级；对无法以单元形式表达的用例，用更贴近意图的纯函数/反射/契约解析测试替代，必要时删除原用例。

