# tests/playwright-single 全绿 E2E 与 CI 设计

## 状态

已完成设计讨论，等待实现规划。

## 背景

`tests/playwright-single` 是通过浏览器访问已经部署的 single 拓扑的端到端
验收套件。它当前覆盖 smoke、认证、社区、钱包、市场、网盘和后台，但其中
`99-known-issues.spec.ts` 将若干真实的 `503`、`403` 或页面空壳状态当作通过
条件。最近仓库又推进了 IM 会话分页、历史消息和前端错误恢复，因此部分已知
问题断言已经过时。

当前目标不是调整断言来隐藏失败，而是修复被测试暴露的产品或部署问题，并使
本地与 CI 都能以同一套命令验证完整成功链路。

## 目标

1. `tests/playwright-single/tests/` 下的业务用例全部按成功语义通过。
2. 收藏、IM 会话、后台主体页面和网盘公开分享校验不再产生未预期的 `5xx` 或
   `4xx`。
3. 保留合法的权限行为：匿名访问受保护页面可以跳转登录，普通用户访问后台
   用户管理可以得到精确的 `403`。这些是被明确断言的业务结果，不属于隐藏错误。
4. 本地运行和 GitHub Actions 运行共用同一个 runner、配置和测试入口。
5. PR 快速反馈，nightly 或手动运行完整回归，并在失败时保留足够的诊断证据。
6. 所有 backend 业务修复遵守仓库的 DDD Tactical Layering 和现有架构守卫。

## 非目标

- 本轮不扩展到所有尚未覆盖的业务域；IM、收藏、后台和网盘只补足当前已暴露
  的失败链路。
- 本轮不把完整数据库清理框架作为前置条件。CI 使用全新的隔离 volume，本地
  使用带 run ID 的数据；通用数据重置可以作为后续独立工作。
- 本轮不通过删除失败用例、放宽状态码断言、使用 `test.fail` 或增加无限重试
  来获得绿色结果。

## 方案选择

已比较三种方案：

1. 只修改 Playwright 断言或跳过已知问题，成本最低但会掩盖产品缺陷，拒绝。
2. 按故障做垂直修复，同时加固 runner 和 CI。范围可控，并能让绿色结果保持
   业务含义，采用此方案。
3. 先建设完整的可重置测试环境，再处理产品问题。长期隔离性最好，但会把
   数据治理、部署和 E2E 修复绑定为一个过大的首轮项目。

方案 2 会采用方案 3 中必要的隔离 project、volume、网络和日志收集能力，
但不等待完整数据重置平台完成后才修复业务。

## 验收边界

### 测试组织

- 删除 `99-known-issues.spec.ts` 的“当前问题复现”语义，不再保留
  `expected 503/403` 断言。
- 将收藏成功验证并入社区回归；将后台主体内容验证并入后台回归；将网盘
  分享校验并入网盘回归；新增 `07-im.spec.ts` 验证 IM 会话页和会话接口成功
  返回。
- smoke 保留部署可达、Gateway 健康、匿名帖子页、受保护路由跳转、登录和
  登录页直达等最小路径。
- `package.json` 提供 `test:smoke`、`test:regression`、`report` 和
  `show-report`。`test` 保留为完整回归别名；完成迁移后移除 `test:known`。
- `playwright.config.ts` 不再通过 `testIgnore` 隐藏任何业务 spec。每个
  `describe` 或 test 都必须带 `@smoke` 或 `@regression` 标记；smoke 用例同时
  可以标记为 regression。`test:smoke` 使用 `--grep @smoke`，
  `test:regression` 使用 `--grep @regression`。

### 正常与预期异常

“全绿”表示没有未预期的页面或接口错误，而不是取消合法的授权语义：

- 匿名访问 `/wallet` 的登录跳转必须成功。
- 普通用户访问 `/admin/users` 的 `403` 必须成功且被精确断言。
- 收藏列表、IM 会话、管理员主体页面和网盘分享校验必须返回成功结果并渲染
  对应内容或空状态。
