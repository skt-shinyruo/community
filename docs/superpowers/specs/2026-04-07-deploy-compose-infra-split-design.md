# 本地部署设计：将 `compose.infra.yml` 细化拆分为多文件基础设施层

## 1. 背景

上一轮本地部署重构已经把单体 `deploy/docker-compose.yml` 拆成：

- `deploy/compose.yml`
- `deploy/compose.infra.yml`
- `deploy/compose.runtime.yml`
- 若干 debug / observability / json-logs overlay

这一步解决了“单一 compose 入口过重”的问题，但当前 [compose.infra.yml](/home/feng/code/project/community/deploy/compose.infra.yml) 仍然集中承载了所有基础设施与控制面：

- MySQL 主从与 replication bootstrap
- Redis Cluster 与 cluster bootstrap
- Kafka KRaft 与 topic bootstrap
- Elasticsearch 集群与 index bootstrap
- `nacos`
- `xxl-job-admin`
- `mailhog`
- `mock-data-studio-db-bootstrap`

当前文件规模已经达到 500+ 行，继续存在以下问题：

- 维护者修改某个中间件时仍然要在一个大文件里横跳
- MySQL / Redis / Kafka / Elasticsearch 的 review 边界仍然不清晰
- 控制面和数据基础设施混在一起，职责不够直观
- `Makefile` 与文档已经收敛为分层模型，但 infra 层内部还不够细

用户已经明确确认以下方向：

- 继续沿用当前 layered compose + `Makefile` operator 模型
- `compose.infra.yml` 继续拆分
- 拆分边界明确采用：
  - `mysql`
  - `redis`
  - `kafka`
  - `elasticsearch`
  - `nacos`
  - `xxl-job`
  - `mailhog`
  - `mock-data-studio-db-bootstrap`

因此，本设计的目标不是再次改变 operator 入口，而是在保持当前使用方式不变的前提下，把 infra 层内部继续细化为可独立维护的多文件结构。

## 2. 目标

本轮设计目标如下：

- 将 [compose.infra.yml](/home/feng/code/project/community/deploy/compose.infra.yml) 拆分为 8 个职责清晰的子文件
- 保持 `make up` / `make up-debug` / `make up-obs` / `make up-elastic` / `make up-elastic-json` 不变
- 保持现有 service 名、端口、命名卷、依赖关系和本地 HA 拓扑不变
- 让 MySQL / Redis / Kafka / Elasticsearch / 控制面变更拥有更清晰的 review 边界
- 删除旧的 `compose.infra.yml`，避免形成双轨 source of truth

## 3. 非目标

本轮不做以下事情：

- 不改 `compose.runtime.yml`
- 不改 overlay 文件结构
- 不改任何 service 名
- 不改任何 `depends_on` 目标名
- 不顺手调整中间件参数、端口或资源预算
- 不把 `nacos`、`xxl-job`、`mailhog` 合并回一个“control”文件
- 不引入新的 operator 命令名

## 4. 方案对比

### 4.1 方案 A：保持单一 `compose.infra.yml`

做法：

- 维持当前 infra 单文件
- 仅通过注释改善可读性

优点：

- 改动最小
- 不需要更新 `Makefile` 与文件分工文档

缺点：

- 500+ 行 infra 文件继续增长
- MySQL / Redis / Kafka / Elasticsearch / 控制面边界仍不清晰
- 后续任何 infra 变更依旧要在同一文件内 review

结论：

- 不采用

### 4.2 方案 B：按 5 组拆分

做法：

- `mysql`
- `redis`
- `kafka`
- `elasticsearch`
- `control`（`nacos` / `xxl-job` / `mailhog` / `mock-data-studio-db-bootstrap`）

优点：

- 比当前明显更清晰
- `Makefile` 改动适中

缺点：

- 用户已经明确希望 `nacos`、`xxl-job`、`mailhog`、`mock-data-studio-db-bootstrap` 分别独立
- `control` 文件仍然会混合职责

结论：

- 不采用

### 4.3 方案 C：按 8 组精确拆分

做法：

- `compose.infra.mysql.yml`
- `compose.infra.redis.yml`
- `compose.infra.kafka.yml`
- `compose.infra.elasticsearch.yml`
- `compose.infra.nacos.yml`
- `compose.infra.xxl-job.yml`
- `compose.infra.mailhog.yml`
- `compose.infra.mock-data-studio-bootstrap.yml`

优点：

