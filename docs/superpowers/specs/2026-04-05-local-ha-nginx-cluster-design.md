# 本地 HA 演练设计：NGINX 入口 + 多副本业务服务 + 原生多节点中间件

## 1. 背景

当前仓库的本地部署主路径是 `docker compose`，默认拓扑是：

- `frontend`
- `community-gateway`
- `community-app`
- `im-core`
- `im-realtime`
- MySQL / Redis / Kafka / Elasticsearch
- `xxl-job-admin`

这一拓扑适合本地单机联调，但不具备“单节点故障后核心业务继续可用”的演练价值。现有 compose 也大量使用：

- 固定 `container_name`
- 单一宿主机端口映射
- 单地址 upstream
- 单节点中间件模式

因此，本设计的目标不是“简单加几个副本”，而是为本仓库新增一套本地 HA 演练拓扑，使业务入口、业务服务、核心中间件、任务控制面都可以在本地以接近生产的方式运行。

## 2. 目标

本轮设计目标如下：

- 使用 `NGINX` 作为本地唯一外部入口
- 让 `community-gateway`、`community-app`、`im-core`、`im-realtime` 支持多副本运行
- 让 MySQL、Redis、Zookeeper、Kafka、Elasticsearch、`xxl-job-admin` 支持原生多节点形态
- 不通过内部统一代理层代理 MySQL / Redis / Kafka / Elasticsearch
- 浏览器和前端只感知单一入口地址
- 当任意单个业务实例或单个中间件节点故障时，核心业务保持可用
- 对 MySQL 主库故障允许人工切主，不要求自动无感写切换

## 3. 非目标

本轮不做以下事情：

- 不把 `frontend` 纳入多副本目标
- 不为 MySQL / Redis / 业务入口新增 `haproxy`、`sentinel`、统一路由层
- 不把 `mailhog`、`mock-data-studio`、一次性 init/bootstrap sidecar 纳入 HA 范围
- 不把 observability profile 里的 Grafana / Loki / Prometheus / Kibana 等本地排障组件纳入 HA 范围
- 不要求最外层 `NGINX` 自身在本地 compose 中也具备无感高可用
- 不在本轮引入 Kubernetes、Ingress、Gateway API 或 Helm 交付物

## 4. 方案对比

### 4.1 方案 A：无入口层，前端/客户端自行 failover

做法：

- 不新增 `NGINX`
- 浏览器端感知多个 `community-gateway` 地址
- 前端或客户端在 gateway 故障时自行切换地址

优点：

- 少一层组件
- compose 拓扑相对简单

缺点：

- 明显偏离生产常见部署方式
- 浏览器和客户端要承担故障切换逻辑
- WebSocket 入口体验差，配置面分裂

结论：

- 不采用

### 4.2 方案 B：`NGINX` 只做最外层入口，内部依赖走原生集群协议

做法：

- `NGINX` 作为浏览器和客户端唯一入口
- `community-gateway` 多副本
- 业务服务到 Redis / Kafka / Elasticsearch / MySQL 使用原生多节点接入

优点：

- 贴近生产常见模型
- 入口职责清晰
- 不用额外内部代理层掩盖真实集群行为

缺点：

- 需要改应用配置模型，从单地址升级为多节点/服务池配置
- 本地资源占用高

结论：

- 采用

### 4.3 方案 C：`NGINX` 同时代理业务入口和内部中间件

做法：

- `NGINX` 既代理浏览器流量，也代理 MySQL / Redis / Kafka / Elasticsearch

优点：

- 应用侧配置简单

缺点：

- 与“不为 MySQL/Redis/业务入口增加统一路由层兜底”的约束冲突
- 中间件真实集群行为被掩盖
- 故障演练价值下降

结论：

- 不采用

## 5. 总体架构

本地 HA 拓扑采用以下结构：

