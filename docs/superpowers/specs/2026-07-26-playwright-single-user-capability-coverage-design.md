# Playwright Single 用户可达业务能力全覆盖设计

## 状态

已完成设计讨论，等待规格审阅。

## 背景

`tests/playwright-single` 当前有 8 个业务 spec，已经覆盖登录、社区、钱包、
市场、网盘、后台和 IM 的部分主路径。但前端路由和 API service 暴露的用户能力
显著多于这些场景，现有用例还把许多页面可达性当成业务覆盖，缺少状态迁移、
关键拒绝路径、最终一致读模型和治理操作的真实验收。

本设计将“覆盖所有业务逻辑”限定为所有**用户可达**业务能力均有可追溯的
浏览器端到端场景。无 UI 的领域规则、异步消费者、恢复/补偿 job、存储生命周期
和内部安全逻辑仍由 domain/application/integration 测试覆盖；它们不能通过让
Playwright 直接调用后端 API 来伪造成浏览器覆盖。

## 目标

1. 每个产品路由和每个页面实际触发的业务 service 操作，均有对应的 Playwright
   capability 记录或明确、可审计的排除理由。
2. 每个 capability 至少关联一个真实用户旅程；写操作必须断言用户可见的状态
   变化，读操作必须断言真实内容、空状态或权限结果。
3. 关键拒绝路径使用真实 UI 触发，并同时断言精确的可见反馈；不把未预期的
   `4xx`/`5xx`、`test.skip`、`test.fail` 或重试伪装成通过。
4. 业务 spec 只经由浏览器 UI、浏览器会话和 WebSocket 与系统交互，禁止用
   Playwright 的直接 HTTP 能力替代用户动作。
5. 本地和 CI 使用同一个隔离 single runner。每次运行使用独立的 Compose
   project、网络和 volume namespace，结束后收集诊断并清理。
6. CI 在 PR 上执行能力矩阵门禁与 smoke，在 nightly/手动运行完整 regression。

## 非目标

- 不要求 Playwright 覆盖没有 UI 的每一条后端分支或每一个内部 endpoint。
- 不新增浏览器测试专用业务后门、测试 controller、数据库直连或 API 造数脚本。
- 不把健康检查、部署探针或报表脚本计入业务能力覆盖。
- 不以代码覆盖率百分比作为此套件的验收指标。

## 设计原则

### 浏览器边界

业务 spec 只能执行用户可见行为：导航、点击、表单输入、文件选择/上传、下载、
cookie/session 生命周期和页面建立的 WebSocket 交互。测试可使用
`page.waitForResponse` 观察由 UI 动作触发的请求，但最终断言必须落在页面可见的
状态、状态迁移或下载结果。

以下行为在业务 spec 和业务 fixture 中被禁止：

- `APIRequestContext`、`page.request`、Playwright `request` fixture；
- Node `fetch`、axios 或产品 API client；
- `page.evaluate` 内的 `fetch`/XHR；
- 通过 API 创建、查询、修复或清理业务数据。

`scripts/health-check.mjs` 是唯一的 HTTP 预检例外，只检查前端可达性和 Gateway
健康状态。`00-smoke.spec.ts` 本身只保留浏览器可见的冒烟行为。

### 能力覆盖的定义

一个 capability 是用户从产品 UI 能发起或观察到的一项业务能力，而不是单个
HTTP endpoint。它要声明：

- 稳定 capability ID、owner domain 和用户价值；
- 路由/UI 入口和页面实际使用的 service 操作；
- 允许角色、前置条件和 run-scoped 测试数据；
- 成功场景、关键拒绝场景和可见断言；
- 覆盖它的 Playwright spec/capability tag；
- 如不适用，明确的排除理由和复核人可理解的说明。

一个端到端旅程可以覆盖多个 capability，例如“发布帖子并在搜索、通知、收藏和
个人主页中可见”。矩阵保留一对多关联，避免将一个跨域动作拆成彼此无状态的页面
探针。

## 测试集合

测试按业务旅程而不是纯页面文件拆分，目标集合如下：

| 集合 | 主要覆盖能力 |
| --- | --- |
| `00-smoke` | 前端可达、匿名/受保护路由、登录会话和登录页直达。 |
| `01-auth` | 注册、验证码、登录风控、refresh、登出、密码重置和关键输入校验。 |
| `02-content-social-profile` | Feed、分类/标签、发帖媒体、编辑/删除、评论/回复、点赞、收藏、关注、拉黑和用户主页。 |
| `03-notice-search-growth` | 内容/社交动作后的搜索、通知已读、成长任务和等级可见性。 |
| `04-wallet` | 余额、流水、充值、提现、转账及余额/输入限制。 |
| `05-market` | 商品、库存、地址、虚拟/实物订单、交付、发货、确认、取消和争议。 |
| `06-drive-oss` | 空间、目录、文件上传/下载、搜索/移动、回收站、公开分享、校验和撤销。 |
| `07-governance` | 举报和处置、用户角色、钱包管理员操作、市场裁定和分析。 |
| `08-im` | 会话、历史、发送、已读、重连后的可见性和拉黑 policy。 |

具体文件拆分可以随实现调整，但 capability ID、矩阵和测试文件必须保持同步。

## 测试数据与隔离

### 部署基线

共享 isolated runner 从 single 配置生成带本次 run ID 的临时 env 文件，并设置独立
Compose project、network subnet、静态地址、host port 和 volume namespace。启动的
bootstrap 只提供稳定的角色基线，例如管理员、两个普通用户、可变更候选用户和
执行治理场景所需的初始余额/权限。

