# 单机拓扑 Playwright E2E 测试

`tests/playwright-single` 是面向本地 `single` 部署拓扑的独立 Playwright
端到端测试套件。它从浏览器用户的视角访问已经部署的前端 Nginx 和统一
Gateway，而不是直接调用后端服务，也不依赖 `frontend/` 内部的单元测试环境。

该目录用于验证已部署系统的关键可用性、认证、社区、钱包、市场、网盘和后台
访问链路。它不会启动服务、初始化测试账号或自动清理数据；运行前需要准备好
可登录的本地 single 环境。

## 目录说明

```text
tests/playwright-single/
├── README.md                    # 本说明
├── .env.example                 # 可用环境变量及本地默认值示例
├── package.json                 # Playwright 命令入口和开发依赖
├── package-lock.json            # npm 依赖锁定文件
├── playwright.config.ts         # 浏览器、超时、报告、失败产物和测试筛选配置
├── fixtures/
│   ├── accounts.ts               # 本地测试账号、固定用户 ID 与环境变量覆盖方式
│   ├── auth.ts                   # 通过 UI 登录及浏览器 storage state 辅助方法
│   ├── helpers.ts                # URL、健康检查、页面跳转和常用断言辅助方法
│   └── test-data.ts              # 带运行时间戳的测试数据生成器
├── tests/
│   ├── 00-smoke.spec.ts          # 部署可达性和最小登录路径
│   ├── 01-auth.spec.ts           # 认证页面与开发账号登录
│   ├── 02-community.spec.ts      # 社区发帖、互动与个人中心页面
│   ├── 03-wallet.spec.ts         # 测试积分领取和转账
│   ├── 04-market.spec.ts         # 市场卖家、买家、订单和库存流程
│   ├── 05-drive.spec.ts          # 网盘目录和公开分享流程
│   ├── 06-admin.spec.ts          # 权限拦截和后台只读页面
│   └── 07-im.spec.ts             # IM 会话列表流程
├── scripts/
│   ├── health-check.mjs          # 不启动浏览器的前端/Gateway 健康检查
│   └── markdown-report.mjs       # 将 Playwright JSON 结果转换为 Markdown 报告
└── reports/                      # 生成的 JSON 与 Markdown 报告目录
```

`node_modules/`、`.auth/`、`test-results/`、`playwright-report/` 以及
`reports/` 下生成的 JSON/Markdown 均被 Git 忽略。仓库只保留
`reports/.gitkeep` 以固定报告目录。

## 前置条件

从仓库根目录启动完整的单机拓扑：

```bash
./deploy/deployment.sh up --stack single --no-observability
```

默认访问目标如下：

| 目标 | 默认地址 | 用途 |
| --- | --- | --- |
| 前端 | `http://localhost:12881` | 浏览器页面、SPA 路由和静态入口 |
| Gateway | `http://localhost:12880` | `/actuator/health` 及前端业务 API 的统一入口 |

测试依赖 single 环境中已有以下开发账号及固定用户 ID。账号名和密码可通过
环境变量覆盖，但用例中的用户 ID 是固定的；若本地 seed 数据使用不同 ID，涉及
个人页、转账和权限的断言会失败。

| 角色 | 默认用户名 | 默认密码 | 固定用户 ID | 可覆盖的环境变量 |
| --- | --- | --- | --- | --- |
| 普通用户 A | `aaa` | `aaa` | `00000000-0000-7000-8000-000000000001` | `SINGLE_USER_A_USERNAME`、`SINGLE_USER_A_PASSWORD` |
| 普通用户 B | `bbb` | `aaa` | `00000000-0000-7000-8000-000000000002` | `SINGLE_USER_B_USERNAME`、`SINGLE_USER_B_PASSWORD` |
| 管理员 | `admin` | `aaa` | `00000000-0000-7000-8000-000000000003` | `SINGLE_ADMIN_USERNAME`、`SINGLE_ADMIN_PASSWORD` |

`.env.example` 仅列出这些变量和默认地址，测试脚本不会自动加载 `.env` 文件。
请在 shell 或 CI 环境中显式导出变量。例如：

```bash
SINGLE_WEB_BASE_URL=http://localhost:12881 \
SINGLE_API_BASE_URL=http://localhost:12880 \
npm --prefix tests/playwright-single run health
```

## 安装

在仓库根目录执行：

```bash
npm --prefix tests/playwright-single install
npx --prefix tests/playwright-single playwright install chromium
```

第一条命令安装 Node 依赖，第二条命令下载本套件使用的 Chromium 浏览器二进制。
配置只运行 Playwright 的 `Desktop Chrome` 项目。

## 推荐执行顺序

```bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test:regression
npm --prefix tests/playwright-single run report
```

先用 `health` 排除部署、端口和 Gateway 健康问题，再用冒烟测试确认浏览器路径
可用，最后执行完整产品回归。`report` 必须在至少执行过一次 Playwright 测试后
运行，因为它读取 `reports/latest-results.json`。

## 可用命令

| 命令 | 作用 |
| --- | --- |
| `npm --prefix tests/playwright-single run health` | 使用 `fetch` 检查前端首页返回 2xx，检查 Gateway `/actuator/health` 返回 `UP`；不启动浏览器。 |
| `npm --prefix tests/playwright-single run test:smoke` | 只执行 `00-smoke.spec.ts`，快速验证部署可达、匿名访问、受保护路由跳转和登录。 |
| `npm --prefix tests/playwright-single run test:regression` | 执行全部带 `@regression` 标签的产品回归。 |
| `npm --prefix tests/playwright-single run test` | `test:regression` 的别名。 |
| `npm --prefix tests/playwright-single run test:headed` | 以有界面模式运行常规回归，便于本地观察交互。 |
| `npm --prefix tests/playwright-single run report` | 将最近一次 JSON 结果写成带时间戳的 Markdown 报告。 |
| `npm --prefix tests/playwright-single run show-report` | 打开 Playwright HTML 报告 `playwright-report/`。 |

