# 本地部署设计：将 `Nacos` 从单节点切换为真实三节点集群

## 1. 背景

当前本地 HA 演练栈中的 `Nacos` 仍是单节点：

- [compose.infra.nacos.yml](/home/feng/code/project/community/deploy/compose.infra.nacos.yml) 只有一个 `nacos` service
- 运行模式是 `MODE=standalone`
- 对宿主机暴露单个检查口 `127.0.0.1:${NACOS_HOST_PORT:-18848}:8848`
- [compose.runtime.yml](/home/feng/code/project/community/deploy/compose.runtime.yml) 和 [deploy/.env.example](/home/feng/code/project/community/deploy/.env.example) 的默认地址都是 `nacos:8848`
- operator 文档仍明确描述“单节点 `Nacos` 注册中心”

这与当前其余本地 HA 栈的方向不一致：

- `community-gateway`、`community-app`、`im-core`、`im-realtime` 都已经是多副本
- MySQL、Redis、Kafka、Elasticsearch 也都已经切换为多节点形态
- 但服务发现平面仍存在单点

用户已经明确确认以下方向：

- 不是简单增加多个 `Nacos` 容器
- 要做真实的 `Nacos` 集群
- 集群存储复用现有 `mysql-primary`
- 在现有主库中新增 `nacos` schema
- 业务客户端默认改为 3 个 `Nacos` 地址，而不是继续默认 `nacos:8848`

因此，本设计的目标不是做“多实例伪集群”，而是在保持本地 operator 入口不变的前提下，把当前单节点 `Nacos` 演练环境升级成真实可用的三节点 `Nacos` 集群，并补齐对已有 MySQL 数据卷的升级路径。

## 2. 目标

本轮设计目标如下：

- 将 [compose.infra.nacos.yml](/home/feng/code/project/community/deploy/compose.infra.nacos.yml) 从单节点 `Nacos` 改为三节点真实集群
- 集群持久化复用 [compose.infra.mysql.yml](/home/feng/code/project/community/deploy/compose.infra.mysql.yml) 中的 `mysql-primary`
- 在 `mysql-primary` 中新增 `nacos` schema 与对应授权
- 将 runtime 默认 `NACOS_SERVER_ADDR` 改为 `nacos-1:8848,nacos-2:8848,nacos-3:8848`
- 为已有 `mysql_primary_data` 数据卷提供幂等升级路径，不要求用户删卷重建
- 保持 `make up` / `make up-debug` / `make up-obs` / `make up-elastic` / `make up-elastic-json` 不变

## 3. 非目标

本轮不做以下事情：

- 不引入额外的 `Nacos` 前置负载均衡器或 VIP service
- 不开启 `Nacos` 认证、鉴权或 TLS
- 不单独新增 `Nacos` 专用 MySQL 实例
- 不改业务应用中的 `Nacos` 客户端实现代码
- 不改 `Makefile` target 名称
- 不顺手调整其他基础设施拓扑

## 4. 方案对比

### 4.1 方案 A：继续使用单节点 `Nacos`

做法：

- 保持当前 `nacos` 单 service
- 仅更新文档措辞或未来再处理 HA

优点：

- 改动最小
- 不需要改 runtime 默认地址

缺点：

- 服务发现平面继续是单点
- 与当前本地 HA 演练目标不一致
- 用户已经明确要求真实集群

结论：

- 不采用

### 4.2 方案 B：三节点 `Nacos` 集群，但在前面增加一个稳定代理地址

做法：

- 新增 `nacos-1`、`nacos-2`、`nacos-3`
- 额外增加一个 `nacos` 代理层，对内继续暴露 `nacos:8848`
- 业务容器仍只连代理地址

优点：

- 对运行时默认地址改动较少
- 可保留 `nacos:8848` 兼容路径

缺点：

- 引入额外代理层，排障更复杂
- 客户端并没有真正持有多地址 failover 能力
- 多了一层组件和健康路径，不符合当前“最少新增抽象”的本地部署原则