```text
Browser / frontend
        |
        v
     NGINX
   :12880 / :12887
        |
        +--> community-gateway-1
        +--> community-gateway-2
        +--> community-gateway-3

community-gateway(x3)
        |
        +--> community-app(x3)
        +--> im-core(x3)
        +--> im-realtime worker pool(x3)

community-app(x3) / im-core(x3) / im-realtime(x3)
        |
        +--> MySQL cluster
        +--> Redis Cluster
        +--> Kafka cluster + Zookeeper ensemble
        +--> Elasticsearch cluster

Browser / operator
        |
        v
     NGINX
      :12887
        |
        +--> xxl-job-admin-1
        +--> xxl-job-admin-2
```

### 5.1 入口层边界

`NGINX` 是本地唯一外部入口，但只承担以下职责：

- `community-gateway` 的 HTTP / WebSocket 反向代理与负载均衡
- `xxl-job-admin` 的控制台入口
- 对外暴露稳定地址和端口
- upstream 失败时自动摘除和重试

`NGINX` 不承担以下职责：

- MySQL / Redis / Kafka / Elasticsearch 的内部代理
- 业务鉴权语义
- 替代 `community-gateway` 的业务路由逻辑

### 5.2 现实约束

在本地 compose 中，本轮设计不再引入比 `NGINX` 更外层的云 LB、keepalived、VIP 或 Kubernetes Ingress。因此：

- 本地 `NGINX` 本身仍是入口单点
- 本轮能演练的是 `NGINX` 之后的所有业务服务和中间件节点故障
- 不承诺“`localhost:12880` 在 `NGINX` 自身宕机后仍无感可用”

这与生产常见模型不同。生产环境通常还会在 `NGINX` 之上挂云负载均衡或 Ingress。

## 6. 目标拓扑

### 6.1 业务服务

- `community-gateway`：3 副本
- `community-app`：3 副本
- `im-core`：3 副本
- `im-realtime`：3 副本
- `frontend`：保持单实例

### 6.2 中间件与控制面

- MySQL：`1 主 + 2 从`
- Redis：`3 主 + 3 从` 的 Redis Cluster
- Zookeeper：3 节点
- Kafka：3 broker
- Elasticsearch：3 节点
- `xxl-job-admin`：2 副本，共享 `xxl_job` schema

### 6.3 明确保持单实例的组件

- `frontend`
- `mailhog`
- `mock-data-studio`
- `kafka-init`
- `es-init`
- `mock-data-studio-db-bootstrap`
- `community-app-debug-port`
- `im-core-debug-port`
- `im-realtime-debug-port`
- observability profile 里的观测组件

## 7. 配置与连接策略

### 7.1 总体原则

统一采用以下连接策略：

- 浏览器侧只认 `NGINX` 单入口
- 服务间 HTTP / WS 使用服务池或稳定入口配置
- 服务到中间件使用原生多节点连接
- 禁止新的单点 `host:port` 写死进应用运行配置

### 7.2 `community-gateway`

`community-gateway` 调整为：

- 不再直接映射宿主机 `12880`
- 由 `NGINX` 反向代理到多个 gateway 实例
- HTTP upstream 从单一 `uri` 升级为可配置 upstream 列表
- WebSocket worker 列表从当前单 worker 扩展为 3 个 `im-realtime` worker

需要的配置演进：

- `gateway.http.routes[*].uri` 从单 URI 扩展为 upstream pool 表达
- `gateway.ws.proxy.default-worker-uri` 保留作兜底，但主要依赖显式 worker 列表
- `gateway.ws.shard.workers[]` 在 compose 中配置 3 个 worker

### 7.3 `community-app`

`community-app` 调整为：

- MySQL 使用主库写入口配置，不直接写死 `mysql:3306`
- Redis 改为 Redis Cluster 节点列表配置
- Elasticsearch 改为多节点 `uris`
- XXL admin 地址改为通过 `NGINX:12887` 访问的稳定入口
- 本地头像文件暂时继续使用共享 volume 挂载

文件存储策略：