- 不得再出现把 `503` 或旧的 IM `403` 当作通过条件的测试。

## 产品修复设计

### 收藏

目标是让已登录用户请求
`GET /api/bookmarks?page=0&size=10` 返回 `200` 和合法的帖子摘要列表。

实现时沿着 Controller、BookmarkApplicationService、BookmarkRepository、
MyBatis mapper 和内容摘要组装链路定位根因。重点检查收藏 SQL 与当前 schema
的一致性，以及标签、最近活动、内容块预览等可选投影数据为空时的行为。不得
在 Controller 中吞掉持久化异常或把错误转换为空列表。

后端增加能覆盖列表查询和摘要组装的应用/持久化测试；Playwright 在帖子收藏
后打开收藏页，确认新帖标题可见，并确认收藏 API 没有未预期错误。

### IM 会话

目标是让当前前端使用的会话接口、Gateway 路由和 `im-core` 鉴权保持一致。
空会话也必须返回 `200` 和稳定的空列表响应，页面显示正常空状态；有会话时
继续显示会话摘要。

实现时核对旧的 offset 接口和新的 cursor 分页接口，确认前端、Gateway 和
im-core 选择同一条公开协作契约，并确认 JWT 在 Gateway 到 im-core 的边界被
正确转发。修复必须保留 `im-core` 的认证和内部投影权限边界，不以放宽安全规则
换取页面成功。

后端增加 controller/security 或 Gateway route 集成覆盖；前端增加消息页成功
和空状态测试；Playwright 验证 `/messages` 页面加载、会话请求成功以及页面不
出现错误状态。

### 管理后台

目标是保留普通用户的权限拦截，同时让管理员页面渲染实际主体：用户管理的
搜索区域、市场争议的列表或空状态、开发检查台的主体内容都不能只剩页面 shell。

后端核对管理员接口、角色校验和空数据响应；前端核对路由、加载状态、空状态和
接口错误状态的渲染。Playwright 保留普通用户 `403` 测试，并以管理员身份验证
这些主体页面的成功内容。

### 网盘公开分享

目标是使用真实提取码完成分享校验，返回 `200`、有效 ticket 和分享条目结果，
随后可以读取分享目录或文件入口。

实现时检查 DriveShareApplicationService 的 share/access 记录、ticket 编解码、
对象存储 adapter 以及 single 拓扑中 Garage/OSS 的 endpoint、密钥和 bucket
配置。错误的对象存储或 ticket 配置必须修复在 infrastructure/deployment
边界，不能在 Controller 中把 `503` 改写成成功响应。

后端增加分享校验和 ticket 行为测试；Playwright 在创建分享后验证成功响应、
token/ticket 和后续分享内容。

### Playwright 自身的稳定性

社区发帖入口使用 `.posts-composer` 或明确的按钮 role 进行定位，避免页面中
两个同名“开始一个讨论”元素触发 strict mode。市场跨步骤流程不再让后续测试
隐式依赖前一个测试留下的内存 URL；它要么收敛为一个带 `test.step` 的完整业务
旅程，要么通过显式 fixture 创建并返回场景数据。

## Runner 设计

### 配置和错误审计

- 保留单 Chromium 项目和默认单 worker。完成数据隔离前不开放并行。
- 健康检查脚本以固定上限轮询前端首页和 Gateway `actuator/health`；业务测试
  不使用自动重试，保证失败仍然暴露。
- 公共 Playwright fixture 监听同源 API 的 `5xx`、未列入 allowlist 的 `4xx`、
  `pageerror` 和应用 console error，并在测试结束时失败。allowlist 只允许
  明确的匿名跳转和普通用户后台授权场景，必须同时限定请求路径与状态码。
- 继续生成 list、HTML、JSON 和 Markdown 报告，并保留失败 trace、截图和视频。

### 测试数据

- 所有写入数据使用 run ID；CI 的 run ID 来自 workflow 运行上下文，本地可由
  `SINGLE_TEST_RUN_ID` 显式指定。