结论：

- 不采用

### 4.3 方案 C：三节点 `Nacos` 集群，业务客户端默认直连三地址

做法：

- 将单节点 `nacos` 改为 `nacos-1`、`nacos-2`、`nacos-3`
- 每个节点使用 `MODE=cluster`
- 集群共享 `mysql-primary` 中的 `nacos` schema
- runtime 默认 `NACOS_SERVER_ADDR` 改为三地址列表
- 通过一次性 bootstrap service 为已有 MySQL 数据卷补建 `nacos` schema 与表

优点：

- 与真实集群模型一致
- 客户端显式持有多地址 failover 能力
- 不引入额外代理层
- 升级路径可同时覆盖全新环境与已有数据卷环境

缺点：

- 需要同步改 compose 默认值、文档与验收步骤
- `compose.infra.nacos.yml` 的复杂度会高于当前单节点文件

结论：

- 采用

## 5. 总体设计

### 5.1 设计原则

本次切换遵循以下原则：

- 保持 operator 命令不变
- 明确做真实 `Nacos` 集群，而不是“多个 standalone 容器”
- 默认行为优先反映真实拓扑，而不是保留历史单地址假象
- 新环境和已有环境都必须能落地
- 所有升级动作必须幂等，可重复执行

### 5.2 目标拓扑

`deploy/compose.infra.nacos.yml` 最终承载以下服务：

- `nacos-1`
- `nacos-2`
- `nacos-3`
- `nacos-db-bootstrap`

角色划分：

- `nacos-1..3`：真实 `Nacos` 集群节点
- `nacos-db-bootstrap`：一次性数据库准备 service，负责确保 `nacos` schema、账号授权和官方表结构存在

### 5.3 集群连接模型

`Nacos` 集群采用以下连接模型：

- 运行模式：`MODE=cluster`
- 节点列表：`nacos-1:8848 nacos-2:8848 nacos-3:8848`
- 持久化数据库：
  - host：`mysql-primary`
  - schema：`nacos`
- 业务客户端默认地址：
  - `nacos-1:8848,nacos-2:8848,nacos-3:8848`

宿主机检查入口继续保留单个映射：

- `127.0.0.1:${NACOS_HOST_PORT:-18848}:8848`

该端口只作为本地 operator 检查入口，不被定义为集群 VIP。设计上允许它直接绑定到 `nacos-1`，因为业务客户端的 HA 依赖的是容器内三地址列表，而不是宿主机检查口。

### 5.4 客户端地址策略

以下默认值统一切换为三地址列表：

- [deploy/.env.example](/home/feng/code/project/community/deploy/.env.example) 中的 `NACOS_SERVER_ADDR`
- [compose.runtime.yml](/home/feng/code/project/community/deploy/compose.runtime.yml) 中所有 `NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-...}`
- [compose.runtime.yml](/home/feng/code/project/community/deploy/compose.runtime.yml) 中所有 `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=${NACOS_SERVER_ADDR:-...}`

设计要求：

- 不再把 `nacos:8848` 作为默认值
- 允许用户通过 `deploy/.env` 手工覆盖 `NACOS_SERVER_ADDR`
- 应用资源文件中的 fallback `localhost:8848` 保持不变，因为那是非 compose 场景下的本地开发兜底，不属于本轮 operator 改造范围

## 6. 数据库设计与升级路径

### 6.1 数据库存储选择

本次 `Nacos` 集群复用现有 `mysql-primary`，不新建 MySQL 实例。

选择原因：

- 用户已明确要求复用现有主库
- 当前本地部署已经有稳定的 MySQL 初始化与 replication bootstrap 链路
- 对本地演练环境而言，额外维护一套 `Nacos` 专用 MySQL 收益不足

### 6.2 schema 与账号

