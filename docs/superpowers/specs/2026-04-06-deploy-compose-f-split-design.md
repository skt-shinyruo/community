# 本地部署设计：基于 `-f` overlay 的 Docker Compose 分层拆分

## 1. 背景

当前仓库的本地部署主路径集中在 [deploy/docker-compose.yml](/home/feng/code/project/community/deploy/docker-compose.yml)：

- 单文件承载基础设施、业务运行时、bootstrap sidecar、debug 端口暴露、两套 observability
- 主文件已经增长到 50 个 service，review、排障和局部修改都需要在同一文件内来回跳转
- `community-app`、`community-gateway`、`im-core`、`im-realtime` 各自 3 副本，存在大段重复配置
- 文档主路径虽然只有一条命令，但维护者已经需要同时理解 `profiles`、override 和大文件内分段

用户已经明确确认以下方向：

- 继续采用 Docker Compose
- 不把拆分主方案建立在 `include` 上
- 采用显式 `-f` 文件组合
- 对使用者继续保留简单、稳定的主命令入口

因此，本设计的目标不是改变本地拓扑，而是把当前“过重的单文件 compose”重构为“单入口 + 分域子文件 + 可选 overlay”的可维护结构。

## 2. 目标

本轮设计目标如下：

- 将当前 `deploy/docker-compose.yml` 拆分为职责清晰的多个 compose 文件
- 对使用者继续保留简单、可复制粘贴的启动入口
- 让基础设施、业务运行时、debug、observability 的修改边界清晰可 review
- 消除当前单文件中最显著的重复配置
- 保持现有本地 HA 拓扑、端口模型、网络行为和命名习惯不变
- 为后续新增 `dev-lite` 或其他场景 overlay 预留稳定扩展点

## 3. 非目标

本轮不做以下事情：

- 不改变当前本地 HA 拓扑本身
- 不修改现有 service 名称
- 不把 `community-app-1..3` 等副本改造成 `docker compose up --scale`
- 不引入 Kubernetes、Helm、Tilt、Dev Containers 或其他本地编排体系
- 不在本轮顺手重构业务应用配置语义
- 不把 `include` 作为主装配机制

## 4. 方案对比

### 4.1 方案 A：继续维持单一 `docker-compose.yml`

做法：

- 保持当前单文件
- 仅在文件内增加注释和少量 anchor 去重

优点：

- 改动最小
- 不影响现有命令和文档

缺点：

- 文件尺寸和认知负担继续增长
- 基础设施、运行时、观测配置仍然耦合在一起
- 后续新增场景时仍会继续堆大文件

结论：

- 不采用

### 4.2 方案 B：采用 `include` 作为主拆分机制

做法：

- 由一个主 compose 文件 `include` 多个子 compose 文件
- 试图通过 `include` 完成基础装配和可选层组合

优点：

- 模块化表达看起来更直接
- 对“固定组合的子域复用”有一定吸引力

缺点：

- 当前仓库不只是“装配模块”，还存在明确的 override 需求，例如 JSON logs 场景
- `include` 更适合纳入子模型，不适合作为“覆盖已有服务字段”的主手段
- 维护者仍需要同时理解 `include` 与 overlay 规则，收益不足以抵消额外复杂度

结论：

- 不作为主方案采用

### 4.3 方案 C：采用显式 `-f` overlay 作为主拆分机制

做法：

- 将 always-on 栈拆为基础入口文件、`infra` 文件、`runtime` 文件
- 将 `debug`、`observability`、`observability-elastic`、`json-logs` 独立成 overlay 文件
- 通过统一脚本或 `Makefile` 对外暴露简洁命令

优点：

- 与 Docker Compose 当前主流使用习惯一致
- 对“基础层 + 可选层 + 字段覆盖层”表达清晰
- 对 review、排障和局部改动最友好
- 可以平滑替换当前 `profiles` + 单文件的认知模型

缺点：

- 需要同步更新文档和命令入口
- 如果不提供统一别名，使用者会看到较长的 `docker compose -f ...` 命令

结论：

- 采用

## 5. 总体设计

### 5.1 设计原则

本次拆分遵循以下原则：

- 对使用者保持单入口，对维护者提供分层结构
- 基础设施与业务运行时分离
- 可选能力采用显式 overlay，而不是继续把所有东西塞进同一文件
- 所有 named volume 保持集中定义
- service 名、网络别名、对外端口和容器依赖关系保持现状

### 5.2 目标文件结构