- CI 使用全新 volume，因此不依赖历史数据库中的测试数据；本地不自动清空
  业务数据，README 明确记录这一副作用。
- 跨测试的业务依赖必须显式表达。市场场景不允许仅依赖模块级变量保存的
  `listingUrl` 或 `orderUrl`。

## CI 设计

新增独立的 Playwright single workflow，和现有后端架构 workflow 分开管理：

### Pull Request

1. checkout 仓库。
2. 安装 Node 依赖和 Chromium system dependencies。
3. 从 single 示例配置生成本次运行的隔离配置，设置独立 Compose project、
   volume namespace、网络和不冲突的静态地址。
4. 使用 `deployment.sh up --topology single --no-observability` 启动拓扑。
5. bounded polling 等待前端和 Gateway 健康。
6. 执行 `npm --prefix tests/playwright-single run test:smoke`。
7. 无论成功失败，都收集报告、`test-results/`、Compose 状态和服务日志，最后
   使用相同的 project/config 执行 `deployment.sh down`。

### Nightly 和手动运行

使用同一套启动、预检、artifact 和清理步骤，将 smoke 替换为
`npm --prefix tests/playwright-single run test:regression`，随后运行 Markdown
报告生成脚本。

CI job 的 project、volume、网络和临时配置必须包含 workflow run 标识，避免
并发任务相互发现容器、端口或数据库状态。observability overlay 默认关闭，
不改变被测业务拓扑的 Gateway、frontend、community-app、market、drive 和 IM
路径。

## 错误处理和可诊断性

- 健康检查失败属于环境失败：输出前端响应、Gateway health body、Compose ps
  和相关服务日志。
- 业务 API 返回 `5xx` 或未预期 `4xx` 属于产品/部署失败：保留请求 URL、状态、
  trace、页面截图和视频，并让测试失败。
- 合法的 `403` 只在对应权限测试中允许，不能使用全局宽泛 allowlist。
- 报告必须区分 passed、failed 和 skipped；首轮配置不自动重试业务测试，
  因此不会把偶发失败静默改成 passed。

## 架构约束

所有 backend 修改遵守仓库根目录 `AGENTS.md` 的严格 DDD Tactical Layering：

- Controller 只做 HTTP 绑定、认证提取和 DTO 转换，并调用同域
  ApplicationService。
- ApplicationService 负责编排 domain、repository interface、跨域 `api.*`
  和事件契约。
- Domain 不依赖 Spring、HTTP、infrastructure、mapper/dataobject 或 owner
  `api.*`。
- MyBatis、Redis、OSS、MQ、Spring event 和 deployment 适配器留在
  infrastructure。
- 修改架构边界时补跑并更新
  `backend/community-app/src/test/java/com/nowcoder/community/app/arch` 下的
  ArchUnit guardrails。

## 文档同步

实现完成后同步更新：

- `tests/playwright-single/README.md`：命令、测试集合、CI 行为、数据副作用和
  失败证据。
- `docs/handbook/testing.md`：single E2E 的 smoke/regression 入口、CI 触发方式
  和 artifact 约定。
- 同步更新与现行 IM、Drive、后台接口语义不一致的测试说明；不保留把已修复
  状态描述为 known issue 的文档。

## 完成标准

1. 本地 single 环境执行 `health`、`test:smoke`、`test:regression` 和 `report`
   全部成功。
2. `tests/playwright-single` 中不再存在预期 `503` 或旧 IM `403` 的通过断言，
   也不存在被配置永久忽略的业务 spec。
3. 收藏、IM、后台主体、网盘分享和社区发帖入口均有真实成功断言，并有对应
   的前端/后端测试覆盖。
4. PR smoke workflow 成功，nightly 或手动 regression workflow 成功；失败时
   artifacts 和 Compose 日志可下载。
5. 相关 backend 测试遵守 DDD 边界，架构 guardrails 和受影响模块测试通过。
6. README 与 handbook 已描述最终命令和运行语义，且与实际 workflow/config
   一致。