在 [001_create_databases.sh](/home/feng/code/project/community/deploy/mysql-init/001_create_databases.sh) 中新增：

- `nacos` database
- `NACOS_MYSQL_USER`
- `NACOS_MYSQL_PASSWORD`
- 对 `nacos.*` 的运行时授权

默认约定：

- schema：`nacos`
- host：`mysql-primary`
- 用户名与密码在 `deploy/.env.example` 中给出显式默认值
- `Nacos` 运行时账号权限明确限定为：
  - `select`
  - `insert`
  - `update`
  - `delete`

### 6.3 官方 schema 管理

仓库内 vendoring 一份与 `nacos/nacos-server:v2.3.2-slim` 对应的官方 MySQL schema，建议放在：

- [030_nacos_schema.sql](/home/feng/code/project/community/deploy/mysql-init/030_nacos_schema.sql)

设计要求：

- 不在运行时在线下载 SQL
- 版本与当前 `Nacos` 镜像固定绑定
- schema 文件作为仓库内容可审计、可 review、可随镜像升级一起维护

### 6.4 已有数据卷升级路径

必须显式支持已有 `mysql_primary_data` 数据卷。

原因：

- `/docker-entrypoint-initdb.d` 只在 MySQL 数据卷首次初始化时执行
- 如果仅修改 [001_create_databases.sh](/home/feng/code/project/community/deploy/mysql-init/001_create_databases.sh) 和新增 SQL 文件，已有环境不会自动拿到 `nacos` schema

因此新增 `nacos-db-bootstrap`，职责如下：

- 等待 `mysql-primary` healthy
- 使用 `MYSQL_ROOT_PASSWORD` 幂等执行 `create database if not exists nacos`
- 使用 `MYSQL_ROOT_PASSWORD` 幂等执行 `create user if not exists ...` 与 `grant ...`
- 使用 root 连接导入 `030_nacos_schema.sql`
- 可重复执行，不要求人工预清理

`nacos-1..3` 必须依赖 `nacos-db-bootstrap` 成功后再启动。

## 7. Compose 层设计

### 7.1 `deploy/compose.infra.nacos.yml`

该文件从单 service 演进为集群文件：

- 删除 `nacos`
- 新增 `nacos-1`
- 新增 `nacos-2`
- 新增 `nacos-3`
- 新增 `nacos-db-bootstrap`

节点共性：

- 相同镜像版本：`nacos/nacos-server:v2.3.2-slim`
- 相同集群模式：`MODE=cluster`
- 相同共享 MySQL 存储配置
- 相同 JVM 与内存预算

节点差异：

- `hostname`
- 节点名
- 宿主机端口映射仅由 `nacos-1` 承担

### 7.2 `deploy/compose.runtime.yml`

runtime 侧需要做两类调整：

- 默认 `NACOS_SERVER_ADDR` 切换为三地址列表
- `depends_on` 从单个 `nacos` 调整为集群相关依赖

依赖策略建议如下：

- 各业务 service 依赖 `nacos-db-bootstrap` 成功
- 至少依赖 `nacos-1` started

设计上不要求每个业务 service 同时 `depends_on` 三个 `Nacos` 节点都 started，因为客户端真正的高可用来自多地址列表，而不是 Compose 的串行启动语义。

### 7.3 `deploy/compose.yml`

本轮原则上不新增 `Nacos` 专用 named volume。

原因：

- 当前设计使用 MySQL 持久化 `Nacos` 元数据
- 若容器镜像本身存在少量临时本地状态，可继续采用容器默认行为
- 在没有明确持久化收益的前提下，不引入额外卷以免扩大运维面

## 8. 文档与 operator 口径

以下文档需要统一从“单节点 `Nacos`”切换为“3 节点 `Nacos` 集群”：