- 完全符合用户指定的维护边界
- 文件职责最直观
- 修改某个基础设施时定位最快
- 有利于后续继续把各类 operator runbook 精准映射到文件边界

缺点：

- `Makefile` 内部 `-f` 列表会变长
- 文档中的文件结构说明需要同步更新

结论：

- 采用

## 5. 总体设计

### 5.1 设计原则

本次细化拆分遵循以下原则：

- 对 operator 继续保持单入口
- 对维护者进一步细化 infra 层边界
- 数据基础设施与控制面分开
- 不新增“聚合型占位 infra 文件”，避免再次形成双 source of truth
- 继续把行为不变放在首位，先做纯结构迁移

### 5.2 目标文件结构

`deploy/` 目录中的基础设施层改为：

```text
deploy/
  compose.yml
  compose.infra.mysql.yml
  compose.infra.redis.yml
  compose.infra.kafka.yml
  compose.infra.elasticsearch.yml
  compose.infra.nacos.yml
  compose.infra.xxl-job.yml
  compose.infra.mailhog.yml
  compose.infra.mock-data-studio-bootstrap.yml
  compose.runtime.yml
  compose.debug.yml
  compose.observability.yml
  compose.observability-elastic.yml
  compose.json-logs.override.yml
```

旧的 `deploy/compose.infra.yml` 在迁移完成后删除。

### 5.3 文件职责

#### `deploy/compose.infra.mysql.yml`

承载：

- `mysql-primary`
- `mysql-replica-1`
- `mysql-replica-2`
- `mysql-replication-bootstrap`

说明：

- `mysql-replication-bootstrap` 继续和 MySQL owner 文件放在一起，不拆到独立 bootstrap 文件
- 原因是其生命周期和依赖完全受 MySQL 拓扑约束

#### `deploy/compose.infra.redis.yml`

承载：

- `redis-1..6`
- `redis-cluster-bootstrap`

#### `deploy/compose.infra.kafka.yml`

承载：

- `kafka-1..3`
- `kafka-init`

#### `deploy/compose.infra.elasticsearch.yml`

承载：

- `elasticsearch-1..3`
- `es-init`

#### `deploy/compose.infra.nacos.yml`

承载：

- `nacos`

#### `deploy/compose.infra.xxl-job.yml`

承载：

- `xxl-job-admin-1`
- `xxl-job-admin-2`

#### `deploy/compose.infra.mailhog.yml`

承载：

- `mailhog`

#### `deploy/compose.infra.mock-data-studio-bootstrap.yml`

承载：

- `mock-data-studio-db-bootstrap`

说明：

- 这里的“bootstrap”仅指 `mock-data-studio-db-bootstrap`
- 不把 MySQL / Redis / Kafka / Elasticsearch 的 bootstrap sidecar 统一挪到这个文件
- 原因是这些 sidecar 分别属于各自基础设施域的拓扑组成部分

## 6. 命令入口设计

### 6.1 Operator 命令保持不变

以下命令名继续保持不变：

- `make up`
- `make up-debug`
- `make up-obs`
- `make up-elastic`
- `make up-elastic-json`
- 全部 `down-*`
- 全部 `ps-*`
- 全部 `logs-*`
- 全部 `config*`

### 6.2 Makefile 内部结构调整

当前 `Makefile` 的：

```make
COMPOSE_BASE = docker compose --env-file deploy/.env \
	-f deploy/compose.yml \
	-f deploy/compose.infra.yml \
	-f deploy/compose.runtime.yml
```

调整为：

```make
COMPOSE_INFRA = \
	-f deploy/compose.infra.mysql.yml \
	-f deploy/compose.infra.redis.yml \
	-f deploy/compose.infra.kafka.yml \
	-f deploy/compose.infra.elasticsearch.yml \
	-f deploy/compose.infra.nacos.yml \
	-f deploy/compose.infra.xxl-job.yml \
	-f deploy/compose.infra.mailhog.yml \
	-f deploy/compose.infra.mock-data-studio-bootstrap.yml

COMPOSE_BASE = docker compose --env-file deploy/.env \
	-f deploy/compose.yml \
	$(COMPOSE_INFRA) \
	-f deploy/compose.runtime.yml
```

这样对使用者零认知变化，只在内部组合层增加精细度。

## 7. 依赖关系约束

### 7.1 service 名和 `depends_on` 名保持不变

`compose.runtime.yml` 中所有现有依赖继续沿用原 service 名，例如：

