# 测试策略

本文档是仓库测试层级、常用命令、关键测试套件和文档变更验证的 SSOT。架构规则见 [architecture.md](architecture.md)，可靠性机制见 [reliability.md](reliability.md)，本地启动命令见 [local-development.md](local-development.md)。

## 测试层级

| 层级 | 目标 | 代表位置 |
| --- | --- | --- |
| 后端单元 / slice / 集成测试 | 验证 domain、application、adapter、controller、infra 行为。 | `backend/**/src/test/java/**/*.java` |
| 架构守卫 | 防止 DDD 分层、事务边界、DTO / domain / infra 依赖退化。 | `backend/community-app/src/test/java/com/nowcoder/community/app/arch` |
| 前端单元 / 组件测试 | 验证路由、session、HTTP interceptor、状态纯函数、Vue 组件交互。 | `frontend/src/**/*.test.js` |
| 工具测试 | 验证本地工具的 env、API contract、job、planner、batch delete。 | `tools/**/test/*.mjs` |
| 构建验证 | 确认 Maven / Vite 可编译和打包。 | `mvn package`、`npm run build` |
| 文档变更验证 | 捕获 Markdown 中尾随空白、坏 patch、明显格式问题。 | `git diff --check -- docs README.md frontend/README.md backend/README.md deploy/README.md tools` |

## 什么时候跑哪些测试

| 变更类型 | 最小验证 | 扩展验证 |
| --- | --- | --- |
| 只改 handbook / README | `git diff --check -- docs README.md frontend/README.md backend/README.md deploy/README.md tools` | 视内容引用的命令，抽样运行相关测试。 |
| 后端业务逻辑 | 定向 `mvn test -pl <module> -Dtest=<TestName>` | `cd backend && mvn test` |
| 后端架构规则 / 包结构 | 对应 ArchUnit 测试 | `cd backend && mvn test -pl :community-app -Dtest='*ArchTest'` 和全量后端测试 |
| 幂等 / outbox / scheduler / saga | 定向可靠性测试 | `cd backend && mvn test`，必要时本地 compose 演练 |
| 前端路由 / session / HTTP / store / 页面状态 | 定向 Vitest 文件 | `cd frontend && npm test` |
| 前端构建相关 | `cd frontend && npm run build` | `cd frontend && npm test && npm run build` |
| tools/mock-data-studio | 定向 `npm --prefix tools/mock-data-studio test -- <files>` | 全量 mock-data-studio 测试 |
| tools/im-load | 参数 / README 校验，必要时小流量压测 | single / cluster 拓扑下 legacy 协议压测；当前 IM session 语义需先升级工具 |

## 后端测试

从 `backend/` 执行：

```bash
cd backend
mvn test
```

打包 community-app：

```bash
cd backend
mvn -q -DskipTests -pl :community-app -am package
```

定向测试示例：

```bash
cd backend
mvn test -pl :community-app -Dtest=PostPublishingApplicationServiceTest
mvn test -pl :community-app -Dtest=MarketWalletActionProcessorApplicationServiceTest
mvn test -pl :community-app -Dtest=OutboxWorkerRetryTest
mvn test -pl :community-app -Dtest=IdempotencyGuardSerializationFailureTest
```

如果测试依赖 Testcontainers、Docker、MySQL、Redis、Kafka 或 Elasticsearch，本地环境不可用时不要把失败误判为代码失败。先确认错误是环境连接失败还是断言失败。

## 架构守卫测试

ArchUnit 测试位于：

```text
backend/community-app/src/test/java/com/nowcoder/community/app/arch
```

核心职责：

- `ControllerBoundaryArchTest`：controller 只能作为 HTTP inbound adapter。
- `DddLayeringArchTest`：业务域必须遵守 DDD Tactical Layering。
- `DomainBoundaryArchTest`：domain 不依赖 controller、application、infrastructure、MyBatis、HTTP DTO 或 owner API。
- `InfraBoundaryArchTest`：infrastructure 不泄漏到 domain。
- `ListenerBoundaryArchTest`：listener / handler / enqueuer 回到同域 application boundary。
- `TransactionBoundaryArchTest`：事务边界属于 application service。
- `DtoBoundaryArchTest`：HTTP DTO 不进入 domain。

修改 backend 架构规则或包边界时，必须同步：

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

## 可靠性关键测试

高风险机制应优先补回归测试，再改实现。

