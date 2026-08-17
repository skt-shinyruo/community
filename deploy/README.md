# deploy/

本目录维护三个相互隔离的本地 Stack：

- `infra`：供宿主机后端使用的单节点基础设施
- `single`：基础设施、六个后端 deployable 和前端组成的完整单机 Stack
- `cluster`：用于多实例和集群路径验证的完整 Stack

唯一支持的操作入口是 `./deploy/deployment.sh`。以下命令默认从仓库根目录执行。

部署模型、隔离规则和启动顺序以[本地开发手册](../docs/handbook/local-development.md)为准；生产式运行、排障和观测步骤见[运维手册](../docs/handbook/operations.md)。

## 常用命令

```bash
# 基础设施供宿主机后端使用
./deploy/deployment.sh up --stack infra
./deploy/deployment.sh render-backend-env --stack infra

# 完整 Stack
./deploy/deployment.sh up --stack single
./deploy/deployment.sh up --stack cluster

# 一次性生成/删除本地演示数据
./deploy/deployment.sh mock-data --stack single -- generate --seed demo
./deploy/deployment.sh mock-data --stack single -- delete <batch-id>

# 检查或渲染
./deploy/deployment.sh ps --stack single
./deploy/deployment.sh logs --stack cluster community-gateway-1
./deploy/deployment.sh config --stack single --env-file deploy/stacks/single/.env.example

# 契约测试
./deploy/tests/run-contracts.sh
```

`deployment.sh` 只接受 `--stack infra|single|cluster`。Stack manifest `stacks/*/compose.yml` 是 Compose 组合的唯一事实源。

## 环境文件

首次运行前复制目标 Stack 的模板：

```bash
cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env
```

只需创建实际使用的文件，且不得提交真实 `.env`。脚本不会 `source` 环境文件，只读取受支持的键；优先级为当前 shell、Stack env、内置默认值。

默认 Compose project name 分别是 `community-infra`、`community-single`、`community-cluster`。三个 Stack 使用独立的 volume namespace、网络和宿主机端口，可以并存。自定义 project 必须同时提供独立的 volume namespace、网段、静态地址和宿主机端口，完整约束见[本地开发手册的环境文件章节](../docs/handbook/local-development.md#环境文件)。

## 快速开始

### infra

```bash
./deploy/deployment.sh up --stack infra
./deploy/deployment.sh render-backend-env --stack infra
```

infra 不启动前端或容器化后端。依赖只绑定 `127.0.0.1`；生成的六个后端 env、端口和启动顺序见[宿主机启动后端](../docs/handbook/local-development.md#宿主机启动后端)。

### single

```bash
./deploy/deployment.sh up --stack single
```

默认浏览器入口：

- 前端：`http://localhost:12881`
- Gateway：`http://localhost:12880`
- IM session：`POST http://localhost:12880/api/im/sessions`
- IM WebSocket：session 返回的 `wsUrl`，本地默认 `ws://localhost:12880/ws/im`
- Nacos：`http://localhost:18848/nacos`
- MailHog：`http://localhost:8025`

### cluster

```bash
./deploy/deployment.sh up --stack cluster
```

cluster 默认使用独立的 `13880`、`13881`、`38848` 等端口。副本布局和启动依赖见[本地运行拓扑](../docs/handbook/architecture.md#本地运行拓扑)。

## 观测层

| Stack | 默认 | `--observability` | `--no-observability` |
| --- | --- | --- | --- |
| `infra` | 关闭 | 不支持 | 关闭 |
| `single` | 关闭 | 开启 | 关闭 |
| `cluster` | 开启 | 开启 | 关闭 |

例如，为 single 开启观测层：

```bash
./deploy/deployment.sh up --stack single --observability
```

运行 smoke：

```bash
./deploy/tests/smoke/observability_smoke.sh
```

观测信号契约以[观测手册](../docs/handbook/observability.md)为准；端口和查询见[运维手册](../docs/handbook/operations.md#observability)，短时深度诊断见[JVM 短时诊断](../docs/handbook/operations.md#jvm-短时诊断)。

## 停止与清理

```bash
./deploy/deployment.sh down --stack single
./deploy/deployment.sh reset-mysql --stack single
./deploy/deployment.sh down --stack cluster -- -v
```

`reset-mysql` 会先停止目标 Stack，只删除显式命名的 MySQL volumes；cluster 会删除 primary 和两个 replica volumes。`down ... -- -v` 会删除目标 Stack 的全部 Compose volumes。两者都不可恢复，执行前必须确认 Stack 和 project。

业务 schema 与开发期重建流程见[数据与存储手册](../docs/handbook/data-and-storage.md)及[运维手册](../docs/handbook/operations.md#business-mysql-schema)。

## 目录与验证

```text
deploy/
  stacks/{infra,single,cluster}/  # Stack manifest、env 模板
  compose/                        # 基础设施、运行时、edge 和 overlay 片段
  images/                         # 生产镜像构建输入
  database/                       # 业务 schema、账号初始化和开发 seed
  config/                         # Nacos、Nginx、Garage 配置
  observability/                  # Collector、Kibana 和静态信号契约
  scripts/                        # 基础设施 bootstrap 脚本
  tests/{contracts,smoke}/        # 静态契约与运行态 smoke
```

测试分组和执行条件见[部署测试 README](tests/README.md)。镜像约束可单独验证：

```bash
./deploy/tests/contracts/images/production_image_contract.sh
```