可用的运行时变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SINGLE_WEB_BASE_URL` | `http://localhost:12881` | 前端根地址；末尾的 `/` 会被自动去除。 |
| `SINGLE_API_BASE_URL` | `http://localhost:12880` | Gateway 根地址；用于健康检查和浏览器 API 错误审计。 |
| `PW_WORKERS` | `1` | Playwright worker 数量。默认单 worker，适合共享的本地状态。 |
| `SINGLE_TEST_RUN_ID` | 当前 UTC 时间戳 | 生成帖子、商品和网盘目录等唯一名称的运行标识；可在排查时指定固定值。 |

## 用例覆盖范围

常规用例按文件编号组织，便于阅读和定位。市场测试内部依赖前序步骤创建的商品
和订单，因此使用 `test.describe.serial` 串行运行；不要只挑选其中后续的单个用例
执行。

| 文件 | 验证内容 | 会产生的状态 |
| --- | --- | --- |
| `00-smoke.spec.ts` | 前端首页可访问、Gateway 健康状态为 `UP`、匿名帖子页可打开、匿名访问钱包会跳转登录页、`aaa` 可通过 UI 登录并创建 storage state、登录页可直接访问。 | 会创建 `.auth/aaa.json` 以验证 storage state 写入。 |
| `01-auth.spec.ts` | `aaa`、`bbb`、`admin` 三个账号均可通过 UI 登录和退出；注册页和找回密码页可渲染，空提交能显示校验提示。 | 无预期业务数据写入。 |
| `02-community.spec.ts` | `aaa` 发帖、添加标签、查看详情、点赞、收藏和评论；个人页、关注/粉丝、通知、评论通知和设置页可打开。 | 新帖子、标签关联、点赞、收藏和评论。 |
| `03-wallet.spec.ts` | `aaa` 的钱包页可打开，领取 1 个测试积分成功，并向 `bbb` 转账 1 积分成功。 | 本地验收配额、账户余额和钱包流水发生变化。 |
| `04-market.spec.ts` | `aaa` 发布预存内容的虚拟商品；`bbb` 下单并创建默认收货地址；`aaa` 查看卖单、追加库存并使库存失效。 | 商品、库存、订单、地址及相关余额/状态变化。 |
| `05-drive.spec.ts` | `bbb` 创建、重命名、删除目录；生成带提取码的公开分享链接，刷新后确认分享仍存在并撤销。 | 网盘目录、回收站记录和已撤销分享记录。 |
| `06-admin.spec.ts` | 普通用户访问用户管理会跳转 `403`；管理员可见治理菜单、统计、用户管理、钱包后台和争议裁定入口，并可打开统计和治理页面。 | 无预期业务数据写入。 |
| `07-im.spec.ts` | `bbb` 打开消息页，验证 IM 会话分页接口返回成功并渲染空状态或会话列表。 | 无预期业务数据写入。 |

`fixtures/test-data.ts` 会为会写入系统的数据附加时间戳，例如帖子标题、虚拟
商品、订单库存和网盘目录。这样多次运行通常不会因名称冲突而失败，但不会清空
已有数据。

## 错误审计

每个页面测试都会审计 single API 返回的 4xx/5xx、浏览器 `pageerror` 和应用
`console.error`。除匿名认证探针和普通用户访问后台用户管理时的预期授权结果外，
任何错误都会使测试失败。业务用例只使用成功语义，不通过“预期失败”登记问题。

## 配置、报告与失败证据

`playwright.config.ts` 的关键行为如下：

- 测试目录为 `tests/`，默认超时为 60 秒，普通断言超时为 15 秒。
- 默认单 worker 且关闭完全并行，降低本地共享账号和数据互相干扰的概率。
- 失败时保留 trace、截图和视频，写入 `test-results/`。
- 同时生成控制台列表、`playwright-report/` HTML 报告和
  `reports/latest-results.json` JSON 结果。

`scripts/markdown-report.mjs` 读取最近一次的 JSON 结果，统计通过、失败、跳过
数量，并在 `reports/` 下生成
`single-playwright-report-<ISO 时间戳>.md`。`latest-results.json` 会被下一次
Playwright 执行覆盖，带时间戳的 Markdown 报告不会覆盖之前的报告。

## 数据影响与排查建议

本套件不是无副作用的只读验收。社区、钱包、市场和网盘用例都会写入单机环境，
当前不会自动清理 MySQL、Redis、对象存储或 Elasticsearch。进行可重复验收时，
请使用隔离的本地数据卷或先自行准备干净环境。

若 `health` 失败，先确认 single 拓扑已启动、两个端口未被其他服务占用，以及
Gateway 的 `/actuator/health` 是否为 `UP`。若登录、转账、IM、网盘或后台权限
用例失败，先检查测试账号、密码、角色和固定用户 ID 是否与部署 seed 数据一致。
然后查看 `playwright-report/`、`test-results/` 和生成的 Markdown 报告定位；报告会
保留失败请求、页面错误和控制台错误的具体信息。