| 机制 | 代表测试 |
| --- | --- |
| HTTP 幂等 | `common-idempotency/src/test/java/.../IdempotencyGuardFingerprintTest.java`、`IdempotencyGuardStoreFailureTest.java`、`backend/community-app/src/test/java/.../IdempotencyGuardSerializationFailureTest.java` |
| Outbox | `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerRetryTest.java`、`JdbcOutboxEventStoreTest.java`、`OutboxWorkerSchedulerTest.java` |
| Search projection / reindex | `SearchPostProjectionApplicationServiceTest.java`、`PostOutboxHandlerTest.java`、`SearchReindexApplicationServiceTest.java`、`SearchReindexHandlerTest.java` |
| Market wallet saga | `MarketWalletAction*Test.java`、`MarketOrderAutoConfirmHandlerTest.java` |
| IM command / event | `community-im/im-core/src/test/java/...`、`community-im/im-realtime/src/test/java/...` |
| Gateway WS / HTTP edge | `community-gateway/src/test/java/.../HttpRoutingIntegrationTest.java`、`WsTransparentProxyIntegrationTest.java` |

新增幂等、outbox、补偿、single-flight、pending 状态机或资金动作时，至少要覆盖：

1. 首次成功。
2. 重放成功或冲突。
3. 并发 / processing 状态。
4. 下游失败后的重试或补偿。
5. 坏 payload / 不支持版本进入明确失败路径。

## 前端测试

从 `frontend/` 执行：

```bash
cd frontend
npm test
npm run build
```

定向测试示例：

```bash
cd frontend
npm test -- src/router/index.test.js src/router/authGuard.test.js src/router/navigation.test.js
npm test -- src/auth/session.test.js src/api/http.test.js src/api/imCoreHttp.test.js
npm test -- src/im/imRealtimeClient.test.js
npm test -- src/views/postsViewState.test.js src/views/postDetailState.test.js
npm test -- src/views/conversationDetailState.test.js
npm test -- src/views/marketState.test.js src/views/walletState.test.js
```

前端测试分工：

- `router/*.test.js`：路由注册、导航权限、posts query 语义。
- `auth/session.test.js`：refresh、`me` 拉取、single-flight、anonymous / error 状态。
- `api/*.test.js`：axios interceptor、endpoint 解析、Result unwrap、幂等 key 缓存。
- `im/imRealtimeClient.test.js`：session bootstrap、WS connect ticket、重连和发送消息。
- `views/*State.test.js`：复杂页面纯状态转换。
- `views/*View.test.js` / `components/**/*.test.js`：Vue 组件交互和用户可见状态。

复杂页面逻辑优先写纯函数测试。只有必须验证渲染、事件绑定或组件生命周期时，才写组件测试。

## Mock Data Studio 测试

从仓库根目录执行：

```bash
npm --prefix tools/mock-data-studio test
```

定向示例：

```bash
npm --prefix tools/mock-data-studio test -- \
  test/env.test.mjs \
  test/runtime-status.test.mjs \
  test/job-runner.test.mjs \
  test/planner.test.mjs \
  test/delete-batch-service.test.mjs
```

修改 `tools/mock-data-studio` 的生成逻辑时，优先补：

- env 解析。
- runtime-status API contract。
- planner target / deficit 计算。
- job single-flight。
- batch repository / delete 顺序。
- community API 或 domain generator 的字段语义。

## IM 压测工具验证

`tools/im-load` 主要是压测工具，不是业务测试套件。修改参数解析、JWT 生成或连接行为后，至少做小流量冒烟：

```bash
cd tools/im-load
npm install
node src/index.mjs connect-only \
  --wsUrl ws://localhost:12880/ws/im \
  --connections 2 \
  --startUserId 1 \
  --durationSec 10
```

注意：`tools/im-load` 当前仍使用旧直连 `/ws/im` + `auth` 消息协议，不覆盖当前浏览器客户端的 `/api/im/sessions` ticket bootstrap。真实容量压测当前 IM 语义前，应先升级工具或使用支持 session-bootstrap 的脚本。

## 文档验证

只改 Markdown 或 README 时，最小验证：

```bash
git diff --check -- docs README.md frontend/README.md backend/README.md deploy/README.md tools
```

如果文档新增命令示例，优先执行不会破坏本地状态的命令，例如：

```bash
cd frontend
npm test -- src/router/authGuard.test.js
```

不要为了验证文档示例执行 destructive 命令，例如删除 volume、清空数据库或强制重建索引，除非任务明确要求且已经确认影响范围。

## 测试写法要求

- 每个 bugfix 优先补能复现原问题的测试。
- 每个新业务规则至少覆盖成功路径、拒绝路径和边界输入。
- 架构迁移同时补 ArchUnit 或更新现有 ArchUnit 断言。
- 前端异步测试避免任意长 timeout，优先等待明确事件、Promise 或 DOM 状态。
- 测试数据使用最小必要字段，避免复制生产 DTO 全量字段。
- 不要用 snapshot 掩盖行为断言；只有稳定、低噪声 UI 结构才使用 snapshot。