基础账号不是测试后门：它们是隔离部署的初始产品状态。每个旅程后续创建的帖子、
评论、订单、地址、文件、分享、举报和会话内容都必须通过产品 UI 产生。

### 场景数据

- 所有写入名称、邮箱和文件名带 `SINGLE_TEST_RUN_ID`，避免同一次运行中冲突。
- 需要跨用户协作的操作放入一个 `test` 的 `test.step` 中，显式切换浏览器身份。
- 不依赖前一个 test 的内存变量或运行历史；必要的前置状态在同一旅程中从 UI 创建。
- 管理角色、冻结、处罚等破坏性操作只作用于 disposable candidate，不修改管理员
  或后续场景依赖的基础账号。
- 搜索、通知、成长和其他最终一致结果通过用户页面的有限刷新/等待策略验证，
  不轮询后端 API。

## 能力矩阵与自动门禁

新增 `tests/playwright-single/coverage/`，其核心是一个机器可读的 capability
manifest 和一个校验脚本。

校验脚本使用 TypeScript AST，而不是正则，执行以下检查：

1. 从 `frontend/src/router/index.js` 提取产品路由，要求逐一映射或显式排除。
   预览、404、403、开发诊断等非产品路由必须有说明，不能静默忽略。
2. 从 `frontend/src/views/` 解析页面实际 import 的 `api/services` 导出，要求每项
   用户可达业务操作被 manifest 覆盖或带排除理由。纯展示或内部聚合 helper 的
   排除要能指出其用户入口归属。
3. 用 `playwright test --list` 验证 manifest 中的 capability ID 确实出现在一个
   `@regression` 测试标题中，并确认该 spec 文件存在。
4. 扫描业务 spec 和 fixture，拒绝直接 HTTP 调用、产品 API client import、
   `APIRequestContext`、`page.request`、Playwright `request` fixture 的
   `get/post/put/patch/delete/fetch` 调用、Node `fetch`、axios 及
   `page.evaluate(fetch)`。由 `waitForResponse` 返回的 `response.request()`
   仅可用于观察 UI 已触发请求的方法和 URL。健康预检脚本是唯一 allowlist 条目。
5. 拒绝未声明的 `test.skip`、`test.fail`、永久忽略配置和业务测试自动重试。

脚本生成 `reports/capability-coverage.md` 和 JSON 摘要，列出已覆盖、明确排除、
缺失映射和 capability-to-spec 对应关系。任何缺失、重复映射、失效测试引用或
禁止调用都会以非零退出码失败。

## 错误、权限与一致性

统一 fixture 继续记录同源 API 的未预期 `4xx`/`5xx`、`pageerror` 和应用
`console.error`。合法的匿名重定向、角色拒绝或表单校验必须同时满足：

1. 测试通过页面完成触发；
2. 路径、状态和场景精确匹配 allowlist；
3. 页面显示明确的登录、无权限或校验反馈。

任何其他客户端/服务端错误均视为产品或部署失败，并保留 trace、截图、视频、
响应上下文和 Compose 日志。对最终一致读模型使用受限等待，并在超时后留下最后
可见页面状态；不以无限重试掩盖消费者或 outbox 故障。

## Runner 与 CI

现有 workflow 已具备 isolated single 运行条件。实现时将其内联准备/启动/收集/
清理逻辑抽取成 `tests/playwright-single` 下的共享 runner，使本地和 CI 使用同一
生命周期：

```text
create isolated env
  -> start single topology
  -> health preflight
  -> check capability coverage
  -> run Playwright suite
  -> create reports and collect diagnostics
  -> always stop topology and remove isolated volumes
```

- PR：执行 `check:coverage` 与 `test:smoke`，上传 capability、Playwright 和
  Compose 诊断 artifact。
- nightly 与手动触发：执行同一 runner 的 `test:regression`。
- 本地提供等价命令，不再建议在共享的长期 single volume 上运行会写入的完整回归。

## 验收标准

1. manifest 中不存在未覆盖、无理由排除或无实际 Playwright 关联的用户能力。
2. `check:coverage` 能发现新增未登记路由、页面业务操作、失效 capability ID 和
   禁止的直接 API 调用。
3. 所有业务 spec 均从 UI 建立和验证业务状态；健康检查之外不直接访问后端 API。
4. 每个写入型能力至少验证一个页面可见的成功状态；每个高风险域至少有一个
   关键权限、校验或状态不允许转移的路径。
5. isolated runner 在本地和 CI 都能完成启动、测试、报告、诊断收集和清理。
6. PR smoke 与 nightly/manual regression 都先执行能力矩阵门禁；完整回归没有
   skipped、expected 5xx/4xx 或自动重试掩盖的业务场景。
7. `tests/playwright-single/README.md` 与 `docs/handbook/testing.md` 说明最终命令、
   隔离副作用、浏览器边界和 coverage report 位置。

## 分层测试责任

Playwright 验证端到端用户能力。下列逻辑继续由对应测试层负责，并在矩阵中标明
非浏览器责任而不是伪造 capability：

- domain policy、幂等细节、账本复式记账不变量和异常分支：domain/application 单元测试；
- repository SQL、事务边界、outbox、Kafka consumer、DLQ 和恢复 job：持久化/
  集成测试；
- owner-domain API 与 gateway route 合约：controller、MockMvc、Gateway 或 contract
  测试；
- DDD Tactical Layering：现有 ArchUnit guardrail。

这保证“所有用户可达业务能力”有真实浏览器证据，同时不误导读者认为浏览器测试
替代了后端业务正确性测试。