- 一期保留 `user.avatar.storage=local`
- 通过共享 volume 保证多个 `community-app` 副本都能访问同一文件目录
- 同时保留后续切换 `r2` 的演进路径

### 7.4 `im-core`

`im-core` 调整为：

- MySQL 写连接切到主库入口
- Kafka 改为 3 broker bootstrap servers
- consumer group 继续共享，使 3 个实例分摊 command topic 消费

### 7.5 `im-realtime`

`im-realtime` 调整为：

- 作为 3 个显式 worker 实例存在
- gateway 基于 `userId` 稳定路由到具体 worker
- Kafka 使用 3 broker bootstrap servers
- 对 `im.core.base-url` 和 `im.community.base-url` 升级为服务池/稳定服务访问方式
- 保持每个 worker 自己的连接状态和本地房间索引

特别约束：

- 不能让多个 `im-realtime` 副本错误共享同一个 consumer identity
- 不能把“持有本地连接的 worker”和“消费事件的 worker”解耦成错误拓扑
- 必须保持“命中某 worker 的连接，由该 worker 负责本地推送”

### 7.6 `xxl-job-admin`

`xxl-job-admin` 调整为：

- 启动 2 个 admin 实例
- 共享同一个 `xxl_job` schema
- 浏览器入口统一走 `NGINX:12887`
- `community-app` executor 也通过统一入口访问 admin

设计原则：

- 不让应用直接依赖某一个 admin 容器地址
- 不通过客户端自己记多个 admin 地址来做故障切换

## 8. 中间件拓扑约束

### 8.1 MySQL

一期采用：

- `1 主 + 2 从`
- 写流量固定打主库
- 主库故障时允许人工切主
- 从库故障时业务应自动继续

不在本轮承诺：

- 主库故障自动无感切主
- 完整生产级 MySQL operator / orchestrator 能力

### 8.2 Redis

一期采用：

- 6 节点 Redis Cluster：`3 主 + 3 从`
- 应用使用 cluster nodes 配置接入

目标是验证：

- 集群模式下验证码、限流、analytics、帖子热度队列仍可工作
- 任意单主或单从故障时集群仍能服务

### 8.3 Zookeeper + Kafka

一期采用：

- 3 个 Zookeeper 节点
- 3 个 Kafka broker
- topic 的 replication factor 不再固定为 `1`

目标是验证：

- 任意 1 个 broker 故障时 IM command/event 链路继续可用
- 任意 1 个 Zookeeper 节点故障时集群仍保持 quorum

### 8.4 Elasticsearch

一期采用：

- 3 节点 Elasticsearch 集群
- `community-app` 通过多节点 `uris` 接入

目标是验证：

- 任意 1 个节点故障时搜索查询仍可用
- reindex 可允许降级，但不能全链路失效

## 9. 故障切换与验收口径

### 9.1 `NGINX`

`NGINX` 本身不纳入一期 HA 范围，但必须满足：

- 任意一个 `community-gateway` 副本故障时，入口 `:12880` 继续可用
- 任意一个 `xxl-job-admin` 副本故障时，入口 `:12887` 继续可用

### 9.2 `community-gateway`

验收口径：

- 任意 1 个 gateway 实例故障后，`/api/**`、`/files/**` 继续可用
- `/ws/im` 允许已有连接断开，但客户端重连后必须恢复可用

### 9.3 `community-app`

验收口径：

- 任意 1 个实例故障后，注册、登录、发帖、评论、搜索、文件访问继续可用
- outbox / post-score / XXL executor 不出现不可控双跑

### 9.4 `im-core`

验收口径：

- 任意 1 个实例故障后，IM command 消费和消息持久化继续进行

### 9.5 `im-realtime`

验收口径：

- 任意 1 个 worker 故障后，其他 worker 上的在线用户不受影响
- 命中故障 worker 的连接允许断线重连
- 重连后仍能继续完成发送、接收和房间增量拉取

### 9.6 Redis

验收口径：