`deploy/` 目录调整为以下结构：

```text
deploy/
  compose.yml
  compose.infra.yml
  compose.runtime.yml
  compose.debug.yml
  compose.observability.yml
  compose.observability-elastic.yml
  compose.json-logs.override.yml
  Dockerfile.backend-service
  Dockerfile.frontend
  mysql/
  mysql-init/
  nginx/
  observability/
  observability-elastic/
  scripts/
  README.md
```

### 5.3 文件职责

#### `deploy/compose.yml`

只承载以下内容：

- `name: community`
- 全部共享 `volumes:`
- 必要的顶层注释

该文件尽量不承载具体 service 定义。

#### `deploy/compose.infra.yml`

承载所有基础设施与一次性基础 bootstrap：

- `mysql-primary`
- `mysql-replica-1`
- `mysql-replica-2`
- `mysql-replication-bootstrap`
- `redis-1..6`
- `redis-cluster-bootstrap`
- `kafka-1..3`
- `kafka-init`
- `elasticsearch-1..3`
- `es-init`
- `nacos`
- `xxl-job-admin-1..2`
- `mailhog`
- `mock-data-studio-db-bootstrap`

#### `deploy/compose.runtime.yml`

承载业务运行时与外部入口：

- `frontend`
- `nginx`
- `community-app-1..3`
- `community-gateway-1..3`
- `im-core-1..3`
- `im-realtime-1..3`
- `mock-data-studio`

#### `deploy/compose.debug.yml`

只承载 localhost 调试端口 sidecar：

- `community-app-debug-port`
- `im-core-debug-port`
- `im-realtime-debug-port`

#### `deploy/compose.observability.yml`

只承载现有 Prometheus / Loki / Grafana 体系：

- `prometheus`
- `alertmanager`
- `loki`
- `promtail`
- `grafana`

#### `deploy/compose.observability-elastic.yml`

只承载 Elastic 观测侧增强：

- `elasticsearch-observability-port`
- `kibana`
- `observability-gateway-edot-collector`

#### `deploy/compose.json-logs.override.yml`

只承载现有 backend 运行 profile 的字段覆盖：

- `community-app-1..3`
- `community-gateway-1..3`
- `im-core-1..3`
- `im-realtime-1..3`

该文件只覆盖 `environment` 中与 JSON logs 相关的字段，不承载新增服务。

## 6. 边界规则

### 6.1 允许的依赖方向

- `runtime` 可以依赖 `infra`
- `debug` 可以依赖 `runtime`
- `observability` 和 `observability-elastic` 可以依赖 `runtime` 与共享 volume
- `infra` 不反向依赖 `runtime`

### 6.2 不可拆散的服务组

以下服务组必须保持在同一 compose 文件中：

- MySQL 主从与 replication bootstrap
- Redis 6 节点与 cluster bootstrap
- Kafka brokers 与 topic bootstrap
- Elasticsearch 节点与 `es-init`

### 6.3 命名与兼容性规则

- 当前 service 名保持不变
- 当前命名卷保持不变
- 当前对外端口保持不变
- 当前网络别名保持不变
- 当前 `depends_on` 语义保持不变

本次拆分的目标是整理结构，不是顺带改 topology。

## 7. 文件内去重策略

`compose.runtime.yml` 内需要显式抽取以下公共模板：

- `x-community-app-base`
- `x-community-gateway-base`
- `x-im-core-base`
- `x-im-realtime-base`

原则如下：

- 公共模板只抽“副本间真正共享的字段”
- 单实例差异保留在具体 service 下，例如日志文件名、worker id、executor address
- 不为了抽象而抽象，避免产生难以追踪的多层 anchor 继承链

`compose.infra.yml` 中可对 MySQL replica、Kafka broker、Elasticsearch node 做有限度 anchor 抽取，但优先保证可读性。

## 8. 命令入口设计

### 8.1 默认命令模型

基础命令固定组合：

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env
```

默认启动命令：

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d --build
```

### 8.2 可选 overlay

在默认组合之上增加：

- debug：追加 `-f deploy/compose.debug.yml`
- Loki/Grafana 观测：追加 `-f deploy/compose.observability.yml`
- Elastic 观测：追加 `-f deploy/compose.observability-elastic.yml`
- Elastic + stdout JSON：在 Elastic 观测基础上再追加 `-f deploy/compose.json-logs.override.yml`

### 8.3 对外别名

为避免把长命令暴露给所有使用者，需要提供一层稳定别名。推荐优先级如下：