- [deploy/README.md](/home/feng/code/project/community/deploy/README.md)
- [docs/DEPLOYMENT.md](/home/feng/code/project/community/docs/DEPLOYMENT.md)
- [docs/ARCHITECTURE.md](/home/feng/code/project/community/docs/ARCHITECTURE.md)
- [docs/LOCAL_HA.md](/home/feng/code/project/community/docs/LOCAL_HA.md)

文档需要同步更新的内容包括：

- 文件职责说明
- 拓扑摘要
- 本地检查入口说明
- `Nacos` 注册检查命令
- 对 `NACOS_SERVER_ADDR` 的默认值描述

operator 入口保持不变：

- `make up`
- `make up-debug`
- `make up-obs`
- `make up-elastic`
- `make up-elastic-json`

## 9. 验证策略

### 9.1 配置渲染验证

至少执行以下命令并要求 exit code 为 `0`：

- `make config`
- `make config-debug`
- `make config-obs`
- `make config-elastic`
- `make config-elastic-json`

### 9.2 静态结构验证

需要额外确认：

- `docker compose ... config --services` 中包含：
  - `nacos-1`
  - `nacos-2`
  - `nacos-3`
  - `nacos-db-bootstrap`
- operator 文档与 `.env.example` 中不再把“单节点 `Nacos`”作为默认事实
- compose 默认值中不再残留 `nacos:8848` 作为 operator 主路径默认地址

### 9.3 运行时验证

若本地资源允许，至少验证以下路径：

- `http://localhost:18848/nacos`
- `http://localhost:18848/nacos/v1/ns/instance/list?serviceName=community-app`
- `http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-core`
- `http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker`

期望：

- `Nacos` 控制台/HTTP API 可访问
- 三类服务都能在注册中心中看到多实例注册结果

## 10. 风险与取舍

### 10.1 宿主机检查口不是 HA VIP

保留单个 `localhost:18848` 入口的代价是：

- 本地检查端口只代表其中一个节点
- 它不是 `Nacos` 集群的逻辑 VIP

这是有意为之，因为业务客户端已经直接使用三地址列表，不再依赖宿主机检查口承载高可用语义。

### 10.2 bootstrap service 增加了 infra 启动步骤

新增 `nacos-db-bootstrap` 会让 `compose.infra.nacos.yml` 多一个一次性服务，但这是支持已有 MySQL 数据卷升级的必要代价。相比要求用户删卷或手工执行 SQL，这个代价更可控。

### 10.3 文档与默认值必须同时迁移

如果只改 compose，不同步文档和 `.env.example`，仓库会出现：

- 实际集群拓扑已经是三节点
- 但 operator 文档仍暗示单节点
- 业务默认地址仍停留在 `nacos:8848`

这会造成明显的认知漂移，因此本轮必须把配置、文档和验收说明一并迁移。

## 11. 与现有 spec 的关系

本设计建立在以下既有决策之上：

- [2026-04-06-deploy-compose-f-split-design.md](/home/feng/code/project/community/docs/superpowers/specs/2026-04-06-deploy-compose-f-split-design.md)
- [2026-04-07-deploy-compose-infra-split-design.md](/home/feng/code/project/community/docs/superpowers/specs/2026-04-07-deploy-compose-infra-split-design.md)

本稿：

- 保留 layered compose + `Makefile` 的 operator 模型
- 保留当前 infra 文件拆分边界
- 仅替换其中关于 `compose.infra.nacos.yml` 的单节点设计

若本文与旧 spec 中关于 `Nacos` 的描述冲突，以本文为准。

## 12. 最终决策

本轮最终决策如下：

- 将 `deploy/compose.infra.nacos.yml` 从单节点 `Nacos` 改为三节点真实集群
- 复用 `mysql-primary`，新增 `nacos` schema
- 将 runtime 默认 `NACOS_SERVER_ADDR` 改为三地址列表
- 新增 vendored 官方 `Nacos` MySQL schema 文件
- 新增 `nacos-db-bootstrap` 处理新环境与已有数据卷的统一升级路径
- 保持 operator 命令名不变