- `community-app-*` 继续依赖 `mailhog`、`nacos`、`mock-data-studio-db-bootstrap`、`mysql-primary`、`xxl-job-admin-1`、`redis-cluster-bootstrap`、`es-init`
- `community-gateway-*` 继续依赖 `community-app-1`、`im-core-1`、`im-realtime-1`、`nacos`
- `im-core-*` 继续依赖 `mysql-primary`、`kafka-init`、`nacos`
- `im-realtime-*` 继续依赖 `kafka-init`、`im-core-1`、`nacos`

即：

- 只换文件归属
- 不换 service identity

### 7.2 不新增跨域聚合文件

不保留新的 `compose.infra.yml` 作为空壳或 include 层，原因如下：

- 会引入额外 source of truth
- 会让文档和 `Makefile` 同时出现“真实 infra 文件”和“聚合 infra 文件”两层概念
- 当前 `Makefile` 已足够承担聚合作用

## 8. 文档影响

需要同步更新的文档主要是“文件分工”与“底层命令示例”层面：

- [deploy/README.md](/home/feng/code/project/community/deploy/README.md)
- [docs/DEPLOYMENT.md](/home/feng/code/project/community/docs/DEPLOYMENT.md)
- [docs/ARCHITECTURE.md](/home/feng/code/project/community/docs/ARCHITECTURE.md)
- [deploy/.env.example](/home/feng/code/project/community/deploy/.env.example)

更新重点：

- 不再出现 `compose.infra.yml`
- 文件分工说明改成 8 个 infra 文件
- 显式命令示例中的 `-f deploy/compose.infra.yml` 改成 `$(COMPOSE_INFRA)` 展开的等价多文件组合

主入口命令说明继续以 `make` 为主，不增加新的 operator 学习成本。

## 9. 风险与取舍

### 9.1 Makefile 组合行更长

这是接受的代价。因为：

- operator 不直接记忆这串命令
- 真实复杂度本来就存在，只是从单文件内部移动到了 `Makefile`

### 9.2 bootstrap 文件分布不完全对称

这次设计里：

- `mysql-replication-bootstrap` 跟 MySQL
- `redis-cluster-bootstrap` 跟 Redis
- `kafka-init` 跟 Kafka
- `es-init` 跟 Elasticsearch
- `mock-data-studio-db-bootstrap` 单独成文件

这种分布不是“纯按 bootstrap 类型归一”，但它更符合实际 owner 边界和用户指定分组。

### 9.3 文档中 `-f` 示例会更长

这仍然是可接受取舍，因为：

- 主路径优先展示 `make`
- 长命令主要出现在低层 operator 文档中，本来就是给需要底层控制的人看的

## 10. 验收标准

本轮细化拆分完成后，应满足以下标准：

- `make up` / `make up-debug` / `make up-obs` / `make up-elastic` / `make up-elastic-json` 命令名保持不变
- `make config*` 全部通过
- 基础设施分工文档切换到新的 8 文件模型
- `deploy/compose.infra.yml` 被删除
- 渲染后的 compose 结果与拆分前等价，不引入 service 集、端口、卷、依赖关系漂移

## 11. 与上一版设计的关系

本设计是对 [2026-04-06-deploy-compose-f-split-design.md](/home/feng/code/project/community/docs/superpowers/specs/2026-04-06-deploy-compose-f-split-design.md) 的细化演进。

关系如下：

- 保留其“layered compose + `Makefile` operator 入口”的总体决策
- 替换其中关于 `deploy/compose.infra.yml` 的单文件设计
- 不改变 `compose.yml` / `compose.runtime.yml` / overlays 的大方向

若两份 spec 在 infra 文件布局上冲突，以本稿为准。

## 12. 最终决策

本轮进一步拆分采用以下最终决策：

- 删除 `deploy/compose.infra.yml`
- 新增 8 个 infra 文件：
  - `deploy/compose.infra.mysql.yml`
  - `deploy/compose.infra.redis.yml`
  - `deploy/compose.infra.kafka.yml`
  - `deploy/compose.infra.elasticsearch.yml`
  - `deploy/compose.infra.nacos.yml`
  - `deploy/compose.infra.xxl-job.yml`
  - `deploy/compose.infra.mailhog.yml`
  - `deploy/compose.infra.mock-data-studio-bootstrap.yml`
- `Makefile` 继续作为 operator 侧的聚合入口
- operator 命令名保持不变
- service 名、端口、卷、依赖关系与 runtime 语义保持不变