- 任意 1 个 master 或 replica 故障后，Redis 相关能力继续可用
- 可接受短暂抖动，不可接受整体不可用

### 9.7 Kafka + Zookeeper

验收口径：

- 任意 1 个 broker 故障后，私信/群聊链路继续闭环
- 任意 1 个 Zookeeper 节点故障后，集群仍可用

### 9.8 Elasticsearch

验收口径：

- 任意 1 个节点故障后，搜索查询继续可用

### 9.9 MySQL

验收口径分两类：

- 从库故障：业务自动继续
- 主库故障：允许写流量中断，人工切主后恢复

### 9.10 `xxl-job-admin`

验收口径：

- 任意 1 个 admin 实例故障后，控制台继续可访问
- `community-app` executor 继续注册和执行任务

## 10. 代码与部署改造范围

本设计覆盖以下改造面：

### 10.1 部署层

- 新增 `NGINX` 配置与容器
- 重写 `deploy/docker-compose.yml` 的拓扑
- 去除不适合多副本的固定 `container_name`
- 为多副本业务服务和多节点中间件重新设计网络、卷和启动顺序
- 为 `NGINX` 维护稳定外部端口

### 10.2 应用配置层

- 将 `community-gateway` HTTP upstream 改为多实例配置模型
- 将 gateway worker registry 扩展为多 worker compose 配置
- 将 `community-app` / `im-core` / `im-realtime` 的中间件连接改为多节点配置模型
- 将 `xxl-job-admin` 的访问方式改为稳定入口

### 10.3 文档层

- 更新 `deploy/README.md`
- 更新 `docs/DEPLOYMENT.md`
- 更新 `docs/ARCHITECTURE.md`
- 必要时补充本地 HA 演练说明、故障注入步骤和资源要求

## 11. 风险与取舍

### 11.1 资源成本

这是一个重型本地演练拓扑：

- 容器数量显著增加
- 内存和 CPU 消耗显著高于现有 compose
- 本地启动和恢复时间也会变长

这是已接受取舍，用户已明确接受更高资源预算。

### 11.2 入口单点

本地 `NGINX` 仍是入口单点。这不是遗漏，而是显式范围控制：

- 本轮目标是验证 `NGINX` 之后的多副本和多节点行为
- 生产环境的最外层 HA 由云 LB / Ingress / 更外层入口解决

### 11.3 MySQL 主故障

本轮不追求 MySQL 主故障自动无感切换，因此：

- 文档必须明确人工切主流程
- 验收中必须把“从库故障自动继续”和“主库故障人工恢复”区分开

### 11.4 配置模型复杂化

从单地址配置升级为多节点/服务池配置后：

- 配置项数量会增加
- 应用启动校验要加强
- 文档必须同步说明默认值、示例和本地演练口径

## 12. 分阶段实施建议

虽然目标拓扑是一次性定义的，但实施应按下面顺序推进：

1. 引入 `NGINX` 外层入口，先跑通多副本 `community-gateway`
2. 让 `community-app`、`im-core`、`im-realtime` 支持多副本和多实例 upstream
3. 升级 Redis / Kafka / Elasticsearch 为原生多节点拓扑
4. 升级 MySQL 为 `1 主 + 2 从` 并补人工切主演练流程
5. 升级 `xxl-job-admin` 为多实例控制面
6. 补全文档、故障注入步骤和验收命令

## 13. 最终决策

本轮本地 HA 设计采用以下最终决策：

- 采用 `NGINX` 作为最外层本地入口
- 采用 `community-gateway`、`community-app`、`im-core`、`im-realtime` 多副本拓扑
- 采用 MySQL / Redis / Zookeeper / Kafka / Elasticsearch / `xxl-job-admin` 多节点拓扑
- 不为内部依赖增加统一代理层
- 浏览器和前端只认单一入口地址
- MySQL 主故障接受人工切主
- 最外层 `NGINX` 自身不纳入一期 HA 范围