1. `Makefile`
2. `deploy/scripts/*.sh`
3. 团队统一 shell alias

推荐至少提供以下命令：

- `make up`
- `make up-debug`
- `make up-obs`
- `make up-elastic`
- `make up-elastic-json`
- `make down`
- `make ps`
- `make logs`

## 9. 为什么不把 `include` 作为主方案

本仓库当前需要同时支持两类能力：

- 固定基础层装配
- 对既有服务进行字段覆盖

`include` 对第一类能力有帮助，但无法替代第二类能力的主路径。当前仓库已经存在 JSON logs 这类覆盖诉求，因此如果主方案改为 `include`，最终仍要并存 overlay 机制。这样不会减少认知负担，反而会让维护者同时理解两套装配模型。

因此，本设计显式选择：

- 固定装配使用 `-f` 组合
- 字段覆盖也继续使用 `-f` overlay
- 不引入额外的主装配语义

## 10. 已知问题与修正要求

当前 [deploy/observability-elastic/docker-compose.override.yml](/home/feng/code/project/community/deploy/observability-elastic/docker-compose.override.yml) 存在 service 名不匹配问题：

- 文件中使用了 `community-app`
- `community-gateway`
- `im-core`
- `im-realtime`

但主 compose 中的真实 service 名是：

- `community-app-1..3`
- `community-gateway-1..3`
- `im-core-1..3`
- `im-realtime-1..3`

因此，本次实施必须同步修正该 override 逻辑，避免 compose 在合并时把覆盖目标误判为新服务。

## 11. 文档与运维迁移

以下文档需要同步更新：

- [deploy/README.md](/home/feng/code/project/community/deploy/README.md)
- [docs/DEPLOYMENT.md](/home/feng/code/project/community/docs/DEPLOYMENT.md)
- [docs/ARCHITECTURE.md](/home/feng/code/project/community/docs/ARCHITECTURE.md)

更新原则如下：

- 对新用户继续展示最少命令
- 文档正文优先展示 `make` 或统一脚本入口
- 在附录或命令速查中展示真实 `docker compose -f ...` 组合
- 明确说明 `profiles` 不再是主使用路径

## 12. 实施顺序

建议按以下顺序实施：

1. 提取当前 compose 中的公共模板，先在不拆文件的前提下压缩重复配置
2. 拆出 `compose.debug.yml`
3. 拆出 `compose.observability.yml` 与 `compose.observability-elastic.yml`
4. 修正 JSON logs override，改为与真实 service 名一致
5. 将 always-on 栈拆分为 `compose.infra.yml` 与 `compose.runtime.yml`
6. 新建 `compose.yml` 集中管理 `name` 与 `volumes`
7. 提供统一 `Makefile` 或脚本入口
8. 更新部署与架构文档

## 13. 风险与取舍

### 13.1 命令表面上会变长

真实底层命令一定会变长。这是接受的代价。解决方式不是回退单文件，而是提供稳定别名。

### 13.2 文档迁移不完整会导致团队短期混乱

如果代码已经拆分，但文档仍然保留旧命令，开发者会在短期内频繁踩坑。因此文档更新不是附属工作，而是本次改造的必要交付物。

### 13.3 过度抽象 anchor 会损害可读性

去重的目标是压掉显著重复，不是建立一个难以理解的 YAML 模板系统。公共模板必须克制使用。

### 13.4 一次性大改风险高

虽然最终目标是完整分层，但实施顺序需要渐进，优先拆收益最高、风险最低的可选层。

## 14. 验收标准

设计实施完成后，应满足以下验收标准：

- 默认 `up` 仍能启动当前核心本地 HA 栈
- `debug`、`observability`、`observability-elastic` 都可以通过追加 overlay 独立开启
- Elastic JSON logs 场景可正常覆盖到全部 backend 副本
- `docker compose config` 对所有官方支持组合都能成功输出
- 文档中主命令、速查命令和真实行为一致
- 新旧文件职责边界清晰，维护者不需要再在一个超大文件中横跳

## 15. 最终决策

本轮本地部署重构采用以下最终决策：

- 主拆分机制采用显式 `-f` overlay
- 目标结构为 `compose.yml + compose.infra.yml + compose.runtime.yml + 可选 overlay`
- 对外保留统一命令入口，不要求使用者直接记忆长命令
- 不把 `include` 作为主方案
- 不改变现有 topology、端口、service 名与本地 HA 语义
